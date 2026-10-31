package com.genusit.prms.domain.repository

import com.genusit.prms.domain.model.*
import kotlinx.coroutines.flow.StateFlow

/*
 * Repository contracts (Clean Architecture "ports"). The data module implements them on top of
 * the PRMS REST API v1; the presentation layer only ever sees these interfaces through use cases.
 * Every call throws [com.genusit.prms.domain.error.ApiException] on failure.
 */

/** The signed-in session, persisted on the device. */
interface SessionRepository {
    val session: StateFlow<Session?>
    /** False until the stored session has been read at app start. */
    val loaded: StateFlow<Boolean>
    /** Current bearer token, readable synchronously by the network layer. */
    val token: String?
    suspend fun load()
    suspend fun save(session: Session)
    suspend fun clear()
}

interface AuthRepository {
    suspend fun login(username: String, password: String, lang: String): LoginResult
    /** Revokes [token] on the server (the current session token when null). */
    suspend fun logout(token: String? = null)
    suspend fun me(): Me
    suspend fun saveLanguage(lang: String)
    /** Profile update for the signed-in role. A password change needs [currentPassword]. */
    suspend fun updateProfile(role: Role, phone: String?, email: String?, currentPassword: String?, newPassword: String?)
}

interface CommonRepository {
    suspend fun meta(): Meta
    suspend fun notifications(): NotificationList
    suspend fun markAllNotificationsRead()
    suspend fun receipt(receiptNumber: String): Receipt
}

/** Owner: rooms. */
interface PropertyRepository {
    suspend fun properties(): List<Property>
    suspend fun property(id: Int): Property
    suspend fun add(p: NewProperty): CreatedId
    /** [status] may be "available" or "maintenance"; null keeps the current status. */
    suspend fun update(id: Int, p: NewProperty, status: String?)
    /** Refused by the server with HAS_HISTORY when the room has allocations or invoices. */
    suspend fun delete(id: Int)
}

/** Owner: tenant records. */
interface TenantAdminRepository {
    suspend fun tenants(): List<TenantRow>
    suspend fun tenant(id: Int): TenantDetail
    suspend fun editable(id: Int): TenantEditable
    /** [idempotencyKey]: a retried create returns the first tenant instead of adding a second one. */
    suspend fun add(t: TenantInput, idempotencyKey: String? = null): CreatedId
    /** Returns which draft documents were attached (empty when none were sent). */
    suspend fun update(id: Int, t: TenantInput): Map<String, Boolean>
    /** Only for tenants without any rent history (server answers HAS_HISTORY otherwise). */
    suspend fun delete(id: Int)
    /** Full history, the same records as the web "Full History" page. */
    suspend fun history(id: Int): TenantHistory
    /** Downloads the tenant's Aadhaar / PAN document (owner of the tenant only). */
    suspend fun document(id: Int, type: TenantDocType): DocumentFile
    /** Uploads or replaces the document; [consent] must be true (the server refuses otherwise). */
    suspend fun uploadDocument(id: Int, type: TenantDocType, file: java.io.File, mimeType: String, consent: Boolean)
    /** Uploads a document before the tenant is saved; returns the draft token. [replaceToken] is deleted on the server. */
    suspend fun uploadDraft(type: TenantDocType, file: java.io.File, mimeType: String, consent: Boolean, replaceToken: String?): String
    suspend fun deleteDraft(type: TenantDocType, token: String)
}

