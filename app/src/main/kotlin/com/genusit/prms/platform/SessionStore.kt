package com.genusit.prms.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.Session
import com.genusit.prms.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "prms_session")

/**
 * Keeps the login token in app-private DataStore (excluded from backups) and mirrors it in
 * memory so the network layer can read it synchronously.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : SessionRepository {
    private val kToken = stringPreferencesKey("token")
    private val kRole = stringPreferencesKey("role")
    private val kName = stringPreferencesKey("name")

    private val _session = MutableStateFlow<Session?>(null)
    override val session: StateFlow<Session?> = _session.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    override val token: String? get() = _session.value?.token

    override suspend fun load() {
        if (_loaded.value) return
        val p = context.sessionDataStore.data.first()
        val token = p[kToken]
        val role = Role.from(p[kRole])
        _session.value = if (!token.isNullOrBlank() && role != null && role != Role.ADMIN) Session(token, role, p[kName] ?: "") else null
        _loaded.value = true
    }

    override suspend fun save(session: Session) {
        context.sessionDataStore.edit { it[kToken] = session.token; it[kRole] = session.role.api; it[kName] = session.name }
        _session.value = session
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.remove(kToken); it.remove(kRole); it.remove(kName) }
        _session.value = null
    }
}
