package com.genusit.prms.data.remote

import com.genusit.prms.domain.error.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Runs one Retrofit call and turns every outcome into either the envelope's `data` element or an
 * [ApiException] with a stable code:
 *  - IOException (DNS, refused, TLS, timeout)          → NETWORK_ERROR (the cause is kept in the message)
 *  - 3xx the redirect policy refused to follow        → SERVER_REDIRECT ("HTTP 301 → <Location>")
 *  - body that is not the JSON envelope (HTML, empty) → BAD_RESPONSE
 *  - `{"status":"error"}` or non-2xx                  → the server's code + localized message
 */
class ApiCaller(val json: Json) {

    suspend fun call(request: suspend () -> Response<ResponseBody>): JsonElement {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) { // includes SocketTimeoutException
            throw ApiException(ApiException.NETWORK, "timeout: ${e.javaClass.simpleName}: ${e.message ?: ""}", 0)
        } catch (e: IOException) {
            // keep the exception type (UnknownHost / Connect / SSLHandshake …) for diagnostics
            throw ApiException(ApiException.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "network"}", 0)
        }
        val status = response.code()
        if (status in 300..399) {
            response.errorBody()?.close()
            throw ApiException(ApiException.SERVER_REDIRECT, "HTTP $status → ${response.headers()["Location"] ?: "(no Location header)"}", status)
        }
        val text = try {
            (if (response.isSuccessful) response.body() else response.errorBody())?.use { it.string() } ?: ""
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "network"}", 0)
        }
        return parseEnvelope(status, text)
    }

    /** Envelope rules shared by every endpoint (unchanged from app 1.1). */
    fun parseEnvelope(status: Int, body: String): JsonElement {
        val root = try {
            json.parseToJsonElement(body).jsonObject
        } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, "HTTP $status", status)
        }
        val state = root["status"]?.jsonPrimitive?.contentOrNull
        if (state == "success" && status in 200..299) return root["data"] ?: JsonNull
        val code = root["code"]?.jsonPrimitive?.contentOrNull ?: "HTTP_$status"
        val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "HTTP $status"
        throw ApiException(code, msg, status, root["errors"]?.toString())
    }

    fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T =
        try {
            json.decodeFromJsonElement(serializer, element)
        } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, e.message ?: "decode", 200)
        }

    /** Call + decode in one step. */
    suspend fun <T> get(serializer: KSerializer<T>, request: suspend () -> Response<ResponseBody>): T =
        decode(serializer, call(request))

    /**
     * Binary download (documents). A 2xx response that is not JSON is the file; anything else
     * goes through the normal envelope rules so the server's error code and message are kept.
     */
    suspend fun download(request: suspend () -> Response<ResponseBody>): Pair<ByteArray, String> {
        val response = try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: InterruptedIOException) {
            throw ApiException(ApiException.NETWORK, "timeout: ${e.javaClass.simpleName}: ${e.message ?: ""}", 0)
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "network"}", 0)
        }
        val status = response.code()
        if (status in 300..399) {
            response.errorBody()?.close()
            throw ApiException(ApiException.SERVER_REDIRECT, "HTTP $status → ${response.headers()["Location"] ?: "(no Location header)"}", status)
        }
        val body = if (response.isSuccessful) response.body() else response.errorBody()
        val type = body?.contentType()?.let { "${it.type}/${it.subtype}" } ?: response.headers()["Content-Type"].orEmpty()
        val bytes = try {
            body?.use { it.bytes() } ?: ByteArray(0)
        } catch (e: IOException) {
            throw ApiException(ApiException.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "network"}", 0)
        }
        if (response.isSuccessful && bytes.isNotEmpty() && !type.contains("json")) return bytes to type
        parseEnvelope(status, String(bytes, Charsets.UTF_8))
        throw ApiException(ApiException.BAD_RESPONSE, "HTTP $status (no file)", status)
    }

    /** Call whose `data` is not needed (the envelope is still checked). */
    suspend fun run(request: suspend () -> Response<ResponseBody>) {
        call(request)
    }
}
