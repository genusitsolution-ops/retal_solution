package com.genusit.prms.core.util

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
}
