package com.genusit.prms.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Response

/** Current bearer token (null when signed out). Read on every request. */
fun interface TokenProvider { fun token(): String? }

/** Current app language: "en" | "hi" | "mr" (sent as Accept-Language so server messages are localized). */
fun interface LanguageProvider { fun language(): String }

/** Adds the headers every PRMS request needs. An explicit Authorization header on a call wins. */
class HeaderInterceptor(
    private val tokens: TokenProvider,
    private val languages: LanguageProvider,
    private val userAgent: String,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val b = original.newBuilder()
            .header("Accept", "application/json")
            .header("Accept-Language", languages.language())
            .header("User-Agent", userAgent)
        if (original.header("Authorization") == null) {
            tokens.token()?.takeIf { it.isNotBlank() }?.let { b.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(b.build())
    }
}

/**
 * Follows a server redirect only when it is safe (OkHttp's own redirect following is switched off):
 *  - only to the configured host or its www / non-www twin,
 *  - never from https down to http, and never to another port,
 *  - only to a path inside the configured API folder (e.g. /rentalsolutionfinal/api/v1/…),
 *  - at most [maxHops] times, and never for 303 ("see other" means "switch to GET").
 * The request is re-sent with the same method, headers and body (incl. Idempotency-Key): a 3xx means
 * the PHP script never ran, so a payment cannot be recorded twice. This covers the usual cPanel
 * .htaccess canonical rules (add/strip "www", strip ".php"). Anything else is returned unchanged and
 * reported by [ApiCaller] as SERVER_REDIRECT. [onRedirect] lets debug builds log each followed hop.
 */
class SafeRedirectInterceptor(
    baseUrl: String,
    private val maxHops: Int = 2,
    private val onRedirect: ((status: Int, from: String, to: String) -> Unit)? = null,
) : Interceptor {
    private val base: HttpUrl = baseUrl.toHttpUrl()

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var hops = 0
        while (response.code in FOLLOW && hops < maxHops) {
            val target = safeTarget(request.url, response.header("Location")) ?: break
            onRedirect?.invoke(response.code, request.url.toString(), target.toString())
            response.close()
            request = request.newBuilder().url(target).build()
            response = chain.proceed(request)
            hops++
        }
        return response
    }

    /** Absolute target URL, or null when the redirect is not safe to follow. */
    fun safeTarget(from: HttpUrl, location: String?): HttpUrl? {
        if (location.isNullOrBlank()) return null
        val to = from.resolve(location.trim()) ?: return null
        if (to.host.lowercase().removePrefix("www.") != base.host.lowercase().removePrefix("www.")) return null
        if (base.isHttps && !to.isHttps) return null
        if (to.port != base.port) return null
        if (!to.encodedPath.startsWith(base.encodedPath)) return null
        return to
    }

    private companion object {
        val FOLLOW = setOf(301, 302, 307, 308)
    }
}
