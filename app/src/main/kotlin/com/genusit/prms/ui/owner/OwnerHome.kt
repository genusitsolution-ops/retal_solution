package com.genusit.prms.ui.owner

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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.core.util.Money
import com.genusit.prms.ui.common.BilledCollectedChart
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.LocalAppContainer
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StatTile
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.SubscriptionBanner
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.rememberLoader
import com.genusit.prms.ui.theme.Palette

@Composable
fun OwnerHomeScreen(
    openInvoice: (Int) -> Unit,
    openInvoices: () -> Unit,
    openSubscription: () -> Unit,
    openNotifications: () -> Unit,
) {
    val vm = rememberLoader("owner-dashboard") { ownerDashboard() }
    val state by vm.state.collectAsStateWithLifecycle()
    val name = LocalAppContainer.current.session.session.collectAsStateWithLifecycle().value?.name.orEmpty()

    ScreenScaffold(
        title = stringResource(R.string.hello_name, name.substringBefore(' ')),
        actions = {
            IconButton(onClick = vm::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
            IconButton(onClick = openNotifications) { Icon(Icons.Filled.Notifications, stringResource(R.string.notifications)) }
        },
    ) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            val s = d.summary
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SubscriptionBanner(d.subscription, openSubscription) }
                item {
                    WalletCard(
                        label = stringResource(R.string.collected_this_month),
                        amount = s.thisMonthCollected,
                        footer = stringResource(R.string.billed_and_pending, Money.format(s.thisMonthBilled), Money.format(s.pendingAmount)),
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Icons.Filled.Apartment, stringResource(R.string.rooms_occupied), "${s.occupiedProperties}/${s.properties}", Palette.Brand, Modifier.weight(1f))
                        StatTile(Icons.Filled.Groups, stringResource(R.string.tenants), s.tenants.toString(), Palette.Info, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatTile(Icons.AutoMirrored.Filled.ReceiptLong, stringResource(R.string.pending_invoices_n, s.pendingInvoices), Money.compact(s.pendingAmount), Palette.Danger, Modifier.weight(1f), onClick = openInvoices)
                        StatTile(Icons.Filled.QuestionAnswer, stringResource(R.string.open_queries), s.openQueries.toString(), Palette.Warning, Modifier.weight(1f))
                    }
                }
                item { BilledCollectedChart(d.chart) }
                item { SectionHeader(stringResource(R.string.recent_invoices), stringResource(R.string.view_all), openInvoices) }
                if (d.recentInvoices.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices)) }
                items(d.recentInvoices, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = true) { openInvoice(inv.id) } }
                item { Spacer(Modifier.height(8.dp).fillMaxWidth()) }
            }
        }
    }
}
