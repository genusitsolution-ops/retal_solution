package com.genusit.prms.ui.owner

import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.EventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.dial
import com.genusit.prms.ui.theme.Palette

@Composable
fun SearchField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { Text(stringResource(R.string.search)) },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
fun OwnerTenantsScreen(openTenant: (Int) -> Unit, addTenant: (() -> Unit)? = null) {
    val vm = hiltViewModel<OwnerTenantsViewModel>().tenants
    val state by vm.state.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    androidx.compose.runtime.LaunchedEffect(Unit) { if (state !is com.genusit.prms.ui.common.UiState.Loading) vm.refresh() }
    ScreenScaffold(stringResource(R.string.tenants), actions = {
        if (addTenant != null) androidx.compose.material3.IconButton(onClick = addTenant) {
            Icon(androidx.compose.material.icons.Icons.Filled.PersonAdd, stringResource(R.string.add_tenant))
        }
    }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            val shown = list.filter { query.isBlank() || it.fullName.contains(query, true) || (it.phone ?: "").contains(query) || (it.allocatedProperty ?: "").contains(query, true) }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SearchField(query) { query = it } }
                if (shown.isEmpty()) item { EmptyState(stringResource(R.string.no_tenants)) }
                items(shown, key = { it.id }) { t ->
                    AppCard(onClick = { openTenant(t.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Icons.Filled.Person, Palette.Brand)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(t.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(listOfNotNull(t.allocatedProperty ?: stringResource(R.string.not_allocated), t.phone).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (t.pendingAmount > 0) Text(Money.format(t.pendingAmount), color = Palette.Danger, fontWeight = FontWeight.Bold)
                                StatusChip(t.status, t.statusLabel)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OwnerTenantDetailScreen(
    id: Int, onBack: () -> Unit, openInvoice: (Int) -> Unit, edit: () -> Unit = {}, onDeleted: () -> Unit = onBack,
    openHistory: () -> Unit = {}, openDocument: (com.genusit.prms.domain.model.TenantDocType) -> Unit = {},
) {
    val model: OwnerTenantDetailViewModel = hiltViewModel()
    val vm = model.tenant
    val deleteState by model.deleteAction.state.collectAsStateWithLifecycle()
    val uploadState by model.uploadAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onDeleted() }
    val state by vm.state.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(Unit) { if (state !is com.genusit.prms.ui.common.UiState.Loading) vm.refresh() }
    val ctx = LocalContext.current
    ScreenScaffold(stringResource(R.string.tenant_details), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { t ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(t.fullName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                StatusChip(t.status, t.statusLabel)
                            }
                            Spacer(Modifier.height(8.dp))
                            LabelValue(stringResource(R.string.property), t.allocatedProperty ?: stringResource(R.string.not_allocated))
                            LabelValue(stringResource(R.string.phone), t.phone ?: "—")
                            LabelValue(stringResource(R.string.email), t.email ?: "—")
                            LabelValue(stringResource(R.string.doc_number_aadhaar), com.genusit.prms.domain.util.IdMask.mask(t.aadharNumber) ?: "—")
                            LabelValue(stringResource(R.string.doc_number_pan), t.panNumber?.takeIf { it.isNotBlank() } ?: "—")
                            LabelValue(stringResource(R.string.tenant_portal),
                                if (t.hasAccount) stringResource(R.string.yes) + (t.username?.takeIf { it.isNotBlank() }?.let { " · @$it" } ?: "") else stringResource(R.string.no),
                                if (t.hasAccount) Palette.Success else androidx.compose.ui.graphics.Color.Unspecified)
                            LabelValue(stringResource(R.string.aadhaar), stringResource(if (t.hasAadhaar) R.string.uploaded else R.string.not_uploaded))
                            LabelValue(stringResource(R.string.outstanding), Money.format(t.pendingAmount), if (t.pendingAmount > 0) Palette.Danger else Palette.Success, bold = true)
                            if (!t.phone.isNullOrBlank()) {
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.OutlinedButton(onClick = { dial(ctx, t.phone!!) }) {
                                    Icon(Icons.Filled.Call, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.call))
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            TenantManageActions(t.invoices.size, deleteState, onEdit = edit, onDelete = model::delete)
                            androidx.compose.material3.OutlinedButton(onClick = openHistory) {
                                Icon(Icons.Filled.History, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.full_history))
                            }
                        }
                    }
                }
                item {
                    TenantDocumentsCard(
                        hasAadhaar = t.hasAadhaar, hasPan = t.hasPanDoc, uploadState = uploadState,
                        onView = openDocument, newCaptureTarget = model::newCaptureTarget,
                        onUpload = { type, uri, consent, file -> model.upload(type, uri, consent, file) },
                    )
                }
                item { SectionHeader(stringResource(R.string.invoices)) }
                if (t.invoices.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices), Icons.Filled.Badge) }
                items(t.invoices, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = false) { openInvoice(inv.id) } }
            }
        }
    }
}

private val INVOICE_FILTERS = listOf("" to R.string.all, "due" to R.string.all_due, "pending" to R.string.st_pending, "partial" to R.string.st_partial, "overdue" to R.string.st_overdue, "paid" to R.string.st_paid)

/** Invoice list; with [onlyOpen] it is the "pick an invoice to collect" screen behind the FAB. */
@Composable
fun OwnerInvoicesScreen(openInvoice: (Int) -> Unit, onlyOpen: Boolean = false, onBack: (() -> Unit)? = null, initialFilter: String = "", onCollect: (() -> Unit)? = null) {
    val vm = hiltViewModel<OwnerInvoicesViewModel>().invoices
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(if (onlyOpen) R.string.collect_select_invoice else R.string.invoices), onBack = onBack, actions = {
        // "Collect payment" (formerly the centre + button) lives on the Bills tab
        if (onCollect != null) androidx.compose.material3.FilledTonalButton(onClick = onCollect, modifier = Modifier.padding(end = 8.dp)) {
            Icon(androidx.compose.material.icons.Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.collect_payment))
        }
    }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            val shown = list.filter { inv ->
                (if (onlyOpen) inv.isOpen else filter.isEmpty() || (filter == "due" && inv.isOpen) || inv.status == filter) &&
                    (query.isBlank() || (inv.tenantName ?: "").contains(query, true) || inv.invoiceNumber.contains(query, true) || (inv.propertyCode ?: "").contains(query, true))
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SearchField(query) { query = it } }
                if (!onlyOpen) item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        INVOICE_FILTERS.forEach { (value, label) ->
                            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(stringResource(label)) })
                        }
                    }
                }
                if (shown.isEmpty()) item { EmptyState(stringResource(if (onlyOpen) R.string.no_pending_invoices else R.string.no_invoices)) }
                items(shown, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = true) { openInvoice(inv.id) } }
            }
        }
    }
}
