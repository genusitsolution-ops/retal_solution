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
    branding: com.genusit.prms.platform.BrandingStore,
) : androidx.lifecycle.ViewModel() {
    val loaded: StateFlow<Boolean> = session.loaded
    val session: StateFlow<Session?> = session.session
    /** 1.11: logo / name published by the Super Admin (cached on the phone; built-in look when none). */
    val brand: StateFlow<com.genusit.prms.platform.Branding> = branding.branding
    private val _splashDone = kotlinx.coroutines.flow.MutableStateFlow(false)
    /** The branded splash stays at least a short moment, never waiting for the network. */
    val splashDone: StateFlow<Boolean> = _splashDone

    init {
        viewModelScope.launch { restoreSession() }
        viewModelScope.launch { kotlinx.coroutines.delay(700); _splashDone.value = true }
        // refresh the published branding in the background; the next start (and the login screen) show the new logo
        viewModelScope.launch { branding.refresh() }
    }
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
    private val markRead: com.genusit.prms.domain.usecase.MarkNotificationReadUseCase,
    push: com.genusit.prms.platform.PushManager,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val notifications = loadable(live = true) { getNotifications() }

    init { viewModelScope.launch { push.incoming.collect { notifications.refresh() } } }

    /** 1.11: tapping one marks it read (best effort). */
    fun open(id: Int) {
        viewModelScope.launch {
            try { markRead(id) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            notifications.refresh()
        }
    }

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

/** 1.11: signed-in shell — push registration, unread badge. */
@HiltViewModel
class ShellViewModel @Inject constructor(
    val push: com.genusit.prms.platform.PushManager,
    private val getUnread: com.genusit.prms.domain.usecase.GetUnreadCountUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val _unread = kotlinx.coroutines.flow.MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread

    init {
        push.onSignedIn()
        refreshUnread()
        viewModelScope.launch { push.incoming.collect { refreshUnread() } }
        viewModelScope.launch { expiry.dataChanges.changes.collect { refreshUnread() } }
    }

    fun refreshUnread() {
        viewModelScope.launch {
            try { _unread.value = getUnread() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
    }
}

/** 1.11: notification preferences in Settings. */
@HiltViewModel
class NotificationPrefsViewModel @Inject constructor(
    getPrefs: com.genusit.prms.domain.usecase.GetNotificationPrefsUseCase,
    private val savePrefs: com.genusit.prms.domain.usecase.SaveNotificationPrefsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val prefs = loadable { getPrefs() }
    val save = action<com.genusit.prms.domain.model.NotificationPrefs>()
    fun set(category: String, enabled: Boolean) = save.run(onSuccess = { prefs.refresh() }) { savePrefs(mapOf(category to enabled)) }
}
