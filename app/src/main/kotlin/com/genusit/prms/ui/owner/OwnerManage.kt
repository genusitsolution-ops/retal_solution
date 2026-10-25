package com.genusit.prms.ui.owner

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.core.model.AllocationClosed
import com.genusit.prms.core.model.CreatedId
import com.genusit.prms.core.model.GeneratedInvoice
import com.genusit.prms.core.model.MeterReading
import com.genusit.prms.core.model.NewAllocation
import com.genusit.prms.core.model.NewCharge
import com.genusit.prms.core.model.NewProperty
import com.genusit.prms.core.model.TenantInput
import com.genusit.prms.core.util.Dates
import com.genusit.prms.core.util.Money
import com.genusit.prms.core.util.Validators
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AmountDialog
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.ChoiceField
import com.genusit.prms.ui.common.ConfirmDialog
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.LoadingBox
import com.genusit.prms.ui.common.LocalAppContainer
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.SuccessText
import com.genusit.prms.ui.common.TextDialog
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.rememberAction
import com.genusit.prms.ui.common.rememberLoader
import com.genusit.prms.ui.theme.Palette

/* ═════════════════════════ tenants: add / edit / delete ═════════════════════════ */

/** Add ([id] = 0) or edit a tenant. Same validation as the web form; the server validates again. */
@Composable
fun TenantFormScreen(id: Int, onBack: () -> Unit, onSaved: () -> Unit) {
    val api = LocalAppContainer.current.api
    val action = rememberAction<Unit>("tenant-form-$id")
    val act by action.state.collectAsStateWithLifecycle()
    LaunchedEffect(act) { if (act is ActionState.Done<*>) onSaved() }

    var loaded by rememberSaveable { mutableStateOf(id == 0) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var aadhaar by rememberSaveable { mutableStateOf("") }
    var pan by rememberSaveable { mutableStateOf("") }
    var hasAccount by rememberSaveable { mutableStateOf(false) }
    var hadAccount by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }

    if (!loaded) {
        LaunchedEffect(id) {
            try {
                val t = api.ownerTenantEditable(id)
                name = t.fullName; phone = t.phone.orEmpty(); email = t.email.orEmpty(); address = t.address.orEmpty()
                aadhaar = t.aadharNumber.orEmpty(); pan = t.panNumber.orEmpty()
                hasAccount = t.hasAccount; hadAccount = t.hasAccount; username = t.username.orEmpty()
                loaded = true
            } catch (e: Exception) { loadError = e.message }
        }
    }

    val aadhaarOk = aadhaar.isEmpty() || aadhaar.matches(Regex("\\d{12}"))
    val panOk = pan.isEmpty() || pan.uppercase().matches(Regex("[A-Z]{5}[0-9]{4}[A-Z]"))
    val emailOk = email.isEmpty() || android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val needPassword = hasAccount && !hadAccount
    val accountOk = !hasAccount || (username.isNotBlank() && (!needPassword || password.length >= 6) && (password.isEmpty() || password.length >= 6))
    val valid = name.isNotBlank() && Validators.phone(phone) && emailOk && aadhaarOk && panOk && accountOk

    ScreenScaffold(stringResource(if (id == 0) R.string.add_tenant else R.string.edit_tenant), onBack = onBack) { pad ->
        if (!loaded) {
            if (loadError != null) Text(loadError ?: "", Modifier.padding(pad).padding(16.dp), color = MaterialTheme.colorScheme.error)
            else LoadingBox(Modifier.padding(pad))
            return@ScreenScaffold
        }
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            FormField(name, { name = it.take(150) }, stringResource(R.string.full_name), tried && name.isBlank())
            FormField(phone, { phone = it.filter { c -> c.isDigit() || c == '+' }.take(15) }, stringResource(R.string.phone), tried && !Validators.phone(phone), KeyboardType.Phone)
            FormField(email, { email = it.trim().take(150) }, stringResource(R.string.email_optional), tried && !emailOk, KeyboardType.Email)
            FormField(address, { address = it.take(500) }, stringResource(R.string.address), singleLine = false, minLines = 2)
            FormField(aadhaar, { aadhaar = it.filter(Char::isDigit).take(12) }, stringResource(R.string.aadhaar_number), tried && !aadhaarOk, KeyboardType.Number,
                supporting = stringResource(R.string.aadhaar_hint))
            FormField(pan, { pan = it.uppercase().filter(Char::isLetterOrDigit).take(10) }, stringResource(R.string.pan_number), tried && !panOk)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.tenant_login), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.tenant_login_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hasAccount, onCheckedChange = { hasAccount = it }, enabled = !hadAccount)
            }
            if (hasAccount) {
                FormField(username, { username = it.trim().take(50) }, stringResource(R.string.username), tried && username.isBlank(), enabled = !hadAccount)
                FormField(password, { password = it.take(100) }, stringResource(if (hadAccount) R.string.new_password_optional else R.string.password),
                    tried && ((needPassword && password.length < 6) || (password.isNotEmpty() && password.length < 6)), password = true)
            }
            ActionError(act)
            SubmitButton(stringResource(R.string.save), act) {
                tried = true
                if (valid) {
                    val input = TenantInput(
                        fullName = name.trim(), phone = phone, email = email.ifBlank { null }, address = address.trim(),
                        aadharNumber = aadhaar.ifBlank { null }, panNumber = pan.ifBlank { null },
                        hasAccount = hasAccount, username = if (hasAccount) username else null,
                        password = password.ifEmpty { null },
                    )
                    action.run { if (id == 0) { api.addTenant(input); Unit } else api.updateTenant(id, input) }
                }
            }
        }
    }
}

