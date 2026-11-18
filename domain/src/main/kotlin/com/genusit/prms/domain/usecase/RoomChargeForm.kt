package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.FREQ_MONTHLY
import com.genusit.prms.domain.model.FREQ_ONE_TIME
import com.genusit.prms.domain.model.NewCharge
import com.genusit.prms.domain.model.RoomChargeChange
import com.genusit.prms.domain.model.RoomChargeInput
import com.genusit.prms.domain.model.RoomChargeOption
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.domain.util.Money

/** What the owner typed on the Add / Edit charge form (raw text, as on screen). */
data class RoomChargeFormState(
    /** Charge Master id, or [NEW] for a new charge, or 0 = nothing chosen. */
    val chargeId: Int = 0,
    val newName: String = "",
    val newIsMeter: Boolean = false,
    val newRate: String = "",
    val newUnit: String = "unit",
    val amount: String = "",
    val oneTime: Boolean = false,
    val startDate: String = "",
    val remarks: String = "",
) {
    companion object { const val NEW = -1 }
}

enum class RoomChargeProblem { CHOOSE_CHARGE, NAME, RATE, AMOUNT, DATE, DATE_BEFORE_ALLOCATION }

/**
 * Checks of the Manage Charges form before it is sent (the server checks the same again).
 * A meter (per-unit) charge is always billed every month and has no amount (the rate comes from Charge Master).
 */
object RoomChargeForm {

    fun isMeter(f: RoomChargeFormState, options: List<RoomChargeOption>, editingMeter: Boolean? = null): Boolean = when {
        editingMeter != null -> editingMeter
        f.chargeId == RoomChargeFormState.NEW -> f.newIsMeter
        else -> options.firstOrNull { it.id == f.chargeId }?.isMeter ?: false
    }

    fun problems(f: RoomChargeFormState, options: List<RoomChargeOption>, allocationDate: String?, editingMeter: Boolean? = null): Set<RoomChargeProblem> = buildSet {
        val meter = isMeter(f, options, editingMeter)
        if (editingMeter == null) {
            if (f.chargeId == 0) add(RoomChargeProblem.CHOOSE_CHARGE)
            if (f.chargeId == RoomChargeFormState.NEW) {
                if (f.newName.isBlank() || f.newName.trim().length > 150) add(RoomChargeProblem.NAME)
                if (f.newIsMeter && (Money.parse(f.newRate)?.let { it < 0 } ?: true)) add(RoomChargeProblem.RATE)
            }
        }
        if (!meter && (Money.parse(f.amount)?.let { it < 0 || it > 10_000_000 } ?: true)) add(RoomChargeProblem.AMOUNT)
        val d = Dates.parse(f.startDate)
        if (d == null) add(RoomChargeProblem.DATE)
        else Dates.parse(allocationDate)?.let { if (d.isBefore(it)) add(RoomChargeProblem.DATE_BEFORE_ALLOCATION) }
    }

    private fun freq(f: RoomChargeFormState, meter: Boolean) = if (!meter && f.oneTime) FREQ_ONE_TIME else FREQ_MONTHLY

    /** Request for a new room charge (call only when [problems] is empty). */
    fun input(f: RoomChargeFormState, options: List<RoomChargeOption>): RoomChargeInput {
        val meter = isMeter(f, options)
        val amount = if (meter) null else Money.parse(f.amount)
        val newCharge = if (f.chargeId == RoomChargeFormState.NEW) NewCharge(
            name = f.newName.trim(), chargeType = if (f.newIsMeter) "variable" else "fixed",
            fixedAmount = if (f.newIsMeter) null else amount, ratePerUnit = if (f.newIsMeter) Money.parse(f.newRate) else null,
            unitLabel = f.newUnit.trim().ifBlank { "unit" }.take(50),
        ) else null
        return RoomChargeInput(
            chargeId = if (newCharge == null) f.chargeId else null, newCharge = newCharge, amount = amount,
            frequency = freq(f, meter), startDate = f.startDate.trim(), remarks = f.remarks.trim().take(255),
        )
    }

    fun change(f: RoomChargeFormState, meter: Boolean): RoomChargeChange =
        RoomChargeChange(if (meter) null else Money.parse(f.amount), freq(f, meter), f.startDate.trim(), f.remarks.trim().take(255))
}
