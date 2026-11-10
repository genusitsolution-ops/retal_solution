package com.genusit.prms.ui.common

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.model.SubscriptionBrief
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.theme.Palette

/* ───────── scaffolding ───────── */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    /** Small line under the title (e.g. the role on a home screen). */
    subtitle: String? = null,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // the outer role shell already applies the navigation-bar inset
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge, color = Palette.Ink)
                        if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = Palette.Ink)
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                    actionIconContentColor = Palette.Brand,
                ),
            )
        },
        content = content,
    )
}

/** Renders loading / error / data for a screen's [UiState]. */
@Composable
fun <T> StateContent(state: UiState<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    // short cross-fade between loading / error / content (keyed by the kind of state, not the data)
    val kind = when (state) { is UiState.Loading -> 0; is UiState.Error -> 1; is UiState.Data -> 2 }
    Crossfade(targetState = kind, animationSpec = tween(220), label = "state") { k ->
        when (k) {
            0 -> LoadingBox(modifier)
            1 -> (state as? UiState.Error)?.let { ErrorBox(it.error, onRetry, modifier) }
            else -> (state as? UiState.Data)?.let { s ->
                Box(modifier.fillMaxSize()) {
                    content(s.value)
                    if (s.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Palette.Teal, trackColor = Palette.TealSoft)
                }
            }
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.Brand, strokeWidth = 3.dp) }
}

@Composable
fun errorText(e: UiError): String = when {
    e.isNetwork -> stringResource(R.string.error_network)
    e.message.isNullOrBlank() -> stringResource(R.string.error_generic)
    else -> e.message
}

@Composable
fun ErrorBox(error: UiError, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(84.dp).clip(CircleShape).background(Palette.WarningSoft), contentAlignment = Alignment.Center) {
            Icon(if (error.isNetwork) Icons.Filled.CloudOff else Icons.Filled.WarningAmber, null, tint = Palette.Warning, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(errorText(error), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(onClick = onRetry, shape = RoundedCornerShape(14.dp), modifier = Modifier.heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry))
        }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Filled.Inbox, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(76.dp).clip(RoundedCornerShape(24.dp)).background(Palette.TealSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Teal, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/* ───────── cards ───────── */

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val base = modifier.fillMaxWidth().shadow(5.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
    val colors = CardDefaults.cardColors(containerColor = Palette.Card)
    val border = BorderStroke(1.dp, Palette.Line.copy(alpha = .8f))
    if (onClick != null) {
        // Material card with onClick: ripple, button semantics for TalkBack, and a gentle press scale
        val interaction = remember { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val scale by animateFloatAsState(if (pressed) .97f else 1f, animationSpec = tween(120), label = "press")
        Card(
            onClick = onClick, interactionSource = interaction,
            modifier = base.graphicsLayer { scaleX = scale; scaleY = scale },
            shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp),
        ) { Box(Modifier.padding(16.dp)) { content() } }
    } else {
        Card(modifier = base, shape = shape, colors = colors, border = border, elevation = CardDefaults.cardElevation(0.dp)) {
            Box(Modifier.padding(16.dp)) { content() }
        }
    }
}

/** Small "opens something" hint shown on clickable dashboard cards. */
@Composable
fun OpenHint(tint: Color = Palette.Muted) {
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = tint.copy(alpha = .8f), modifier = Modifier.size(20.dp))
}

/** Navy-to-teal money card with a gold sweep — the main figure of each dashboard. */
@Composable
fun WalletCard(label: String, amount: Double, footer: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(24.dp)
    val base = modifier.fillMaxWidth().shadow(12.dp, shape, ambientColor = Palette.BrandDark, spotColor = Palette.BrandDark).clip(shape).background(Palette.WalletGradient)
    Box(if (onClick != null) base.clickable(onClick = onClick, role = Role.Button) else base) {
        // decorative only: soft circle + gold / teal sweeps along the bottom edge
        Box(Modifier.align(Alignment.TopEnd).offset(x = 46.dp, y = (-56).dp).size(170.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))
        BrandWave(Modifier.matchParentSize(), alpha = .4f)
        Box(Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Palette.GoldGradient), contentAlignment = Alignment.Center) {
                Icon(if (onClick != null) Icons.AutoMirrored.Filled.KeyboardArrowRight else Icons.Filled.CurrencyRupee, null,
                    tint = Palette.BrandDark, modifier = Modifier.size(22.dp))
            }
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
            Text(label, color = Color.White.copy(alpha = .88f), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 48.dp))
            Spacer(Modifier.height(4.dp))
            Text(Money.format(amount), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            if (footer != null) {
                Spacer(Modifier.height(6.dp))
                Text(footer, color = Color.White.copy(alpha = .82f), style = MaterialTheme.typography.labelMedium)
            }
            extra()
        }
    }
}

/** One figure on a [WalletCard] (e.g. Billed / Pending), with a thin divider drawn by [WalletFigures]. */
@Composable
fun WalletFigures(vararg figures: Pair<String, String>) {
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.BrandDark.copy(alpha = .45f)).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        figures.forEachIndexed { i, (label, value) ->
            if (i > 0) Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(30.dp).background(Color.White.copy(alpha = .25f)))
            Column(Modifier.weight(1f)) {
                Text(label, color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Gold and teal sweeps from the logo (purely decorative). */
@Composable
fun BrandWave(modifier: Modifier = Modifier, alpha: Float = 1f, fromTop: Boolean = false) {
    androidx.compose.foundation.Canvas(modifier) {
        val w = size.width; val h = size.height
        fun y(f: Float) = if (fromTop) h * (1 - f) else h * f
        val teal = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y(.86f))
            cubicTo(w * .30f, y(.70f), w * .62f, y(1.02f), w, y(.66f))
            lineTo(w, y(1f)); lineTo(0f, y(1f)); close()
        }
        drawPath(teal, Brush.horizontalGradient(listOf(Palette.TealBright.copy(alpha = .55f * alpha), Palette.Teal.copy(alpha = .9f * alpha))))
        val gold = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, y(.80f))
            cubicTo(w * .28f, y(.66f), w * .58f, y(.98f), w, y(.62f))
            lineTo(w, y(.655f))
            cubicTo(w * .58f, y(1.0f), w * .28f, y(.70f), 0f, y(.84f)); close()
        }
        drawPath(gold, Brush.horizontalGradient(listOf(Palette.Gold.copy(alpha = .2f * alpha), Palette.GoldBright.copy(alpha = alpha), Palette.Gold.copy(alpha = .8f * alpha))))
    }
}

