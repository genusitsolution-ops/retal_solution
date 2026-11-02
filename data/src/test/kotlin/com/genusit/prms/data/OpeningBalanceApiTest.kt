package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.AllocationRepositoryImpl
import com.genusit.prms.data.repository.TenantAdminRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.MigrationInput
import com.genusit.prms.domain.model.OpeningInput
import com.genusit.prms.domain.model.OpeningLine
import com.genusit.prms.domain.model.TenantInput
import com.genusit.prms.domain.repository.DataTopic
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
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

/** App 1.12: existing tenant with opening balance — request bodies and the real server responses. */
class OpeningBalanceApiTest {
    private lateinit var server: MockWebServer
    private lateinit var alloc: AllocationRepositoryImpl
    private lateinit var tenants: TenantAdminRepositoryImpl
    private lateinit var caller: ApiCaller

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        caller = ApiCaller(json)
        alloc = AllocationRepositoryImpl(api, caller); tenants = TenantAdminRepositoryImpl(api, caller)
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) { server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)) }

    private val opening = OpeningInput(
        asOfDate = "2026-10-31", billingStart = "2026-11", previousMonthlyRent = 4000.0,
        lines = listOf(OpeningLine("rent", "2026-09", "", 4000.0), OpeningLine("rent", "2026-10", "", 4000.0), OpeningLine("electricity", "2026-10", "", 500.0)),
        notes = "Moved from register book", depositAgreed = 8000.0, depositCollected = 5000.0,
    )

    @Test fun existingTenantIsSentWithRoomDepositAndOpeningInOneRequest() = runTest {
        enqueue(201, """{"status":"success","message":"Existing tenant added with the opening balance.","data":{"id":7,"documents":{},"tenant_type":"existing","allocation_id":3,"opening_id":2,"opening_invoice_id":9}}""")
        val created = tenants.add(TenantInput(fullName = "Rahul", phone = "9876500001", tenantType = "existing",
            migration = MigrationInput(1, "2025-06-01", listOf(4), 8000.0, 5000.0, opening)), "k1")
        assertEquals(7, created.id)
        val req = server.takeRequest()
        assertEquals("/api/v1/owner/tenants.php", req.path)
        val b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("existing", b["tenant_type"]!!.jsonPrimitive.content)
        val m = b["migration"]!!.jsonObject
        assertEquals(1, m["property_id"]!!.jsonPrimitive.int); assertEquals("2025-06-01", m["joining_date"]!!.jsonPrimitive.content)
        assertEquals(listOf(4), m["charge_ids"]!!.jsonArray.map { it.jsonPrimitive.int })
        assertEquals(8000.0, m["deposit_agreed"]!!.jsonPrimitive.double); assertEquals(5000.0, m["deposit_collected"]!!.jsonPrimitive.double)
        val o = m["opening"]!!.jsonObject
        assertEquals("2026-10-31", o["as_of_date"]!!.jsonPrimitive.content); assertEquals("2026-11", o["billing_start"]!!.jsonPrimitive.content)
        assertEquals(listOf("rent", "rent", "electricity"), o["lines"]!!.jsonArray.map { it.jsonObject["category"]!!.jsonPrimitive.content })
        assertEquals(8500.0, o["lines"]!!.jsonArray.sumOf { it.jsonObject["amount"]!!.jsonPrimitive.double })
        assertFalse(o.containsKey("previous_paid_total"), "null optional fields are not sent")
    }

    @Test fun newTenantBodyIsUnchanged() = runTest {
        enqueue(201, """{"status":"success","message":"Tenant added.","data":{"id":8,"documents":{}}}""")
        tenants.add(TenantInput(fullName = "Asha", phone = "9876500003"), null)
        val b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertFalse(b.containsKey("tenant_type")); assertFalse(b.containsKey("migration"))
    }

    @Test fun openingBalanceIsReadFromTheServer() = runTest {
        enqueue(200, OpeningFixtures.OPENING)
        val o = alloc.openingBalance(1)
        assertEquals("1", server.takeRequest().requestUrl!!.queryParameter("id"))
        assertEquals("Opening Balance – Migrated", o.label)
        assertEquals(8500.0, o.totalOutstanding); assertEquals(5000.0, o.paid); assertEquals(3500.0, o.remaining)
        assertEquals(8000.0, o.rentOutstanding); assertEquals(500.0, o.chargesOutstanding)
        assertEquals(3000.0, o.depositPending); assertEquals("2026-11", o.billingStart); assertEquals("November 2026", o.billingStartLabel)
        assertEquals(listOf("2026-09", "2026-10", "2026-10"), o.lines.map { it.period })
        assertNull(o.previousPaidTotal); assertTrue(o.editable); assertTrue(o.depositEditable)
        assertEquals("create", o.history.single().action); assertEquals(8500.0, o.history.single().totalAfter)
    }

    @Test fun tenantDetailCarriesTypeSummaryAndOpeningEntry() = runTest {
        enqueue(200, OpeningFixtures.TENANT_DETAIL)
        val t = tenants.tenant(1)
        assertEquals("existing", t.tenantType)
        val s = assertNotNull(t.summary)
        assertTrue(s.isExisting)
        assertEquals(8500.0, s.openingBalance); assertEquals(3500.0, s.previousOutstanding); assertEquals(4500.0, s.currentMonthCharges)
        assertEquals(5000.0, s.totalPaid); assertEquals(8000.0, s.remainingOutstanding)
        assertEquals(3000.0, s.deposit!!.pending); assertEquals("2026-11-02", s.migrationDate)
        assertEquals(1, t.openingBalances.size)
        // the opening entry shows with its own label among the bills, never as "0/2026"
        assertEquals(listOf("November 2026", "Opening Balance – Migrated"), t.invoices.map { it.periodLabel })
    }

    @Test fun createAndUpdateBodies() = runTest {
        enqueue(201, OpeningFixtures.OPENING)
        alloc.createOpening(3, opening.copy(depositAgreed = null, depositCollected = null))
        var b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("create", b["action"]!!.jsonPrimitive.content); assertEquals(3, b["allocation_id"]!!.jsonPrimitive.int)
        assertFalse(b["opening"]!!.jsonObject.containsKey("deposit_agreed"))

        enqueue(200, OpeningFixtures.OPENING)
        alloc.updateOpening(1, opening, "  Water was 600  ")
        val req = server.takeRequest()
        assertEquals("/api/v1/owner/opening_balance.php", req.path)
        b = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("update", b["action"]!!.jsonPrimitive.content); assertEquals(1, b["id"]!!.jsonPrimitive.int)
        assertEquals("Water was 600", b["reason"]!!.jsonPrimitive.content)
        assertEquals(5000.0, b["opening"]!!.jsonObject["deposit_collected"]!!.jsonPrimitive.double)
    }

    @Test fun serverRefusalsArriveAsLocalizedMessages() = runTest {
        enqueue(409, """{"status":"error","code":"BELOW_PAID","message":"₹5,000.00 was already paid against the opening balance; the total cannot be lower than that."}""")
        try { alloc.updateOpening(1, opening, "fix"); fail() } catch (e: ApiException) {
            assertEquals("BELOW_PAID", e.code); assertTrue(e.message!!.contains("already paid"))
        }
    }

    @Test fun writesRefreshTenantsAllocationsAndBills() {
        val t = setOf(DataTopic.TENANTS, DataTopic.ALLOCATIONS, DataTopic.BILLING)
        assertEquals(t, caller.topicsFor("/api/v1/owner/tenants.php"))
        assertEquals(t, caller.topicsFor("/api/v1/owner/opening_balance.php"))
        assertEquals(setOf(DataTopic.TENANTS), caller.topicsFor("/api/v1/owner/tenant_document.php"))
    }
}
