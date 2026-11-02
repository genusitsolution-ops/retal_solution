package com.genusit.prms.domain

import com.genusit.prms.domain.model.OpeningLine
import com.genusit.prms.domain.util.OpeningRules
import com.genusit.prms.domain.util.OpeningRules.Problem
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 1.12: the phone checks the opening balance like the server (OpeningBalance::normalize). */
class OpeningRulesTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val rahul = listOf(OpeningLine("rent", "2026-09", "", 4000.0), OpeningLine("rent", "2026-10", "", 4000.0), OpeningLine("electricity", "2026-10", "", 500.0))

    @Test fun exampleFromTheRequirement() {
        val t = OpeningRules.totals(rahul)
        assertEquals(8000.0, t.rent); assertEquals(500.0, t.other); assertEquals(8500.0, t.total)
        assertEquals(emptySet(), OpeningRules.check("2026-10-31", "2026-11", rahul, "2025-06-01", 8000.0, 5000.0, today))
    }

    @Test fun defaultFirstBillingMonthIsTheMonthAfterTheBalanceDate() {
        assertEquals("2026-11", OpeningRules.defaultBillingStart("2026-10-31"))
        assertEquals("2027-01", OpeningRules.defaultBillingStart("2026-12-15"))
        assertEquals(null, OpeningRules.defaultBillingStart("31/10/2026"))
    }

    @Test fun aBilledMonthCannotBeInThePreviousBalance() {
        val withNov = rahul + OpeningLine("rent", "2026-11", "", 4000.0)
        assertEquals(setOf(Problem.LINE_MONTH_BILLED), OpeningRules.check("2026-10-31", "2026-11", withNov, today = today))
    }

    @Test fun datesAndMonths() {
        assertTrue(Problem.AS_OF_FUTURE in OpeningRules.check("2026-11-03", "2026-12", rahul, today = today))
        assertTrue(Problem.AS_OF_BEFORE_JOIN in OpeningRules.check("2026-10-31", "2026-11", rahul, "2026-11-01", today = today))
        assertTrue(Problem.AS_OF_DATE in OpeningRules.check("", "2026-11", rahul, today = today))
        assertTrue(Problem.BILLING_START in OpeningRules.check("2026-10-31", "2026-13", rahul, today = today))
        assertTrue(Problem.BILLING_START_BEFORE_AS_OF in OpeningRules.check("2026-10-31", "2026-09", emptyList(), today = today))
        assertTrue(Problem.BILLING_START_TOO_LATE in OpeningRules.check("2026-10-31", "2027-01", emptyList(), today = today))
        assertTrue(Problem.LINE_MONTH in OpeningRules.check("2026-10-31", "2026-11", listOf(OpeningLine("rent", "Oct", "", 1.0)), today = today))
    }

    @Test fun amountsAndDeposit() {
        assertTrue(Problem.LINE_AMOUNT in OpeningRules.check("2026-10-31", "2026-11", listOf(OpeningLine("water", "", "meter", 0.0)), today = today))
        assertTrue(Problem.DEPOSIT in OpeningRules.check("2026-10-31", "2026-11", rahul, null, 5000.0, 8000.0, today))
        // empty rows of the form are ignored; a tenant without dues is valid (total 0)
        val empty = listOf(OpeningLine("rent", "", "", 0.0))
        assertEquals(emptySet(), OpeningRules.check("2026-10-31", "2026-11", empty, today = today))
        assertEquals(0.0, OpeningRules.totals(empty).total)
    }
}
