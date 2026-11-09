package com.genusit.prms.ui.owner

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.VendorCommission
import com.genusit.prms.domain.model.VendorDashboard
import com.genusit.prms.domain.model.VendorMonth
import com.genusit.prms.domain.model.VendorPayout
import com.genusit.prms.domain.model.VendorReferredOwner
import com.genusit.prms.domain.usecase.GetVendorDashboardUseCase
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/* ═════════════════════════ 1.13 Vendor Dashboard (read-only) ═════════════════════════ */

@HiltViewModel
class VendorViewModel @Inject constructor(
    getVendor: GetVendorDashboardUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val dashboard = loadable { getVendor() }
}

/** Owner "More" menu: shows the Vendor Dashboard row only to active vendors (flag from owner/dashboard.php). */
@HiltViewModel
class VendorFlagViewModel @Inject constructor(
    getDashboard: com.genusit.prms.domain.usecase.GetOwnerDashboardUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val dashboard = loadable { getDashboard() }
}

private enum class VendorTab { SUMMARY, OWNERS, COMMISSIONS, PAYOUTS }

/** "November 2026" for "2026-11". */
@Composable
private fun monthLabel(ym: String): String {
    val months = stringArrayResource(R.array.months)
    val m = ym.substringAfter('-', "").toIntOrNull() ?: return ym
    return "${months.getOrElse(m - 1) { m.toString() }} ${ym.substringBefore('-')}"
}

private fun pct(p: Double?): String = p?.let { (if (it % 1.0 == 0.0) it.toLong().toString() else "%.2f".format(it).trimEnd('0').trimEnd('.')) + "%" } ?: "—"

@Composable
fun VendorDashboardScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<VendorViewModel>().dashboard
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(VendorTab.SUMMARY) }
    ScreenScaffold(stringResource(R.string.vendor_dashboard), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { VendorHeader(d) }
                item { VendorTotalsGrid(d) }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            VendorTab.SUMMARY to R.string.vendor_tab_monthly, VendorTab.OWNERS to R.string.vendor_tab_owners,
                            VendorTab.COMMISSIONS to R.string.vendor_tab_commissions, VendorTab.PAYOUTS to R.string.vendor_tab_payouts,
                        ).forEach { (t, label) -> FilterChip(selected = tab == t, onClick = { tab = t }, label = { Text(stringResource(label)) }) }
                    }
                }
                when (tab) {
                    VendorTab.SUMMARY -> {
                        item { SectionHeader(stringResource(R.string.vendor_monthly_summary)) }
                        if (d.monthly.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_commission), Icons.Filled.CalendarMonth) }
                        items(d.monthly, key = { "m" + it.month }) { MonthCard(it) }
                    }
                    VendorTab.OWNERS -> {
                        item { SectionHeader(stringResource(R.string.vendor_referred_owners)) }
                        if (d.referredOwners.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_referrals, d.vendor.referralCode ?: "—"), Icons.Filled.Groups) }
                        items(d.referredOwners) { OwnerCard(it) }
                    }
                    VendorTab.COMMISSIONS -> {
                        item { SectionHeader(stringResource(R.string.vendor_commissions)) }
                        if (d.commissions.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_commission), Icons.Filled.Payments) }
                        items(d.commissions, key = { "c" + it.id }) { CommissionCard(it) }
                    }
                    VendorTab.PAYOUTS -> {
                        item { SectionHeader(stringResource(R.string.vendor_payout_history)) }
                        if (d.payouts.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_payout), Icons.Filled.History) }
                        items(d.payouts) { PayoutCard(it) }
                    }
                }
                item {
                    Text(stringResource(R.string.vendor_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun VendorHeader(d: VendorDashboard) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storefront, null, tint = Palette.Brand)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vendor_commission_rate, pct(d.vendor.commissionPercent)), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.vendor_rate_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.your_referral_code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(d.vendor.referralCode ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Brand)
            }
        }
    }
}

