package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.AppConfig
import com.genusit.prms.domain.model.RegisterInput
import com.genusit.prms.domain.model.RegisterResult
import com.genusit.prms.domain.repository.PublicRepository
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import javax.inject.Inject

class PublicRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
    private val config: NetworkConfig,
) : PublicRepository {
    override suspend fun appConfig(): AppConfig = caller.get(AppConfig.serializer()) { api.appConfig() }

    override suspend fun register(input: RegisterInput): RegisterResult =
        caller.get(RegisterResult.serializer()) { api.register(caller.body(RegisterInput.serializer(), input)) }

    override suspend fun image(url: String): ByteArray {
        val safe = sameServer(url) ?: throw ApiException(ApiException.BAD_RESPONSE, "image host not allowed", 0)
        return caller.download { api.download(safe) }.first
    }

    /**
     * Branding images are fetched only from the API's own server (or its www twin, which is rewritten to the
     * API host so the server's www redirect is never needed), never over plain HTTP when the API uses HTTPS.
     */
    fun sameServer(url: String): String? {
        val base = config.baseUrl.toHttpUrlOrNull() ?: return null
        val u = url.toHttpUrlOrNull() ?: return null
        if (base.isHttps && !u.isHttps) return null
        val bare = { h: String -> h.removePrefix("www.") }
        if (bare(u.host) != bare(base.host) || u.port != base.port) return null
        return u.newBuilder().host(base.host).build().toString()
    }
}
