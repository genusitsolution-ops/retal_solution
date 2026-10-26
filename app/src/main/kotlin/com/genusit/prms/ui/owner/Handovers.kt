package com.genusit.prms.ui.owner

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.CollectionDashboard
import com.genusit.prms.domain.model.CollectionEntry
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.CollectionPage
import com.genusit.prms.domain.model.EmployeeCollectionRow
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.HandoverMethod
import com.genusit.prms.domain.model.SettlementDetail
import com.genusit.prms.domain.model.SettlementSummary
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.ChoiceField
import com.genusit.prms.ui.common.ConfirmDialog
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StatTile
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.TextDialog
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette

/*
 * Owner "Collections & Handovers" (app 1.7): the same figures as the website's Collection Report,
 * employee-wise totals, and review of the cash employees hand over. A handover is money already
 * collected from tenants — it is never shown or counted as new income.
 */

/* ═════════════════════════ shared pieces ═════════════════════════ */

private fun handoverColors(status: String): Pair<Color, Color> = when (status) {
    "confirmed", "settled" -> Palette.Success to Palette.SuccessSoft
    "partially_confirmed", "partial" -> Palette.Info to Palette.InfoSoft
    "submitted" -> Palette.Warning to Palette.WarningSoft
    "rejected", "pending" -> Palette.Danger to Palette.DangerSoft
    else -> Palette.Muted to Color(0xFFF1F5F9)
}

@Composable
fun HandoverChip(status: String, label: String?) {
    val (fg, bg) = handoverColors(status)
    Text(label ?: status, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp))
}

/** Date range with quick periods (this month / 3 months / this year). */
@Composable
fun RangeFields(from: String, to: String, onChange: (String, String) -> Unit) {
    var f by rememberSaveable(from) { mutableStateOf(from) }
    var t by rememberSaveable(to) { mutableStateOf(to) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                R.string.period_this_month to Dates.thisMonth(),
                R.string.period_3_months to Dates.lastMonths(3),
                R.string.period_this_year to Dates.thisYear(),
            ).forEach { (label, range) ->
                FilterChip(selected = from == range.first && to == range.second, onClick = { onChange(range.first, range.second) }, label = { Text(stringResource(label)) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                FormField(f, { v -> f = v.filter { it.isDigit() || it == '-' }.take(10); if (Dates.parse(f) != null && f.length == 10) onChange(f, t) },
                    stringResource(R.string.from_date), Dates.parse(f) == null && f.isNotEmpty())
            }
            Column(Modifier.weight(1f)) {
                FormField(t, { v -> t = v.filter { it.isDigit() || it == '-' }.take(10); if (Dates.parse(t) != null && t.length == 10) onChange(f, t) },
                    stringResource(R.string.to_date), Dates.parse(t) == null && t.isNotEmpty())
            }
        }
    }
}

@Composable
private fun CollectionCard(c: CollectionEntry, openReceipt: (String) -> Unit) {
    AppCard(onClick = { openReceipt(c.receiptNumber) }) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.tenantName ?: "—", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(dateText(c.collectionDate), c.propertyCode, c.paymentModeLabel).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(c.amount), fontWeight = FontWeight.Bold, color = Palette.Success)
            }
            Spacer(Modifier.padding(top = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(listOfNotNull(c.collectedByLabel, c.receiptNumber, c.invoiceNumber).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 2)
                HandoverChip(c.settlementStatus, c.settlementStatusLabel)
            }
            if (c.awaiting > 0) Text(stringResource(R.string.ho_awaiting_x, Money.format(c.awaiting)), style = MaterialTheme.typography.labelSmall, color = Palette.Warning)
        }
    }
}

@Composable
fun SettlementCard(s: SettlementSummary, showEmployee: Boolean, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (showEmployee) s.employeeName ?: "—" else s.settlementNumber, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(if (showEmployee) s.settlementNumber else null, dateText(s.handoverDate), s.methodLabel).joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(s.amountSubmitted), fontWeight = FontWeight.Bold)
                    HandoverChip(s.status, s.statusLabel)
                }
            }
            if (s.isConfirmed && s.amountConfirmed != null) Text(stringResource(R.string.ho_confirmed_x, Money.format(s.amountConfirmed!!)), style = MaterialTheme.typography.labelMedium, color = Palette.Success)
            if (s.status == "rejected" && !s.reviewNote.isNullOrBlank()) Text(s.reviewNote!!, style = MaterialTheme.typography.labelMedium, color = Palette.Danger)
        }
    }
}

