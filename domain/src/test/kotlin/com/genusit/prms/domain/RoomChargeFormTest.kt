package com.genusit.prms.domain

import com.genusit.prms.domain.model.InvoiceContext
import com.genusit.prms.domain.model.RoomChargeOption
import com.genusit.prms.domain.model.VariableChargeLine
import com.genusit.prms.domain.usecase.BillCalculator
import com.genusit.prms.domain.usecase.BillSelection
import com.genusit.prms.domain.usecase.RoomChargeForm
import com.genusit.prms.domain.usecase.RoomChargeFormState
import com.genusit.prms.domain.usecase.RoomChargeProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** App 1.16: the Manage Charges form and the Generate Bill meter summary (12 units, ₹13 → ₹15 for this bill). */
class RoomChargeFormTest {
    private val options = listOf(
        RoomChargeOption(2, "Water bill", "fixed", fixedAmount = 50.0),
        RoomChargeOption(4, "Gas", "variable", ratePerUnit = 9.0, unitLabel = "kg"),
    )

    @Test fun nothingChosenOrMissingDateIsRefused() {
        val p = RoomChargeForm.problems(RoomChargeFormState(startDate = ""), options, "2026-10-01")
        assertTrue(RoomChargeProblem.CHOOSE_CHARGE in p); assertTrue(RoomChargeProblem.DATE in p)
    }

    @Test fun dateBeforeAllocationIsRefused() {
        val p = RoomChargeForm.problems(RoomChargeFormState(chargeId = 2, amount = "50", startDate = "2026-09-30"), options, "2026-10-01")
        assertEquals(setOf(RoomChargeProblem.DATE_BEFORE_ALLOCATION), p)
    }

    @Test fun fixedChargeOneTimeWithOwnAmount() {
        val f = RoomChargeFormState(chargeId = 2, amount = "75", oneTime = true, startDate = "2027-01-01", remarks = " Tank cleaning ")
        assertTrue(RoomChargeForm.problems(f, options, "2026-10-01").isEmpty())
        val i = RoomChargeForm.input(f, options)
        assertEquals(2, i.chargeId); assertEquals(75.0, i.amount); assertEquals("one_time", i.frequency); assertEquals("Tank cleaning", i.remarks); assertNull(i.newCharge)
    }

    @Test fun meterChargeHasNoAmountAndIsAlwaysMonthly() {
        val f = RoomChargeFormState(chargeId = 4, amount = "", oneTime = true, startDate = "2026-11-18")
        assertTrue(RoomChargeForm.problems(f, options, "2026-10-01").isEmpty(), "no amount needed for a per-unit charge")
        val i = RoomChargeForm.input(f, options)
        assertNull(i.amount); assertEquals("monthly", i.frequency)
    }

    @Test fun newFixedChargeNeedsNameAndAmount() {
        val bad = RoomChargeForm.problems(RoomChargeFormState(chargeId = RoomChargeFormState.NEW, startDate = "2026-12-01"), options, "2026-10-01")
        assertEquals(setOf(RoomChargeProblem.NAME, RoomChargeProblem.AMOUNT), bad)
        val f = RoomChargeFormState(chargeId = RoomChargeFormState.NEW, newName = " Maintenance ", amount = "300", startDate = "2026-12-01")
        val i = RoomChargeForm.input(f, options)
        assertNull(i.chargeId); assertEquals("Maintenance", i.newCharge!!.name); assertEquals("fixed", i.newCharge!!.chargeType); assertEquals(300.0, i.newCharge!!.fixedAmount)
    }

    @Test fun newMeterChargeNeedsRate() {
        val bad = RoomChargeForm.problems(RoomChargeFormState(chargeId = RoomChargeFormState.NEW, newName = "Gas 2", newIsMeter = true, startDate = "2026-12-01"), options, null)
        assertEquals(setOf(RoomChargeProblem.RATE), bad)
        val i = RoomChargeForm.input(RoomChargeFormState(chargeId = RoomChargeFormState.NEW, newName = "Gas 2", newIsMeter = true, newRate = "9.5", newUnit = "kg", startDate = "2026-12-01"), options)
        assertEquals("variable", i.newCharge!!.chargeType); assertEquals(9.5, i.newCharge!!.ratePerUnit); assertEquals("kg", i.newCharge!!.unitLabel); assertNull(i.amount)
    }

    @Test fun editingIgnoresTheChargeChoice() {
        val f = RoomChargeFormState(amount = "80", startDate = "2026-12-01")
        assertTrue(RoomChargeForm.problems(f, options, "2026-10-01", editingMeter = false).isEmpty())
        assertEquals(80.0, RoomChargeForm.change(f, meter = false).amount)
        assertNull(RoomChargeForm.change(f, meter = true).amount)
    }

    /* the requirement's example: previous 100, current 112, Charge Master ₹13, new rate ₹15 for this bill */

    @Test fun newRateShownAndUsedAtOnce() {
        val m = BillCalculator.meterAmount(100.0, 112.0, 13.0, 15.0)!!
        assertEquals(15.0, m.rate, "the rate shown is the new rate"); assertEquals(180.0, m.amount); assertTrue(m.changed)
    }

    @Test fun untickedRestoresTheChargeMasterRate() {
        val m = BillCalculator.meterAmount(100.0, 112.0, 13.0, null)!!
        assertEquals(13.0, m.rate); assertEquals(156.0, m.amount)
    }

    @Test fun readingChangeRecalculates() {
        assertEquals(300.0, BillCalculator.meterAmount(100.0, 120.0, 13.0, 15.0)!!.amount)
        assertNull(BillCalculator.meterAmount(100.0, 99.0, 13.0, 15.0), "lower reading: no amount")
    }

    @Test fun estimatedTotalFollowsTheNewRate() {
        val ctx = InvoiceContext(baseRent = 3500.0, variableCharges = listOf(VariableChargeLine(tcId = 1, name = "Light bill", rate = 13.0, lastReading = 100.0)))
        val sel = BillSelection(readings = mapOf(1 to (100.0 to 112.0)), rates = mapOf(1 to 15.0))
        assertEquals(3680.0, BillCalculator.total(BillCalculator.lines(ctx, sel, "Rent")))
        assertEquals(3656.0, BillCalculator.total(BillCalculator.lines(ctx, sel.copy(rates = emptyMap()), "Rent")))
    }
}
