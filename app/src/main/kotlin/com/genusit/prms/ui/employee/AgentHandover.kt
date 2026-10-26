package com.genusit.prms.ui.employee

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.HandoverMethod
import com.genusit.prms.domain.usecase.HandoverProblem
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StatTile
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.WalletCard
import com.genusit.prms.ui.common.amountInput
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.owner.AgentHandoverViewModel
import com.genusit.prms.ui.owner.MethodChips
import com.genusit.prms.ui.owner.SettlementCard
import com.genusit.prms.ui.owner.SettlementStatusChips
import com.genusit.prms.ui.owner.SubmitHandoverViewModel
import com.genusit.prms.ui.theme.Palette

/** Compact card on the agent's home screen: money still to hand over. */
@Composable
fun HandoverHomeCard(onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.genusit.prms.ui.common.IconBadge(Icons.Filled.AccountBalanceWallet, Palette.Accent)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.ho_hand_over_cash), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.ho_hand_over_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            com.genusit.prms.ui.common.OpenHint()
        }
    }
}

/** Employee: own balance with the owner and the history of handovers. */
@Composable
fun AgentHandoverScreen(onBack: () -> Unit, submit: () -> Unit, openSettlement: (Int) -> Unit) {
    val model: AgentHandoverViewModel = hiltViewModel()
    val state by model.balance.state.collectAsStateWithLifecycle()
    val hState by model.history.state.collectAsStateWithLifecycle()
    val status by model.status.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.ho_hand_over_cash), onBack = onBack, actions = { IconButton(onClick = model::refresh) { Icon(Icons.Filled.Refresh, stringResource(R.string.refresh)) } }) { pad ->
        StateContent(state, model.balance::refresh, Modifier.padding(pad)) { h ->
            val b = h.balance
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WalletCard(stringResource(R.string.ho_still_with_you), b.outstanding, stringResource(R.string.ho_can_hand_over_x, Money.format(b.available))) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(Icons.Filled.CheckCircle, stringResource(R.string.ho_confirmed_by_owner), Money.format(b.confirmed), Palette.Success, Modifier.weight(1f))
                        StatTile(Icons.Filled.HourglassTop, stringResource(R.string.ho_awaiting_confirmation), Money.format(b.awaitingConfirmation), Palette.Warning, Modifier.weight(1f))
                    }
                }
                item {
                    AppCard {
                        Column {
                            com.genusit.prms.ui.common.LabelValue(stringResource(R.string.ho_total_collected), Money.format(b.collected) + " (" + b.collections + ")")
                            com.genusit.prms.ui.common.LabelValue(stringResource(R.string.ho_last_handover), dateText(b.lastSettlementDate))
                            Text(stringResource(R.string.ho_counts_after_confirm), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item {
                    Button(onClick = submit, enabled = b.available > 0, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AccountBalanceWallet, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.ho_submit_handover), fontWeight = FontWeight.Bold)
                    }
                }
                item { SectionHeader(stringResource(R.string.ho_history)) }
                item { SettlementStatusChips(status, model::setStatus) }
                when (val hs = hState) {
                    is com.genusit.prms.ui.common.UiState.Data -> {
                        if (hs.value.items.isEmpty()) item { EmptyState(stringResource(R.string.ho_no_handovers)) }
                        items(hs.value.items, key = { it.id }) { s -> SettlementCard(s, showEmployee = false) { openSettlement(s.id) } }
                    }
                    is com.genusit.prms.ui.common.UiState.Error -> item { com.genusit.prms.ui.common.ErrorBox(hs.error, model.history::refresh) }
                    else -> item { com.genusit.prms.ui.common.LoadingBox() }
                }
            }
        }
    }
}

/** Employee hands over money: an amount (oldest collections first) or chosen collections. */
@Composable
fun SubmitHandoverScreen(onBack: () -> Unit, onDone: (Int) -> Unit) {
    val model: SubmitHandoverViewModel = hiltViewModel()
    val state by model.balance.state.collectAsStateWithLifecycle()
    val act by model.submit.state.collectAsStateWithLifecycle()
    EventEffect(model) { if (it is ScreenEvent.HandoverSaved) onDone(it.settlementId) }
    var bySelection by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(listOf<Int>()) }
    var method by rememberSaveable { mutableStateOf(HandoverMethod.CASH) }
    var date by rememberSaveable { mutableStateOf(Dates.today()) }
    var reference by rememberSaveable { mutableStateOf("") }
    var remarks by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.ho_submit_handover), onBack = onBack) { pad ->
        StateContent(state, model.balance::refresh, Modifier.padding(pad)) { h ->
            val available = h.balance.available
            val chosen = h.openCollections.filter { it.id in selected }
            val input = HandoverInput(if (bySelection) null else Money.parse(amount), if (bySelection) selected else emptyList(), method, date, reference, remarks)
            val problems = model.problems(input, available) + if (bySelection && selected.isEmpty()) setOf(HandoverProblem.AMOUNT) else emptySet()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WalletCard(stringResource(R.string.ho_can_hand_over), available, stringResource(R.string.ho_counts_after_confirm))
                if (available <= 0) { EmptyState(stringResource(R.string.ho_nothing_to_hand_over)); return@Column }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !bySelection, onClick = { bySelection = false }, label = { Text(stringResource(R.string.ho_enter_amount)) })
                    FilterChip(selected = bySelection, onClick = { bySelection = true }, label = { Text(stringResource(R.string.ho_select_collections)) })
                }
                if (!bySelection) {
                    FormField(amount, { amount = amountInput(it) }, stringResource(R.string.amount),
                        tried && (HandoverProblem.AMOUNT in problems || HandoverProblem.MORE_THAN_AVAILABLE in problems), KeyboardType.Decimal,
                        supporting = if (tried && HandoverProblem.MORE_THAN_AVAILABLE in problems) stringResource(R.string.ho_more_than_available, Money.format(available))
                        else stringResource(R.string.ho_oldest_first))
                } else {
                    Text(stringResource(R.string.ho_selected_x, Money.format(chosen.sumOf { it.available })), fontWeight = FontWeight.Bold)
                    if (tried && selected.isEmpty()) Text(stringResource(R.string.ho_select_one), color = MaterialTheme.colorScheme.error)
                    h.openCollections.forEach { c ->
                        AppCard(onClick = { selected = if (c.id in selected) selected - c.id else selected + c.id }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = c.id in selected, onCheckedChange = { on -> selected = if (on) selected + c.id else selected - c.id })
                                Column(Modifier.weight(1f)) {
                                    Text(c.tenantName ?: "—", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(listOfNotNull(dateText(c.collectionDate), c.propertyCode, c.paymentModeLabel, c.receiptNumber).joinToString(" · "),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(Money.format(c.available), fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Text(stringResource(R.string.ho_method), style = MaterialTheme.typography.labelLarge)
                MethodChips(method) { method = it }
                FormField(date, { date = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, stringResource(R.string.ho_handover_date), tried && HandoverProblem.DATE in problems,
                    supporting = stringResource(R.string.payment_date_hint))
                FormField(reference, { reference = it.take(100) }, stringResource(R.string.ho_reference), tried && HandoverProblem.REFERENCE in problems,
                    supporting = if (method.needsReference) stringResource(R.string.ho_reference_needed) else null)
                FormField(remarks, { remarks = it.take(500) }, stringResource(R.string.remarks), singleLine = false, minLines = 2)
                ActionError(act)
                SubmitButton(stringResource(R.string.ho_submit_to_owner), act) { tried = true; if (problems.isEmpty()) model.send(input) }
            }
        }
    }
}
