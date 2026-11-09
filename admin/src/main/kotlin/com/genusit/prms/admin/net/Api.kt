package com.genusit.prms.admin.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** An error answer from the server ({"status":"error","code","message"}) or a network problem (code NETWORK). */
class ApiException(val code: String, override val message: String, val http: Int = 0) : Exception(message) {
    val isSessionEnded: Boolean get() = http == 401 || code == "TOKEN_EXPIRED" || code == "UNAUTHENTICATED"
}

val AdminJson = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true; isLenient = true }

/**
 * Talks to the Rental Solution API (`…/api/v1/`). Only the Super Admin endpoints (`admin/…`) and sign-in are used.
 * The token is sent as a Bearer header; it is never logged.
 */
class AdminApi(baseUrl: String, private val tokenProvider: () -> String?, client: OkHttpClient? = null) {
    private val base: HttpUrl = baseUrl.toHttpUrl()
    private val http: OkHttpClient = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(40, TimeUnit.SECONDS).writeTimeout(40, TimeUnit.SECONDS).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private fun url(path: String, query: Map<String, String?> = emptyMap()): HttpUrl {
        val b = base.newBuilder().addPathSegments(path)
        query.forEach { (k, v) -> if (!v.isNullOrBlank()) b.addQueryParameter(k, v) }
        return b.build()
    }

    private fun request(u: HttpUrl): Request.Builder {
        val r = Request.Builder().url(u).header("Accept", "application/json").header("Accept-Language", "en")
        tokenProvider()?.let { r.header("Authorization", "Bearer $it") }
        return r
    }

