package com.genusit.prms.ui.employee

import com.genusit.prms.ui.owner.paymentModes
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.EventEffect
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.PaymentResult
import com.genusit.prms.domain.model.TenantRow
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.PaymentRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StatTile
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubscriptionBanner
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.owner.PaymentForm
import com.genusit.prms.ui.owner.SearchField
import com.genusit.prms.ui.theme.Palette

@Composable
fun EmployeeHomeScreen(
    collect: (tenantId: Int, invoiceId: Int) -> Unit,
    openReceipt: (String) -> Unit,
    openNotifications: () -> Unit,
    openTenants: () -> Unit = {},
    openCollections: () -> Unit = {},
) {
    val model: AgentHomeViewModel = hiltViewModel()
    val vm = model.dashboard
    val state by vm.state.collectAsStateWithLifecycle()
    val name by model.userName.collectAsStateWithLifecycle()
    ScreenScaffold(
        stringResource(R.string.hello_name, name.substringBefore(' ')),
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            IconButton(onClick = openNotifications) { Icon(Icons.Filled.Notifications, stringResource(R.string.notifications)) }
        },
    ) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SubscriptionBanner(d.subscription) }
                item {
                    WalletCard(
                        stringResource(R.string.collected_this_month), d.summary.monthCollected,
                        stringResource(R.string.all_time_n, Money.format(d.summary.allTimeCollected), d.summary.totalTransactions),
                        onClick = openCollections,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Icons.Filled.Apartment, stringResource(R.string.assigned_rooms), d.summary.assignedProperties.toString(), Palette.Brand, Modifier.weight(1f), onClick = openTenants)
                        StatTile(Icons.Filled.Groups, stringResource(R.string.active_tenants), d.summary.activeTenants.toString(), Palette.Info, Modifier.weight(1f), onClick = openTenants)
                    }
                }
                item { SectionHeader(stringResource(R.string.pending_dues)) }
                if (d.pendingInvoices.isEmpty()) item { EmptyState(stringResource(R.string.no_pending_invoices)) }
                items(d.pendingInvoices, key = { it.id }) { inv ->
                    InvoiceRow(inv, showTenant = true) { collect(inv.tenantId, inv.id) }
                }
                if (d.recentCollections.isNotEmpty()) item { SectionHeader(stringResource(R.string.recent_collections)) }
                items(d.recentCollections, key = { "c" + it.id }) { c ->
                    PaymentRow(c.tenantName ?: "—", listOfNotNull(dateText(c.collectionDate), c.paymentModeLabel, c.propertyCode).joinToString(" · "), c.amount) { openReceipt(c.receiptNumber) }
                }
            }
        }
    }
}

@Composable
fun EmployeeTenantsScreen(collect: (tenantId: Int, invoiceId: Int) -> Unit) {
    val vm = hiltViewModel<AgentTenantsViewModel>().tenants
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(R.string.my_tenants)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            val shown = list.filter { query.isBlank() || it.fullName.contains(query, true) || (it.propertyCode ?: "").contains(query, true) || (it.phone ?: "").contains(query) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SearchField(query) { query = it } }
                if (shown.isEmpty()) item { EmptyState(stringResource(R.string.no_tenants)) }
                items(shown, key = { it.id }) { t -> EmployeeTenantCard(t) { collect(t.id, 0) } }
            }
        }
    }
}

@Composable
private fun EmployeeTenantCard(t: TenantRow, onCollect: () -> Unit) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Person, Palette.Brand)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(t.propertyCode, t.city, t.phone).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(t.pendingAmount), fontWeight = FontWeight.Bold, color = if (t.pendingAmount > 0) Palette.Danger else Palette.Success)
            }
            if (t.pendingAmount > 0) TextButton(onClick = onCollect, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.collect_payment)) }
        }
    }
}

