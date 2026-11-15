package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.BillingRepositoryImpl
import com.genusit.prms.data.repository.PropertyRepositoryImpl
import com.genusit.prms.domain.model.ChargeRateUpdate
import com.genusit.prms.domain.model.ExtraCharge
import com.genusit.prms.domain.model.MeterReading
import com.genusit.prms.domain.model.NewCharge
import com.genusit.prms.domain.model.NewProperty
import com.genusit.prms.domain.model.Slab
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App 1.15: slab rules in the bill context, rate for this bill in the generate request, property types, charge rates. */
class MeterRatesApiTest {
    private lateinit var server: MockWebServer
    private lateinit var billing: BillingRepositoryImpl
    private lateinit var props: PropertyRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        billing = BillingRepositoryImpl(api, ApiCaller(json)); props = PropertyRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body))
    }

    private fun sentBody() = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject

    @Test fun contextCarriesSlabRuleAndLastReadingMonth() = runTest {
        // captured from api/v1/owner/invoices.php?action=context on the local test copy (made-up data)
        enqueue(200, """{"status":"success","message":"Success","data":{"allocation_id":2,"tenant_name":"Meena Test","property_name":"Room 102, pune","base_rent":4000,
            "billing_month":12,"billing_year":2026,"period_label":"December 2026","already_exists":false,"fixed_charges":[],
            "variable_charges":[{"tc_id":5,"name":"Electricity Slab","rate":13,"unit_label":"unit","last_reading":640,"rate_mode":"slab_progressive",
              "rate_mode_label":"Each slab at its own rate","slabs":[{"upto":5,"rate":50},{"upto":null,"rate":60}],"slabs_text":"1–5: ₹50 · Above 5: ₹60",
              "last_reading_label":"November 2026"}],"other_outstanding":26910,
            "available_charges":[{"id":3,"name":"Electricity","type":"variable","amount":0,"rate":13,"unit_label":"unit","last_reading":null,"rate_mode":"flat",
              "rate_mode_label":"One rate for all units","slabs":[],"slabs_text":null,"last_reading_label":null}]}}""")
        val ctx = billing.invoiceContext(2, 12, 2026)
        val v = ctx.variableCharges.single()
        assertEquals("slab_progressive", v.rateMode); assertEquals(listOf(Slab(5.0, 50.0), Slab(null, 60.0)), v.slabs)
        assertEquals("November 2026", v.lastReadingLabel); assertEquals(640.0, v.lastReading)
        assertEquals("flat", ctx.availableCharges.single().rateMode)
    }

    @Test fun generateSendsTheRateOnlyWhenTyped() = runTest {
        enqueue(201, """{"status":"success","message":"OK","data":{"id":9,"number":"INV-9","total":5550,"period":"July 2026"}}""")
        billing.generateInvoice(1, 7, 2026, listOf(1, 2), listOf(MeterReading(3, 1020.0, 1040.0, 15.0), MeterReading(4, 30.0, 30.0)), emptyList(),
            listOf(ExtraCharge(8, everyMonth = true, previous = 0.0, current = 5.0, rate = 9.5)))
        val b = sentBody()
        assertEquals("15.0", b["var"]!!.jsonObject["3"]!!.jsonObject["rate"]!!.jsonPrimitive.content)
        assertFalse("rate" in b["var"]!!.jsonObject["4"]!!.jsonObject, "no typed rate → server uses Charge Master")
        assertEquals("9.5", b["adhoc_var"]!!.jsonObject["8"]!!.jsonObject["rate"]!!.jsonPrimitive.content)
    }

    @Test fun chargeWithSlabsAndRateChange() = runTest {
        enqueue(201, """{"status":"success","message":"Charge added.","data":{"id":12}}""")
        billing.addCharge(NewCharge("Electricity", "variable", ratePerUnit = 13.0, unitLabel = "unit", rateMode = "slab_single",
            slabs = listOf(Slab(10.0, 13.0), Slab(null, 14.0))))
        val add = sentBody()
        assertEquals("slab_single", add["rate_mode"]!!.jsonPrimitive.content)
        assertEquals(JsonNull, add["slabs"]!!.jsonArray[1].jsonObject["upto"] ?: JsonNull)
        enqueue(201, """{"status":"success","message":"Charge added.","data":{"id":13}}""")
        billing.addCharge(NewCharge("Light", "variable", ratePerUnit = 13.0, unitLabel = "unit"))
        val flat = sentBody()
        assertFalse("rate_mode" in flat || "slabs" in flat, "a flat charge is sent exactly as before 1.15")
        enqueue(200, """{"status":"success","message":"Charge updated.","data":{"id":12}}""")
        billing.updateChargeRates(12, ChargeRateUpdate(15.0, "flat"))
        val r = server.takeRequest()
        assertEquals("PUT", r.method); assertTrue(r.path!!.endsWith("owner/charges.php?id=12"))
        val u = Json.parseToJsonElement(r.body.readUtf8()).jsonObject
        assertEquals("15.0", u["rate_per_unit"]!!.jsonPrimitive.content); assertFalse("status" in u)
    }

    @Test fun propertyTypes() = runTest {
        // captured from api/v1/owner/properties.php?types=1
        enqueue(200, """{"status":"success","message":"Success","data":[{"id":1,"name":"1 BHK Apartment","label":"1 BHK Apartment","active":true},
            {"id":3,"name":"Shop / Commercial","label":"Shop / Commercial","active":true},{"id":2,"name":"Single Room","label":"Single Room","active":true}]}""")
        val t = props.types(7)
        assertEquals(3, t.size); assertTrue(server.takeRequest().path!!.contains("types=1&include=7"))
        enqueue(201, """{"status":"success","message":"Property added.","data":{"id":5,"property_code":"Room 201"}}""")
        props.add(NewProperty("Room 201", "x", "Pune", "MH", "411001", 3000.0, 0.0, propertyTypeId = 2))
        assertEquals("2", sentBody()["property_type_id"]!!.jsonPrimitive.content)
        enqueue(200, """{"status":"success","message":"Property updated.","data":null}""")
        props.update(5, NewProperty("Room 201", "x", "Pune", "MH", "411001", 3000.0, 0.0), null)
        assertNull(sentBody()["property_type_id"], "no type chosen → the room's type is left as it is")
    }
}
