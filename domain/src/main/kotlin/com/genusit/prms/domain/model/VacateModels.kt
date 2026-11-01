package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDoubleOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ───────── 1.11: security deposit, vacate settlement, push notifications ───────── */

/** One deposit movement: in = collected, out = adjusted against dues / refunded. */
@Serializable
data class DepositTxn(
    val id: Int = 0,
    @SerialName("txn_type") val type: String = "",
    @SerialName("type_label") val typeLabel: String? = null,
    val amount: Double = 0.0,
    @SerialName("txn_date") val date: String? = null,
    val method: String? = null,
    @SerialName("method_label") val methodLabel: String? = null,
    val reference: String? = null,
    val notes: String? = null,
    val direction: String = "in",
    @SerialName("held_after") val heldAfter: Double = 0.0,
) {
    val isIn: Boolean get() = direction == "in"
}

/** Deposit figures of one allocation (all from the server's records). */
@Serializable
data class DepositSummary(
    @SerialName("allocation_id") val allocationId: Int = 0,
    val agreed: Double = 0.0,
    val collected: Double = 0.0,
    val pending: Double = 0.0,
    val adjusted: Double = 0.0,
    val refunded: Double = 0.0,
    val held: Double = 0.0,
)

/** Short settlement result shown in lists. */
@Serializable
data class SettlementBrief(
    @SerialName("settlement_id") val settlementId: Int = 0,
    @SerialName("settlement_no") val settlementNo: String? = null,
    @SerialName("vacate_date") val vacateDate: String? = null,
    @SerialName("final_amount") val finalAmount: Double = 0.0,
    @SerialName("refund_amount") val refundAmount: Double = 0.0,
    @SerialName("tenant_payable") val tenantPayable: Double = 0.0,
    @SerialName("refund_status") val refundStatus: String = "none",
    val result: String = "nil",
    @SerialName("result_label") val resultLabel: String? = null,
)

/** Tenant details: deposit of every allocation of the tenant (current first). */
@Serializable
data class AllocationDeposit(
    @SerialName("allocation_id") val allocationId: Int = 0,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    val status: String = "active",
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("deallocation_date") val deallocationDate: String? = null,
    val agreed: Double = 0.0,
    val collected: Double = 0.0,
    val pending: Double = 0.0,
    val adjusted: Double = 0.0,
    val refunded: Double = 0.0,
    val held: Double = 0.0,
    val history: List<DepositTxn> = emptyList(),
    val settlement: SettlementBrief? = null,
    /** tenant API only: full statement of a vacated room */
    val statement: SettlementStatement? = null,
    @SerialName("share_text") val shareText: String? = null,
) {
    val isActive: Boolean get() = status == "active"
}

@Serializable
data class StatementLine(
    val key: String = "",
    val label: String = "",
    val amount: Double = 0.0,
    /** info | due | credit | total */
    val kind: String = "info",
)

@Serializable
data class SettlementBill(
    @SerialName("invoice_id") val invoiceId: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("period_label") val periodLabel: String = "",
    val total: Double = 0.0,
    val paid: Double = 0.0,
    val balance: Double = 0.0,
    val status: String = "",
    val bucket: String? = null,
)

@Serializable
data class SettlementParty(val id: Int = 0, val name: String? = null, val phone: String? = null)

@Serializable
data class SettlementPlace(val id: Int = 0, val code: String? = null, val city: String? = null)

@Serializable
data class SettlementCharge(val label: String = "", val amount: Double = 0.0, val reason: String = "")

