package com.genusit.prms.ui.owner

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material.icons.filled.Redeem
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.NewProperty
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.PaymentRow
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.common.errorText
import com.genusit.prms.ui.theme.Palette

@Composable
fun MenuRow(icon: ImageVector, tint: Color, title: String, subtitle: String? = null, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, tint)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.Muted)
        }
    }
}

@Composable
fun OwnerMoreScreen(open: (String) -> Unit) {
    ScreenScaffold(stringResource(R.string.nav_more)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MenuRow(Icons.AutoMirrored.Filled.ReceiptLong, Palette.Accent, stringResource(R.string.generate_bill), stringResource(R.string.generate_bill_sub)) { open("owner/generate") }
            MenuRow(Icons.Filled.Apartment, Palette.Brand, stringResource(R.string.properties), stringResource(R.string.properties_sub)) { open("owner/properties") }
            MenuRow(Icons.Filled.Key, Palette.Info, stringResource(R.string.allocations), stringResource(R.string.allocations_sub)) { open("owner/allocations") }
            MenuRow(Icons.Filled.Bolt, Palette.Warning, stringResource(R.string.charges), stringResource(R.string.charges_menu_sub)) { open("owner/charges") }
            MenuRow(Icons.Filled.Badge, Palette.Brand, stringResource(R.string.employees), stringResource(R.string.employees_sub)) { open("owner/employees") }
            MenuRow(Icons.Filled.QuestionAnswer, Palette.Danger, stringResource(R.string.complaints), stringResource(R.string.complaints_sub)) { open("owner/queries") }
            MenuRow(Icons.Filled.AccountBalanceWallet, Palette.Accent, stringResource(R.string.ho_dashboard_title), stringResource(R.string.ho_dashboard_sub)) { open("owner/collections-hub") }
            MenuRow(Icons.Filled.Assessment, Palette.Info, stringResource(R.string.reports), stringResource(R.string.reports_sub)) { open("owner/reports") }
            MenuRow(Icons.Filled.CurrencyRupee, Palette.Success, stringResource(R.string.payments), stringResource(R.string.payments_sub)) { open("owner/payments") }
            MenuRow(Icons.Filled.WorkspacePremium, Palette.Warning, stringResource(R.string.subscription), stringResource(R.string.subscription_sub)) { open("owner/subscription") }
            MenuRow(Icons.Filled.Redeem, Palette.Success, stringResource(R.string.refer_earn), stringResource(R.string.refer_earn_sub)) { open("owner/referrals") }
            MenuRow(Icons.Filled.Notifications, Palette.Info, stringResource(R.string.notifications)) { open("notifications") }
            MenuRow(Icons.Filled.Settings, Palette.Muted, stringResource(R.string.settings), stringResource(R.string.settings_sub)) { open("settings") }
        }
    }
}

