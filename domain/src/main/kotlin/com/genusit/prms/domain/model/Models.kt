@file:UseSerializers(FlexDouble::class, FlexInt::class, FlexBool::class)

package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDouble
import com.genusit.prms.domain.util.FlexDoubleOrNull
import com.genusit.prms.domain.util.FlexInt
import com.genusit.prms.domain.util.FlexString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

enum class Role(val api: String) {
    OWNER("owner"), EMPLOYEE("employee"), TENANT("tenant"), ADMIN("admin");
    companion object { fun from(v: String?): Role? = values().firstOrNull { it.api == v } }
}

/* ───────── auth ───────── */

@Serializable
data class LoginUser(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
)

@Serializable
data class SubscriptionBrief(
    val state: String = "none",
    @SerialName("state_label") val stateLabel: String? = null,
    val restricted: Boolean = false,
    val mode: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("days_left") val daysLeft: Int? = null,
    @SerialName("is_trial") val isTrial: Boolean = false,
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("grace_end_date") val graceEndDate: String? = null,
)

@Serializable
data class LoginResult(
    val token: String,
    @SerialName("expires_in") val expiresIn: Int = 0,
    @SerialName("user_type") val userType: String,
    val user: LoginUser = LoginUser(),
    val lang: String? = null,
    val subscription: SubscriptionBrief? = null,
    /** Changes whenever the profile photo changes; null = no photo (owners and tenants). */
    @SerialName("photo_version") val photoVersion: String? = null,
)

@Serializable
data class Me(
    val id: Int = 0,
    @SerialName("user_type") val userType: String = "",
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val lang: String? = null,
    val subscription: SubscriptionBrief? = null,
)

