package com.genusit.prms.core

import com.genusit.prms.core.model.InvoiceSummary
import com.genusit.prms.core.model.SubscriptionStatus
import com.genusit.prms.core.net.ApiException
import com.genusit.prms.core.net.HttpResult
import com.genusit.prms.core.net.HttpTransport
import com.genusit.prms.core.net.PrmsApi
import com.genusit.prms.core.util.Money
import com.genusit.prms.core.util.Validators
import kotlinx.coroutines.runBlocking

/** Recorded fake server. */
class FakeTransport(private val responder: (String, String, Map<String, String>, String?) -> HttpResult) : HttpTransport {
    val calls = mutableListOf<Triple<String, String, Map<String, String>>>()
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult {
        calls.add(Triple(method, url, headers))
        return responder(method, url, headers, body)
    }
}

private fun check(cond: Boolean, msg: String) { if (!cond) throw AssertionError(msg) }

/** Framework-free checks, run by JUnit (CoreTest) and by the local verifier. */
object CoreChecks {

    fun moneyFormatting() {
        check(Money.format(3269.0) == "₹3,269.00", Money.format(3269.0))
        check(Money.format(123456.5) == "₹1,23,456.50", Money.format(123456.5))
        check(Money.format(10000000.0) == "₹1,00,00,000.00", Money.format(10000000.0))
        check(Money.format(-1500.0) == "-₹1,500.00", Money.format(-1500.0))
        check(Money.format(0.005) == "₹0.01", "HALF_UP rounding")
        check(Money.compact(125000.0) == "₹1.3L", Money.compact(125000.0))
        check(Money.compact(45300.0) == "₹45.3K", Money.compact(45300.0))
        check(Money.parse("1,500.50") == 1500.5, "parse grouping")
        check(Money.parse("12.345") == null, "max 2 decimals")
        check(Money.parse("abc") == null, "invalid")
    }

    fun validators() {
        check(Validators.amount("") == Validators.AmountError.EMPTY, "empty")
        check(Validators.amount("0") == Validators.AmountError.NOT_POSITIVE, "zero")
        check(Validators.amount("-5") == Validators.AmountError.NOT_POSITIVE, "negative")
        check(Validators.amount("1001", 1000.0) == Validators.AmountError.EXCEEDS_BALANCE, "exceeds")
        check(Validators.amount("1000", 1000.0) == null, "equal ok")
        check(Validators.phone("+91 98765 43210"), "phone ok")
        check(!Validators.phone("12345"), "phone short")
    }

    /** Server sends DECIMAL as strings and flags as 0/1 — models must accept both. */
    fun lenientDecoding() {
        val api = PrmsApi("https://x/api/v1/", { null }, { "en" }, FakeTransport { _, _, _, _ -> HttpResult(200, "") })
        val inv = api.json.decodeFromString(InvoiceSummary.serializer(),
            """{"id":"2","invoice_number":"INV-1","billing_month":5,"billing_year":"2026","total_amount":"3269.00","paid_amount":2269,"balance_amount":"1000.00","status":"partial","extra":1}""")
        check(inv.id == 2 && inv.totalAmount == 3269.0 && inv.paidAmount == 2269.0 && inv.billingYear == "2026" && inv.isOpen, inv.toString())
        val sub = api.json.decodeFromString(SubscriptionStatus.serializer(),
            """{"state":"trial","is_trial":1,"restricted":"0","days_left":"59","usage":{"properties":3,"max_properties":null},"history":[{"is_trial":true,"amount_paid":"0.00"}]}""")
        check(sub.isTrial && !sub.restricted && sub.daysLeft == 59 && sub.usage.maxProperties == null && sub.history[0].isTrial, sub.toString())
    }