private val SETTLEMENT_STATUSES = listOf(
    "" to R.string.all, "submitted" to R.string.ho_st_submitted, "partially_confirmed" to R.string.ho_st_partly,
    "confirmed" to R.string.ho_st_confirmed, "rejected" to R.string.ho_st_rejected, "cancelled" to R.string.ho_st_cancelled,
)

@Composable
fun SettlementStatusChips(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SETTLEMENT_STATUSES.forEach { (v, l) -> FilterChip(selected = selected == v, onClick = { onSelect(v) }, label = { Text(stringResource(l)) }) }
    }
}

@Composable
private fun Pager(page: Int, pages: Int, onPage: (Int) -> Unit) {
    if (pages <= 1) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { onPage(page - 1) }, enabled = page > 1) { Text(stringResource(R.string.ho_prev)) }
        Text(stringResource(R.string.ho_page_x, page, pages), style = MaterialTheme.typography.labelLarge)
        TextButton(onClick = { onPage(page + 1) }, enabled = page < pages) { Text(stringResource(R.string.ho_next)) }
    }
}

private fun LazyListScope.collectionItems(page: CollectionPage, openReceipt: (String) -> Unit, onPage: (Int) -> Unit) {
    if (page.items.isEmpty()) item { EmptyState(stringResource(R.string.no_collections)) }
    items(page.items, key = { "c" + it.id }) { c -> CollectionCard(c, openReceipt) }
    item { Pager(page.page, page.pages, onPage) }
}

/* ═════════════════════════ dashboard ═════════════════════════ */

private val DASH_TABS = listOf(R.string.ho_tab_overview, R.string.ho_tab_employees, R.string.ho_tab_collections, R.string.ho_tab_handovers)

@Composable
fun CollectionDashboardScreen(onBack: () -> Unit, openEmployee: (Int) -> Unit, openSettlement: (Int) -> Unit, openReceipt: (String) -> Unit) {
    val model: CollectionDashboardViewModel = hiltViewModel()
    val filter by model.filter.collectAsStateWithLifecycle()
    val query by model.query.collectAsStateWithLifecycle()
    val state by model.dashboard.state.collectAsStateWithLifecycle()
    val sState by model.settlements.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var showFilters by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.ho_dashboard_title), onBack = onBack, actions = {
        IconButton(onClick = { showFilters = true }) { Icon(Icons.Filled.FilterList, stringResource(R.string.ho_filters)) }
        IconButton(onClick = model::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) }
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(16.dp, 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DASH_TABS.forEachIndexed { i, l -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(l)) }) }
            }
            if (tab == 3) {
                Column(Modifier.padding(horizontal = 16.dp)) { SettlementStatusChips(query.status) { model.setQuery(query.copy(status = it, page = 1)) } }
                StateContent(sState, model.settlements::refresh) { page ->
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (page.items.isEmpty()) item { EmptyState(stringResource(R.string.ho_no_handovers)) }
                        items(page.items, key = { it.id }) { s -> SettlementCard(s, showEmployee = true) { openSettlement(s.id) } }
                        item { Pager(page.page, page.pages) { p -> model.setQuery(query.copy(page = p)) } }
                    }
                }
            } else {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    RangeFields(filter.from, filter.to) { f, t -> model.setFilter(filter.copy(from = f, to = t)) }
                    if (filter.activeCount > 0) TextButton(onClick = { model.setFilter(CollectionFilter(from = filter.from, to = filter.to)) }) {
                        Text(stringResource(R.string.ho_clear_filters_n, filter.activeCount))
                    }
                }
                StateContent(state, model.dashboard::refresh) { d ->
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (tab) {
                            0 -> overview(d, openPending = { model.setQuery(SettlementQuery(status = "submitted")); tab = 3 }, openEmployees = { tab = 1 })
                            1 -> {
                                if (d.employees.isEmpty()) item { EmptyState(stringResource(R.string.no_employees), Icons.Filled.Groups) }
                                items(d.employees, key = { it.id }) { e -> EmployeeCollectionCard(e) { openEmployee(e.id) } }
                            }
                            else -> collectionItems(d.collections, openReceipt) { model.page(it) }
                        }
                    }
                    if (showFilters) FilterDialog(filter, d, onApply = { model.setFilter(it); showFilters = false }, onDismiss = { showFilters = false })
                }
            }
        }
    }
}