/** Edit / delete buttons shown on the tenant detail screen. Delete is refused by the server when history exists. */
@Composable
fun TenantManageActions(id: Int, invoiceCount: Int, onEdit: () -> Unit, onDeleted: () -> Unit) {
    val api = LocalAppContainer.current.api
    val action = rememberAction<Unit>("tenant-delete-$id")
    val act by action.state.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(act) { if (act is ActionState.Done<*>) onDeleted() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onEdit) { Icon(Icons.Filled.Edit, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.edit)) }
            if (invoiceCount == 0) OutlinedButton(onClick = { confirm = true }) {
                Icon(Icons.Filled.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete), color = Palette.Danger)
            }
        }
        ActionError(act)
    }
    if (confirm) ConfirmDialog(stringResource(R.string.delete_tenant), stringResource(R.string.delete_tenant_confirm), stringResource(R.string.delete),
        onConfirm = { confirm = false; action.run { api.deleteTenant(id) } }, onDismiss = { confirm = false })
}

/* ═════════════════════════ rooms: edit / delete ═════════════════════════ */

@Composable
fun EditPropertyScreen(id: Int, onBack: () -> Unit, onDone: () -> Unit) {
    val api = LocalAppContainer.current.api
    val vm = rememberLoader("property-$id") { ownerProperty(id) }
    val state by vm.state.collectAsStateWithLifecycle()
    val save = rememberAction<Unit>("property-save-$id")
    val del = rememberAction<Unit>("property-delete-$id")
    val saveState by save.state.collectAsStateWithLifecycle()
    val delState by del.state.collectAsStateWithLifecycle()
    LaunchedEffect(saveState, delState) { if (saveState is ActionState.Done<*> || delState is ActionState.Done<*>) onDone() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(stringResource(R.string.edit_room), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { p ->
            var address by rememberSaveable { mutableStateOf(p.address) }
            var city by rememberSaveable { mutableStateOf(p.city) }
            var st by rememberSaveable { mutableStateOf(p.state) }
            var pincode by rememberSaveable { mutableStateOf(p.pincode) }
            var rent by rememberSaveable { mutableStateOf(Money.format(p.baseRent, withSymbol = false).replace(",", "")) }
            var deposit by rememberSaveable { mutableStateOf(Money.format(p.depositAmount, withSymbol = false).replace(",", "")) }
            var status by rememberSaveable { mutableStateOf(p.status) }
            var tried by rememberSaveable { mutableStateOf(false) }
            val rentV = Money.parse(rent); val depV = Money.parse(deposit)
            val valid = address.isNotBlank() && city.isNotBlank() && st.isNotBlank() && pincode.matches(Regex("\\d{6}")) &&
                rentV != null && rentV > 0 && depV != null && depV >= 0
            val occupied = p.status == "occupied"
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(p.propertyCode, style = MaterialTheme.typography.titleLarge)
                FormField(address, { address = it.take(255) }, stringResource(R.string.address), tried && address.isBlank())
                FormField(city, { city = it.take(100) }, stringResource(R.string.city), tried && city.isBlank())
                FormField(st, { st = it.take(100) }, stringResource(R.string.state), tried && st.isBlank())
                FormField(pincode, { pincode = it.filter(Char::isDigit).take(6) }, stringResource(R.string.pincode), tried && !pincode.matches(Regex("\\d{6}")), KeyboardType.Number)
                FormField(rent, { rent = amountInput(it) }, stringResource(R.string.monthly_rent), tried && (rentV == null || rentV <= 0), KeyboardType.Decimal)
                FormField(deposit, { deposit = amountInput(it) }, stringResource(R.string.security_deposit), tried && (depV == null || depV < 0), KeyboardType.Decimal)
                if (occupied) {
                    Text(stringResource(R.string.room_occupied_status_locked), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = status == "available", onClick = { status = "available" }, label = { Text(stringResource(R.string.st_available)) })
                    FilterChip(selected = status == "maintenance", onClick = { status = "maintenance" }, label = { Text(stringResource(R.string.st_maintenance)) })
                }
                ActionError(saveState)
                SubmitButton(stringResource(R.string.save), saveState) {
                    tried = true
                    if (valid) save.run {
                        api.updateProperty(id, NewProperty(p.propertyCode, address.trim(), city.trim(), st.trim(), pincode, rentV!!, depV!!), if (occupied) null else status)
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.delete_room_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ActionError(delState)
                OutlinedButton(onClick = { confirmDelete = true }, enabled = !occupied, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Delete, null, tint = Palette.Danger); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.delete_room), color = Palette.Danger)
                }
            }
        }
    }
    if (confirmDelete) ConfirmDialog(stringResource(R.string.delete_room), stringResource(R.string.delete_room_confirm), stringResource(R.string.delete),
        onConfirm = { confirmDelete = false; del.run { api.deleteProperty(id) } }, onDismiss = { confirmDelete = false })
}

