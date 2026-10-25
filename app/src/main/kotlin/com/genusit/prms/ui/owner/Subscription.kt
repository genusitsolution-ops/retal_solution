package com.genusit.prms.ui.owner

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette

/**
 * Owner plan: trial/active/grace/expired state, room usage, the room-wise renewal quote and
 * subscription invoices. Payments are recorded by the Super Admin after money is received —
 * the app never marks anything as paid.
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val model: SubscriptionViewModel = hiltViewModel()
    val vm = model.subscription
    val state by vm.state.collectAsStateWithLifecycle()
    val act by model.renewal.state.collectAsStateWithLifecycle()

    ScreenScaffold(stringResource(R.string.subscription), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { s ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.packageName ?: stringResource(R.string.no_plan), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                StatusChip(s.state, s.stateLabel)
                            }
                            Spacer(Modifier.height(6.dp))
                            val line = when (s.state) {
                                "trial" -> stringResource(R.string.sub_trial_until, dateText(s.endDate), s.daysLeft ?: 0)
                                "active" -> stringResource(R.string.sub_active_until, dateText(s.endDate), s.daysLeft ?: 0)
                                "grace" -> stringResource(R.string.sub_grace, dateText(s.graceEndDate))
                                "expired" -> stringResource(R.string.sub_expired_on, dateText(s.endDate))
                                else -> stringResource(R.string.sub_none)
                            }
                            Text(line, color = if (s.restricted) Palette.Danger else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s.restricted) {
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.sub_restricted), color = Palette.Danger, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            Text(stringResource(R.string.plan_usage), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            UsageBar(stringResource(R.string.rooms), s.usage.properties, s.usage.maxProperties)
                            Spacer(Modifier.height(10.dp))
                            UsageBar(stringResource(R.string.tenants), s.usage.tenants, s.usage.maxTenants)
                            Spacer(Modifier.height(6.dp))
                            Text(stringResource(if (s.usage.billableMode == "occupied") R.string.billable_occupied else R.string.billable_all),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                s.renewalQuote?.takeIf { it.totalAmount > 0 }?.let { q ->
                    item {
                        AppCard {
                            Column {
                                Text(stringResource(R.string.renewal_quote, q.packageName), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(6.dp))
                                LabelValue(stringResource(R.string.base_price_incl, q.includedUnits), Money.format(q.baseAmount))
                                if (q.extraUnits > 0) LabelValue(stringResource(R.string.extra_rooms_calc, q.extraUnits, Money.format(q.extraUnitPrice)), Money.format(q.extraAmount))
                                if (q.discountAmount > 0) LabelValue(stringResource(R.string.discount), "-" + Money.format(q.discountAmount))
                                if (q.taxAmount > 0) LabelValue("${s.taxLabel} ${q.taxPercent}%", Money.format(q.taxAmount))
                                HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                LabelValue(stringResource(R.string.total_for_days, q.cycleDays), Money.format(q.totalAmount), bold = true)
                            }
                        }
                    }
                }
                if (!s.paymentInstructions.isNullOrBlank()) item {
                    AppCard {
                        Column {
                            Text(stringResource(R.string.how_to_pay), style = MaterialTheme.typography.titleMedium)
                            Text(s.paymentInstructions!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val hasUnpaid = s.invoices.any { it.status == "unpaid" }
                if (s.canRequestRenewal && !hasUnpaid) item {
                    Column {
                        (act as? ActionState.Failed)?.let { Text(errorText(it.error), color = MaterialTheme.colorScheme.error) }
                        Button(onClick = model::requestRenewal, enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                            if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.request_renewal_invoice))
                        }
                        Text(stringResource(R.string.renewal_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (s.invoices.isNotEmpty()) item { SectionHeader(stringResource(R.string.subscription_invoices)) }
                items(s.invoices, key = { it.id }) { inv ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.invoiceNumber, fontWeight = FontWeight.SemiBold)
                                Text(dateText(inv.periodStart) + " – " + dateText(inv.periodEnd), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Money.format(inv.totalAmount), fontWeight = FontWeight.Bold)
                                StatusChip(inv.status, inv.statusLabel)
                            }
                        }
                    }
                }
                if (s.history.isNotEmpty()) item { SectionHeader(stringResource(R.string.plan_history)) }
                items(s.history, key = { "h" + it.id }) { h ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((h.packageName ?: "") + if (h.isTrial) " · " + stringResource(R.string.trial) else "", fontWeight = FontWeight.SemiBold)
                                Text(dateText(h.startDate) + " – " + dateText(h.endDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(h.amountPaid), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageBar(label: String, used: Int, max: Int?) {
    Column {
        Row {
            Text(label, modifier = Modifier.weight(1f))
            Text(if (max != null) "$used / $max" else used.toString(), fontWeight = FontWeight.Bold)
        }
        if (max != null && max > 0) {
            val f = (used.toFloat() / max).coerceIn(0f, 1f)
            Spacer(Modifier.height(4.dp))
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth(), color = if (f >= .9f) Palette.Danger else Palette.Brand)
        }
    }
}
