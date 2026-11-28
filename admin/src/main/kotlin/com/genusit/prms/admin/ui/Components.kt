package com.genusit.prms.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/* ── colours (Rental Solution admin brand: deep indigo + indigo) ── */
object C {
    val Brand = Color(0xFF4F46E5)
    val BrandDark = Color(0xFF1E1B4B)
    val BrandMid = Color(0xFF312E81)
    val BrandSoft = Color(0xFFEEF2FF)
    val Success = Color(0xFF059669)
    val SuccessSoft = Color(0xFFD1FAE5)
    val Warning = Color(0xFFB45309)
    val WarningSoft = Color(0xFFFEF3C7)
    val Danger = Color(0xFFDC2626)
    val DangerSoft = Color(0xFFFEE2E2)
    val Info = Color(0xFF0369A1)
    val InfoSoft = Color(0xFFE0F2FE)
    val Teal = Color(0xFF0F766E)
    val TealSoft = Color(0xFFCCFBF1)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF64748B)
    val Border = Color(0xFFE5E7F0)
    val Bg = Color(0xFFF4F5FB)
}

val AdminColors = lightColorScheme(
    primary = C.Brand, onPrimary = Color.White, primaryContainer = C.BrandSoft, onPrimaryContainer = C.BrandDark,
    secondary = C.BrandDark, background = C.Bg, surface = Color.White, onSurface = C.Ink, onSurfaceVariant = C.Muted,
    error = C.Danger, secondaryContainer = C.BrandSoft, onSecondaryContainer = C.BrandDark, outline = Color(0xFFCBD2E1), outlineVariant = C.Border,
)

private val base = Typography()
private val AdminType = Typography(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelSmall = base.labelSmall.copy(fontWeight = FontWeight.Medium),
)

@Composable
fun AdminTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = AdminColors, typography = AdminType, content = content)

/* ── formats ── */
private val inr: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
fun money(v: Double): String = inr.format(v)
fun pct(v: Double): String = (if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')) + "%"
fun plain(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else "%.2f".format(Locale.US, v).trimEnd('0').trimEnd('.')

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
fun longToday(): String = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, dd MMMM yyyy", Locale.ENGLISH))

/* ── phone / share helpers (nothing is sent by the app itself; the admin's own apps open) ── */
fun dial(ctx: Context, phone: String) = try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } catch (e: Exception) {}
fun email(ctx: Context, to: String) = try { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$to"))) } catch (e: Exception) {}
fun whatsapp(ctx: Context, phone: String, text: String = "") = try {
    val digits = phone.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
    ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits" + if (text.isNotEmpty()) "?text=" + Uri.encode(text) else "")))
} catch (e: Exception) {}
fun shareText(ctx: Context, text: String, title: String = "Share") = try {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), title))
} catch (e: Exception) {}
fun copyText(ctx: Context, label: String, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText(label, text))
}

/* ── building blocks ── */
@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Int = 16, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) m.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else m,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, C.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) { Box(Modifier.padding(padding.dp)) { content() } }
}

/** Rounded tinted square holding an icon. */
@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Int = 38) {
    Box(Modifier.size(size.dp).clip(RoundedCornerShape(11.dp)).background(color.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = color, modifier = Modifier.size((size * .55).dp))
    }
}

/** Summary card: icon, big number, label (and an optional line under it). */
@Composable
fun StatTile(label: String, value: String, color: Color, modifier: Modifier = Modifier, icon: ImageVector? = null, sub: String? = null, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick, padding = 14) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (icon != null) IconBadge(icon, color)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = if (icon != null) C.Ink else color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = C.Muted, maxLines = 2)
            if (sub != null) Text(sub, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
        }
    }
}

/** Small figure used inside a card (e.g. commission amounts). */
@Composable
fun MiniStat(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = .08f)).padding(horizontal = 10.dp, vertical = 10.dp)) {
        Text(value, color = color, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, color = C.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 2)
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
        color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Neutral pill (vendor type, code, method…). */
@Composable
fun Tag(text: String, fg: Color = C.BrandMid, bg: Color = C.BrandSoft) =
    Text(text, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(bg).padding(horizontal = 7.dp, vertical = 2.dp))

/** Circle with the name's initials; the colour is stable per name. */
@Composable
fun Avatar(name: String, size: Int = 42) {
    val palette = listOf(C.Brand, C.Teal, C.Info, Color(0xFF7C3AED), Color(0xFFC2410C), Color(0xFF0E7490))
    val col = palette[(name.hashCode() and 0x7fffffff) % palette.size]
    val initials = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }
    Box(Modifier.size(size.dp).clip(CircleShape).background(col.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
        Text(initials, color = col, fontWeight = FontWeight.Bold, fontSize = (size * .36).sp)
    }
}

/** Avatar + title + subtitle + trailing content: the standard list row. */
@Composable
fun PersonRow(name: String, subtitle: String?, trailing: @Composable () -> Unit = {}) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Avatar(name)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, fontWeight = FontWeight.SemiBold, color = C.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = C.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
fun InfoRow(label: String, value: String?, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(.42f))
        Text(if (value.isNullOrBlank()) "—" else value, color = if (valueColor == Color.Unspecified) C.Ink else valueColor, style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(.58f))
    }
}

