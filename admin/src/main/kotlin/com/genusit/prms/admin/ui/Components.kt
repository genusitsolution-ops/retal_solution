package com.genusit.prms.admin.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ── colours ── */
object C {
    val Brand = Color(0xFF4F46E5)
    val BrandDark = Color(0xFF1E1B4B)
    val BrandSoft = Color(0xFFEEF2FF)
    val Success = Color(0xFF059669)
    val SuccessSoft = Color(0xFFD1FAE5)
    val Warning = Color(0xFFB45309)
    val WarningSoft = Color(0xFFFEF3C7)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFEE2E2)
    val Info = Color(0xFF0369A1)
    val InfoSoft = Color(0xFFE0F2FE)
    val Muted = Color(0xFF64748B)
    val Bg = Color(0xFFF4F5FB)
}

val AdminColors = lightColorScheme(
    primary = C.Brand, onPrimary = Color.White, primaryContainer = C.BrandSoft, onPrimaryContainer = C.BrandDark,
    secondary = C.BrandDark, background = C.Bg, surface = Color.White, onSurfaceVariant = C.Muted,
    error = C.Danger, secondaryContainer = C.BrandSoft, onSecondaryContainer = C.BrandDark,
)

@Composable
fun AdminTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AdminColors, content = content)

/* ── formats ── */
private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
fun money(v: Double): String = inr.format(v)
fun pct(v: Double): String = (if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')) + "%"

private val dOut = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
private val dtOut = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
fun date(s: String?): String {
    if (s.isNullOrBlank() || s.startsWith("0000")) return "—"
    return try { LocalDate.parse(s.take(10)).format(dOut) } catch (e: Exception) { s }
}
fun dateTime(s: String?): String {
    if (s.isNullOrBlank() || s.startsWith("0000")) return "—"
    return try { LocalDateTime.parse(s.replace(' ', 'T').take(19)).format(dtOut) } catch (e: Exception) { date(s) }
}
fun monthName(ym: String): String = try { LocalDate.parse("$ym-01").format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)) } catch (e: Exception) { ym }
fun today(): String = LocalDate.now().toString()

fun dial(ctx: Context, phone: String) = try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) {}
fun email(ctx: Context, to: String) = try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to"))) } catch (e: Exception) {}

/* ── building blocks ── */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) m.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick) else m,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Box(Modifier.padding(14.dp)) { content() } }
}

@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick) {
        Column {
            if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelMedium, color = C.Muted, maxLines = 2)
        }
    }
}

fun statusColors(status: String?): Pair<Color, Color> = when (status) {
    "active", "approved", "paid", "success", "confirmed" -> C.Success to C.SuccessSoft
    "pending", "inactive", "trial" -> C.Warning to C.WarningSoft
    "suspended", "rejected", "reversed", "failed", "expired" -> C.Danger to C.DangerSoft
    else -> C.Info to C.InfoSoft
}

@Composable
fun StatusChip(status: String?, label: String? = null) {
    val (fg, bg) = statusColors(status)
    Text(
        (label ?: status ?: "—").replaceFirstChar { it.uppercase() },
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun InfoRow(label: String, value: String?, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(130.dp))
        Text(if (value.isNullOrBlank()) "—" else value, color = valueColor, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = C.BrandDark, modifier = modifier.padding(top = 6.dp, bottom = 2.dp))

@Composable
fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) -> FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) }) }
    }
}

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }

@Composable
fun <T> LoadContent(state: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Centered { CircularProgressIndicator() }
        is Load.Error -> Centered {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CloudOff, null, tint = C.Muted, modifier = Modifier.size(44.dp))
                Text(state.message, color = C.Muted)
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
        is Load.Data -> content(state.value)
    }
}

@Composable
fun Empty(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Filled.Inbox, null, tint = C.Muted, modifier = Modifier.size(40.dp))
        Spacer(Modifier.height(6.dp))
        Text(text, color = C.Muted)
    }
}

@Composable
fun ActionRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
fun GreenButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = modifier, colors = ButtonDefaults.buttonColors(containerColor = C.Success)) { Text(text) }

@Composable
fun RedOutlineButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Danger)) { Text(text) }

/* ── dialogs ── */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = {
            Button(onClick = { onConfirm(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = if (danger) C.Danger else C.Brand)) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class Field(val key: String, val label: String, val initial: String = "", val required: Boolean = false,
            val keyboard: KeyboardType = KeyboardType.Text, val multiline: Boolean = false, val hint: String? = null)

/** A form in a dialog: text fields, optional checkboxes; [onSubmit] gets the values (checkbox keys → "1"/""). */
@Composable
fun FormDialog(
    title: String, fields: List<Field>, confirm: String, onSubmit: (Map<String, String>) -> Unit, onDismiss: () -> Unit,
    intro: String? = null, checks: List<Triple<String, String, Boolean>> = emptyList(), requiredChecks: Set<String> = emptySet(),
    options: Triple<String, String, List<Pair<String, String>>>? = null, danger: Boolean = false,
) {
    val values = remember { fields.associate { it.key to mutableStateOf(it.initial) } }
    val ticks = remember { checks.associate { it.first to mutableStateOf(it.third) } }
    var choice by remember { mutableStateOf(options?.second ?: "") }
    val ok = fields.all { !it.required || values[it.key]!!.value.isNotBlank() } && requiredChecks.all { ticks[it]?.value == true }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (intro != null) Text(intro, style = MaterialTheme.typography.bodyMedium, color = C.Muted)
                if (options != null) {
                    Text(options.first, style = MaterialTheme.typography.labelMedium, color = C.Muted)
                    Chips(options.third, choice) { choice = it }
                }
                fields.forEach { f ->
                    OutlinedTextField(
                        value = values[f.key]!!.value, onValueChange = { values[f.key]!!.value = it },
                        label = { Text(f.label + if (f.required) " *" else "") },
                        placeholder = f.hint?.let { { Text(it) } },
                        singleLine = !f.multiline, minLines = if (f.multiline) 2 else 1,
                        keyboardOptions = KeyboardOptions(keyboardType = f.keyboard), modifier = Modifier.fillMaxWidth(),
                    )
                }
                checks.forEach { (key, label, _) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { ticks[key]!!.value = !ticks[key]!!.value }) {
                        Checkbox(checked = ticks[key]!!.value, onCheckedChange = { ticks[key]!!.value = it })
                        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                }
            }
        },
        confirmButton = {
            Button(enabled = ok, colors = ButtonDefaults.buttonColors(containerColor = if (danger) C.Danger else C.Brand), onClick = {
                val out = values.mapValues { it.value.value.trim() }.toMutableMap()
                ticks.forEach { (k, v) -> out[k] = if (v.value) "1" else "" }
                if (options != null) out["_choice"] = choice
                onSubmit(out); onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun SmallText(text: String, color: Color = C.Muted) = Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 3, overflow = TextOverflow.Ellipsis)
