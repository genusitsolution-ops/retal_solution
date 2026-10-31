package com.genusit.prms.ui.owner

import com.genusit.prms.ui.common.ErrorBox
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.EventEffect
import androidx.hilt.navigation.compose.hiltViewModel
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
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
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.EmployeeInput
import com.genusit.prms.domain.model.EmployeeRemoved
import com.genusit.prms.domain.model.QueryDetail
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.EmployeeFormRules
import com.genusit.prms.domain.util.Validators
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.ConfirmDialog
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.InvoiceRow
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.LoadingBox
import com.genusit.prms.ui.common.PaymentRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.SuccessText
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette

/* ═════════════════════════ employees ═════════════════════════ */

@Composable
fun EmployeesScreen(onBack: () -> Unit, openEmployee: (Int) -> Unit, addEmployee: () -> Unit) {
    val vm = hiltViewModel<EmployeesViewModel>().employees
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = addEmployee, icon = { Icon(Icons.Filled.PersonAdd, null) }, text = { Text(stringResource(R.string.add_employee)) }) },
    ) { outer ->
        ScreenScaffold(stringResource(R.string.employees), onBack = onBack) { pad ->
            StateContent(state, vm::refresh, Modifier.padding(pad).padding(outer)) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_employees), Icons.Filled.Badge) }
                    items(list, key = { it.id }) { e ->
                        AppCard(onClick = { openEmployee(e.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Filled.Badge, if (e.status == "active") Palette.Brand else Palette.Muted)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(e.fullName, fontWeight = FontWeight.SemiBold)
                                    Text(listOfNotNull(e.phone, "@" + (e.username ?: ""), stringResource(R.string.n_rooms, e.assignedProps)).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(e.totalCollected, decimals = 0), fontWeight = FontWeight.Bold)
                                    StatusChip(e.status, stringResource(if (e.status == "active") R.string.st_active else R.string.st_inactive))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Add ([id] = 0) or edit a collection agent. Username is unique across all roles (server checks). */
@Composable
fun EmployeeFormScreen(id: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val model: EmployeeFormViewModel = hiltViewModel()
    val act by model.saveAction.state.collectAsStateWithLifecycle()
    val existing by model.existing.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onSaved() }
    var loaded by rememberSaveable { mutableStateOf(id == 0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    // copy the stored values into the form once, when they arrive
    LaunchedEffect(existing) {
        val e = (existing as? UiState.Data)?.value
        if (!loaded && e != null) {
            name = e.fullName; phone = e.phone.orEmpty(); email = e.email.orEmpty(); username = e.username.orEmpty(); loaded = true
        }
    }
    val problems = EmployeeFormRules.problems(id == 0, name, phone, email, username, password, confirm)
    fun bad(p: EmployeeFormRules.Problem) = p in problems && (tried || when (p) {
        // shown while typing once the field has something in it; everything else after "Save"
        EmployeeFormRules.Problem.PASSWORD_SHORT -> password.isNotEmpty()
        EmployeeFormRules.Problem.PASSWORD_MISMATCH -> confirm.length >= password.length && confirm.isNotEmpty()
        EmployeeFormRules.Problem.EMAIL -> email.contains('@') && email.substringAfter('@').contains('.')
        else -> false
    })
    val pwRule = stringResource(R.string.emp_password_rule, Validators.EMPLOYEE_PASSWORD_MIN)
    ScreenScaffold(stringResource(if (id == 0) R.string.add_employee else R.string.edit_employee), onBack = onBack) { pad ->
        if (!loaded) {
            val failed = existing as? UiState.Error
            if (failed != null) ErrorBox(failed.error, onRetry = model.existing::refresh, modifier = Modifier.padding(pad))
            else LoadingBox(Modifier.padding(pad))
            return@ScreenScaffold
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField(name, { name = it.take(150) }, stringResource(R.string.full_name), bad(EmployeeFormRules.Problem.NAME),
                supporting = if (bad(EmployeeFormRules.Problem.NAME)) stringResource(R.string.emp_err_name) else null, capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words)
            FormField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, stringResource(R.string.phone), bad(EmployeeFormRules.Problem.PHONE), KeyboardType.Phone,
                supporting = if (bad(EmployeeFormRules.Problem.PHONE)) stringResource(R.string.emp_err_phone) else null)
            FormField(email, { email = it.trim().take(150) }, stringResource(R.string.email_optional), bad(EmployeeFormRules.Problem.EMAIL), KeyboardType.Email, autoCorrect = false,
                supporting = if (bad(EmployeeFormRules.Problem.EMAIL)) stringResource(R.string.emp_err_email) else null)
            FormField(username, { username = it.trim().filter { c -> !c.isWhitespace() }.take(80) }, stringResource(R.string.username), bad(EmployeeFormRules.Problem.USERNAME), enabled = id == 0, autoCorrect = false,
                supporting = if (id == 0) stringResource(if (bad(EmployeeFormRules.Problem.USERNAME)) R.string.emp_err_username else R.string.signup_username_hint) else null)
            FormField(password, { password = it.take(100) }, stringResource(if (id == 0) R.string.password else R.string.new_password_optional),
                bad(EmployeeFormRules.Problem.PASSWORD_REQUIRED) || bad(EmployeeFormRules.Problem.PASSWORD_SHORT), password = true, passwordToggle = true,
                supporting = when {
                    bad(EmployeeFormRules.Problem.PASSWORD_REQUIRED) -> stringResource(R.string.emp_err_password_required) + " " + pwRule
                    bad(EmployeeFormRules.Problem.PASSWORD_SHORT) -> stringResource(R.string.emp_err_password_short, Validators.EMPLOYEE_PASSWORD_MIN)
                    id != 0 -> stringResource(R.string.emp_password_keep) + " " + pwRule
                    else -> pwRule
                })
            if (id == 0 || password.isNotEmpty()) FormField(confirm, { confirm = it.take(100) }, stringResource(R.string.emp_confirm_password), bad(EmployeeFormRules.Problem.PASSWORD_MISMATCH),
                password = true, passwordToggle = true, supporting = if (bad(EmployeeFormRules.Problem.PASSWORD_MISMATCH)) stringResource(R.string.passwords_differ) else null)
            Text(stringResource(R.string.employee_form_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionError(act)
            SubmitButton(stringResource(R.string.save), act) {
                tried = true
                if (problems.isEmpty()) {
                    val input = EmployeeInput(name.trim(), phone, email.ifBlank { null }, if (id == 0) username else null, password.ifEmpty { null })
                    model.save(input)
                }
            }
        }
    }
}

@Composable
fun EmployeeDetailScreen(id: Int, onBack: () -> Unit, edit: () -> Unit, openReceipt: (String) -> Unit) {
    val model: EmployeeDetailViewModel = hiltViewModel()
    val vm = model.employee
    val state by vm.state.collectAsStateWithLifecycle()
    val pState by model.properties.state.collectAsStateWithLifecycle()
    val aState by model.assignAction.state.collectAsStateWithLifecycle()
    val sState by model.status.state.collectAsStateWithLifecycle()
    val rState by model.removeAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onBack() }
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(stringResource(R.string.employee), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { e ->
            val selected = remember(e) { mutableStateMapOf<Int, Boolean>().apply { e.assignedProperties.forEach { put(it.id, true) } } }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(e.fullName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                StatusChip(e.status, stringResource(if (e.status == "active") R.string.st_active else R.string.st_inactive))
                            }
                            LabelValue(stringResource(R.string.username), e.username ?: "—")
                            LabelValue(stringResource(R.string.phone), e.phone ?: "—")
                            LabelValue(stringResource(R.string.email), e.email ?: "—")
                            LabelValue(stringResource(R.string.last_login), dateText(e.lastLogin))
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = edit) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.edit)) }
                                OutlinedButton(onClick = { model.setActive(e.status != "active") }) {
                                    Text(stringResource(if (e.status == "active") R.string.deactivate else R.string.activate))
                                }
                            }
                            ActionError(sState)
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.assigned_rooms)) }
                item {
                    AppCard {
                        Column {
                            val rooms = (pState as? UiState.Data)?.value.orEmpty()
                            if (rooms.isEmpty()) Text(stringResource(R.string.no_properties), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            rooms.forEach { r ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selected[r.id] == true, onCheckedChange = { selected[r.id] = it })
                                    Text(r.propertyCode + " · " + r.city)
                                }
                            }
                            ActionError(aState)
                            if (aState is ActionState.Done<*>) SuccessText(stringResource(R.string.saved))
                            Button(onClick = { model.assign(selected.filterValues { it }.keys.toList()) }, enabled = aState !is ActionState.Running) {
                                Text(stringResource(R.string.save_assignments))
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.recent_collections)) }
                if (e.recentCollections.isEmpty()) item { EmptyState(stringResource(R.string.no_collections)) }
                items(e.recentCollections, key = { it.id }) { c ->
                    PaymentRow(c.tenantName ?: "—", listOfNotNull(dateText(c.collectionDate), c.propertyCode, c.receiptNumber).joinToString(" · "), c.amount) { openReceipt(c.receiptNumber) }
                }
                item {
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text(stringResource(R.string.delete_employee_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ActionError(rState)
                    OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete_employee), color = Palette.Danger)
                    }
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.delete_employee), stringResource(R.string.delete_employee_confirm), stringResource(R.string.delete),
        onConfirm = { confirmDelete = false; model.remove() }, onDismiss = { confirmDelete = false })
}

