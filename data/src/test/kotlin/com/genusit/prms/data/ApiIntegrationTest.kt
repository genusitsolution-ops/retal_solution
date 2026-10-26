package com.genusit.prms.data

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.AuthRepositoryImpl
import com.genusit.prms.data.repository.BillingRepositoryImpl
import com.genusit.prms.data.repository.SubscriptionRepositoryImpl
import com.genusit.prms.data.repository.ReportRepositoryImpl
import com.genusit.prms.data.repository.AgentRepositoryImpl
import com.genusit.prms.data.repository.TenantAdminRepositoryImpl
import com.genusit.prms.domain.model.TenantDocType
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.InvoiceSummary
import com.genusit.prms.domain.model.SubscriptionStatus
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs the real Retrofit + OkHttp stack (interceptors, converter, envelope parsing) against a
 * local MockWebServer. No live server is contacted.
 */
class ApiIntegrationTest {
    private lateinit var server: MockWebServer
    private var token: String? = null
    private val redirects = mutableListOf<String>()
    private lateinit var caller: ApiCaller
    private lateinit var api: com.genusit.prms.data.remote.PrmsApiService

    private val ok = """{"status":"success","message":"OK","data":{"token":"T","user_type":"owner","user":{"id":"1","full_name":"Abhi"}}}"""

    @BeforeTest fun setUp() {
        server = MockWebServer(); server.start()
        build(server.url("/rentalsolutionfinal/api/v1/").toString())
    }

    private fun build(baseUrl: String) {
        val config = NetworkConfig(baseUrl, "PRMS-Android/test", debugLogging = false, connectTimeoutSeconds = 5, readTimeoutSeconds = 5)
        val json = PrmsNetwork.json()
        val client = PrmsNetwork.okHttp(config, { token }, { "mr" }, onRedirect = { _, _, to -> redirects += to })
        api = PrmsNetwork.service(PrmsNetwork.retrofit(config, client, json))
        caller = ApiCaller(json)
    }

    @AfterTest fun tearDown() { server.shutdown() }

