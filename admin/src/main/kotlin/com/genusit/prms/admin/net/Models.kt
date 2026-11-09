@file:UseSerializers(FlexDouble::class, FlexInt::class)

package com.genusit.prms.admin.net

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/* Numbers from MySQL can arrive as "12.50" or 12.5 — both are read. */
object FlexDouble : KSerializer<Double> {
    override val descriptor = PrimitiveSerialDescriptor("FlexDouble", PrimitiveKind.DOUBLE)
    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)
    override fun deserialize(decoder: Decoder): Double {
        val e = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeDouble()
        return if (e is JsonPrimitive && e !is JsonNull) e.doubleOrNull ?: e.content.toDoubleOrNull() ?: 0.0 else 0.0
    }
}
object FlexInt : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor("FlexInt", PrimitiveKind.INT)
    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeInt(value)
    override fun deserialize(decoder: Decoder): Int {
        val e = (decoder as? JsonDecoder)?.decodeJsonElement() ?: return decoder.decodeInt()
        if (e !is JsonPrimitive || e is JsonNull) return 0
        e.booleanOrNull?.let { return if (it) 1 else 0 }
        return e.content.toDoubleOrNull()?.toInt() ?: 0
    }
}

@Serializable data class LoginResult(val token: String = "", @SerialName("user_type") val userType: String = "", val user: LoginUser = LoginUser())
@Serializable data class LoginUser(val id: Int = 0, @SerialName("full_name") val fullName: String? = null, val username: String? = null)

/* ── dashboard ── */
@Serializable data class Dashboard(
    @SerialName("pending_signups") val pendingSignups: Int = 0,
    @SerialName("pending_payments") val pendingPayments: Int = 0,
    @SerialName("signup_mode") val signupMode: String = "",
    val owners: OwnerCounts = OwnerCounts(),
    val vendors: VendorCounts = VendorCounts(),
    @SerialName("recent_signups") val recentSignups: List<RecentSignup> = emptyList(),
    @SerialName("recent_payments") val recentPayments: List<RecentPayment> = emptyList(),
)
@Serializable data class OwnerCounts(val total: Int = 0, val active: Int = 0, val inactive: Int = 0, val suspended: Int = 0)
@Serializable data class VendorCounts(
    val ready: Boolean = false, val total: Int = 0, val active: Int = 0,
    @SerialName("commission_pending") val commissionPending: Double = 0.0,
    @SerialName("commission_approved") val commissionApproved: Double = 0.0,
    @SerialName("commission_paid") val commissionPaid: Double = 0.0,
)
@Serializable data class RecentSignup(val id: Int = 0, @SerialName("owner_id") val ownerId: Int = 0, @SerialName("full_name") val fullName: String = "",
                                      val username: String = "", @SerialName("created_at") val createdAt: String? = null)
@Serializable data class RecentPayment(val id: Int = 0, @SerialName("request_no") val requestNo: String? = null, @SerialName("owner_name") val ownerName: String = "",
                                       val amount: Double = 0.0, val utr: String? = null, @SerialName("created_at") val createdAt: String? = null)

/* ── sign-ups ── */
@Serializable data class SignupList(val mode: String = "", val counts: Map<String, Int> = emptyMap(), val signups: List<Signup> = emptyList())
@Serializable data class Signup(
    val id: Int = 0, @SerialName("owner_id") val ownerId: Int = 0, @SerialName("referral_code") val referralCode: String? = null,
    val mode: String = "", val status: String = "", val notes: String? = null,
    @SerialName("created_at") val createdAt: String? = null, @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("full_name") val fullName: String = "", val email: String? = null, val phone: String? = null, val username: String = "",
    @SerialName("owner_status") val ownerStatus: String? = null, @SerialName("referrer_name") val referrerName: String? = null,
    @SerialName("reviewer_name") val reviewerName: String? = null,
)

