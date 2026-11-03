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
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Call
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
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.TenantMoneySummary
import com.genusit.prms.domain.usecase.GetOpeningBalanceUseCase
import com.genusit.prms.domain.usecase.SaveSimpleOpeningUseCase
import com.genusit.prms.domain.usecase.GetSupportUseCase
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
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

/* ═════════════════════════ 1.12.1 Existing tenant: previous outstanding balance ═════════════════════════ */

/** Text the owner typed as an amount: blank / invalid → null, otherwise ≥ 0 rounded to paise. */
fun previousAmount(text: String): Double? = Money.parse(text.trim())?.takeIf { it >= 0.0 && it <= 10_000_000.0 }

/**
 * The three details of an existing tenant (Add Tenant form): Previous Outstanding Balance (₹, 0 allowed), Tenant Since
 * and the room — the same room choice and monthly charges as a normal allocation.
 */
@Composable
fun ExistingTenantSection(
    rooms: List<Property>, charges: List<Charge>, propertyId: Int, onProperty: (Int) -> Unit,
    tenantSince: String, onTenantSince: (String) -> Unit, chosen: Map<Int, Boolean>, onCharge: (Int, Boolean) -> Unit,
    previous: String, onPrevious: (String) -> Unit, tried: Boolean,
) {
    val sinceDate = Dates.parse(tenantSince)
    val sinceOk = sinceDate != null && !sinceDate.isAfter(java.time.LocalDate.now())
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Book, null, tint = Palette.Warning); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.ex_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.ex_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormField(previous, { onPrevious(amountInput(it)) }, stringResource(R.string.ex_previous), tried && previousAmount(previous) == null,
                KeyboardType.Decimal, supporting = stringResource(R.string.ex_previous_hint))
            FormField(tenantSince, { onTenantSince(it.filter { c -> c.isDigit() || c == '-' }.take(10)) }, stringResource(R.string.ex_since),
                tried && !sinceOk, supporting = stringResource(R.string.ex_since_hint))
            ChoiceField(stringResource(R.string.ex_room), rooms.map { it.id to "${it.propertyCode} · ${it.city} · ${Money.format(it.baseRent, decimals = 0)}" }, propertyId,
                onProperty, isError = tried && propertyId == 0, emptyText = stringResource(R.string.ob_no_vacant_room))
            if (charges.isNotEmpty()) {
                Text(stringResource(R.string.monthly_charges), style = MaterialTheme.typography.titleSmall)
                charges.forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chosen[ch.id] != false, onCheckedChange = { onCharge(ch.id, it) })
                        Text(ch.name + " · " + if (ch.isVariable) stringResource(R.string.per_unit_x, Money.format(ch.ratePerUnit ?: 0.0), ch.unitLabel ?: "unit") else Money.format(ch.fixedAmount ?: 0.0))
                    }
                }
            }
        }
    }
}

/* ───────── record / edit screen ───────── */

@HiltViewModel
class OpeningBalanceViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val getOpening: GetOpeningBalanceUseCase,
    private val saveOpening: SaveSimpleOpeningUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id: Int = handle.intArg()
    val allocationId: Int = handle.intArg("allocationId")
    val opening = loadable(autoLoad = id > 0) { getOpening(id) }
    val saveAction = action<OpeningBalanceInfo>()
    var amount by mutableStateOf("")
    var reason by mutableStateOf("")
    var filled = false

    fun fill(o: OpeningBalanceInfo) {
        if (filled) return
        amount = if (o.totalOutstanding == Math.floor(o.totalOutstanding)) o.totalOutstanding.toLong().toString() else o.totalOutstanding.toString()
        filled = true
    }

    fun save() {
        val a = previousAmount(amount) ?: return
        saveAction.run(onSuccess = { reason = ""; if (id == 0) sendEvent(ScreenEvent.Saved) else opening.refresh() }) { saveOpening(id, allocationId, a, reason) }
    }
}

