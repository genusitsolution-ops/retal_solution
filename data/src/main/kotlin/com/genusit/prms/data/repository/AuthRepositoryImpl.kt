package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.LoginResult
import com.genusit.prms.domain.model.Me
import com.genusit.prms.domain.model.ProfileSaved
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.repository.AuthRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : AuthRepository {

    override suspend fun login(username: String, password: String, lang: String): LoginResult {
        val body = buildJsonObject { put("username", username); put("password", password); put("lang", lang) }
        return caller.get(LoginResult.serializer()) { api.login(body) }
    }

    override suspend fun logout(token: String?) {
        caller.run { api.logout(JsonObject(emptyMap()), token?.let { "Bearer $it" }) }
    }

    override suspend fun me(): Me = caller.get(Me.serializer()) { api.me() }

    override suspend fun saveLanguage(lang: String) {
        caller.run { api.updateMe(buildJsonObject { put("lang", lang) }) }
    }

    override suspend fun updateProfile(role: Role, phone: String?, email: String?, currentPassword: String?, newPassword: String?) {
        val body = buildJsonObject {
            phone?.let { put("phone", it) }
            email?.let { put("email", it) }
            if (!newPassword.isNullOrEmpty()) { put("current_password", currentPassword ?: ""); put("password", newPassword) }
        }
        when (role) {
            // owner profile (phone/email/password) lives on auth/me.php
            Role.OWNER -> caller.get(ProfileSaved.serializer()) { api.updateMe(body) }
            Role.TENANT -> caller.run { api.updateTenantProfile(body) }
            Role.EMPLOYEE -> caller.run { api.updateAgentProfile(body) }
            Role.ADMIN -> Unit
        }
    }
}
