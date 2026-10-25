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

    /** First and last day of the current month ("2026-10-01" to "2026-10-31") — the dashboard's "this month". */
    fun thisMonth(today: LocalDate = LocalDate.now()): Pair<String, String> =
        today.withDayOfMonth(1).format(iso) to today.withDayOfMonth(today.lengthOfMonth()).format(iso)

    /** From the 1st of the month [months] - 1 months ago up to today (3 → 1 Aug … 25 Oct). */
    fun lastMonths(months: Int, today: LocalDate = LocalDate.now()): Pair<String, String> =
        today.withDayOfMonth(1).minusMonths((months - 1).toLong()).format(iso) to today.format(iso)

    /** 1 January … today. */
    fun thisYear(today: LocalDate = LocalDate.now()): Pair<String, String> =
        today.withDayOfYear(1).format(iso) to today.format(iso)

    fun isPast(value: String?): Boolean = parse(value)?.isBefore(LocalDate.now()) ?: false
}
