package com.genusit.prms.admin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.genusit.prms.admin.Session
import com.genusit.prms.admin.net.ApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Data<T>(val value: T) : Load<T>
    data class Error(val message: String) : Load<Nothing>
}

/**
 * One screen's data plus its actions. [load] fetches; [act] runs an action (approve, pay…), shows the server's
 * message and reloads. A 401 ends the session (the app returns to sign-in).
 */
open class ScreenModel<T>(private val session: Session, private val fetch: suspend () -> T) : ViewModel() {
    private val _state = MutableStateFlow<Load<T>>(Load.Loading)
    val state: StateFlow<Load<T>> = _state.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()
    private var job: Job? = null

    init { load() }

    fun load(showSpinner: Boolean = _state.value !is Load.Data) {
        job?.cancel()
        job = viewModelScope.launch {
            if (showSpinner) _state.value = Load.Loading
            _state.value = try { Load.Data(fetch()) } catch (e: CancellationException) { throw e } catch (e: ApiException) {
                if (e.isSessionEnded) session.clear()
                if (_state.value is Load.Data && !showSpinner) { _messages.tryEmit(e.message); _state.value } else Load.Error(e.message)
            } catch (e: Exception) {
                Load.Error("Could not read the server's answer. ${e.message ?: ""}".trim())
            }
        }
    }

    /** Runs [block] once (ignored while another action is running), then reloads. [onDone] runs only on success. */
    fun act(block: suspend () -> String, onDone: () -> Unit = {}) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                _messages.emit(block())
                onDone()
                load(showSpinner = false)
            } catch (e: CancellationException) { throw e } catch (e: ApiException) {
                if (e.isSessionEnded) session.clear()
                _messages.emit(e.message)
            } catch (e: Exception) {
                _messages.emit("Something went wrong. ${e.message ?: ""}".trim())
            } finally { _busy.value = false }
        }
    }
}