private fun LazyListScope.overview(d: CollectionDashboard, openPending: () -> Unit, openEmployees: () -> Unit) {
    val s = d.summary
    if (s.pendingSettlementCount > 0) item {
        AppCard(onClick = openPending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.WarningAmber, Palette.Warning)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.ho_pending_alert, s.pendingSettlementCount, Money.format(s.awaitingConfirmation)),
                    fontWeight = FontWeight.SemiBold, color = Palette.Warning, modifier = Modifier.weight(1f))
            }
        }
    }
    item { WalletCard(stringResource(R.string.received_in_period), s.totalCollected, stringResource(R.string.ho_n_txns, s.transactions)) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.Person, stringResource(R.string.ho_by_you), Money.format(s.ownerCollected), Palette.Brand, Modifier.weight(1f))
            StatTile(Icons.Filled.Groups, stringResource(R.string.ho_by_employees), Money.format(s.employeeCollected), Palette.Accent, Modifier.weight(1f), onClick = openEmployees)
        }
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(Icons.Filled.Payments, stringResource(R.string.pending_dues), Money.format(s.pendingDues), Palette.Danger, Modifier.weight(1f))
            StatTile(Icons.Filled.Groups, stringResource(R.string.ho_active_employees), s.activeEmployees.toString(), Palette.Info, Modifier.weight(1f), onClick = openEmployees)
        }
    }
    item { SectionHeader(stringResource(R.string.ho_handover_status)) }
    item {
        AppCard {
            Column {
                Text(stringResource(R.string.ho_all_dates_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LabelValue(stringResource(R.string.ho_handed_over_total), Money.format(s.handedOverTotal), Palette.Success, bold = true)
                LabelValue(stringResource(R.string.ho_awaiting_confirmation), Money.format(s.awaitingConfirmation), Palette.Warning)
                LabelValue(stringResource(R.string.ho_still_with_employees), Money.format(s.pendingSettlement), if (s.pendingSettlement > 0) Palette.Danger else Palette.Success, bold = true)
                LabelValue(stringResource(R.string.ho_handed_over_period), Money.format(s.handedOverInPeriod))
            }
        }
    }
    if (d.employees.isNotEmpty()) item { SectionHeader(stringResource(R.string.employees)) }
    items(d.employees.take(5), key = { "e" + it.id }) { e -> EmployeeCollectionCard(e, onClick = null) }
}

@Composable
private fun EmployeeCollectionCard(e: EmployeeCollectionRow, onClick: (() -> Unit)?) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Person, if (e.status == "active") Palette.Brand else Palette.Muted)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.code + " · " + stringResource(R.string.ho_n_txns, e.periodCollections), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Money.format(e.periodCollected), fontWeight = FontWeight.Bold, color = Palette.Success)
            }
            Text(stringResource(R.string.ho_cash_upi_other, Money.format(e.periodCash), Money.format(e.periodUpi), Money.format(e.periodOther)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ho_handed_x, Money.format(e.confirmed)), color = Palette.Success, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ho_pending_x, Money.format(e.outstanding)), color = if (e.outstanding > 0) Palette.Danger else Palette.Muted, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            Text(stringResource(R.string.ho_last_dates, dateText(e.lastCollectionDate), dateText(e.lastSettlementDate)),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterDialog(current: CollectionFilter, d: CollectionDashboard, onApply: (CollectionFilter) -> Unit, onDismiss: () -> Unit) {
    var f by androidx.compose.runtime.remember(current) { mutableStateOf(current) }
    val all = stringResource(R.string.all)
    val modes = paymentModes(com.genusit.prms.ui.common.UiState.Loading)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ho_filters)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoiceField(stringResource(R.string.employee), listOf(0 to all) + d.options.employees.map { it.id to it.fullName }, f.employeeId, { f = f.copy(employeeId = it) })
                ChoiceField(stringResource(R.string.property), listOf(0 to all) + d.options.properties.map { it.id to listOfNotNull(it.propertyCode, it.city).joinToString(" · ") }, f.propertyId, { f = f.copy(propertyId = it) })
                ChoiceField(stringResource(R.string.tenant), listOf(0 to all) + d.options.tenants.map { it.id to it.fullName }, f.tenantId, { f = f.copy(tenantId = it) })
                ChoiceField(stringResource(R.string.payment_mode), listOf("" to all) + modes.map { it.value to it.label }, f.mode, { f = f.copy(mode = it) })
                ChoiceField(stringResource(R.string.ho_collected_by), listOf("" to all, "owner" to stringResource(R.string.owner), "employee" to stringResource(R.string.employee)), f.collector, { f = f.copy(collector = it) })
                ChoiceField(stringResource(R.string.ho_handover_status), listOf(
                    "" to all, "pending" to stringResource(R.string.ho_cs_pending), "partial" to stringResource(R.string.ho_cs_partial),
                    "settled" to stringResource(R.string.ho_cs_settled), "owner" to stringResource(R.string.ho_cs_owner),
                ), f.settlement, { f = f.copy(settlement = it) })
            }
        },
        confirmButton = { TextButton(onClick = { onApply(f) }) { Text(stringResource(R.string.ho_apply), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = { onApply(CollectionFilter(from = f.from, to = f.to)) }) { Text(stringResource(R.string.ho_clear)) } },
    )
}

