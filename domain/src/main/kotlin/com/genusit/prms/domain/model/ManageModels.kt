@file:UseSerializers(FlexDouble::class, FlexInt::class, FlexBool::class)

package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDouble
import com.genusit.prms.domain.util.FlexDoubleOrNull
import com.genusit.prms.domain.util.FlexInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/*
 * Models for the owner "manage" screens added in app version 1.1:
 * tenants/properties editing, charges, allocations, invoice generation,
 * employees, complaints, reports, referral program and profile.
 * Every field matches the PHP API v1 response (see docs/03_API.md).
 */

/* ───────── tenants ───────── */

@Serializable
data class TenantEditable(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    @SerialName("aadhar_number") val aadharNumber: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    val username: String? = null,
    @SerialName("has_account") val hasAccount: Boolean = false,
    val status: String = "active",
)

/** Body for POST owner/tenants.php (create) and PUT ?id=N (update). Null fields are not sent. */
@Serializable
data class TenantInput(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    @SerialName("aadhar_number") val aadharNumber: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("has_account") val hasAccount: Boolean = false,
    val username: String? = null,
    val password: String? = null,
    val status: String? = null,
)

/* ───────── charges ───────── */

@Serializable
data class Charge(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    @SerialName("charge_type") val chargeType: String = "fixed",
    @SerialName("fixed_amount") @Serializable(with = FlexDoubleOrNull::class) val fixedAmount: Double? = null,
    @SerialName("rate_per_unit") @Serializable(with = FlexDoubleOrNull::class) val ratePerUnit: Double? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    val status: String = "active",
) {
    val isVariable: Boolean get() = chargeType == "variable"
}

@Serializable
data class NewCharge(
    val name: String,
    @SerialName("charge_type") val chargeType: String,
    @SerialName("fixed_amount") val fixedAmount: Double? = null,
    @SerialName("rate_per_unit") val ratePerUnit: Double? = null,
    @SerialName("unit_label") val unitLabel: String? = null,
    val description: String? = null,
)

/* ───────── allocations ───────── */

@Serializable
data class Allocation(
    val id: Int = 0,
    @SerialName("property_id") val propertyId: Int = 0,
    @SerialName("tenant_id") val tenantId: Int = 0,
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("deallocation_date") val deallocationDate: String? = null,
    @SerialName("deposit_agreed") val depositAgreed: Double = 0.0,
    @SerialName("deposit_collected") val depositCollected: Double = 0.0,
    @SerialName("deposit_balance") val depositBalance: Double = 0.0,
    val status: String = "active",
    @SerialName("settlement_amount") @Serializable(with = FlexDoubleOrNull::class) val settlementAmount: Double? = null,
    @SerialName("settlement_notes") val settlementNotes: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    @SerialName("base_rent") val baseRent: Double = 0.0,
) {
    val isActive: Boolean get() = status == "active"
}

@Serializable
data class NewAllocation(
    @SerialName("property_id") val propertyId: Int,
    @SerialName("tenant_id") val tenantId: Int,
    @SerialName("allocation_date") val allocationDate: String,
    @SerialName("deposit_agreed") val depositAgreed: Double,
    @SerialName("deposit_collected") val depositCollected: Double,
    @SerialName("charge_ids") val chargeIds: List<Int> = emptyList(),
)

@Serializable
data class AllocationCreated(@SerialName("allocation_id") val allocationId: Int = 0)

@Serializable
data class AllocationClosed(
    @SerialName("settlement_amount") val settlementAmount: Double = 0.0,
    @SerialName("pending_rent") val pendingRent: Double = 0.0,
)

@Serializable
data class DepositCollected(
    @SerialName("allocation_id") val allocationId: Int = 0,
    @SerialName("deposit_collected") val depositCollected: Double = 0.0,
    @SerialName("deposit_balance") val depositBalance: Double = 0.0,
)

/* ───────── invoice generation ───────── */

@Serializable
data class FixedChargeLine(
    @SerialName("tc_id") val tcId: Int = 0,
    val name: String = "",
    val amount: Double = 0.0,
)

