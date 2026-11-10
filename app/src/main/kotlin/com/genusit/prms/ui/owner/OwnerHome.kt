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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Bolt

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
        title = stringResource(R.string.hello_name, name.substringBefore(' ')) + " 👋",
        subtitle = stringResource(R.string.role_owner),
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            com.genusit.prms.ui.common.NotificationBell(onClick = openNotifications)
        },
    ) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            val s = d.summary
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { SubscriptionBanner(d.subscription, openSubscription) }
                item {
                    // same rows and the same dates as Payments → "This month"
                    WalletCard(
                        label = stringResource(R.string.collected_in_month, monthYearText(s.monthFrom)),
                        amount = s.thisMonthCollected,
                        footer = stringResource(R.string.payments_count, s.thisMonthPayments),
                        onClick = openPayments,
                    ) {
                        com.genusit.prms.ui.common.WalletFigures(
                            stringResource(R.string.billed) to Money.format(s.thisMonthBilled),
                            stringResource(R.string.st_pending) to Money.format(s.pendingAmount),
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
                        // 1.10: counted per tenant (all unpaid months of a tenant together) when the server sends it
                        val duesLabel = s.duesTenants?.let { stringResource(R.string.tenants_with_dues_n, it) } ?: stringResource(R.string.pending_invoices_n, s.pendingInvoices)
                        val m = Modifier.weight(1f).fillMaxHeight()
                        com.genusit.prms.ui.common.MiniStat(Icons.Filled.Apartment, "${s.occupiedProperties}/${s.properties}", stringResource(R.string.rooms_occupied), Palette.Brand, m, onClick = openProperties)
                        com.genusit.prms.ui.common.MiniStat(Icons.Filled.Groups, s.tenants.toString(), stringResource(R.string.tenants), Palette.Teal, m, onClick = openTenants)
                        com.genusit.prms.ui.common.MiniStat(Icons.Filled.CurrencyRupee, Money.compact(s.pendingAmount), duesLabel, Palette.Gold, m,
                            onClick = if (s.duesTenants != null) openDuesByTenant else openDueInvoices)
                        com.genusit.prms.ui.common.MiniStat(Icons.Filled.QuestionAnswer, s.openQueries.toString(), stringResource(R.string.open_queries), Palette.Purple, m, onClick = openOpenQueries)
                    }
                }
                // the owner's everyday actions one tap away (generate bill: pick an occupied room → month & charges → preview → save)
                item { QuickActionsRow(addTenant, collectPayment, addProperty, generateBill) }
                // 1.13: owners converted to vendors by the Super Admin
                if (d.isVendor) item { VendorHomeCard(openVendor) }
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

/** "Quick actions": four large, labelled shortcuts (big touch targets for non-technical owners). */
@androidx.compose.runtime.Composable
fun QuickActionsRow(addTenant: () -> Unit, collectPayment: () -> Unit, addProperty: () -> Unit, generateBill: (() -> Unit)? = null) {
    androidx.compose.foundation.layout.Column {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(Icons.Filled.Bolt, null, tint = Palette.Gold, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            androidx.compose.material3.Text(stringResource(R.string.quick_actions), style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Palette.Ink)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            val m = Modifier.weight(1f).fillMaxHeight()
            if (generateBill != null)
                com.genusit.prms.ui.common.ActionTile(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.generate_bill), Palette.Purple, m, generateBill)
            com.genusit.prms.ui.common.ActionTile(Icons.Filled.PersonAdd, stringResource(R.string.qa_add_tenant), Palette.Teal, m, addTenant)
            com.genusit.prms.ui.common.ActionTile(Icons.Filled.CurrencyRupee, stringResource(R.string.qa_collect), Palette.Gold, m, collectPayment)
            com.genusit.prms.ui.common.ActionTile(Icons.Filled.MeetingRoom, stringResource(R.string.qa_add_room), Palette.Brand, m, addProperty)
        }
    }
}

/** "Need help?" card that opens Contact Support. */
@androidx.compose.runtime.Composable
fun HelpCard(onClick: () -> Unit) {
    com.genusit.prms.ui.common.AppCard(onClick = onClick) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            com.genusit.prms.ui.common.IconBadge(androidx.compose.material.icons.Icons.Filled.SupportAgent, Palette.Teal)
            androidx.compose.foundation.layout.Column(Modifier.weight(1f).padding(start = 12.dp)) {
                androidx.compose.material3.Text(stringResource(R.string.sup_need_help), style = androidx.compose.material3.MaterialTheme.typography.titleSmall)
                androidx.compose.material3.Text(stringResource(R.string.sup_need_help_sub), style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