/* ═════════════════════════ complaints (owner) ═════════════════════════ */

private val QUERY_FILTERS = listOf("" to R.string.all, "open" to R.string.st_open, "in_progress" to R.string.st_in_progress, "resolved" to R.string.st_resolved, "closed" to R.string.st_closed)

@Composable
fun OwnerQueriesScreen(onBack: () -> Unit, openQuery: (Int) -> Unit, initialFilter: String = "") {
    val vm = hiltViewModel<OwnerQueriesViewModel>().queries
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(initialFilter) }
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }
    ScreenScaffold(stringResource(R.string.complaints), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            val shown = list.filter { filter.isEmpty() || it.status == filter }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUERY_FILTERS.forEach { (v, l) -> FilterChip(selected = filter == v, onClick = { filter = v }, label = { Text(stringResource(l)) }) }
                    }
                }
                if (shown.isEmpty()) item { EmptyState(stringResource(R.string.no_queries), Icons.Filled.QuestionAnswer) }
                items(shown, key = { it.id }) { q ->
                    AppCard(onClick = { openQuery(q.id) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(q.subject, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(q.tenantName, q.ticketId, dateText(q.createdAt), q.priorityLabel).joinToString(" · "),
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

/** Ticket conversation for owner and tenant. The owner can also change the status. */
@Composable
fun QueryDetailScreen(id: Int, role: Role, onBack: () -> Unit) {
    val model: QueryDetailViewModel = hiltViewModel()
    val vm = model.detail
    val state by vm.state.collectAsStateWithLifecycle()
    val rState by model.reply.state.collectAsStateWithLifecycle()
    var message by rememberSaveable { mutableStateOf("") }
    var newStatus by rememberSaveable { mutableStateOf("") }
    EventEffect(model) { if (it == ScreenEvent.ReplySent) { message = ""; newStatus = "" } }

    ScreenScaffold(stringResource(R.string.complaint), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { q: QueryDetail ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(q.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusChip(q.status, q.statusLabel)
                            }
                            Text(listOfNotNull(q.ticketId, q.tenantName, q.priorityLabel, dateText(q.createdAt)).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(q.description)
                        }
                    }
                }
                items(q.responses, key = { it.id }) { r ->
                    val mine = r.responderType == role.api
                    AppCard {
                        Column {
                            Text(stringResource(if (r.responderType == "owner") R.string.owner else R.string.tenant) + " · " + dateText(r.createdAt),
                                style = MaterialTheme.typography.labelMedium, color = if (mine) Palette.Brand else Palette.Muted, fontWeight = FontWeight.SemiBold)
                            Text(r.message)
                        }
                    }
                }
                item {
                    if (q.isClosed && role == Role.TENANT) {
                        Text(stringResource(R.string.ticket_closed), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(message, { message = it.take(2000) }, stringResource(R.string.your_reply), singleLine = false, minLines = 3)
                        if (role == Role.OWNER) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            QUERY_FILTERS.drop(1).forEach { (v, l) ->
                                FilterChip(selected = (newStatus.ifEmpty { q.status }) == v, onClick = { newStatus = v }, label = { Text(stringResource(l)) })
                            }
                        }
                        ActionError(rState)
                        SubmitButton(stringResource(R.string.send), rState) {
                            val st = newStatus.takeIf { it.isNotEmpty() && it != q.status }
                            model.send(message, st)
                        }
                    }
                }
            }
        }
    }
}

/* ═════════════════════════ reports ═════════════════════════ */

@Composable
fun ReportsScreen(onBack: () -> Unit, openInvoice: (Int) -> Unit, openReceipt: (String) -> Unit) {
    val model: ReportsViewModel = hiltViewModel()
    var tab by rememberSaveable { mutableStateOf(0) }
    val today = Dates.today()
    var from by rememberSaveable { mutableStateOf(today.substring(0, 8) + "01") }
    var to by rememberSaveable { mutableStateOf(today) }
    val rangeOk = Dates.parse(from) != null && Dates.parse(to) != null
    ScreenScaffold(stringResource(R.string.reports), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(16.dp, 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(R.string.collections_report, R.string.revenue_report, R.string.pending_report).forEachIndexed { i, l ->
                    FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(stringResource(l)) })
                }
            }
            if (tab != 2) Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) { FormField(from, { from = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, stringResource(R.string.from_date), Dates.parse(from) == null) }
                Column(Modifier.weight(1f)) { FormField(to, { to = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, stringResource(R.string.to_date), Dates.parse(to) == null) }
            }
            when (tab) {
                0 -> if (rangeOk) CollectionsReportBody(model, from, to, openReceipt)
                1 -> if (rangeOk) RevenueReportBody(model, from, to)
                else -> PendingReportBody(model, openInvoice)
            }
        }
    }
}