@Serializable
data class VariableChargeLine(
    @SerialName("tc_id") val tcId: Int = 0,
    val name: String = "",
    val rate: Double = 0.0,
    @SerialName("unit_label") val unitLabel: String? = null,
    @SerialName("last_reading") @Serializable(with = FlexDoubleOrNull::class) val lastReading: Double? = null,
)

@Serializable
data class InvoiceContext(
    @SerialName("allocation_id") val allocationId: Int = 0,
    @SerialName("tenant_name") val tenantName: String = "",
    @SerialName("property_name") val propertyName: String = "",
    @SerialName("base_rent") val baseRent: Double = 0.0,
    @SerialName("billing_month") val billingMonth: Int = 0,
    @SerialName("billing_year") val billingYear: Int = 0,
    @SerialName("period_label") val periodLabel: String = "",
    @SerialName("already_exists") val alreadyExists: Boolean = false,
    @SerialName("fixed_charges") val fixedCharges: List<FixedChargeLine> = emptyList(),
    @SerialName("variable_charges") val variableCharges: List<VariableChargeLine> = emptyList(),
    @SerialName("other_outstanding") val otherOutstanding: Double = 0.0,
)

/** One meter reading for a variable charge (units = curr − prev, server re-checks). */
data class MeterReading(val tcId: Int, val previous: Double, val current: Double)

@Serializable
data class GeneratedInvoice(
    val id: Int = 0,
    val number: String = "",
    val total: Double = 0.0,
    val period: String = "",
)

/* ───────── employees ───────── */

@Serializable
data class EmployeeRow(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val role: String? = null,
    val status: String = "active",
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("assigned_props") val assignedProps: Int = 0,
    @SerialName("total_collections") val totalCollections: Int = 0,
    @SerialName("total_collected") val totalCollected: Double = 0.0,
)

@Serializable
data class EmployeeDetail(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val role: String? = null,
    val status: String = "active",
    @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("assigned_properties") val assignedProperties: List<AssignedProperty> = emptyList(),
    @SerialName("recent_collections") val recentCollections: List<CollectionRow> = emptyList(),
)

@Serializable
data class EmployeeInput(
    @SerialName("full_name") val fullName: String,
    val phone: String,
    val email: String? = null,
    val username: String? = null,
    val password: String? = null,
    val role: String = "collection_agent",
    val status: String? = null,
)

@Serializable
data class EmployeeRemoved(val deactivated: Boolean = false)

/* ───────── complaints (queries) ───────── */