@Serializable
data class Notification(
    val id: Int = 0,
    val title: String = "",
    val message: String = "",
    val link: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NotificationList(val unread: Int = 0, val items: List<Notification> = emptyList())

@Serializable
data class LabelValue(val value: String = "", val label: String = "")

@Serializable
data class Meta(
    val lang: String = "en",
    @SerialName("status_labels") val statusLabels: Map<String, String> = emptyMap(),
    @SerialName("payment_modes") val paymentModes: List<LabelValue> = emptyList(),
    val currency: String = "INR",
    @SerialName("api_version") val apiVersion: String? = null,
)

/* ───────── invoices & payments ───────── */

@Serializable
data class InvoiceSummary(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("billing_month") val billingMonth: Int = 0,
    @SerialName("billing_year") @Serializable(with = FlexString::class) val billingYear: String = "",
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
    @SerialName("balance_amount") val balanceAmount: Double = 0.0,
    val status: String = "pending",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("invoice_date") val invoiceDate: String? = null,
    @SerialName("tenant_id") val tenantId: Int = 0,
    @SerialName("property_id") val propertyId: Int = 0,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
) {
    val isOpen: Boolean get() = status == "pending" || status == "partial" || status == "overdue"
}

@Serializable
data class InvoiceItem(
    val id: Int = 0,
    @SerialName("item_type") val itemType: String = "",
    val description: String = "",
    val quantity: Double = 0.0,
    @SerialName("unit_price") val unitPrice: Double = 0.0,
    val amount: Double = 0.0,
)

@Serializable
data class InvoicePayment(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("payment_date") val paymentDate: String? = null,
    val amount: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_method_label") val paymentMethodLabel: String? = null,
    @SerialName("reference_number") val referenceNumber: String? = null,
)

@Serializable
data class InvoiceDetail(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("billing_month") val billingMonth: Int = 0,
    @SerialName("billing_year") @Serializable(with = FlexString::class) val billingYear: String = "",
    @SerialName("invoice_date") val invoiceDate: String? = null,
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    @SerialName("paid_amount") val paidAmount: Double = 0.0,
    @SerialName("balance_amount") val balanceAmount: Double = 0.0,
    val status: String = "pending",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("tenant_id") val tenantId: Int = 0,
    @SerialName("property_id") val propertyId: Int = 0,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("owner_phone") val ownerPhone: String? = null,
    val notes: String? = null,
    val items: List<InvoiceItem> = emptyList(),
    val payments: List<InvoicePayment> = emptyList(),
    @SerialName("other_outstanding") val otherOutstanding: Double = 0.0,
) {
    val isOpen: Boolean get() = status == "pending" || status == "partial" || status == "overdue"
}

@Serializable
data class PaymentResult(
    @SerialName("receipt_number") val receiptNumber: String = "",
    val amount: Double = 0.0,
    @SerialName("new_status") val newStatus: String? = null,
    val balance: Double? = null,
    @SerialName("balance_after") val balanceAfter: Double? = null,
    val replayed: Boolean = false,
)

@Serializable
data class ReceiptLine(
    @SerialName("invoice_id") val invoiceId: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("period_label") val periodLabel: String = "",
    @SerialName("invoice_total") val invoiceTotal: Double = 0.0,
    val applied: Double = 0.0,
    @SerialName("balance_after") val balanceAfter: Double = 0.0,
)

@Serializable
data class Receipt(
    @SerialName("receipt_number") val receiptNumber: String = "",
    val date: String? = null,
    val amount: Double = 0.0,
    val mode: String = "",
    @SerialName("mode_label") val modeLabel: String = "",
    @SerialName("reference_number") val referenceNumber: String? = null,
    val remarks: String? = null,
    @SerialName("tenant_name") val tenantName: String = "",
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    @SerialName("property_code") val propertyCode: String = "",
    val city: String? = null,
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("collected_by") val collectedBy: String = "",
    val lines: List<ReceiptLine> = emptyList(),
    @SerialName("share_text") val shareText: String = "",
    @SerialName("whatsapp_url") val whatsappUrl: String? = null,
)

/* ───────── owner ───────── */

@Serializable
data class OwnerSummary(
    val properties: Int = 0,
    @SerialName("occupied_properties") val occupiedProperties: Int = 0,
    val tenants: Int = 0,
    @SerialName("active_employees") val activeEmployees: Int = 0,
    @SerialName("pending_invoices") val pendingInvoices: Int = 0,
    @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("this_month_billed") val thisMonthBilled: Double = 0.0,
    @SerialName("this_month_collected") val thisMonthCollected: Double = 0.0,
    /** Number of payment records behind [thisMonthCollected] and the exact dates used (same rule as the Payments screen). */
    @SerialName("this_month_payments") val thisMonthPayments: Int = 0,
    @SerialName("month_from") val monthFrom: String? = null,
    @SerialName("month_to") val monthTo: String? = null,
    @SerialName("emp_collected_month") val employeeCollectedMonth: Double = 0.0,
    @SerialName("open_queries") val openQueries: Int = 0,
)

@Serializable
data class ChartPoint(
    @SerialName("month_label") val monthLabel: String = "",
    val billed: Double = 0.0,
    val collected: Double = 0.0,
)

@Serializable
data class OwnerDashboard(
    val summary: OwnerSummary = OwnerSummary(),
    @SerialName("recent_invoices") val recentInvoices: List<InvoiceSummary> = emptyList(),
    @SerialName("chart_6months") val chart: List<ChartPoint> = emptyList(),
    val subscription: SubscriptionBrief? = null,
)

@Serializable
data class ActiveTenant(
    @SerialName("tenant_id") val tenantId: Int = 0,
    @SerialName("tenant_name") val tenantName: String = "",
    @SerialName("tenant_phone") val tenantPhone: String? = null,
    @SerialName("allocation_date") val allocationDate: String? = null,
)

@Serializable
data class Property(
    val id: Int = 0,
    @SerialName("property_code") val propertyCode: String = "",
    @SerialName("property_type_name") val typeName: String? = null,
    val address: String = "",
    val city: String = "",
    val state: String = "",
    val pincode: String = "",
    @SerialName("base_rent") val baseRent: Double = 0.0,
    @SerialName("deposit_amount") val depositAmount: Double = 0.0,
    val status: String = "available",
    @SerialName("status_label") val statusLabel: String? = null,
    /** List endpoint sends the tenant name (string); detail endpoint sends an object. */
    @SerialName("active_tenant") val activeTenantRaw: JsonElement? = null,
) {
    val activeTenantName: String?
        get() = when (val t = activeTenantRaw) {
            is JsonObject -> (t["tenant_name"] as? JsonPrimitive)?.contentOrNull
            is JsonPrimitive -> t.contentOrNull
            else -> null
        }
    val activeTenantId: Int?
        get() = ((activeTenantRaw as? JsonObject)?.get("tenant_id") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
}

@Serializable
data class NewProperty(
    @SerialName("property_code") val propertyCode: String,
    val address: String,
    val city: String,
    val state: String,
    val pincode: String,
    @SerialName("base_rent") val baseRent: Double,
    @SerialName("deposit_amount") val depositAmount: Double,
)

@Serializable
data class CreatedId(
    val id: Int = 0,
    @SerialName("ticket_id") val ticketId: String? = null,
    /** owner/tenants.php: which draft documents were attached ({"aadhaar":true}). */
    val documents: Map<String, Boolean> = emptyMap(),
)

@Serializable
data class TenantRow(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val status: String = "active",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("allocated_property") val allocatedProperty: String? = null,
    @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("has_aadhaar") val hasAadhaar: Boolean = false,
    // employee/tenants.php
    @SerialName("property_id") val propertyId: Int = 0,
    @SerialName("property_code") val propertyCode: String? = null,
    val city: String? = null,
    val invoices: List<InvoiceSummary> = emptyList(),
    @SerialName("photo_version") val photoVersion: String? = null,
)

@Serializable
data class TenantDetail(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val status: String = "active",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("allocated_property") val allocatedProperty: String? = null,
    @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("has_aadhaar") val hasAadhaar: Boolean = false,
    val invoices: List<InvoiceSummary> = emptyList(),
    // same fields the web "Tenant Details" page shows
    @SerialName("aadhar_number") val aadharNumber: String? = null,
    @SerialName("pan_number") val panNumber: String? = null,
    @SerialName("has_account") val hasAccount: Boolean = false,
    val username: String? = null,
    @SerialName("has_pan_doc") val hasPanDoc: Boolean = false,
    @SerialName("photo_version") val photoVersion: String? = null,
)

@Serializable
data class OwnerPaymentRow(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("payment_date") val paymentDate: String? = null,
    val amount: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("payment_method_label") val paymentMethodLabel: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
    @SerialName("collected_by_label") val collectedByLabel: String? = null,
)

@Serializable
data class OwnerPayments(
    val payments: List<OwnerPaymentRow> = emptyList(),
    val total: Double = 0.0,
    val from: String? = null,
    val to: String? = null,
)

@Serializable
data class Usage(
    val properties: Int = 0,
    @SerialName("billable_units") val billableUnits: Int = 0,
    @SerialName("billable_mode") val billableMode: String = "all",
    val tenants: Int = 0,
    @SerialName("max_properties") @Serializable(with = FlexIntOrNullSerializer::class) val maxProperties: Int? = null,
    @SerialName("max_tenants") @Serializable(with = FlexIntOrNullSerializer::class) val maxTenants: Int? = null,
)

@Serializable
data class Quote(
    @SerialName("package_id") val packageId: Int = 0,
    @SerialName("package_name") val packageName: String = "",
    @SerialName("cycle_days") val cycleDays: Int = 0,
    @SerialName("billable_units") val billableUnits: Int = 0,
    @SerialName("included_units") val includedUnits: Int = 0,
    @SerialName("extra_units") val extraUnits: Int = 0,
    @SerialName("base_amount") val baseAmount: Double = 0.0,
    @SerialName("extra_unit_price") val extraUnitPrice: Double = 0.0,
    @SerialName("extra_amount") val extraAmount: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_percent") val taxPercent: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
)

@Serializable
data class SubscriptionInvoice(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
    val status: String = "unpaid",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("package_name") val packageName: String? = null,
)

@Serializable
data class SubscriptionHistoryRow(
    val id: Int = 0,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("amount_paid") val amountPaid: Double = 0.0,
    val status: String = "",
    @SerialName("is_trial") val isTrial: Boolean = false,
    @SerialName("package_name") val packageName: String? = null,
)

@Serializable
data class SubscriptionStatus(
    val state: String = "none",
    @SerialName("state_label") val stateLabel: String? = null,
    @SerialName("is_trial") val isTrial: Boolean = false,
    val restricted: Boolean = false,
    @SerialName("restriction_mode") val restrictionMode: String = "read_only",
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null,
    @SerialName("days_left") val daysLeft: Int? = null,
    @SerialName("grace_end_date") val graceEndDate: String? = null,
    val usage: Usage = Usage(),
    @SerialName("renewal_quote") val renewalQuote: Quote? = null,
    @SerialName("tax_label") val taxLabel: String = "GST",
    @SerialName("payment_instructions") val paymentInstructions: String? = null,
    @SerialName("can_request_renewal") val canRequestRenewal: Boolean = false,
    val invoices: List<SubscriptionInvoice> = emptyList(),
    val history: List<SubscriptionHistoryRow> = emptyList(),
)

/* ───────── employee ───────── */

@Serializable
data class AssignedProperty(
    val id: Int = 0,
    @SerialName("property_code") val propertyCode: String = "",
    val city: String = "",
    val status: String = "",
)

@Serializable
data class EmployeeSummary(
    @SerialName("assigned_properties") val assignedProperties: Int = 0,
    @SerialName("active_tenants") val activeTenants: Int = 0,
    @SerialName("month_collected") val monthCollected: Double = 0.0,
    @SerialName("all_time_collected") val allTimeCollected: Double = 0.0,
    @SerialName("total_transactions") val totalTransactions: Int = 0,
)

@Serializable
data class CollectionRow(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    val amount: Double = 0.0,
    @SerialName("collection_date") val collectionDate: String? = null,
    @SerialName("payment_mode") val paymentMode: String? = null,
    @SerialName("payment_mode_label") val paymentModeLabel: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

@Serializable
data class EmployeeDashboard(
    val subscription: SubscriptionBrief? = null,
    val summary: EmployeeSummary = EmployeeSummary(),
    @SerialName("assigned_properties") val assignedProperties: List<AssignedProperty> = emptyList(),
    @SerialName("pending_invoices") val pendingInvoices: List<InvoiceSummary> = emptyList(),
    @SerialName("recent_collections") val recentCollections: List<CollectionRow> = emptyList(),
)

@Serializable
data class EmployeeCollections(
    val collections: List<CollectionRow> = emptyList(),
    val total: Double = 0.0,
    val count: Int = 0,
)

/* ───────── tenant ───────── */

@Serializable
data class TenantAllocation(
    val id: Int = 0,
    @SerialName("property_id") val propertyId: Int = 0,
    @SerialName("allocation_date") val allocationDate: String? = null,
    @SerialName("deposit_agreed") val depositAgreed: Double = 0.0,
    @SerialName("deposit_collected") val depositCollected: Double = 0.0,
    @SerialName("deposit_balance") val depositBalance: Double = 0.0,
    @SerialName("property_code") val propertyCode: String = "",
    val city: String? = null,
    val address: String? = null,
    @SerialName("base_rent") val baseRent: Double = 0.0,
    @SerialName("property_type") val propertyType: String? = null,
    @SerialName("owner_name") val ownerName: String? = null,
    @SerialName("owner_phone") val ownerPhone: String? = null,
)

@Serializable
data class TenantSummary(
    @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("overdue_amount") val overdueAmount: Double = 0.0,
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    @SerialName("invoice_count") val invoiceCount: Int = 0,
    @SerialName("open_queries") val openQueries: Int = 0,
)

@Serializable
data class NextDue(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("due_date") val dueDate: String? = null,
    @SerialName("balance_amount") val balanceAmount: Double = 0.0,
)

@Serializable
data class TenantSelf(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class TenantDashboard(
    val tenant: TenantSelf = TenantSelf(),
    val allocation: TenantAllocation? = null,
    val summary: TenantSummary = TenantSummary(),
    @SerialName("next_due") val nextDue: NextDue? = null,
    @SerialName("recent_invoices") val recentInvoices: List<InvoiceSummary> = emptyList(),
)

@Serializable
data class TenantPaymentRow(
    val id: Int = 0,
    @SerialName("receipt_number") val receiptNumber: String = "",
    @SerialName("payment_date") val paymentDate: String? = null,
    val amount: Double = 0.0,
    @SerialName("payment_method_label") val paymentMethodLabel: String? = null,
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("period_label") val periodLabel: String? = null,
    @SerialName("property_code") val propertyCode: String? = null,
)

@Serializable
data class TenantPayments(
    val payments: List<TenantPaymentRow> = emptyList(),
    @SerialName("total_paid") val totalPaid: Double = 0.0,
    val outstanding: Double = 0.0,
)

@Serializable
data class Query(
    val id: Int = 0,
    @SerialName("ticket_id") val ticketId: String = "",
    val subject: String = "",
    val priority: String = "low",
    @SerialName("priority_label") val priorityLabel: String? = null,
    val status: String = "open",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("tenant_name") val tenantName: String? = null,
)

@Serializable
data class NewQuery(val subject: String, val description: String, val priority: String)

@Serializable
data class Profile(
    val id: Int = 0,
    @SerialName("full_name") val fullName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val username: String? = null,
    val address: String? = null,
    @SerialName("lang_pref") val langPref: String? = null,
)

object FlexIntOrNullSerializer : kotlinx.serialization.KSerializer<Int?> {
    override val descriptor = kotlinx.serialization.descriptors.PrimitiveSerialDescriptor("FlexIntOrNull", kotlinx.serialization.descriptors.PrimitiveKind.INT)
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    override fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: Int?) {
        if (value == null) encoder.encodeNull() else encoder.encodeInt(value)
    }
    override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): Int? {
        val v = FlexDoubleOrNull.deserialize(decoder) ?: return null
        return v.toInt()
    }
}

/** Whose profile photo (owner or tenant). Separate from Aadhaar / PAN documents. */
enum class PhotoSubject(val apiValue: String) { OWNER("owner"), TENANT("tenant") }
