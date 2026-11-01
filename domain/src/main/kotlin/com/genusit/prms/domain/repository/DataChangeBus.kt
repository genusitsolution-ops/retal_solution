package com.genusit.prms.domain.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** What changed on the server after a successful write (a payment, a bill, a handover …). */
enum class DataTopic { BILLING, COLLECTIONS, HANDOVERS, TENANTS, ALLOCATIONS, EMPLOYEES, SUBSCRIPTION }

/**
 * Repository-level change notifications (1.10). Repositories publish after the server confirmed a write;
 * every screen that shows server data and is still open (dashboards, bill lists, bill details, employee home …)
 * listens and reloads from the server, so new balances appear everywhere at once without restarting the app.
 * The server stays the source of truth: nothing is patched locally.
 */
interface DataChangeBus {
    val changes: SharedFlow<Set<DataTopic>>
    fun publish(vararg topics: DataTopic)
}

@Singleton
class SharedFlowDataChangeBus @Inject constructor() : DataChangeBus {
    private val flow = MutableSharedFlow<Set<DataTopic>>(extraBufferCapacity = 16, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    override val changes: SharedFlow<Set<DataTopic>> = flow.asSharedFlow()
    override fun publish(vararg topics: DataTopic) { if (topics.isNotEmpty()) flow.tryEmit(topics.toSet()) }
}

/** For code (and tests) that does not need notifications. */
object NoDataChanges : DataChangeBus {
    private val flow = MutableSharedFlow<Set<DataTopic>>()
    override val changes: SharedFlow<Set<DataTopic>> = flow.asSharedFlow()
    override fun publish(vararg topics: DataTopic) {}
}
