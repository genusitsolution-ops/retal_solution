package com.genusit.prms.ui.auth

import androidx.lifecycle.viewModelScope
import com.genusit.prms.BuildConfig
import com.genusit.prms.R
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.LoginOutcome
import com.genusit.prms.domain.usecase.LoginUseCase
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.SessionExpiryHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Login form state. Exactly one of [errorRes] (an app string) or [error] (a localized server
 * message) is shown. [debugInfo] is filled in debug builds only: the server URL and the technical
 * reason a request failed.
 */
data class LoginUiState(
    val busy: Boolean = false,
    val errorRes: Int = 0,
    val error: String? = null,
    val debugInfo: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val login: LoginUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    /**
     * Signs in. On success the session is saved and the app switches to the user's role screens
     * (AppRoot observes the session), so no navigation event is needed here.
     */
    fun submit(username: String, password: String, lang: String) {
        if (_state.value.busy) return // a second tap while signing in is ignored
        if (username.isBlank() || password.isEmpty()) {
            _state.value = LoginUiState(errorRes = R.string.login_required)
            return
        }
        _state.value = LoginUiState(busy = true)
        viewModelScope.launch {
            val next = try {
                when (login(username, password, lang)) {
                    is LoginOutcome.SignedIn -> LoginUiState()
                    // Super Admin works in the web panel only
                    LoginOutcome.UseWebPanel -> LoginUiState(errorRes = R.string.login_admin_web)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiException) {
                val debug = if (BuildConfig.DEBUG && (e.isNetwork || e.isServerMisconfig)) "Server: ${BuildConfig.API_BASE_URL}\n${e.message}" else null
                when {
                    e.isNetwork -> LoginUiState(errorRes = R.string.error_network, debugInfo = debug)
                    // never show raw "HTTP 301" / HTML to users; details go to debugInfo (debug builds)
                    e.isServerMisconfig -> LoginUiState(errorRes = R.string.error_server, debugInfo = debug)
                    else -> LoginUiState(error = e.message)
                }
            } catch (e: Exception) {
                LoginUiState(errorRes = R.string.error_generic)
            }
            _state.update { next }
        }
    }
}
