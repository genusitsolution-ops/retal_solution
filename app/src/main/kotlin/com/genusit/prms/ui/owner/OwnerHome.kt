package com.genusit.prms.ui.owner

import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.BilledCollectedChart
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.monthYearText
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.TenantDuesRow
import com.genusit.prms.ui.common.StatTile
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.SubscriptionBanner
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.theme.Palette

@Composable
fun OwnerHomeScreen(
    openInvoice: (Int) -> Unit,
    openInvoices: () -> Unit,
    openSubscription: () -> Unit,
    openNotifications: () -> Unit,
    openProperties: () -> Unit = {},
    openTenants: () -> Unit = {},
    openDueInvoices: () -> Unit = openInvoices,
    openDuesByTenant: () -> Unit = openDueInvoices,
    openOpenQueries: () -> Unit = {},
    openPayments: () -> Unit = {},
    openReports: () -> Unit = {},
    generateBill: () -> Unit = {},
    addTenant: () -> Unit = {},
    collectPayment: () -> Unit = {},
    addProperty: () -> Unit = {},
    openSupport: () -> Unit = {},
    openVendor: () -> Unit = {},
) {
    val model: OwnerHomeViewModel = hiltViewModel()
    val vm = model.dashboard
    val state by vm.state.collectAsStateWithLifecycle()
    val name by model.userName.collectAsStateWithLifecycle()
    // Home stays in the back stack while payments are recorded elsewhere: reload every time it is shown
    // again, so the totals always match the Payments screen (the first load is done by the ViewModel)
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        var first = true
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) {
            if (!first && state !is com.genusit.prms.ui.common.UiState.Loading) vm.refresh()
            first = false
        }
    }

    ScreenScaffold(
        title = stringResource(R.string.hello_name, name.substringBefore(' ')),
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            com.genusit.prms.ui.common.NotificationBell(onClick = openNotifications)
        },
    ) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            val s = d.summary
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SubscriptionBanner(d.subscription, openSubscription) }
                item {
                    // same rows and the same dates as Payments → "This month"
                    WalletCard(
                        label = stringResource(R.string.collected_in_month, monthYearText(s.monthFrom)),
                        amount = s.thisMonthCollected,
                        footer = stringResource(R.string.payments_count, s.thisMonthPayments) + "\n" +
                            stringResource(R.string.billed_and_pending, Money.format(s.thisMonthBilled), Money.format(s.pendingAmount)),
                        onClick = openPayments,
                    )
                }
                // the bill workflow: pick an occupied room → month & charges → preview → save
                item { GenerateBillCard(onClick = generateBill) }
                // 1.12.1: the owner's everyday actions one tap away
                item { QuickActionsRow(addTenant, collectPayment, addProperty) }
                // 1.13: owners converted to vendors by the Super Admin
                if (d.isVendor) item { VendorHomeCard(openVendor) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Icons.Filled.Apartment, stringResource(R.string.rooms_occupied), "${s.occupiedProperties}/${s.properties}", Palette.Brand, Modifier.weight(1f), onClick = openProperties)
                        StatTile(Icons.Filled.Groups, stringResource(R.string.tenants), s.tenants.toString(), Palette.Info, Modifier.weight(1f), onClick = openTenants)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 1.10: counted per tenant (all unpaid months of a tenant together) when the server sends it
                        val duesLabel = s.duesTenants?.let { stringResource(R.string.tenants_with_dues_n, it) } ?: stringResource(R.string.pending_invoices_n, s.pendingInvoices)
                        StatTile(Icons.AutoMirrored.Filled.ReceiptLong, duesLabel, Money.compact(s.pendingAmount), Palette.Danger, Modifier.weight(1f),
                            onClick = if (s.duesTenants != null) openDuesByTenant else openDueInvoices)
                        StatTile(Icons.Filled.QuestionAnswer, stringResource(R.string.open_queries), s.openQueries.toString(), Palette.Warning, Modifier.weight(1f), onClick = openOpenQueries)
                    }
                }
                item { BilledCollectedChart(d.chart, onClick = openReports) }
                if (d.duesByTenant.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.dues_by_tenant), stringResource(R.string.view_all), openDuesByTenant) }
                    items(d.duesByTenant, key = { "due" + it.tenantId }) { t -> TenantDuesRow(t, onClick = { openInvoice(t.latestInvoiceId) }) }
                }
                item { SectionHeader(stringResource(R.string.recent_invoices), stringResource(R.string.view_all), openInvoices) }
                if (d.recentInvoices.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices)) }
                items(d.recentInvoices, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = true) { openInvoice(inv.id) } }
                // 1.12.1: help from the support person set by the Super Admin
                item { HelpCard(openSupport) }
                item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
            }
        }
    }
}

/** Three large, labelled shortcuts (big touch targets for non-technical owners). */
@androidx.compose.runtime.Composable
fun QuickActionsRow(addTenant: () -> Unit, collectPayment: () -> Unit, addProperty: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        QuickAction(androidx.compose.material.icons.Icons.Filled.PersonAdd, stringResource(R.string.qa_add_tenant), Palette.Info, Modifier.weight(1f), addTenant)
        QuickAction(androidx.compose.material.icons.Icons.Filled.CurrencyRupee, stringResource(R.string.qa_collect), Palette.Success, Modifier.weight(1f), collectPayment)
        QuickAction(androidx.compose.material.icons.Icons.Filled.MeetingRoom, stringResource(R.string.qa_add_room), Palette.Brand, Modifier.weight(1f), addProperty)
    }
}

@androidx.compose.runtime.Composable
private fun QuickAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    com.genusit.prms.ui.common.AppCard(modifier, onClick) {
        androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth(), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            com.genusit.prms.ui.common.IconBadge(icon, tint)
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, maxLines = 2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
}

/** "Need help?" card that opens Contact Support. */
@androidx.compose.runtime.Composable
fun HelpCard(onClick: () -> Unit) {
    com.genusit.prms.ui.common.AppCard(onClick = onClick) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.genusit.prms.ui.common.IconBadge(androidx.compose.material.icons.Icons.Filled.SupportAgent, Palette.Brand)
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 12.dp)) {
                androidx.compose.material3.Text(stringResource(R.string.sup_need_help), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                androidx.compose.material3.Text(stringResource(R.string.sup_need_help_sub), style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
