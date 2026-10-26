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
 * Employee collections and cash handover ("settlement") to the owner — app version 1.7.
 * Matches api/v1/owner/settlements.php and api/v1/employee/settlements.php.
 *
 * A handover is a transfer of money an employee already collected from tenants; it is never a
 * new payment. Only an owner confirmation counts as money received:
 *   outstanding (with the employee) = collected − confirmed
 *   available to hand over          = collected − confirmed − awaiting confirmation
 */

/** All-time balance of one employee. */
@Serializable
data class HandoverBalance(
    val collections: Int = 0,
    val collected: Double = 0.0,
    val confirmed: Double = 0.0,
    @SerialName("awaiting_confirmation") val awaitingConfirmation: Double = 0.0,
    val outstanding: Double = 0.0,
    val available: Double = 0.0,
    @SerialName("last_collection_date") val lastCollectionDate: String? = null,
    @SerialName("last_settlement_date") val lastSettlementDate: String? = null,
)

/** One tenant collection (receipt) with its handover state. */
@Serializable
data class CollectionEntry(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("collection_date") val collectionDate: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val amount: Double = 0.0,
    @SerialName("payment_mode") val paymentMode: String = "",
    @SerialName("payment_mode_label") val paymentModeLabel: String? = null,
    @SerialName("collected_by") val collectedBy: String = "",
    @SerialName("collected_by_label") val collectedByLabel: String? = null,
    @SerialName("employee_id") val employeeId: Int = 0,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("handed_over") val handedOver: Double = 0.0,
    val awaiting: Double = 0.0,
    val outstanding: Double = 0.0,
    /** pending | partial | settled | owner */
    @SerialName("settlement_status") val settlementStatus: String = "",
    @SerialName("settlement_status_label") val settlementStatusLabel: String? = null,
)

@Serializable
data class CollectionPage(
    val items: List<CollectionEntry> = emptyList(),
    val page: Int = 1,
    @SerialName("per_page") val perPage: Int = 50,
    @SerialName("total_items") val totalItems: Int = 0,
    val pages: Int = 1,
)

@Serializable
data class CollectionSummary(
    @SerialName("total_collected") val totalCollected: Double = 0.0,
    val transactions: Int = 0,
    @SerialName("owner_collected") val ownerCollected: Double = 0.0,
    @SerialName("employee_collected") val employeeCollected: Double = 0.0,
    @SerialName("pending_dues") val pendingDues: Double = 0.0,
    @SerialName("active_employees") val activeEmployees: Int = 0,
    @SerialName("handed_over_in_period") val handedOverInPeriod: Double = 0.0,
    @SerialName("handed_over_total") val handedOverTotal: Double = 0.0,
    @SerialName("awaiting_confirmation") val awaitingConfirmation: Double = 0.0,
    @SerialName("pending_settlement") val pendingSettlement: Double = 0.0,
    @SerialName("pending_settlement_count") val pendingSettlementCount: Int = 0,
)

/** Per-employee row of the owner dashboard: period figures + all-time balance. */
@Serializable
data class EmployeeCollectionRow(
    val id: Int = 0,
    val code: String = "",
    @SerialName("full_name") val fullName: String = "",
    val phone: String? = null,
    val status: String? = null,
    @SerialName("period_collections") val periodCollections: Int = 0,
    @SerialName("period_collected") val periodCollected: Double = 0.0,
    @SerialName("period_cash") val periodCash: Double = 0.0,
    @SerialName("period_upi") val periodUpi: Double = 0.0,
    @SerialName("period_other") val periodOther: Double = 0.0,
    val collections: Int = 0,
    val collected: Double = 0.0,
    val confirmed: Double = 0.0,
    @SerialName("awaiting_confirmation") val awaitingConfirmation: Double = 0.0,
    val outstanding: Double = 0.0,
    val available: Double = 0.0,
    @SerialName("last_collection_date") val lastCollectionDate: String? = null,
    @SerialName("last_settlement_date") val lastSettlementDate: String? = null,
)

@Serializable
data class IdName(val id: Int = 0, @SerialName("full_name") val fullName: String = "")

@Serializable
data class PropertyOption(val id: Int = 0, @SerialName("property_code") val propertyCode: String = "", val city: String? = null)

@Serializable
data class CollectionFilterOptions(
    val employees: List<IdName> = emptyList(),
    val properties: List<PropertyOption> = emptyList(),
    val tenants: List<IdName> = emptyList(),
)

@Serializable
data class CollectionDashboard(
    val summary: CollectionSummary = CollectionSummary(),
    val employees: List<EmployeeCollectionRow> = emptyList(),
    val collections: CollectionPage = CollectionPage(),
    val options: CollectionFilterOptions = CollectionFilterOptions(),
)

/** Filters of the owner dashboard / collection history. Empty / 0 = all. */
data class CollectionFilter(
    val from: String = "",
    val to: String = "",
    val employeeId: Int = 0,
    val propertyId: Int = 0,
    val tenantId: Int = 0,
    val mode: String = "",
    /** owner | employee */
    val collector: String = "",
    /** pending | partial | settled | owner */
    val settlement: String = "",
    val page: Int = 1,
) {
    val activeCount: Int get() = listOf(employeeId > 0, propertyId > 0, tenantId > 0, mode.isNotEmpty(), collector.isNotEmpty(), settlement.isNotEmpty()).count { it }
}