/* ═════════════════════════ one employee ═════════════════════════ */

@Composable
fun EmployeeCollectionScreen(onBack: () -> Unit, openSettlement: (Int) -> Unit, openReceipt: (String) -> Unit) {
    val model: EmployeeCollectionViewModel = hiltViewModel()
    val q by model.query.collectAsStateWithLifecycle()
    val state by model.detail.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var recording by rememberSaveable { mutableStateOf(false) }
    EventEffect(model) { if (it is ScreenEvent.HandoverSaved) { recording = false; openSettlement(it.settlementId) } }
    val title = (state as? com.genusit.prms.ui.common.UiState.Data)?.value?.employee?.fullName ?: stringResource(R.string.employee)
    ScreenScaffold(title, onBack = onBack, actions = { IconButton(onClick = model.detail::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } }) { pad ->
        StateContent(state, model.detail::refresh, Modifier.padding(pad)) { d ->
            val b = d.balance
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(d.employee.code + (d.employee.phone?.let { " · $it" } ?: ""), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item { WalletCard(stringResource(R.string.ho_still_with_employee), b.outstanding, stringResource(R.string.ho_of_collected_x, Money.format(b.collected), b.collections)) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.Filled.CheckCircle, stringResource(R.string.ho_handed_over_total), Money.format(b.confirmed), Palette.Success, Modifier.weight(1f))
                        StatTile(Icons.Filled.HourglassTop, stringResource(R.string.ho_awaiting_confirmation), Money.format(b.awaitingConfirmation), Palette.Warning, Modifier.weight(1f))
                    }
                }
                item {
                    AppCard {
                        Column {
                            LabelValue(stringResource(R.string.ho_last_collection), dateText(b.lastCollectionDate))
                            LabelValue(stringResource(R.string.ho_last_handover), dateText(b.lastSettlementDate))
                        }
                    }
                }
                if (b.available > 0) item {
                    Button(onClick = { recording = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AccountBalanceWallet, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ho_record_received))
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(R.string.ho_tab_collections, R.string.ho_tab_handovers).forEachIndexed { i, l -> FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(l)) }) }
                    }
                }
                if (tab == 0) {
                    item { RangeFields(q.from, q.to) { f, t -> model.setQuery(q.copy(from = f, to = t, page = 1)) } }
                    item {
                        AppCard {
                            Column {
                                LabelValue(stringResource(R.string.ho_shown_total), Money.format(d.period.total), bold = true)
                                LabelValue(stringResource(R.string.mode_cash), Money.format(d.period.cash))
                                LabelValue("UPI", Money.format(d.period.upi))
                                LabelValue(stringResource(R.string.mode_other), Money.format(d.period.other))
                            }
                        }
                    }
                    collectionItems(d.collections, openReceipt) { p -> model.setQuery(q.copy(page = p)) }
                } else {
                    if (d.settlements.isEmpty()) item { EmptyState(stringResource(R.string.ho_no_handovers)) }
                    items(d.settlements, key = { "s" + it.id }) { s -> SettlementCard(s, showEmployee = false) { openSettlement(s.id) } }
                }
            }
            if (recording) RecordDialog(b.available, model.record.state.collectAsStateWithLifecycle().value, onConfirm = model::recordReceived,
                onDismiss = { recording = false; model.record.reset() })
        }
    }
}

