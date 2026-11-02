package com.genusit.prms.ui.owner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Book
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.OpeningBalanceInfo
import com.genusit.prms.domain.model.OpeningCategory
import com.genusit.prms.domain.model.OpeningInput
import com.genusit.prms.domain.model.OpeningLine
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.TenantMoneySummary
import com.genusit.prms.domain.usecase.GetOpeningBalanceUseCase
import com.genusit.prms.domain.usecase.SaveOpeningBalanceUseCase
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.OpeningRules
import com.genusit.prms.domain.util.OpeningRules.Problem
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ChoiceField
import com.genusit.prms.ui.common.ErrorBox
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.LoadingBox
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/* ═════════════════════════ 1.12 Opening Balance – Migrated (existing tenants) ═════════════════════════ */

/** One editable line of the previous outstanding (text as typed). */
class LineDraft(category: String = OpeningCategory.RENT, period: String = "", description: String = "", amount: String = "") {
    var category by mutableStateOf(category)
    var period by mutableStateOf(period)
    var description by mutableStateOf(description)
    var amount by mutableStateOf(amount)
    fun toLine() = OpeningLine(category, period.trim(), description.trim(), Money.parse(amount) ?: if (amount.isBlank()) 0.0 else -1.0)
}

/**
 * The opening-balance form state, kept in the ViewModel so it survives rotation. Same fields as the web form;
 * the server validates and recalculates everything again.
 */
class OpeningDraft {
    var asOf by mutableStateOf("")
    var billingStart by mutableStateOf("")
    var previousRent by mutableStateOf("")
    val lines = mutableStateListOf(LineDraft(OpeningCategory.RENT), LineDraft(OpeningCategory.RENT), LineDraft("electricity"))
    var paidTotal by mutableStateOf("")
    var paymentsNote by mutableStateOf("")
    var notes by mutableStateOf("")
    var depositAgreed by mutableStateOf("")
    var depositCollected by mutableStateOf("")
    var filled = false

    fun fill(o: OpeningBalanceInfo) {
        asOf = o.asOfDate; billingStart = o.billingStart; previousRent = plain(o.previousMonthlyRent)
        lines.clear(); o.lines.forEach { lines += LineDraft(it.category, it.period, it.description, plain(it.amount)) }
        if (lines.isEmpty()) lines += LineDraft()
        paidTotal = o.previousPaidTotal?.let(::plain).orEmpty(); paymentsNote = o.previousPaymentsNote.orEmpty(); notes = o.notes.orEmpty()
        depositAgreed = plain(o.depositAgreed); depositCollected = plain(o.depositCollected)
        filled = true
    }

    fun lineValues(): List<OpeningLine> = lines.map { it.toLine() }
    fun totals() = OpeningRules.totals(lineValues().filter { it.amount >= 0 })

    fun problems(joiningDate: String?, withDeposit: Boolean): Set<Problem> =
        OpeningRules.check(asOf.trim(), billingStart.trim(), lineValues(), joiningDate,
            if (withDeposit) Money.parse(depositAgreed.ifBlank { "0" }) ?: -1.0 else null,
            if (withDeposit) Money.parse(depositCollected.ifBlank { "0" }) ?: -1.0 else null)

    fun toInput(withDeposit: Boolean) = OpeningInput(
        asOfDate = asOf.trim(), billingStart = billingStart.trim(), previousMonthlyRent = Money.parse(previousRent) ?: 0.0,
        lines = OpeningRules.effectiveLines(lineValues()), previousPaidTotal = Money.parse(paidTotal),
        previousPaymentsNote = paymentsNote.trim(), notes = notes.trim(),
        depositAgreed = if (withDeposit) Money.parse(depositAgreed.ifBlank { "0" }) else null,
        depositCollected = if (withDeposit) Money.parse(depositCollected.ifBlank { "0" }) else null,
    )

    private fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}