    fun envelopeAndHeaders() = runBlocking {
        val fake = FakeTransport { method, url, headers, _ ->
            when {
                url.contains("owner/invoices.php") && method == "POST" ->
                    HttpResult(422, """{"status":"error","code":"AMOUNT_EXCEEDS_BALANCE","message":"रक्कम जास्त आहे."}""")
                url.contains("auth/me.php") -> HttpResult(401, """{"status":"error","code":"TOKEN_EXPIRED","message":"Login again"}""")
                url.contains("owner/subscription.php") -> HttpResult(200, """{"status":"success","message":"OK","data":{"state":"expired","restricted":true}}""")
                else -> HttpResult(500, "<html>oops</html>")
            }
        }
        val api = PrmsApi("https://x/api/v1/", { "tok123" }, { "mr" }, fake)
        try {
            api.ownerRecordPayment(2, 99999.0, "cash", "2026-10-18", "", "", "key-1"); check(false, "should throw")
        } catch (e: ApiException) {
            check(e.code == "AMOUNT_EXCEEDS_BALANCE" && e.httpStatus == 422 && e.message == "रक्कम जास्त आहे.", e.toString())
        }
        val h = fake.calls.last().third
        check(h["Authorization"] == "Bearer tok123" && h["Accept-Language"] == "mr" && h["Idempotency-Key"] == "key-1", h.toString())
        try { api.me(); check(false, "should throw") } catch (e: ApiException) { check(e.isAuthError, "401") }
        check(api.subscription().restricted, "decoded")
        try { api.ownerDashboard(); check(false, "should throw") } catch (e: ApiException) { check(e.code == ApiException.BAD_RESPONSE, e.code) }
    }

    /** HTTP 301 on login (2026-10-25): only safe same-site redirects inside the API folder are followed. */
    fun redirects() = runBlocking {
        val base = "https://genusitsolution.com/rentalsolutionfinal/api/v1/"
        val ok = """{"status":"success","message":"OK","data":{"token":"t","user_type":"owner","user":{"id":1,"full_name":"A"}}}"""
        val bodies = mutableListOf<String?>()
        fun api(redirect: (String) -> HttpResult?): Pair<PrmsApi, MutableList<String>> {
            val seen = mutableListOf<String>()
            val t = FakeTransport { _, url, _, body -> bodies.add(body); redirect(url) ?: HttpResult(200, ok) }
            return PrmsApi(base, { null }, { "en" }, t, onRedirect = { _, _, to -> seen.add(to) }) to seen
        }
        // 1. bare domain -> www (canonical-host rule): followed, same method + body
        val (a1, s1) = api { u -> if (u.startsWith(base)) HttpResult(301, "<html>moved</html>", u.replace("://genusitsolution", "://www.genusitsolution")) else null }
        bodies.clear()
        check(a1.login("abhi", "pw", "en").token == "t", "www redirect followed")
        check(s1 == listOf("https://www.genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php"), s1.toString())
        check(bodies.size == 2 && bodies[0] == bodies[1] && bodies[1]!!.contains("\"username\":\"abhi\""), "POST body resent unchanged")
        // 2. ".php" stripped by .htaccess, relative Location
        val (a2, s2) = api { u -> if (u.endsWith("login.php")) HttpResult(301, "", "/rentalsolutionfinal/api/v1/auth/login") else null }
        check(a2.login("abhi", "pw", "en").token == "t" && s2.single().endsWith("/api/v1/auth/login"), s2.toString())
        // 3. refused: web login page, plain http, other site, loop
        for (bad in listOf("/rentalsolutionfinal/login.php", "http://genusitsolution.com/rentalsolutionfinal/api/v1/auth/login.php",
                           "https://evil.example/rentalsolutionfinal/api/v1/auth/login.php", null)) {
            val (a, _) = api { HttpResult(301, "", bad) }
            try { a.login("abhi", "pw", "en"); check(false, "should refuse $bad") }
            catch (e: ApiException) { check(e.code == ApiException.SERVER_REDIRECT && e.isServerMisconfig && e.httpStatus == 301, "$bad -> ${e.code}") }
        }
        val (loop, _) = api { u -> HttpResult(302, "", u) }
        try { loop.login("abhi", "pw", "en"); check(false, "loop") } catch (e: ApiException) { check(e.code == ApiException.SERVER_REDIRECT, e.code) }
        // 4. 303 is never replayed as POST
        val (a4, _) = api { u -> if (u.endsWith("login.php")) HttpResult(303, "", u + "?x=1") else null }
        try { a4.login("abhi", "pw", "en"); check(false, "303") } catch (e: ApiException) { check(e.code == ApiException.SERVER_REDIRECT, e.code) }
    }

    fun all() { moneyFormatting(); validators(); lenientDecoding(); envelopeAndHeaders(); redirects() }
}
