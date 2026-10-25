package com.genusit.prms.di

import android.util.Log
import com.genusit.prms.BuildConfig
import com.genusit.prms.platform.AppLanguage
import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.LanguageProvider
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.remote.TokenProvider
import com.genusit.prms.domain.repository.SessionRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun networkConfig(): NetworkConfig = NetworkConfig(
        baseUrl = BuildConfig.API_BASE_URL,
        userAgent = "PRMS-Android/${BuildConfig.VERSION_NAME}",
        debugLogging = BuildConfig.DEBUG,
    )

    @Provides
    @Singleton
    fun json(): Json = PrmsNetwork.json()

    @Provides
    @Singleton
    fun okHttpClient(config: NetworkConfig, session: SessionRepository): OkHttpClient =
        PrmsNetwork.okHttp(
            config = config,
            tokens = TokenProvider { session.token },
            languages = LanguageProvider { AppLanguage.current() },
            onRedirect = if (BuildConfig.DEBUG) { status, from, to ->
                // The app follows only safe same-site redirects, but each one means the server's
                // .htaccess is rewriting API URLs – it should be fixed on the server (see FIX-NOTES.md).
                Log.w("PrmsApi", "Server redirect $status: ${from.substringBefore('?')} -> ${to.substringBefore('?')}")
            } else null,
        )

    @Provides
    @Singleton
    fun retrofit(config: NetworkConfig, client: OkHttpClient, json: Json): Retrofit = PrmsNetwork.retrofit(config, client, json)

    @Provides
    @Singleton
    fun prmsApiService(retrofit: Retrofit): PrmsApiService = PrmsNetwork.service(retrofit)

    @Provides
    @Singleton
    fun apiCaller(json: Json): ApiCaller = ApiCaller(json)
}