/* ═════════════════════════ charges ═════════════════════════ */

@Composable
fun ChargesScreen(onBack: () -> Unit) {
    val api = LocalAppContainer.current.api
    val vm = rememberLoader("owner-charges") { ownerCharges(includeInactive = true) }
    val state by vm.state.collectAsStateWithLifecycle()
    val toggle = rememberAction<Unit>("charge-toggle")
    val tState by toggle.state.collectAsStateWithLifecycle()
    LaunchedEffect(tState) { if (tState is ActionState.Done<*>) { vm.refresh(); toggle.reset() } }
    var adding by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_charge)) }) },
    ) { outer ->
        ScreenScaffold(stringResource(R.string.charges), onBack = onBack) { pad ->
            StateContent(state, vm::refresh, Modifier.padding(pad).padding(outer)) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Text(stringResource(R.string.charges_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    item { ActionError(tState) }
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_charges), Icons.Filled.Bolt) }
                    items(list, key = { it.id }) { c ->
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Filled.Bolt, if (c.isVariable) Palette.Info else Palette.Brand)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        if (c.isVariable) stringResource(R.string.per_unit_x, Money.format(c.ratePerUnit ?: 0.0), c.unitLabel ?: "unit")
                                        else stringResource(R.string.fixed_per_month_x, Money.format(c.fixedAmount ?: 0.0)),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(checked = c.status == "active", onCheckedChange = { on -> toggle.run { api.setChargeStatus(c.id, on) } })
                            }
                        }
                    }
                }
            }
        }
    }
    if (adding) AddChargeDialog(onDismiss = { adding = false }, onSaved = { adding = false; vm.refresh() })
}

