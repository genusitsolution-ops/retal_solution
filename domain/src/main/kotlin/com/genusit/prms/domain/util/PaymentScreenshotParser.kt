package com.genusit.prms.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What was read from a UPI payment screenshot (1.10). Every field may be missing; the owner checks and corrects them. */
data class ScreenshotReading(
    val utr: String? = null,
    val amount: Double? = null,
    /** yyyy-MM-dd */
    val paidOn: String? = null,
    /** success | pending | failed */
    val status: String? = null,
    /** phonepe | gpay | paytm | bhim | other */
    val app: String? = null,
) {
    val isEmpty: Boolean get() = utr == null && amount == null && paidOn == null && status == null
}

/**
 * Reads the UTR / UPI reference, amount, date, status and app from the text that on-device OCR (ML Kit) found
 * on a payment screenshot from PhonePe, Google Pay, Paytm, BHIM or a bank app. Pure text rules, no network.
 * The result only pre-fills the form: it is never proof of payment, and the Super Admin still approves.
 */
object PaymentScreenshotParser {

    fun parse(lines: List<String>): ScreenshotReading {
        val clean = lines.map { it.replace(' ', ' ').trim() }.filter { it.isNotEmpty() }
        val all = clean.joinToString("\n")
        return ScreenshotReading(utr(clean), amount(clean), date(all), status(all), app(all))
    }

    /* ── app ── */
    internal fun app(text: String): String? {
        val t = text.lowercase(Locale.ROOT)
        return when {
            "phonepe" in t || "phone pe" in t -> "phonepe"
            "google pay" in t || Regex("\\bg ?pay\\b").containsMatchIn(t) -> "gpay"
            "paytm" in t -> "paytm"
            "bhim" in t -> "bhim"
            else -> null
        }
    }

    /* ── status ── */
    internal fun status(text: String): String? {
        val t = text.lowercase(Locale.ROOT)
        return when {
            Regex("\\b(failed|failure|declined|unsuccessful|cancelled|reversed)\\b").containsMatchIn(t) -> "failed"
            Regex("\\b(pending|processing|in progress|awaiting)\\b").containsMatchIn(t) -> "pending"
            Regex("\\b(successful|success|completed|paid successfully|payment done|money sent|sent successfully|transaction successful)\\b").containsMatchIn(t) -> "success"
            Regex("\\bpaid to\\b|\\bpaid\\b").containsMatchIn(t) -> "success"
            else -> null
        }
    }

    /* ── UTR / UPI reference ── */
    private val UTR_LABEL = Regex("(?i)\\b(utr|upi\\s*ref(erence)?\\.?\\s*(no|number|id)?|upi\\s*transaction\\s*id|upi\\s*txn\\s*id|ref(erence)?\\.?\\s*(no|number|id)|bank\\s*ref(erence)?\\s*(no|number)?|rrn)\\b")
    private val TWELVE = Regex("(?<![0-9])([0-9]{4}\\s?[0-9]{4}\\s?[0-9]{4})(?![0-9])")
    private val TXN_ID = Regex("(?i)\\btransaction\\s*id\\b")
    private val ALNUM = Regex("\\b([A-Z0-9]{10,30})\\b")

    internal fun utr(lines: List<String>): String? {
        // 1) a 12-digit number on the line of a UTR / UPI reference label, or on the next line
        for ((i, l) in lines.withIndex()) {
            if (!UTR_LABEL.containsMatchIn(l)) continue
            val after = l.substring(UTR_LABEL.find(l)!!.range.last + 1)
            TWELVE.find(after)?.let { return digits(it.value) }
            lines.getOrNull(i + 1)?.let { n -> TWELVE.find(n)?.let { return digits(it.value) } }
        }
        // 2) any stand-alone 12-digit number (UPI UTRs are 12 digits; phone numbers are 10)
        lines.firstNotNullOfOrNull { l -> TWELVE.find(l)?.takeIf { !l.contains('₹') }?.let { digits(it.value) } }?.let { return it }
        // 3) the app's transaction ID (e.g. PhonePe "T2510…"), letters and digits
        for ((i, l) in lines.withIndex()) {
            if (!TXN_ID.containsMatchIn(l)) continue
            val after = l.substring(TXN_ID.find(l)!!.range.last + 1).uppercase(Locale.ROOT)
            ALNUM.find(after.replace(" ", ""))?.let { return it.groupValues[1].take(22) }
            lines.getOrNull(i + 1)?.uppercase(Locale.ROOT)?.replace(" ", "")?.let { n -> ALNUM.find(n)?.let { return it.groupValues[1].take(22) } }
        }
        return null
    }

    private fun digits(s: String) = s.filter(Char::isDigit)

    /* ── amount ── */
    private val CURRENCY = Regex("(?i)(₹|rs\\.?|inr)\\s*([0-9][0-9,]*(\\.[0-9]{1,2})?)")
    private val AMOUNT_LABEL = Regex("(?i)\\b(amount|paid|total)\\b[^0-9]{0,12}([0-9][0-9,]*(\\.[0-9]{1,2})?)")

    internal fun amount(lines: List<String>): Double? {
        for (l in lines) CURRENCY.find(l)?.let { m -> number(m.groupValues[2])?.let { return it } }
        for (l in lines) AMOUNT_LABEL.find(l)?.let { m -> number(m.groupValues[2])?.let { return it } }
        return null
    }

    private fun number(s: String): Double? {
        val v = s.replace(",", "").toDoubleOrNull() ?: return null
        return v.takeIf { it > 0 && it < 10_000_000 }
    }

    /* ── date ── */
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    private val D_MON_Y = Regex("(?i)\\b([0-3]?[0-9])(?:st|nd|rd|th)?[\\s\\-/,]+([a-z]{3,9})[\\s\\-/,]+((?:20)?[0-9]{2})\\b")
    private val MON_D_Y = Regex("(?i)\\b([a-z]{3,9})\\s+([0-3]?[0-9])(?:st|nd|rd|th)?,?\\s+((?:20)?[0-9]{2})\\b")
    private val NUMERIC = Regex("\\b([0-3]?[0-9])[/\\-.]([01]?[0-9])[/\\-.]((?:20)?[0-9]{2})\\b")
    private val ISO = Regex("\\b(20[0-9]{2})-([01][0-9])-([0-3][0-9])\\b")

    internal fun date(text: String): String? {
        ISO.find(text)?.let { m -> make(m.groupValues[1], m.groupValues[2].toInt(), m.groupValues[3].toInt())?.let { return it } }
        D_MON_Y.find(text)?.let { m -> month(m.groupValues[2])?.let { mo -> make(m.groupValues[3], mo, m.groupValues[1].toInt())?.let { return it } } }
        MON_D_Y.find(text)?.let { m -> month(m.groupValues[1])?.let { mo -> make(m.groupValues[3], mo, m.groupValues[2].toInt())?.let { return it } } }
        NUMERIC.find(text)?.let { m -> make(m.groupValues[3], m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { return it } }
        return null
    }

    private fun month(s: String): Int? {
        val k = s.lowercase(Locale.ROOT).take(3)
        return MONTHS.indexOf(k).takeIf { it >= 0 && (s.length == 3 || s.length >= 4) }?.plus(1)
    }

    private fun make(year: String, month: Int, day: Int): String? {
        val y = if (year.length == 2) 2000 + year.toInt() else year.toInt()
        return runCatching { LocalDate.of(y, month, day).format(DateTimeFormatter.ISO_LOCAL_DATE) }.getOrNull()
    }
}
