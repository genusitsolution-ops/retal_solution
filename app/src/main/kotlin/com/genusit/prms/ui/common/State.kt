package com.genusit.prms.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.genusit.prms.di.ApplicationScope
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.usecase.ExpireSessionUseCase
import com.genusit.prms.domain.util.IdempotencyKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/* ═════════════════════════ UI state models ═════════════════════════ */

/** What a screen shows while it loads data. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Data<T>(val value: T, val refreshing: Boolean = false) : UiState<T>
    data class Error(val error: UiError) : UiState<Nothing>
}

/** Server messages are already localized; network and server-configuration problems use the app's own strings. */
data class UiError(val message: String?, val isNetwork: Boolean, val code: String?) {
    companion object {
        fun from(t: Throwable): UiError = when (t) {
            is ApiException -> UiError(if (t.isNetwork || t.isServerMisconfig) null else t.message, t.isNetwork, t.code)
            else -> UiError(null, false, null)
        }
    }
}

/** State of a submit action (payment, new property, query...). */
sealed interface ActionState<out R> {
    data object Idle : ActionState<Nothing>
    data object Running : ActionState<Nothing>
    data class Done<R>(val result: R) : ActionState<R>
    data class Failed(val error: UiError) : ActionState<Nothing>
}

/** One-time events from a ViewModel to its screen (navigation, closing a dialog, clearing a form). */
sealed interface ScreenEvent {
    /** A form was saved / an item was deleted – the screen usually goes back. */
    data object Saved : ScreenEvent
    /** An item was deleted – show a confirmation and go back. */
    data object Deleted : ScreenEvent
    /** The tenant was saved but the document photo could not be uploaded (it can be uploaded from the tenant page). */
    data object SavedDocumentFailed : ScreenEvent
    /** A payment or collection was recorded – open its receipt. */
    data class ReceiptReady(val receiptNumber: String) : ScreenEvent
    /** A rent bill was generated – open it. */
    data class InvoiceCreated(val invoiceId: Int) : ScreenEvent
    /** A reply was sent – clear the reply box. */
    data object ReplySent : ScreenEvent
    /** The password was changed – clear the password fields. */
    data object PasswordChanged : ScreenEvent
    /** A charge was added – close the "add charge" dialog. */
    data object ChargeAdded : ScreenEvent
}

/* ═════════════════════════ session expiry ═════════════════════════ */

/**
 * Every screen funnels its failures through here: a 401 (token expired / account deactivated)
 * clears the session, which sends the user back to the login screen. Runs in the application
 * scope so it completes even though the screen that saw the error is being torn down.
 */
@Singleton
class SessionExpiryHandler @Inject constructor(
    private val expireSession: ExpireSessionUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    fun onError(t: Throwable) {
        if (t is ApiException && t.isAuthError) scope.launch { expireSession() }
    }
}

/* ═════════════════════════ state holders ═════════════════════════ */

/** Loads data once (or on demand) and exposes it as a [StateFlow] of [UiState]. */
class Loadable<T>(
    private val scope: CoroutineScope,
    private val expiry: SessionExpiryHandler,
    autoLoad: Boolean,
    private val loader: suspend () -> T,
) {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()
    private var job: Job? = null

    init { if (autoLoad) refresh() }

    /** Reloads; existing data stays visible (marked refreshing) until the new data arrives. */
    fun refresh() {
        val current = _state.value
        _state.value = if (current is UiState.Data) current.copy(refreshing = true) else UiState.Loading
        job?.cancel()
        job = scope.launch {
            _state.value = try {
                UiState.Data(loader())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
                UiState.Error(UiError.from(e))
            }
        }
    }
}

/**
 * Loads data for a parameter (a filter, a date range, a billing month). Changing the parameter
 * shows the loading state and fetches again; the same parameter is not fetched twice.
 */
class ParamLoadable<P, T>(
    private val scope: CoroutineScope,
    private val expiry: SessionExpiryHandler,
    private val loader: suspend (P) -> T,
) {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()
    private var job: Job? = null
    private var param: P? = null
    private var hasParam = false

    fun load(p: P) {
        if (hasParam && p == param) return
        param = p; hasParam = true
        _state.value = UiState.Loading
        fetch(p)
    }

    fun refresh() {
        if (!hasParam) return
        @Suppress("UNCHECKED_CAST")
        val p = param as P
        val current = _state.value
        _state.value = if (current is UiState.Data) current.copy(refreshing = true) else UiState.Loading
        fetch(p)
    }

    private fun fetch(p: P) {
        job?.cancel()
        job = scope.launch {
            _state.value = try {
                UiState.Data(loader(p))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
                UiState.Error(UiError.from(e))
            }
        }
    }
}

/**
 * Runs one submit at a time (double taps are ignored) and keeps an idempotency key for money
 * requests: the same key is reused when the user retries after a failure, and a new key is
 * created only after a success — so a payment can never be recorded twice.
 */
class ActionRunner<R>(
    private val scope: CoroutineScope,
    private val expiry: SessionExpiryHandler,
) {
    private val _state = MutableStateFlow<ActionState<R>>(ActionState.Idle)
    val state: StateFlow<ActionState<R>> = _state.asStateFlow()
    var idempotencyKey: String = IdempotencyKey.new()
        private set

    fun run(onSuccess: (R) -> Unit = {}, block: suspend (idempotencyKey: String) -> R) {
        if (_state.value is ActionState.Running) return
        _state.value = ActionState.Running
        scope.launch {
            try {
                val r = block(idempotencyKey)
                idempotencyKey = IdempotencyKey.new()
                _state.value = ActionState.Done(r)
                onSuccess(r)
            } catch (e: CancellationException) {
                _state.value = ActionState.Idle
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
                _state.value = ActionState.Failed(UiError.from(e))
            }
        }
    }

    fun reset() { _state.value = ActionState.Idle }
}

/* ═════════════════════════ base ViewModel ═════════════════════════ */

/**
 * Base for every screen ViewModel: [StateFlow]s for screen state (through [loadable],
 * [paramLoadable] and [action]) and a [SharedFlow] of one-time [ScreenEvent]s.
 *
 * Events are kept (replay = 1) until the screen has handled them and called [consumeEvent],
 * so a "saved → go back" event is not lost if it arrives while the app is in the background.
 */
abstract class BaseViewModel(protected val expiry: SessionExpiryHandler) : ViewModel() {

    private val _events = MutableSharedFlow<ScreenEvent>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<ScreenEvent> = _events.asSharedFlow()

    protected fun sendEvent(event: ScreenEvent) { _events.tryEmit(event) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun consumeEvent() { _events.resetReplayCache() }

    protected fun <T> loadable(autoLoad: Boolean = true, loader: suspend () -> T): Loadable<T> =
        Loadable(viewModelScope, expiry, autoLoad, loader)

    protected fun <P, T> paramLoadable(loader: suspend (P) -> T): ParamLoadable<P, T> =
        ParamLoadable(viewModelScope, expiry, loader)

    protected fun <R> action(): ActionRunner<R> = ActionRunner(viewModelScope, expiry)
}

/** Collects a ViewModel's one-time events while the screen is at least STARTED. */
@Composable
fun EventEffect(vm: BaseViewModel, onEvent: (ScreenEvent) -> Unit) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val handler by rememberUpdatedState(onEvent)
    LaunchedEffect(vm, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                vm.consumeEvent()
                handler(event)
            }
        }
    }
}