/** Label on the left, value right-aligned (summary cards). */
@Composable
fun MetricRow(label: String, value: String, valueColor: Color = C.Ink, dot: Color? = null, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick) else Modifier).padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) { Box(Modifier.size(9.dp).clip(CircleShape).background(dot)); Spacer(Modifier.width(10.dp)) }
        Text(label, color = C.Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.End)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = C.BrandDark, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun Chips(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) }, shape = RoundedCornerShape(50),
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = C.BrandDark, selectedLabelColor = Color.White, containerColor = Color.White))
        }
    }
}

/** Search box used on list screens (search runs on the keyboard's search key or the Search button). */
@Composable
fun SearchBox(typed: String, onType: (String) -> Unit, query: String, onSearch: (String) -> Unit, placeholder: String) {
    OutlinedTextField(typed, onType, singleLine = true, placeholder = { Text(placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = { if (typed.trim() != query) TextButton(onClick = { onSearch(typed.trim()) }) { Text("Search") } },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onSearch(typed.trim()) }),
        shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = Color.White, focusedContainerColor = Color.White,
            unfocusedBorderColor = C.Border),
        modifier = Modifier.fillMaxWidth())
}

@Composable
fun Centered(content: @Composable () -> Unit) = Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) { content() }

@Composable
fun <T> LoadContent(state: Load<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        is Load.Loading -> Box(Modifier.fillMaxWidth().padding(vertical = 60.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Load.Error -> Box(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Filled.CloudOff, null, tint = C.Muted, modifier = Modifier.size(44.dp))
                Text(state.message, color = C.Muted, textAlign = TextAlign.Center)
                Button(onClick = onRetry) { Text("Try again") }
            }
        }
        is Load.Data -> content(state.value)
    }
}

@Composable
fun Empty(text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(C.BrandSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Inbox, null, tint = C.Brand, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(text, color = C.Muted, textAlign = TextAlign.Center)
    }
}

@Composable
fun ActionRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, content = content)

@Composable
fun GreenButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    Button(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = C.Success)) { Text(text) }

@Composable
fun RedOutlineButton(text: String, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) =
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, C.Danger.copy(alpha = .5f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Danger)) { Text(text) }

/* ── dialogs ── */
@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, danger: Boolean = false, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) }, containerColor = Color.White,
        confirmButton = {
            Button(onClick = { onConfirm(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = if (danger) C.Danger else C.Brand)) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

class Field(val key: String, val label: String, val initial: String = "", val required: Boolean = false,
            val keyboard: KeyboardType = KeyboardType.Text, val multiline: Boolean = false, val hint: String? = null)

/**
 * A form in a dialog: text fields, optional checkboxes and one optional chip choice; [onSubmit] gets the values
 * (checkbox keys → "1"/"", the choice → "_choice"). [requiredWhen] makes fields required for some choices.
 */
@Composable
fun FormDialog(
    title: String, fields: List<Field>, confirm: String, onSubmit: (Map<String, String>) -> Unit, onDismiss: () -> Unit,
    intro: String? = null, checks: List<Triple<String, String, Boolean>> = emptyList(), requiredChecks: Set<String> = emptySet(),
    options: Triple<String, String, List<Pair<String, String>>>? = null, danger: Boolean = false,
    requiredWhen: Map<String, Set<String>> = emptyMap(),
) {
    val values = remember { fields.associate { it.key to mutableStateOf(it.initial) } }
    val ticks = remember { checks.associate { it.first to mutableStateOf(it.third) } }
    var choice by remember { mutableStateOf(options?.second ?: "") }
    fun required(f: Field) = f.required || (requiredWhen[f.key]?.contains(choice) == true)
    val ok = fields.all { !required(it) || values[it.key]!!.value.isNotBlank() } && requiredChecks.all { ticks[it]?.value == true }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) }, containerColor = Color.White,
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
                        label = { Text(f.label + if (required(f)) " *" else "") },
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

val MonoStyle = TextStyle(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
