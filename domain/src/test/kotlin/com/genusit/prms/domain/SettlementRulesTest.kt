package com.genusit.prms.domain

import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.HandoverMethod
import com.genusit.prms.domain.usecase.ConfirmSettlementUseCase
import com.genusit.prms.domain.usecase.HandoverProblem
import com.genusit.prms.domain.usecase.SubmitHandoverUseCase
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SettlementRulesTest {
    private val today = LocalDate.now().toString()
    private fun input(amount: Double? = 100.0, ids: List<Int> = emptyList(), m: HandoverMethod = HandoverMethod.CASH, ref: String = "", date: String = today) =
        HandoverInput(amount, ids, m, date, ref, "")

    @Test fun amountRules() {
        assertTrue(SubmitHandoverUseCase.problems(input(2000.0), 2000.0).isEmpty())
        assertEquals(setOf(HandoverProblem.MORE_THAN_AVAILABLE), SubmitHandoverUseCase.problems(input(2000.01), 2000.0))
        assertEquals(setOf(HandoverProblem.AMOUNT), SubmitHandoverUseCase.problems(input(0.0), 2000.0))
        assertEquals(setOf(HandoverProblem.AMOUNT), SubmitHandoverUseCase.problems(input(null), 2000.0))
        // selected collections: the amount comes from the collections
        assertTrue(SubmitHandoverUseCase.problems(input(null, listOf(1, 2)), 0.0).isEmpty())
    }

    @Test fun referenceAndDate() {
        assertEquals(setOf(HandoverProblem.REFERENCE), SubmitHandoverUseCase.problems(input(m = HandoverMethod.UPI), 500.0))
        assertTrue(SubmitHandoverUseCase.problems(input(m = HandoverMethod.BANK_TRANSFER, ref = "UTR1"), 500.0).isEmpty())
        assertEquals(setOf(HandoverProblem.DATE), SubmitHandoverUseCase.problems(input(date = LocalDate.now().plusDays(1).toString()), 500.0))
        assertEquals(setOf(HandoverProblem.DATE), SubmitHandoverUseCase.problems(input(date = "26/10/2026"), 500.0))
    }

    @Test fun confirmLimit() {
        val uc = ConfirmSettlementUseCase(FakeOwnerRepo)
        assertTrue(uc.isValid(3000.0, 3000.0)); assertTrue(uc.isValid(1.0, 3000.0))
        assertFalse(uc.isValid(3000.5, 3000.0)); assertFalse(uc.isValid(0.0, 3000.0)); assertFalse(uc.isValid(null, 3000.0))
    }

    @Test fun filterCount() {
        assertEquals(0, CollectionFilter(from = "2026-10-01", to = "2026-10-31").activeCount)
        assertEquals(3, CollectionFilter(employeeId = 1, mode = "upi", settlement = "pending").activeCount)
    }

    private object FakeOwnerRepo : com.genusit.prms.domain.repository.OwnerSettlementRepository {
        override suspend fun dashboard(filter: CollectionFilter) = throw NotImplementedError()
        override suspend fun employee(employeeId: Int, from: String, to: String, propertyId: Int, page: Int) = throw NotImplementedError()
        override suspend fun settlements(status: String, employeeId: Int, page: Int) = throw NotImplementedError()
        override suspend fun detail(id: Int) = throw NotImplementedError()
        override suspend fun confirm(id: Int, amountReceived: Double, note: String) = throw NotImplementedError()
        override suspend fun reject(id: Int, reason: String) = throw NotImplementedError()
        override suspend fun record(employeeId: Int, input: HandoverInput, idempotencyKey: String) = throw NotImplementedError()
    }
}
