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
    if (!whatsappUrl.isNullOrBlank() && openInWhatsApp(ctx, whatsappUrl)) return
    shareText(ctx, text, chooserTitle)
}

/** The system share sheet with plain text (fallback when WhatsApp or the tenant's number is missing). */
fun shareText(ctx: Context, text: String, chooserTitle: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { ctx.startActivity(Intent.createChooser(send, chooserTitle)) }
}

/**
 * Opens the chat in WhatsApp (or WhatsApp Business) with the message filled in; the user still taps Send.
 * Returns false when neither app is installed — then nothing was opened (1.10: no browser detour).
 */
fun openInWhatsApp(ctx: Context, waMeUrl: String): Boolean {
    for (pkg in listOf("com.whatsapp", "com.whatsapp.w4b")) {
        try {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(waMeUrl)).setPackage(pkg))
            return true
        } catch (_: ActivityNotFoundException) { /* try the next app */ }
    }
    return false
}

private var printingView: android.webkit.WebView? = null

/** Renders [html] and hands it to Android printing (printer or "Save as PDF"). */
fun printHtml(ctx: Context, jobName: String, html: String) {
    val web = android.webkit.WebView(ctx).also { printingView = it }   // kept until the print job has the document
    web.webViewClient = object : android.webkit.WebViewClient() {
        override fun onPageFinished(view: android.webkit.WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as android.print.PrintManager
            pm.print(jobName, view.createPrintDocumentAdapter(jobName), android.print.PrintAttributes.Builder().build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}
