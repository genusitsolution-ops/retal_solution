package com.genusit.prms.ui.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.usecase.WhatsAppChoice

/**
 * 1.15: WhatsApp or WhatsApp Business for the messages the owner sends from the app.
 * The choice is saved on this phone only (SharedPreferences); nothing is sent to the server.
 */
object WhatsAppApps {
    private const val PREFS = "whatsapp"
    private const val KEY = "app"

    fun saved(ctx: Context): String? = prefs(ctx).getString(KEY, null)

    fun save(ctx: Context, pkg: String?) {
        prefs(ctx).edit().apply { if (pkg == null) remove(KEY) else putString(KEY, pkg) }.commit()
    }

    /** Which of WhatsApp / WhatsApp Business are installed and enabled on this phone. */
    fun installed(ctx: Context): Set<String> = WhatsAppChoice.APPS.filter { isInstalled(ctx, it) }.toSet()

    private fun isInstalled(ctx: Context, pkg: String): Boolean = try {
        ctx.packageManager.getApplicationInfo(pkg, 0).enabled
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    fun label(ctx: Context, pkg: String): String =
        ctx.getString(if (pkg == WhatsAppChoice.BUSINESS) R.string.wa_app_business else R.string.wa_app_whatsapp)

    /** Opens the chat (number and message unchanged) in [pkg]; false when Android could not open it. */
    fun launch(ctx: Context, url: String, pkg: String): Boolean = try {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
        if (ctx !is android.app.Activity) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        true
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    } catch (_: RuntimeException) {
        false
    }

    /** A choice waiting for the owner; shown by [WhatsAppChooserHost]. */
    data class Request(val url: String, val options: List<String>, val missing: String?)

    var pending by mutableStateOf<Request?>(null)

    /** The owner picked [pkg]: saved for next time, then the chat opens. */
    fun choose(ctx: Context, req: Request, pkg: String) {
        save(ctx, pkg)
        pending = null
        if (!launch(ctx, req.url, pkg)) Toast.makeText(ctx, ctx.getString(R.string.wa_open_failed), Toast.LENGTH_LONG).show()
    }

    /**
     * Opens [url] (wa.me link with the number and message) in the saved app, asks once when nothing is saved and both
     * apps are installed, and offers the other app when the saved one is missing. False only when neither app is installed.
     */
    fun open(ctx: Context, url: String): Boolean {
        val installed = installed(ctx)
        return when (val d = WhatsAppChoice.decide(saved(ctx), installed)) {
            is WhatsAppChoice.Decision.Open -> launch(ctx, url, d.pkg) || run {
                val others = WhatsAppChoice.APPS.filter { it in installed && it != d.pkg }
                if (others.isEmpty()) false else { pending = Request(url, others, d.pkg); true }
            }
            is WhatsAppChoice.Decision.Ask -> { pending = Request(url, d.options, d.missing); true }
            WhatsAppChoice.Decision.NoApp -> false
        }
    }

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

private val WaGreen = Color(0xFF075E54)

/** The one-time "Send with" choice (or the fallback when the saved app is not installed). Placed once at the app root. */
@Composable
fun WhatsAppChooserHost() {
    val req = WhatsAppApps.pending ?: return
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = { WhatsAppApps.pending = null },
        title = { Text(stringResource(R.string.wa_choose_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (req.missing != null) stringResource(R.string.wa_pref_missing, WhatsAppApps.label(ctx, req.missing))
                    else stringResource(R.string.wa_choose_msg),
                    style = MaterialTheme.typography.bodyMedium,
                )
                req.options.forEach { pkg ->
                    Button(
                        onClick = { WhatsAppApps.choose(ctx, req, pkg) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = WaGreen, contentColor = Color.White),
                    ) {
                        Icon(painterResource(R.drawable.ic_whatsapp), null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(WhatsAppApps.label(ctx, pkg), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { WhatsAppApps.pending = null }) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Settings: which app opens WhatsApp messages. Saved on this phone. */
@Composable
fun WhatsAppAppCard() {
    val ctx = LocalContext.current
    var saved by remember { mutableStateOf(WhatsAppApps.saved(ctx)) }
    val installed = remember { WhatsAppApps.installed(ctx) }
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_whatsapp), null, Modifier.size(22.dp), tint = Color(0xFF25D366))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.wa_app_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.wa_app_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            WhatsAppChoice.APPS.forEach { pkg ->
                val ok = pkg in installed
                Row(
                    Modifier.fillMaxWidth().selectable(selected = saved == pkg, enabled = ok, role = Role.RadioButton,
                        onClick = { WhatsAppApps.save(ctx, pkg); saved = pkg }).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = saved == pkg, onClick = null, enabled = ok)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(WhatsAppApps.label(ctx, pkg))
                        if (!ok) Text(stringResource(R.string.wa_app_not_installed), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (saved == null) Text(stringResource(R.string.wa_app_not_set), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