@Composable
fun PropertiesScreen(onBack: (() -> Unit)?, addProperty: () -> Unit, editProperty: (Int) -> Unit = {}) {
    val vm = hiltViewModel<PropertiesViewModel>().properties
    val state by vm.state.collectAsStateWithLifecycle()
    // reload when coming back from "add property"
    LaunchedEffect(Unit) { if (state !is com.genusit.prms.ui.common.UiState.Loading) vm.refresh() }
    androidx.compose.material3.Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = addProperty, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.add_property)) })
        },
    ) { outer ->
        ScreenScaffold(stringResource(R.string.properties), onBack = onBack) { pad ->
            StateContent(state, vm::refresh, Modifier.padding(pad).padding(outer)) { list ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_properties), Icons.Filled.MeetingRoom) }
                    items(list, key = { it.id }) { p ->
                        AppCard(onClick = { editProperty(p.id) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconBadge(Icons.Filled.MeetingRoom, if (p.status == "occupied") Palette.Info else Palette.Success)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.propertyCode, fontWeight = FontWeight.SemiBold)
                                    Text(listOfNotNull(p.typeName, p.city, p.activeTenantName).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(Money.format(p.baseRent, decimals = 0) + stringResource(R.string.per_month), fontWeight = FontWeight.Bold)
                                    StatusChip(p.status, p.statusLabel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddPropertyScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val model: AddPropertyViewModel = hiltViewModel()
    val act by model.saveAction.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it == ScreenEvent.Saved) onSaved() }
    var code by rememberSaveable { mutableStateOf("") }
    var address by rememberSaveable { mutableStateOf("") }
    var city by rememberSaveable { mutableStateOf("") }
    var stateName by rememberSaveable { mutableStateOf("") }
    var pincode by rememberSaveable { mutableStateOf("") }
    var rent by rememberSaveable { mutableStateOf("") }
    var deposit by rememberSaveable { mutableStateOf("0") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val rentV = Money.parse(rent)
    val depV = Money.parse(deposit)
    val valid = code.isNotBlank() && address.isNotBlank() && city.isNotBlank() && stateName.isNotBlank() &&
        pincode.matches(Regex("\\d{6}")) && rentV != null && rentV > 0 && depV != null && depV >= 0

    ScreenScaffold(stringResource(R.string.add_property), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            @Composable
            fun field(v: String, set: (String) -> Unit, label: Int, ok: Boolean, kb: KeyboardType = KeyboardType.Text) = OutlinedTextField(
                value = v, onValueChange = set, label = { Text(stringResource(label)) }, singleLine = true,
                isError = tried && !ok, keyboardOptions = KeyboardOptions(keyboardType = kb), modifier = Modifier.fillMaxWidth(),
            )
            field(code, { code = it.take(50) }, R.string.property_code, code.isNotBlank())
            field(address, { address = it.take(255) }, R.string.address, address.isNotBlank())
            field(city, { city = it.take(100) }, R.string.city, city.isNotBlank())
            field(stateName, { stateName = it.take(100) }, R.string.state, stateName.isNotBlank())
            field(pincode, { pincode = it.filter(Char::isDigit).take(6) }, R.string.pincode, pincode.matches(Regex("\\d{6}")), KeyboardType.Number)
            field(rent, { rent = it.filter { c -> c.isDigit() || c == '.' } }, R.string.monthly_rent, rentV != null && rentV > 0, KeyboardType.Decimal)
            field(deposit, { deposit = it.filter { c -> c.isDigit() || c == '.' } }, R.string.security_deposit, depV != null && depV >= 0, KeyboardType.Decimal)
            (act as? ActionState.Failed)?.let { Text(errorText(it.error), color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    tried = true
                    if (valid) model.save(NewProperty(code.trim(), address.trim(), city.trim(), stateName.trim(), pincode, rentV!!, depV!!))
                },
                enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.save), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OwnerPaymentsScreen(onBack: () -> Unit, openReceipt: (String) -> Unit) {
    val model = hiltViewModel<OwnerPaymentsViewModel>()
    val vm = model.payments
    val state by vm.state.collectAsStateWithLifecycle()
    val period by model.period.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.payments), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // the period is always visible, so the total can be compared with the dashboard ("This month")
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(PaymentPeriod.MONTH to R.string.period_this_month, PaymentPeriod.LAST_3_MONTHS to R.string.period_3_months, PaymentPeriod.YEAR to R.string.period_this_year)
                    .forEach { (p, label) -> androidx.compose.material3.FilterChip(selected = period == p, onClick = { model.select(p) }, label = { Text(stringResource(label)) }) }
            }
            StateContent(state, vm::refresh) { d ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item {
                        WalletCard(stringResource(R.string.received_in_period), d.total,
                            dateText(d.from) + " – " + dateText(d.to) + " · " + stringResource(R.string.payments_count, d.payments.size))
                    }
                    if (d.payments.isEmpty()) item { EmptyState(stringResource(R.string.no_payments)) }
                    items(d.payments, key = { it.id }) { p ->
                        val receipt = p.receiptNumber.takeIf { it.isNotBlank() }
                        PaymentRow(
                            title = p.tenantName ?: "—",
                            subtitle = listOfNotNull(dateText(p.paymentDate), p.paymentMethodLabel, p.collectedByLabel, receipt)
                                .filter { it.isNotBlank() }.joinToString(" · "),
                            amount = p.amount,
                            // a record without a receipt number has no receipt to open
                            onClick = receipt?.let { r -> { openReceipt(r) } },
                        )
                    }
                }
            }
        }
    }
}