/** Owner records money received from the employee (counted as confirmed at once). */
@Composable
private fun RecordDialog(available: Double, act: ActionState<*>, onConfirm: (HandoverInput) -> Unit, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf("") }
    var method by rememberSaveable { mutableStateOf(HandoverMethod.CASH) }
    var reference by rememberSaveable { mutableStateOf("") }
    var remarks by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val input = HandoverInput(Money.parse(amount), emptyList(), method, Dates.today(), reference, remarks)
    val problems = com.genusit.prms.domain.usecase.SubmitHandoverUseCase.problems(input, available)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ho_record_received)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ho_record_hint, Money.format(available)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(amount, { amount = amountInput(it) }, stringResource(R.string.amount), tried && problems.any { it.name.startsWith("AMOUNT") || it.name == "MORE_THAN_AVAILABLE" }, KeyboardType.Decimal)
                MethodChips(method) { method = it }
                FormField(reference, { reference = it.take(100) }, stringResource(R.string.ho_reference), tried && com.genusit.prms.domain.usecase.HandoverProblem.REFERENCE in problems)
                FormField(remarks, { remarks = it.take(500) }, stringResource(R.string.remarks))
                ActionError(act)
            }
        },
        confirmButton = {
            TextButton(onClick = { tried = true; if (problems.isEmpty()) onConfirm(input) }, enabled = act !is ActionState.Running) {
                Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun MethodChips(selected: HandoverMethod, onSelect: (HandoverMethod) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(HandoverMethod.CASH to R.string.mode_cash, HandoverMethod.UPI to R.string.ho_upi, HandoverMethod.BANK_TRANSFER to R.string.mode_bank, HandoverMethod.OTHER to R.string.mode_other)
            .forEach { (m, l) -> FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(stringResource(l)) }) }
    }
}

/* ═════════════════════════ one handover (owner and employee) ═════════════════════════ */

