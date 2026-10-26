package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.AppConfig
import com.genusit.prms.domain.model.RegisterInput
import com.genusit.prms.domain.model.RegisterResult
import com.genusit.prms.domain.repository.PublicRepository
import com.genusit.prms.domain.util.Validators
import javax.inject.Inject

class GetAppConfigUseCase @Inject constructor(private val repo: PublicRepository) {
    suspend operator fun invoke(): AppConfig = repo.appConfig()
}

/** Owner self-registration. Inputs are checked here first (same rules as the server, which stays the authority). */
class RegisterOwnerUseCase @Inject constructor(private val repo: PublicRepository) {
    enum class Problem { NAME, PHONE, EMAIL, USERNAME, PASSWORD, PASSWORD_MISMATCH }

    fun check(i: RegisterInput, confirmPassword: String): Set<Problem> = buildSet {
        if (i.fullName.trim().length !in 2..150) add(Problem.NAME)
        if (!Validators.phone(i.phone)) add(Problem.PHONE)
        if (!Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(i.email.trim())) add(Problem.EMAIL)
        if (!Regex("^[A-Za-z0-9._@-]{3,80}$").matches(i.username.trim())) add(Problem.USERNAME)
        if (i.password.length < 8 || i.password.none { it.isLetter() } || i.password.none { it.isDigit() }) add(Problem.PASSWORD)
        if (i.password != confirmPassword) add(Problem.PASSWORD_MISMATCH)
    }

    suspend operator fun invoke(i: RegisterInput): RegisterResult = repo.register(
        i.copy(fullName = i.fullName.trim(), email = i.email.trim(), username = i.username.trim(),
            referralCode = i.referralCode?.uppercase()?.filter { it.isLetterOrDigit() }?.ifBlank { null })
    )
}
