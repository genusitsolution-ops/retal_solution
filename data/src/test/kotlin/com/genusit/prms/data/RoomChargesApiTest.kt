package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.BillingRepositoryImpl
import com.genusit.prms.data.repository.RoomChargesRepositoryImpl
import com.genusit.prms.domain.model.NewCharge
import com.genusit.prms.domain.model.RoomChargeChange
import com.genusit.prms.domain.model.RoomChargeInput
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App 1.16: Manage Charges of an allocated room (owner/room_charges.php) and the one-time / remarks fields on the bill context. */
class RoomChargesApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: RoomChargesRepositoryImpl
    private lateinit var billing: BillingRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        repo = RoomChargesRepositoryImpl(api, ApiCaller(json)); billing = BillingRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) =
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))

    @Test fun listDecodesTheServerAnswer() = runTest {
        // captured from api/v1/owner/room_charges.php on the local test copy (made-up data)
        enqueue(200, """{"status":"success","message":"Success","data":{"ready":true,"allocation":{"id":1,"status":"active","allocation_date":"2026-10-01","tenant_name":"Asha Test","property_code":"Room R1","city":"pune","base_rent":3500},
          "charges":[{"id":2,"charge_id":3,"name":"Maintenance","charge_type":"fixed","amount":300,"master_amount":300,"own_amount":false,"rate_per_unit":null,"unit_label":"unit","frequency":"monthly","start_date":"2026-12-01","end_date":null,"remarks":"Lift","state":"scheduled","state_label":"Starts 01 Dec 2026","billed_invoice_number":null,"master_active":true,"editable":true,"can_restart":false},
                     {"id":1,"charge_id":1,"name":"Light bill","charge_type":"variable","amount":null,"master_amount":null,"own_amount":false,"rate_per_unit":13,"unit_label":"unit","frequency":"monthly","start_date":null,"end_date":null,"remarks":null,"state":"active","state_label":"Active","billed_invoice_number":null,"master_active":true,"editable":true,"can_restart":false},
                     {"id":3,"charge_id":2,"name":"Water bill","charge_type":"fixed","amount":80,"master_amount":50,"own_amount":true,"rate_per_unit":null,"unit_label":"unit","frequency":"one_time","start_date":"2027-01-01","end_date":null,"remarks":null,"state":"billed","state_label":"Billed on INV-1","billed_invoice_number":"INV-1","master_active":true,"editable":false,"can_restart":true}],
          "available":[{"id":3,"name":"Maintenance","charge_type":"fixed","fixed_amount":300,"rate_per_unit":null,"unit_label":"unit","on_room":true},
                       {"id":4,"name":"Gas","charge_type":"variable","fixed_amount":null,"rate_per_unit":9,"unit_label":"kg","on_room":false}],"today":"2026-11-18"}}""")
        val d = repo.roomCharges(1)
        assertEquals("/api/v1/owner/room_charges.php?allocation_id=1", server.takeRequest().path)
        assertTrue(d.ready); assertEquals("2026-10-01", d.allocation.allocationDate); assertTrue(d.allocation.isActive)
        val m = d.charges[0]
        assertEquals(300.0, m.amount); assertEquals("scheduled", m.state); assertTrue(m.isOn); assertEquals("Lift", m.remarks)
        val light = d.charges[1]
        assertTrue(light.isMeter); assertNull(light.amount); assertEquals(13.0, light.ratePerUnit)
        val water = d.charges[2]
        assertTrue(water.isOneTime); assertTrue(water.ownAmount); assertEquals("INV-1", water.billedInvoiceNumber); assertTrue(water.canRestart); assertFalse(water.editable)
        assertEquals(listOf("Gas"), d.addable.map { it.name }, "charges already on the room are not offered again")
    }

    @Test fun addExistingChargeSendsAmountFrequencyDateRemarks() = runTest {
        enqueue(201, """{"status":"success","message":"Charge added to this room.","data":{"id":7}}""")
        val id = repo.addRoomCharge(1, RoomChargeInput(chargeId = 2, amount = 75.0, frequency = "one_time", startDate = "2027-01-01", remarks = "Tank cleaning"))
        assertEquals(7, id.id)
        val r = server.takeRequest(); val b = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("POST", r.method); assertEquals("/api/v1/owner/room_charges.php", r.path)
        assertEquals("1", b["allocation_id"]!!.jsonPrimitive.content); assertEquals("2", b["charge_id"]!!.jsonPrimitive.content)
        assertEquals("75.0", b["amount"]!!.jsonPrimitive.content); assertEquals("one_time", b["frequency"]!!.jsonPrimitive.content)
        assertEquals("2027-01-01", b["start_date"]!!.jsonPrimitive.content); assertEquals("Tank cleaning", b["remarks"]!!.jsonPrimitive.content)
        assertFalse("new_charge" in b)
    }

    @Test fun addNewChargeSendsTheChargeMasterEntry() = runTest {
        enqueue(201, """{"status":"success","message":"OK","data":{"id":8}}""")
        repo.addRoomCharge(1, RoomChargeInput(newCharge = NewCharge("Maintenance", "fixed", fixedAmount = 300.0, unitLabel = "unit"), amount = 300.0, startDate = "2026-12-01"))
        val b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        val n = b["new_charge"]!!.jsonObject
        assertEquals("Maintenance", n["name"]!!.jsonPrimitive.content); assertEquals("fixed", n["charge_type"]!!.jsonPrimitive.content)
        assertEquals("300.0", n["fixed_amount"]!!.jsonPrimitive.content); assertFalse("charge_id" in b)
        assertEquals("monthly", b["frequency"]!!.jsonPrimitive.content)
    }

    @Test fun meterChargeSendsNoAmount() = runTest {
        enqueue(201, """{"status":"success","message":"OK","data":{"id":9}}""")
        repo.addRoomCharge(1, RoomChargeInput(chargeId = 4, amount = null, startDate = "2026-11-18"))
        assertFalse("amount" in Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject)
    }

    @Test fun updateStopRestartRequests() = runTest {
        repeat(3) { enqueue(200, """{"status":"success","message":"OK","data":{"id":3}}""") }
        repo.updateRoomCharge(3, RoomChargeChange(80.0, "monthly", "2026-12-01", "x"))
        repo.stopRoomCharge(3, "2027-02-28")
        repo.restartRoomCharge(3, "2027-04-01")
        val u = server.takeRequest(); assertEquals("PUT", u.method); assertEquals("/api/v1/owner/room_charges.php?id=3", u.path)
        assertEquals("80.0", Json.parseToJsonElement(u.body.readUtf8()).jsonObject["amount"]!!.jsonPrimitive.content)
        val s = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("stop", s["action"]!!.jsonPrimitive.content); assertEquals("2027-02-28", s["end_date"]!!.jsonPrimitive.content)
        val a = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("restart", a["action"]!!.jsonPrimitive.content); assertEquals("2027-04-01", a["start_date"]!!.jsonPrimitive.content)
    }

    @Test fun serverRefusalIsShownAsItsMessage() = runTest {
        enqueue(409, """{"status":"error","code":"DUPLICATE","message":"Maintenance is already added to this room. Edit it instead."}""")
        val e = assertFailsWith<ApiException> { repo.addRoomCharge(1, RoomChargeInput(chargeId = 3, startDate = "2026-11-18")) }
        assertEquals("DUPLICATE", e.code); assertTrue(e.message!!.contains("already added"))
    }

    @Test fun contextCarriesOneTimeAndRemarks() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"allocation_id":1,"tenant_name":"Asha Test","property_name":"Room R1, pune","base_rent":3500,"billing_month":1,"billing_year":2027,"period_label":"January 2027","already_exists":false,
          "fixed_charges":[{"tc_id":2,"name":"Maintenance","amount":300,"frequency":"monthly","remarks":"Lift"},{"tc_id":3,"name":"Water bill","amount":80,"frequency":"one_time","remarks":null}],
          "variable_charges":[{"tc_id":1,"name":"Light bill","rate":13,"unit_label":"unit","last_reading":124,"last_reading_label":"December 2026","remarks":null}],"other_outstanding":0,"available_charges":[]}}""")
        val c = billing.invoiceContext(1, 1, 2027)
        assertFalse(c.fixedCharges[0].isOneTime); assertEquals("Lift", c.fixedCharges[0].remarks); assertTrue(c.fixedCharges[1].isOneTime)
    }

    @Test fun olderServerContextStillDecodes() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":{"allocation_id":1,"tenant_name":"A","property_name":"R","base_rent":3500,"billing_month":1,"billing_year":2027,"period_label":"x","already_exists":false,
          "fixed_charges":[{"tc_id":2,"name":"Maintenance","amount":300}],"variable_charges":[],"other_outstanding":0,"available_charges":[]}}""")
        val f = billing.invoiceContext(1, 1, 2027).fixedCharges.single()
        assertEquals("monthly", f.frequency); assertFalse(f.isOneTime)
    }
}
