package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.BillingRepositoryImpl
import com.genusit.prms.data.repository.PhotoRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.ExtraCharge
import com.genusit.prms.domain.model.MeterReading
import com.genusit.prms.domain.model.PhotoSubject
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

/** App 1.8: charges not yet on a tenancy in the bill context / generate request, and profile photo upload. */
class BillingPhotoApiTest {
    private lateinit var server: MockWebServer
    private lateinit var billing: BillingRepositoryImpl
    private lateinit var photos: PhotoRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        billing = BillingRepositoryImpl(api, ApiCaller(json)); photos = PhotoRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String, type: String = "application/json") {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", type).setBody(body))
    }

    @Test fun contextListsChargesNotYetOnTheTenancy() = runTest {
        // shape returned by api/v1/owner/invoices.php?action=context (captured from the PHP API on test data)
        enqueue(200, """{"status":"success","message":"Success","data":{"allocation_id":1,"tenant_name":"Test Tenant A","property_name":"R1, pune","base_rent":4000,"billing_month":10,"billing_year":2026,
            "period_label":"October 2026","already_exists":false,"fixed_charges":[],"variable_charges":[],"other_outstanding":0,
            "available_charges":[{"id":1,"name":"light bill","type":"variable","amount":0,"rate":13,"unit_label":"unit","last_reading":null},
                                 {"id":2,"name":"water","type":"fixed","amount":100,"rate":0,"unit_label":"unit","last_reading":null}]}}""")
        val ctx = billing.invoiceContext(1, 10, 2026)
        assertEquals(2, ctx.availableCharges.size)
        assertTrue(ctx.availableCharges[0].isMeter); assertEquals(13.0, ctx.availableCharges[0].rate); assertNull(ctx.availableCharges[0].lastReading)
        assertEquals(100.0, ctx.availableCharges[1].amount)
    }

    @Test fun generateSendsSkipsAndExtras() = runTest {
        enqueue(201, """{"status":"success","message":"OK","data":{"id":9,"number":"INV-9","total":4230,"period":"October 2026"}}""")
        val r = billing.generateInvoice(1, 10, 2026, listOf(5), listOf(MeterReading(6, 10.0, 25.0)), skipVarIds = listOf(7),
            extras = listOf(ExtraCharge(2, everyMonth = true), ExtraCharge(1, everyMonth = true, previous = 0.0, current = 10.0), ExtraCharge(3, everyMonth = false)))
        assertEquals(4230.0, r.total)
        val b = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        fun ids(k: String) = b[k]!!.jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("5"), ids("fixed_charge_ids")); assertEquals(listOf("7"), ids("skip_var_ids"))
        assertEquals(listOf("2", "1"), ids("attach_ids")); assertEquals(listOf("3"), ids("adhoc_fixed_ids"))
        assertEquals("10.0", b["adhoc_var"]!!.jsonObject["1"]!!.jsonObject["curr"]!!.jsonPrimitive.content)
        assertEquals("25.0", b["var"]!!.jsonObject["6"]!!.jsonObject["curr"]!!.jsonPrimitive.content)
    }

    @Test fun photoUploadDownloadAndErrors() = runTest {
        val f = java.io.File.createTempFile("photo", ".jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, 0, 1, 2, 3)); deleteOnExit() }
        enqueue(200, """{"status":"success","message":"Photo saved.","data":{"type":"tenant","id":7,"has_photo":true,"photo_version":"abc123def456"}}""")
        assertEquals("abc123def456", photos.upload(PhotoSubject.TENANT, 7, f, consent = true))
        val rq = server.takeRequest(); val body = rq.body.readUtf8()
        assertTrue(rq.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"type\"") && body.contains("tenant") && body.contains("name=\"consent\"") && body.contains("name=\"photo\"; filename=\"photo.jpg\""))

        enqueue(200, "JPEGBYTES", "image/jpeg")
        assertEquals("JPEGBYTES", String(photos.download(PhotoSubject.OWNER, null)))
        assertEquals("owner", server.takeRequest().requestUrl!!.queryParameter("type"))

        enqueue(422, """{"status":"error","code":"CONSENT_REQUIRED","message":"Confirm that the tenant agreed to the photo."}""")
        try { photos.upload(PhotoSubject.TENANT, 7, f, consent = false); fail() } catch (e: ApiException) { assertEquals("CONSENT_REQUIRED", e.code) }
        server.takeRequest()
        enqueue(404, """{"status":"error","code":"NOT_FOUND","message":"No photo."}""")
        try { photos.download(PhotoSubject.TENANT, 8); fail() } catch (e: ApiException) { assertEquals(404, e.httpStatus) }
    }
}