@Composable
fun SettlementDetailScreen(onBack: () -> Unit, openReceipt: (String) -> Unit) {
    val model: SettlementDetailViewModel = hiltViewModel()
    val state by model.detail.state.collectAsStateWithLifecycle()
    val act by model.review.state.collectAsStateWithLifecycle()
    var dialog by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(R.string.ho_handover), onBack = onBack, actions = { IconButton(onClick = model.detail::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } }) { pad ->
        StateContent(state, model.detail::refresh, Modifier.padding(pad)) { s ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    WalletCard(stringResource(R.string.ho_amount_submitted), s.amountSubmitted,
                        s.amountConfirmed?.let { stringResource(R.string.ho_confirmed_x, Money.format(it)) } ?: stringResource(R.string.ho_not_counted_yet))
                }
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.settlementNumber, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                HandoverChip(s.status, s.statusLabel)
                            }
                            LabelValue(stringResource(R.string.employee), s.employeeName ?: "—")
                            LabelValue(stringResource(R.string.ho_handover_date), dateText(s.handoverDate))
                            LabelValue(stringResource(R.string.ho_submitted_on), dateText(s.createdAt) + timeOf(s.createdAt))
                            LabelValue(stringResource(R.string.ho_method), s.methodLabel ?: s.method)
                            LabelValue(stringResource(R.string.ho_reference), s.referenceNo ?: "—")
                            if (s.reviewedAt != null) LabelValue(stringResource(R.string.ho_reviewed_on), dateText(s.reviewedAt) + timeOf(s.reviewedAt))
                            if (!s.remarks.isNullOrBlank()) LabelValue(stringResource(R.string.remarks), s.remarks!!)
                            if (!s.reviewNote.isNullOrBlank()) LabelValue(stringResource(if (s.status == "rejected") R.string.ho_rejection_reason else R.string.ho_owner_note), s.reviewNote!!,
                                if (s.status == "rejected") Palette.Danger else Color.Unspecified)
                            if (s.createdByType == "owner") Text(stringResource(R.string.ho_recorded_by_owner), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (s.isAwaiting) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionError(act)
                        if (model.owner) {
                            Button(onClick = { dialog = "confirm" }, modifier = Modifier.fillMaxWidth(), enabled = act !is ActionState.Running) { Text(stringResource(R.string.ho_confirm_received), fontWeight = FontWeight.Bold) }
                            OutlinedButton(onClick = { dialog = "reject" }, modifier = Modifier.fillMaxWidth(), enabled = act !is ActionState.Running) { Text(stringResource(R.string.ho_reject), color = Palette.Danger) }
                        } else {
                            OutlinedButton(onClick = { dialog = "cancel" }, modifier = Modifier.fillMaxWidth(), enabled = act !is ActionState.Running) { Text(stringResource(R.string.ho_cancel_handover), color = Palette.Danger) }
                        }
                    }
                }
                item { SettlementShareActions(s) }
                item { SectionHeader(stringResource(R.string.ho_collections_covered)) }
                items(s.items, key = { "i" + it.collectionId }) { it2 ->
                    AppCard(onClick = it2.receiptNumber?.let { r -> { openReceipt(r) } }) {
                        Column {
                            Row {
                                Text(it2.tenantName ?: "—", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(Money.format(it2.amountSubmitted), fontWeight = FontWeight.Bold)
                            }
                            Text(listOfNotNull(it2.receiptNumber, dateText(it2.collectionDate), it2.propertyCode, it2.paymentModeLabel).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (s.isConfirmed) Text(stringResource(R.string.ho_confirmed_x, Money.format(it2.amountConfirmed)), style = MaterialTheme.typography.labelMedium, color = Palette.Success)
                        }
                    }
                }
                if (s.audit.isNotEmpty()) item { SectionHeader(stringResource(R.string.ho_audit)) }
                items(s.audit.size) { i ->
                    val a = s.audit[i]
                    Text(dateText(a.createdAt) + timeOf(a.createdAt) + " · " + (a.description ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when (dialog) {
                "confirm" -> ConfirmAmountDialog(s, model::canConfirm, onConfirm = { amt, note -> dialog = ""; model.confirm(amt, note) }, onDismiss = { dialog = "" })
                "reject" -> TextDialog(stringResource(R.string.ho_reject), stringResource(R.string.reason), stringResource(R.string.ho_reject), required = true,
                    onConfirm = { dialog = ""; model.reject(it) }, onDismiss = { dialog = "" }, message = stringResource(R.string.ho_reject_hint))
                "cancel" -> ConfirmDialog(stringResource(R.string.ho_cancel_handover), stringResource(R.string.ho_cancel_confirm), stringResource(R.string.ho_cancel_handover),
                    onConfirm = { dialog = ""; model.cancel() }, onDismiss = { dialog = "" })
            }
        }
    }
}

private fun timeOf(v: String?): String = v?.takeIf { it.length >= 16 }?.substring(11, 16)?.let { ", $it" } ?: ""

@Composable
private fun ConfirmAmountDialog(s: SettlementDetail, valid: (Double?, Double) -> Boolean, onConfirm: (Double, String) -> Unit, onDismiss: () -> Unit) {
    var amount by rememberSaveable { mutableStateOf(Money.format(s.amountSubmitted, withSymbol = false).replace(",", "")) }
    var note by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val v = Money.parse(amount)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ho_confirm_received)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.ho_confirm_hint, Money.format(s.amountSubmitted)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                FormField(amount, { amount = amountInput(it) }, stringResource(R.string.ho_amount_received), tried && !valid(v, s.amountSubmitted), KeyboardType.Decimal)
                FormField(note, { note = it.take(500) }, stringResource(R.string.ho_note_optional))
            }
        },
        confirmButton = { TextButton(onClick = { tried = true; if (valid(v, s.amountSubmitted)) onConfirm(v!!, note) }) { Text(stringResource(R.string.ho_confirm), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/* ───────── share / print a handover receipt ───────── */

private data class HoLabels(val title: String, val employee: String, val owner: String, val date: String, val submitted: String,
                            val confirmed: String, val method: String, val reference: String, val status: String, val collections: String, val note: String)

@Composable
fun SettlementShareActions(s: SettlementDetail) {
    val ctx = LocalContext.current
    val months = stringArrayResource(R.array.months).toList()
    val l = HoLabels(stringResource(R.string.ho_receipt_title), stringResource(R.string.employee), stringResource(R.string.owner), stringResource(R.string.ho_handover_date),
        stringResource(R.string.ho_amount_submitted), stringResource(R.string.ho_amount_confirmed), stringResource(R.string.ho_method), stringResource(R.string.ho_reference),
        stringResource(R.string.status), stringResource(R.string.ho_collections_covered), stringResource(R.string.ho_not_income_note))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { shareSettlement(ctx, s, l, months) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share))
        }
        OutlinedButton(onClick = { printSettlement(ctx, s, l, months) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Print, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.print_pdf))
        }
    }
}