/** Owner: invoices, payments and charges. */
interface BillingRepository {
    suspend fun invoices(status: String? = null): List<InvoiceSummary>
    suspend fun invoice(id: Int): InvoiceDetail
    /** Never pays more than the balance (server enforces). */
    suspend fun recordPayment(invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String, idempotencyKey: String): PaymentResult
    suspend fun payments(from: String? = null, to: String? = null): OwnerPayments
    suspend fun invoiceContext(allocationId: Int, month: Int, year: Int): InvoiceContext
    /** Totals are computed on the server from the owner's charges. */
    /**
     * [skipVarIds]: meter charges on the tenancy deliberately not billed this month (the server refuses a
     * meter charge that has neither a reading nor a skip). [extras]: charges not yet on the tenancy.
     */
    suspend fun generateInvoice(
        allocationId: Int, month: Int, year: Int, fixedChargeIds: List<Int>, readings: List<MeterReading>,
        skipVarIds: List<Int> = emptyList(), extras: List<com.genusit.prms.domain.model.ExtraCharge> = emptyList(),
    ): GeneratedInvoice
    /** Only unpaid invoices can be cancelled (server answers NOT_CANCELLABLE otherwise). */
    suspend fun cancelInvoice(invoiceId: Int, reason: String)
    suspend fun charges(includeInactive: Boolean = false): List<Charge>
    suspend fun setChargeStatus(id: Int, active: Boolean)
    suspend fun addCharge(c: NewCharge): CreatedId
    /** Deactivates the charge (history stays on old invoices). */
    suspend fun deactivateCharge(id: Int)
}

/** Owner: room allocations (move-in / move-out). */
interface AllocationRepository {
    suspend fun allocations(status: String? = null): List<Allocation>
    suspend fun allocate(a: NewAllocation): AllocationCreated
    /** The server computes the settlement (deposit collected − pending rent). */
    suspend fun close(id: Int, notes: String): AllocationClosed
    suspend fun collectDeposit(id: Int, amount: Double): DepositCollected
}

/** Owner: collection agents. */
interface TeamRepository {
    suspend fun employees(): List<EmployeeRow>
    suspend fun employee(id: Int): EmployeeDetail
    suspend fun add(e: EmployeeInput): CreatedId
    suspend fun update(id: Int, e: EmployeeInput)
    suspend fun setStatus(id: Int, active: Boolean)
    /** Deletes the agent, or deactivates when collections exist ([EmployeeRemoved.deactivated]). */
    suspend fun delete(id: Int): EmployeeRemoved
    suspend fun assignProperties(employeeId: Int, propertyIds: List<Int>)
}

/** Complaints / support tickets for owner and tenant. */
interface QueryRepository {
    suspend fun ownerQueries(): List<Query>
    suspend fun ownerQuery(id: Int): QueryDetail
    suspend fun ownerReply(id: Int, message: String, status: String?)
    suspend fun ownerSetStatus(id: Int, status: String)
    suspend fun tenantQueries(): List<Query>
    suspend fun tenantQuery(id: Int): QueryDetail
    /** Refused by the server when the ticket is closed. */
    suspend fun tenantReply(id: Int, message: String)
    suspend fun raise(q: NewQuery): CreatedId
}

/** Owner: dashboard, reports and referral programme. */
interface ReportRepository {
    suspend fun ownerDashboard(): OwnerDashboard
    suspend fun collections(from: String?, to: String?): CollectionsReport
    suspend fun revenue(from: String?, to: String?): List<RevenueRow>
    suspend fun pending(): PendingReport
    suspend fun referrals(): ReferralSummary
}

/** Owner: plan status and renewal. */
interface SubscriptionRepository {
    suspend fun status(): SubscriptionStatus
    suspend fun requestRenewal()
}

/** Collection agent (employee) features. */
interface AgentRepository {
    suspend fun dashboard(): EmployeeDashboard
    suspend fun tenants(): List<TenantRow>
    suspend fun collections(from: String? = null, to: String? = null): EmployeeCollections
    /**
     * With [invoiceId] = 0 the server applies the money to the oldest unpaid invoices first.
     * The same [idempotencyKey] on a retry returns the first receipt instead of recording twice.
     */
    suspend fun collect(
        tenantId: Int, propertyId: Int, invoiceId: Int, amount: Double, mode: String, date: String,
        reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult
    suspend fun profile(): Profile
}

/** Tenant self-service features. */
interface TenantPortalRepository {
    suspend fun dashboard(): TenantDashboard
    suspend fun invoices(): List<InvoiceSummary>
    suspend fun invoice(id: Int): InvoiceDetail
    suspend fun payments(): TenantPayments
    suspend fun profile(): Profile
}

/** Public endpoints used before sign-in (login screen). */
interface PublicRepository {
    suspend fun appConfig(): AppConfig
    suspend fun register(input: RegisterInput): RegisterResult
    /** Downloads a branding image (same server only). */
    suspend fun image(url: String): ByteArray
}
