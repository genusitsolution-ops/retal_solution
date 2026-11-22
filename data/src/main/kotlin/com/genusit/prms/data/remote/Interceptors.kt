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
 * 1.17 — the property the owner works in. [buildingId] null = All Properties (nothing added: the server then
 * answers for every property, exactly as for app 1.16). [onUnknown] is told when the server no longer knows the id
 * (the property was deleted on the website); the request is then repeated for All Properties.
 */
interface PropertyScopeProvider {
    fun buildingId(): Int?
    fun onUnknown(id: Int) {}
}

/**
 * Adds `building_id` to owner API requests so the server filters rooms, tenants, bills, collections, reports and
 * the dashboard by the selected property. Requests that already name a building, the property list itself
 * (owner/buildings.php) and every non-owner request are left unchanged.
 */
class PropertyScopeInterceptor(private val scope: PropertyScopeProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val id = scope.buildingId()?.takeIf { it > 0 }
        if (id == null || !applies(req.url)) return chain.proceed(req)
        val scoped = req.newBuilder().url(req.url.newBuilder().addQueryParameter(PARAM, id.toString()).build()).build()
        val res = chain.proceed(scoped)
        if (res.code == 404 && res.peekBody(4096).string().contains("PROPERTY_NOT_FOUND")) {
            res.close()
            scope.onUnknown(id)
            return chain.proceed(req)
        }
        return res
    }

    companion object {
        const val PARAM = "building_id"
        fun applies(url: HttpUrl): Boolean {
            val path = url.encodedPath
            return path.contains("/owner/") && !path.endsWith("/owner/buildings.php") && url.queryParameter(PARAM) == null
        }
    }
}

/**
 * Follows a server redirect only when it is safe (OkHttp's own redirect following is switched off):
 *  - only to the configured host or its www / non-www twin,
 *  - never from https down to http, and never to another port,
 *  - only to a path inside the configured API folder (e.g. /api/v1/…),
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
