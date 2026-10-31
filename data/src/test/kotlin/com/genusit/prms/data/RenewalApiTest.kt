package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.SubscriptionRepositoryImpl
import com.genusit.prms.data.repository.TeamRepositoryImpl
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.RenewalPaymentInput
import com.genusit.prms.domain.util.UpiQr
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** App 1.9: subscription renewal — packages, payment details, manual UPI request, gateway status, employee photo version. */
class RenewalApiTest {
    private lateinit var server: MockWebServer
    private lateinit var repo: SubscriptionRepositoryImpl
    private lateinit var team: TeamRepositoryImpl

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        val config = NetworkConfig(server.url("/rentalsolutionfinal/api/v1/").toString(), "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val api = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { "T" }, { "en" }), json))
        repo = SubscriptionRepositoryImpl(api, ApiCaller(json)); team = TeamRepositoryImpl(api, ApiCaller(json))
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String, type: String = "application/json") {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", type).setBody(body))
    }

    @Test fun subscriptionScreenDataFromTheServer() = runTest {
        enqueue(200, RenewalFixtures.SUBSCRIPTION)
        val s = repo.status()
        assertEquals("trial", s.state)
        assertEquals("Starter", s.packageName)
        assertEquals(listOf("Test Basic", "Professional"), s.packages.map { it.name })
        val pro = s.packages.first { it.name == "Professional" }
        assertEquals(3538.82, pro.quote.totalAmount, 0.001)
        assertEquals(539.82, pro.quote.taxAmount, 0.001)
        assertEquals(90, pro.durationDays)
        assertEquals(listOf("Unlimited receipts", "Employee collections", "Priority support"), pro.features)
        assertEquals("payment_pending", s.displayStatus!!.code)
        assertTrue("renewed" in s.displayStatus!!.flags)
        assertNotNull(s.openInvoiceId)
        assertTrue(s.requests.isNotEmpty())
        val rejected = s.requests.first { it.status == "rejected" }
        assertEquals("Amount does not match the invoice", rejected.reviewNote)
        assertTrue(rejected.requestNo!!.matches(Regex("RR-\\d{6}-\\d{6}")))
        assertTrue(s.requests.all { it.status == "rejected" || it.reviewNote == null })
        assertNull(s.currentPackage)   // Starter is free, so it is not offered for purchase
    }

    @Test fun selectPackageReturnsInvoiceAndUpiLink() = runTest {
        enqueue(201, RenewalFixtures.SELECT_PACKAGE)
        val r = repo.selectPackage(1)
        val body = Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject
        assertEquals("select_package", body["action"]!!.jsonPrimitive.content)
        assertEquals(1, body["package_id"]!!.jsonPrimitive.int)
        assertTrue(r.invoiceId > 0)
        val p = r.payment
        assertEquals("Test Basic", p.invoice.packageName)
        assertEquals(999.0, p.invoice.totalAmount, 0.001)
        assertTrue(p.methods.manualUpi)
        assertTrue(p.screenshotRequired)
        val upi = p.upi!!
        assertEquals("rentalsol@testbank", upi.upiId)
        assertEquals(999.0, UpiQr.amount(upi.uri)!!, 0.001)
        assertEquals(p.invoice.invoiceNumber, UpiQr.param(upi.uri, "tr"))
        assertEquals("INR", UpiQr.param(upi.uri, "cu"))
        assertEquals("rentalsol@testbank", UpiQr.param(upi.uri, "pa"))
    }

    @Test fun paymentClaimsDecode() = runTest {
        enqueue(200, RenewalFixtures.PAYMENT_CLAIMS)
        val d = repo.paymentOptions(7)
        assertEquals("7", server.takeRequest().requestUrl!!.queryParameter("invoice_id"))
        assertNotNull(d.payment)
        assertEquals(d.payment!!.invoice.totalAmount, d.payment!!.upi!!.amount, 0.001)
    }

    @Test fun freePackageAndPendingErrorsKeepServerCodes() = runTest {
        enqueue(422, """{"status":"error","code":"PACKAGE_NOT_PAYABLE","message":"This package is free and cannot be bought. Choose a paid package."}""")
        try { repo.selectPackage(2); fail() } catch (e: ApiException) { assertEquals("PACKAGE_NOT_PAYABLE", e.code); assertTrue(e.message!!.contains("free")) }
        enqueue(409, """{"status":"error","code":"CLAIM_PENDING","message":"A payment for your current invoice is waiting for verification."}""")
        try { repo.selectPackage(3); fail() } catch (e: ApiException) { assertEquals("CLAIM_PENDING", e.code) }
    }

