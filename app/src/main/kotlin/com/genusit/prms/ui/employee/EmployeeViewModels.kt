package com.genusit.prms.ui.employee

import androidx.lifecycle.SavedStateHandle
import com.genusit.prms.domain.model.PaymentResult
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.CollectRentUseCase
import com.genusit.prms.domain.usecase.GetAgentCollectionsUseCase
import com.genusit.prms.domain.usecase.GetAgentDashboardUseCase
import com.genusit.prms.domain.usecase.GetAgentTenantsUseCase
import com.genusit.prms.domain.usecase.GetMetaUseCase
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.owner.userNameIn
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class AgentHomeViewModel @Inject constructor(
    getDashboard: GetAgentDashboardUseCase,
    session: SessionRepository,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val dashboard = loadable { getDashboard() }
    val userName: StateFlow<String> = session.userNameIn(this)
}

@HiltViewModel
class AgentTenantsViewModel @Inject constructor(
    getTenants: GetAgentTenantsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val tenants = loadable { getTenants() }
}

@HiltViewModel
class AgentCollectionsViewModel @Inject constructor(
    getCollections: GetAgentCollectionsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val collections = loadable { getCollections() }
}

/** Collect rent for an assigned tenant (pre-selected tenant/invoice come from the route). */
@HiltViewModel
class AgentCollectViewModel @Inject constructor(
    handle: SavedStateHandle,
    getTenants: GetAgentTenantsUseCase,
    getDashboard: GetAgentDashboardUseCase,
    getMeta: GetMetaUseCase,
    private val collectRent: CollectRentUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val preTenant: Int = handle.get<Int>("tenant") ?: 0
    val preInvoice: Int = handle.get<Int>("invoice") ?: 0
    val tenants = loadable { getTenants() }
    /** Assigned rooms (to find the property id when an older server sends only the code). */
    val dashboard = loadable { getDashboard() }
    val meta = loadable { getMeta() }
    val collectAction = action<PaymentResult>()

    fun collect(tenantId: Int, propertyId: Int, invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String) =
        collectAction.run(onSuccess = { sendEvent(ScreenEvent.ReceiptReady(it.receiptNumber)) }) { key ->
            collectRent(tenantId, propertyId, invoiceId, amount, mode, date, reference, remarks, key)
        }
}
