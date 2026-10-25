package com.genusit.prms.ui.common

import android.content.Context
import com.genusit.prms.R
import com.genusit.prms.domain.model.Receipt
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.ReceiptLabels
import com.genusit.prms.domain.util.ReceiptMessage

/**
 * Single entry point for sharing a receipt. The text is built from the receipt data with the
 * labels of the app's current language ([context] carries the per-app locale), so the WhatsApp
 * message is English, Hindi or Marathi exactly like the screen.
 */
object ReceiptShare {

    fun message(context: Context, r: Receipt): String {
        val res = context.resources
        val labels = ReceiptLabels(
            title = res.getString(R.string.rent_receipt),
            receiptNo = res.getString(R.string.receipt_no),
            date = res.getString(R.string.date),
            receivedFrom = res.getString(R.string.received_from),
            property = res.getString(R.string.property),
            amountPaid = res.getString(R.string.amount_paid),
            paymentMode = res.getString(R.string.payment_mode),
            reference = res.getString(R.string.wa_reference),
            collectedBy = res.getString(R.string.collected_by),
            remarks = res.getString(R.string.remarks),
            billTotal = res.getString(R.string.invoice_total),
            thisPayment = res.getString(R.string.this_payment),
            balanceNow = res.getString(R.string.balance_now),
            thanks = res.getString(R.string.receipt_thanks),
        )
        val months = res.getStringArray(R.array.months).toList()
        return ReceiptMessage.text(r, labels, Dates.display(r.date, months), modeLabel(context, r))
    }

    /** Payment mode in the app language (falls back to the server label for unknown modes). */
    private fun modeLabel(context: Context, r: Receipt): String = when (r.mode) {
        "cash" -> context.getString(R.string.mode_cash)
        "upi" -> "UPI"
        "online" -> context.getString(R.string.mode_online)
        "bank_transfer" -> context.getString(R.string.mode_bank)
        "cheque" -> context.getString(R.string.mode_cheque)
        "other" -> context.getString(R.string.mode_other)
        else -> r.modeLabel.ifBlank { r.mode }
    }

    /** Opens WhatsApp chat with the tenant (when the number is known) or the share sheet. */
    fun share(context: Context, r: Receipt) {
        val text = message(context, r)
        shareReceipt(context, ReceiptMessage.whatsAppUrl(r.tenantPhone, text), text, context.getString(R.string.share_receipt))
    }
}