    @Test fun manualPaymentIsMultipartWithScreenshot() = runTest {
        enqueue(201, """{"status":"success","message":"Payment details submitted.","data":{"claim":{"id":9,"request_no":"RR-202610-000009","invoice_id":7,"invoice_number":"SUB-1","amount":"999.00","expected_amount":"999.00","utr":"412345678901","paid_on":"2026-10-31","status":"pending","review_note":null,"reviewed_at":null,"created_at":"2026-10-31 18:00:00","method":"upi","package_name":"Test Basic","duration_days":30,"has_screenshot":"1"}}}""")
        val f = java.io.File.createTempFile("shot", ".jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, -32, 1, 2, 3)) }
        val c = repo.submitPayment(RenewalPaymentInput(7, "412345678901", "2026-10-31", 999.0, "from app"), f)
        val rq = server.takeRequest(); val body = rq.body.readUtf8()
        assertTrue(rq.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"screenshot\"; filename=\"payment.jpg\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("name=\"amount\"") && body.contains("999.00"))
        assertTrue(body.contains("name=\"utr\"") && body.contains("412345678901"))
        assertEquals("RR-202610-000009", c!!.requestNo)
        assertEquals("pending", c.status)
        assertTrue(c.hasScreenshot)
        f.delete()
        enqueue(422, """{"status":"error","code":"SCREENSHOT_REQUIRED","message":"Upload the payment screenshot."}""")
        try { repo.submitPayment(RenewalPaymentInput(7, "412345678901", "2026-10-31", 999.0, null), java.io.File.createTempFile("shot", ".png")); fail() }
        catch (e: ApiException) { assertEquals("SCREENSHOT_REQUIRED", e.code) }
        server.takeRequest()
        enqueue(409, """{"status":"error","code":"DUPLICATE_UTR","message":"This UTR / reference number has already been submitted."}""")
        try { repo.submitPayment(RenewalPaymentInput(7, "412345678901", "2026-10-31", 999.0, null), java.io.File.createTempFile("shot", ".jpg")); fail() }
        catch (e: ApiException) { assertEquals("DUPLICATE_UTR", e.code) }
    }

    @Test fun gatewayStartAndServerCheckedStatus() = runTest {
        enqueue(201, """{"status":"success","message":"ok","data":{"id":4,"checkout_url":"https://rzp.io/i/abc","amount":3538.82}}""")
        val g = repo.startGateway(7)
        assertEquals(7, Json.parseToJsonElement(server.takeRequest().body.readUtf8()).jsonObject["invoice_id"]!!.jsonPrimitive.int)
        assertEquals("https://rzp.io/i/abc", g.checkoutUrl)
        enqueue(200, """{"status":"success","message":"Success","data":{"id":4,"status":"paid","amount":3538.82,"payment_reference":"pay_abc","invoice_status":"paid","request":{"request_no":"RR-202610-000010","status":"approved"}}}""")
        val s = repo.gatewayStatus(4)
        assertEquals("4", server.takeRequest().requestUrl!!.queryParameter("id"))
        assertEquals("paid", s.status); assertEquals("paid", s.invoiceStatus); assertEquals("approved", s.request!!.status)
        enqueue(422, """{"status":"error","code":"METHOD_DISABLED","message":"Online payment is not available. Please pay by UPI."}""")
        try { repo.startGateway(7); fail() } catch (e: ApiException) { assertEquals("METHOD_DISABLED", e.code) }
    }

    @Test fun employeeListCarriesPhotoVersion() = runTest {
        enqueue(200, """{"status":"success","message":"Success","data":[{"id":3,"full_name":"Photo Agent","email":null,"phone":"9000000401","username":"agentP","role":null,"status":"active","last_login":null,"assigned_props":0,"total_collections":0,"total_collected":"0","photo_version":"a1b2c3d4e5f6"},{"id":4,"full_name":"No Photo","status":"active","assigned_props":"1","total_collections":"0","total_collected":"0","photo_version":null}]}""")
        val list = team.employees()
        assertEquals("a1b2c3d4e5f6", list[0].photoVersion)
        assertNull(list[1].photoVersion)
    }
}
