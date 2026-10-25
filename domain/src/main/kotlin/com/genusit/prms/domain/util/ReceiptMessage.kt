package com.genusit.prms.domain.util

import com.genusit.prms.domain.model.Receipt
import java.net.URLEncoder

/** Labels of the WhatsApp receipt in the user's language (filled from the app's string resources). */
data class ReceiptLabels(
    val title: String,
    val receiptNo: String,
    val date: String,
    val receivedFrom: String,
    val property: String,
    val amountPaid: String,
    val paymentMode: String,
    val reference: String,
    val collectedBy: String,
    val remarks: String,
    val billTotal: String,
    val thisPayment: String,
    val balanceNow: String,
    val thanks: String,
)

/**
 * Builds the WhatsApp / share text of a receipt on the device, in the app's selected language.
 * (Before 1.2 the server's pre-built share text was sent as-is, so its language did not follow the app.)
 * All amounts, numbers and names come from the real receipt; only the labels are translated.
 */
object ReceiptMessage {

    fun text(r: Receipt, l: ReceiptLabels, dateText: String, modeText: String): String = buildString {
        if (r.ownerName.isNotBlank()) appendLine("*${r.ownerName}*")
        appendLine(l.title)
        appendLine()
        appendLine("${l.receiptNo}: ${r.receiptNumber}")
        appendLine("${l.date}: $dateText")
        appendLine("${l.receivedFrom}: ${r.tenantName}")
        val place = listOf(r.propertyCode, r.city.orEmpty()).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotBlank()) appendLine("${l.property}: $place")
        appendLine("${l.amountPaid}: *${Money.format(r.amount)}*")
        appendLine("${l.paymentMode}: $modeText")
        r.referenceNumber?.takeIf { it.isNotBlank() }?.let { appendLine("${l.reference}: $it") }
        if (r.collectedBy.isNotBlank()) appendLine("${l.collectedBy}: ${r.collectedBy}")
        r.lines.forEach { ln ->
            appendLine()
            appendLine("${ln.periodLabel} · ${ln.invoiceNumber}".trim(' ', '·'))
            appendLine("${l.billTotal}: ${Money.format(ln.invoiceTotal)}")
            appendLine("${l.thisPayment}: ${Money.format(ln.applied)}")
            appendLine("${l.balanceNow}: ${Money.format(ln.balanceAfter)}")
        }
        r.remarks?.takeIf { it.isNotBlank() }?.let { appendLine(); appendLine("${l.remarks}: $it") }
        appendLine()
        append(l.thanks)
    }

    /**
     * WhatsApp "click to chat" link for the tenant's number with the message pre-filled
     * (UTF-8 percent-encoding, so Hindi/Marathi text arrives intact). Null when there is no usable number.
     */
    fun whatsAppUrl(phone: String?, text: String): String? {
        val number = normalizeIndianMobile(phone) ?: return null
        val encoded = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
        return "https://wa.me/$number?text=$encoded"
    }

    /** "98765 43210", "+91-98765-43210", "098765 43210" → "919876543210"; null when not a phone number. */
    fun normalizeIndianMobile(phone: String?): String? {
        var d = phone.orEmpty().filter(Char::isDigit)
        if (d.length == 11 && d.startsWith("0")) d = d.drop(1)
        if (d.length == 10) d = "91$d"
        return d.takeIf { it.length in 11..15 }
    }
}
