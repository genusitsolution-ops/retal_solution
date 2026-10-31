package com.genusit.prms.domain

import com.genusit.prms.domain.model.AvailableCharge
import com.genusit.prms.domain.model.FixedChargeLine
import com.genusit.prms.domain.model.InvoiceContext
import com.genusit.prms.domain.model.VariableChargeLine
import com.genusit.prms.domain.usecase.BillCalculator
import com.genusit.prms.domain.usecase.BillProblem
import com.genusit.prms.domain.util.EmployeeFormRules
import com.genusit.prms.domain.util.EmployeeFormRules.Problem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Same figures as the server test (test_v18_charges.py): rent 4000, water 100/month, light 13/unit. */
class BillCalculatorTest {
    private val firstBill = InvoiceContext(baseRent = 4000.0, availableCharges = listOf(
        AvailableCharge(1, "light bill", "variable", rate = 13.0, unitLabel = "unit"),
        AvailableCharge(2, "water", "fixed", amount = 100.0),
    ))

    @Test fun chargesNotYetOnTheTenancyAreIncludedByDefault() {
        var sel = BillCalculator.defaults(firstBill)
        // the meter charge needs a reading — never silently left out
        assertEquals(listOf<BillProblem>(BillProblem.MissingReading("light bill")), BillCalculator.problems(firstBill, sel))
        sel = sel.copy(extraReadings = mapOf(1 to (0.0 to 10.0)))
        val lines = BillCalculator.lines(firstBill, sel, "Base rent")
        assertEquals(listOf(4000.0, 100.0, 130.0), lines.map { it.amount })
        assertEquals(4230.0, BillCalculator.total(lines))
        assertTrue(BillCalculator.problems(firstBill, sel).isEmpty())
        val extras = BillCalculator.extras(firstBill, sel)
        assertTrue(extras.all { it.everyMonth }); assertEquals(10.0, extras.first { it.chargeId == 1 }.current)
    }

    @Test fun attachedChargesNextMonth() {
        val ctx = InvoiceContext(baseRent = 4000.0, fixedCharges = listOf(FixedChargeLine(11, "water", 100.0)),
            variableCharges = listOf(VariableChargeLine(12, "light bill", 13.0, "unit", lastReading = 10.0)))
        var sel = BillCalculator.defaults(ctx).copy(readings = mapOf(12 to (10.0 to 25.0)))
        assertEquals(4295.0, BillCalculator.total(BillCalculator.lines(ctx, sel, "Base rent")))
        sel = sel.copy(readings = mapOf(12 to (25.0 to 20.0)))
        assertEquals(listOf<BillProblem>(BillProblem.LowerReading("light bill")), BillCalculator.problems(ctx, sel))
        // skipped on purpose: no problem, not billed, sent as skip
        sel = BillCalculator.defaults(ctx).copy(meterSkipped = setOf(12))
        assertTrue(BillCalculator.problems(ctx, sel).isEmpty())
        assertEquals(4100.0, BillCalculator.total(BillCalculator.lines(ctx, sel, "Base rent")))
        assertEquals(listOf(12), BillCalculator.skipped(ctx, sel)); assertTrue(BillCalculator.readings(ctx, sel).isEmpty())
        // fixed charge unticked
        sel = sel.copy(fixedOn = emptySet())
        assertEquals(4000.0, BillCalculator.total(BillCalculator.lines(ctx, sel, "Base rent")))
    }

    @Test fun allocationSearch() {
        val a = com.genusit.prms.domain.model.Allocation(tenantName = "Karunashankar Gupta", tenantPhone = "+91 79723 30661", propertyCode = "R1", propertyName = "Room 1", city = "pune")
        assertTrue(a.matches("") && a.matches("gupta") && a.matches("r1") && a.matches("Room") && a.matches("PUNE"))
        assertTrue(a.matches("97233") && a.matches("7972 330"))
        assertTrue(!a.matches("sharma") && !a.matches("12") && !a.matches("R2"))
    }

    @Test fun employeeFormRules() {
        assertEquals(setOf(Problem.PASSWORD_REQUIRED), EmployeeFormRules.problems(true, "emp1", "7972330661", "emp@gmail.com", "emp1", "", ""))
        assertEquals(setOf(Problem.PASSWORD_SHORT), EmployeeFormRules.problems(true, "emp1", "7972330661", "", "emp1", "12345", "12345"))
        assertEquals(setOf(Problem.PASSWORD_MISMATCH), EmployeeFormRules.problems(true, "emp1", "7972330661", "", "emp1", "secret1", "secret2"))
        assertTrue(EmployeeFormRules.problems(true, "emp1", "7972330661", "emp@gmail.com", "emp1", "secret1", "secret1").isEmpty())
        assertEquals(setOf(Problem.NAME, Problem.PHONE, Problem.EMAIL, Problem.USERNAME), EmployeeFormRules.problems(true, " ", "123", "bad@", "a b", "secret1", "secret1"))
        // editing: password optional, username not changed
        assertTrue(EmployeeFormRules.problems(false, "emp1", "7972330661", "", "", "", "").isEmpty())
    }
}