/** Previous outstanding balance of one tenancy: record it (allocationId) or view / change it (id) with the change history. */
@Composable
fun OpeningBalanceScreen(onBack: () -> Unit, onSaved: () -> Unit = onBack, openInvoice: (Int) -> Unit = {}) {
    val model: OpeningBalanceViewModel = hiltViewModel()
    val state by model.opening.state.collectAsStateWithLifecycle()
    val act by model.saveAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onSaved() }
    var tried by rememberSaveable { mutableStateOf(false) }
    val current = (state as? UiState.Data)?.value
    LaunchedEffect(current) { if (current != null) model.fill(current) }

    ScreenScaffold(stringResource(R.string.ex_screen_title), onBack = onBack) { pad ->
        if (model.id > 0 && current == null) {
            val failed = state as? UiState.Error
            if (failed != null) ErrorBox(failed.error, onRetry = model.opening::refresh, modifier = Modifier.padding(pad)) else LoadingBox(Modifier.padding(pad))
            return@ScreenScaffold
        }
        val amountOk = previousAmount(model.amount) != null
        val reasonOk = current == null || model.reason.trim().length >= 3
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
                        current.joiningDate?.let { LabelValue(stringResource(R.string.ex_since_short), dateText(it)) }
                        LabelValue(stringResource(R.string.ob_migration_date), dateText(current.migrationDate))
                        LabelValue(stringResource(R.string.ob_opening_balance), Money.format(current.totalOutstanding), bold = true)
                        LabelValue(stringResource(R.string.ob_paid_against), Money.format(current.paid), Palette.Success)
                        LabelValue(stringResource(R.string.ob_left), Money.format(current.remaining), if (current.remaining > 0) Palette.Danger else Palette.Success, bold = true)
                        if (current.invoiceId != null) TextButton(onClick = { openInvoice(current.invoiceId!!) }) { Text(stringResource(R.string.ob_view_entry)) }
                    }
                }
            } else {
                Text(stringResource(R.string.ex_record_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (current == null || current.editable) {
                if (current != null && current.paid > 0) Text(stringResource(R.string.ob_paid_floor, Money.format(current.paid)), style = MaterialTheme.typography.bodyMedium, color = Palette.Warning)
                FormField(model.amount, { model.amount = amountInput(it) }, stringResource(R.string.ex_previous), tried && !amountOk, KeyboardType.Decimal,
                    supporting = stringResource(R.string.ex_previous_hint))
                if (current != null) FormField(model.reason, { model.reason = it.take(500) }, stringResource(R.string.ob_reason), tried && !reasonOk)
                ActionError(act)
                SubmitButton(stringResource(if (current != null) R.string.ob_save_changes else R.string.ob_save_new), act) {
                    tried = true
                    if (amountOk && reasonOk) model.save()
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

/* ───────── 1.12.1 Contact Support (support person set by the Super Admin) ───────── */

@HiltViewModel
class ContactSupportViewModel @Inject constructor(getSupport: GetSupportUseCase, expiry: SessionExpiryHandler) : BaseViewModel(expiry) {
    /** Read from the server every time the screen opens, so an admin change shows without an app update. */
    val support = loadable { getSupport() }
}

/** Opens the phone app with the number filled in (the owner presses call) — never calls by itself. */
fun supportDialIntent(telUrl: String): android.content.Intent =
    android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse(telUrl)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

fun dialSupport(ctx: android.content.Context, telUrl: String): Boolean = runCatching { ctx.startActivity(supportDialIntent(telUrl)); true }.getOrDefault(false)

/** Opens WhatsApp (or wa.me in the browser) with the prefilled message; the owner reviews it and presses send. */
fun supportWhatsAppIntent(url: String): android.content.Intent =
    android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)

fun openWhatsAppSupport(ctx: android.content.Context, url: String): Boolean = runCatching { ctx.startActivity(supportWhatsAppIntent(url)); true }.getOrDefault(false)

@Composable
fun ContactSupportScreen(onBack: () -> Unit) {
    val model: ContactSupportViewModel = hiltViewModel()
    val state by model.support.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.sup_title), onBack = onBack) { pad ->
        com.genusit.prms.ui.common.StateContent(state, model.support::refresh, Modifier.padding(pad)) { s ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SupportCard(s)
            }
        }
    }
}

/** Support details with Call / WhatsApp; a short message when support is off or not set up. */
@Composable
fun SupportCard(s: com.genusit.prms.domain.model.SupportInfo) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var failed by rememberSaveable { mutableStateOf(false) }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!s.usable) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.SupportAgent, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp))
                    Text(s.message ?: stringResource(R.string.sup_not_set), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.genusit.prms.ui.common.IconBadge(Icons.Filled.SupportAgent, Palette.Brand)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                    s.hours?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                StatusChip(if (s.available) "active" else "inactive", s.availabilityLabel)
            }
            if (!s.available) Text(stringResource(R.string.sup_unavailable_note), style = MaterialTheme.typography.bodyMedium, color = Palette.Warning)
            s.phone?.let { LabelValue(stringResource(R.string.sup_mobile), it) }
            s.whatsapp?.let { LabelValue(stringResource(R.string.sup_whatsapp), it) }
            s.email?.let { LabelValue(stringResource(R.string.email), it) }
            s.instructions?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            s.telUrl?.let { url ->
                androidx.compose.material3.Button(onClick = { failed = !dialSupport(ctx, url) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Call, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sup_call), fontWeight = FontWeight.Bold)
                }
            }
            s.whatsappUrl?.let { url ->
                androidx.compose.material3.Button(onClick = { failed = !openWhatsAppSupport(ctx, url) }, modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Success)) {
                    Icon(Icons.Filled.Chat, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.sup_whatsapp_btn), fontWeight = FontWeight.Bold)
                }
            }
            Text(stringResource(R.string.sup_confirm_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (failed) Text(stringResource(R.string.sup_open_failed), color = MaterialTheme.colorScheme.error)
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
            // one row per figure: a single long value would squeeze its label out of the row
            summary.deposit?.let { d ->
                LabelValue(stringResource(R.string.ob_deposit_agreed), Money.format(d.agreed))
                LabelValue(stringResource(R.string.ob_deposit_collected), Money.format(d.collected), Palette.Success)
                LabelValue(stringResource(R.string.ob_deposit_pending), Money.format(d.pending), if (d.pending > 0) Palette.Danger else Color.Unspecified)
            }
            openings.forEach { o ->
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(o.label + (o.propertyCode?.let { " · $it" } ?: ""), fontWeight = FontWeight.SemiBold, color = Palette.Warning)
                Text(stringResource(R.string.ob_as_of_line, dateText(o.asOfDate), o.billingStartLabel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                o.lines.forEach { l ->
                    LabelValue((l.categoryLabel ?: l.category) + (l.periodLabel?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") + (l.description.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""), Money.format(l.amount))
                }
                Text(stringResource(R.string.ob_paid_left, Money.format(o.paid), Money.format(o.remaining)), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp))
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
