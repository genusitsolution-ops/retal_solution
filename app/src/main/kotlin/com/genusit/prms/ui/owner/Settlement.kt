package com.genusit.prms.ui.owner

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.AllocationDetail
import com.genusit.prms.domain.model.DepositSummary
import com.genusit.prms.domain.model.DepositTxn
import com.genusit.prms.domain.model.SettlementCharge
import com.genusit.prms.domain.model.SettlementStatement
import com.genusit.prms.domain.model.VacateInput
import com.genusit.prms.domain.model.VacateResult
import com.genusit.prms.domain.usecase.ConfirmVacateUseCase
import com.genusit.prms.domain.usecase.GetAllocationDetailUseCase
import com.genusit.prms.domain.usecase.PreviewVacateUseCase
import com.genusit.prms.domain.usecase.RecordRefundPaidUseCase
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.ReceiptMessage
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ConfirmDialog
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.openInWhatsApp
import com.genusit.prms.ui.common.printHtml
import com.genusit.prms.ui.common.shareText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/* ═════════════════════════ 1.11 security deposit + vacate settlement ═════════════════════════ */

/** Agreed / collected / pending (+ adjusted / refunded / held once used). All values from the server. */
@Composable
fun DepositFigures(d: DepositSummary, showUsage: Boolean = d.adjusted > 0 || d.refunded > 0) {
    LabelValue(stringResource(R.string.deposit_agreed), Money.format(d.agreed))
    LabelValue(stringResource(R.string.deposit_collected), Money.format(d.collected), Palette.Success)
    LabelValue(stringResource(R.string.deposit_pending), Money.format(d.pending), if (d.pending > 0) Palette.Danger else Color.Unspecified, bold = d.pending > 0)
    if (showUsage) {
        LabelValue(stringResource(R.string.deposit_adjusted), Money.format(d.adjusted))
        LabelValue(stringResource(R.string.deposit_refunded), Money.format(d.refunded))
        LabelValue(stringResource(R.string.deposit_held), Money.format(d.held), bold = true)
    }
}