@Composable
private fun CollectionsReportBody(model: ReportsViewModel, from: String, to: String, openReceipt: (String) -> Unit) {
    val vm = model.collections
    LaunchedEffect(from, to) { vm.load(DateRange(from, to)) }
    val state by vm.state.collectAsStateWithLifecycle()
    StateContent(state, vm::refresh) { r ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { WalletCard(stringResource(R.string.received_in_period), r.totalCollected, stringResource(R.string.outstanding_x, Money.format(r.pendingTotal))) }
            item {
                AppCard {
                    Column {
                        LabelValue(stringResource(R.string.collected_by_owner), Money.format(r.ownerCollected))
                        r.employeeSummary.forEach { e -> LabelValue(e.fullName + " (" + e.txns + ")", Money.format(e.total)) }
                    }
                }
            }
            if (r.collections.isEmpty()) item { EmptyState(stringResource(R.string.no_payments)) }
            items(r.collections, key = { it.receiptNumber }) { c ->
                PaymentRow(c.tenantName ?: "—", listOfNotNull(dateText(c.collectionDate), c.paymentModeLabel, c.empName, c.receiptNumber).joinToString(" · "), c.amount) {
                    openReceipt(c.receiptNumber)
                }
            }
        }
    }
}

@Composable
private fun RevenueReportBody(model: ReportsViewModel, from: String, to: String) {
    val vm = model.revenue
    LaunchedEffect(from, to) { vm.load(DateRange(from, to)) }
    val state by vm.state.collectAsStateWithLifecycle()
    StateContent(state, vm::refresh) { rows ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                WalletCard(stringResource(R.string.collected), rows.sumOf { it.collected },
                    stringResource(R.string.billed) + " " + Money.format(rows.sumOf { it.billed }) + " · " + stringResource(R.string.outstanding_x, Money.format(rows.sumOf { it.outstanding })))
            }
            if (rows.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices)) }
            items(rows, key = { it.ym }) { r ->
                AppCard {
                    Column {
                        Text(r.label, fontWeight = FontWeight.SemiBold)
                        LabelValue(stringResource(R.string.billed), Money.format(r.billed))
                        LabelValue(stringResource(R.string.collected), Money.format(r.collected), Palette.Success)
                        LabelValue(stringResource(R.string.balance), Money.format(r.outstanding), if (r.outstanding > 0) Palette.Danger else Palette.Success, bold = true)
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingReportBody(model: ReportsViewModel, openInvoice: (Int) -> Unit) {
    val vm = model.pending
    LaunchedEffect(Unit) { vm.load(Unit) }
    val state by vm.state.collectAsStateWithLifecycle()
    StateContent(state, vm::refresh) { r ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { WalletCard(stringResource(R.string.pending_dues), r.totalPending, stringResource(R.string.pending_invoices_n, r.invoices.size)) }
            if (r.invoices.isEmpty()) item { EmptyState(stringResource(R.string.no_pending_invoices)) }
            items(r.invoices, key = { it.id }) { inv -> InvoiceRow(inv, showTenant = true) { openInvoice(inv.id) } }
        }
    }
}

/* ═════════════════════════ referral program ═════════════════════════ */

@Composable
fun ReferralScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<ReferralViewModel>().referrals
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var copied by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.refer_earn), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { r ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!r.enabled) {
                    item { EmptyState(stringResource(R.string.referral_disabled), Icons.Filled.Redeem) }
                    return@LazyColumn
                }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(stringResource(R.string.your_referral_code), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(r.code ?: "—", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Palette.Brand)
                            Text(stringResource(R.string.referral_reward_x, r.rewardDays) + (r.conditionLabel?.let { "\n$it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { shareText(ctx, r.shareText ?: (r.code ?: "")) }) {
                                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share))
                                }
                                OutlinedButton(onClick = { copyText(ctx, r.shareUrl ?: r.code ?: ""); copied = true }) {
                                    Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.copy_link))
                                }
                            }
                            if (copied) SuccessText(stringResource(R.string.copied))
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(
                            stringResource(R.string.referrals_total) to r.stats.total.toString(),
                            stringResource(R.string.st_pending) to r.stats.pending.toString(),
                            stringResource(R.string.days_earned) to r.stats.daysEarned.toString(),
                        ).forEach { (l, v) ->
                            AppCard(Modifier.weight(1f)) {
                                Column { Text(v, style = MaterialTheme.typography.titleLarge); Text(l, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.referred_owners)) }
                if (r.items.isEmpty()) item { EmptyState(stringResource(R.string.no_referrals), Icons.Filled.Redeem) }
                items(r.items, key = { it.id }) { it2 ->
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(it2.referredName, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(dateText(it2.createdAt), it2.statusReason).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                StatusChip(it2.status, it2.statusLabel)
                                if (it2.status == "rewarded") Text(stringResource(R.string.plus_days_x, it2.rewardDays), color = Palette.Success, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shareText(ctx: Context, text: String) {
    val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { ctx.startActivity(Intent.createChooser(send, null)) }
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("referral", text))
}