@Composable
private fun categoryLabel(key: String): String = stringResource(when (key) {
    "rent" -> R.string.ob_cat_rent; "electricity" -> R.string.ob_cat_electricity; "water" -> R.string.ob_cat_water
    "maintenance" -> R.string.ob_cat_maintenance; else -> R.string.ob_cat_other
})

@Composable
private fun problemText(p: Problem): String = stringResource(when (p) {
    Problem.AS_OF_DATE -> R.string.ob_err_as_of
    Problem.AS_OF_FUTURE -> R.string.ob_err_as_of_future
    Problem.AS_OF_BEFORE_JOIN -> R.string.ob_err_as_of_before_join
    Problem.BILLING_START -> R.string.ob_err_start
    Problem.BILLING_START_BEFORE_AS_OF -> R.string.ob_err_start_before
    Problem.BILLING_START_TOO_LATE -> R.string.ob_err_start_late
    Problem.LINE_AMOUNT -> R.string.ob_err_line_amount
    Problem.LINE_MONTH -> R.string.ob_err_line_month
    Problem.LINE_MONTH_BILLED -> R.string.ob_err_line_billed
    Problem.DEPOSIT -> R.string.ob_err_deposit
})

/** Opening-balance fields (used by the Add Tenant form and the record / edit screen). */
@Composable
fun OpeningFields(draft: OpeningDraft, tried: Boolean, problems: Set<Problem>, showDeposit: Boolean, depositLocked: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FormField(draft.asOf, { v ->
            draft.asOf = v.filter { it.isDigit() || it == '-' }.take(10)
            if (draft.billingStart.isBlank()) OpeningRules.defaultBillingStart(draft.asOf)?.let { draft.billingStart = it }
        }, stringResource(R.string.ob_as_of), tried && (Problem.AS_OF_DATE in problems || Problem.AS_OF_FUTURE in problems || Problem.AS_OF_BEFORE_JOIN in problems),
            supporting = stringResource(R.string.ob_as_of_hint))
        FormField(draft.billingStart, { v -> draft.billingStart = v.filter { it.isDigit() || it == '-' }.take(7) }, stringResource(R.string.ob_billing_start),
            tried && problems.any { it in setOf(Problem.BILLING_START, Problem.BILLING_START_BEFORE_AS_OF, Problem.BILLING_START_TOO_LATE) },
            supporting = stringResource(R.string.ob_billing_start_hint))
        FormField(draft.previousRent, { draft.previousRent = amountInput(it) }, stringResource(R.string.ob_previous_rent), keyboard = KeyboardType.Decimal,
            supporting = stringResource(R.string.ob_previous_rent_hint))

        Text(stringResource(R.string.ob_lines_title), style = MaterialTheme.typography.titleMedium)
        draft.lines.forEachIndexed { i, l ->
            AppCard {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.ob_line_n, i + 1), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (draft.lines.size > 1) draft.lines.removeAt(i) else { l.amount = ""; l.period = ""; l.description = "" } }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.ob_remove_line))
                        }
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OpeningCategory.ALL.forEach { c ->
                            FilterChip(selected = l.category == c, onClick = { l.category = c }, label = { Text(categoryLabel(c), style = MaterialTheme.typography.labelSmall) })
                        }
                    }
                    FormField(l.period, { v -> l.period = v.filter { it.isDigit() || it == '-' }.take(7) }, stringResource(R.string.ob_line_month),
                        tried && l.period.isNotBlank() && (OpeningRules.yearMonth(l.period) == null || Problem.LINE_MONTH_BILLED in problems && OpeningRules.yearMonth(draft.billingStart)?.let { s -> OpeningRules.yearMonth(l.period)?.isBefore(s) == false } == true))
                    FormField(l.description, { l.description = it.take(120) }, stringResource(R.string.ob_line_details))
                    FormField(l.amount, { l.amount = amountInput(it) }, stringResource(R.string.amount),
                        tried && (l.period.isNotBlank() || l.description.isNotBlank() || l.amount.isNotBlank()) && (Money.parse(l.amount) ?: 0.0) <= 0.0, KeyboardType.Decimal)
                }
            }
        }
        OutlinedButton(onClick = { draft.lines += LineDraft("other") }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ob_add_line)) }
        val t = draft.totals()
        AppCard {
            Column {
                LabelValue(stringResource(R.string.ob_total_rent), Money.format(t.rent))
                LabelValue(stringResource(R.string.ob_total_other), Money.format(t.other))
                LabelValue(stringResource(R.string.ob_total), Money.format(t.total), Palette.Danger, bold = true)
            }
        }
        if (showDeposit) {
            FormField(draft.depositAgreed, { draft.depositAgreed = amountInput(it) }, stringResource(R.string.ob_deposit_agreed), tried && Problem.DEPOSIT in problems,
                KeyboardType.Decimal, enabled = !depositLocked)
            FormField(draft.depositCollected, { draft.depositCollected = amountInput(it) }, stringResource(R.string.ob_deposit_collected), tried && Problem.DEPOSIT in problems,
                KeyboardType.Decimal, enabled = !depositLocked)
            val pending = ((Money.parse(draft.depositAgreed) ?: 0.0) - (Money.parse(draft.depositCollected) ?: 0.0)).coerceAtLeast(0.0)
            LabelValue(stringResource(R.string.ob_deposit_pending), Money.format(pending), if (pending > 0) Palette.Danger else Color.Unspecified, bold = true)
            if (depositLocked) Text(stringResource(R.string.ob_deposit_locked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        FormField(draft.paidTotal, { draft.paidTotal = amountInput(it) }, stringResource(R.string.ob_paid_total), keyboard = KeyboardType.Decimal,
            supporting = stringResource(R.string.ob_paid_total_hint))
        FormField(draft.paymentsNote, { draft.paymentsNote = it.take(500) }, stringResource(R.string.ob_payments_note), singleLine = false, minLines = 2)
        FormField(draft.notes, { draft.notes = it.take(1000) }, stringResource(R.string.ob_notes), singleLine = false, minLines = 2)
        if (tried) problems.forEach { Text(problemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    }
}

/** Room, joining date, monthly charges and the opening balance of an existing tenant (Add Tenant form). */
@Composable
fun ExistingTenantSection(
    rooms: List<Property>, charges: List<Charge>, propertyId: Int, onProperty: (Int) -> Unit,
    joiningDate: String, onJoiningDate: (String) -> Unit, chosen: Map<Int, Boolean>, onCharge: (Int, Boolean) -> Unit,
    draft: OpeningDraft, tried: Boolean, problems: Set<Problem>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HorizontalDivider()
        Text(stringResource(R.string.ob_room_title), style = MaterialTheme.typography.titleMedium)
        ChoiceField(stringResource(R.string.room), rooms.map { it.id to "${it.propertyCode} · ${it.city} · ${Money.format(it.baseRent, decimals = 0)}" }, propertyId,
            { id -> onProperty(id); if (draft.previousRent.isBlank()) rooms.firstOrNull { it.id == id }?.let { r -> draft.previousRent = Money.format(r.baseRent, withSymbol = false, decimals = 0).replace(",", "") } },
            isError = tried && propertyId == 0, emptyText = stringResource(R.string.ob_no_vacant_room))
        FormField(joiningDate, { onJoiningDate(it.filter { c -> c.isDigit() || c == '-' }.take(10)) }, stringResource(R.string.ob_joining_date),
            tried && (Dates.parse(joiningDate) == null || Dates.parse(joiningDate)!!.isAfter(java.time.LocalDate.now())), supporting = stringResource(R.string.ob_joining_hint))
        if (charges.isNotEmpty()) {
            Text(stringResource(R.string.ob_monthly_charges), style = MaterialTheme.typography.titleSmall)
            charges.forEach { ch ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = chosen[ch.id] != false, onCheckedChange = { onCharge(ch.id, it) })
                    Text(ch.name + " · " + if (ch.isVariable) stringResource(R.string.per_unit_x, Money.format(ch.ratePerUnit ?: 0.0), ch.unitLabel ?: "unit") else Money.format(ch.fixedAmount ?: 0.0))
                }
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Book, null, tint = Palette.Warning); Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.ob_section_title), style = MaterialTheme.typography.titleMedium)
        }
        OpeningFields(draft, tried, problems, showDeposit = true)
    }
}

