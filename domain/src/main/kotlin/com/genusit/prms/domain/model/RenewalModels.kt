package com.genusit.prms.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.genusit.prms.domain.util.FlexDoubleOrNull
import com.genusit.prms.domain.util.FlexBool

/*
 * Subscription renewal / upgrade (app 1.9). Every amount here comes from the server, which calculates it
 * from the package's current configuration; the app only shows it. Nothing in the app marks anything paid.
 */

/** A package the owner can buy, with its price worked out for the owner's rooms. */
@Serializable
data class PlanPackage(
    val id: Int = 0,
    val name: String = "",
    val description: String? = null,
    val features: List<String> = emptyList(),
    @SerialName("max_properties") val maxProperties: Int = 0,
    @SerialName("max_tenants") val maxTenants: Int = 0,
    @SerialName("monthly_price") val monthlyPrice: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int = 0,
    @SerialName("tax_percent") val taxPercent: Double = 0.0,
    @SerialName("is_featured") @Serializable(with = FlexBool::class) val isFeatured: Boolean = false,
    val quote: Quote = Quote(),
)

/**
 * What the owner sees as the plan status. [code] is the most important of [flags]:
 * expired | grace | verification_pending | rejected | payment_pending | renewed | expiring_soon | trial | active | none.
 */
@Serializable
data class PlanDisplayStatus(
    val code: String = "none",
    val label: String? = null,
    val flags: List<String> = emptyList(),
)

/** A renewal request: the owner's payment for one subscription invoice, verified by the Super Admin. */
@Serializable
data class RenewalRequest(
    val id: Int = 0,
    @SerialName("request_no") val requestNo: String? = null,
    @SerialName("invoice_id") val invoiceId: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    val amount: Double = 0.0,
    @SerialName("expected_amount") @Serializable(with = FlexDoubleOrNull::class) val expectedAmount: Double? = null,
    val utr: String = "",
    @SerialName("paid_on") val paidOn: String? = null,
    /** pending | approved | rejected */
    val status: String = "pending",
    @SerialName("status_label") val statusLabel: String? = null,
    /** Only the rejection reason is sent to owners. */
    @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    /** upi | gateway */
    val method: String = "upi",
    @SerialName("package_name") val packageName: String? = null,
    @SerialName("has_screenshot") @Serializable(with = FlexBool::class) val hasScreenshot: Boolean = false,
)

/** The invoice to pay, with its full price breakdown. */
@Serializable
data class PayInvoice(
    val id: Int = 0,
    @SerialName("invoice_number") val invoiceNumber: String = "",
    @SerialName("package_id") val packageId: Int = 0,
    @SerialName("package_name") val packageName: String = "",
    @SerialName("duration_days") val durationDays: Int = 0,
    @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null,
    @SerialName("billable_units") val billableUnits: Int = 0,
    @SerialName("included_units") val includedUnits: Int = 0,
    @SerialName("extra_units") val extraUnits: Int = 0,
    @SerialName("base_amount") val baseAmount: Double = 0.0,
    @SerialName("extra_unit_price") val extraUnitPrice: Double = 0.0,
    @SerialName("extra_amount") val extraAmount: Double = 0.0,
    @SerialName("discount_amount") val discountAmount: Double = 0.0,
    @SerialName("tax_label") val taxLabel: String = "GST",
    @SerialName("tax_percent") val taxPercent: Double = 0.0,
    @SerialName("tax_amount") val taxAmount: Double = 0.0,
    @SerialName("total_amount") val totalAmount: Double = 0.0,
)

@Serializable
data class PayMethods(
    @SerialName("manual_upi") val manualUpi: Boolean = false,
    val gateway: Boolean = false,
)

/** UPI details from the server; [uri] is a standard upi://pay link with the exact amount (QR code and UPI apps). */
@Serializable
data class UpiDetails(
    @SerialName("upi_id") val upiId: String = "",
    val payee: String = "",
    val amount: Double = 0.0,
    val currency: String = "INR",
    val reference: String = "",
    val note: String = "",
    val uri: String = "",
)

@Serializable
data class PendingRequestRef(
    val id: Int = 0,
    @SerialName("request_no") val requestNo: String? = null,
    val method: String = "upi",
    @SerialName("submitted_at") val submittedAt: String? = null,
)

@Serializable
data class LastRejection(
    @SerialName("request_no") val requestNo: String? = null,
    val reason: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class PaymentOptions(
    val invoice: PayInvoice = PayInvoice(),
    val methods: PayMethods = PayMethods(),
    val upi: UpiDetails? = null,
    @SerialName("static_qr") val staticQr: Boolean = false,
    val payee: String? = null,
    val instructions: String? = null,
    @SerialName("screenshot_required") val screenshotRequired: Boolean = true,
    @SerialName("pending_request") val pendingRequest: PendingRequestRef? = null,
    @SerialName("last_rejection") val lastRejection: LastRejection? = null,
)

/** POST owner/subscription.php action=select_package */
@Serializable
data class SelectedPackage(
    @SerialName("invoice_id") val invoiceId: Int = 0,
    val payment: PaymentOptions = PaymentOptions(),
)

/** GET owner/payment_claims.php — only the parts the app uses. */
@Serializable
data class PaymentClaimsData(
    val claims: List<RenewalRequest> = emptyList(),
    val payment: PaymentOptions? = null,
)

@Serializable
data class SubmittedRequest(val claim: RenewalRequest? = null)

/** Payment details the owner enters after paying by UPI. The screenshot file travels separately. */
data class RenewalPaymentInput(
    val invoiceId: Int,
    val utr: String,
    val paidOn: String,
    val amount: Double,
    val note: String?,
)

/** POST owner/payment_gateway.php */
@Serializable
data class GatewayStart(
    val id: Int = 0,
    @SerialName("checkout_url") val checkoutUrl: String = "",
    val amount: Double = 0.0,
)

@Serializable
data class GatewayRequestRef(
    @SerialName("request_no") val requestNo: String? = null,
    val status: String? = null,
)

/** GET owner/payment_gateway.php?id= — the server re-checks with the gateway before answering. */
@Serializable
data class GatewayStatus(
    val id: Int = 0,
    /** created | paid | failed | expired | cancelled | refunded */
    val status: String = "created",
    val amount: Double = 0.0,
    @SerialName("payment_reference") val paymentReference: String? = null,
    @SerialName("invoice_status") val invoiceStatus: String? = null,
    val request: GatewayRequestRef? = null,
)
