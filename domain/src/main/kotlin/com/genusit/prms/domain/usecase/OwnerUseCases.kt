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

class GetVendorDashboardUseCase @Inject constructor(private val repo: ReportRepository) {
    suspend operator fun invoke(): com.genusit.prms.domain.model.VendorDashboard = repo.vendorDashboard()
}

/* ───────── 1.17 independent vendor ───────── */

class GetVendorHomeUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.VendorAccountRepository) {
    suspend operator fun invoke(): com.genusit.prms.domain.model.VendorDashboard = repo.home()
}

class GetVendorProfileUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.VendorAccountRepository) {
    suspend operator fun invoke(): com.genusit.prms.domain.model.VendorProfile = repo.profile()
}

/**
 * Checks the payment details the same way the server does (UPI / IFSC / account number format; bank transfer needs
 * holder, account number and IFSC) and saves them. Returns a validation key, or null after saving.
 */
class SaveVendorPaymentUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.VendorAccountRepository) {
    suspend operator fun invoke(input: com.genusit.prms.domain.model.VendorPaymentInput, hasSavedAccount: Boolean): com.genusit.prms.domain.model.VendorProfile {
        val err = validate(input, hasSavedAccount)
        if (err != null) throw com.genusit.prms.domain.error.ApiException("VALIDATION", err, 422)
        return repo.savePayment(input)
    }

    companion object {
        const val UPI = "upi_invalid"; const val IFSC = "ifsc_invalid"; const val ACCOUNT = "account_invalid"; const val BANK_NEEDS = "bank_needs_details"
        /** null when valid, otherwise one of the keys above (the screen shows the translated text). */
        fun validate(i: com.genusit.prms.domain.model.VendorPaymentInput, hasSavedAccount: Boolean): String? {
            val upi = i.upiId.trim(); val ifsc = i.ifsc.trim().uppercase(); val acc = i.accountNumber.replace(" ", "")
            if (upi.isNotEmpty() && !Regex("^[A-Za-z0-9._-]{2,256}@[A-Za-z][A-Za-z0-9.-]{1,64}$").matches(upi)) return UPI
            if (ifsc.isNotEmpty() && !Regex("^[A-Z]{4}0[A-Z0-9]{6}$").matches(ifsc)) return IFSC
            if (acc.isNotEmpty() && !Regex("^\\d{6,20}$").matches(acc)) return ACCOUNT
            if (i.payoutMethod == "bank" && (i.accountHolder.isBlank() || ifsc.isEmpty() || (acc.isEmpty() && !hasSavedAccount))) return BANK_NEEDS
            return null
        }
    }
}

class ChangeVendorPasswordUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.VendorAccountRepository) {
    suspend operator fun invoke(current: String, new: String) = repo.changePassword(current, new)
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

/**
 * Adds ([id] = 0) or updates a tenant, attaching any draft documents named in [input].
 * [idempotencyKey] makes a retried "add" safe (the server returns the tenant created the first time).
 */
class SaveTenantUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(id: Int, input: TenantInput, idempotencyKey: String? = null): SaveTenantResult {
        val sent = listOfNotNull(input.aadhaarDraft?.let { "aadhaar" }, input.panDraft?.let { "pan" })
        if (id == 0) {
            val r = repo.add(input, idempotencyKey)
            return SaveTenantResult(r.id, sent.all { r.documents[it] == true }, r.whatsapp)
        }
        val attached = repo.update(id, input)
        return SaveTenantResult(id, sent.all { attached[it] == true })
    }
}

/** Uploads a captured document photo as a draft right away (the tenant may not exist yet). */
class UploadDraftDocumentUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(type: TenantDocType, file: java.io.File, consent: Boolean, replaceToken: String?): String {
        require(consent) { "consent required" }
        return repo.uploadDraft(type, file, "image/jpeg", consent, replaceToken)
    }
}

class DeleteDraftDocumentUseCase @Inject constructor(private val repo: TenantAdminRepository) {
    suspend operator fun invoke(type: TenantDocType, token: String) = repo.deleteDraft(type, token)
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

/** 1.10: tenants with money due, all open bills of a tenant together. */
class GetDuesByTenantUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(): List<com.genusit.prms.domain.model.TenantDues> = repo.duesByTenant()
}

/** Owner records rent received against one invoice. The idempotency key makes retries safe. */
class RecordPaymentUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(
        invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String, idempotencyKey: String,
        oldestFirst: Boolean = false,
    ): PaymentResult = repo.recordPayment(invoiceId, amount, mode, date, reference, remarks, idempotencyKey, oldestFirst)
}

class GetOwnerPaymentsUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(from: String? = null, to: String? = null): OwnerPayments = repo.payments(from, to)
}

class GetInvoiceContextUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(allocationId: Int, month: Int, year: Int): InvoiceContext = repo.invoiceContext(allocationId, month, year)
}

class GenerateInvoiceUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(
        allocationId: Int, month: Int, year: Int, fixedChargeIds: List<Int>, readings: List<MeterReading>,
        skipVarIds: List<Int> = emptyList(), extras: List<com.genusit.prms.domain.model.ExtraCharge> = emptyList(),
    ): GeneratedInvoice = repo.generateInvoice(allocationId, month, year, fixedChargeIds, readings, skipVarIds, extras)
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

/* ───────── 1.16: Manage Charges of an allocated room ───────── */

class GetRoomChargesUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.RoomChargesRepository) {
    suspend operator fun invoke(allocationId: Int) = repo.roomCharges(allocationId)
}

class SaveRoomChargeUseCase @Inject constructor(private val repo: com.genusit.prms.domain.repository.RoomChargesRepository) {
    suspend fun add(allocationId: Int, i: com.genusit.prms.domain.model.RoomChargeInput) = repo.addRoomCharge(allocationId, i)
    suspend fun update(id: Int, c: com.genusit.prms.domain.model.RoomChargeChange) = repo.updateRoomCharge(id, c)
    suspend fun stop(id: Int, endDate: String) = repo.stopRoomCharge(id, endDate)
    suspend fun restart(id: Int, startDate: String) = repo.restartRoomCharge(id, startDate)
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
    suspend operator fun invoke(id: Int, amount: Double, method: String = "cash", reference: String = ""): DepositCollected = repo.collectDeposit(id, amount, method, reference)
}

/* ───────── 1.12 opening balance (existing tenants) ───────── */

class GetOpeningBalanceUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int): OpeningBalanceInfo = repo.openingBalance(id)
}

/** Records ([id] = 0, for [allocationId]) or edits ([id] > 0, with [reason]) an opening balance. */
class SaveOpeningBalanceUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, allocationId: Int, input: OpeningInput, reason: String): OpeningBalanceInfo =
        if (id > 0) repo.updateOpening(id, input, reason) else repo.createOpening(allocationId, input)
}

/** 1.12.1: record / edit an opening balance with only the total previous outstanding. */
class SaveSimpleOpeningUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, allocationId: Int, amount: Double, reason: String): OpeningBalanceInfo = repo.saveSimpleOpening(id, allocationId, amount, reason)
}

/** 1.12.1: support person set by the Super Admin. */
class GetSupportUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(): SupportInfo = repo.support()
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
    /** Returns the employee's id (the new id when adding). */
    suspend operator fun invoke(id: Int, input: EmployeeInput): Int {
        if (id == 0) return repo.add(input).id
        repo.update(id, input)
        return id
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

class SelectPackageUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(packageId: Int) = repo.selectPackage(packageId)
}

class GetPaymentOptionsUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(invoiceId: Int) = repo.paymentOptions(invoiceId)
}

/** Sends the payment details (reference normalised like the server does) with the screenshot. */
class SubmitRenewalPaymentUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(input: RenewalPaymentInput, screenshot: java.io.File) =
        repo.submitPayment(input.copy(utr = com.genusit.prms.domain.util.RenewalRules.normalizeUtr(input.utr)), screenshot)
}

class StartGatewayPaymentUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(invoiceId: Int) = repo.startGateway(invoiceId)
}

class GetGatewayStatusUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(id: Int) = repo.gatewayStatus(id)
}

class GetPaymentScreenshotUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke(requestId: Int) = repo.screenshot(requestId)
}

class GetStaticPaymentQrUseCase @Inject constructor(private val repo: SubscriptionRepository) {
    suspend operator fun invoke() = repo.staticQr()
}

/* ───────── 1.11: allocation details, vacate with settlement ───────── */

class GetAllocationDetailUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int): com.genusit.prms.domain.model.AllocationDetail = repo.allocation(id)
}

/** Read-only statement for the owner's choices (nothing is changed on the server). */
class PreviewVacateUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, input: com.genusit.prms.domain.model.VacateInput) = repo.vacatePreview(id, input)
}

/** Vacates only when the server's final amount still equals [expectedFinal] (what the owner confirmed). */
class ConfirmVacateUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, input: com.genusit.prms.domain.model.VacateInput, expectedFinal: Double) = repo.vacateConfirm(id, input, expectedFinal)
}

class RecordRefundPaidUseCase @Inject constructor(private val repo: AllocationRepository) {
    suspend operator fun invoke(id: Int, method: String, reference: String, date: String) = repo.refundPaid(id, method, reference, date)
}

/** 1.15: property types from the Super Admin's list ([includeId] = a property's own type, even when inactive). */
class GetPropertyTypesUseCase @Inject constructor(private val repo: PropertyRepository) {
    suspend operator fun invoke(includeId: Int? = null): List<com.genusit.prms.domain.model.PropertyType> = repo.types(includeId)
}

/** Change a per-unit charge's rate per unit in Charge Master (bills already generated keep their rate). */
class UpdateChargeRatesUseCase @Inject constructor(private val repo: BillingRepository) {
    suspend operator fun invoke(id: Int, u: com.genusit.prms.domain.model.ChargeRateUpdate) = repo.updateChargeRates(id, u)
}