    private fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) {
        val r = MockResponse().setResponseCode(code).setBody(body)
        headers.forEach { (k, v) -> r.setHeader(k, v) }
        server.enqueue(r)
    }

    private fun take(): RecordedRequest = server.takeRequest()

    @Test fun tenantDetailHistoryAndDocuments() = runTest {
        val repo = TenantAdminRepositoryImpl(api, caller)
        token = "TOK"
        enqueue(200, """{"status":"success","message":"OK","data":{"id":"7","full_name":"Kept Tenant","has_aadhaar":true,"has_pan_doc":false,"has_account":"1","username":"kt","aadhar_number":"123412340123","pan_number":null,"invoices":[]}}""")
        val d = repo.tenant(7)
        assertTrue(d.hasAadhaar && d.hasAccount && !d.hasPanDoc); assertEquals("kt", d.username); assertEquals("123412340123", d.aadharNumber)
        server.takeRequest()

        enqueue(200, """{"status":"success","message":"OK","data":{"tenant":{"id":7,"full_name":"Kept Tenant"},"summary":{"total_billed":"1000.00","total_paid":"500.00","total_outstanding":"500.00","invoice_count":"1","paid_count":"0","unpaid_count":"1","late_payments":0},
            "deposits":{"total_agreed":"2000","total_collected":"1500","total_refunded":"0","total_pending":"500"},
            "allocations":[{"id":"3","status":"active","status_label":"Active","allocation_date":"2026-09-01","deallocation_date":null,"deposit_agreed":"2000.00","property_code":"K-201","city":"Pune","base_rent":"5000.00","tenancy_days":"54"}],
            "payments":[{"id":"9","receipt_number":"RCP-1","payment_date":"2026-10-05","amount":"500.00","payment_method_label":"UPI","reference_number":null,"invoice_number":"INV-1","period_label":"October 2026","property_code":"K-201"}],
            "invoices":[{"id":"4","invoice_number":"INV-1","period_label":"October 2026","total_amount":"1000.00","paid_amount":"500.00","balance_amount":"500.00","status":"partial","status_label":"Partial"}]}}""")
        val h = repo.history(7)
        assertEquals(500.0, h.summary.totalOutstanding); assertEquals(1500.0, h.deposits.totalCollected)
        assertEquals("K-201", h.allocations.single().propertyCode); assertEquals(54, h.allocations.single().tenancyDays)
        assertEquals("RCP-1", h.payments.single().receiptNumber); assertEquals("partial", h.invoices.single().status)
        assertTrue(server.takeRequest().path!!.endsWith("owner/tenant_history.php?id=7"))

        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 1, 2, 3)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/jpeg").setBody(okio.Buffer().write(jpeg)))
        val f = repo.document(7, TenantDocType.AADHAAR)
        assertTrue(f.bytes.contentEquals(jpeg)); assertEquals("image/jpeg", f.contentType)
        val dr = server.takeRequest()
        assertTrue(dr.path!!.endsWith("owner/tenant_document.php?id=7&type=aadhaar")); assertEquals("Bearer TOK", dr.getHeader("Authorization"))

        enqueue(404, """{"status":"error","code":"NOT_FOUND","message":"दस्तऐवज सापडला नाही."}""", "Content-Type" to "application/json")
        try { repo.document(7, TenantDocType.PAN); fail("expected 404") } catch (e: ApiException) { assertEquals("NOT_FOUND", e.code) }
        server.takeRequest()

        val tmp = java.io.File.createTempFile("doc", ".jpg").apply { writeBytes(jpeg); deleteOnExit() }
        enqueue(201, """{"status":"success","message":"Document uploaded.","data":{"tenant_id":7,"type":"pan","has_document":true}}""")
        repo.uploadDocument(7, TenantDocType.PAN, tmp, "image/jpeg", consent = true)
        val up = server.takeRequest()
        val body = up.body.readUtf8()
        assertTrue(up.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"id\"") && body.contains("name=\"type\"") && body.contains("pan") && body.contains("name=\"consent\"") && body.contains("name=\"file\"; filename=\"pan.jpg\""), body.take(400))
    }

    @Test fun draftDocumentsAndIdempotentTenantCreate() = runTest {
        val repo = TenantAdminRepositoryImpl(api, caller)
        token = "TOK"
        val tmp = java.io.File.createTempFile("doc", ".jpg").apply { writeBytes(byteArrayOf(-1, -40, -1, 0)); deleteOnExit() }
        enqueue(201, """{"status":"success","message":"Document uploaded.","data":{"draft_token":"0123456789abcdef0123456789abcdef","type":"aadhaar"}}""")
        val tok = repo.uploadDraft(TenantDocType.AADHAAR, tmp, "image/jpeg", consent = true, replaceToken = "ffffffffffffffffffffffffffffffff")
        assertEquals("0123456789abcdef0123456789abcdef", tok)
        val up = server.takeRequest().body.readUtf8()
        assertTrue(up.contains("name=\"draft\"") && up.contains("name=\"replace_draft\"") && up.contains("ffffffffffffffffffffffffffffffff") && up.contains("name=\"consent\""), up.take(300))

        enqueue(201, """{"status":"success","message":"Tenant added.","data":{"id":"41","documents":{"aadhaar":true}}}""")
        val input = com.genusit.prms.domain.model.TenantInput(fullName = "A", phone = "9000000001", aadhaarDraft = tok)
        val created = repo.add(input, "key-1")
        assertEquals(41, created.id); assertEquals(mapOf("aadhaar" to true), created.documents)
        val cr = server.takeRequest()
        assertEquals("key-1", cr.getHeader("Idempotency-Key"))
        assertTrue(cr.body.readUtf8().contains("\"aadhaar_draft\":\"$tok\""))

        enqueue(200, """{"status":"success","message":"Tenant updated.","data":null}""")
        assertEquals(emptyMap(), repo.update(41, input.copy(aadhaarDraft = null)))
        assertTrue(!server.takeRequest().body.readUtf8().contains("aadhaar_draft"))
        enqueue(200, """{"status":"success","message":"Tenant updated.","data":{"id":41,"documents":{"pan":false}}}""")
        assertEquals(mapOf("pan" to false), repo.update(41, input))
        server.takeRequest()

        enqueue(200, """{"status":"success","message":"Removed.","data":null}""")
        repo.deleteDraft(TenantDocType.PAN, tok)
        val del = server.takeRequest()
        assertEquals("DELETE", del.method); assertTrue(del.path!!.endsWith("owner/tenant_document.php?draft=$tok&type=pan"))

        enqueue(422, """{"status":"error","code":"DRAFT_EXPIRED","message":"The document photo has expired."}""")
        try { repo.add(input, "key-2"); fail("expected DRAFT_EXPIRED") } catch (e: ApiException) { assertEquals("DRAFT_EXPIRED", e.code) }
        server.takeRequest()
    }

    @Test fun publicConfigRegisterAndBrandingImage() = runTest {
        val base = server.url("/rentalsolutionfinal/api/v1/").toString()
        val cfgNet = NetworkConfig(base, "PRMS-Android/test", debugLogging = false)
        val repo = com.genusit.prms.data.repository.PublicRepositoryImpl(api, caller, cfgNet)
        enqueue(200, """{"status":"success","message":"OK","data":{"version":"3","branding":{"name":"Acme Rentals","tagline":"Homes","show_tagline":true,"logo_url":"${server.url("/rentalsolutionfinal/uploads/branding/b.png")}","login_logo_url":null},"signup":{"mode":"approval","enabled":true},"referral":{"enabled":true}}}""")
        val c = repo.appConfig()
        assertEquals(3, c.version); assertEquals("Acme Rentals", c.branding.name); assertTrue(c.signup.enabled && c.referral.enabled)
        assertTrue(server.takeRequest().path!!.endsWith("common/app_config.php"))

        enqueue(201, """{"status":"success","message":"Thank you!","data":{"status":"pending_approval","trial":null,"referral":null}}""")
        val r = repo.register(com.genusit.prms.domain.model.RegisterInput("A B", "9123456789", "a@b.in", "ab12", "Secret123", "RSABCDEF"))
        assertEquals("pending_approval", r.status); assertTrue(!r.isActive)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("\"referral_code\":\"RSABCDEF\"") && !body.contains("user_type"), body)

        enqueue(422, """{"status":"error","code":"REFERRAL_SELF","message":"An owner cannot refer themselves."}""")
        try { repo.register(com.genusit.prms.domain.model.RegisterInput("A B", "9123456789", "a@b.in", "ab12", "Secret123")); fail() } catch (e: ApiException) { assertEquals("REFERRAL_SELF", e.code) }
        server.takeRequest()

        val png = byteArrayOf(-119, 80, 78, 71, 13, 10, 26, 10)
        server.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody(okio.Buffer().write(png)))
        assertTrue(repo.image(c.branding.logoUrl!!).contentEquals(png))
        server.takeRequest()
        // other hosts are never contacted
        try { repo.image("https://evil.example.com/x.png"); fail() } catch (e: ApiException) { assertEquals(ApiException.BAD_RESPONSE, e.code) }
        assertEquals(null, repo.sameServer("ftp://x/y"))
        val https = com.genusit.prms.data.repository.PublicRepositoryImpl(api, caller, NetworkConfig("https://www.genusitsolution.com/rentalsolutionfinal/api/v1/", "t", false))
        assertEquals("https://www.genusitsolution.com/rentalsolutionfinal/uploads/branding/b.png", https.sameServer("https://genusitsolution.com/rentalsolutionfinal/uploads/branding/b.png"))
        assertEquals(null, https.sameServer("http://www.genusitsolution.com/rentalsolutionfinal/uploads/branding/b.png"))
    }

    @Test fun loginSendsJsonAndHeaders() = runTest {
        enqueue(200, ok)
        val r = AuthRepositoryImpl(api, caller).login("abhi", "secret", "mr")
        assertEquals("T", r.token); assertEquals("owner", r.userType); assertEquals(1, r.user.id)
        val req = take()
        assertEquals("POST", req.method)
        assertEquals("/rentalsolutionfinal/api/v1/auth/login.php", req.path)
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("abhi", body["username"]!!.jsonPrimitive.content)
        assertEquals("secret", body["password"]!!.jsonPrimitive.content)
        assertEquals("mr", body["lang"]!!.jsonPrimitive.content)
        assertTrue(req.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("application/json", req.getHeader("Accept"))
        assertEquals("mr", req.getHeader("Accept-Language"))
        assertEquals("PRMS-Android/test", req.getHeader("User-Agent"))
        assertNull(req.getHeader("Authorization"), "no token yet")
    }

    @Test fun bearerTokenAndIdempotencyKeyOnPayments() = runTest {
        token = "tok123"
        enqueue(422, """{"status":"error","code":"AMOUNT_EXCEEDS_BALANCE","message":"रक्कम जास्त आहे."}""")
        try {
            BillingRepositoryImpl(api, caller).recordPayment(2, 99999.0, "cash", "2026-10-18", "", "", "key-1"); fail("should throw")
        } catch (e: ApiException) {
            assertEquals("AMOUNT_EXCEEDS_BALANCE", e.code); assertEquals(422, e.httpStatus); assertEquals("रक्कम जास्त आहे.", e.message)
        }
        val req = take()
        assertEquals("/rentalsolutionfinal/api/v1/owner/invoices.php", req.path)
        assertEquals("Bearer tok123", req.getHeader("Authorization"))
        assertEquals("key-1", req.getHeader("Idempotency-Key"))
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertEquals("2", body["invoice_id"]!!.jsonPrimitive.content)
        assertEquals("cash", body["payment_method"]!!.jsonPrimitive.content)
    }

    @Test fun agentCollectionLeavesOutInvoiceWhenOldestFirst() = runTest {
        token = "t"
        enqueue(200, """{"status":"success","data":{"receipt_number":"RC-1","amount":"500.00"}}""")
        val r = AgentRepositoryImpl(api, caller).collect(5, 9, 0, 500.0, "upi", "2026-10-20", "UTR1", "", "k")
        assertEquals("RC-1", r.receiptNumber)
        val req = take()
        val body = Json.parseToJsonElement(req.body.readUtf8()).jsonObject
        assertNull(body["invoice_id"]); assertEquals("upi", body["payment_mode"]!!.jsonPrimitive.content)
        assertEquals("k", req.getHeader("Idempotency-Key"))
    }

    @Test fun errorsAreClassified() = runTest {
        token = "t"
        enqueue(401, """{"status":"error","code":"TOKEN_EXPIRED","message":"Login again"}""")
        try { AuthRepositoryImpl(api, caller).me(); fail() } catch (e: ApiException) { assertTrue(e.isAuthError) }
        enqueue(200, """{"status":"success","message":"OK","data":{"state":"expired","restricted":true}}""")
        assertTrue(SubscriptionRepositoryImpl(api, caller).status().restricted)
        enqueue(500, "<html>oops</html>")
        try { ReportRepositoryImpl(api, caller).ownerDashboard(); fail() } catch (e: ApiException) { assertEquals(ApiException.BAD_RESPONSE, e.code); assertTrue(e.isServerMisconfig) }
        enqueue(200, "")
        try { ReportRepositoryImpl(api, caller).ownerDashboard(); fail() } catch (e: ApiException) { assertEquals(ApiException.BAD_RESPONSE, e.code) }
    }

    @Test fun networkFailureIsNetworkError() = runTest {
        server.shutdown()
        try { AuthRepositoryImpl(api, caller).login("a", "b", "en"); fail() } catch (e: ApiException) {
            assertTrue(e.isNetwork, e.message)
        }
    }

    @Test fun queryParametersAndOmittedNulls() = runTest {
        token = "t"
        enqueue(200, """{"status":"success","data":[]}""")
        BillingRepositoryImpl(api, caller).invoices(null)
        assertEquals("/rentalsolutionfinal/api/v1/owner/invoices.php", take().path)
        enqueue(200, """{"status":"success","data":{"collections":[]}}""")
        ReportRepositoryImpl(api, caller).collections("2026-10-01", "2026-10-20")
        assertEquals("/rentalsolutionfinal/api/v1/owner/reports.php?type=collections&from=2026-10-01&to=2026-10-20", take().path)
        enqueue(200, """{"status":"success","data":{"state":"active"}}""")
        BillingRepositoryImpl(api, caller).invoiceContext(3, 10, 2026).let { }
        assertEquals("/rentalsolutionfinal/api/v1/owner/invoices.php?action=context&allocation_id=3&billing_month=10&billing_year=2026", take().path)
    }

    @Test fun lenientDecoding() {
        val json = PrmsNetwork.json()
        val inv = json.decodeFromString(InvoiceSummary.serializer(),
            """{"id":"2","invoice_number":"INV-1","billing_month":5,"billing_year":"2026","total_amount":"3269.00","paid_amount":2269,"balance_amount":"1000.00","status":"partial","extra":1}""")
        assertTrue(inv.id == 2 && inv.totalAmount == 3269.0 && inv.paidAmount == 2269.0 && inv.billingYear == "2026" && inv.isOpen, inv.toString())
        val sub = json.decodeFromString(SubscriptionStatus.serializer(),
            """{"state":"trial","is_trial":1,"restricted":"0","days_left":"59","usage":{"properties":3,"max_properties":null},"history":[{"is_trial":true,"amount_paid":"0.00"}]}""")
        assertTrue(sub.isTrial && !sub.restricted && sub.daysLeft == 59 && sub.usage.maxProperties == null && sub.history[0].isTrial, sub.toString())
    }

    /* ───────── HTTP 301 on login (2026-10-25) ───────── */

    @Test fun phpStrippingRedirectIsFollowedWithSameMethodAndBody() = runTest {
        enqueue(301, "<html>moved</html>", "Location" to "/rentalsolutionfinal/api/v1/auth/login")
        enqueue(200, ok)
        assertEquals("T", AuthRepositoryImpl(api, caller).login("abhi", "pw", "en").token)
        val first = take(); val second = take()
        assertEquals("/rentalsolutionfinal/api/v1/auth/login.php", first.path)
        assertEquals("POST", second.method)
        assertEquals("/rentalsolutionfinal/api/v1/auth/login", second.path)
        assertEquals(first.body.readUtf8(), second.body.readUtf8())
        assertEquals(1, redirects.size)
    }

    @Test fun idempotencyKeyIsKeptAcrossRedirect() = runTest {
        token = "t"
        enqueue(308, "", "Location" to server.url("/rentalsolutionfinal/api/v1/owner/invoices").toString())
        enqueue(200, """{"status":"success","data":{"receipt_number":"RC-9"}}""")
        BillingRepositoryImpl(api, caller).recordPayment(1, 10.0, "cash", "2026-10-20", "", "", "same-key")
        take(); val second = take()
        assertEquals("same-key", second.getHeader("Idempotency-Key"))
        assertEquals("Bearer t", second.getHeader("Authorization"))
    }

    @Test fun unsafeRedirectsAreRefused() = runTest {
        val unsafe = listOf(
            "/rentalsolutionfinal/login.php",                                    // the website's login page
            "https://evil.example/rentalsolutionfinal/api/v1/auth/login.php",    // another site
            null,                                                                 // no Location header
        )
        for (loc in unsafe) {
            if (loc != null) enqueue(301, "", "Location" to loc) else enqueue(301, "")
            try { AuthRepositoryImpl(api, caller).login("a", "b", "en"); fail("should refuse $loc") }
            catch (e: ApiException) {
                assertEquals(ApiException.SERVER_REDIRECT, e.code, "$loc")
                assertTrue(e.isServerMisconfig); assertEquals(301, e.httpStatus)
            }
            take()
        }
        // 303 means "switch to GET": never replayed as POST
        enqueue(303, "", "Location" to "/rentalsolutionfinal/api/v1/auth/login")
        try { AuthRepositoryImpl(api, caller).login("a", "b", "en"); fail() } catch (e: ApiException) { assertEquals(ApiException.SERVER_REDIRECT, e.code) }
        take()
        // loop: at most 2 hops, then refused
        repeat(3) { enqueue(302, "", "Location" to "/rentalsolutionfinal/api/v1/auth/login.php") }
        try { AuthRepositoryImpl(api, caller).login("a", "b", "en"); fail() } catch (e: ApiException) { assertEquals(ApiException.SERVER_REDIRECT, e.code) }
        assertEquals(3, server.requestCount - 4, "first request + 2 followed hops, then refused")
    }

    @Test fun httpsIsNeverDowngraded() {
        val i = com.genusit.prms.data.remote.SafeRedirectInterceptor("https://genusitsolution.com/rentalsolutionfinal/api/v1/")
        val from = okhttp3.HttpUrl.Builder().scheme("https").host("genusitsolution.com").addPathSegments("rentalsolutionfinal/api/v1/auth/login.php").build()
        assertEquals("https://www.genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php",
            i.safeTarget(from, "https://www.genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php").toString(), "www twin allowed")
        assertNull(i.safeTarget(from, "http://genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php"), "http refused")
        assertNull(i.safeTarget(from, "https://genusitsolution.com:8443/rentalsolutionfinal/api/v1/auth/login.php"), "port change refused")
        assertNull(i.safeTarget(from, "https://genusitsolution.com/other/api/v1/auth/login.php"), "outside API folder refused")
    }
}
