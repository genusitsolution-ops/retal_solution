package com.genusit.prms.admin

import com.genusit.prms.admin.net.AdminApi
import com.genusit.prms.admin.net.AdminJson
import com.genusit.prms.admin.net.ApiException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import java.io.File

/**
 * The admin API client against answers recorded from the real server code (tools/admin-replay/fixtures.json,
 * made-up accounts): every screen's data parses, actions send the right body, errors and 401s are recognised.
 */
class AdminApiTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AdminApi
    private var token: String? = "t0ken"
    private val fx = AdminJson.parseToJsonElement(
        listOf("../tools/admin-replay/fixtures.json", "tools/admin-replay/fixtures.json").map(::File).first { it.exists() }.readText()
    ).jsonObject

    private fun body(key: String) = fx[key]!!.jsonObject["body"].toString()
    private fun enqueue(json: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(json))

    @BeforeTest fun up() { server = MockWebServer(); server.start(); api = AdminApi(server.url("/api/v1/").toString(), { token }) }
    @AfterTest fun down() { server.shutdown() }

    @Test fun dashboard() = runTest {
        enqueue(body("admin/dashboard.php"))
        val d = api.dashboard()
        assertEquals(1, d.pendingSignups); assertEquals(2, d.pendingPayments); assertEquals(7, d.owners.total); assertEquals(1, d.owners.suspended)
        assertTrue(d.vendors.ready); assertEquals(49.9, d.vendors.commissionPaid, 0.001); assertEquals(2, d.recentPayments.size)
        val r = server.takeRequest()
        assertEquals("/api/v1/admin/dashboard.php", r.path); assertEquals("Bearer t0ken", r.getHeader("Authorization"))
    }

    @Test fun signupsAndActions() = runTest {
        enqueue(body("admin/signups.php?status=all"))
        val s = api.signups("all")
        assertEquals(mapOf("pending" to 1, "approved" to 3, "rejected" to 1), s.counts)
        val pooja = s.signups.first { it.username == "pooja.r" }
        assertEquals("pending", pooja.status); assertEquals("Ramesh Patel", pooja.referrerName)
        assertEquals("Duplicate test account", s.signups.first { it.status == "rejected" }.notes)
        assertEquals("/api/v1/admin/signups.php?status=all", server.takeRequest().path)
        enqueue("""{"status":"success","message":"Owner approved and activated.","data":{}}""")
        assertEquals("Owner approved and activated.", api.approveSignup(pooja.id))
        val sent = AdminJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("approve", sent["action"]!!.jsonPrimitive.content); assertEquals(pooja.id.toString(), sent["id"]!!.jsonPrimitive.content)
    }

    @Test fun ownersListAndDetail() = runTest {
        enqueue(body("admin/owners.php"))
        val l = api.owners("", "")
        assertEquals(7, l.owners.size); assertEquals(7, l.counts["all"])
        val ramesh = l.owners.first { it.id == 1 }
        assertTrue(ramesh.isVendor); assertEquals("Patel Residency", ramesh.businessName)
        assertEquals("/api/v1/admin/owners.php", server.takeRequest().path)   // blank filters are not sent
        enqueue(body("admin/owners.php?id=1"))
        val d = api.owner(1)
        assertEquals(10.0, d.vendor!!.commissionPercent); assertEquals(2, d.vendor!!.totals.referrals)
        enqueue(body("admin/owners.php?id=3"))
        val amit = api.owner(3)
        assertEquals("Ramesh Patel", amit.referredBy!!.name); assertEquals("approved", amit.signup!!.status); assertNull(amit.vendor)
        assertEquals(1, amit.recentPayments.size)
    }

    @Test fun vendorDetailAndPayout() = runTest {
        enqueue(body("admin/vendors.php?id=1"))
        val v = api.vendor(1)
        assertEquals("Ramesh Patel", v.vendor.fullName); assertEquals(2, v.commissions.size); assertEquals(2, v.referredOwners.size)
        assertEquals(1, v.unpaidMonths.size); assertEquals(1, v.unpaidMonths[0].pendingN)
        assertEquals("NEFT-HDFC-88231", v.payouts[0].payoutReference); assertEquals("Test Admin", v.payouts[0].recordedBy)
        assertTrue(v.commissions.any { it.status == "paid" && it.payoutReference == "NEFT-HDFC-88231" })
        server.takeRequest()
        enqueue("""{"status":"success","message":"Payout saved and marked as paid.","data":{"count":1,"amount":49.9}}""")
        api.payMonth(1, v.unpaidMonths[0].month, "2026-11-09", "UTR77", "", true)
        val sent = AdminJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("pay_month", sent["action"]!!.jsonPrimitive.content); assertEquals("UTR77", sent["payout_reference"]!!.jsonPrimitive.content)
        assertEquals("true", sent["approve_pending"]!!.jsonPrimitive.content)
        enqueue(body("admin/vendors.php?commission_id=1"))
        val h = api.commissionHistory(1)
        assertEquals("created", h.history.first().action); assertTrue(h.history.any { it.action == "paid" })
    }

    @Test fun paymentsWithChecks() = runTest {
        enqueue(body("admin/payments.php?status=pending"))
        val p = api.payments("pending")
        assertEquals(2, p.payments.size)
        val short = p.payments.first { it.utr == "712345678904" }
        assertTrue(short.checks.shortPaid); assertEquals(false, short.checks.ocr!!.utrMatches); assertEquals(true, short.checks.ocr!!.amountMatches)
        assertEquals("Monthly 499", short.invoice.requestedPackage); assertTrue(short.hasScreenshot)
        val clean = p.payments.first { it.utr == "612345678903" }
        assertFalse(clean.checks.shortPaid); assertEquals(true, clean.checks.ocr!!.utrMatches)
        server.takeRequest()
        enqueue("""{"status":"success","message":"Payment approved. The plan is active.","data":{}}""")
        api.approvePayment(clean.id, "ok", false)
        val sent = AdminJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("true", sent["verified"]!!.jsonPrimitive.content)   // the app always confirms the bank check explicitly
    }

    @Test fun screenshotBytes() = runTest {
        server.enqueue(MockResponse().setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()))))
        val (bytes, type) = api.screenshot(3)
        assertEquals("image/png", type); assertEquals(4, bytes.size)
        assertEquals("/api/v1/admin/payments.php?file=3", server.takeRequest().path)
        enqueue("""{"status":"error","code":"NOT_FOUND","message":"Screenshot not found."}""", 404)
        try { api.screenshot(9); fail() } catch (e: ApiException) { assertEquals("Screenshot not found.", e.message) }
    }

    @Test fun errorsAndSessionEnd() = runTest {
        enqueue("""{"status":"error","code":"AMOUNT_MISMATCH","message":"The amount paid is less than the invoice total."}""", 422)
        try { api.approvePayment(1, "", false); fail() } catch (e: ApiException) {
            assertEquals("AMOUNT_MISMATCH", e.code); assertFalse(e.isSessionEnded); assertEquals("The amount paid is less than the invoice total.", e.message)
        }
        enqueue("""{"status":"error","code":"TOKEN_EXPIRED","message":"Invalid or expired token. Please login again."}""", 401)
        try { api.dashboard(); fail() } catch (e: ApiException) { assertTrue(e.isSessionEnded) }
        enqueue("""{"status":"error","code":"FORBIDDEN","message":"Access denied for your role."}""", 403)
        try { api.owners("", ""); fail() } catch (e: ApiException) { assertEquals("FORBIDDEN", e.code); assertFalse(e.isSessionEnded) }
        enqueue("<html>oops</html>", 500)
        try { api.dashboard(); fail() } catch (e: ApiException) { assertEquals("SERVER_ERROR", e.code) }
    }

    @Test fun loginSendsNoTokenAndNoLanguage() = runTest {
        enqueue(fx["POST auth/login.php"].toString())
        val r = api.login("testadmin", "pw")
        assertEquals("admin", r.userType)
        val req = server.takeRequest()
        assertNull(req.getHeader("Authorization"))
        val sent = AdminJson.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertNull(sent["lang"])
        enqueue(fx["POST auth/login.php#owner"].toString())
        assertEquals("owner", api.login("testowner", "pw").userType)   // the screen refuses non-admin accounts
    }
}