@Composable
fun DepositTxnRow(t: DepositTxn) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(t.typeLabel ?: t.type, fontWeight = FontWeight.SemiBold)
            Text(listOfNotNull(dateText(t.date), t.methodLabel, t.reference?.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text((if (t.isIn) "+ " else "− ") + Money.format(t.amount), fontWeight = FontWeight.Bold, color = if (t.isIn) Palette.Success else Palette.Danger)
            Text(stringResource(R.string.held_after_x, Money.format(t.heldAfter)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Item-wise statement (preview and stored). */
@Composable
fun SettlementStatementCard(st: SettlementStatement, title: String) {
    AppCard(Modifier.testTag("settlement_statement")) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                st.settlementNo?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Spacer(Modifier.height(6.dp))
            LabelValue(stringResource(R.string.tenant), listOfNotNull(st.tenant.name, st.tenant.phone).joinToString(" · "))
            LabelValue(stringResource(R.string.property), listOfNotNull(st.property.code, st.property.city).joinToString(", "))
            LabelValue(stringResource(R.string.allocation_date), dateText(st.allocationDate))
            LabelValue(stringResource(R.string.vacating_date), dateText(st.vacateDate))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            LabelValue(stringResource(R.string.total_rent_billed), Money.format(st.rentBilled))
            LabelValue(stringResource(R.string.total_rent_paid), Money.format(st.rentPaid), Palette.Success)
            st.lines.filter { it.key != "final" }.forEach { l ->
                LabelValue(l.label, Money.format(l.amount),
                    when (l.kind) { "due" -> Palette.Danger; "credit" -> Palette.Success; else -> Color.Unspecified }, bold = l.kind == "total")
            }
            if (st.openBills.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(stringResource(R.string.unpaid_bills_x, st.openBills.joinToString(", ") { it.periodLabel + " " + Money.format(it.balance) }),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (st.billsToCancel.isNotEmpty()) {
                Text(stringResource(R.string.bills_cancelled_on_vacate, st.billsToCancel.joinToString(", ") { it.periodLabel + " " + Money.format(it.total) }),
                    style = MaterialTheme.typography.labelMedium, color = Palette.Warning)
            }
            if (st.otherOutstanding > 0) Text(stringResource(R.string.other_rooms_outstanding, Money.format(st.otherOutstanding)),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            val (bg, fg) = when (st.result) {
                "refund" -> Palette.Success.copy(alpha = .12f) to Palette.Success
                "payable" -> Palette.Danger.copy(alpha = .10f) to Palette.Danger
                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
            }
            Text(st.resultLabel, fontWeight = FontWeight.ExtraBold, color = fg, style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth().background(bg, RoundedCornerShape(10.dp)).padding(12.dp).testTag("settlement_result"))
            when (st.refundStatus) {
                "paid" -> Text(stringResource(R.string.refund_paid) + (st.refundMethodLabel?.let { " · $it" } ?: "") + (st.refundDate?.let { " · " + dateText(it) } ?: ""),
                    color = Palette.Success, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
                "pending" -> Text(stringResource(R.string.refund_pending), color = Palette.Warning, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
            if (st.result == "payable" && st.settlementNo == null) Text(stringResource(R.string.payable_stays_on_bills),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** WhatsApp (tenant's registered number) or the share sheet; Print / PDF through Android printing. */
@Composable
fun StatementShareActions(st: SettlementStatement, text: String?) {
    val ctx = LocalContext.current
    val noNumber = stringResource(R.string.tenant_no_mobile)
    val noWa = stringResource(R.string.whatsapp_missing)
    val chooser = stringResource(R.string.share_whatsapp)
    val title = stringResource(R.string.vacate_settlement)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!text.isNullOrBlank()) Button(
            onClick = {
                val url = ReceiptMessage.whatsAppUrl(st.tenant.phone, text)
                when {
                    url == null -> { Toast.makeText(ctx, noNumber, Toast.LENGTH_LONG).show(); shareText(ctx, text, chooser) }
                    !openInWhatsApp(ctx, url) -> { Toast.makeText(ctx, noWa, Toast.LENGTH_LONG).show(); shareText(ctx, text, chooser) }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF075E54), contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) { Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share_whatsapp)) }
        OutlinedButton(onClick = { printHtml(ctx, st.settlementNo ?: "settlement", statementHtml(st, title)) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Print, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.print_pdf))
        }
    }
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

/** Printable statement (the same rows as on screen; amounts from the server). */
fun statementHtml(st: SettlementStatement, title: String): String {
    val rows = st.lines.filter { it.key != "final" }.joinToString("") {
        "<tr${if (it.kind == "total") " class=t" else ""}><td>${esc(it.label)}</td><td class=r>${esc(Money.format(it.amount))}</td></tr>"
    }
    return """<html><head><meta charset="utf-8"><style>body{font-family:sans-serif;color:#0f172a;padding:24px}h1{font-size:20px;margin:0}
        table{width:100%;border-collapse:collapse;margin-top:14px}td{padding:7px;border-bottom:1px solid #e2e8f0}.r{text-align:right}.t td{font-weight:bold}
        .res{margin-top:14px;padding:12px;border:2px solid #0f172a;font-weight:bold;font-size:17px}.m{color:#64748b;font-size:13px}</style></head><body>
        <h1>${esc(title)} ${esc(st.settlementNo ?: "")}</h1>
        <p class=m>${esc(st.tenant.name ?: "")} · ${esc(st.tenant.phone ?: "")}<br>${esc(listOfNotNull(st.property.code, st.property.city).joinToString(", "))}
        <br>${esc(st.allocationDate ?: "")} → ${esc(st.vacateDate ?: "")}</p>
        <table><tr><td>Rent billed</td><td class=r>${esc(Money.format(st.rentBilled))}</td></tr><tr><td>Rent paid</td><td class=r>${esc(Money.format(st.rentPaid))}</td></tr>$rows</table>
        <div class=res>${esc(st.resultLabel)}</div></body></html>"""
}

/* ───────── allocation details ───────── */

@HiltViewModel
class AllocationDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    getDetail: GetAllocationDetailUseCase,
    private val refundPaid: RecordRefundPaidUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id = handle.intArg()
    val detail = loadable(live = true) { getDetail(id) }
    val refund = action<SettlementStatement>()
    fun recordRefund(method: String, reference: String, date: String) = refund.run(onSuccess = { detail.refresh() }) { refundPaid(id, method, reference, date) }
}

@Composable
fun AllocationDetailScreen(onBack: () -> Unit, vacate: (Int) -> Unit) {
    val model: AllocationDetailViewModel = hiltViewModel()
    val state by model.detail.state.collectAsStateWithLifecycle()
    val refundState by model.refund.state.collectAsStateWithLifecycle()
    var refundOpen by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.allocation_details), onBack = onBack) { pad ->
        StateContent(state, model.detail::refresh, Modifier.padding(pad)) { d: AllocationDetail ->
            val a = d.allocation
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(a.tenantName ?: "—", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                StatusChip(a.status, stringResource(if (a.isActive) R.string.st_active else R.string.st_closed))
                            }
                            Text(listOfNotNull(a.propertyCode, a.city).joinToString(", ") + " · " + dateText(a.allocationDate) +
                                (a.deallocationDate?.let { " → " + dateText(it) } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.security_deposit), style = MaterialTheme.typography.titleSmall)
                            DepositFigures(d.deposit, showUsage = true)
                            if (a.isActive) {
                                Spacer(Modifier.height(8.dp))
                                OutlinedButton(onClick = { vacate(a.id) }, border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Danger.copy(alpha = .5f))) {
                                    Text(stringResource(R.string.vacate), color = Palette.Danger)
                                }
                            }
                        }
                    }
                }
                d.settlement?.let { st ->
                    item { SettlementStatementCard(st, stringResource(R.string.vacate_settlement)) }
                    item { StatementShareActions(st, d.shareText) }
                    if (st.refundStatus == "pending") item {
                        Button(onClick = { model.refund.reset(); refundOpen = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.record_refund_x, Money.format(st.refundAmount)))
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.deposit_history)) }
                if (d.history.isEmpty()) item { EmptyState(stringResource(R.string.no_deposit_history)) }
                items(d.history, key = { it.id }) { t -> AppCard { DepositTxnRow(t) } }
            }
            if (refundOpen) RefundDialog(d.settlement?.refundAmount ?: 0.0, refundState,
                onConfirm = { m, r, dt -> model.recordRefund(m, r, dt) }, onDismiss = { refundOpen = false })
            LaunchedEffect(refundState) { if (refundState is ActionState.Done) refundOpen = false }
        }
    }
}

private val REFUND_MODES = listOf("cash", "upi", "bank_transfer", "cheque", "other")

@Composable
fun modeLabel(m: String): String = when (m) {
    "cash" -> stringResource(R.string.mode_cash); "upi" -> "UPI"; "bank_transfer" -> stringResource(R.string.mode_bank)
    "cheque" -> stringResource(R.string.mode_cheque); else -> stringResource(R.string.mode_other)
}

@Composable
private fun RefundDialog(amount: Double, state: ActionState<*>, onConfirm: (String, String, String) -> Unit, onDismiss: () -> Unit) {
    var mode by rememberSaveable { mutableStateOf("cash") }
    var ref by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.record_refund_x, Money.format(amount))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REFUND_MODES.take(3).forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(modeLabel(m)) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    REFUND_MODES.drop(3).forEach { m -> FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(modeLabel(m)) }) }
                }
                OutlinedTextField(ref, { ref = it.take(100) }, label = { Text(stringResource(R.string.reference_no)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                ActionError(state)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(mode, ref, Dates.today()) }, enabled = state !is ActionState.Running) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/* ───────── vacate: inputs → preview → confirm ───────── */

@HiltViewModel
class VacateViewModel @Inject constructor(
    handle: SavedStateHandle,
    getDetail: GetAllocationDetailUseCase,
    private val preview: PreviewVacateUseCase,
    private val confirm: ConfirmVacateUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id = handle.intArg()
    val detail = loadable { getDetail(id) }
    val previewAction = action<SettlementStatement>()
    val confirmAction = action<VacateResult>()
    /** Inputs the shown preview was made with; Confirm is offered only for exactly these (Compose state). */
    var previewedWith by mutableStateOf<VacateInput?>(null)
        private set

    fun runPreview(input: VacateInput) {
        confirmAction.reset()
        previewAction.run { preview(id, input).also { previewedWith = input } }
    }

    fun runConfirm(input: VacateInput, expectedFinal: Double) = confirmAction.run(onSuccess = { sendEvent(com.genusit.prms.ui.common.ScreenEvent.Saved) }) {
        confirm(id, input, expectedFinal)
    }
}

/** One editable additional-charge row. */
private class ChargeRow(label: String = "", amount: String = "", reason: String = "") {
    var label by mutableStateOf(label); var amount by mutableStateOf(amount); var reason by mutableStateOf(reason)
    val isBlank get() = label.isBlank() && amount.isBlank() && reason.isBlank()
}

@Composable
fun VacateScreen(onBack: () -> Unit, onDone: (Int) -> Unit) {
    val model: VacateViewModel = hiltViewModel()
    val detail by model.detail.state.collectAsStateWithLifecycle()
    val pState by model.previewAction.state.collectAsStateWithLifecycle()
    val cState by model.confirmAction.state.collectAsStateWithLifecycle()
    com.genusit.prms.ui.common.EventEffect(model) { if (it == com.genusit.prms.ui.common.ScreenEvent.Saved) onDone(model.id) }

    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var applyDeposit by rememberSaveable { mutableStateOf(true) }
    var notes by rememberSaveable { mutableStateOf("") }
    var refundNow by rememberSaveable { mutableStateOf(false) }
    var refundMode by rememberSaveable { mutableStateOf("cash") }
    var refundRef by rememberSaveable { mutableStateOf("") }
    val charges = remember { mutableStateListOf(ChargeRow()) }
    var tried by rememberSaveable { mutableStateOf(false) }
    var askConfirm by rememberSaveable { mutableStateOf(false) }

    val chargeProblems = charges.filterNot { it.isBlank }.any { c -> c.label.isBlank() || c.reason.isBlank() || (c.amount.toDoubleOrNull() ?: 0.0) <= 0.0 }
    val dateOk = Dates.parse(date)?.let { !it.isAfter(java.time.LocalDate.now()) } ?: false
    fun input() = VacateInput(
        vacateDate = date, applyDeposit = applyDeposit, notes = notes,
        charges = charges.filterNot { it.isBlank }.map { SettlementCharge(it.label.trim(), it.amount.toDoubleOrNull() ?: 0.0, it.reason.trim()) },
        refundNow = refundNow, refundMethod = refundMode, refundReference = refundRef,
    )
    val shown = (pState as? ActionState.Done<SettlementStatement>)?.result
    // the preview is valid only for the inputs it was made with (refund fields do not change the figures)
    val stale = shown != null && model.previewedWith?.copy(refundNow = false, refundMethod = "cash", refundReference = "") != input().copy(refundNow = false, refundMethod = "cash", refundReference = "")

    ScreenScaffold(stringResource(R.string.vacate_room), onBack = onBack) { pad ->
        StateContent(detail, model.detail::refresh, Modifier.padding(pad)) { d ->
            val a = d.allocation
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard {
                    Column {
                        Text(a.tenantName ?: "—", style = MaterialTheme.typography.titleMedium)
                        Text(listOfNotNull(a.propertyCode, a.city, a.tenantPhone).joinToString(" · ") + " · " + stringResource(R.string.allocated_on_x, dateText(a.allocationDate)),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        DepositFigures(d.deposit, showUsage = false)
                    }
                }
                if (!a.isActive) {
                    Text(stringResource(R.string.already_vacated), color = Palette.Warning)
                    return@Column
                }
                Text(stringResource(R.string.settlement_details), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(date, { date = it.take(10) }, label = { Text(stringResource(R.string.vacating_date_hint)) }, singleLine = true,
                    isError = !dateOk, modifier = Modifier.fillMaxWidth().testTag("vacate_date"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.adjust_deposit), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.adjust_deposit_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = applyDeposit, onCheckedChange = { applyDeposit = it })
                }
                Text(stringResource(R.string.additional_charges), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.additional_charges_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                charges.forEachIndexed { i, c ->
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(c.label, { c.label = it.take(100) }, label = { Text(stringResource(R.string.charge_name)) }, singleLine = true,
                                    isError = tried && !c.isBlank && c.label.isBlank(), modifier = Modifier.weight(1f))
                                IconButton(onClick = { if (charges.size > 1) charges.removeAt(i) else { c.label = ""; c.amount = ""; c.reason = "" } }) {
                                    Icon(Icons.Filled.Close, stringResource(R.string.remove))
                                }
                            }
                            OutlinedTextField(c.amount, { c.amount = amountInput(it) }, label = { Text(stringResource(R.string.amount)) }, prefix = { Text("₹") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), isError = tried && !c.isBlank && (c.amount.toDoubleOrNull() ?: 0.0) <= 0.0,
                                modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(c.reason, { c.reason = it.take(300) }, label = { Text(stringResource(R.string.reason)) },
                                isError = tried && !c.isBlank && c.reason.isBlank(), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                if (charges.size < 20) TextButton(onClick = { charges.add(ChargeRow()) }) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.add_charge)) }
                OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text(stringResource(R.string.notes)) }, minLines = 2, modifier = Modifier.fillMaxWidth())
                if (tried && chargeProblems) Text(stringResource(R.string.charge_needs_reason), color = MaterialTheme.colorScheme.error)
                ActionError(pState)
                SubmitButton(stringResource(if (shown == null) R.string.preview_settlement else R.string.update_preview), pState) {
                    tried = true
                    if (!chargeProblems && dateOk) model.runPreview(input())
                }

                if (shown != null) {
                    SettlementStatementCard(shown, stringResource(R.string.settlement_statement))
                    if (stale) Text(stringResource(R.string.preview_outdated), color = Palette.Warning)
                    else {
                        if (shown.refundAmount > 0) AppCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(stringResource(R.string.refund_paid_now_x, Money.format(shown.refundAmount)), modifier = Modifier.weight(1f))
                                    Switch(checked = refundNow, onCheckedChange = { refundNow = it })
                                }
                                if (refundNow) {
                                    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        REFUND_MODES.take(3).forEach { m -> FilterChip(selected = refundMode == m, onClick = { refundMode = m }, label = { Text(modeLabel(m)) }) }
                                    }
                                    OutlinedTextField(refundRef, { refundRef = it.take(100) }, label = { Text(stringResource(R.string.reference_no)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                } else Text(stringResource(R.string.refund_later_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(stringResource(R.string.vacate_effects), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        ActionError(cState)
                        if ((cState as? ActionState.Failed)?.error?.code == "SETTLEMENT_CHANGED") {
                            TextButton(onClick = { model.runPreview(input()) }) { Text(stringResource(R.string.update_preview)) }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f).height(52.dp)) { Text(stringResource(R.string.cancel)) }
                            Button(onClick = { askConfirm = true }, enabled = cState !is ActionState.Running,
                                colors = ButtonDefaults.buttonColors(containerColor = Palette.Danger), modifier = Modifier.weight(1f).height(52.dp).testTag("confirm_vacate")) {
                                Text(stringResource(R.string.confirm_vacate), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
    if (askConfirm && shown != null) ConfirmDialog(
        title = stringResource(R.string.confirm_vacate),
        text = shown.resultLabel + "\n\n" + stringResource(R.string.vacate_cannot_undo),
        confirmLabel = stringResource(R.string.confirm_vacate),
        onConfirm = { askConfirm = false; model.runConfirm(input(), shown.finalAmount) },
        onDismiss = { askConfirm = false },
    )
}

/** Small helper for the tenant details screen: deposit card with a link to the allocation's details. */
@Composable
fun TenantDepositCard(deposits: List<com.genusit.prms.domain.model.AllocationDeposit>, open: (Int) -> Unit) {
    if (deposits.isEmpty()) return
    AppCard {
        Column {
            Text(stringResource(R.string.security_deposit), style = MaterialTheme.typography.titleMedium)
            deposits.forEach { d ->
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open(d.allocationId) }) {
                    Text(listOfNotNull(d.propertyCode, d.city).joinToString(", "), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    StatusChip(d.status, stringResource(if (d.isActive) R.string.st_active else R.string.st_closed))
                }
                DepositFigures(DepositSummary(d.allocationId, d.agreed, d.collected, d.pending, d.adjusted, d.refunded, d.held))
                d.settlement?.resultLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { open(d.allocationId) }) { Text(stringResource(R.string.deposit_history)) }
            }
        }
    }
}
