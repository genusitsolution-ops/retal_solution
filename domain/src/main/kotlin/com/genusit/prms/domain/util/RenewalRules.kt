package com.genusit.prms.domain.util

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.time.LocalDate

/** Checks for the manual UPI payment form — the same rules the server applies. */
object RenewalRules {
    enum class Problem { UTR, DATE, AMOUNT, SCREENSHOT }

    /** UTR / bank reference: spaces and hyphens removed, upper case (as the server stores it). */
    fun normalizeUtr(utr: String): String = utr.replace(Regex("[\\s-]+"), "").uppercase()

    /** 6–22 letters or digits (UPI UTR is 12 digits; NEFT/IMPS references vary). */
    fun validUtr(utr: String): Boolean = normalizeUtr(utr).matches(Regex("[A-Z0-9]{6,22}"))

    /** Paid today or within the last 90 days. */
    fun validPaidOn(date: String, today: LocalDate = LocalDate.now()): Boolean {
        val d = runCatching { LocalDate.parse(date) }.getOrNull() ?: return false
        return !d.isAfter(today) && !d.isBefore(today.minusDays(90))
    }

    fun problems(utr: String, paidOn: String, amount: Double?, hasScreenshot: Boolean, screenshotRequired: Boolean,
                 today: LocalDate = LocalDate.now()): Set<Problem> = buildSet {
        if (!validUtr(utr)) add(Problem.UTR)
        if (!validPaidOn(paidOn, today)) add(Problem.DATE)
        if (amount == null || amount <= 0.0 || amount > 10_000_000.0) add(Problem.AMOUNT)
        if (screenshotRequired && !hasScreenshot) add(Problem.SCREENSHOT)
    }

    /** True when the entered amount is below the invoice total (the admin must accept the difference). */
    fun isShort(amount: Double?, total: Double): Boolean = amount != null && amount + 0.005 < total
}

/** QR code for the server's upi://pay link. The text is encoded exactly as received (amount included). */
object UpiQr {
    fun matrix(uri: String, size: Int = 600): BitMatrix {
        require(uri.startsWith("upi://pay?")) { "not a UPI payment link" }
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2, EncodeHintType.CHARACTER_SET to "UTF-8")
        return QRCodeWriter().encode(uri, BarcodeFormat.QR_CODE, size, size, hints)
    }

    /** The amount (am=) of a UPI link, e.g. 3538.82, or null. */
    fun amount(uri: String): Double? = param(uri, "am")?.toDoubleOrNull()

    fun param(uri: String, name: String): String? =
        uri.substringAfter('?', "").split('&').firstOrNull { it.startsWith("$name=") }
            ?.substringAfter('=')?.let { java.net.URLDecoder.decode(it.replace("+", "%2B"), "UTF-8") }
}

/** Plan status chips: which statuses the owner sees, in the server's order. */
object PlanStatus {
    val WARNING = setOf("expiring_soon", "payment_pending", "verification_pending", "grace")
    val DANGER = setOf("expired", "rejected", "none")
    val GOOD = setOf("active", "renewed", "trial")
}
