package com.genusit.prms.ui.tenant

import androidx.lifecycle.SavedStateHandle
import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.NewQuery
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.GetTenantDashboardUseCase
import com.genusit.prms.domain.usecase.GetTenantInvoiceUseCase
import com.genusit.prms.domain.usecase.GetTenantInvoicesUseCase
import com.genusit.prms.domain.usecase.GetTenantPaymentsUseCase
import com.genusit.prms.domain.usecase.GetTenantQueriesUseCase
import com.genusit.prms.domain.usecase.RaiseQueryUseCase
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.owner.intArg
import com.genusit.prms.ui.owner.userNameIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class TenantHomeViewModel @Inject constructor(
    getDashboard: GetTenantDashboardUseCase,
    session: SessionRepository,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val dashboard = loadable { getDashboard() }
    val userName: StateFlow<String> = session.userNameIn(this)
}

@HiltViewModel
class TenantInvoicesViewModel @Inject constructor(
    getInvoices: GetTenantInvoicesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val invoices = loadable { getInvoices() }
}

@HiltViewModel
class TenantInvoiceDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    getInvoice: GetTenantInvoiceUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val invoice = loadable { getInvoice(id) }
}

@HiltViewModel
class TenantPaymentsViewModel @Inject constructor(
    getPayments: GetTenantPaymentsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val payments = loadable { getPayments() }
}

@HiltViewModel
class TenantQueriesViewModel @Inject constructor(
    getQueries: GetTenantQueriesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val queries = loadable { getQueries() }
}

@HiltViewModel
class RaiseQueryViewModel @Inject constructor(
    private val raiseQuery: RaiseQueryUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val submitAction = action<CreatedId>()

    fun submit(q: NewQuery) = submitAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { raiseQuery(q) }
}
