package com.genusit.prms.domain.usecase

import kotlin.math.abs
import kotlin.math.round

/**
 * Result for one meter charge: [rate] = the rate per unit used; [changed] = the owner ticked
 * "Change rate for this bill?" and typed a different rate (this bill only — Charge Master is not changed).
 */
data class MeterAmount(val amount: Double, val rate: Double, val changed: Boolean)

/**
 * 1.15.1 meter maths — the same rule as the server (includes/MeterRate.php): units used × rate per unit.
 * The rate is the Charge Master rate, or the new rate typed for this bill. The server works the amount out
 * again when the bill is saved, so this is only the live preview the owner sees while typing.
 */
object MeterCalc {
    private fun r2(v: Double) = round(v * 100) / 100
    private fun r3(v: Double) = round(v * 1000) / 1000

    fun calc(unitsIn: Double, rate: Double, newRate: Double? = null): MeterAmount {
        val units = r3(maxOf(0.0, unitsIn))
        if (newRate != null) {
            val used = round(newRate * 10000) / 10000
            return MeterAmount(r2(units * used), used, abs(used - rate) >= 0.00005) // typing the same rate is not a change
        }
        return MeterAmount(r2(units * rate), rate, false)
    }

    fun num(v: Double): String { val r = r3(v); return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString() }
}
