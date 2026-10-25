package com.genusit.prms.domain.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Indian-style currency formatting (₹1,23,456.00) that does not depend on device locale. */
object Money {
    fun format(amount: Double, withSymbol: Boolean = true, decimals: Int = 2): String {
        val bd = BigDecimal.valueOf(amount).setScale(decimals, RoundingMode.HALF_UP)
        val negative = bd.signum() < 0
        val plain = bd.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = if (decimals > 0) "." + plain.substringAfter('.', "").padEnd(decimals, '0') else ""
        val grouped = groupIndian(intPart)
        return (if (negative) "-" else "") + (if (withSymbol) "₹" else "") + grouped + frac
    }

    /** Short form for dashboard tiles: ₹1.2L, ₹45.3K. */
    fun compact(amount: Double): String {
        val a = kotlin.math.abs(amount)
        val sign = if (amount < 0) "-" else ""
        return when {
            a >= 1_00_00_000 -> sign + "₹" + trim(a / 1_00_00_000) + "Cr"
            a >= 1_00_000 -> sign + "₹" + trim(a / 1_00_000) + "L"
            a >= 1_000 -> sign + "₹" + trim(a / 1_000) + "K"
            else -> format(amount, decimals = 0)
        }
    }

    private fun trim(v: Double): String {
        val s = BigDecimal.valueOf(v).setScale(1, RoundingMode.HALF_UP).toPlainString()
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    internal fun groupIndian(digits: String): String {
        if (digits.length <= 3) return digits
        val last3 = digits.takeLast(3)
        var rest = digits.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) {
            parts.add(0, rest.takeLast(2))
            rest = rest.dropLast(2)
        }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** Parses user input like "1,500.50" safely; null when invalid. */
    fun parse(input: String): Double? {
        val cleaned = input.replace(",", "").replace("₹", "").trim()
        if (cleaned.isEmpty()) return null
        val v = cleaned.toBigDecimalOrNull() ?: return null
        if (v.scale() > 2) return null
        return v.toDouble()
    }
}
