package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.AgentSettlementRepositoryImpl
import com.genusit.prms.data.repository.OwnerSettlementRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.HandoverMethod
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Employee collections & cash handover (app 1.7). The response bodies below were captured from
 * the PHP API running against a test database (made-up test accounts), so decoding is checked
 * against what the server really sends.
 */
class SettlementApiTest {
    private lateinit var server: MockWebServer
    private lateinit var owner: OwnerSettlementRepositoryImpl
    private lateinit var agent: AgentSettlementRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        owner = OwnerSettlementRepositoryImpl(api, ApiCaller(json)); agent = AgentSettlementRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) { server.enqueue(MockResponse().setResponseCode(code).setBody(body)) }

    @Test fun ownerDashboardDecodesAndSendsFilters() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"period":{"from":"2026-09-01","to":"2026-10-26"},"summary":{"total_collected":7200,"transactions":5,"owner_collected":500,"employee_collected":6700,"by_mode":{"cash":5200,"upi":2000},"pending_dues":300,"active_employees":2,"handed_over_in_period":6700,"handed_over_total":6700,"awaiting_confirmation":0,"pending_settlement":0,"pending_settlement_count":0},"employees":[{"id":1,"code":"EMP-0001","full_name":"Agent One","phone":null,"status":"active","period_collections":2,"period_collected":5000,"period_cash":3000,"period_upi":2000,"period_other":0,"collections":2,"collected":5000,"confirmed":5000,"awaiting_confirmation":0,"outstanding":0,"available":0,"last_collection_date":"2026-10-25","last_settlement_date":"2026-10-26 22:11:31"},{"id":2,"code":"EMP-0002","full_name":"Agent Two","phone":null,"status":"active","period_collections":2,"period_collected":1700,"period_cash":1700,"period_upi":0,"period_other":0,"collections":2,"collected":1700,"confirmed":1700,"awaiting_confirmation":0,"outstanding":0,"available":0,"last_collection_date":"2026-10-26","last_settlement_date":"2026-10-26 22:11:31"}],"collections":{"items":[{"id":5,"receipt_number":"EC-20261026221131-94EB","collection_date":"2026-10-26","created_at":"2026-10-26 16:41:31","amount":700,"payment_mode":"cash","collected_by":"employee","employee_id":2,"tenant_id":3,"property_id":1,"invoice_id":3,"remarks":"","emp_name":"Agent Two","tenant_name":"Tenant Three","property_code":"R-1","city":"Pune","invoice_number":"INV-A3","handed_over":700,"awaiting":0,"outstanding":0,"settlement_status":"settled","settlement_status_label":"Handed over","payment_mode_label":"Cash","collected_by_label":"Agent Two"},{"id":4,"receipt_number":"OC-20261026221131-F17F","collection_date":"2026-10-26","created_at":"2026-10-26 16:41:31","amount":500,"payment_mode":"cash","collected_by":"owner","employee_id":null,"tenant_id":3,"property_id":1,"invoice_id":3,"remarks":"","emp_name":"","tenant_name":"Tenant Three","property_code":"R-1","city":"Pune","invoice_number":"INV-A3","handed_over":0,"awaiting":0,"outstanding":0,"settlement_status":"owner","settlement_status_label":"Collected by owner","payment_mode_label":"Cash","collected_by_label":"Owner"}],"page":1,"per_page":2,"total_items":5,"pages":3},"filters":{"from":"2026-09-01","to":"2026-10-26"},"options":{"employees":[{"id":1,"full_name":"Agent One"},{"id":2,"full_name":"Agent Two"}],"properties":[{"id":1,"property_code":"R-1","city":"Pune"},{"id":2,"property_code":"R-2","city":"Pune"}],"tenants":[{"id":1,"full_name":"Tenant One"},{"id":3,"full_name":"Tenant Three"},{"id":2,"full_name":"Tenant Two"}],"modes":{"cash":"Cash","upi":"UPI","online":"Online Transfer","bank_transfer":"Bank Transfer","cheque":"Cheque","other":"Other"}}}}""")
        val d = owner.dashboard(CollectionFilter(from = "2026-09-01", to = "2026-10-26", employeeId = 2, mode = "upi", settlement = "pending"))
        val r = server.takeRequest()
        val url = r.requestUrl!!
        assertEquals("dashboard", url.queryParameter("view")); assertEquals("2", url.queryParameter("employee_id")); assertEquals("upi", url.queryParameter("mode"))
        assertEquals("pending", url.queryParameter("settlement")); assertNull(url.queryParameter("property_id"), "0 = all is left out"); assertNull(url.queryParameter("tenant_id"))
        assertEquals(7200.0, d.summary.totalCollected); assertEquals(500.0, d.summary.ownerCollected); assertEquals(6700.0, d.summary.employeeCollected)
        assertEquals(d.summary.totalCollected, d.summary.ownerCollected + d.summary.employeeCollected)
        assertEquals(2, d.employees.size); assertTrue(d.employees.all { it.code.startsWith("EMP-") })
        assertEquals(d.summary.employeeCollected, d.employees.sumOf { it.collected })
        assertEquals(2, d.collections.items.size); assertEquals(5, d.collections.totalItems); assertEquals(3, d.collections.pages)
        assertTrue(d.options.employees.isNotEmpty() && d.options.properties.isNotEmpty())
    }

    @Test fun employeeDetailAndHistory() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"employee":{"id":1,"full_name":"Agent One","phone":null,"status":"active","code":"EMP-0001"},"balance":{"collections":2,"collected":5000,"confirmed":5000,"awaiting_confirmation":0,"outstanding":0,"available":0,"last_collection_date":"2026-10-25","last_settlement_date":"2026-10-26 22:11:31"},"period":{"count":2,"total":5000,"cash":3000,"upi":2000,"other":0},"collections":{"items":[{"id":2,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","created_at":"2026-10-26 16:41:31","amount":2000,"payment_mode":"upi","collected_by":"employee","employee_id":1,"tenant_id":2,"property_id":2,"invoice_id":2,"remarks":"","emp_name":"Agent One","tenant_name":"Tenant Two","property_code":"R-2","city":"Pune","invoice_number":"INV-A2","handed_over":2000,"awaiting":0,"outstanding":0,"settlement_status":"settled","settlement_status_label":"Handed over","payment_mode_label":"UPI","collected_by_label":"Agent One"},{"id":1,"receipt_number":"EC-20261026221131-722E","collection_date":"2026-10-23","created_at":"2026-10-26 16:41:31","amount":3000,"payment_mode":"cash","collected_by":"employee","employee_id":1,"tenant_id":1,"property_id":1,"invoice_id":1,"remarks":"","emp_name":"Agent One","tenant_name":"Tenant One","property_code":"R-1","city":"Pune","invoice_number":"INV-A1","handed_over":3000,"awaiting":0,"outstanding":0,"settlement_status":"settled","settlement_status_label":"Handed over","payment_mode_label":"Cash","collected_by_label":"Agent One"}],"page":1,"per_page":2,"total_items":2,"pages":1},"settlements":[{"id":3,"settlement_number":"ST-20261026-EFC2A","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":2000,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":null,"status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Confirmed","method_label":"Cash"},{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Rejected","method_label":"UPI"},{"id":1,"settlement_number":"ST-20261026-3F1D0","owner_id":1,"employee_id":1,"amount_submitted":3000,"amount_confirmed":3000,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":"evening cash","status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Confirmed","method_label":"Cash"}]}}""")
        val e = owner.employee(1, "", "", 0, 1)
        val url = server.takeRequest().requestUrl!!
        assertEquals("employee", url.queryParameter("view")); assertEquals("1", url.queryParameter("employee_id")); assertNull(url.queryParameter("from"))
        assertEquals("EMP-0001", e.employee.code); assertEquals(5000.0, e.balance.collected); assertEquals(0.0, e.balance.outstanding)
        assertEquals(e.period.total, e.period.cash + e.period.upi + e.period.other)
        assertTrue(e.settlements.any { it.status == "rejected" && it.amountConfirmed == null })

        enqueue(200, """{"status":"success","message":"Success","data":{"items":[{"id":7,"settlement_number":"ST-20261026-07415","owner_id":1,"employee_id":2,"amount_submitted":700,"amount_confirmed":700,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":null,"status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent Two","item_count":1,"status_label":"Confirmed","method_label":"Cash"},{"id":6,"settlement_number":"ST-20261026-811A8","owner_id":1,"employee_id":2,"amount_submitted":400,"amount_confirmed":400,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":"paid at office","status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"owner","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent Two","item_count":1,"status_label":"Confirmed","method_label":"Cash"}],"page":1,"per_page":2,"total_items":7,"pages":4,"pending_count":0}}""")
        val l = owner.settlements("submitted", 0, 2)
        val u2 = server.takeRequest().requestUrl!!
        assertEquals("submitted", u2.queryParameter("status")); assertEquals("2", u2.queryParameter("page"))
        assertTrue(l.items.isNotEmpty() && l.items.all { it.itemCount >= 1 })

        enqueue(200, """{"status":"success","message":"Success","data":{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Rejected","method_label":"UPI","items":[{"collection_id":2,"amount_submitted":2000,"amount_confirmed":0,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","collection_amount":2000,"payment_mode":"upi","tenant_name":"Tenant Two","property_code":"R-2","payment_mode_label":"UPI"}],"audit":[{"user_type":"employee","action":"create","description":"Submitted handover ST-20261026-CCC68 of 2000.00 (upi) for employee #1","created_at":"2026-10-26 16:41:31"},{"user_type":"owner","action":"reject","description":"Rejected ST-20261026-CCC68: Not received in bank","created_at":"2026-10-26 16:41:31"}]}}""")
        val d = owner.detail(2)
        assertEquals("rejected", d.status); assertNull(d.amountConfirmed); assertEquals("Not received in bank", d.reviewNote)
        assertEquals(1, d.items.size); assertEquals(2000.0, d.items[0].amountSubmitted); assertEquals(2, d.audit.size)
    }

    @Test fun confirmRejectAndRecordBodies() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Rejected","method_label":"UPI","items":[{"collection_id":2,"amount_submitted":2000,"amount_confirmed":0,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","collection_amount":2000,"payment_mode":"upi","tenant_name":"Tenant Two","property_code":"R-2","payment_mode_label":"UPI"}],"audit":[{"user_type":"employee","action":"create","description":"Submitted handover ST-20261026-CCC68 of 2000.00 (upi) for employee #1","created_at":"2026-10-26 16:41:31"},{"user_type":"owner","action":"reject","description":"Rejected ST-20261026-CCC68: Not received in bank","created_at":"2026-10-26 16:41:31"}]}}"""); owner.confirm(5, 1500.0, "short")
        var b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("confirm", b["action"]!!.jsonPrimitive.content); assertEquals("1500.0", b["amount_received"]!!.jsonPrimitive.content)
        enqueue(200, """{"status":"success","message":"Success","data":{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Rejected","method_label":"UPI","items":[{"collection_id":2,"amount_submitted":2000,"amount_confirmed":0,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","collection_amount":2000,"payment_mode":"upi","tenant_name":"Tenant Two","property_code":"R-2","payment_mode_label":"UPI"}],"audit":[{"user_type":"employee","action":"create","description":"Submitted handover ST-20261026-CCC68 of 2000.00 (upi) for employee #1","created_at":"2026-10-26 16:41:31"},{"user_type":"owner","action":"reject","description":"Rejected ST-20261026-CCC68: Not received in bank","created_at":"2026-10-26 16:41:31"}]}}"""); owner.reject(5, "not received")
        b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("reject", b["action"]!!.jsonPrimitive.content); assertEquals("not received", b["reason"]!!.jsonPrimitive.content)
        enqueue(201, """{"status":"success","message":"Success","data":{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Rejected","method_label":"UPI","items":[{"collection_id":2,"amount_submitted":2000,"amount_confirmed":0,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","collection_amount":2000,"payment_mode":"upi","tenant_name":"Tenant Two","property_code":"R-2","payment_mode_label":"UPI"}],"audit":[{"user_type":"employee","action":"create","description":"Submitted handover ST-20261026-CCC68 of 2000.00 (upi) for employee #1","created_at":"2026-10-26 16:41:31"},{"user_type":"owner","action":"reject","description":"Rejected ST-20261026-CCC68: Not received in bank","created_at":"2026-10-26 16:41:31"}]}}"""); owner.record(4, HandoverInput(400.0, emptyList(), HandoverMethod.CASH, "2026-10-26", "", "office"), "KEY-1")
        val rq = server.takeRequest(); b = Json.parseToJsonElement(rq.body.readUtf8()).jsonObject
        assertEquals("KEY-1", rq.getHeader("Idempotency-Key")); assertEquals("record", b["action"]!!.jsonPrimitive.content); assertEquals("4", b["employee_id"]!!.jsonPrimitive.content)
        assertEquals("cash", b["method"]!!.jsonPrimitive.content)
    }

    @Test fun agentBalanceSubmitCancel() = runTest {
        enqueue(200, """{"status": "success", "message": "Success", "data": {"balance": {"collections": 2, "collected": "5000.00", "confirmed": "3000", "awaiting_confirmation": 0, "outstanding": 2000, "available": 2000, "last_collection_date": "2026-10-25", "last_settlement_date": "2026-10-26 22:11:31"}, "open_collections": [{"id": "2", "receipt_number": "EC-2", "collection_date": "2026-10-25", "tenant_name": "Tenant Two", "property_code": "R-2", "payment_mode": "upi", "payment_mode_label": "UPI", "amount": "2000.00", "handed_over": 0, "awaiting": 0, "available": 2000}], "methods": {"cash": "Cash", "upi": "UPI", "bank_transfer": "Bank Transfer", "other": "Other"}}}""")
        val bal = agent.balance(); assertEquals("balance", server.takeRequest().requestUrl!!.queryParameter("view"))
        assertEquals(2000.0, bal.balance.available); assertEquals(1, bal.openCollections.size); assertEquals(2000.0, bal.openCollections[0].available)

        enqueue(201, """{"status":"success","message":"Success","data":{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","emp_phone":null,"owner_name":"Test Owner","status_label":"Rejected","method_label":"UPI","items":[{"collection_id":2,"amount_submitted":2000,"amount_confirmed":0,"receipt_number":"EC-20261026221131-31FA","collection_date":"2026-10-25","collection_amount":2000,"payment_mode":"upi","tenant_name":"Tenant Two","property_code":"R-2","payment_mode_label":"UPI"}],"audit":[{"user_type":"employee","action":"create","description":"Submitted handover ST-20261026-CCC68 of 2000.00 (upi) for employee #1","created_at":"2026-10-26 16:41:31"},{"user_type":"owner","action":"reject","description":"Rejected ST-20261026-CCC68: Not received in bank","created_at":"2026-10-26 16:41:31"}]}}""")
        agent.submit(HandoverInput(null, listOf(2, 3), HandoverMethod.UPI, "2026-10-26", " UTR1 ", ""), "IDEM-9")
        val rq = server.takeRequest(); val b = Json.parseToJsonElement(rq.body.readUtf8()).jsonObject
        assertEquals("IDEM-9", rq.getHeader("Idempotency-Key")); assertEquals("submit", b["action"]!!.jsonPrimitive.content)
        assertEquals(listOf("2", "3"), b["collection_ids"]!!.jsonArray.map { it.jsonPrimitive.content }); assertNull(b["amount"], "amount not sent with selected collections")
        assertEquals("UTR1", b["reference_no"]!!.jsonPrimitive.content); assertEquals("upi", b["method"]!!.jsonPrimitive.content)

        enqueue(200, """{"status":"success","message":"Success","data":{"items":[{"id":3,"settlement_number":"ST-20261026-EFC2A","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":2000,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":null,"status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Confirmed","method_label":"Cash"},{"id":2,"settlement_number":"ST-20261026-CCC68","owner_id":1,"employee_id":1,"amount_submitted":2000,"amount_confirmed":null,"method":"upi","reference_no":"UTR998877","handover_date":"2026-10-26","remarks":null,"status":"rejected","review_note":"Not received in bank","reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Rejected","method_label":"UPI"},{"id":1,"settlement_number":"ST-20261026-3F1D0","owner_id":1,"employee_id":1,"amount_submitted":3000,"amount_confirmed":3000,"method":"cash","reference_no":null,"handover_date":"2026-10-26","remarks":"evening cash","status":"confirmed","review_note":null,"reviewed_at":"2026-10-26 22:11:31","created_by_type":"employee","created_at":"2026-10-26 22:11:31","updated_at":"2026-10-26 16:41:31","emp_name":"Agent One","item_count":1,"status_label":"Confirmed","method_label":"Cash"}],"page":1,"per_page":50,"total_items":3,"pages":1}}""")
        val l = agent.settlements("", 1)
        assertTrue(l.items.isNotEmpty() && l.items.all { it.employeeId == 1 })
        server.takeRequest()

        enqueue(409, """{"status":"error","code":"SETTLEMENT_REVIEWED","message":"This settlement was already reviewed."}""")
        try { agent.cancel(3); fail() } catch (e: ApiException) { assertEquals("SETTLEMENT_REVIEWED", e.code); assertEquals(409, e.httpStatus) }
        enqueue(422, """{"status":"error","code":"AMOUNT_EXCEEDS_BALANCE","message":"Amount ₹2,500.00 is more than the amount still to hand over (₹2,000.00)."}""")
        try { agent.submit(HandoverInput(2500.0, emptyList(), HandoverMethod.CASH, "2026-10-26", "", ""), "K"); fail() }
        catch (e: ApiException) { assertEquals("AMOUNT_EXCEEDS_BALANCE", e.code) }
    }
}
