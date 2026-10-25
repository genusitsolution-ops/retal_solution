package com.genusit.prms.data

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.genusit.prms.BuildConfig
import com.genusit.prms.core.net.PrmsApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Manual dependency container (no DI framework needed for an app of this size). */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    val session = SessionStore(context.applicationContext)

    val api = PrmsApi(
        baseUrl = BuildConfig.API_BASE_URL,
        tokenProvider = { session.token },
        langProvider = { currentLanguage() },
        userAgent = "PRMS-Android/${BuildConfig.VERSION_NAME}",
        onRedirect = { status, from, to ->
            // The app follows only safe same-site redirects, but each one means the server's
            // .htaccess is rewriting API URLs – it should be fixed on the server (see FIX-NOTES.md).
            if (BuildConfig.DEBUG) Log.w("PrmsApi", "Server redirect $status: ${from.substringBefore('?')} -> ${to.substringBefore('?')}")
        },
    )

    init {
        appScope.launch { session.load() }
    }

    /** Server answered 401 (token expired / account deactivated): drop the session → login screen. */
    fun onAuthError() {
        appScope.launch { session.clear() }
    }

    fun logout() {
        appScope.launch {
            runCatching { api.logout() }
            session.clear()
        }
    }

    companion object {
        val SUPPORTED = listOf("en", "hi", "mr")

        /** App language: the per-app choice, else the device language when supported, else English. */
        fun currentLanguage(): String {
            val app = AppCompatDelegate.getApplicationLocales()
            val tag = if (!app.isEmpty) app[0]?.language else Locale.getDefault().language
            return if (tag in SUPPORTED) tag!! else "en"
        }

        /** Switch the whole app language (Activity is recreated by AppCompat). */
        fun setLanguage(lang: String) {
            if (lang !in SUPPORTED) return
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
        }
    }
}