@Composable
private fun AddChargeDialog(onDismiss: () -> Unit, onSaved: () -> Unit) {
    val api = LocalAppContainer.current.api
    val action = rememberAction<CreatedId>("charge-add")
    val act by action.state.collectAsStateWithLifecycle()
    LaunchedEffect(act) { if (act is ActionState.Done<*>) { action.reset(); onSaved() } }
    var name by rememberSaveable { mutableStateOf("") }
    var variable by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable { mutableStateOf("unit") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val v = Money.parse(amount)
    val valid = name.isNotBlank() && v != null && v >= 0
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_charge)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FormField(name, { name = it.take(150) }, stringResource(R.string.charge_name), tried && name.isBlank())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !variable, onClick = { variable = false }, label = { Text(stringResource(R.string.fixed_monthly)) })
                    FilterChip(selected = variable, onClick = { variable = true }, label = { Text(stringResource(R.string.meter_based)) })
                }
                FormField(amount, { amount = amountInput(it) }, stringResource(if (variable) R.string.rate_per_unit else R.string.amount), tried && (v == null || v < 0), KeyboardType.Decimal)
                if (variable) FormField(unit, { unit = it.take(50) }, stringResource(R.string.unit_label))
                ActionError(act)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                tried = true
                if (valid) action.run {
                    api.addCharge(
                        if (variable) NewCharge(name.trim(), "variable", ratePerUnit = v, unitLabel = unit.ifBlank { "unit" })
                        else NewCharge(name.trim(), "fixed", fixedAmount = v),
                    )
                }
            }, enabled = act !is ActionState.Running) { Text(stringResource(R.string.save), fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/* ═════════════════════════ allocations ═════════════════════════ */

@Composable
fun AllocationsScreen(onBack: () -> Unit, newAllocation: () -> Unit, generateInvoice: (Int) -> Unit) {
    val api = LocalAppContainer.current.api
    var filter by rememberSaveable { mutableStateOf("active") }
    val vm = rememberLoader("owner-allocations-$filter") { ownerAllocations(filter) }
    val state by vm.state.collectAsStateWithLifecycle()
    val deposit = rememberAction<Unit>("alloc-deposit")
    val close = rememberAction<AllocationClosed>("alloc-close")
    val dState by deposit.state.collectAsStateWithLifecycle()
    val cState by close.state.collectAsStateWithLifecycle()
    var depositFor by rememberSaveable { mutableStateOf(0) }
    var closeFor by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(dState) { if (dState is ActionState.Done<*>) vm.refresh() }
    LaunchedEffect(cState) { if (cState is ActionState.Done<*>) vm.refresh() }
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = { ExtendedFloatingActionButton(onClick = newAllocation, icon = { Icon(Icons.Filled.Key, null) }, text = { Text(stringResource(R.string.allocate_room)) }) },
    ) { outer ->
        ScreenScaffold(stringResource(R.string.allocations), onBack = onBack) { pad ->
            StateContent(state, vm::refresh, Modifier.padding(pad).padding(outer)) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = filter == "active", onClick = { filter = "active" }, label = { Text(stringResource(R.string.st_active)) })
                            FilterChip(selected = filter == "closed", onClick = { filter = "closed" }, label = { Text(stringResource(R.string.st_closed)) })
                        }
                    }
                    item {
                        ActionError(dState); ActionError(cState)
                        (cState as? ActionState.Done<AllocationClosed>)?.result?.let { r ->
                            SuccessText(stringResource(R.string.settlement_result, Money.format(r.settlementAmount), Money.format(r.pendingRent)))
                        }
                    }
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_allocations), Icons.Filled.MeetingRoom) }
                    items(list, key = { it.id }) { a ->
                        AppCard {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(a.tenantName ?: "—", fontWeight = FontWeight.SemiBold)
                                        Text(listOfNotNull(a.propertyCode, a.city).joinToString(", ") + " · " + dateText(a.allocationDate),
                                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    StatusChip(a.status, if (a.isActive) stringResource(R.string.st_active) else stringResource(R.string.st_closed))
                                }
                                Spacer(Modifier.height(6.dp))
                                LabelValue(stringResource(R.string.monthly_rent), Money.format(a.baseRent))
                                LabelValue(stringResource(R.string.deposit_agreed), Money.format(a.depositAgreed))
                                LabelValue(stringResource(R.string.deposit_collected), Money.format(a.depositCollected), Palette.Success)
                                if (a.depositBalance > 0) LabelValue(stringResource(R.string.deposit_pending), Money.format(a.depositBalance), Palette.Danger, bold = true)
                                if (!a.isActive) a.settlementAmount?.let { LabelValue(stringResource(R.string.settlement), Money.format(it), bold = true) }
                                if (a.isActive) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(onClick = { generateInvoice(a.id) }) { Icon(Icons.Filled.Receipt, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.generate_bill)) }
                                    if (a.depositBalance > 0) TextButton(onClick = { deposit.reset(); depositFor = a.id }) { Text(stringResource(R.string.collect_deposit)) }
                                    TextButton(onClick = { close.reset(); closeFor = a.id }) { Text(stringResource(R.string.vacate), color = Palette.Danger) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    val list = (state as? UiState.Data)?.value.orEmpty()
    list.firstOrNull { it.id == depositFor }?.let { a ->
        AmountDialog(stringResource(R.string.collect_deposit) + " · " + (a.tenantName ?: ""), a.depositBalance,
            onConfirm = { amt -> depositFor = 0; deposit.run { api.collectDeposit(a.id, amt); Unit } }, onDismiss = { depositFor = 0 })
    }
    list.firstOrNull { it.id == closeFor }?.let { a ->
        TextDialog(
            title = stringResource(R.string.vacate) + " · " + (a.tenantName ?: ""),
            label = stringResource(R.string.settlement_notes),
            confirmLabel = stringResource(R.string.vacate),
            required = false,
            message = stringResource(R.string.vacate_confirm),
            onConfirm = { notes -> closeFor = 0; close.run { api.closeAllocation(a.id, notes) } },
            onDismiss = { closeFor = 0 },
        )
    }
}

@Composable
fun NewAllocationScreen(onBack: () -> Unit, onDone: () -> Unit) {
    val api = LocalAppContainer.current.api
    val props = rememberLoader("alloc-props") { ownerProperties() }
    val tenants = rememberLoader("alloc-tenants") { ownerTenants() }
    val charges = rememberLoader("alloc-charges") { ownerCharges() }
    val pState by props.state.collectAsStateWithLifecycle()
    val tState by tenants.state.collectAsStateWithLifecycle()
    val cState by charges.state.collectAsStateWithLifecycle()
    val action = rememberAction<Unit>("alloc-new")
    val act by action.state.collectAsStateWithLifecycle()
    LaunchedEffect(act) { if (act is ActionState.Done<*>) onDone() }

    var propId by rememberSaveable { mutableStateOf(0) }
    var tenantId by rememberSaveable { mutableStateOf(0) }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var agreed by rememberSaveable { mutableStateOf("") }
    var collected by rememberSaveable { mutableStateOf("0") }
    val chosen = remember { mutableStateMapOf<Int, Boolean>() }
    var tried by rememberSaveable { mutableStateOf(false) }

    ScreenScaffold(stringResource(R.string.allocate_room), onBack = onBack) { pad ->
        val p = (pState as? UiState.Data)?.value
        val t = (tState as? UiState.Data)?.value
        val c = (cState as? UiState.Data)?.value
        if (p == null || t == null || c == null) { LoadingBox(Modifier.padding(pad)); return@ScreenScaffold }
        val freeRooms = p.filter { it.status == "available" }
        val freeTenants = t.filter { it.status == "active" && it.allocatedProperty == null }
        val agreedV = Money.parse(agreed); val collV = Money.parse(collected)
        val dateOk = Dates.parse(date) != null
        val valid = propId > 0 && tenantId > 0 && dateOk && agreedV != null && agreedV >= 0 && collV != null && collV >= 0 && collV <= agreedV + 0.005
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceField(stringResource(R.string.room), freeRooms.map { it.id to "${it.propertyCode} · ${it.city} · ${Money.format(it.baseRent, decimals = 0)}" }, propId,
                { id -> propId = id; if (agreed.isBlank()) freeRooms.firstOrNull { it.id == id }?.let { r -> agreed = Money.format(r.depositAmount, withSymbol = false).replace(",", "") } },
                isError = tried && propId == 0, emptyText = stringResource(R.string.no_free_rooms))
            ChoiceField(stringResource(R.string.tenant), freeTenants.map { it.id to (it.fullName + (it.phone?.let { ph -> " · $ph" } ?: "")) }, tenantId, { tenantId = it },
                isError = tried && tenantId == 0, emptyText = stringResource(R.string.no_free_tenants))
            FormField(date, { date = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) }, stringResource(R.string.allocation_date_hint), tried && !dateOk)
            FormField(agreed, { agreed = amountInput(it) }, stringResource(R.string.deposit_agreed), tried && (agreedV == null || agreedV < 0), KeyboardType.Decimal)
            FormField(collected, { collected = amountInput(it) }, stringResource(R.string.deposit_collected_now), tried && (collV == null || agreedV == null || collV > agreedV + 0.005), KeyboardType.Decimal)
            if (c.isNotEmpty()) {
                Text(stringResource(R.string.monthly_charges), style = MaterialTheme.typography.titleMedium)
                c.forEach { ch ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = chosen[ch.id] == true, onCheckedChange = { chosen[ch.id] = it })
                        Text(ch.name + " · " + if (ch.isVariable) stringResource(R.string.per_unit_x, Money.format(ch.ratePerUnit ?: 0.0), ch.unitLabel ?: "unit")
                            else Money.format(ch.fixedAmount ?: 0.0))
                    }
                }
            }
            ActionError(act)
            SubmitButton(stringResource(R.string.allocate_room), act) {
                tried = true
                if (valid) action.run {
                    api.allocate(NewAllocation(propId, tenantId, date, agreedV!!, collV!!, chosen.filterValues { it }.keys.toList())); Unit
                }
            }
        }
    }
}

