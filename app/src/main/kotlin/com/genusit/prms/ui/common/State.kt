package com.genusit.prms.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import com.genusit.prms.core.net.ApiException
import com.genusit.prms.core.net.PrmsApi
import com.genusit.prms.data.AppContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** What a screen shows while it loads data. */
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Data<T>(val value: T, val refreshing: Boolean = false) : UiState<T>
    data class Error(val error: UiError) : UiState<Nothing>
}

/** Server messages are already localized; network problems use the app's own string. */
data class UiError(val message: String?, val isNetwork: Boolean, val code: String?) {
    companion object {
        fun from(t: Throwable): UiError = when (t) {
            is ApiException -> UiError(if (t.isNetwork || t.isServerMisconfig) null else t.message, t.isNetwork, t.code)
            else -> UiError(null, false, null)
        }
    }
}

/** Generic "load and show" ViewModel used by the read-only screens. */
class LoadViewModel<T>(
    private val loader: suspend () -> T,
    private val onAuthError: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<T>>(UiState.Loading)
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        val current = _state.value
        _state.value = if (current is UiState.Data) current.copy(refreshing = true) else UiState.Loading
        viewModelScope.launch {
            _state.value = try {
                UiState.Data(loader())
            } catch (e: Exception) {
                if (e is ApiException && e.isAuthError) onAuthError()
                UiState.Error(UiError.from(e))
            }
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

/**
 * Runs one submit at a time (double taps are ignored) and keeps an idempotency key for
 * money requests: the same key is reused when the user retries after a failure, and a new
 * key is created only after a success — so a payment can never be recorded twice.
 */
class ActionViewModel<R>(
    private val api: PrmsApi,
    private val onAuthError: () -> Unit,
) : ViewModel() {
    private val _state = MutableStateFlow<ActionState<R>>(ActionState.Idle)
    val state: StateFlow<ActionState<R>> = _state.asStateFlow()
    var idempotencyKey: String = api.newIdempotencyKey()
        private set

    fun run(block: suspend (idempotencyKey: String) -> R) {
        if (_state.value is ActionState.Running) return
        _state.value = ActionState.Running
        viewModelScope.launch {
            _state.value = try {
                val r = block(idempotencyKey)
                idempotencyKey = api.newIdempotencyKey()
                ActionState.Done(r)
            } catch (e: Exception) {
                if (e is ApiException && e.isAuthError) onAuthError()
                ActionState.Failed(UiError.from(e))
            }
        }
    }

    fun reset() { _state.value = ActionState.Idle }
}

/** Screen-scoped loader (survives rotation; one per [key] per navigation entry). */
@Composable
fun <T> rememberLoader(key: String, loader: suspend PrmsApi.() -> T): LoadViewModel<T> {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val container = LocalAppContainer.current
    return remember(owner, key) {
        val factory = object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                @Suppress("UNCHECKED_CAST")
                return LoadViewModel({ container.api.loader() }, container::onAuthError) as VM
            }
        }
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider(owner, factory)["load:$key", LoadViewModel::class.java] as LoadViewModel<T>
    }
}

@Composable
fun <R> rememberAction(key: String): ActionViewModel<R> {
    val owner = checkNotNull(LocalViewModelStoreOwner.current) { "No ViewModelStoreOwner" }
    val container = LocalAppContainer.current
    return remember(owner, key) {
        val factory = object : ViewModelProvider.Factory {
            override fun <VM : ViewModel> create(modelClass: Class<VM>): VM {
                @Suppress("UNCHECKED_CAST")
                return ActionViewModel<R>(container.api, container::onAuthError) as VM
            }
        }
        @Suppress("UNCHECKED_CAST")
        ViewModelProvider(owner, factory)["action:$key", ActionViewModel::class.java] as ActionViewModel<R>
    }
}