@Serializable
data class SettlementSummary(
    val id: Int = 0,
    @SerialName("settlement_number") val settlementNumber: String = "",
    @SerialName("employee_id") val employeeId: Int = 0,
    @SerialName("emp_name") val employeeName: String? = null,
    @SerialName("amount_submitted") val amountSubmitted: Double = 0.0,
    @SerialName("amount_confirmed") @Serializable(with = FlexDoubleOrNull::class) val amountConfirmed: Double? = null,
    val method: String = "cash",
    @SerialName("method_label") val methodLabel: String? = null,
    @SerialName("reference_no") val referenceNo: String? = null,
    @SerialName("handover_date") val handoverDate: String? = null,
    val remarks: String? = null,
    /** submitted | confirmed | partially_confirmed | rejected | cancelled */
    val status: String = "submitted",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_by_type") val createdByType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("item_count") val itemCount: Int = 0,
) {
    val isAwaiting: Boolean get() = status == "submitted"
    val isConfirmed: Boolean get() = status == "confirmed" || status == "partially_confirmed"
}

@Serializable
data class SettlementItem(
    @SerialName("collection_id") val collectionId: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String? = null,
    @SerialName("collection_date") val collectionDate: String? = null,
    @SerialName("collection_amount") val collectionAmount: Double = 0.0,
    @SerialName("amount_submitted") val amountSubmitted: Double = 0.0,
    @SerialName("amount_confirmed") val amountConfirmed: Double = 0.0,
    @SerialName("payment_mode_label") val paymentModeLabel: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

@Serializable
data class AuditEntry(
    @SerialName("user_type") val userType: String? = null,
    val action: String? = null,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class SettlementDetail(
    val id: Int = 0,
    @SerialName("settlement_number") val settlementNumber: String = "",
    @SerialName("employee_id") val employeeId: Int = 0,
    @SerialName("emp_name") val employeeName: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("amount_submitted") val amountSubmitted: Double = 0.0,
    @SerialName("amount_confirmed") @Serializable(with = FlexDoubleOrNull::class) val amountConfirmed: Double? = null,
    val method: String = "cash",
    @SerialName("method_label") val methodLabel: String? = null,
    @SerialName("reference_no") val referenceNo: String? = null,
    @SerialName("handover_date") val handoverDate: String? = null,
    val remarks: String? = null,
    val status: String = "submitted",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_by_type") val createdByType: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val items: List<SettlementItem> = emptyList(),
    val audit: List<AuditEntry> = emptyList(),
) {
    val isAwaiting: Boolean get() = status == "submitted"
    val isConfirmed: Boolean get() = status == "confirmed" || status == "partially_confirmed"
}

@Serializable
data class SettlementPage(
    val items: List<SettlementSummary> = emptyList(),
    val page: Int = 1,
    @SerialName("total_items") val totalItems: Int = 0,
    val pages: Int = 1,
    @SerialName("pending_count") val pendingCount: Int = 0,
)

@Serializable
data class EmployeeBrief(val id: Int = 0, val code: String = "", @SerialName("full_name") val fullName: String = "", val phone: String? = null, val status: String? = null)

@Serializable
data class PeriodTotals(val count: Int = 0, val total: Double = 0.0, val cash: Double = 0.0, val upi: Double = 0.0, val other: Double = 0.0)

/** Owner view of one employee. */
@Serializable
data class EmployeeCollectionDetail(
    val employee: EmployeeBrief = EmployeeBrief(),
    val balance: HandoverBalance = HandoverBalance(),
    val period: PeriodTotals = PeriodTotals(),
    val collections: CollectionPage = CollectionPage(),
    val settlements: List<SettlementSummary> = emptyList(),
)

/** A collection the employee can still hand over. */
@Serializable
data class OpenCollection(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("collection_date") val collectionDate: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    @SerialName("payment_mode_label") val paymentModeLabel: String? = null,
    val amount: Double = 0.0,
    @SerialName("handed_over") val handedOver: Double = 0.0,
    val awaiting: Double = 0.0,
    val available: Double = 0.0,
)

/** Employee view: own balance and the collections still to hand over (oldest first). */
@Serializable
data class AgentHandover(
    val balance: HandoverBalance = HandoverBalance(),
    @SerialName("open_collections") val openCollections: List<OpenCollection> = emptyList(),
)

/** Handover methods the server accepts. */
enum class HandoverMethod(val apiValue: String) {
    CASH("cash"), UPI("upi"), BANK_TRANSFER("bank_transfer"), OTHER("other");
    val needsReference: Boolean get() = this != CASH
}

/** What the employee submits: an amount (oldest collections first) or chosen collections. */
data class HandoverInput(
    val amount: Double?,
    val collectionIds: List<Int>,
    val method: HandoverMethod,
    val date: String,
    val reference: String,
    val remarks: String,
)
