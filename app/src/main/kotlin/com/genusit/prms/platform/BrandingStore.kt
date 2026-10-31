package com.genusit.prms.platform

import android.content.Context
import com.genusit.prms.domain.model.AppConfig
import com.genusit.prms.domain.usecase.GetAppConfigUseCase
import com.genusit.prms.domain.repository.PublicRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Branding the screens show: published by the Super Admin, cached on the phone, bundled fallback when absent. */
data class Branding(
    val name: String?,
    val tagline: String?,
    val showTagline: Boolean,
    val logo: File?,
    val loginLogo: File?,
    val signupEnabled: Boolean?,
    val referralEnabled: Boolean,
    val trial: com.genusit.prms.domain.model.TrialInfo? = null,
)

/**
 * Keeps the last published app branding (name, tagline, logos) in app-private storage, so the login screen
 * shows it immediately and offline. [refresh] asks the server for the current version and downloads the logos
 * only when the version changed. A failed refresh keeps the cached branding (or the built-in one).
 */
@Singleton
class BrandingStore @Inject constructor(
    @ApplicationContext context: Context,
    private val getConfig: GetAppConfigUseCase,
    private val repo: PublicRepository,
) {
    private val dir = File(context.filesDir, "branding").apply { mkdirs() }
    private val cfgFile = File(dir, "config.json")
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val lock = Mutex()
    private val _branding = MutableStateFlow(load())
    val branding: StateFlow<Branding> = _branding.asStateFlow()

    private fun logoFile(v: Int, kind: String) = File(dir, "v$v-$kind.img")

    private fun load(): Branding {
        val cfg = runCatching { json.decodeFromString(AppConfig.serializer(), cfgFile.readText()) }.getOrNull()
            ?: return Branding(null, null, true, null, null, null, false)
        return toBranding(cfg)
    }

    private fun toBranding(cfg: AppConfig) = Branding(
        name = cfg.branding.name.ifBlank { null },
        tagline = cfg.branding.tagline.ifBlank { null },
        showTagline = cfg.branding.showTagline,
        logo = logoFile(cfg.version, "logo").takeIf { it.isFile && it.length() > 0 },
        loginLogo = logoFile(cfg.version, "login").takeIf { it.isFile && it.length() > 0 }
            ?: logoFile(cfg.version, "logo").takeIf { it.isFile && it.length() > 0 },
        signupEnabled = cfg.signup.enabled,
        referralEnabled = cfg.referral.enabled,
        trial = cfg.trial?.takeIf { it.enabled },
    )

    suspend fun refresh() = lock.withLock {
        try {
            val cfg = getConfig()
            withContext(Dispatchers.IO) {
                cfg.branding.logoUrl?.let { fetch(it, logoFile(cfg.version, "logo")) }
                cfg.branding.loginLogoUrl?.takeIf { it != cfg.branding.logoUrl }?.let { fetch(it, logoFile(cfg.version, "login")) }
                val tmp = File(dir, "config.json.tmp")
                tmp.writeText(json.encodeToString(AppConfig.serializer(), cfg))
                tmp.renameTo(cfgFile)
                // older versions' logos are no longer needed
                dir.listFiles()?.filter { it.name.startsWith("v") && !it.name.startsWith("v${cfg.version}-") }?.forEach { it.delete() }
            }
            _branding.value = toBranding(cfg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // offline or server problem: keep what we have
        }
    }

    /** Downloads once per version; only decodable images are kept. */
    private suspend fun fetch(url: String, target: File) {
        if (target.isFile && target.length() > 0) return
        val bytes = repo.image(url)
        if (bytes.size > 2_000_000) return
        val probe = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, probe)
        if (probe.outWidth <= 0 || probe.outHeight <= 0) return
        val tmp = File(target.path + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(target)
    }
}
