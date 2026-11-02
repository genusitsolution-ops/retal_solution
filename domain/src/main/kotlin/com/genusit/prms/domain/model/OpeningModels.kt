package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDoubleOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/* ───────── 1.12: existing-tenant migration and opening balance ("Opening Balance – Migrated") ───────── */

/** Categories of a previous-balance line (same keys as the server). */
object OpeningCategory {
    const val RENT = "rent"
    val ALL = listOf("rent", "electricity", "water", "maintenance", "other")
}

/** One line of the previous outstanding: what it is for, optional month (YYYY-MM), details, amount. */
@Serializable
data class OpeningLine(
    val category: String = OpeningCategory.RENT,
    val period: String = "",
    val description: String = "",
    val amount: Double = 0.0,
    @SerialName("category_label") val categoryLabel: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
)

/** What the owner enters (create and edit). Deposit fields only when migrating / when the server allows the change. */
@Serializable
data class OpeningInput(
    @SerialName("as_of_date") val asOfDate: String,
    @SerialName("billing_start") val billingStart: String,
    @SerialName("previous_monthly_rent") val previousMonthlyRent: Double = 0.0,
    val lines: List<OpeningLine> = emptyList(),
    @SerialName("previous_paid_total") val previousPaidTotal: Double? = null,
    @SerialName("previous_payments_note") val previousPaymentsNote: String = "",
    val notes: String = "",
    @SerialName("deposit_agreed") val depositAgreed: Double? = null,
    @SerialName("deposit_collected") val depositCollected: Double? = null,
)

/** Room + opening balance of an existing tenant, sent with the new tenant (owner/tenants.php, tenant_type "existing"). */
@Serializable
data class MigrationInput(
    @SerialName("property_id") val propertyId: Int,
    @SerialName("joining_date") val joiningDate: String,
    @SerialName("charge_ids") val chargeIds: List<Int> = emptyList(),
    @SerialName("deposit_agreed") val depositAgreed: Double,
    @SerialName("deposit_collected") val depositCollected: Double,
    val opening: OpeningInput,
)

@Serializable
data class OpeningChange(val field: String = "", val label: String = "", val from: String = "", val to: String = "")

@Serializable
data class OpeningHistoryEntry(
    val action: String = "",
    @SerialName("action_label") val actionLabel: String = "",
    val reason: String? = null,
    val by: String? = null,
    val at: String? = null,
    val changes: List<OpeningChange> = emptyList(),
    @SerialName("total_after") @Serializable(with = FlexDoubleOrNull::class) val totalAfter: Double? = null,
)

/** A stored opening balance with live paid / remaining figures and its change history. */
@Serializable
data class OpeningBalanceInfo(
    val id: Int = 0,
    @SerialName("tenant_id") val tenantId: Int = 0,
    @SerialName("allocation_id") val allocationId: Int = 0,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val label: String = "",
    @SerialName("joining_date") val joiningDate: String? = null,
    @SerialName("as_of_date") val asOfDate: String = "",
    @SerialName("billing_start") val billingStart: String = "",
    @SerialName("billing_start_label") val billingStartLabel: String = "",
    @SerialName("migration_date") val migrationDate: String? = null,
    @SerialName("previous_monthly_rent") val previousMonthlyRent: Double = 0.0,
    @SerialName("rent_outstanding") val rentOutstanding: Double = 0.0,
    @SerialName("charges_outstanding") val chargesOutstanding: Double = 0.0,
    @SerialName("total_outstanding") val totalOutstanding: Double = 0.0,
    val paid: Double = 0.0,
    val remaining: Double = 0.0,
    @SerialName("deposit_agreed") val depositAgreed: Double = 0.0,
    @SerialName("deposit_collected") val depositCollected: Double = 0.0,
    @SerialName("deposit_pending") val depositPending: Double = 0.0,
    @SerialName("previous_paid_total") @Serializable(with = FlexDoubleOrNull::class) val previousPaidTotal: Double? = null,
    @SerialName("previous_payments_note") val previousPaymentsNote: String? = null,
    val notes: String? = null,
    @SerialName("invoice_id") val invoiceId: Int? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    val status: String = "none",
    @SerialName("status_label") val statusLabel: String? = null,
    val lines: List<OpeningLine> = emptyList(),
    @Serializable(with = FlexBool::class) val editable: Boolean = true,
    @SerialName("deposit_editable") @Serializable(with = FlexBool::class) val depositEditable: Boolean = false,
    val history: List<OpeningHistoryEntry> = emptyList(),
)

/** Money summary of a tenant (all tenancies with this owner). */
@Serializable
data class TenantMoneySummary(
    @SerialName("tenant_type") val tenantType: String = "new",
    @SerialName("tenant_type_label") val tenantTypeLabel: String? = null,
    @SerialName("opening_balance") val openingBalance: Double = 0.0,
    @SerialName("previous_outstanding") val previousOutstanding: Double = 0.0,
    @SerialName("current_month_charges") val currentMonthCharges: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("remaining_outstanding") val remainingOutstanding: Double = 0.0,
    val deposit: DepositSummary? = null,
    @SerialName("migration_date") val migrationDate: String? = null,
) {
    val isExisting: Boolean get() = tenantType == "existing"
}