    private suspend fun call(req: Request): JsonElement = withContext(Dispatchers.IO) {
        val resp = try { http.newCall(req).execute() } catch (e: IOException) {
            throw ApiException("NETWORK", "No connection to the server. Check the internet and try again.")
        }
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            val root = try { AdminJson.parseToJsonElement(text).jsonObject } catch (e: Exception) {
                throw ApiException(if (r.code >= 500) "SERVER_ERROR" else "BAD_RESPONSE",
                    if (r.code >= 500) "The server had a problem (HTTP ${r.code}). Try again later." else "Unexpected answer from the server (HTTP ${r.code}).", r.code)
            }
            val status = root["status"]?.jsonPrimitive?.contentOrNull
            if (status == "success" && r.code in 200..299) return@use root["data"] ?: JsonNull
            throw ApiException(root["code"]?.jsonPrimitive?.contentOrNull ?: "HTTP_${r.code}",
                root["message"]?.jsonPrimitive?.contentOrNull ?: "Request failed (HTTP ${r.code}).", r.code)
        }
    }

    private suspend fun <T> get(ser: KSerializer<T>, path: String, query: Map<String, String?> = emptyMap()): T =
        AdminJson.decodeFromJsonElement(ser, call(request(url(path, query)).get().build()))

    /** POST a JSON body; returns the server's message (shown to the admin after the action). */
    private suspend fun post(path: String, body: JsonObject): String = withContext(Dispatchers.IO) {
        val req = request(url(path)).post(body.toString().toRequestBody(jsonType)).build()
        val resp = try { http.newCall(req).execute() } catch (e: IOException) {
            throw ApiException("NETWORK", "No connection to the server. Check the internet and try again.")
        }
        resp.use { r ->
            val text = r.body?.string().orEmpty()
            val root = try { AdminJson.parseToJsonElement(text).jsonObject } catch (e: Exception) {
                throw ApiException("BAD_RESPONSE", "Unexpected answer from the server (HTTP ${r.code}).", r.code)
            }
            val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: ""
            if (root["status"]?.jsonPrimitive?.contentOrNull == "success" && r.code in 200..299) return@use msg.ifBlank { "Saved." }
            throw ApiException(root["code"]?.jsonPrimitive?.contentOrNull ?: "HTTP_${r.code}", msg.ifBlank { "Request failed (HTTP ${r.code})." }, r.code)
        }
    }

    // ── sign-in ──
    suspend fun login(username: String, password: String): LoginResult {
        val body = buildJsonObject { put("username", username); put("password", password) }   // no "lang": the website language preference stays as it is
        val req = request(url("auth/login.php")).removeHeader("Authorization").post(body.toString().toRequestBody(jsonType)).build()
        return AdminJson.decodeFromJsonElement(LoginResult.serializer(), call(req))
    }

    // ── admin endpoints ──
    suspend fun dashboard() = get(Dashboard.serializer(), "admin/dashboard.php")

    suspend fun signups(status: String) = get(SignupList.serializer(), "admin/signups.php", mapOf("status" to status))
    suspend fun approveSignup(id: Int) = post("admin/signups.php", buildJsonObject { put("action", "approve"); put("id", id) })
    suspend fun rejectSignup(id: Int, reason: String) = post("admin/signups.php", buildJsonObject { put("action", "reject"); put("id", id); put("reason", reason) })

    suspend fun owners(q: String, status: String) = get(OwnerList.serializer(), "admin/owners.php", mapOf("q" to q, "status" to status))
    suspend fun owner(id: Int) = get(OwnerDetail.serializer(), "admin/owners.php", mapOf("id" to id.toString()))
    suspend fun setOwnerStatus(id: Int, status: String) = post("admin/owners.php", buildJsonObject { put("action", "status"); put("id", id); put("status", status) })

    suspend fun vendors(q: String, status: String) = get(VendorList.serializer(), "admin/vendors.php", mapOf("q" to q, "status" to status))
    suspend fun vendor(id: Int) = get(VendorDetail.serializer(), "admin/vendors.php", mapOf("id" to id.toString()))
    suspend fun commissionHistory(id: Int) = get(CommissionHistory.serializer(), "admin/vendors.php", mapOf("commission_id" to id.toString()))
    suspend fun convertToVendor(ownerId: Int, percent: String, status: String, remarks: String) = post("admin/vendors.php", buildJsonObject {
        put("action", "convert"); put("owner_id", ownerId); put("commission_percent", percent); put("status", status); put("remarks", remarks)
    })
    suspend fun updateVendor(id: Int, percent: String, status: String, remarks: String) = post("admin/vendors.php", buildJsonObject {
        put("action", "update"); put("id", id); put("commission_percent", percent); put("status", status); put("remarks", remarks)
    })
    suspend fun commissionAction(action: String, commissionId: Int, fields: Map<String, String>) = post("admin/vendors.php", buildJsonObject {
        put("action", action); put("commission_id", commissionId); fields.forEach { (k, v) -> put(k, v) }
    })
    suspend fun payMonth(vendorId: Int, month: String, paidDate: String, reference: String, remarks: String, approvePending: Boolean) =
        post("admin/vendors.php", buildJsonObject {
            put("action", "pay_month"); put("id", vendorId); put("month", month); put("paid_date", paidDate)
            put("payout_reference", reference); put("remarks", remarks); put("approve_pending", approvePending)
        })

    suspend fun payments(status: String, ownerId: Int? = null) =
        get(PaymentList.serializer(), "admin/payments.php", mapOf("status" to status, "owner_id" to ownerId?.toString()))
    suspend fun payment(id: Int) = get(Payment.serializer(), "admin/payments.php", mapOf("id" to id.toString()))
    suspend fun approvePayment(id: Int, note: String, acceptDifference: Boolean) = post("admin/payments.php", buildJsonObject {
        put("action", "approve"); put("id", id); put("verified", true); put("note", note); put("accept_difference", acceptDifference)
    })
    suspend fun rejectPayment(id: Int, reason: String) = post("admin/payments.php", buildJsonObject { put("action", "reject"); put("id", id); put("reason", reason) })
    suspend fun paymentRemarks(id: Int, remarks: String) = post("admin/payments.php", buildJsonObject { put("action", "remarks"); put("id", id); put("remarks", remarks) })

    /** The payment screenshot as bytes (image or PDF) with its content type. */
    suspend fun screenshot(id: Int): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
        val req = request(url("admin/payments.php", mapOf("file" to id.toString()))).header("Accept", "*/*").get().build()
        val resp = try { http.newCall(req).execute() } catch (e: IOException) {
            throw ApiException("NETWORK", "No connection to the server. Check the internet and try again.")
        }
        resp.use { r ->
            val type = r.header("Content-Type").orEmpty()
            val bytes = r.body?.bytes() ?: ByteArray(0)
            if (r.code != 200 || type.startsWith("application/json")) {
                val msg = try { AdminJson.parseToJsonElement(String(bytes)).jsonObject["message"]?.jsonPrimitive?.contentOrNull } catch (e: Exception) { null }
                throw ApiException("FILE", msg ?: "Screenshot not available (HTTP ${r.code}).", r.code)
            }
            bytes to type
        }
    }
}
