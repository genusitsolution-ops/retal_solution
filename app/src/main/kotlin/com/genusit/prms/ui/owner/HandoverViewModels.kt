package com.genusit.prms.ui.owner

import androidx.lifecycle.SavedStateHandle
import com.genusit.prms.domain.model.AgentHandover
import com.genusit.prms.domain.model.CollectionDashboard
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.EmployeeCollectionDetail
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.SettlementDetail
import com.genusit.prms.domain.model.SettlementPage
import com.genusit.prms.domain.usecase.CancelSettlementUseCase
import com.genusit.prms.domain.usecase.ConfirmSettlementUseCase
import com.genusit.prms.domain.usecase.GetAgentHandoverUseCase
import com.genusit.prms.domain.usecase.GetAgentSettlementUseCase
import com.genusit.prms.domain.usecase.GetAgentSettlementsUseCase
import com.genusit.prms.domain.usecase.GetCollectionDashboardUseCase
import com.genusit.prms.domain.usecase.GetEmployeeCollectionsUseCase
import com.genusit.prms.domain.usecase.GetOwnerSettlementUseCase
import com.genusit.prms.domain.usecase.GetOwnerSettlementsUseCase
import com.genusit.prms.domain.usecase.RecordHandoverUseCase
import com.genusit.prms.domain.usecase.RejectSettlementUseCase
import com.genusit.prms.domain.usecase.SubmitHandoverUseCase
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.SessionExpiryHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/** Settlement list query (status "" = all, employee 0 = all). */
data class SettlementQuery(val status: String = "", val employeeId: Int = 0, val page: Int = 1)

/** Owner collection dashboard: summary, employee-wise figures, tenant collection history and handover history. */
@HiltViewModel
class CollectionDashboardViewModel @Inject constructor(
    private val getDashboard: GetCollectionDashboardUseCase,
    private val getSettlements: GetOwnerSettlementsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val _filter = MutableStateFlow(Dates.thisMonth().let { (f, t) -> CollectionFilter(from = f, to = t) })
    val filter: StateFlow<CollectionFilter> = _filter.asStateFlow()
    private val _query = MutableStateFlow(SettlementQuery())
    val query: StateFlow<SettlementQuery> = _query.asStateFlow()

    val dashboard = paramLoadable<CollectionFilter, CollectionDashboard> { getDashboard(it) }
    val settlements = paramLoadable<SettlementQuery, SettlementPage> { getSettlements(it.status, it.employeeId, it.page) }

    init { dashboard.load(_filter.value); settlements.load(_query.value) }

    /** New filters start again at page 1. */
    fun setFilter(f: CollectionFilter) { _filter.value = f.copy(page = 1); dashboard.load(_filter.value) }
    fun page(p: Int) { _filter.value = _filter.value.copy(page = p.coerceAtLeast(1)); dashboard.load(_filter.value) }
    fun setQuery(q: SettlementQuery) { _query.value = q; settlements.load(q) }
    fun refresh() { dashboard.refresh(); settlements.refresh() }
}

/** Range + property filter on one employee's collections. */
data class EmployeeQuery(val from: String = "", val to: String = "", val propertyId: Int = 0, val page: Int = 1)

@HiltViewModel
class EmployeeCollectionViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val getEmployee: GetEmployeeCollectionsUseCase,
    private val recordHandover: RecordHandoverUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val employeeId = handle.intArg()
    private val _query = MutableStateFlow(EmployeeQuery())
    val query: StateFlow<EmployeeQuery> = _query.asStateFlow()
    val detail = paramLoadable<EmployeeQuery, EmployeeCollectionDetail> { q -> getEmployee(employeeId, q.from, q.to, q.propertyId, q.page) }
    val record = action<SettlementDetail>()

    init { detail.load(_query.value) }

    fun setQuery(q: EmployeeQuery) { _query.value = q; detail.load(q) }

    /** The owner records cash received directly from this employee (confirmed at once). */
    fun recordReceived(input: HandoverInput) = record.run(onSuccess = { record.reset(); detail.refresh(); sendEvent(ScreenEvent.HandoverSaved(it.id)) }) { key ->
        recordHandover(employeeId, input, key)
    }
}

/**
 * One handover. [owner] decides which API is used: the owner can confirm (fully or partly) or
 * reject; the employee can cancel a handover that is still awaiting confirmation.
 */
@HiltViewModel
class SettlementDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    private val ownerGet: GetOwnerSettlementUseCase,
    private val agentGet: GetAgentSettlementUseCase,
    private val confirmUseCase: ConfirmSettlementUseCase,
    private val rejectUseCase: RejectSettlementUseCase,
    private val cancelUseCase: CancelSettlementUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val owner: Boolean = handle.get<String>("role") != "agent"
    val detail = loadable { if (owner) ownerGet(id) else agentGet(id) }
    val review = action<SettlementDetail>()

    fun canConfirm(received: Double?, submitted: Double) = confirmUseCase.isValid(received, submitted)
    fun confirm(received: Double, note: String) = review.run(onSuccess = { detail.refresh() }) { confirmUseCase(id, received, note) }
    fun reject(reason: String) = review.run(onSuccess = { detail.refresh() }) { rejectUseCase(id, reason) }
    fun cancel() = review.run(onSuccess = { detail.refresh() }) { cancelUseCase(id) }
}

/* ───────── employee ───────── */

@HiltViewModel
class AgentHandoverViewModel @Inject constructor(
    getBalance: GetAgentHandoverUseCase,
    private val getSettlements: GetAgentSettlementsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val balance = loadable { getBalance() }
    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()
    val history = paramLoadable<String, SettlementPage> { getSettlements(it, 1) }

    init { history.load("") }

    fun setStatus(s: String) { _status.value = s; history.load(s) }
    fun refresh() { balance.refresh(); history.refresh() }
}

@HiltViewModel
class SubmitHandoverViewModel @Inject constructor(
    getBalance: GetAgentHandoverUseCase,
    private val submitUseCase: SubmitHandoverUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val balance = loadable<AgentHandover> { getBalance() }
    val submit = action<SettlementDetail>()

    fun problems(input: HandoverInput, available: Double) = SubmitHandoverUseCase.problems(input, available)

    /** Retrying after a failure reuses the same idempotency key, so a handover is never sent twice. */
    fun send(input: HandoverInput) = submit.run(onSuccess = { sendEvent(ScreenEvent.HandoverSaved(it.id)) }) { key -> submitUseCase(input, key) }
}