/* ── owners ── */
@Serializable data class OwnerList(val counts: Map<String, Int> = emptyMap(), val owners: List<Owner> = emptyList())
@Serializable data class Owner(
    val id: Int = 0, @SerialName("full_name") val fullName: String = "", val username: String = "", val email: String? = null, val phone: String? = null,
    @SerialName("business_name") val businessName: String? = null, val status: String = "", @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_login") val lastLogin: String? = null, @SerialName("package_name") val packageName: String? = null,
    @SerialName("referral_code") val referralCode: String? = null, val properties: Int = 0, val tenants: Int = 0,
    @SerialName("is_vendor") val isVendor: Boolean = false, @SerialName("vendor_status") val vendorStatus: String? = null,
    @SerialName("signup_status") val signupStatus: String? = null,
)
@Serializable data class OwnerDetail(
    val id: Int = 0, @SerialName("full_name") val fullName: String = "", val username: String = "", val email: String? = null, val phone: String? = null,
    @SerialName("business_name") val businessName: String? = null, val address: String? = null, val status: String = "",
    @SerialName("created_at") val createdAt: String? = null, @SerialName("last_login") val lastLogin: String? = null,
    @SerialName("package_name") val packageName: String? = null, @SerialName("referral_code") val referralCode: String? = null,
    val properties: Int = 0, val tenants: Int = 0, val employees: Int = 0,
    @SerialName("signup_status") val signupStatus: String? = null,
    val subscription: SubscriptionInfo = SubscriptionInfo(),
    val signup: SignupInfo? = null,
    @SerialName("referred_by") val referredBy: ReferredBy? = null,
    val vendor: OwnerVendor? = null,
    @SerialName("recent_payments") val recentPayments: List<OwnerPaymentBrief> = emptyList(),
)
@Serializable data class SubscriptionInfo(
    val state: String = "", @SerialName("package_name") val packageName: String? = null, @SerialName("start_date") val startDate: String? = null,
    @SerialName("end_date") val endDate: String? = null, @SerialName("days_left") val daysLeft: Int = 0,
    @SerialName("is_trial") val isTrial: Boolean = false, val restricted: Boolean = false,
)
@Serializable data class SignupInfo(val id: Int = 0, val mode: String = "", val status: String = "", @SerialName("referral_code") val referralCode: String? = null,
                                    val notes: String? = null, @SerialName("created_at") val createdAt: String? = null)
@Serializable data class ReferredBy(@SerialName("owner_id") val ownerId: Int = 0, val name: String = "", val status: String? = null, val date: String? = null)
@Serializable data class OwnerVendor(val id: Int = 0, val status: String = "", @SerialName("commission_percent") val commissionPercent: Double = 0.0,
                                     val totals: VendorTotals = VendorTotals())
@Serializable data class OwnerPaymentBrief(val id: Int = 0, @SerialName("request_no") val requestNo: String? = null, val amount: Double = 0.0,
                                           val utr: String? = null, val status: String = "", @SerialName("created_at") val createdAt: String? = null)

