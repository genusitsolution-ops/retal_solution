package com.genusit.prms.domain.usecase

/**
 * 1.15: which app opens a WhatsApp message — WhatsApp or WhatsApp Business.
 * The owner's choice is saved on the phone; this only decides what to do with it.
 */
object WhatsAppChoice {
    const val WHATSAPP = "com.whatsapp"
    const val BUSINESS = "com.whatsapp.w4b"
    val APPS = listOf(WHATSAPP, BUSINESS)

    sealed interface Decision {
        /** Open the chat in this app straight away. */
        data class Open(val pkg: String) : Decision
        /** Let the owner pick one of [options]; [missing] = the saved app that is no longer installed. */
        data class Ask(val options: List<String>, val missing: String?) : Decision
        /** Neither app is installed: the caller keeps its old fallback (share sheet). */
        data object NoApp : Decision
    }

    /** [saved]: the saved choice (null = none yet); [installed]: which of [APPS] are installed and enabled. */
    fun decide(saved: String?, installed: Collection<String>): Decision {
        val options = APPS.filter { it in installed }
        val pref = saved?.takeIf { it in APPS }
        return when {
            options.isEmpty() -> Decision.NoApp
            pref != null && pref in options -> Decision.Open(pref)
            pref != null -> Decision.Ask(options, pref)            // saved app was uninstalled: offer the other one
            options.size == 1 -> Decision.Open(options.single())   // nothing to choose between
            else -> Decision.Ask(options, null)                    // both installed, nothing saved yet: ask once
        }
    }
}
