package com.genusit.prms.domain.usecase

import com.genusit.prms.domain.model.*
import com.genusit.prms.domain.repository.AllocationRepository
import com.genusit.prms.domain.repository.BillingRepository
import com.genusit.prms.domain.repository.PropertyRepository
import com.genusit.prms.domain.repository.ReportRepository
import com.genusit.prms.domain.repository.SubscriptionRepository
import com.genusit.prms.domain.repository.TeamRepository
import com.genusit.prms.domain.repository.TenantAdminRepository
import javax.inject.Inject

/* ───────── dashboard, reports, referrals ───────── */

class GetOwnerDashboardUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(): OwnerDashboard = repo.ownerDashboard()
}

class GetCollectionsReportUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(from: String?, to: String?): CollectionsReport = repo.collections(from, to)
}

class GetRevenueReportUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(from: String?, to: String?): List<RevenueRow> = repo.revenue(from, to)
}

class GetPendingReportUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(): PendingReport = repo.pending()
}

class GetReferralsUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(): ReferralSummary = repo.referrals()
}

/* ───────── rooms ───────── */

class GetPropertiesUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(): List<Property> = repo.properties()
}

class GetPropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(id: Int): Property = repo.property(id)
}

class AddPropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(p: NewProperty): CreatedId = repo.add(p)
}

class UpdatePropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(id: Int, p: NewProperty, status: String?) = repo.update(id, p, status)
}

class DeletePropertyUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(id: Int) = repo.delete(id)
}

/* ───────── tenants (owner) ───────── */

class GetOwnerTenantsUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(): List<TenantRow> = repo.tenants()
}

class GetOwnerTenantUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int): TenantDetail = repo.tenant(id)
}

class GetEditableTenantUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int): TenantEditable = repo.editable(id)
}

/** Adds ([id] = 0) or updates a tenant. Returns the tenant id (the new one when adding). */
class SaveTenantUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int, input: TenantInput): Int =
        if (id == 0) repo.add(input).id else { repo.update(id, input); id }
}

class DeleteTenantUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int) = repo.delete(id)
}

class GetTenantHistoryUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int): TenantHistory = repo.history(id)
}

class GetTenantDocumentUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int, type: TenantDocType): DocumentFile = repo.document(id, type)
}

/** Uploads (or replaces) a tenant's ID document. Refuses without the tenant's consent. */
class UploadTenantDocumentUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int, type: TenantDocType, file: java.io.File, mimeType: String, consent: Boolean) {
        require(consent) { "consent required" }
        repo.uploadDocument(id, type, file, mimeType, consent)
    }
}

/* ───────── billing ───────── */

class GetOwnerInvoicesUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(status: String? = null): List<InvoiceSummary> = repo.invoices(status)
}

class GetOwnerInvoiceUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(id: Int): InvoiceDetail = repo.invoice(id)
}

/** Owner records rent received against one invoice. The idempotency key makes retries safe. */
class RecordPaymentUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(
        invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult = repo.recordPayment(invoiceId, amount, mode, date, reference, remarks, idempotencyKey)
}

class GetOwnerPaymentsUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(from: String? = null, to: String? = null): OwnerPayments = repo.payments(from, to)
}

class GetInvoiceContextUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(allocationId: Int, month: Int, year: Int): InvoiceContext = repo.invoiceContext(allocationId, month, year)
}

class GenerateInvoiceUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(allocationId: Int, month: Int, year: Int, fixedChargeIds: List<Int>, readings: List<MeterReading>): GeneratedInvoice =
        repo.generateInvoice(allocationId, month, year, fixedChargeIds, readings)
}

class CancelInvoiceUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(invoiceId: Int, reason: String) = repo.cancelInvoice(invoiceId, reason)
}

class GetChargesUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(includeInactive: Boolean = false): List<Charge> = repo.charges(includeInactive)
}

class SetChargeStatusUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(id: Int, active: Boolean) = repo.setChargeStatus(id, active)
}

class AddChargeUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(c: NewCharge): CreatedId = repo.addCharge(c)
}

/* ───────── allocations ───────── */

class GetAllocationsUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(status: String? = null): List<Allocation> = repo.allocations(status)
}

class AllocateRoomUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(a: NewAllocation): AllocationCreated = repo.allocate(a)
}

class CloseAllocationUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, notes: String): AllocationClosed = repo.close(id, notes)
}

class CollectDepositUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, amount: Double): DepositCollected = repo.collectDeposit(id, amount)
}

/* ───────── collection agents ───────── */

class GetEmployeesUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(): List<EmployeeRow> = repo.employees()
}

class GetEmployeeUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(id: Int): EmployeeDetail = repo.employee(id)
}

/** Adds ([id] = 0) or updates a collection agent. */
class SaveEmployeeUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(id: Int, input: EmployeeInput) {
        if (id == 0) repo.add(input) else repo.update(id, input)
    }
}

class SetEmployeeStatusUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(id: Int, active: Boolean) = repo.setStatus(id, active)
}

class DeleteEmployeeUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(id: Int): EmployeeRemoved = repo.delete(id)
}

class AssignPropertiesUseCase @Inject constructor(private val repo: TeamRepository) {
    suspend operator fun invoke(employeeId: Int, propertyIds: List<Int>) = repo.assignProperties(employeeId, propertyIds)
}

/* ───────── subscription ───────── */

class GetSubscriptionUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(): SubscriptionStatus = repo.status()
}

class RequestRenewalUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke() = repo.requestRenewal()
}
