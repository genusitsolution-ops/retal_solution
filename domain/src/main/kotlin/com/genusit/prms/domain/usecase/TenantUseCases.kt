package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.model.InvoiceSummary
import com.genusit.prms.domain.model.TenantDashboard
import com.genusit.prms.domain.model.TenantPayments
import com.genusit.prms.domain.repository.TenantPortalRepository
import javax.inject.Inject

class GetTenantDashboardUseCase @Inject constructor(private val repo: TenantPortalRepository) {
    suspend operator fun invoke(): TenantDashboard = repo.dashboard()
}

class GetTenantInvoicesUseCase @Inject constructor(private val repo: TenantPortalRepository) {
    suspend operator fun invoke(): List<InvoiceSummary> = repo.invoices()
}

class GetTenantInvoiceUseCase @Inject constructor(private val repo: TenantPortalRepository) {
    suspend operator fun invoke(id: Int): InvoiceDetail = repo.invoice(id)
}

class GetTenantPaymentsUseCase @Inject constructor(private val repo: TenantPortalRepository) {
    suspend operator fun invoke(): TenantPayments = repo.payments()
}
