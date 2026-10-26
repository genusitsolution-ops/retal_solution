package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.AgentHandover
import com.genusit.prms.domain.model.CollectionDashboard
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.EmployeeCollectionDetail
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.SettlementDetail
import com.genusit.prms.domain.model.SettlementPage
import com.genusit.prms.domain.repository.AgentSettlementRepository
import com.genusit.prms.domain.repository.OwnerSettlementRepository
import com.genusit.prms.domain.util.Dates
import javax.inject.Inject

/* ───────── owner ───────── */

class GetCollectionDashboardUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(filter: CollectionFilter): CollectionDashboard = repo.dashboard(filter)
}

class GetEmployeeCollectionsUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(employeeId: Int, from: String, to: String, propertyId: Int, page: Int = 1): EmployeeCollectionDetail =
        repo.employee(employeeId, from, to, propertyId, page)
}

class GetOwnerSettlementsUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(status: String = "", employeeId: Int = 0, page: Int = 1): SettlementPage = repo.settlements(status, employeeId, page)
}

class GetOwnerSettlementUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(id: Int): SettlementDetail = repo.detail(id)
}

/** Owner confirms the amount actually received — never more than submitted (the server checks again). */
class ConfirmSettlementUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    fun isValid(received: Double?, submitted: Double): Boolean = received != null && received > 0 && received <= submitted + 0.005
    suspend operator fun invoke(id: Int, received: Double, note: String): SettlementDetail = repo.confirm(id, received, note.trim())
}

class RejectSettlementUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(id: Int, reason: String): SettlementDetail = repo.reject(id, reason.trim())
}

class RecordHandoverUseCase @Inject constructor(private val repo: OwnerSettlementRepository) {
    suspend operator fun invoke(employeeId: Int, input: HandoverInput, idempotencyKey: String): SettlementDetail =
        repo.record(employeeId, input, idempotencyKey)
}

/* ───────── employee ───────── */

class GetAgentHandoverUseCase @Inject constructor(private val repo: AgentSettlementRepository) {
    suspend operator fun invoke(): AgentHandover = repo.balance()
}

class GetAgentSettlementsUseCase @Inject constructor(private val repo: AgentSettlementRepository) {
    suspend operator fun invoke(status: String = "", page: Int = 1): SettlementPage = repo.settlements(status, page)
}

class GetAgentSettlementUseCase @Inject constructor(private val repo: AgentSettlementRepository) {
    suspend operator fun invoke(id: Int): SettlementDetail = repo.detail(id)
}

class CancelSettlementUseCase @Inject constructor(private val repo: AgentSettlementRepository) {
    suspend operator fun invoke(id: Int): SettlementDetail = repo.cancel(id)
}

/** Employee hands over money; duplicate-safe through the idempotency key. */
class SubmitHandoverUseCase @Inject constructor(private val repo: AgentSettlementRepository) {
    suspend operator fun invoke(input: HandoverInput, idempotencyKey: String): SettlementDetail = repo.submit(input, idempotencyKey)

    companion object {
        /** Client-side checks before sending (the server repeats all of them). */
        fun problems(input: HandoverInput, available: Double): Set<HandoverProblem> {
            val p = mutableSetOf<HandoverProblem>()
            if (input.collectionIds.isEmpty()) {
                val a = input.amount
                if (a == null || a <= 0) p += HandoverProblem.AMOUNT
                else if (a > available + 0.005) p += HandoverProblem.MORE_THAN_AVAILABLE
            }
            if (input.method.needsReference && input.reference.isBlank()) p += HandoverProblem.REFERENCE
            val d = Dates.parse(input.date)
            if (d == null || d.isAfter(java.time.LocalDate.now())) p += HandoverProblem.DATE
            return p
        }
    }
}

enum class HandoverProblem { AMOUNT, MORE_THAN_AVAILABLE, REFERENCE, DATE }
