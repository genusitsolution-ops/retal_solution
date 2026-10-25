package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.LoginOutcome
import com.genusit.prms.domain.model.Me
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.Session
import com.genusit.prms.domain.repository.AuthRepository
import com.genusit.prms.domain.repository.SessionRepository
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

/**
 * Signs in and stores the session. Owners, collection agents and tenants are let in;
 * Super Admin (and any unknown role) is signed out again because those accounts use the web panel.
 */
class LoginUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionRepository,
) {
    suspend operator fun invoke(username: String, password: String, lang: String): LoginOutcome {
        val result = auth.login(username.trim(), password, lang)
        val role = Role.from(result.userType)
        if (role == null || role == Role.ADMIN) {
            try { auth.logout(result.token) } catch (e: CancellationException) { throw e } catch (_: Exception) { }
            return LoginOutcome.UseWebPanel
        }
        val s = Session(result.token, role, result.user.fullName)
        session.save(s)
        return LoginOutcome.SignedIn(s)
    }
}

/** Revokes the token on the server (best effort) and always clears the local session. */
class LogoutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionRepository,
) {
    suspend operator fun invoke() {
        try { auth.logout() } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        session.clear()
    }
}

/** Server answered 401 (token expired / account deactivated): drop the session → login screen. */
class ExpireSessionUseCase @Inject constructor(private val session: SessionRepository) {
    suspend operator fun invoke() = session.clear()
}

class RestoreSessionUseCase @Inject constructor(private val session: SessionRepository) {
    suspend operator fun invoke() = session.load()
}

class GetMeUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(): Me = auth.me()
}

/** Saves the app language on the server too, so web, receipts and notifications follow it. */
class SaveLanguageUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(lang: String) = auth.saveLanguage(lang)
}

class UpdateContactDetailsUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(role: Role, phone: String, email: String?) = auth.updateProfile(role, phone, email, null, null)
}

class ChangePasswordUseCase @Inject constructor(private val auth: AuthRepository) {
    suspend operator fun invoke(role: Role, currentPassword: String, newPassword: String) =
        auth.updateProfile(role, null, null, currentPassword, newPassword)
}
