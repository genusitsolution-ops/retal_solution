package com.genusit.prms.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object Dates {
    private val iso = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): String = LocalDate.now().format(iso)

    /** "2026-06-23" or "2026-06-23 13:08:14" → LocalDate, null when blank/invalid. */
    fun parse(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value.take(10), iso) }.getOrNull()
    }

    /**
     * Month names come from the app's string resources so they follow the app language;
     * [monthNames] must hold 12 entries (January..December).
     */
    fun display(value: String?, monthNames: List<String>): String {
        val d = parse(value) ?: return "—"
        val name = monthNames.getOrNull(d.monthValue - 1) ?: d.monthValue.toString()
        return "${d.dayOfMonth} $name ${d.year}"
    }

    fun isPast(value: String?): Boolean = parse(value)?.isBefore(LocalDate.now()) ?: false
}
