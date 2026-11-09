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
)

@Serializable
data class VendorInfo(
    @SerialName("commission_percent") val commissionPercent: Double = 0.0,
    val status: String = "",
    val since: String? = null,
    @SerialName("referral_code") val referralCode: String? = null,
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