private fun settlementText(s: SettlementDetail, l: HoLabels, months: List<String>): String = buildString {
    appendLine("${l.title} ${s.settlementNumber}")
    appendLine("${l.employee}: ${s.employeeName ?: ""}")
    s.ownerName?.let { appendLine("${l.owner}: $it") }
    appendLine("${l.date}: ${Dates.display(s.handoverDate, months)}")
    appendLine("${l.submitted}: ${Money.format(s.amountSubmitted)}")
    s.amountConfirmed?.let { appendLine("${l.confirmed}: ${Money.format(it)}") }
    appendLine("${l.method}: ${s.methodLabel ?: s.method}" + (s.referenceNo?.let { " · ${l.reference}: $it" } ?: ""))
    appendLine("${l.status}: ${s.statusLabel ?: s.status}")
    append(l.note)
}

private fun shareSettlement(ctx: Context, s: SettlementDetail, l: HoLabels, months: List<String>) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, settlementText(s, l, months))
    runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
}

private fun esc(v: String) = v.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
private var printingView: WebView? = null

private fun printSettlement(ctx: Context, s: SettlementDetail, l: HoLabels, months: List<String>) {
    val rows = s.items.joinToString("") {
        "<tr><td>${esc(it.receiptNumber ?: "")}<br><span class=m>${esc(it.tenantName ?: "")} · ${esc(it.propertyCode ?: "")}</span></td>" +
            "<td>${esc(Dates.display(it.collectionDate, months))}</td><td class=r>${esc(Money.format(it.amountSubmitted))}</td></tr>"
    }
    val html = """<html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;color:#0f172a;padding:24px} h1{color:#4f46e5;font-size:22px;margin:0}
        table{width:100%;border-collapse:collapse;margin-top:12px} td{padding:7px;border-bottom:1px solid #e2e8f0;vertical-align:top} .r{text-align:right} .m{color:#64748b;font-size:12px}</style></head><body>
        <h1>${esc(l.title)} ${esc(s.settlementNumber)}</h1>
        <p>${esc(l.employee)}: <b>${esc(s.employeeName ?: "")}</b><br>${esc(l.owner)}: ${esc(s.ownerName ?: "")}<br>${esc(l.date)}: ${esc(Dates.display(s.handoverDate, months))}<br>
        ${esc(l.method)}: ${esc(s.methodLabel ?: s.method)}${s.referenceNo?.let { " · " + esc(l.reference) + ": " + esc(it) } ?: ""}<br>${esc(l.status)}: <b>${esc(s.statusLabel ?: s.status)}</b></p>
        <table><tr><td>${esc(l.submitted)}</td><td class=r><b>${esc(Money.format(s.amountSubmitted))}</b></td></tr>
        <tr><td>${esc(l.confirmed)}</td><td class=r><b>${esc(s.amountConfirmed?.let { Money.format(it) } ?: "—")}</b></td></tr></table>
        <h3>${esc(l.collections)}</h3><table>$rows</table><p class=m>${esc(l.note)}</p></body></html>"""
    val web = WebView(ctx).also { printingView = it }
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(s.settlementNumber, view.createPrintDocumentAdapter(s.settlementNumber), PrintAttributes.Builder().build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}
