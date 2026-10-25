package com.genusit.prms.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.genusit.prms.core.model.Role
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

private val Context.sessionDataStore by preferencesDataStore(name = "prms_session")

data class Session(val token: String, val role: Role, val name: String)

/**
 * Keeps the login token in app-private DataStore (excluded from backups) and mirrors it in
 * memory so the network layer can read it synchronously.
 */
class SessionStore(private val context: Context) {
    private val kToken = stringPreferencesKey("token")
    private val kRole = stringPreferencesKey("role")
    private val kName = stringPreferencesKey("name")

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    val token: String? get() = _session.value?.token

    suspend fun load() {
        val p = context.sessionDataStore.data.first()
        val token = p[kToken]
        val role = Role.from(p[kRole])
        _session.value = if (!token.isNullOrBlank() && role != null && role != Role.ADMIN) Session(token, role, p[kName] ?: "") else null
        _loaded.value = true
    }

    suspend fun save(s: Session) {
        context.sessionDataStore.edit { it[kToken] = s.token; it[kRole] = s.role.api; it[kName] = s.name }
        _session.value = s
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(kToken); it.remove(kRole); it.remove(kName) }
        _session.value = null
    }
}
