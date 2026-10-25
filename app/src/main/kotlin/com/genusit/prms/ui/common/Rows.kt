package com.genusit.prms.ui.common

import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.genusit.prms.R
import com.genusit.prms.domain.model.ChartPoint
import com.genusit.prms.domain.model.InvoiceSummary
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.theme.Palette

@Composable
fun IconBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(tint.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** One invoice in a list: period + party on the left, balance and status on the right. */
@Composable
fun InvoiceRow(inv: InvoiceSummary, showTenant: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.AutoMirrored.Filled.ReceiptLong, statusColors(inv.status).first)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (showTenant) (inv.tenantName ?: "—") else (inv.periodLabel ?: inv.invoiceNumber),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                val sub = buildList {
                    if (showTenant) add(inv.periodLabel ?: "${inv.billingMonth}/${inv.billingYear}")
                    inv.propertyCode?.let { add(it) }
                    add(inv.invoiceNumber)
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(if (inv.isOpen) inv.balanceAmount else inv.totalAmount), fontWeight = FontWeight.Bold,
                    color = if (inv.isOpen) Palette.Danger else MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.height(4.dp))
                StatusChip(inv.status, inv.statusLabel)
            }
        }
    }
}

@Composable
fun PaymentRow(title: String, subtitle: String, amount: Double, onClick: (() -> Unit)?) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.Payments, Palette.Success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text("+" + Money.format(amount), fontWeight = FontWeight.Bold, color = Palette.Success)
        }
    }
}

/** Six-month billed vs collected bars (no chart library needed). */
@Composable
fun BilledCollectedChart(points: List<ChartPoint>, onClick: (() -> Unit)? = null) {
    if (points.isEmpty()) return
    val max = points.maxOf { maxOf(it.billed, it.collected) }.takeIf { it > 0 } ?: 1.0
    // bars grow in once when the data arrives
    val grow = remember { Animatable(0f) }
    LaunchedEffect(points) { grow.snapTo(0f); grow.animateTo(1f, tween(700)) }
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.billed_vs_collected), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (onClick != null) OpenHint()
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Palette.BrandSoft))
                Text(" " + stringResource(R.string.billed) + "   ", style = MaterialTheme.typography.labelMedium)
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Palette.Brand))
                Text(" " + stringResource(R.string.collected), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().height(130.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                points.forEach { p ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(Modifier.height(104.dp), verticalAlignment = Alignment.Bottom) {
                            Box(Modifier.width(9.dp).fillMaxHeight(((p.billed / max).toFloat() * grow.value).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp)).background(Palette.BrandSoft))
                            Spacer(Modifier.width(3.dp))
                            Box(Modifier.width(9.dp).fillMaxHeight(((p.collected / max).toFloat() * grow.value).coerceIn(0.02f, 1f)).clip(RoundedCornerShape(4.dp))
                                .background(Brush.verticalGradient(listOf(Palette.Accent, Palette.Brand))))
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(p.monthLabel.substringBefore(' ').take(4), style = MaterialTheme.typography.labelMedium, maxLines = 1,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 1.dp))
                    }
                }
            }
        }
    }
}
