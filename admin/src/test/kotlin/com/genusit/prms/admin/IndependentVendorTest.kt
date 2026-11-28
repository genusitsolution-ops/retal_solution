package com.genusit.prms.admin

import com.genusit.prms.admin.net.AdminApi
import com.genusit.prms.admin.net.AdminJson
import com.genusit.prms.admin.net.ApiException
import com.genusit.prms.admin.ui.vendorFormErrors
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** Independent vendors (1.1.0): registration, profile, status, sign-in reset — against answers recorded from the server. */
class IndependentVendorTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AdminApi
    private val fx = AdminJson.parseToJsonElement(
        listOf("../tools/admin-replay/fixtures.json", "tools/admin-replay/fixtures.json").map(::File).first { it.exists() }.readText()
    ).jsonObject
    private fun body(key: String) = fx[key]!!.jsonObject["body"].toString()
    private fun enqueue(json: String, code: Int = 200) = server.enqueue(MockResponse().setResponseCode(code).setBody(json))
    private fun sent() = AdminJson.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @BeforeTest fun up() { server = MockWebServer(); server.start(); api = AdminApi(server.url("/api/v1/").toString(), { "t0ken" }) }
    @AfterTest fun down() { server.shutdown() }

    @Test fun listHasBothKinds() = runTest {
        enqueue(body("admin/vendors.php"))
        val l = api.vendors("", "")
        val sunita = l.vendors.first { it.id == 101 }
        assertTrue(sunita.isIndependent); assertEquals("VEN10101", sunita.code)
        val ramesh = l.vendors.first { it.fullName == "Ramesh Patel" }
        assertFalse(ramesh.isIndependent); assertEquals(ramesh.referralCode, ramesh.code)
    }

    @Test fun independentDetailWithProfileAndPayment() = runTest {
        enqueue(body("admin/vendors.php?id=101"))
        val d = api.vendor(101)
        val p = assertNotNull(d.profile)
        assertEquals("VEN10101", p.vendorCode); assertEquals("upi", p.payment.payoutMethod); assertEquals("sunita@okbank", p.payment.upiId)
        assertEquals("123456789012", p.accountNumber); assertTrue(p.payment.accountNumberMasked!!.endsWith("9012"))
        assertEquals(2, d.referredOwners.size)
        assertTrue(d.commissions.any { it.status == "paid" && it.payoutMethod == "upi" && it.payoutReference == "UTR55667788" })
        assertEquals("upi", d.payouts.first().payoutMethod)
    }

    @Test fun createSendsEveryFieldAndReadsTheSignIn() = runTest {
        enqueue("""{"status":"success","message":"Vendor registered.","data":{"id":5,"vendor_code":"VEN10005","mobile":"9876501234","password":"ABCD1234","password_generated":true}}""", 201)
        val (msg, c) = api.createVendor(mapOf("full_name" to "Meena Joshi", "mobile" to "9876501234", "commission_percent" to "12.5", "payout_method" to "bank",
            "vendor_can_edit_payment" to "", "password" to ""))
        assertEquals("Vendor registered.", msg); assertEquals(5, c.id); assertEquals("VEN10005", c.vendorCode); assertEquals("ABCD1234", c.password)
        val b = sent()
        assertEquals("create_independent", b["action"]!!.jsonPrimitive.content); assertEquals("12.5", b["commission_percent"]!!.jsonPrimitive.content)
        assertEquals("false", b["vendor_can_edit_payment"]!!.jsonPrimitive.content)   // a real boolean, as the server expects
    }

    @Test fun statusResetAndErrors() = runTest {
        enqueue("""{"status":"success","message":"Vendor status updated.","data":{"id":101}}""")
        api.setVendorStatus(101, "suspended", "Documents pending")
        val b = sent(); assertEquals("set_status", b["action"]!!.jsonPrimitive.content); assertEquals("Documents pending", b["reason"]!!.jsonPrimitive.content)
        enqueue("""{"status":"success","message":"New temporary password created.","data":{"id":101,"password":"WXYZ9876"}}""")
        assertEquals("WXYZ9876", api.resetVendorPassword(101).second)
        assertEquals("reset_password", sent()["action"]!!.jsonPrimitive.content)
        enqueue("""{"status":"error","code":"DUPLICATE","message":"Another vendor already uses this mobile number."}""", 409)
        try { api.updateIndependentVendor(101, mapOf("mobile" to "9000000001")); fail("expected an error") } catch (e: ApiException) {
            assertEquals("DUPLICATE", e.code); assertEquals("Another vendor already uses this mobile number.", e.message)
        }
    }

    @Test fun formChecksMatchTheServerRules() {
        val ok = mapOf("full_name" to "Meena", "mobile" to "9876501234", "address" to "Road", "city" to "Pune", "state" to "MH", "pincode" to "411001",
            "commission_percent" to "10", "payout_method" to "upi")
        assertTrue(vendorFormErrors(ok, true).isEmpty())
        assertTrue("mobile" in vendorFormErrors(ok + ("mobile" to "12345"), true))
        assertTrue("commission_percent" in vendorFormErrors(ok + ("commission_percent" to "0"), true))
        assertTrue("commission_percent" in vendorFormErrors(ok + ("commission_percent" to "101"), true))
        assertTrue("upi_id" in vendorFormErrors(ok + ("upi_id" to "nope"), true))
        assertTrue("ifsc" in vendorFormErrors(ok + ("ifsc" to "ABC"), true))
        assertTrue(vendorFormErrors(ok + ("ifsc" to "test0001234"), true).isEmpty())   // lower case is upper-cased before sending
        val bank = vendorFormErrors(ok + ("payout_method" to "bank"), true)
        assertTrue("account_holder" in bank && "account_number" in bank && "ifsc" in bank)
        assertTrue("password" in vendorFormErrors(ok + ("password" to "abc"), true))
        assertFalse("password" in vendorFormErrors(ok + ("password" to "abc"), false))   // no password field when editing
    }
}