@Composable
private fun VendorTotalsGrid(d: VendorDashboard) {
    val t = d.totals
    val cells = listOf(
        Triple(stringResource(R.string.vendor_total_referrals), t.referrals.toString(), Palette.Info),
        Triple(stringResource(R.string.vendor_paid_renewals), t.paidRenewals.toString(), Palette.Brand),
        Triple(stringResource(R.string.vendor_total_commission), Money.format(t.total), Palette.Success),
        Triple(stringResource(R.string.vendor_pending), Money.format(t.pending), Palette.Warning),
        Triple(stringResource(R.string.vendor_approved), Money.format(t.approved), Palette.Info),
        Triple(stringResource(R.string.vendor_paid), Money.format(t.paid), Palette.Success),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        cells.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (label, value, color) ->
                    AppCard(Modifier.weight(1f)) {
                        Column {
                            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthCard(m: VendorMonth) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(monthLabel(m.month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Money.format(m.commission), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Palette.Success)
            }
            Spacer(Modifier.height(6.dp))
            LabelValue(stringResource(R.string.vendor_paid_renewals), m.renewals.toString())
            LabelValue(stringResource(R.string.vendor_renewal_revenue), Money.format(m.renewalRevenue))
            LabelValue(stringResource(R.string.vendor_pending), Money.format(m.pending), if (m.pending > 0) Palette.Warning else androidx.compose.ui.graphics.Color.Unspecified)
            LabelValue(stringResource(R.string.vendor_approved), Money.format(m.approved))
            LabelValue(stringResource(R.string.vendor_paid), Money.format(m.paid), if (m.paid > 0) Palette.Success else androidx.compose.ui.graphics.Color.Unspecified)
            if (m.reversed > 0) LabelValue(stringResource(R.string.vendor_reversed), Money.format(m.reversed), Palette.Danger)
        }
    }
}

@Composable
private fun OwnerCard(o: VendorReferredOwner) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(o.ownerName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                StatusChip(o.commissionStatus ?: "none", o.commissionLabel)
            }
            Spacer(Modifier.height(4.dp))
            LabelValue(stringResource(R.string.vendor_referral_date), dateText(o.referralDate))
            o.trialStatus?.takeIf { it.isNotBlank() }?.let { LabelValue(stringResource(R.string.vendor_trial_status), it) }
            LabelValue(stringResource(R.string.vendor_renewal_status), (o.renewalLabel ?: "—") + if (o.paidRenewals > 1) " ×${o.paidRenewals}" else "")
            LabelValue(stringResource(R.string.vendor_renewal_amount), if (o.renewalAmount > 0) Money.format(o.renewalAmount) else "—")
            o.paymentLabel?.let { LabelValue(stringResource(R.string.vendor_payment_status), it, if (o.paymentStatus == "confirmed") Palette.Success else androidx.compose.ui.graphics.Color.Unspecified) }
            LabelValue(stringResource(R.string.vendor_commission), Money.format(o.commissionAmount) + " · " + pct(o.commissionPercent), bold = true)
            if (o.paidRenewals > 1) LabelValue(stringResource(R.string.vendor_all_renewals), Money.format(o.commissionTotal))
        }
    }
}

@Composable
private fun CommissionCard(c: VendorCommission) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.referredOwner, fontWeight = FontWeight.SemiBold)
                    Text(listOfNotNull(c.invoiceNumber, dateText(c.paymentConfirmedAt)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                StatusChip(c.status, c.statusLabel)
            }
            Spacer(Modifier.height(4.dp))
            LabelValue(stringResource(R.string.vendor_renewal_amount), Money.format(c.renewalAmount))
            LabelValue(stringResource(R.string.vendor_commission), Money.format(c.commissionAmount) + " · " + pct(c.commissionPercent), bold = true)
            if (c.status == "paid") LabelValue(stringResource(R.string.vendor_paid_on), dateText(c.paidAt) + (c.payoutReference?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), Palette.Success)
            if (c.status == "reversed" && !c.reversalReason.isNullOrBlank()) LabelValue(stringResource(R.string.vendor_reversed), c.reversalReason!!, Palette.Danger)
        }
    }
}

@Composable
private fun PayoutCard(p: VendorPayout) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(dateText(p.paidAt), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Money.format(p.amount), fontWeight = FontWeight.Bold, color = Palette.Success)
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp).fillMaxWidth())
            LabelValue(stringResource(R.string.vendor_utr), p.payoutReference?.takeIf { it.isNotBlank() } ?: "—")
            LabelValue(stringResource(R.string.vendor_entries), p.entries.toString())
            if (p.reversedAmount > 0) LabelValue(stringResource(R.string.vendor_reversed_later), Money.format(p.reversedAmount), Palette.Danger)
        }
    }
}

/** Owner home: entry to the Vendor Dashboard, shown only to active vendors. */
@Composable
fun VendorHomeCard(onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Storefront, null, tint = Palette.Success)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.vendor_dashboard), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.vendor_home_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}
