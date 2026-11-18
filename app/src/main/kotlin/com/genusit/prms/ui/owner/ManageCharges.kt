package com.genusit.prms.ui.owner

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.genusit.prms.domain.model.RoomCharge
import com.genusit.prms.domain.model.RoomChargeChange
import com.genusit.prms.domain.model.RoomChargeInput
import com.genusit.prms.domain.model.RoomChargesData
import com.genusit.prms.domain.usecase.GetRoomChargesUseCase
import com.genusit.prms.domain.usecase.RoomChargeForm
import com.genusit.prms.domain.usecase.RoomChargeFormState
import com.genusit.prms.domain.usecase.RoomChargeProblem
import com.genusit.prms.domain.usecase.SaveRoomChargeUseCase
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ChoiceField
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject

/*
 * 1.16 — Manage Charges of an allocated room: see the room's charges, add one the owner forgot at allocation
 * (from Charge Master or a new one), change its amount / how often / start date / remarks, stop it or start it again.
 * No re-allocation. The server decides what goes on each bill; bills already generated never change.
 */

@HiltViewModel
class ManageChargesViewModel @Inject constructor(
    handle: SavedStateHandle,
    getRoomCharges: GetRoomChargesUseCase,
    private val save: SaveRoomChargeUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val allocationId = handle.intArg()
    val data = loadable(live = true) { getRoomCharges(allocationId) }
    /** Add / edit form. */
    val formAction = action<Unit>()
    /** Stop / start again on a charge card. */
    val rowAction = action<Unit>()

    private fun saved(a: com.genusit.prms.ui.common.ActionRunner<Unit>) { a.reset(); data.refresh(); sendEvent(ScreenEvent.Saved) }

    fun add(i: RoomChargeInput) = formAction.run(onSuccess = { saved(formAction) }) { save.add(allocationId, i); Unit }
    fun update(id: Int, c: RoomChargeChange) = formAction.run(onSuccess = { saved(formAction) }) { save.update(id, c) }
    fun stop(id: Int, endDate: String) = rowAction.run(onSuccess = { saved(rowAction) }) { save.stop(id, endDate) }
    fun restart(id: Int, startDate: String) = rowAction.run(onSuccess = { saved(rowAction) }) { save.restart(id, startDate) }
}

@Composable
fun ManageChargesScreen(onBack: () -> Unit, generateBill: (Int) -> Unit = {}) {
    val model: ManageChargesViewModel = hiltViewModel()
    val state by model.data.state.collectAsStateWithLifecycle()
    val formState by model.formAction.state.collectAsStateWithLifecycle()
    val rowState by model.rowAction.state.collectAsStateWithLifecycle()
    /** null = list; 0 = add a charge; > 0 = edit that room charge. */
    var formFor by rememberSaveable { mutableStateOf<Int?>(null) }
    /** Room charge whose Stop / Start again panel is open. */
    var stopFor by rememberSaveable { mutableStateOf(0) }
    var againFor by rememberSaveable { mutableStateOf(0) }
    val ctx = LocalContext.current
    val savedMsg = stringResource(R.string.rc_saved)
    EventEffect(model) {
        if (it == ScreenEvent.Saved) {
            formFor = null; stopFor = 0; againFor = 0
            Toast.makeText(ctx, savedMsg, Toast.LENGTH_SHORT).show()
        }
    }
    ScreenScaffold(stringResource(R.string.manage_charges), onBack = { if (formFor != null) { formFor = null; model.formAction.reset() } else onBack() }) { pad ->
        StateContent(state, model.data::refresh, Modifier.padding(pad)) { d: RoomChargesData ->
            val f = formFor
            if (f != null) {
                ChargeForm(d, d.charges.firstOrNull { it.id == f }, formState,
                    onCancel = { formFor = null; model.formAction.reset() },
                    onAdd = model::add, onUpdate = model::update)
            } else {
                ChargeList(d, rowState, stopFor, againFor,
                    onAdd = { model.formAction.reset(); formFor = 0 },
                    onEdit = { model.formAction.reset(); formFor = it },
                    onStopOpen = { model.rowAction.reset(); againFor = 0; stopFor = it },
                    onAgainOpen = { model.rowAction.reset(); stopFor = 0; againFor = it },
                    onClosePanel = { stopFor = 0; againFor = 0; model.rowAction.reset() },
                    onStop = model::stop, onRestart = model::restart,
                    generateBill = { generateBill(d.allocation.id) })
            }
        }
    }
}

/* ───────── list ───────── */

