package com.genusit.prms.platform

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/** App language (AppCompat per-app locales), shared by the UI and the network layer. */
object AppLanguage {
    val SUPPORTED = listOf("en", "hi", "mr")

    /** The per-app choice, else the device language when supported, else English. */
    fun current(): String {
        val app = AppCompatDelegate.getApplicationLocales()
        val tag = if (!app.isEmpty) app[0]?.language else Locale.getDefault().language
        return if (tag in SUPPORTED) tag!! else "en"
    }

    /** Switch the whole app language (the Activity is recreated by AppCompat). */
    fun set(lang: String) {
        if (lang !in SUPPORTED) return
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(lang))
    }
}
