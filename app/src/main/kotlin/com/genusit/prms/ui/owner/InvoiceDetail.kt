package com.genusit.prms.ui.owner

import com.genusit.prms.domain.model.Meta
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.model.LabelValue as Lv
import com.genusit.prms.domain.model.PaymentResult
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.Validators
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.PaymentRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette

/** Shared invoice body for owner and tenant. */
@Composable
fun InvoiceBody(inv: InvoiceDetail, showTenant: Boolean, openReceipt: (String) -> Unit, footer: @Composable () -> Unit = {}) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            WalletCard(
                label = stringResource(if (inv.isOpen) R.string.balance_due else R.string.invoice_total),
                amount = if (inv.isOpen) inv.balanceAmount else inv.totalAmount,
                footer = (inv.periodLabel ?: "") + " · " + inv.invoiceNumber,
            )
        }
        item {
            AppCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.details), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        StatusChip(inv.status, inv.statusLabel)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (showTenant) LabelValue(stringResource(R.string.tenant), inv.tenantName ?: "—")
                    else LabelValue(stringResource(R.string.owner), inv.ownerName ?: "—")
                    LabelValue(stringResource(R.string.property), listOfNotNull(inv.propertyCode, inv.city).joinToString(", "))
                    LabelValue(stringResource(R.string.invoice_date), dateText(inv.invoiceDate))
                    LabelValue(stringResource(R.string.due_date), dateText(inv.dueDate), if (inv.isOpen && Dates.isPast(inv.dueDate)) Palette.Danger else Color.Unspecified)
                }
            }
        }
        item {
            AppCard {
                Column {
                    Text(stringResource(R.string.breakdown), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    inv.items.forEach { it2 ->
                        val label = when (it2.itemType) {
                            "base_rent" -> stringResource(R.string.base_rent)
                            else -> it2.description
                        }
                        LabelValue(label, Money.format(it2.amount))
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    LabelValue(stringResource(R.string.total), Money.format(inv.totalAmount), bold = true)
                    LabelValue(stringResource(R.string.paid), Money.format(inv.paidAmount), Palette.Success)
                    LabelValue(stringResource(R.string.balance), Money.format(inv.balanceAmount), if (inv.balanceAmount > 0) Palette.Danger else Palette.Success, bold = true)
                    if (inv.otherOutstanding > 0) {
                        Spacer(Modifier.height(6.dp))
                        Text(stringResource(R.string.other_outstanding, Money.format(inv.otherOutstanding)), color = Palette.Warning, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        item { footer() }
        if (inv.payments.isNotEmpty()) item { SectionHeader(stringResource(R.string.payments)) }
        items(inv.payments, key = { it.id }) { p ->
            PaymentRow(
                title = p.paymentMethodLabel ?: (p.paymentMethod ?: ""),
                subtitle = dateText(p.paymentDate) + " · " + p.receiptNumber,
                amount = p.amount,
                onClick = { openReceipt(p.receiptNumber) },
            )
        }
    }
}

@Composable
fun InvoiceDetailScreen(id: Int, onBack: () -> Unit, recordPayment: (Int) -> Unit, openReceipt: (String) -> Unit) {
    val model: InvoiceDetailViewModel = hiltViewModel()
    val vm = model.invoice
    val state by vm.state.collectAsStateWithLifecycle()
    val cancelState by model.cancelAction.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }
    ScreenScaffold(stringResource(R.string.invoice), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { inv ->
            InvoiceBody(inv, showTenant = true, openReceipt = openReceipt) {
                BillShareActions(inv)
                Spacer(Modifier.height(8.dp))
                if (inv.isOpen) Button(onClick = { recordPayment(inv.id) }, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Filled.Payments, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.record_payment))
                }
                // only a bill without any payment can be cancelled (the server checks again)
                if ((inv.status == "pending" || inv.status == "overdue") && inv.paidAmount <= 0.0) {
                    Spacer(Modifier.height(8.dp))
                    CancelInvoiceButton(cancelState, onCancel = model::cancel)
                }
            }
        }
    }
}

/** Payment modes with server-localized labels; falls back to the app's own strings offline. */
@Composable
fun paymentModes(meta: UiState<Meta>): List<Lv> {
    val st = meta
    val fallback = listOf(
        Lv("cash", stringResource(R.string.mode_cash)), Lv("upi", "UPI"),
        Lv("online", stringResource(R.string.mode_online)), Lv("bank_transfer", stringResource(R.string.mode_bank)),
        Lv("cheque", stringResource(R.string.mode_cheque)), Lv("other", stringResource(R.string.mode_other)),
    )
    return (st as? UiState.Data)?.value?.paymentModes?.takeIf { it.isNotEmpty() } ?: fallback
}

/** Common payment form (owner on an invoice; employee collection reuses it). */
@Composable
fun PaymentForm(
    maxAmount: Double,
    running: Boolean,
    errorMessage: String?,
    modes: List<Lv>,
    onSubmit: (amount: Double, mode: String, date: String, reference: String, remarks: String) -> Unit,
) {
    var amount by rememberSaveable { mutableStateOf("") }
    var mode by rememberSaveable { mutableStateOf("cash") }
    var reference by rememberSaveable { mutableStateOf("") }
    var remarks by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var touched by rememberSaveable { mutableStateOf(false) }
    val err = Validators.amount(amount, maxAmount)
    val dateOk = Dates.parse(date)?.let { !it.isAfter(java.time.LocalDate.now()) } ?: false

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }; touched = true },
            label = { Text(stringResource(R.string.amount)) }, prefix = { Text("₹") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = touched && err != null,
            supportingText = {
                Text(
                    when (if (touched) err else null) {
                        Validators.AmountError.EMPTY, Validators.AmountError.INVALID, Validators.AmountError.NOT_POSITIVE -> stringResource(R.string.err_amount)
                        Validators.AmountError.EXCEEDS_BALANCE -> stringResource(R.string.err_amount_max, Money.format(maxAmount))
                        null -> stringResource(R.string.max_collectable, Money.format(maxAmount))
                    }
                )
            },
            trailingIcon = { TextButton(onClick = { amount = String.format(java.util.Locale.US, "%.2f", maxAmount); touched = true }) { Text(stringResource(R.string.full)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.payment_mode), style = MaterialTheme.typography.labelLarge)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            modes.forEach { m -> FilterChip(selected = mode == m.value, onClick = { mode = m.value }, label = { Text(m.label) }) }
        }
        OutlinedTextField(
            value = date, onValueChange = { date = it.take(10) }, singleLine = true,
            label = { Text(stringResource(R.string.payment_date_hint)) }, isError = !dateOk,
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode != "cash") OutlinedTextField(
            value = reference, onValueChange = { reference = it.take(100) }, singleLine = true,
            label = { Text(stringResource(R.string.reference_no)) }, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(value = remarks, onValueChange = { remarks = it.take(500) }, label = { Text(stringResource(R.string.remarks)) }, modifier = Modifier.fillMaxWidth())
        if (errorMessage != null) Text(errorMessage, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = { touched = true; if (err == null && dateOk) onSubmit(Money.parse(amount)!!, mode, date, reference.trim(), remarks.trim()) },
            enabled = !running, modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (running) CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
            else Text(stringResource(R.string.save_payment), fontWeight = FontWeight.Bold)
        }
        Text(stringResource(R.string.payment_rules_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun RecordPaymentScreen(invoiceId: Int, onBack: () -> Unit, onDone: (String) -> Unit) {
    val model: RecordPaymentViewModel = hiltViewModel()
    val vm = model.invoice
    val state by vm.state.collectAsStateWithLifecycle()
    val meta by model.meta.state.collectAsStateWithLifecycle()
    val act by model.payAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it is ScreenEvent.ReceiptReady) onDone(it.receiptNumber) }

    ScreenScaffold(stringResource(R.string.record_payment), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { inv ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletCard(stringResource(R.string.balance_due), inv.balanceAmount, (inv.tenantName ?: "") + " · " + (inv.periodLabel ?: inv.invoiceNumber))
                if (!inv.isOpen) {
                    Text(stringResource(R.string.invoice_already_paid), color = Palette.Success)
                } else {
                    val failed = act as? ActionState.Failed
                    PaymentForm(
                        maxAmount = inv.balanceAmount,
                        running = act is ActionState.Running,
                        errorMessage = failed?.let { errorText(it.error) },
                        modes = paymentModes(meta),
                    ) { amount, mode, date, ref, remarks ->
                        model.pay(inv.id, amount, mode, date, ref, remarks)
                    }
                }
            }
        }
    }
}