/* ───────── record / edit screen ───────── */

@HiltViewModel
class OpeningBalanceViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val getOpening: GetOpeningBalanceUseCase,
    private val saveOpening: SaveOpeningBalanceUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id: Int = handle.intArg()
    val allocationId: Int = handle.intArg("allocationId")
    val draft = OpeningDraft()
    val opening = loadable(autoLoad = id > 0) { getOpening(id) }
    val saveAction = action<OpeningBalanceInfo>()
    var reason by mutableStateOf("")

    fun save(withDeposit: Boolean) = saveAction.run(onSuccess = { o ->
        draft.fill(o); reason = ""
        if (id == 0) sendEvent(ScreenEvent.Saved) else opening.refresh()
    }) { saveOpening(id, allocationId, draft.toInput(withDeposit), reason) }
}

/** Opening balance of one tenancy: record it (allocationId) or view / edit it (id) with the change history. */
@Composable
fun OpeningBalanceScreen(onBack: () -> Unit, onSaved: () -> Unit = onBack, openInvoice: (Int) -> Unit = {}) {
    val model: OpeningBalanceViewModel = hiltViewModel()
    val state by model.opening.state.collectAsStateWithLifecycle()
    val act by model.saveAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onSaved() }
    var tried by rememberSaveable { mutableStateOf(false) }
    val current = (state as? UiState.Data)?.value
    LaunchedEffect(current) { if (current != null && !model.draft.filled) model.draft.fill(current) }

    ScreenScaffold(stringResource(R.string.ob_title), onBack = onBack) { pad ->
        if (model.id > 0 && current == null) {
            val failed = state as? UiState.Error
            if (failed != null) ErrorBox(failed.error, onRetry = model.opening::refresh, modifier = Modifier.padding(pad)) else LoadingBox(Modifier.padding(pad))
            return@ScreenScaffold
        }
        val withDeposit = current != null && current.depositEditable
        val problems = model.draft.problems(current?.joiningDate, withDeposit)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (current != null) {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(current.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            StatusChip(current.status, current.statusLabel)
                        }
                        Spacer(Modifier.height(6.dp))
                        LabelValue(stringResource(R.string.property), current.propertyCode ?: "—")
                        LabelValue(stringResource(R.string.ob_as_of), dateText(current.asOfDate))
                        LabelValue(stringResource(R.string.ob_billing_start), current.billingStartLabel)
                        LabelValue(stringResource(R.string.ob_migration_date), dateText(current.migrationDate))
                        LabelValue(stringResource(R.string.ob_opening_balance), Money.format(current.totalOutstanding), bold = true)
                        LabelValue(stringResource(R.string.ob_paid_against), Money.format(current.paid), Palette.Success)
                        LabelValue(stringResource(R.string.ob_left), Money.format(current.remaining), if (current.remaining > 0) Palette.Danger else Palette.Success, bold = true)
                        if (current.invoiceId != null) TextButton(onClick = { openInvoice(current.invoiceId!!) }) { Text(stringResource(R.string.ob_view_entry)) }
                    }
                }
            } else {
                Text(stringResource(R.string.ob_record_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current == null || current.editable) {
                if (current != null && current.paid > 0) Text(stringResource(R.string.ob_paid_floor, Money.format(current.paid)), style = MaterialTheme.typography.bodyMedium, color = Palette.Warning)
                OpeningFields(model.draft, tried, problems, showDeposit = current != null, depositLocked = current != null && !current.depositEditable)
                if (current != null) FormField(model.reason, { model.reason = it.take(500) }, stringResource(R.string.ob_reason), tried && model.reason.trim().length < 3)
                ActionError(act)
                SubmitButton(stringResource(if (current != null) R.string.ob_save_changes else R.string.ob_save_new), act) {
                    tried = true
                    if (problems.isEmpty() && (current == null || model.reason.trim().length >= 3)) model.save(withDeposit)
                }
            } else {
                Text(stringResource(R.string.ob_locked), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current != null && current.history.isNotEmpty()) {
                Text(stringResource(R.string.ob_history), style = MaterialTheme.typography.titleMedium)
                current.history.forEach { h ->
                    AppCard {
                        Column {
                            Text("${h.actionLabel} · ${dateText(h.at)}", fontWeight = FontWeight.SemiBold)
                            if (h.action == "create") Text(stringResource(R.string.ob_total) + ": " + Money.format(h.totalAfter ?: 0.0), style = MaterialTheme.typography.bodyMedium)
                            h.changes.forEach { c -> Text("${c.label}: ${c.from.ifBlank { "—" }} → ${c.to.ifBlank { "—" }}", style = MaterialTheme.typography.bodyMedium) }
                            h.reason?.takeIf { it.isNotBlank() }?.let { Text(stringResource(R.string.ob_reason) + ": " + it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

/* ───────── tenant details: type, money summary, opening balance ───────── */

@Composable
fun TenantMoneyCard(
    summary: TenantMoneySummary, openings: List<OpeningBalanceInfo>, activeAllocationId: Int?,
    openOpening: (Int) -> Unit, recordOpening: (Int) -> Unit,
) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.ob_money_summary), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                StatusChip(if (summary.isExisting) "partial" else "active", summary.tenantTypeLabel ?: stringResource(if (summary.isExisting) R.string.ob_type_existing else R.string.ob_type_new))
            }
            Spacer(Modifier.height(6.dp))
            summary.migrationDate?.let { LabelValue(stringResource(R.string.ob_migration_date), dateText(it)) }
            LabelValue(stringResource(R.string.ob_opening_balance), Money.format(summary.openingBalance))
            LabelValue(stringResource(R.string.ob_previous_outstanding), Money.format(summary.previousOutstanding), if (summary.previousOutstanding > 0) Palette.Danger else Color.Unspecified)
            LabelValue(stringResource(R.string.ob_current_month), Money.format(summary.currentMonthCharges))
            LabelValue(stringResource(R.string.ob_total_paid), Money.format(summary.totalPaid), Palette.Success)
            LabelValue(stringResource(R.string.ob_remaining), Money.format(summary.remainingOutstanding), if (summary.remainingOutstanding > 0) Palette.Danger else Palette.Success, bold = true)
            summary.deposit?.let { d ->
                LabelValue(stringResource(R.string.ob_deposit_summary), stringResource(R.string.ob_deposit_line, Money.format(d.agreed), Money.format(d.collected), Money.format(d.pending)))
            }
            openings.forEach { o ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(o.label + (o.propertyCode?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold, color = Palette.Warning)
                Text(stringResource(R.string.ob_as_of_line, dateText(o.asOfDate), o.billingStartLabel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                o.lines.forEach { l ->
                    LabelValue((l.categoryLabel ?: l.category) + (l.periodLabel?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + (l.description.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), Money.format(l.amount))
                }
                LabelValue(stringResource(R.string.ob_paid_left, Money.format(o.paid), Money.format(o.remaining)), "")
                OutlinedButton(onClick = { openOpening(o.id) }) {
                    Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(if (o.editable) R.string.ob_view_edit else R.string.ob_view))
                }
            }
            if (activeAllocationId != null && openings.none { it.allocationId == activeAllocationId }) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = { recordOpening(activeAllocationId) }) {
                    Icon(Icons.Filled.Book, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.ob_record))
                }
            }
        }
    }
}
