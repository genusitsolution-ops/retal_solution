package com.genusit.prms.platform

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.genusit.prms.data.remote.PropertyScopeProvider
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.SelectedProperty
import com.genusit.prms.domain.repository.PropertyScope
import com.genusit.prms.domain.repository.SessionRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

private val Context.scopeDataStore by preferencesDataStore(name = "prms_property")

/**
 * 1.17 — the selected property, kept in memory for the network layer (read on every request) and in app-private
 * DataStore so the app opens the same property after a restart. The server also remembers it (the website and the
 * next login open the same one) and Home re-reads the server's choice on every load. Cleared on sign-out, so another
 * owner signing in on this phone never inherits it. Only owner sessions are filtered.
 */
@Singleton
class PropertyScopeStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val session: SessionRepository,
) : PropertyScope, PropertyScopeProvider {
    private val kId = intPreferencesKey("building_id")
    private val kLabel = stringPreferencesKey("building_label")
    private val kToken = stringPreferencesKey("for_token")
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _selected = MutableStateFlow<SelectedProperty?>(null)
    override val selected: StateFlow<SelectedProperty?> = _selected.asStateFlow()

    init {
        io.launch {
            // restore the last choice of this sign-in; forget it when the owner signs out
            session.session.map { it?.token }.distinctUntilChanged().collect { token ->
                if (token == null) {
                    _selected.value = null
                    context.scopeDataStore.edit { it.clear() }
                } else if (_selected.value == null) {
                    val p = context.scopeDataStore.data.first()
                    if (p[kToken] == fingerprint(token)) p[kId]?.let { _selected.value = SelectedProperty(it, p[kLabel].orEmpty()) }
                }
            }
        }
    }

    /** Ties the stored choice to one sign-in without storing the token itself. */
    private fun fingerprint(token: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

    override fun currentId(): Int? = _selected.value?.id?.takeIf { it > 0 }

    override fun buildingId(): Int? = if (session.session.value?.role == Role.OWNER) currentId() else null

    override fun onUnknown(id: Int) {
        // the property was deleted (e.g. on the website): fall back to All Properties until Home reloads
        if (_selected.value?.id == id) io.launch { set(SelectedProperty(0)) }
    }

    override suspend fun set(selected: SelectedProperty?) {
        _selected.value = selected
        val token = session.token
        context.scopeDataStore.edit {
            if (selected == null || token == null) it.clear()
            else { it[kId] = selected.id; it[kLabel] = selected.label; it[kToken] = fingerprint(token) }
        }
    }
}