/* ── vendors ── */
@Serializable data class VendorTotals(
    val referrals: Int = 0, @SerialName("paid_renewals") val paidRenewals: Int = 0, val total: Double = 0.0,
    val pending: Double = 0.0, val approved: Double = 0.0, val paid: Double = 0.0, val reversed: Double = 0.0,
)
@Serializable data class VendorList(val totals: VendorTotals = VendorTotals(), val vendors: List<Vendor> = emptyList())
@Serializable data class Vendor(
    val id: Int = 0, @SerialName("owner_id") val ownerId: Int = 0, @SerialName("full_name") val fullName: String = "", val username: String = "",
    val phone: String? = null, val email: String? = null, @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("owner_status") val ownerStatus: String? = null, val status: String = "",
    @SerialName("commission_percent") val commissionPercent: Double = 0.0, val remarks: String? = null,
    @SerialName("converted_at") val convertedAt: String? = null, val totals: VendorTotals = VendorTotals(),
)
@Serializable data class VendorDetail(
    val vendor: Vendor = Vendor(), val totals: VendorTotals = VendorTotals(),
    @SerialName("referred_owners") val referredOwners: List<ReferredOwner> = emptyList(),
    val commissions: List<Commission> = emptyList(),
    @SerialName("unpaid_months") val unpaidMonths: List<UnpaidMonth> = emptyList(),
    val payouts: List<Payout> = emptyList(),
)
@Serializable data class ReferredOwner(
    @SerialName("owner_id") val ownerId: Int = 0, @SerialName("owner_name") val ownerName: String = "", @SerialName("referral_date") val referralDate: String? = null,
    @SerialName("trial_status") val trialStatus: String? = null, @SerialName("renewal_label") val renewalLabel: String? = null,
    @SerialName("renewal_amount") val renewalAmount: Double = 0.0, @SerialName("payment_label") val paymentLabel: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null, @SerialName("paid_renewals") val paidRenewals: Int = 0,
    @SerialName("commission_amount") val commissionAmount: Double = 0.0, @SerialName("commission_total") val commissionTotal: Double = 0.0,
    @SerialName("commission_status") val commissionStatus: String? = null, @SerialName("commission_label") val commissionLabel: String? = null,
    @SerialName("referral_type") val referralType: String? = null,
)
@Serializable data class Commission(
    val id: Int = 0, @SerialName("referred_name") val referredName: String? = null, @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("renewal_amount") val renewalAmount: Double = 0.0, @SerialName("eligible_amount") val eligibleAmount: Double = 0.0,
    @SerialName("commission_percent") val commissionPercent: Double = 0.0, @SerialName("commission_amount") val commissionAmount: Double = 0.0,
    @SerialName("payment_confirmed_at") val paymentConfirmedAt: String? = null, val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null, @SerialName("approved_at") val approvedAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null, @SerialName("payout_reference") val payoutReference: String? = null,
    @SerialName("admin_remarks") val adminRemarks: String? = null, @SerialName("reversal_reason") val reversalReason: String? = null,
)
@Serializable data class UnpaidMonth(
    val month: String = "", @SerialName("pending_n") val pendingN: Int = 0, @SerialName("pending_amount") val pendingAmount: Double = 0.0,
    @SerialName("approved_n") val approvedN: Int = 0, @SerialName("approved_amount") val approvedAmount: Double = 0.0,
)
@Serializable data class Payout(
    @SerialName("paid_at") val paidAt: String? = null, @SerialName("payout_reference") val payoutReference: String? = null, val entries: Int = 0,
    val amount: Double = 0.0, @SerialName("reversed_amount") val reversedAmount: Double = 0.0, @SerialName("recorded_by") val recordedBy: String? = null,
)
@Serializable data class CommissionHistory(val history: List<HistoryRow> = emptyList())
@Serializable data class HistoryRow(
    val action: String = "", @SerialName("from_status") val fromStatus: String? = null, @SerialName("to_status") val toStatus: String? = null,
    val note: String? = null, @SerialName("actor_type") val actorType: String? = null, @SerialName("admin_name") val adminName: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

/* ── payments ── */
@Serializable data class PaymentList(val counts: Map<String, Int> = emptyMap(), val payments: List<Payment> = emptyList())
@Serializable data class Payment(
    val id: Int = 0, @SerialName("request_no") val requestNo: String = "", val status: String = "", val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null, val method: String? = null, val utr: String = "",
    @SerialName("paid_on") val paidOn: String? = null, val amount: Double = 0.0, @SerialName("expected_amount") val expectedAmount: Double = 0.0,
    @SerialName("invoice_total") val invoiceTotal: Double = 0.0, @SerialName("owner_note") val ownerNote: String? = null,
    @SerialName("admin_remarks") val adminRemarks: String? = null, @SerialName("review_note") val reviewNote: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null, @SerialName("reviewer_name") val reviewerName: String? = null,
    @SerialName("gateway_status") val gatewayStatus: String? = null, @SerialName("has_screenshot") val hasScreenshot: Boolean = false,
    val owner: PaymentOwner = PaymentOwner(), val invoice: PaymentInvoice = PaymentInvoice(), val checks: PaymentChecks = PaymentChecks(),
)
@Serializable data class PaymentOwner(val id: Int = 0, val name: String = "", val username: String? = null, val phone: String? = null,
                                      val email: String? = null, val status: String? = null)
@Serializable data class PaymentInvoice(
    val number: String? = null, val status: String? = null, @SerialName("period_start") val periodStart: String? = null,
    @SerialName("period_end") val periodEnd: String? = null, @SerialName("current_package") val currentPackage: String? = null,
    @SerialName("requested_package") val requestedPackage: String? = null, @SerialName("duration_days") val durationDays: Int = 0,
)
@Serializable data class PaymentChecks(
    @SerialName("amount_differs") val amountDiffers: Boolean = false, @SerialName("short_paid") val shortPaid: Boolean = false,
    @SerialName("same_utr_count") val sameUtrCount: Int = 0, val ocr: OcrCheck? = null,
)
@Serializable data class OcrCheck(
    val utr: String? = null, @SerialName("utr_matches") val utrMatches: Boolean? = null, val amount: Double? = null,
    @SerialName("amount_matches") val amountMatches: Boolean? = null, @SerialName("paid_on") val paidOn: String? = null,
    val status: String? = null, val app: String? = null,
)
