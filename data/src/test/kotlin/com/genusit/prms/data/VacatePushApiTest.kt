package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.AllocationRepositoryImpl
import com.genusit.prms.data.repository.CommonRepositoryImpl
import com.genusit.prms.data.repository.PublicRepositoryImpl
import com.genusit.prms.data.repository.TenantAdminRepositoryImpl
import com.genusit.prms.data.repository.TenantPortalRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.SettlementCharge
import com.genusit.prms.domain.model.VacateInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** App 1.11: deposit history, vacate preview / confirm / refund, tenant deposit, push registration and preferences. */
class VacatePushApiTest {
    private lateinit var server: MockWebServer
    private lateinit var alloc: AllocationRepositoryImpl
    private lateinit var common: CommonRepositoryImpl
    private lateinit var tenants: TenantAdminRepositoryImpl
    private lateinit var portal: TenantPortalRepositoryImpl
    private lateinit var public: PublicRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        val c = ApiCaller(json)
        alloc = AllocationRepositoryImpl(api, c); common = CommonRepositoryImpl(api, c); tenants = TenantAdminRepositoryImpl(api, c)
        portal = TenantPortalRepositoryImpl(api, c); public = PublicRepositoryImpl(api, c, config)
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }
    private fun body() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun allocationDetailWithDepositHistoryAndStatement() = runTest {
        enqueue(200, VacateFixtures.ALLOC_DETAIL)
        val d = alloc.allocation(1)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("id"))
        assertEquals(8000.0, d.deposit.agreed); assertEquals(6000.0, d.deposit.collected); assertEquals(2000.0, d.deposit.pending)
        assertEquals(6000.0, d.deposit.adjusted); assertEquals(0.0, d.deposit.held)
        assertEquals(listOf("collect", "collect", "adjust_rent", "adjust_charges"), d.history.map { it.type })
        assertEquals(listOf(5000.0, 6000.0, 266.0, 0.0), d.history.map { it.heldAfter })
        assertTrue(d.history[0].isIn); assertFalse(d.history[2].isIn)
        val st = assertNotNull(d.settlement)
        assertEquals("VS-202611-000001", st.settlementNo); assertEquals(-234.0, st.finalAmount); assertEquals("payable", st.result)
        assertEquals("none", st.refundStatus); assertEquals(500.0, st.additionalCharges)
        assertEquals(listOf(SettlementCharge("Painting", 500.0, "Wall repaint")), st.charges)
        assertTrue(st.lines.any { it.key == "final" && it.amount == 234.0 })
        assertTrue(d.shareText!!.contains("Tenant pays ₹234.00"))
    }

    @Test fun closedListCarriesSettlementResult() = runTest {
        enqueue(200, VacateFixtures.ALLOC_CLOSED_LIST)
        val l = alloc.allocations("closed")
        assertTrue(l.isNotEmpty() && l.all { !it.isActive && it.settlement != null })
    }

    @Test fun previewAndConfirmBodies() = runTest {
        enqueue(200, VacateFixtures.PREVIEW)
        val input = VacateInput("2026-11-01", applyDeposit = true, charges = listOf(SettlementCharge(" Painting ", 500.0, " Wall repaint ")), notes = " keys ")
        val p = alloc.vacatePreview(5, input)
        val b = body()
        assertEquals("vacate_preview", b["action"]!!.jsonPrimitive.content); assertEquals("2026-11-01", b["vacate_date"]!!.jsonPrimitive.content)
        assertTrue(b["apply_deposit"]!!.jsonPrimitive.boolean)
        val ch = b["charges"]!!.jsonArray[0].jsonObject
        assertEquals("Painting", ch["label"]!!.jsonPrimitive.content); assertEquals("Wall repaint", ch["reason"]!!.jsonPrimitive.content); assertEquals(500.0, ch["amount"]!!.jsonPrimitive.double)
        assertNull(b["expected_final"], "preview never sends a confirmation amount")
        assertEquals(-3400.0, p.finalAmount); assertEquals("payable", p.result); assertEquals(2900.0, p.outstanding)
        assertTrue(p.lines.isNotEmpty())

        enqueue(409, """{"status":"error","code":"SETTLEMENT_CHANGED","message":"The figures changed since the preview (for example a payment was recorded). Review the settlement again."}""")
        try { alloc.vacateConfirm(5, input.copy(refundNow = true, refundMethod = "upi", refundReference = "UTR1"), -3400.0); fail() }
        catch (e: ApiException) { assertEquals("SETTLEMENT_CHANGED", e.code); assertEquals(409, e.httpStatus) }
        val cb = body()
        assertEquals("vacate_confirm", cb["action"]!!.jsonPrimitive.content); assertEquals(-3400.0, cb["expected_final"]!!.jsonPrimitive.double)
        assertTrue(cb["refund_now"]!!.jsonPrimitive.boolean); assertEquals("upi", cb["refund_method"]!!.jsonPrimitive.content); assertEquals("UTR1", cb["refund_reference"]!!.jsonPrimitive.content)

        enqueue(200, """{"status":"success","message":"Recorded","data":{"settlement_no":"VS-202611-000002","refund_status":"paid","final_amount":4000,"result":"refund","result_label":"Refund ₹4,000.00 to the tenant"}}""")
        val r = alloc.refundPaid(2, "upi", " UTRREF1 ", "2026-11-01")
        val rb = body()
        assertEquals("refund_paid", rb["action"]!!.jsonPrimitive.content); assertEquals("UTRREF1", rb["reference"]!!.jsonPrimitive.content)
        assertEquals("paid", r.refundStatus)
    }

    @Test fun depositCollectSendsModeAndReference() = runTest {
        enqueue(200, """{"status":"success","message":"Deposit collected","data":{"allocation_id":1,"deposit_collected":6000,"deposit_balance":2000}}""")
        alloc.collectDeposit(1, 1000.0, "upi", " UTR555 ")
        val b = body()
        assertEquals("collect_deposit", b["action"]!!.jsonPrimitive.content); assertEquals("upi", b["method"]!!.jsonPrimitive.content); assertEquals("UTR555", b["reference"]!!.jsonPrimitive.content)
    }

    @Test fun tenantDetailAndPortalDeposits() = runTest {
        enqueue(200, VacateFixtures.TENANT_DETAIL)
        val t = tenants.tenant(1)
        assertEquals(2, t.deposits.size); assertTrue(t.deposits[0].isActive); assertEquals("closed", t.deposits[1].status)
        assertEquals("VS-202611-000001", t.deposits[1].settlement!!.settlementNo)
        server.takeRequest()
        enqueue(200, VacateFixtures.TENANT_DEPOSIT)
        val mine = portal.deposits()
        val closed = mine.first { it.status == "closed" && it.statement?.settlementNo == "VS-202611-000001" }
        assertEquals(-234.0, closed.statement!!.finalAmount); assertNotNull(closed.shareText)
    }

    @Test fun pushRegistrationPrefsAndNotifications() = runTest {
        enqueue(200, """{"status":"success","message":"Registered.","data":null}""")
        common.registerPushToken("fcm-token-1", "1.11.0")
        val b = body()
        assertEquals("register", b["action"]!!.jsonPrimitive.content); assertEquals("fcm-token-1", b["token"]!!.jsonPrimitive.content); assertEquals("android", b["platform"]!!.jsonPrimitive.content)
        enqueue(200, VacateFixtures.PREFS)
        val p = common.notificationPrefs()
        assertTrue(p.pushAvailable); assertEquals(listOf("billing", "payments", "tenants", "complaints"), p.categories.map { it.category })
        assertTrue(p.categories.first { it.category == "payments" }.critical)
        server.takeRequest()
        enqueue(200, VacateFixtures.PREFS)
        common.saveNotificationPrefs(mapOf("billing" to false))
        val pb = body()
        assertEquals("prefs", pb["action"]!!.jsonPrimitive.content); assertFalse(pb["prefs"]!!.jsonObject["billing"]!!.jsonPrimitive.boolean)
        enqueue(200, VacateFixtures.NOTIFS)
        val n = common.notifications()
        assertEquals(11, n.unread); assertTrue(n.items.all { !it.route.isNullOrBlank() && !it.category.isNullOrBlank() })
        server.takeRequest()
        enqueue(200, """{"status":"success","message":"Success","data":{"unread":7}}""")
        assertEquals(7, common.unreadCount())
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("count"))
        enqueue(200, """{"status":"success","message":"Updated.","data":null}""")
        common.markNotificationRead(26)
        assertEquals(26, body()["id"]!!.jsonPrimitive.content.toInt())
    }

    @Test fun appConfigPushClientValues() = runTest {
        enqueue(200, VacateFixtures.CONFIG)
        val c = public.appConfig()
        val push = assertNotNull(c.push)
        assertTrue(push.isComplete)
        assertEquals("1:123456789012:android:bbbb", push.appIdFor("com.genusit.prms.debug"))
        assertEquals("1:123456789012:android:aaaa", push.appIdFor("com.genusit.prms"))
        assertFalse(VacateFixtures.CONFIG.contains("private_key"))
    }
}
