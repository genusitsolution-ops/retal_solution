package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.ExtraCharge
import com.genusit.prms.domain.model.InvoiceContext
import com.genusit.prms.domain.model.MeterReading
import kotlin.math.round

/**
 * What the owner chose on the Generate Bill screen. Readings are the raw numbers typed (null = empty).
 *  - [fixedOn]: fixed charges of the tenancy that stay on this bill (tenant_charges ids)
 *  - [meterSkipped]: meter charges of the tenancy deliberately not billed this month
 *  - [extraOn] / [extraEveryMonth]: charges not yet on the tenancy (charge ids)
 */
data class BillSelection(
    val fixedOn: Set<Int> = emptySet(),
    val meterSkipped: Set<Int> = emptySet(),
    val readings: Map<Int, Pair<Double?, Double?>> = emptyMap(),
    val extraOn: Set<Int> = emptySet(),
    val extraEveryMonth: Set<Int> = emptySet(),
    val extraReadings: Map<Int, Pair<Double?, Double?>> = emptyMap(),
)

/** One line of the bill preview; meter lines carry units and rate for the "10 units × ₹13" text. */
data class BillLine(val name: String, val amount: Double, val units: Double? = null, val rate: Double? = null, val unitLabel: String? = null)

sealed interface BillProblem {
    val charge: String
    /** A billed meter charge has no current reading (the owner must enter it or skip the charge). */
    data class MissingReading(override val charge: String) : BillProblem
    data class LowerReading(override val charge: String) : BillProblem
}

/**
 * Bill preview maths — the same rules and line order the server applies in InvoiceService::generate (the
 * server recalculates and its total is the one saved): base rent + ticked fixed charges + units × rate
 * for every billed meter charge, rounded to paise.
 */
object BillCalculator {

    private fun r2(v: Double) = round(v * 100) / 100
    private fun r3(v: Double) = round(v * 1000) / 1000

    fun lines(ctx: InvoiceContext, sel: BillSelection, baseRentLabel: String): List<BillLine> = buildList {
        add(BillLine(baseRentLabel, r2(ctx.baseRent)))
        ctx.fixedCharges.filter { it.tcId in sel.fixedOn }.forEach { add(BillLine(it.name, r2(it.amount))) }
        ctx.availableCharges.filter { it.id in sel.extraOn && !it.isMeter }.forEach { add(BillLine(it.name, r2(it.amount))) }
        ctx.variableCharges.filter { it.tcId !in sel.meterSkipped }.forEach { v ->
            val (p, c) = sel.readings[v.tcId] ?: (v.lastReading to null)
            val prev = p ?: 0.0
            if (c != null && c >= prev) { val u = r3(c - prev); add(BillLine(v.name, r2(u * v.rate), u, v.rate, v.unitLabel)) }
        }
        ctx.availableCharges.filter { it.id in sel.extraOn && it.isMeter }.forEach { a ->
            val (p, c) = sel.extraReadings[a.id] ?: (a.lastReading to null)
            val prev = p ?: 0.0
            if (c != null && c >= prev) { val u = r3(c - prev); add(BillLine(a.name, r2(u * a.rate), u, a.rate, a.unitLabel)) }
        }
    }

    fun total(lines: List<BillLine>): Double = r2(lines.sumOf { it.amount })

    fun problems(ctx: InvoiceContext, sel: BillSelection): List<BillProblem> = buildList {
        fun check(name: String, p: Double?, c: Double?) {
            if (c == null) add(BillProblem.MissingReading(name)) else if (c < (p ?: 0.0)) add(BillProblem.LowerReading(name))
        }
        ctx.variableCharges.filter { it.tcId !in sel.meterSkipped }.forEach { v ->
            val (p, c) = sel.readings[v.tcId] ?: (v.lastReading to null); check(v.name, p, c)
        }
        ctx.availableCharges.filter { it.id in sel.extraOn && it.isMeter }.forEach { a ->
            val (p, c) = sel.extraReadings[a.id] ?: (a.lastReading to null); check(a.name, p, c)
        }
    }

    /** Request pieces for the server (fixed ids, readings, skipped meters, extra charges). */
    fun fixedIds(ctx: InvoiceContext, sel: BillSelection): List<Int> = ctx.fixedCharges.map { it.tcId }.filter { it in sel.fixedOn }

    fun readings(ctx: InvoiceContext, sel: BillSelection): List<MeterReading> =
        ctx.variableCharges.filter { it.tcId !in sel.meterSkipped }.mapNotNull { v ->
            val (p, c) = sel.readings[v.tcId] ?: (v.lastReading to null)
            if (c != null) MeterReading(v.tcId, p ?: 0.0, c) else null
        }

    fun skipped(ctx: InvoiceContext, sel: BillSelection): List<Int> = ctx.variableCharges.map { it.tcId }.filter { it in sel.meterSkipped }

    fun extras(ctx: InvoiceContext, sel: BillSelection): List<ExtraCharge> =
        ctx.availableCharges.filter { it.id in sel.extraOn }.map { a ->
            if (!a.isMeter) ExtraCharge(a.id, a.id in sel.extraEveryMonth)
            else { val (p, c) = sel.extraReadings[a.id] ?: (a.lastReading to null); ExtraCharge(a.id, a.id in sel.extraEveryMonth, p ?: 0.0, c) }
        }

    /** Starting choices: every charge on the tenancy and every enabled not-yet-added charge is on the bill. */
    fun defaults(ctx: InvoiceContext): BillSelection = BillSelection(
        fixedOn = ctx.fixedCharges.map { it.tcId }.toSet(),
        readings = ctx.variableCharges.associate { it.tcId to (it.lastReading to null) },
        extraOn = ctx.availableCharges.map { it.id }.toSet(),
        extraEveryMonth = ctx.availableCharges.map { it.id }.toSet(),
        extraReadings = ctx.availableCharges.filter { it.isMeter }.associate { it.id to (it.lastReading to null) },
    )
}
