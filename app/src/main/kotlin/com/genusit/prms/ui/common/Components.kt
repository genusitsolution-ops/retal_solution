package com.genusit.prms.ui.common

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
    when (state) {
        is UiState.Loading -> LoadingBox(modifier)
        is UiState.Error -> ErrorBox(state.error, onRetry, modifier)
        is UiState.Data -> Box(modifier.fillMaxSize()) {
            content(state.value)
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        Icon(if (error.isNetwork) Icons.Filled.CloudOff else Icons.Filled.WarningAmber, null, tint = Palette.Warning, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text(errorText(error), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.retry))
        }
    }
}

@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Filled.Inbox, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = Palette.Muted.copy(alpha = .5f), modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(10.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

/* ───────── cards ───────── */

@Composable
fun AppCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val m = modifier.fillMaxWidth()
    Card(
        modifier = if (onClick != null) m.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick) else m,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Palette.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) { Box(Modifier.padding(16.dp)) { content() } }
}

/** Gradient "wallet" card from the FiMobile reference — the main figure of each dashboard. */
@Composable
fun WalletCard(label: String, amount: Double, footer: String? = null, modifier: Modifier = Modifier, extra: @Composable () -> Unit = {}) {
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Palette.WalletGradient).padding(20.dp)
    ) {
        Column {
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
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
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
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
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