@Composable
private fun ChargeList(
    d: RoomChargesData, rowState: ActionState<*>, stopFor: Int, againFor: Int,
    onAdd: () -> Unit, onEdit: (Int) -> Unit, onStopOpen: (Int) -> Unit, onAgainOpen: (Int) -> Unit, onClosePanel: () -> Unit,
    onStop: (Int, String) -> Unit, onRestart: (Int, String) -> Unit, generateBill: () -> Unit,
) {
    val a = d.allocation
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            AppCard {
                Column {
                    Text(a.tenantName ?: "—", style = MaterialTheme.typography.titleMedium)
                    Text(listOfNotNull(a.propertyCode, a.city).joinToString(", ") + " · " + stringResource(R.string.rc_allocated_on, dateText(a.allocationDate)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.rc_info), style = MaterialTheme.typography.labelMedium, color = Palette.Teal)
                }
            }
        }
        if (!d.ready) item { Text(stringResource(R.string.rc_not_ready), color = Palette.Warning, fontWeight = FontWeight.SemiBold) }
        if (a.isActive && d.ready) item {
            Button(onClick = onAdd, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("rc_add"), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Filled.Add, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.rc_add), fontWeight = FontWeight.Bold)
            }
        }
        if (!a.isActive) item { Text(stringResource(R.string.rc_vacated), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.base_rent), fontWeight = FontWeight.SemiBold)
                        Text(stringResource(R.string.rc_every_month), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Money.format(a.baseRent), fontWeight = FontWeight.Bold, color = Palette.Brand)
                }
            }
        }
        if (d.charges.isEmpty()) item { Text(stringResource(R.string.rc_none), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(d.charges, key = { it.id }) { c ->
            ChargeCard(c, a.isActive && d.ready, a.allocationDate, rowState, stopFor == c.id, againFor == c.id,
                onEdit = { onEdit(c.id) }, onStopOpen = { onStopOpen(c.id) }, onAgainOpen = { onAgainOpen(c.id) }, onClosePanel = onClosePanel,
                onStop = { onStop(c.id, it) }, onRestart = { onRestart(c.id, it) })
        }
        if (a.isActive) item {
            OutlinedButton(onClick = generateBill, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.generate_bill))
            }
        }
    }
}

/** Colour of the state chip (active = green, starts later = blue, ending = amber, stopped / billed = grey). */
private fun chipKey(state: String) = when (state) {
    "active" -> "active"; "scheduled" -> "in_progress"; "stopping" -> "pending"; else -> "other"
}

@Composable
fun roomChargeAmount(c: RoomCharge): String =
    if (c.isMeter) stringResource(R.string.per_unit_x, Money.format(c.ratePerUnit ?: 0.0), c.unitLabel ?: "unit") else Money.format(c.amount ?: 0.0)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChargeCard(
    c: RoomCharge, canChange: Boolean, allocationDate: String?, rowState: ActionState<*>, stopOpen: Boolean, againOpen: Boolean,
    onEdit: () -> Unit, onStopOpen: () -> Unit, onAgainOpen: () -> Unit, onClosePanel: () -> Unit,
    onStop: (String) -> Unit, onRestart: (String) -> Unit,
) {
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(roomChargeAmount(c), fontWeight = FontWeight.Bold, color = if (c.isOn) Palette.Brand else Palette.Muted)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusChip(chipKey(c.state), c.stateLabel)
                Spacer(Modifier.width(8.dp))
                Text(listOfNotNull(
                    stringResource(if (c.isMeter) R.string.rc_type_meter else R.string.rc_type_fixed),
                    stringResource(if (c.isOneTime) R.string.rc_one_time else R.string.rc_every_month),
                    c.startDate?.let { stringResource(R.string.rc_from_date, dateText(it)) },
                ).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!c.remarks.isNullOrBlank()) Text(c.remarks!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!c.masterActive) Text(stringResource(R.string.rc_master_off), style = MaterialTheme.typography.labelMedium, color = Palette.Danger)
            if (canChange && !stopOpen && !againOpen) {
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.editable) {
                        OutlinedButton(onClick = onEdit, modifier = Modifier.testTag("rc_edit_${c.id}")) {
                            Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.edit))
                        }
                        OutlinedButton(onClick = onStopOpen, border = androidx.compose.foundation.BorderStroke(1.dp, Palette.Danger.copy(alpha = .5f)), modifier = Modifier.testTag("rc_stop_${c.id}")) {
                            Icon(Icons.Filled.StopCircle, null, Modifier.size(18.dp), tint = Palette.Danger); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.rc_stop), color = Palette.Danger)
                        }
                    } else if (c.canRestart) {
                        OutlinedButton(onClick = onAgainOpen, modifier = Modifier.testTag("rc_again_${c.id}")) {
                            Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp), tint = Palette.Success); Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.rc_start_again), color = Palette.Success)
                        }
                    }
                }
            }
            if (stopOpen) {
                var date by rememberSaveable { mutableStateOf(maxOf(Dates.today(), allocationDate ?: "")) }
                val ok = Dates.parse(date) != null
                DateField(date, { date = it }, stringResource(R.string.rc_last_date), allocationDate, isError = !ok, tag = "rc_end_date")
                Text(stringResource(R.string.rc_stop_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionError(rowState)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClosePanel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { if (ok) onStop(date) }, enabled = rowState !is ActionState.Running, modifier = Modifier.weight(1f).testTag("rc_stop_confirm"),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Danger)) { Text(stringResource(R.string.rc_stop_charge)) }
                }
            }
            if (againOpen) {
                var date by rememberSaveable { mutableStateOf(maxOf(Dates.today(), allocationDate ?: "")) }
                val ok = c.isStopping || Dates.parse(date) != null
                if (c.isStopping) Text(stringResource(R.string.rc_again_no_gap), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                else DateField(date, { date = it }, stringResource(R.string.rc_effective_from), allocationDate, isError = !ok, tag = "rc_again_date")
                ActionError(rowState)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClosePanel, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.cancel)) }
                    Button(onClick = { if (ok) onRestart(date) }, enabled = rowState !is ActionState.Running, modifier = Modifier.weight(1f).testTag("rc_again_confirm"),
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Palette.Success)) { Text(stringResource(R.string.rc_start_again)) }
                }
            }
        }
    }
}