@Composable
fun EmployeeCollectionsScreen(openReceipt: (String) -> Unit) {
    val vm = hiltViewModel<AgentCollectionsViewModel>().collections
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.my_collections), actions = { IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WalletCard(stringResource(R.string.collected_this_month), d.total, stringResource(R.string.n_transactions, d.count)) }
                if (d.collections.isEmpty()) item { EmptyState(stringResource(R.string.no_collections)) }
                items(d.collections, key = { it.id }) { c ->
                    PaymentRow(c.tenantName ?: "—", listOfNotNull(dateText(c.collectionDate), c.paymentModeLabel, c.propertyCode, c.receiptNumber).joinToString(" · "), c.amount) { openReceipt(c.receiptNumber) }
                }
            }
        }
    }
}

/**
 * Collect rent for an assigned tenant. Choosing a specific invoice is optional — without one the
 * server applies the money to the oldest unpaid invoices first and never above the balance.
 */
@Composable
fun EmployeeCollectScreen(preTenant: Int, preInvoice: Int, onBack: () -> Unit, onDone: (String) -> Unit) {
    val model: AgentCollectViewModel = hiltViewModel()
    val vmT = model.tenants
    val tState by vmT.state.collectAsStateWithLifecycle()
    val dState by model.dashboard.state.collectAsStateWithLifecycle()
    val meta by model.meta.state.collectAsStateWithLifecycle()
    val action = model.collectAction
    val act by action.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it is ScreenEvent.ReceiptReady) onDone(it.receiptNumber) }

    var tenantId by rememberSaveable { mutableStateOf(preTenant) }
    var invoiceId by rememberSaveable { mutableStateOf(preInvoice) }

    ScreenScaffold(stringResource(R.string.collect_payment), onBack = onBack) { pad ->
        StateContent(tState, vmT::refresh, Modifier.padding(pad)) { tenants ->
            val withDues = tenants.filter { it.pendingAmount > 0 }
            val tenant = tenants.firstOrNull { it.id == tenantId }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (tenant == null) {
                    Text(stringResource(R.string.select_tenant), style = MaterialTheme.typography.titleMedium)
                    if (withDues.isEmpty()) EmptyState(stringResource(R.string.no_pending_invoices))
                    withDues.forEach { t -> EmployeeTenantCard(t) { tenantId = t.id; invoiceId = 0 } }
                } else {
                    val open = tenant.invoices.filter { it.isOpen }
                    val selected = open.firstOrNull { it.id == invoiceId }
                    val max = selected?.balanceAmount ?: tenant.pendingAmount
                    WalletCard(stringResource(R.string.balance_due), max, tenant.fullName + " · " + (tenant.propertyCode ?: ""))
                    TextButton(onClick = { tenantId = 0; invoiceId = 0; action.reset() }) { Text(stringResource(R.string.change_tenant)) }
                    Text(stringResource(R.string.apply_to), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = invoiceId == 0, onClick = { invoiceId = 0 }, label = { Text(stringResource(R.string.oldest_first)) })
                    }
                    open.forEach { inv ->
                        FilterChip(
                            selected = invoiceId == inv.id, onClick = { invoiceId = inv.id },
                            label = { Text("${inv.periodLabel ?: inv.invoiceNumber} · ${Money.format(inv.balanceAmount)}") },
                        )
                    }
                    // property id comes with the tenant row; older servers only send the code
                    val propertyId = tenant.propertyId.takeIf { it > 0 }
                        ?: (dState as? UiState.Data)?.value?.assignedProperties?.firstOrNull { it.propertyCode == tenant.propertyCode }?.id ?: 0
                    PaymentForm(
                        maxAmount = max,
                        running = act is ActionState.Running,
                        errorMessage = (act as? ActionState.Failed)?.let { errorText(it.error) },
                        modes = paymentModes(meta),
                    ) { amount, mode, date, ref, remarks ->
                        if (propertyId > 0) model.collect(tenant.id, propertyId, invoiceId, amount, mode, date, ref, remarks)
                    }
                }
            }
        }
    }
}
