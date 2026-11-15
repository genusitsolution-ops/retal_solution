package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.Slab
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.round

/** One applied slab of a "each slab at its own rate" amount (for the "10 × ₹13 + 10 × ₹14" text). */
data class SlabPart(val units: Double, val rate: Double, val amount: Double)

/**
 * Result for one meter charge. [rate] is the rate shown on the line (for slab-wise amounts the average);
 * [changed] = the owner typed a different rate for this bill.
 */
data class MeterAmount(val amount: Double, val rate: Double, val mode: String, val parts: List<SlabPart>, val changed: Boolean)

/**
 * 1.15 meter maths — the same rules as the server (includes/MeterRate.php); the server works the amount out
 * again when the bill is saved, so this is only the live preview the owner sees while typing.
 *  - flat: units × rate per unit
 *  - slab_single: the rate of the slab the units fall in, for every unit
 *  - slab_progressive: each slab's units at that slab's rate
 *  - a rate typed for this bill replaces the rule: units × that rate (Charge Master is not changed)
 */
object MeterCalc {
    const val FLAT = "flat"
    const val SINGLE = "slab_single"
    const val PROGRESSIVE = "slab_progressive"
    val MODES = listOf(FLAT, SINGLE, PROGRESSIVE)

    private fun r2(v: Double) = round(v * 100) / 100
    private fun r3(v: Double) = round(v * 1000) / 1000

    /** A slab mode without at least two slabs falls back to the flat rate (as on the server). */
    fun effectiveMode(mode: String?, slabs: List<Slab>): String =
        if (mode == null || mode !in MODES) FLAT else if (mode != FLAT && slabs.size < 2) FLAT else mode

    fun singleRate(units: Double, slabs: List<Slab>): Double =
        slabs.firstOrNull { it.upto == null || units <= it.upto }?.rate ?: slabs.lastOrNull()?.rate ?: 0.0

    fun calc(unitsIn: Double, modeIn: String?, slabs: List<Slab>, flatRate: Double, override: Double? = null): MeterAmount {
        val units = r3(maxOf(0.0, unitsIn))
        val mode = effectiveMode(modeIn, slabs)
        if (override != null) {
            val changed = !(mode == FLAT && abs(override - flatRate) < 0.00005)
            return MeterAmount(r2(units * override), override, mode, emptyList(), changed)
        }
        return when (mode) {
            SINGLE -> { val r = singleRate(units, slabs); MeterAmount(r2(units * r), r, mode, emptyList(), false) }
            PROGRESSIVE -> {
                var left = units; var from = 0.0; var sum = 0.0
                val parts = mutableListOf<SlabPart>()
                for (s in slabs) {
                    if (left <= 0) break
                    val cap = if (s.upto == null) left else min(left, r3(s.upto - from))
                    if (cap > 0) { parts += SlabPart(r3(cap), s.rate, r2(cap * s.rate)); sum += cap * s.rate; left = r3(left - cap) }
                    if (s.upto != null) from = s.upto
                }
                val amt = r2(sum)
                MeterAmount(amt, if (units > 0) round(amt / units * 10000) / 10000 else slabs.firstOrNull()?.rate ?: 0.0, mode, parts, false)
            }
            else -> MeterAmount(r2(units * flatRate), flatRate, FLAT, emptyList(), false)
        }
    }

    /**
     * Checks slab rows typed by the owner (upto = null only for the last "above that" row).
     * Returns the problem, or null when the slabs are fine.
     */
    fun slabProblem(slabs: List<Slab>): SlabProblem? {
        if (slabs.size < 2) return SlabProblem.TooFew
        if (slabs.size > 8) return SlabProblem.TooMany
        var prev = 0.0
        slabs.forEachIndexed { i, s ->
            if (s.rate < 0) return SlabProblem.BadRate
            val last = i == slabs.lastIndex
            if (last) { if (s.upto != null) return SlabProblem.LastHasUnits }
            else {
                if (s.upto == null || s.upto <= 0) return SlabProblem.MissingUnits
                if (s.upto <= prev) return SlabProblem.NotIncreasing
                prev = s.upto
            }
        }
        return null
    }

    /** "1–10", "11–30", "above 30" ranges for the slab rows (the caller supplies the "Above" word). */
    fun ranges(slabs: List<Slab>, above: (String) -> String): List<String> {
        var from = 0.0
        return slabs.map { s ->
            val label = if (s.upto == null) above(num(from)) else "${num(if (from == kotlin.math.floor(from)) from + 1 else from)}–${num(s.upto)}"
            if (s.upto != null) from = s.upto
            label
        }
    }

    fun num(v: Double): String { val r = r3(v); return if (r == kotlin.math.floor(r)) r.toLong().toString() else r.toString() }
}

enum class SlabProblem { TooFew, TooMany, BadRate, MissingUnits, LastHasUnits, NotIncreasing }
