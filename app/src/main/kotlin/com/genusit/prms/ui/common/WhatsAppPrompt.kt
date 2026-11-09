package com.genusit.prms.ui.common

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.model.WhatsAppMessage

/**
 * 1.13: opens WhatsApp with a message prepared by the server (the owner taps Send there).
 * No valid mobile number or no WhatsApp installed → the share sheet with the same text.
 */
fun sendWhatsAppMessage(ctx: Context, msg: WhatsAppMessage) {
    val chooser = ctx.getString(R.string.share_whatsapp)
    val url = msg.url
    when {
        url.isNullOrBlank() -> { Toast.makeText(ctx, ctx.getString(R.string.wa_no_mobile), Toast.LENGTH_LONG).show(); shareText(ctx, msg.text, chooser) }
        !openInWhatsApp(ctx, url) -> { Toast.makeText(ctx, ctx.getString(R.string.wa_no_app), Toast.LENGTH_LONG).show(); shareText(ctx, msg.text, chooser) }
    }
}

/** Shows the prepared message with "Send on WhatsApp" / "Later". [onDone] runs after either choice. */
@Composable
fun WhatsAppSendDialog(title: String, msg: WhatsAppMessage, onDone: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(title) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(msg.text, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {
            Button(
                onClick = { sendWhatsAppMessage(ctx, msg); onDone() },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54), contentColor = Color.White),
            ) { Text(stringResource(R.string.send_on_whatsapp)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.later)) } },
    )
}
