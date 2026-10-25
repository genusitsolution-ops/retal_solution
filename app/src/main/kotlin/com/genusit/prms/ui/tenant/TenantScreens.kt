package com.genusit.prms.ui.tenant

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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.NewQuery
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.PaymentRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.dial
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.owner.InvoiceBody
import com.genusit.prms.ui.owner.MenuRow
import com.genusit.prms.ui.theme.Palette

@Composable
fun TenantHomeScreen(openInvoice: (Int) -> Unit, openNotifications: () -> Unit) {
    val model: TenantHomeViewModel = hiltViewModel()
    val vm = model.dashboard
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
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
                item {
                    val nd = d.nextDue
                    WalletCard(
                        stringResource(R.string.total_due),
                        d.summary.pendingAmount,
                        when {
                            nd == null -> stringResource(R.string.no_dues_thanks)
                            Dates.isPast(nd.dueDate) -> stringResource(R.string.overdue_since, dateText(nd.dueDate))
                            else -> stringResource(R.string.next_due_on, dateText(nd.dueDate))
                        },
                    )
                }
                d.allocation?.let { a ->
                    item {
                        AppCard {
                            Column {
                                Text(stringResource(R.string.my_room), style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(4.dp))
                                LabelValue(stringResource(R.string.property), listOfNotNull(a.propertyCode, a.city).joinToString(", "))
                                a.propertyType?.let { LabelValue(stringResource(R.string.type), it) }
                                LabelValue(stringResource(R.string.monthly_rent), Money.format(a.baseRent))
                                LabelValue(stringResource(R.string.deposit_paid), Money.format(a.depositCollected) + " / " + Money.format(a.depositAgreed))
                                LabelValue(stringResource(R.string.since), dateText(a.allocationDate))
                                LabelValue(stringResource(R.string.owner), a.ownerName ?: "—")
                                if (!a.ownerPhone.isNullOrBlank()) {
                                    Spacer(Modifier.height(6.dp))
                                    OutlinedButton(onClick = { dial(ctx, a.ownerPhone!!) }) {
                                        Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.call_owner))
                                    }
                                }
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.recent_invoices)) }
                if (d.recentInvoices.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices)) }
                items(d.recentInvoices, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = false) { openInvoice(inv.id) } }
                item {
                    Text(stringResource(R.string.tenant_pay_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun TenantInvoicesScreen(openInvoice: (Int) -> Unit) {
    val vm = hiltViewModel<TenantInvoicesViewModel>().invoices
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.my_invoices)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices)) }
                items(list, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = false) { openInvoice(inv.id) } }
            }
        }
    }
}

@Composable
fun TenantInvoiceDetailScreen(id: Int, onBack: () -> Unit, openReceipt: (String) -> Unit) {
    val vm = hiltViewModel<TenantInvoiceDetailViewModel>().invoice
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.invoice), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { inv -> InvoiceBody(inv, showTenant = false, openReceipt = openReceipt) }
    }
}

@Composable
fun TenantPaymentsScreen(openReceipt: (String) -> Unit) {
    val vm = hiltViewModel<TenantPaymentsViewModel>().payments
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.payment_history)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WalletCard(stringResource(R.string.total_paid), d.totalPaid, stringResource(R.string.outstanding_x, Money.format(d.outstanding))) }
                if (d.payments.isEmpty()) item { EmptyState(stringResource(R.string.no_payments)) }
                items(d.payments, key = { it.id }) { p ->
                    PaymentRow(p.periodLabel ?: (p.invoiceNumber ?: ""), listOfNotNull(dateText(p.paymentDate), p.paymentMethodLabel, p.receiptNumber).joinToString(" · "), p.amount) { openReceipt(p.receiptNumber) }
                }
            }
        }
    }
}

@Composable
fun TenantMoreScreen(open: (String) -> Unit) {
    ScreenScaffold(stringResource(R.string.nav_more)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow(Icons.Filled.QuestionAnswer, Palette.Warning, stringResource(R.string.my_queries), stringResource(R.string.my_queries_sub)) { open("ten/queries") }
            MenuRow(Icons.Filled.Notifications, Palette.Info, stringResource(R.string.notifications)) { open("notifications") }
            MenuRow(Icons.Filled.Settings, Palette.Muted, stringResource(R.string.settings), stringResource(R.string.settings_sub)) { open("settings") }
        }
    }
}

@Composable
fun TenantQueriesScreen(onBack: () -> Unit, newQuery: () -> Unit, openQuery: (Int) -> Unit = {}) {
    val vm = hiltViewModel<TenantQueriesViewModel>().queries
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.my_queries), onBack = onBack, actions = {
        IconButton(onClick = newQuery) { Icon(Icons.Filled.SupportAgent, stringResource(R.string.raise_query)) }
    }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_queries)) }
                items(list, key = { it.id }) { q ->
                    AppCard(onClick = { openQuery(q.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(q.subject, fontWeight = FontWeight.SemiBold)
                                Text(q.ticketId + " · " + dateText(q.createdAt) + " · " + (q.priorityLabel ?: q.priority),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            StatusChip(q.status, q.statusLabel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RaiseQueryScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val model: RaiseQueryViewModel = hiltViewModel()
    val act by model.submitAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onDone() }
    var subject by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf("medium") }
    var tried by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.raise_query), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.raise_query_sub), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(subject, { subject = it.take(255) }, label = { Text(stringResource(R.string.subject)) }, singleLine = true,
                isError = tried && subject.isBlank(), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(description, { description = it.take(5000) }, label = { Text(stringResource(R.string.describe_issue)) },
                isError = tried && description.isBlank(), minLines = 4, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.priority), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("low" to R.string.pr_low, "medium" to R.string.pr_medium, "high" to R.string.pr_high).forEach { (v, l) ->
                    FilterChip(selected = priority == v, onClick = { priority = v }, label = { Text(stringResource(l)) })
                }
            }
            (act as? ActionState.Failed)?.let { Text(errorText(it.error), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = { tried = true; if (subject.isNotBlank() && description.isNotBlank()) model.submit(NewQuery(subject.trim(), description.trim(), priority)) },
                enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.submit), fontWeight = FontWeight.Bold)
            }
        }
    }
}
