package com.genusit.prms.ui.owner

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.PlanPackage
import com.genusit.prms.domain.model.SubscriptionStatus
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.PlanStatus
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import kotlinx.coroutines.launch

/**
 * Owner plan: status (Active, Trial, Expiring soon, Expired, Payment pending, Admin verification pending, Rejected,
 * Renewed), dates, usage, features, packages to renew / upgrade to (priced by the server for the owner's rooms),
 * renewal requests, payment history and plan history. Choosing a package opens the payment screen.
 * The app never marks anything as paid.
 */
@Composable
fun SubscriptionScreen(onBack: () -> Unit, openPayment: (Int) -> Unit = {}) {
    val model: SubscriptionViewModel = hiltViewModel()
    val vm = model.subscription
    val state by vm.state.collectAsStateWithLifecycle()
    val choose by model.choose.state.collectAsStateWithLifecycle()
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    EventEffect(model) { if (it is ScreenEvent.InvoiceCreated) openPayment(it.invoiceId) }
    // reload when coming back from the payment screen
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }

    ScreenScaffold(stringResource(R.string.subscription), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { s ->
            val plansIndex = 4 + (if (s.openInvoiceId != null) 1 else 0)
            LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { PlanHero(s) }
                item {
                    if (s.canRequestRenewal && s.packages.isNotEmpty()) Button(
                        onClick = { scope.launch { list.animateScrollToItem(plansIndex) } },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp).testTag("renew_upgrade"),
                    ) { Icon(Icons.Filled.Autorenew, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.renew_upgrade)) }
                }
                item { UsageCard(s) }
                item { FeaturesCard(s) }
                s.openInvoiceId?.let { invId ->
                    item {
                        val inv = s.invoices.firstOrNull { it.id == invId }
                        AppCard(onClick = { openPayment(invId) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.ReceiptLong, null, tint = Palette.Warning)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(stringResource(R.string.sub_invoice_waiting, inv?.packageName ?: "", Money.format(inv?.totalAmount ?: 0.0)), fontWeight = FontWeight.SemiBold)
                                    Text(inv?.invoiceNumber ?: "", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(stringResource(if (s.pendingRequest != null) R.string.sub_view else R.string.proceed_to_payment), color = Palette.Brand, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.renew_upgrade)) }
                if (!s.canRequestRenewal) item { Text(stringResource(R.string.sub_contact_support), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else if (s.packages.isEmpty()) item { Text(stringResource(R.string.sub_no_packages), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else {
                    item {
                        Text(stringResource(R.string.sub_price_note, s.usage.billableUnits), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionError(choose)
                    }
                    items(s.packages, key = { "p" + it.id }) { p ->
                        val current = p.id == s.packageId && !s.isTrial
                        val openFor = s.invoices.firstOrNull { it.id == s.openInvoiceId }?.packageId
                        PackageCard(p, s.taxLabel, current, choosing = choose is ActionState.Running, isOpen = openFor == p.id) { model.selectPackage(p.id) }
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
                if (s.requests.isNotEmpty()) item { SectionHeader(stringResource(R.string.renewal_requests)) }
                items(s.requests, key = { "r" + it.id }) { r -> RenewalRequestCard(r) }
                if (s.invoices.isNotEmpty()) item { SectionHeader(stringResource(R.string.payment_history)) }
                items(s.invoices, key = { it.id }) { inv ->
                    AppCard(onClick = if (inv.status == "unpaid") ({ openPayment(inv.id) }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(inv.invoiceNumber, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(inv.packageName, dateText(inv.periodStart) + " – " + dateText(inv.periodEnd)).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
private fun statusLabel(code: String): String = stringResource(when (code) {
    "active" -> R.string.ps_active
    "trial" -> R.string.ps_trial
    "expiring_soon" -> R.string.ps_expiring_soon
    "expired" -> R.string.ps_expired
    "grace" -> R.string.ps_grace
    "payment_pending" -> R.string.ps_payment_pending
    "verification_pending" -> R.string.ps_verification_pending
    "rejected" -> R.string.ps_rejected
    "renewed" -> R.string.ps_renewed
    else -> R.string.no_plan
})

/** Chip colours for the plan statuses. */
private fun chipCode(code: String): String = when (code) {
    in PlanStatus.DANGER -> "expired"
    in PlanStatus.WARNING -> "pending"
    "trial" -> "trial"
    else -> "active"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlanHero(s: SubscriptionStatus) {
    AppCard {
        Column {
            Text(stringResource(R.string.sub_current_plan), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(s.packageName ?: stringResource(R.string.no_plan), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            val flags = s.displayStatus?.flags?.takeIf { it.isNotEmpty() } ?: listOf(s.state)
            FlowRow(Modifier.padding(top = 6.dp).testTag("plan_status"), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                flags.forEach { StatusChip(chipCode(it), statusLabel(it)) }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DateBlock(stringResource(R.string.sub_start_date), dateText(s.currentStartDate ?: s.startDate), Modifier.weight(1f))
                DateBlock(stringResource(if (s.isTrial) R.string.sub_trial_ends else R.string.sub_expiry_date), dateText(s.endDate), Modifier.weight(1f))
                DateBlock(stringResource(R.string.sub_days_remaining), (s.daysLeft?.coerceAtLeast(0) ?: 0).toString(), Modifier.weight(1f))
            }
            if (s.state == "grace") Text(stringResource(R.string.sub_grace, dateText(s.graceEndDate)), color = Palette.Warning, modifier = Modifier.padding(top = 6.dp))
            if (s.restricted) Text(stringResource(R.string.sub_restricted), color = Palette.Danger, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun DateBlock(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun UsageCard(s: SubscriptionStatus) {
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

@Composable
private fun FeaturesCard(s: SubscriptionStatus) {
    AppCard {
        Column {
            Text(stringResource(R.string.package_features), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            s.usage.maxProperties?.let { FeatureLine(stringResource(R.string.up_to_rooms, it)) }
            s.usage.maxTenants?.let { FeatureLine(stringResource(R.string.up_to_tenants, it)) }
            s.currentPackage?.features?.forEach { FeatureLine(it) }
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Check, null, tint = Palette.Success, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
fun PackageCard(p: PlanPackage, taxLabel: String, current: Boolean, choosing: Boolean, isOpen: Boolean, onChoose: () -> Unit) {
    val q = p.quote
    val border = if (current || p.isFeatured) Palette.Brand else Color.Transparent
    AppCard {
        Column(Modifier.border(if (current || p.isFeatured) 1.5.dp else 0.dp, border, MaterialTheme.shapes.small).padding(if (current || p.isFeatured) 8.dp else 0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (current) StatusChip("trial", stringResource(R.string.sub_current_plan)) else if (p.isFeatured) StatusChip("active", stringResource(R.string.popular))
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(Money.format(q.totalAmount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Palette.Brand)
                Spacer(Modifier.width(6.dp))
                Text("/ " + stringResource(R.string.n_days, p.durationDays), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
            LabelValue(stringResource(R.string.base_price_incl, q.includedUnits), Money.format(q.baseAmount))
            if (q.extraUnits > 0) LabelValue(stringResource(R.string.extra_rooms_calc, q.extraUnits, Money.format(q.extraUnitPrice)), Money.format(q.extraAmount))
            if (q.taxAmount > 0) LabelValue("$taxLabel ${if (q.taxPercent % 1.0 == 0.0) q.taxPercent.toLong().toString() else q.taxPercent.toString()}%", Money.format(q.taxAmount))
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            LabelValue(stringResource(R.string.total_for_days, p.durationDays), Money.format(q.totalAmount), bold = true)
            FeatureLine(stringResource(R.string.up_to_rooms, p.maxProperties) + " · " + stringResource(R.string.up_to_tenants, p.maxTenants))
            p.features.forEach { FeatureLine(it) }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onChoose, enabled = !choosing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                if (choosing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(if (isOpen) R.string.proceed_to_payment else R.string.choose_plan))
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
            LinearProgressIndicator(progress = { f }, modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small), color = if (f >= .9f) Palette.Danger else Palette.Brand)
        }
    }
}