/* ═════════════════════════ generate & cancel invoices ═════════════════════════ */

@Composable
fun GenerateInvoiceScreen(allocationId: Int, onBack: () -> Unit, onDone: (Int) -> Unit) {
    val api = LocalAppContainer.current.api
    val today = Dates.today()
    var month by rememberSaveable { mutableStateOf(today.substring(5, 7).toInt()) }
    var year by rememberSaveable { mutableStateOf(today.substring(0, 4).toInt()) }
    val vm = rememberLoader("inv-ctx-$allocationId-$month-$year") { invoiceContext(allocationId, month, year) }
    val state by vm.state.collectAsStateWithLifecycle()
    val action = rememberAction<GeneratedInvoice>("inv-gen-$allocationId")
    val act by action.state.collectAsStateWithLifecycle()
    LaunchedEffect(act) { (act as? ActionState.Done<GeneratedInvoice>)?.let { onDone(it.result.id) } }
    val months = stringArrayResource(R.array.months).toList()

    ScreenScaffold(stringResource(R.string.generate_bill), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(16.dp, 8.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1.4f)) { ChoiceField(stringResource(R.string.month), (1..12).map { it to months[it - 1] }, month, { month = it }) }
                val y0 = today.substring(0, 4).toInt()
                Column(Modifier.weight(1f)) { ChoiceField(stringResource(R.string.year), (y0 - 1..y0 + 1).map { it to it.toString() }, year, { year = it }) }
            }
            StateContent(state, vm::refresh) { ctx ->
                val fixedOn = remember(ctx) { mutableStateMapOf<Int, Boolean>().apply { ctx.fixedCharges.forEach { put(it.tcId, true) } } }
                val prev = remember(ctx) { mutableStateMapOf<Int, String>().apply { ctx.variableCharges.forEach { put(it.tcId, it.lastReading?.let { r -> trimNum(r) } ?: "") } } }
                val curr = remember(ctx) { mutableStateMapOf<Int, String>() }
                var tried by rememberSaveable { mutableStateOf(false) }
                val readings = ctx.variableCharges.map { v ->
                    Triple(v, Money.parse(prev[v.tcId] ?: ""), Money.parse(curr[v.tcId] ?: ""))
                }
                val readingsOk = readings.all { (_, p, c) -> (p == null && c == null) || (p != null && c != null && c >= p) }
                val estimate = ctx.baseRent + ctx.fixedCharges.filter { fixedOn[it.tcId] == true }.sumOf { it.amount } +
                    readings.sumOf { (v, p, c) -> if (p != null && c != null && c >= p) (c - p) * v.rate else 0.0 }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AppCard {
                        Column {
                            Text(ctx.tenantName, style = MaterialTheme.typography.titleMedium)
                            Text(ctx.propertyName + " · " + ctx.periodLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            LabelValue(stringResource(R.string.base_rent), Money.format(ctx.baseRent))
                            if (ctx.otherOutstanding > 0) LabelValue(stringResource(R.string.other_outstanding), Money.format(ctx.otherOutstanding), Palette.Danger)
                        }
                    }
                    if (ctx.alreadyExists) Text(stringResource(R.string.bill_exists_for_month), color = Palette.Danger, fontWeight = FontWeight.SemiBold)
                    ctx.fixedCharges.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = fixedOn[f.tcId] == true, onCheckedChange = { fixedOn[f.tcId] = it })
                            Text(f.name, Modifier.weight(1f)); Text(Money.format(f.amount), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    ctx.variableCharges.forEach { v ->
                        AppCard {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(v.name + " · " + stringResource(R.string.per_unit_x, Money.format(v.rate), v.unitLabel ?: "unit"), fontWeight = FontWeight.SemiBold)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(Modifier.weight(1f)) { FormField(prev[v.tcId] ?: "", { prev[v.tcId] = amountInput(it) }, stringResource(R.string.previous_reading), keyboard = KeyboardType.Decimal) }
                                    Column(Modifier.weight(1f)) {
                                        val p = Money.parse(prev[v.tcId] ?: ""); val c = Money.parse(curr[v.tcId] ?: "")
                                        FormField(curr[v.tcId] ?: "", { curr[v.tcId] = amountInput(it) }, stringResource(R.string.current_reading),
                                            isError = tried && ((p != null) != (c != null) || (p != null && c != null && c < p)), keyboard = KeyboardType.Decimal)
                                    }
                                }
                            }
                        }
                    }
                    if (ctx.variableCharges.isNotEmpty()) Text(stringResource(R.string.reading_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LabelValue(stringResource(R.string.estimated_total), Money.format(estimate), bold = true)
                    ActionError(act)
                    SubmitButton(stringResource(R.string.generate_bill), act) {
                        tried = true
                        if (!ctx.alreadyExists && readingsOk) action.run {
                            api.generateInvoice(
                                allocationId, month, year,
                                ctx.fixedCharges.filter { fixedOn[it.tcId] == true }.map { it.tcId },
                                readings.mapNotNull { (v, p, c) -> if (p != null && c != null) MeterReading(v.tcId, p, c) else null },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun trimNum(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

/** "Cancel bill" button for an unpaid invoice (server refuses once any payment exists). */
@Composable
fun CancelInvoiceButton(invoiceId: Int, onCancelled: () -> Unit) {
    val api = LocalAppContainer.current.api
    val action = rememberAction<Unit>("inv-cancel-$invoiceId")
    val act by action.state.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(act) { if (act is ActionState.Done<*>) { action.reset(); onCancelled() } }
    ActionError(act)
    OutlinedButton(onClick = { asking = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.cancel_bill), color = Palette.Danger) }
    if (asking) TextDialog(
        title = stringResource(R.string.cancel_bill), label = stringResource(R.string.reason), confirmLabel = stringResource(R.string.cancel_bill),
        required = true, message = stringResource(R.string.cancel_bill_confirm),
        onConfirm = { r -> asking = false; action.run { api.cancelInvoice(invoiceId, r) } }, onDismiss = { asking = false },
    )
}
