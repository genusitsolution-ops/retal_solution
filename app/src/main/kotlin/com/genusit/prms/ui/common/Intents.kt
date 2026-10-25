package com.genusit.prms.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

fun dial(ctx: Context, phone: String) {
    runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone.filter { it.isDigit() || it == '+' }))) }
}

/** Opens WhatsApp with the receipt text when available, otherwise the system share sheet. */
fun shareReceipt(ctx: Context, whatsappUrl: String?, text: String, chooserTitle: String) {
    if (!whatsappUrl.isNullOrBlank()) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl)))
            return
        } catch (_: ActivityNotFoundException) { /* fall back to share sheet */ }
    }
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { ctx.startActivity(Intent.createChooser(send, chooserTitle)) }
}