@Serializable
data class QueryResponse(
    val id: Int = 0,
    @SerialName("responder_type") val responderType: String = "",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class QueryDetail(
    val id: Int = 0,
    @SerialName("ticket_id") val ticketId: String = "",
    val subject: String = "",
    val description: String = "",
    val priority: String = "low",
    @SerialName("priority_label") val priorityLabel: String? = null,
    val status: String = "open",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    val responses: List<QueryResponse> = emptyList(),
) {
    val isClosed: Boolean get() = status == "closed"
}

/* ───────── reports ───────── */

@Serializable
data class CollectionReportRow(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    val amount: Double = 0.0,
    @SerialName("collection_date") val collectionDate: String? = null,
    @SerialName("payment_mode_label") val paymentModeLabel: String? = null,
    @SerialName("collected_by") val collectedBy: String? = null,
    @SerialName("emp_name") val empName: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

@Serializable
data class EmployeeTotal(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val txns: Int = 0,
    val total: Double = 0.0,
)

@Serializable
data class ReportFilters(val from: String? = null, val to: String? = null)

@Serializable
data class CollectionsReport(
    val collections: List<CollectionReportRow> = emptyList(),
    @SerialName("employee_summary") val employeeSummary: List<EmployeeTotal> = emptyList(),
    @SerialName("owner_collected") val ownerCollected: Double = 0.0,
    @SerialName("total_collected") val totalCollected: Double = 0.0,
    @SerialName("pending_total") val pendingTotal: Double = 0.0,
    val filters: ReportFilters = ReportFilters(),
)

@Serializable
data class RevenueRow(
    val ym: String = "",
    val label: String = "",
    val billed: Double = 0.0,
    val collected: Double = 0.0,
    val outstanding: Double = 0.0,
)

@Serializable
data class PendingReport(
    val invoices: List<InvoiceSummary> = emptyList(),
    @SerialName("total_pending") val totalPending: Double = 0.0,
)

/* ───────── referral program ───────── */

@Serializable
data class ReferralStats(
    val total: Int = 0,
    val pending: Int = 0,
    val rewarded: Int = 0,
    @SerialName("days_earned") val daysEarned: Int = 0,
)

@Serializable
data class ReferralItem(
    val id: Int = 0,
    val status: String = "pending",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("status_reason") val statusReason: String? = null,
    @SerialName("reward_days") val rewardDays: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("rewarded_at") val rewardedAt: String? = null,
    @SerialName("referred_name") val referredName: String = "",
)

@Serializable
data class ReferralSummary(
    val enabled: Boolean = false,
    val code: String? = null,
    @SerialName("share_url") val shareUrl: String? = null,
    @SerialName("share_text") val shareText: String? = null,
    @SerialName("reward_days") val rewardDays: Int = 0,
    @SerialName("condition_label") val conditionLabel: String? = null,
    val stats: ReferralStats = ReferralStats(),
    val items: List<ReferralItem> = emptyList(),
)

/* ───────── profile ───────── */

@Serializable
data class ProfileSaved(val phone: String? = null, val email: String? = null)

/* ───────── tenant full history (GET owner/tenant_history.php?id=N) — same records as the web page ───────── */

@Serializable
data class TenantHistory(
    val tenant: HistoryTenant = HistoryTenant(),
    val summary: HistorySummary = HistorySummary(),
    val deposits: HistoryDeposits = HistoryDeposits(),
    val allocations: List<HistoryAllocation> = emptyList(),
    val payments: List<HistoryPayment> = emptyList(),
    val invoices: List<HistoryInvoice> = emptyList(),
)

@Serializable
data class HistoryTenant(val id: Int = 0, @SerialName("full_name") val fullName: String = "", val phone: String? = null, val status: String = "active")

@Serializable
data class HistorySummary(
    @SerialName("total_billed") val totalBilled: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("total_outstanding") val totalOutstanding: Double = 0.0,
    @SerialName("invoice_count") val invoiceCount: Int = 0,
    @SerialName("paid_count") val paidCount: Int = 0,
    @SerialName("unpaid_count") val unpaidCount: Int = 0,
    @SerialName("late_payments") val latePayments: Int = 0,
)

@Serializable
data class HistoryDeposits(
    @SerialName("total_agreed") val totalAgreed: Double = 0.0,
    @SerialName("total_collected") val totalCollected: Double = 0.0,
    @SerialName("total_refunded") val totalRefunded: Double = 0.0,
    @SerialName("total_pending") val totalPending: Double = 0.0,
)

@Serializable
data class HistoryAllocation(
    val id: Int = 0,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("deallocation_date") val deallocationDate: String? = null,
    @SerialName("deposit_agreed") val depositAgreed: Double = 0.0,
    @SerialName("deposit_collected") val depositCollected: Double = 0.0,
    @SerialName("settlement_amount") val settlementAmount: Double = 0.0,
    @SerialName("settlement_notes") val settlementNotes: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    @SerialName("base_rent") val baseRent: Double = 0.0,
    @SerialName("tenancy_days") val tenancyDays: Int = 0,
)

@Serializable
data class HistoryPayment(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("payment_date") val paymentDate: String? = null,
    val amount: Double = 0.0,
    @SerialName("payment_method_label") val paymentMethodLabel: String? = null,
    @SerialName("reference_number") val referenceNumber: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

@Serializable
data class HistoryInvoice(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
    @SerialName("balance_amount") val balanceAmount: Double = 0.0,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

/** Tenant ID document kinds stored on the server (same columns as the web form). */
enum class TenantDocType(val apiValue: String) { AADHAAR("aadhaar"), PAN("pan") }

/** A document file downloaded through the authenticated API. */
class DocumentFile(val bytes: ByteArray, val contentType: String) {
    val isPdf: Boolean get() = contentType.startsWith("application/pdf")
}