/** Preview (before confirmation) and the stored statement (after) share this shape. */
@Serializable
data class SettlementStatement(
    @SerialName("allocation_id") val allocationId: Int = 0,
    val tenant: SettlementParty = SettlementParty(),
    val property: SettlementPlace = SettlementPlace(),
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("vacate_date") val vacateDate: String? = null,
    @SerialName("rent_billed") val rentBilled: Double = 0.0,
    @SerialName("rent_paid") val rentPaid: Double = 0.0,
    @SerialName("previous_outstanding") val previousOutstanding: Double = 0.0,
    @SerialName("current_outstanding") val currentOutstanding: Double = 0.0,
    val outstanding: Double = 0.0,
    @SerialName("open_bills") val openBills: List<SettlementBill> = emptyList(),
    @SerialName("bills_to_cancel") val billsToCancel: List<SettlementBill> = emptyList(),
    @SerialName("additional_charges") val additionalCharges: Double = 0.0,
    val charges: List<SettlementCharge> = emptyList(),
    val deposit: DepositSummary = DepositSummary(),
    @SerialName("deposit_available") val depositAvailable: Double = 0.0,
    @SerialName("apply_deposit") @Serializable(with = FlexBool::class) val applyDeposit: Boolean = true,
    @SerialName("deposit_applied_rent") val depositAppliedRent: Double = 0.0,
    @SerialName("deposit_applied_charges") val depositAppliedCharges: Double = 0.0,
    @SerialName("refund_amount") val refundAmount: Double = 0.0,
    @SerialName("tenant_payable") val tenantPayable: Double = 0.0,
    @SerialName("final_amount") val finalAmount: Double = 0.0,
    val result: String = "nil",
    @SerialName("result_label") val resultLabel: String = "",
    @SerialName("other_outstanding") val otherOutstanding: Double = 0.0,
    val notes: String? = null,
    val lines: List<StatementLine> = emptyList(),
    // stored statement only
    @SerialName("settlement_no") val settlementNo: String? = null,
    @SerialName("refund_status") val refundStatus: String? = null,
    @SerialName("refund_method_label") val refundMethodLabel: String? = null,
    @SerialName("refund_reference") val refundReference: String? = null,
    @SerialName("refund_date") val refundDate: String? = null,
    @SerialName("charges_invoice_id") @Serializable(with = FlexDoubleOrNull::class) val chargesInvoiceId: Double? = null,
)

/** GET owner/allocations.php?id=N */
@Serializable
data class AllocationDetail(
    val allocation: Allocation = Allocation(),
    val deposit: DepositSummary = DepositSummary(),
    val history: List<DepositTxn> = emptyList(),
    val settlement: SettlementStatement? = null,
    @SerialName("share_text") val shareText: String? = null,
)

/** The owner's choices on the vacate screen. */
data class VacateInput(
    val vacateDate: String,
    val applyDeposit: Boolean = true,
    val charges: List<SettlementCharge> = emptyList(),
    val notes: String = "",
    val refundNow: Boolean = false,
    val refundMethod: String = "cash",
    val refundReference: String = "",
)

@Serializable
data class VacateResult(
    val settlement: SettlementStatement = SettlementStatement(),
    @SerialName("share_text") val shareText: String? = null,
)

/* ───────── push ───────── */

@Serializable
data class PushClientConfig(
    @SerialName("project_id") val projectId: String = "",
    @SerialName("application_id") val applicationId: String = "",
    @SerialName("application_ids") val applicationIds: Map<String, String> = emptyMap(),
    @SerialName("api_key") val apiKey: String = "",
    @SerialName("sender_id") val senderId: String = "",
) {
    /** The Firebase app id registered for this build's package (release / debug), else the first one. */
    fun appIdFor(packageName: String): String = applicationIds[packageName] ?: applicationIds["*"] ?: applicationId
    val isComplete: Boolean get() = projectId.isNotBlank() && apiKey.isNotBlank() && senderId.isNotBlank() && (applicationId.isNotBlank() || applicationIds.isNotEmpty())
}

@Serializable
data class NotificationCategory(
    val category: String = "",
    @Serializable(with = FlexBool::class) val enabled: Boolean = true,
    @Serializable(with = FlexBool::class) val critical: Boolean = false,
)

@Serializable
data class NotificationPrefs(
    @SerialName("push_available") @Serializable(with = FlexBool::class) val pushAvailable: Boolean = false,
    val categories: List<NotificationCategory> = emptyList(),
)

@Serializable
data class UnreadCount(val unread: Int = 0)
