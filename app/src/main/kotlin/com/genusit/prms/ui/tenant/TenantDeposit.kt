package com.genusit.prms.ui.tenant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.DepositSummary
import com.genusit.prms.domain.usecase.GetTenantDepositsUseCase
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.owner.DepositFigures
import com.genusit.prms.ui.owner.DepositTxnRow
import com.genusit.prms.ui.owner.SettlementStatementCard
import com.genusit.prms.ui.owner.StatementShareActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** 1.11: the tenant's own security deposit per room, its history, and the vacate settlement statement. */
@HiltViewModel
class TenantDepositViewModel @Inject constructor(
    getDeposits: GetTenantDepositsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val deposits = loadable(live = true) { getDeposits() }
}

@Composable
fun TenantDepositScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<TenantDepositViewModel>().deposits
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.deposit_and_settlement), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (list.isEmpty()) item { EmptyState(stringResource(R.string.no_deposit_history)) }
                items(list, key = { it.allocationId }) { d ->
                    AppCard {
                        Column {
                            Text(listOfNotNull(d.propertyCode, d.city).joinToString(", "), style = MaterialTheme.typography.titleMedium)
                            Text(dateText(d.allocationDate) + (d.deallocationDate?.let { " → " + dateText(it) } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            DepositFigures(DepositSummary(d.allocationId, d.agreed, d.collected, d.pending, d.adjusted, d.refunded, d.held), showUsage = true)
                            if (d.history.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Text(stringResource(R.string.deposit_history), fontWeight = FontWeight.SemiBold)
                                d.history.forEach { DepositTxnRow(it) }
                            }
                        }
                    }
                    d.statement?.let { st ->
                        Spacer(Modifier.height(8.dp))
                        SettlementStatementCard(st, stringResource(R.string.vacate_settlement))
                        Spacer(Modifier.height(8.dp))
                        StatementShareActions(st, null)
                    }
                }
            }
        }
    }
}
