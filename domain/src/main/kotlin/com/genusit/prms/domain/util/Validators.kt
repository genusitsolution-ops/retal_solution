package com.genusit.prms.domain.util

/** Client-side checks that mirror the server rules (the server stays the authority). */
object Validators {
    enum class AmountError { EMPTY, INVALID, NOT_POSITIVE, EXCEEDS_BALANCE }

    fun amount(input: String, maxAllowed: Double? = null): AmountError? {
        if (input.isBlank()) return AmountError.EMPTY
        val v = Money.parse(input) ?: return AmountError.INVALID
        if (v <= 0.0) return AmountError.NOT_POSITIVE
        if (maxAllowed != null && v - maxAllowed > 0.005) return AmountError.EXCEEDS_BALANCE
        return null
    }

    /** Indian mobile/landline: 10–13 digits after removing spaces, dashes and leading +. */
    fun phone(input: String): Boolean {
        val d = input.replace(Regex("[\\s-]"), "").removePrefix("+")
        return d.matches(Regex("\\d{10,13}"))
    }

    fun required(input: String): Boolean = input.isNotBlank()

    /** Server rule for usernames (owner/employees API and web form). */
    fun username(input: String): Boolean = input.matches(Regex("[A-Za-z0-9._@-]{3,80}"))

    fun email(input: String): Boolean = input.matches(Regex("[^@\\s]+@[^@\\s]+\\.[^@\\s]+"))

    /** Minimum employee password length the server enforces. */
    const val EMPLOYEE_PASSWORD_MIN = 6
}

/** Field problems of the Add / Edit employee form (same rules as the server). */
object EmployeeFormRules {
    enum class Problem { NAME, PHONE, EMAIL, USERNAME, PASSWORD_REQUIRED, PASSWORD_SHORT, PASSWORD_MISMATCH }

    fun problems(isNew: Boolean, name: String, phone: String, email: String, username: String, password: String, confirm: String): Set<Problem> {
        val p = mutableSetOf<Problem>()
        if (name.isBlank()) p += Problem.NAME
        if (!Validators.phone(phone)) p += Problem.PHONE
        if (email.isNotEmpty() && !Validators.email(email)) p += Problem.EMAIL
        if (isNew && !Validators.username(username)) p += Problem.USERNAME
        if (isNew && password.isEmpty()) p += Problem.PASSWORD_REQUIRED
        else if (password.isNotEmpty() && password.length < Validators.EMPLOYEE_PASSWORD_MIN) p += Problem.PASSWORD_SHORT
        if (password.isNotEmpty() && password != confirm) p += Problem.PASSWORD_MISMATCH
        return p
    }
}
