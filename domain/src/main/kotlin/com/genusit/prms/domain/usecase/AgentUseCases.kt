package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.EmployeeCollections
import com.genusit.prms.domain.model.EmployeeDashboard
import com.genusit.prms.domain.model.PaymentResult
import com.genusit.prms.domain.model.TenantRow
import com.genusit.prms.domain.repository.AgentRepository
import javax.inject.Inject

class GetAgentDashboardUseCase @Inject constructor(private val repo: AgentRepository) {
    suspend operator fun invoke(): EmployeeDashboard = repo.dashboard()
}

class GetAgentTenantsUseCase @Inject constructor(private val repo: AgentRepository) {
    suspend operator fun invoke(): List<TenantRow> = repo.tenants()
}

class GetAgentCollectionsUseCase @Inject constructor(private val repo: AgentRepository) {
    suspend operator fun invoke(from: String? = null, to: String? = null): EmployeeCollections = repo.collections(from, to)
}

/** Agent records a collection; duplicate-safe through [idempotencyKey]. */
class CollectRentUseCase @Inject constructor(private val repo: AgentRepository) {
    suspend operator fun invoke(
        tenantId: Int, propertyId: Int, invoiceId: Int, amount: Double, mode: String, date: String,
        reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult = repo.collect(tenantId, propertyId, invoiceId, amount, mode, date, reference, remarks, idempotencyKey)
}