/** Compact dashboard figure: icon, value and a short label (four fit in a row). */
@Composable
fun MiniStat(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    val base = modifier.shadow(4.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow).clip(shape).background(Palette.Card)
    Column((if (onClick != null) base.clickable(onClick = onClick, role = Role.Button) else base).padding(horizontal = 6.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Palette.Ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** Large, labelled shortcut on a soft tinted tile. */
@Composable
fun ActionTile(icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.shadow(4.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow).clip(shape)
            .background(Brush.verticalGradient(listOf(Palette.Card, tint.copy(alpha = .07f))))
            .clickable(onClick = onClick, role = Role.Button).padding(horizontal = 6.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(7.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = Palette.Ink, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
    }
}

/** Gold call-to-action on dark cards (e.g. "View bill" on the tenant's rent card). */
@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 50.dp).clip(shape).background(Palette.GoldGradient).clickable(onClick = onClick, role = Role.Button),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Palette.BrandDark, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(8.dp))
        Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Palette.BrandDark)
    }
}

/** Round avatar with the first letter of a name (no photo). */
@Composable
fun InitialAvatar(name: String, size: androidx.compose.ui.unit.Dp = 44.dp, tint: Color = Palette.Brand) {
    val letter = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Text(letter, color = tint, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                    Icon(icon, null, tint = tint, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.weight(1f))
                if (onClick != null) OpenHint()
            }
            Spacer(Modifier.height(10.dp))
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
    }
}

@Composable
fun SectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, fontWeight = FontWeight.SemiBold, color = Palette.NavSelected); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, valueColor: Color = Color.Unspecified, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
    }
}

/* ───────── status & formatting ───────── */

fun statusColors(status: String): Pair<Color, Color> = when (status) {
    "paid", "active", "available", "resolved", "converted" -> Palette.Success to Palette.SuccessSoft
    "partial", "in_progress", "trial", "allocated", "occupied" -> Palette.Info to Palette.InfoSoft
    "pending", "unpaid", "grace", "medium", "maintenance" -> Palette.Warning to Palette.WarningSoft
    "overdue", "expired", "suspended", "high", "cancelled" -> Palette.Danger to Palette.DangerSoft
    else -> Palette.Muted to Color(0xFFF0F1F7)
}

@Composable
fun StatusChip(status: String, label: String?) {
    val (fg, bg) = statusColors(status)
    Text(
        label ?: status,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

@Composable
fun dateText(value: String?): String = Dates.display(value, stringArrayResource(R.array.months).toList())

/** "October 2026" for a date in that month (the current month when [value] is missing). */
@Composable
fun monthYearText(value: String?): String {
    val d = Dates.parse(value) ?: java.time.LocalDate.now()
    val months = stringArrayResource(R.array.months)
    return "${months.getOrElse(d.monthValue - 1) { d.monthValue.toString() }} ${d.year}"
}

/* ───────── subscription banner (owner & employee) ───────── */

@Composable
fun SubscriptionBanner(sub: SubscriptionBrief?, onClick: (() -> Unit)? = null) {
    if (sub == null) return
    val (text, color) = when {
        sub.restricted -> stringResource(R.string.sub_restricted) to Palette.Danger
        sub.state == "grace" -> stringResource(R.string.sub_grace, dateText(sub.graceEndDate)) to Palette.Warning
        sub.state == "trial" && (sub.daysLeft ?: 99) <= 7 -> stringResource(R.string.sub_trial_ending, sub.daysLeft ?: 0) to Palette.Warning
        sub.state == "active" && (sub.daysLeft ?: 99) <= 7 -> stringResource(R.string.sub_ending, sub.daysLeft ?: 0) to Palette.Warning
        else -> return
    }
    val m = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = .1f))
    Row((if (onClick != null) m.clickable(onClick = onClick) else m).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (sub.restricted) Icons.Filled.Lock else Icons.Filled.WarningAmber, null, tint = color)
        Spacer(Modifier.width(10.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun MoneyText(amount: Double, color: Color = Color.Unspecified, bold: Boolean = true) {
    Text(Money.format(amount), color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

/** Light status-bar icons while a dark (navy) screen is shown; restores the previous look afterwards. */
@Composable
fun LightStatusBarIcons() {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.DisposableEffect(view) {
        var ctx = view.context
        while (ctx is android.content.ContextWrapper && ctx !is android.app.Activity) ctx = ctx.baseContext
        val window = (ctx as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (controller != null && previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/** Numbered section title of a long form ("1  Photo & documents"). The form itself stays one page. */
@Composable
fun StepHeader(number: Int, title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(Palette.Brand), contentAlignment = Alignment.Center) {
            Text(number.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.Ink, modifier = Modifier.weight(1f))
        Box(Modifier.weight(.35f).height(2.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(Palette.Gold, Color.Transparent))))
    }
}
