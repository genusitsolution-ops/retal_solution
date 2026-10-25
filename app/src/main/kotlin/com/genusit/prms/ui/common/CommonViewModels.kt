package com.genusit.prms.ui.common

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.Session
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.ChangePasswordUseCase
import com.genusit.prms.domain.usecase.GetMeUseCase
import com.genusit.prms.domain.usecase.GetNotificationsUseCase
import com.genusit.prms.domain.usecase.GetReceiptUseCase
import com.genusit.prms.domain.usecase.LogoutUseCase
import com.genusit.prms.domain.usecase.MarkAllNotificationsReadUseCase
import com.genusit.prms.domain.usecase.RestoreSessionUseCase
import com.genusit.prms.domain.usecase.SaveLanguageUseCase
import com.genusit.prms.domain.usecase.UpdateContactDetailsUseCase
import com.genusit.prms.di.ApplicationScope
import com.genusit.prms.platform.AppLanguage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** App-level state: is the stored session read yet, and who is signed in. */
@HiltViewModel
class AppViewModel @Inject constructor(
    session: SessionRepository,
    restoreSession: RestoreSessionUseCase,
) : androidx.lifecycle.ViewModel() {
    val loaded: StateFlow<Boolean> = session.loaded
    val session: StateFlow<Session?> = session.session

    init { viewModelScope.launch { restoreSession() } }
}

@HiltViewModel
class ReceiptViewModel @Inject constructor(
    handle: SavedStateHandle,
    getReceipt: GetReceiptUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val receiptNumber: String = handle.get<String>("no").orEmpty()
    val receipt = loadable { getReceipt(receiptNumber) }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    getNotifications: GetNotificationsUseCase,
    private val markAllReadUseCase: MarkAllNotificationsReadUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val notifications = loadable { getNotifications() }

    /** Best effort, then reload (as before). */
    fun markAllRead() {
        viewModelScope.launch {
            try { markAllReadUseCase() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            notifications.refresh()
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    sessionRepository: SessionRepository,
    getMe: GetMeUseCase,
    private val saveLanguage: SaveLanguageUseCase,
    private val updateContactDetails: UpdateContactDetailsUseCase,
    private val changePassword: ChangePasswordUseCase,
    private val logout: LogoutUseCase,
    @ApplicationScope private val appScope: CoroutineScope,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val session: StateFlow<Session?> = sessionRepository.session
    val me = loadable { getMe() }
    val profileAction = action<Unit>()
    val passwordAction = action<Unit>()

    /** Switches the app language now and saves it on the server too (best effort). */
    fun setLanguage(code: String) {
        appScope.launch {
            try { saveLanguage(code) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        AppLanguage.set(code)
    }

    fun saveContact(role: Role, phone: String, email: String?) =
        profileAction.run { updateContactDetails(role, phone, email) }

    fun changePassword(role: Role, current: String, new: String) =
        passwordAction.run(onSuccess = { sendEvent(ScreenEvent.PasswordChanged) }) { changePassword.invoke(role, current, new) }

    /** Runs in the application scope: this screen disappears as soon as the session is cleared. */
    fun logout() {
        appScope.launch { logout.invoke() }
    }
}
