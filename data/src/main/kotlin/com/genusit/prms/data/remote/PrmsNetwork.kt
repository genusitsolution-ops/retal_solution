package com.genusit.prms.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Everything the network layer needs to know about the server and the build. */
data class NetworkConfig(
    /** e.g. "https://example.com/rentalsolutionfinal/api/v1/" – must end with "/" */
    val baseUrl: String,
    val userAgent: String,
    /** Debug builds log method, URL, status and timing only – never headers or bodies. */
    val debugLogging: Boolean,
    val connectTimeoutSeconds: Long = 15,
    val readTimeoutSeconds: Long = 30,
) {
    init { require(baseUrl.endsWith("/")) { "baseUrl must end with /" } }
}

/**
 * Builds the HTTP stack (OkHttp + Retrofit). Kept free of DI annotations so tests can create it
 * directly; the app's Hilt module calls these functions.
 */
object PrmsNetwork {

    /** Lenient parsing: the PHP API sends numbers as strings, flags as 0/1 and extra keys. */
    @OptIn(ExperimentalSerializationApi::class)
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    fun okHttp(
        config: NetworkConfig,
        tokens: TokenProvider,
        languages: LanguageProvider,
        onRedirect: ((Int, String, String) -> Unit)? = null,
    ): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(config.connectTimeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(config.readTimeoutSeconds, TimeUnit.SECONDS)
            // redirects are handled by SafeRedirectInterceptor only
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(HeaderInterceptor(tokens, languages, config.userAgent))
            .addInterceptor(SafeRedirectInterceptor(config.baseUrl, onRedirect = onRedirect))
        if (config.debugLogging) {
            builder.addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        }
        return builder.build()
    }

    fun retrofit(config: NetworkConfig, client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json; charset=utf-8".toMediaType()))
            .build()

    fun service(retrofit: Retrofit): PrmsApiService = retrofit.create(PrmsApiService::class.java)
}
