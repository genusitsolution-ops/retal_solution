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
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // the outer role shell already applies the navigation-bar inset
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onBack != null) IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        content = content,
    )
}

/** Renders loading / error / data for a screen's [UiState]. */
@Composable
fun <T> StateContent(state: UiState<T>, onRetry: () -> Unit, modifier: Modifier = Modifier, content: @Composable (T) -> Unit) {
    // short cross-fade between loading / error / content (keyed by the kind of state, not the data)
    Crossfade(targetState = state, contentKey = { it::class }, animationSpec = tween(220), label = "state") { s ->
        when (s) {
            is UiState.Loading -> LoadingBox(modifier)
            is UiState.Error -> ErrorBox(s.error, onRetry, modifier)
            is UiState.Data -> Box(modifier.fillMaxSize()) {
                content(s.value)
                if (s.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter), color = Palette.Brand, trackColor = Palette.BrandSoft)
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
        Box(Modifier.size(76.dp).clip(CircleShape).background(Palette.BrandSoft.copy(alpha = .6f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Palette.Brand.copy(alpha = .7f), modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
    }
}

/* ───────── cards ───────── */

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = MaterialTheme.shapes.medium
    val base = modifier.fillMaxWidth().shadow(6.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
    val colors = CardDefaults.cardColors(containerColor = Palette.Card)
    val border = BorderStroke(1.dp, Palette.Line.copy(alpha = .7f))
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

/** Gradient "wallet" card from the FiMobile reference — the main figure of each dashboard. */
@Composable
fun WalletCard(label: String, amount: Double, footer: String? = null, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, extra: @Composable () -> Unit = {}) {
    val shape = RoundedCornerShape(26.dp)
    val base = modifier.fillMaxWidth().shadow(14.dp, shape, ambientColor = Palette.Brand, spotColor = Palette.Brand).clip(shape).background(Palette.WalletGradient)
    Box(if (onClick != null) base.clickable(onClick = onClick, role = Role.Button) else base) {
        // soft decorative circles (purely visual)
        Box(Modifier.align(Alignment.TopEnd).offset(x = 40.dp, y = (-50).dp).size(160.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-30).dp, y = 60.dp).size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = .06f)))
        if (onClick != null) Box(Modifier.align(Alignment.TopEnd).padding(14.dp)) { OpenHint(Color.White) }
        Column(Modifier.padding(22.dp)) {
            Text(label, color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Text(Money.format(amount), color = Color.White, style = MaterialTheme.typography.headlineMedium)
            if (footer != null) {
                Spacer(Modifier.height(6.dp))
                Text(footer, color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.labelMedium)
            }
            extra()
        }
    }
}

@Composable
fun StatTile(icon: ImageVector, label: String, value: String, tint: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    AppCard(modifier, onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)).background(Brush.linearGradient(listOf(tint.copy(alpha = .18f), tint.copy(alpha = .08f)))), contentAlignment = Alignment.Center) {
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
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) TextButton(onClick = onAction) {
            Text(action, fontWeight = FontWeight.SemiBold); Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(18.dp))
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
    else -> Palette.Muted to Color(0xFFF1F5F9)
}

@Composable
fun StatusChip(status: String, label: String?) {
    val (fg, bg) = statusColors(status)
    Text(
        label ?: status,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@Composable
fun dateText(value: String?): String = Dates.display(value, stringArrayResource(R.array.months).toList())

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
