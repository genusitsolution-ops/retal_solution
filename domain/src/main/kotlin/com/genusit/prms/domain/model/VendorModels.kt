@file:UseSerializers(FlexDouble::class, FlexInt::class, FlexBool::class)

package com.genusit.prms.domain.model

import com.genusit.prms.domain.util.FlexBool
import com.genusit.prms.domain.util.FlexDouble
import com.genusit.prms.domain.util.FlexDoubleOrNull
import com.genusit.prms.domain.util.FlexInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers

/**
 * 1.13 Vendor Dashboard (GET owner/vendor.php) — read-only. The vendor sees only owners they referred
 * (name, dates and statuses, never contact details), their commissions and their payouts.
 */
@Serializable
data class VendorDashboard(
    val vendor: VendorInfo = VendorInfo(),
    val totals: VendorTotals = VendorTotals(),
    @SerialName("referred_owners") val referredOwners: List<VendorReferredOwner> = emptyList(),
    val commissions: List<VendorCommission> = emptyList(),
    val payouts: List<VendorPayout> = emptyList(),
    val monthly: List<VendorMonth> = emptyList(),
    /** 1.17 independent vendor (vendor/dashboard.php): own profile and the text to share the code. */
    val profile: VendorProfile? = null,
    @SerialName("share_text") val shareText: String? = null,
)

@Serializable
data class VendorInfo(
    @SerialName("commission_percent") val commissionPercent: Double = 0.0,
    val status: String = "",
    val since: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
    val name: String? = null,
    @SerialName("vendor_type") val vendorType: String? = null,
    @SerialName("share_url") val shareUrl: String? = null,
)

@Serializable
data class VendorTotals(
    val referrals: Int = 0,
    @SerialName("paid_renewals") val paidRenewals: Int = 0,
    val total: Double = 0.0,
    val pending: Double = 0.0,
    val approved: Double = 0.0,
    val paid: Double = 0.0,
    val reversed: Double = 0.0,
    /** 1.17: successful registrations (active referred owners), waiting (no paid renewal yet), earning (commission approved / paid). */
    val registrations: Int = 0,
    @SerialName("referrals_waiting") val referralsWaiting: Int = 0,
    @SerialName("referrals_earning") val referralsEarning: Int = 0,
    @SerialName("referrals_rejected") val referralsRejected: Int = 0,
)

@Serializable
data class VendorReferredOwner(
    @SerialName("owner_name") val ownerName: String = "",
    @SerialName("referral_date") val referralDate: String? = null,
    @SerialName("trial_status") val trialStatus: String? = null,
    @SerialName("renewal_label") val renewalLabel: String? = null,
    @SerialName("renewal_amount") val renewalAmount: Double = 0.0,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("payment_label") val paymentLabel: String? = null,
    @SerialName("paid_renewals") val paidRenewals: Int = 0,
    @Serializable(with = FlexDoubleOrNull::class)
    @SerialName("commission_percent") val commissionPercent: Double? = null,
    @SerialName("commission_amount") val commissionAmount: Double = 0.0,
    @SerialName("commission_total") val commissionTotal: Double = 0.0,
    @SerialName("commission_status") val commissionStatus: String? = null,
    @SerialName("commission_label") val commissionLabel: String? = null,
    /** 1.17: waiting | earning | approved | rejected */
    @SerialName("referral_state") val referralState: String? = null,
)

@Serializable
data class VendorCommission(
    val id: Int = 0,
    @SerialName("referred_owner") val referredOwner: String = "",
    @SerialName("invoice_number") val invoiceNumber: String? = null,
    @SerialName("renewal_amount") val renewalAmount: Double = 0.0,
    @SerialName("commission_percent") val commissionPercent: Double = 0.0,
    @SerialName("commission_amount") val commissionAmount: Double = 0.0,
    @SerialName("payment_confirmed_at") val paymentConfirmedAt: String? = null,
    val status: String = "",
    @SerialName("status_label") val statusLabel: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("payout_reference") val payoutReference: String? = null,
    @SerialName("reversal_reason") val reversalReason: String? = null,
)

@Serializable
data class VendorPayout(
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("payout_reference") val payoutReference: String? = null,
    val entries: Int = 0,
    val amount: Double = 0.0,
    @SerialName("reversed_amount") val reversedAmount: Double = 0.0,
    /** 1.17: upi | bank | cash | other (null for older payouts) */
    @SerialName("payout_method") val payoutMethod: String? = null,
)

/** 1.17: independent vendor's own profile (vendor/profile.php). The bank account number comes masked. */
@Serializable
data class VendorProfile(
    val id: Int = 0,
    @SerialName("vendor_code") val vendorCode: String = "",
    @SerialName("full_name") val fullName: String = "",
    val mobile: String? = null,
    val whatsapp: String? = null,
    val email: String? = null,
    val address: String? = null,
    val city: String? = null,
    val state: String? = null,
    val pincode: String? = null,
    val status: String = "",
    @SerialName("registered_at") val registeredAt: String? = null,
    @SerialName("commission_percent") val commissionPercent: Double = 0.0,
    @SerialName("must_change_password") val mustChangePassword: Boolean = false,
    @SerialName("has_photo") val hasPhoto: Boolean = false,
    @SerialName("photo_version") val photoVersion: String? = null,
    val payment: VendorPayment = VendorPayment(),
)

@Serializable
data class VendorPayment(
    @SerialName("payout_method") val payoutMethod: String = "upi",
    @SerialName("upi_id") val upiId: String? = null,
    @SerialName("account_holder") val accountHolder: String? = null,
    @SerialName("bank_name") val bankName: String? = null,
    @SerialName("account_number_masked") val accountNumberMasked: String? = null,
    @SerialName("has_account_number") val hasAccountNumber: Boolean = false,
    val ifsc: String? = null,
    @SerialName("can_edit") val canEdit: Boolean = false,
)

/** What the vendor sends to change payment details; an empty account number keeps the saved one. */
data class VendorPaymentInput(
    val payoutMethod: String,
    val upiId: String,
    val accountHolder: String,
    val bankName: String,
    val accountNumber: String,
    val ifsc: String,
)

/** One month (by payment confirmation): paid renewals, renewal revenue, commission earned and its status split. */
@Serializable
data class VendorMonth(
    val month: String = "",
    val renewals: Int = 0,
    @SerialName("renewal_revenue") val renewalRevenue: Double = 0.0,
    val commission: Double = 0.0,
    val pending: Double = 0.0,
    val approved: Double = 0.0,
    val paid: Double = 0.0,
    val reversed: Double = 0.0,
)