/* ───────── add / edit form (on the same screen, no pop-up) ───────── */

@Composable
private fun ChargeForm(
    d: RoomChargesData, editing: RoomCharge?, act: ActionState<*>,
    onCancel: () -> Unit, onAdd: (RoomChargeInput) -> Unit, onUpdate: (Int, RoomChargeChange) -> Unit,
) {
    val a = d.allocation
    val defDate = maxOf(Dates.today(), a.allocationDate ?: "")
    var chargeId by rememberSaveable { mutableStateOf(0) }
    var newName by rememberSaveable { mutableStateOf("") }
    var newMeter by rememberSaveable { mutableStateOf(false) }
    var newRate by rememberSaveable { mutableStateOf("") }
    var newUnit by rememberSaveable { mutableStateOf("unit") }
    var amount by rememberSaveable { mutableStateOf(editing?.amount?.let { Money.format(it, withSymbol = false).replace(",", "") } ?: "") }
    var oneTime by rememberSaveable { mutableStateOf(editing?.isOneTime ?: false) }
    var start by rememberSaveable { mutableStateOf(editing?.startDate ?: if (editing != null) (a.allocationDate ?: defDate) else defDate) }
    var remarks by rememberSaveable { mutableStateOf(editing?.remarks ?: "") }
    var tried by rememberSaveable { mutableStateOf(false) }

    val options = d.addable
    val form = RoomChargeFormState(chargeId, newName, newMeter, newRate, newUnit, amount, oneTime, start, remarks)
    val meter = RoomChargeForm.isMeter(form, options, editing?.isMeter)
    val problems = RoomChargeForm.problems(form, options, a.allocationDate, editing?.isMeter)
    val chosen = options.firstOrNull { it.id == chargeId }
    fun bad(p: RoomChargeProblem) = tried && p in problems

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (editing != null) stringResource(R.string.rc_edit_title, editing.name) else stringResource(R.string.rc_add_title),
            style = MaterialTheme.typography.titleMedium)
        Text(listOfNotNull(a.tenantName, listOfNotNull(a.propertyCode, a.city).joinToString(", ")).joinToString(" · "),
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (editing == null) {
            val newLabel = stringResource(R.string.rc_new_charge)
            ChoiceField(stringResource(R.string.rc_charge),
                options.map { o -> o.id to (o.name + " · " + if (o.isMeter) stringResource(R.string.per_unit_x, Money.format(o.ratePerUnit ?: 0.0), o.unitLabel ?: "unit") else Money.format(o.fixedAmount ?: 0.0)) } +
                    (RoomChargeFormState.NEW to newLabel),
                chargeId.takeIf { it != 0 }, { id ->
                    chargeId = id
                    options.firstOrNull { it.id == id }?.let { o -> if (!o.isMeter) amount = Money.format(o.fixedAmount ?: 0.0, withSymbol = false).replace(",", "") else oneTime = false }
                }, isError = bad(RoomChargeProblem.CHOOSE_CHARGE))
            if (chargeId == RoomChargeFormState.NEW) {
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FormField(newName, { newName = it.take(150) }, stringResource(R.string.rc_charge_name), isError = bad(RoomChargeProblem.NAME),
                            supporting = stringResource(R.string.rc_new_note))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = !newMeter, onClick = { newMeter = false }, label = { Text(stringResource(R.string.rc_type_fixed)) })
                            FilterChip(selected = newMeter, onClick = { newMeter = true; oneTime = false }, label = { Text(stringResource(R.string.rc_type_meter)) })
                        }
                        if (newMeter) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column(Modifier.weight(1f)) { FormField(newRate, { newRate = amountInput(it) }, stringResource(R.string.rate_per_unit) + " (₹)", bad(RoomChargeProblem.RATE), KeyboardType.Decimal) }
                                Column(Modifier.weight(1f)) { FormField(newUnit, { newUnit = it.take(50) }, stringResource(R.string.rc_unit)) }
                            }
                        }
                    }
                }
            }
        }
        if (!meter) {
            val master = editing?.masterAmount ?: chosen?.fixedAmount
            FormField(amount, { amount = amountInput(it) }, stringResource(R.string.amount) + " (₹)", bad(RoomChargeProblem.AMOUNT), KeyboardType.Decimal,
                supporting = master?.let { stringResource(R.string.rc_master_amount, Money.format(it)) })
            Text(stringResource(R.string.rc_how_often), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !oneTime, onClick = { oneTime = false }, label = { Text(stringResource(R.string.rc_every_month)) }, modifier = Modifier.testTag("rc_monthly"))
                FilterChip(selected = oneTime, onClick = { oneTime = true }, label = { Text(stringResource(R.string.rc_one_time)) }, modifier = Modifier.testTag("rc_one_time"))
            }
            Text(stringResource(if (oneTime) R.string.rc_one_time_hint else R.string.rc_monthly_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (editing != null || chosen != null) {
            val rate = editing?.ratePerUnit ?: chosen?.ratePerUnit ?: 0.0
            val unit = editing?.unitLabel ?: chosen?.unitLabel ?: "unit"
            AppCard {
                Column {
                    Text(stringResource(R.string.rate_per_unit) + ": " + stringResource(R.string.per_unit_x, Money.format(rate), unit), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.rc_meter_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        DateField(start, { start = it }, stringResource(R.string.rc_effective_from), a.allocationDate,
            isError = bad(RoomChargeProblem.DATE) || bad(RoomChargeProblem.DATE_BEFORE_ALLOCATION), tag = "rc_start_date",
            supporting = stringResource(R.string.rc_start_hint))
        if (bad(RoomChargeProblem.DATE_BEFORE_ALLOCATION)) Text(stringResource(R.string.rc_date_before_alloc, dateText(a.allocationDate)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        FormField(remarks, { remarks = it.take(255) }, stringResource(R.string.rc_remarks_optional))
        if (tried && problems.isNotEmpty()) Text(stringResource(R.string.rc_fix_fields), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        ActionError(act)
        SubmitButton(stringResource(if (editing != null) R.string.save else R.string.rc_add_button), act) {
            tried = true
            if (problems.isEmpty()) {
                if (editing != null) onUpdate(editing.id, RoomChargeForm.change(form, editing.isMeter))
                else onAdd(RoomChargeForm.input(form, options))
            }
        }
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel)) }
    }
}

/* ───────── date: type it (YYYY-MM-DD) or pick it on the calendar ───────── */

private const val DAY_MS = 86_400_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(value: String, onChange: (String) -> Unit, label: String, minDate: String?, isError: Boolean = false, tag: String = "date", supporting: String? = null) {
    var open by remember { mutableStateOf(false) }
    val parsed = Dates.parse(value)
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { ch -> ch.isDigit() || ch == '-' }.take(10)) },
        label = { Text(label) }, singleLine = true, isError = isError,
        supportingText = { Text(listOfNotNull(parsed?.let { dateText(value) }, supporting).joinToString(" · ")) },
        trailingIcon = { IconButton(onClick = { open = true }) { Icon(Icons.Filled.DateRange, contentDescription = stringResource(R.string.rc_pick_date)) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth().testTag(tag),
    )
    if (open) {
        val min = Dates.parse(minDate)?.toEpochDay()?.times(DAY_MS)
        val st = rememberDatePickerState(
            initialSelectedDateMillis = (parsed ?: LocalDate.now()).toEpochDay() * DAY_MS,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = min == null || utcTimeMillis >= min
            },
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { st.selectedDateMillis?.let { onChange(LocalDate.ofEpochDay(it / DAY_MS).toString()) }; open = false }) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(st) }
    }
}
