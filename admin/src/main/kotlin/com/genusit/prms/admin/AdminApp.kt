package com.genusit.prms.admin

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.genusit.prms.admin.net.AdminApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Signed-in Super Admin. The token is kept in encrypted storage (Android Keystore), never in plain files or logs. */
class Session(context: Context) {
    private val prefs: SharedPreferences = try {
        EncryptedSharedPreferences.create(
            context, "rs_admin_session",
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Keystore unavailable (very rare): keep the session in memory only — the admin signs in again next time.
        context.getSharedPreferences("rs_admin_mem", Context.MODE_PRIVATE).also { it.edit().clear().apply() }
    }
    private val memoryOnly = prefs !is EncryptedSharedPreferences
    private var memToken: String? = null

    private val _signedIn = MutableStateFlow(token != null)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    val token: String? get() = if (memoryOnly) memToken else prefs.getString("token", null)
    val adminName: String get() = prefs.getString("name", null) ?: "Super Admin"

    fun save(token: String, name: String?) {
        if (memoryOnly) memToken = token else prefs.edit().putString("token", token).apply()
        prefs.edit().putString("name", name ?: "Super Admin").apply()
        _signedIn.value = true
    }

    fun clear() {
        memToken = null
        prefs.edit().clear().apply()
        _signedIn.value = false
    }
}

class AdminApp : Application() {
    lateinit var session: Session
        private set
    lateinit var api: AdminApi
        private set

    override fun onCreate() {
        super.onCreate()
        session = Session(this)
        api = AdminApi(BuildConfig.API_BASE_URL, { session.token })
    }
}
