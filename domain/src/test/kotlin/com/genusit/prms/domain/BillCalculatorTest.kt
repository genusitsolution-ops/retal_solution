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

    @Test fun chargesNotOnTheTenancyAreNotAddedByDefault() {
        // 1.15: active charges of the owner that were not assigned at room allocation are not on the bill
        val sel = BillCalculator.defaults(firstBill)
        assertTrue(sel.extraOn.isEmpty() && sel.extraEveryMonth.isEmpty())
        assertTrue(BillCalculator.problems(firstBill, sel).isEmpty())
        assertEquals(listOf(4000.0), BillCalculator.lines(firstBill, sel, "Base rent").map { it.amount })
        assertTrue(BillCalculator.extras(firstBill, sel).isEmpty())
    }

    @Test fun onlyAllocatedChargesAreBilled() {
        // allocated: Room Rent + Light Bill (₹13/unit); Water Bill (₹50) is active but not assigned to this tenant
        val ctx = InvoiceContext(baseRent = 3500.0,
            variableCharges = listOf(VariableChargeLine(21, "Light bill", 13.0, "unit", lastReading = 100.0)),
            availableCharges = listOf(AvailableCharge(9, "Water bill", "fixed", amount = 50.0)))
        val sel = BillCalculator.defaults(ctx).copy(readings = mapOf(21 to (100.0 to 120.0)))
        val lines = BillCalculator.lines(ctx, sel, "Base rent")
        assertEquals(listOf("Base rent", "Light bill"), lines.map { it.name })
        assertEquals(3760.0, BillCalculator.total(lines))                       // 3500 + 20 × 13
        assertTrue(BillCalculator.extras(ctx, sel).isEmpty())                  // nothing extra is sent to the server
        // "Change rate for this bill?" still works the same: 20 × 15
        val changed = sel.copy(rates = mapOf(21 to 15.0))
        assertEquals(3800.0, BillCalculator.total(BillCalculator.lines(ctx, changed, "Base rent")))
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
