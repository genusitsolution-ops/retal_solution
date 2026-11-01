package com.genusit.prms.domain.util

import com.genusit.prms.domain.model.InvoiceDetail

/** Labels of the WhatsApp bill message in the app's language (filled from string resources). */
data class BillMessageLabels(
    val title: String,          // "Rent bill"
    val tenant: String,
    val property: String,
    val invoiceNo: String,
    val dueDate: String,
    val thisMonth: String,
    val paid: String,
    val previousBalance: String,
    val totalPayable: String,
    val viewBill: String,
    val baseRent: String,
)

/**
 * Text of a rent bill sent to the tenant on WhatsApp (1.10). Every figure comes from the server's bill:
 * this month's charges, payments, older unpaid months (carry-forward) and the total payable, plus the
 * read-only bill link when the server sent one. The owner still taps Send in WhatsApp — nothing is sent
 * automatically.
 */
object BillMessage {

    fun text(inv: InvoiceDetail, l: BillMessageLabels, dueDateText: String): String = buildString {
        inv.ownerName?.takeIf { it.isNotBlank() }?.let { appendLine("*$it*") }
        appendLine(listOfNotNull(l.title, inv.periodLabel?.takeIf { it.isNotBlank() }).joinToString(" — "))
        appendLine()
        inv.tenantName?.takeIf { it.isNotBlank() }?.let { appendLine("${l.tenant}: $it") }
        val place = listOfNotNull(inv.propertyCode, inv.city).filter { it.isNotBlank() }.joinToString(", ")
        if (place.isNotBlank()) appendLine("${l.property}: $place")
        appendLine("${l.invoiceNo}: ${inv.invoiceNumber}")
        if (!inv.dueDate.isNullOrBlank()) appendLine("${l.dueDate}: $dueDateText")
        appendLine()
        inv.items.forEach { appendLine("${if (it.itemType == "base_rent") l.baseRent else it.description}: ${Money.format(it.amount)}") }
        appendLine("${l.thisMonth}: ${Money.format(inv.totalAmount)}")
        if (inv.paidAmount > 0) appendLine("${l.paid}: ${Money.format(inv.paidAmount)}")
        inv.previousDues.forEach { appendLine("${l.previousBalance} (${it.periodLabel}): ${Money.format(it.balance)}") }
        append("*${l.totalPayable}: ${Money.format(inv.payableNow)}*")
        inv.billUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") }?.let { appendLine(); appendLine(); append("${l.viewBill}: $it") }
    }
}
