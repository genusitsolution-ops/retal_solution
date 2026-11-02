package com.genusit.prms.domain.util

import com.genusit.prms.domain.model.OpeningCategory
import com.genusit.prms.domain.model.OpeningLine
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.12 opening balance rules, checked on the phone before sending (the server checks the same again):
 *  - balance date: valid, not in the future, not before the joining date
 *  - first billing month: YYYY-MM, not before the month of the balance date, at most next month
 *  - every line: amount > 0; a month, when given, must be before the first billing month (that month is billed
 *    in Rental Solution, so it would otherwise be counted twice)
 *  - deposit collected ≤ deposit agreed
 */
object OpeningRules {
    private fun round2(v: Double): Double = java.math.BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    enum class Problem { AS_OF_DATE, AS_OF_FUTURE, AS_OF_BEFORE_JOIN, BILLING_START, BILLING_START_BEFORE_AS_OF, BILLING_START_TOO_LATE, LINE_AMOUNT, LINE_MONTH, LINE_MONTH_BILLED, DEPOSIT }

    data class Totals(val rent: Double, val other: Double) { val total: Double get() = round2(rent + other) }

    fun yearMonth(value: String): YearMonth? =
        if (Regex("^20\\d\\d-(0[1-9]|1[0-2])$").matches(value.trim())) runCatching { YearMonth.parse(value.trim()) }.getOrNull() else null

    /** Month after the balance date's month ("2026-10-31" → "2026-11"). */
    fun defaultBillingStart(asOfDate: String): String? = Dates.parse(asOfDate)?.let { YearMonth.from(it).plusMonths(1).toString() }

    /** Lines with nothing typed in are ignored (empty rows of the form). */
    fun effectiveLines(lines: List<OpeningLine>): List<OpeningLine> =
        lines.filterNot { it.amount == 0.0 && it.period.isBlank() && it.description.isBlank() }

    fun totals(lines: List<OpeningLine>): Totals {
        var rent = 0.0; var other = 0.0
        effectiveLines(lines).forEach { if (it.category == OpeningCategory.RENT) rent += it.amount else other += it.amount }
        return Totals(round2(rent), round2(other))
    }

    fun check(
        asOfDate: String, billingStart: String, lines: List<OpeningLine>, joiningDate: String? = null,
        depositAgreed: Double? = null, depositCollected: Double? = null, today: LocalDate = LocalDate.now(),
    ): Set<Problem> {
        val p = mutableSetOf<Problem>()
        val asOf = Dates.parse(asOfDate)
        if (asOf == null) p += Problem.AS_OF_DATE
        else {
            if (asOf.isAfter(today)) p += Problem.AS_OF_FUTURE
            Dates.parse(joiningDate)?.let { j -> if (asOf.isBefore(j)) p += Problem.AS_OF_BEFORE_JOIN }
        }
        val start = yearMonth(billingStart)
        if (start == null) p += Problem.BILLING_START
        else {
            if (asOf != null && start.isBefore(YearMonth.from(asOf))) p += Problem.BILLING_START_BEFORE_AS_OF
            if (start.isAfter(YearMonth.from(today).plusMonths(1))) p += Problem.BILLING_START_TOO_LATE
        }
        effectiveLines(lines).forEach { l ->
            if (l.amount <= 0.0) p += Problem.LINE_AMOUNT
            if (l.period.isNotBlank()) {
                val ym = yearMonth(l.period)
                if (ym == null) p += Problem.LINE_MONTH
                else if (start != null && !ym.isBefore(start)) p += Problem.LINE_MONTH_BILLED
            }
        }
        if (depositAgreed != null && depositCollected != null && (depositAgreed < 0 || depositCollected < 0 || depositCollected > depositAgreed + 0.005)) p += Problem.DEPOSIT
        return p
    }
}
