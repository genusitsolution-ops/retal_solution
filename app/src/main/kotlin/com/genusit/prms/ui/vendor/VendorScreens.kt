package com.genusit.prms.ui.vendor

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.BuildConfig
import com.genusit.prms.R
import com.genusit.prms.domain.model.PhotoSubject
import com.genusit.prms.domain.model.VendorDashboard
import com.genusit.prms.domain.model.VendorPaymentInput
import com.genusit.prms.domain.model.VendorProfile
import com.genusit.prms.domain.model.VendorReferredOwner
import com.genusit.prms.domain.usecase.ChangeVendorPasswordUseCase
import com.genusit.prms.domain.usecase.GetVendorHomeUseCase
import com.genusit.prms.domain.usecase.GetVendorProfileUseCase
import com.genusit.prms.domain.usecase.SaveVendorPaymentUseCase
import com.genusit.prms.domain.util.Money
import com.genusit.prms.platform.AppLanguage
import com.genusit.prms.ui.LocalUnread
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.SettingsViewModel
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SuccessText
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.owner.CommissionCard
import com.genusit.prms.ui.owner.MonthCard
import com.genusit.prms.ui.owner.OwnerCard
import com.genusit.prms.ui.owner.PayoutCard
import com.genusit.prms.ui.owner.payoutMethodLabel
import com.genusit.prms.ui.owner.pct
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/*
 * 1.17 — Independent vendor app. A vendor account (role "vendor" from the login answer) sees only these screens:
 * Dashboard, My Referrals, Commission, Payout History, My Profile (with Payment Details, password, language, logout).
 * No owner screen is registered in the vendor navigation graph, and the server refuses owner endpoints for a vendor.
 */

/** Vendor dashboard data (vendor/dashboard.php) shared by the Dashboard, Referrals, Commission and Payouts tabs. */
@HiltViewModel
class VendorHomeViewModel @Inject constructor(
    getHome: GetVendorHomeUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val home = loadable(live = true) { getHome() }
}

/** Profile, payment details and password of the signed-in vendor (vendor/profile.php). */
@HiltViewModel
class VendorProfileViewModel @Inject constructor(
    getProfile: GetVendorProfileUseCase,
    private val savePayment: SaveVendorPaymentUseCase,
    private val changePassword: ChangeVendorPasswordUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val profile = loadable { getProfile() }
    val paymentAction = action<VendorProfile>()
    val passwordAction = action<Unit>()

    fun savePayment(input: VendorPaymentInput, hasSaved: Boolean) =
        paymentAction.run(onSuccess = { profile.refresh(); sendEvent(ScreenEvent.Saved) }) { savePayment.invoke(input, hasSaved) }

    fun changePassword(current: String, new: String) =
        passwordAction.run(onSuccess = { profile.refresh(); sendEvent(ScreenEvent.PasswordChanged) }) { changePassword.invoke(current, new) }
}

/* ───────── Dashboard ───────── */

@Composable
fun VendorDashboardHome(openReferrals: () -> Unit, openCommission: () -> Unit, openPayouts: () -> Unit, openNotifications: () -> Unit) {
    val vm = hiltViewModel<VendorHomeViewModel>().home
    val state by vm.state.collectAsStateWithLifecycle()
    val unread = LocalUnread.current
    val ctx = LocalContext.current
    ScreenScaffold(stringResource(R.string.vendor_dashboard), subtitle = stringResource(R.string.role_vendor), actions = {
        IconButton(onClick = openNotifications, modifier = Modifier.testTag("vendor_bell")) {
            Icon(Icons.Filled.Notifications, stringResource(R.string.notifications) + if (unread > 0) " ($unread)" else "")
        }
    }) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize().testTag("vendor_home"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { VendorIdentityCard(d) { text -> com.genusit.prms.ui.common.shareText(ctx, text, ctx.getString(R.string.vendor_share_code)) } }
                item { VendorStats(d) }
                item { SectionHeader(stringResource(R.string.vendor_recent_activity), stringResource(R.string.view_all), openReferrals) }
                if (d.referredOwners.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_referrals, d.vendor.referralCode ?: "—"), Icons.Filled.Groups) }
                items(d.referredOwners.take(5)) { ReferralRow(it) }
                item { SectionHeader(stringResource(R.string.vendor_payout_history), stringResource(R.string.view_all), openPayouts) }
                if (d.payouts.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_payout), Icons.Filled.History) }
                items(d.payouts.take(3)) { PayoutCard(it) }
                item {
                    AppCard(onClick = openCommission) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Payments, null, tint = Palette.Success); Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.vendor_commissions), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorIdentityCard(d: VendorDashboard, share: (String) -> Unit) {
    val p = d.profile
    val name = p?.fullName ?: d.vendor.name.orEmpty()
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.genusit.prms.ui.common.Avatar(PhotoSubject.VENDOR, null, p?.photoVersion, name, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("vendor_name"))
                    Text(listOfNotNull(p?.mobile, p?.city).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.vendor_code), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.vendor.referralCode ?: "—", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold, color = Palette.Brand,
                        modifier = Modifier.testTag("vendor_code"))
                    Text(stringResource(R.string.vendor_commission_rate, pct(d.vendor.commissionPercent)), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val text = d.shareText ?: d.vendor.referralCode.orEmpty()
                OutlinedButton(onClick = { share(text) }, enabled = text.isNotBlank()) {
                    Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share))
                }
            }
        }
    }
}

@Composable
private fun VendorStats(d: VendorDashboard) {
    val t = d.totals
    val cells = listOf(
        Triple(stringResource(R.string.vendor_total_referrals), t.referrals.toString(), Palette.Info),
        Triple(stringResource(R.string.vendor_successful_registrations), t.registrations.toString(), Palette.Brand),
        Triple(stringResource(R.string.vendor_pending_referrals), t.referralsWaiting.toString(), Palette.Warning),
        Triple(stringResource(R.string.vendor_approved_referrals), t.referralsEarning.toString(), Palette.Success),
        Triple(stringResource(R.string.vendor_total_commission), Money.format(t.total), Palette.Success),
        Triple(stringResource(R.string.vendor_pending_commission), Money.format(t.pending + t.approved), Palette.Warning),
        Triple(stringResource(R.string.vendor_paid_commission), Money.format(t.paid), Palette.Success),
        Triple(stringResource(R.string.vendor_paid_renewals), t.paidRenewals.toString(), Palette.Info),
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.testTag("vendor_stats")) {
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
private fun referralStateLabel(s: String?): String = when (s) {
    "earning" -> stringResource(R.string.vendor_state_successful)
    "approved" -> stringResource(R.string.vendor_state_approved)
    "rejected" -> stringResource(R.string.vendor_state_rejected)
    else -> stringResource(R.string.vendor_state_pending)
}

private fun referralStateChip(s: String?): String = when (s) { "earning" -> "partial"; "approved" -> "paid"; "rejected" -> "cancelled"; else -> "pending" }

@Composable
private fun ReferralRow(o: VendorReferredOwner) {
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(o.ownerName, fontWeight = FontWeight.SemiBold)
                Text(dateText(o.referralDate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusChip(referralStateChip(o.referralState), referralStateLabel(o.referralState))
                if (o.commissionTotal > 0) Text(Money.format(o.commissionTotal), fontWeight = FontWeight.Bold, color = Palette.Success)
            }
        }
    }
}

/* ───────── My Referrals ───────── */

@Composable
fun VendorReferralsScreen() {
    val vm = hiltViewModel<VendorHomeViewModel>().home
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("") }
    ScreenScaffold(stringResource(R.string.vendor_my_referrals)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            val list = d.referredOwners.filter { filter.isEmpty() || (it.referralState ?: "waiting") == filter }
            LazyColumn(Modifier.fillMaxSize().testTag("vendor_referrals"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("" to R.string.all, "waiting" to R.string.vendor_state_pending, "earning" to R.string.vendor_state_successful,
                            "approved" to R.string.vendor_state_approved, "rejected" to R.string.vendor_state_rejected).forEach { (k, l) ->
                            FilterChip(selected = filter == k, onClick = { filter = k }, label = { Text(stringResource(l)) })
                        }
                    }
                }
                if (list.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_referrals, d.vendor.referralCode ?: "—"), Icons.Filled.Groups) }
                items(list) { OwnerCard(it) }
                item { Text(stringResource(R.string.vendor_privacy_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/* ───────── Commission ───────── */

@Composable
fun VendorCommissionScreen() {
    val vm = hiltViewModel<VendorHomeViewModel>().home
    val state by vm.state.collectAsStateWithLifecycle()
    var monthly by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.vendor_commission)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            val t = d.totals
            LazyColumn(Modifier.fillMaxSize().testTag("vendor_commission"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            LabelValue(stringResource(R.string.vendor_total_commission), Money.format(t.total), Palette.Success, bold = true)
                            LabelValue(stringResource(R.string.vendor_pending), Money.format(t.pending), Palette.Warning)
                            LabelValue(stringResource(R.string.vendor_approved), Money.format(t.approved), Palette.Info)
                            LabelValue(stringResource(R.string.vendor_paid), Money.format(t.paid), Palette.Success)
                            if (t.reversed > 0) LabelValue(stringResource(R.string.vendor_reversed), Money.format(t.reversed), Palette.Danger)
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !monthly, onClick = { monthly = false }, label = { Text(stringResource(R.string.vendor_tab_commissions)) })
                        FilterChip(selected = monthly, onClick = { monthly = true }, label = { Text(stringResource(R.string.vendor_tab_monthly)) })
                    }
                }
                if (monthly) {
                    if (d.monthly.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_commission), Icons.Filled.CalendarMonth) }
                    items(d.monthly, key = { "m" + it.month }) { MonthCard(it) }
                } else {
                    if (d.commissions.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_commission), Icons.Filled.Payments) }
                    items(d.commissions, key = { "c" + it.id }) { CommissionCard(it) }
                }
                item { Text(stringResource(R.string.vendor_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

/* ───────── Payout History ───────── */

@Composable
fun VendorPayoutsScreen() {
    val vm = hiltViewModel<VendorHomeViewModel>().home
    val state by vm.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.vendor_payout_history)) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { d ->
            LazyColumn(Modifier.fillMaxSize().testTag("vendor_payouts"), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    AppCard {
                        Column {
                            LabelValue(stringResource(R.string.vendor_paid_commission), Money.format(d.totals.paid), Palette.Success, bold = true)
                            LabelValue(stringResource(R.string.vendor_awaiting_payout), Money.format(d.totals.approved), Palette.Info)
                        }
                    }
                }
                if (d.payouts.isEmpty()) item { EmptyState(stringResource(R.string.vendor_no_payout), Icons.Filled.History) }
                items(d.payouts) { PayoutCard(it) }
            }
        }
    }
}

/* ───────── My Profile (payment details, password, language, logout) ───────── */

@Composable
fun VendorProfileScreen(openPayment: () -> Unit, openPassword: () -> Unit, openNotifications: () -> Unit) {
    val vm = hiltViewModel<VendorProfileViewModel>()
    val state by vm.profile.state.collectAsStateWithLifecycle()
    val settings: SettingsViewModel = hiltViewModel()
    val lang = AppLanguage.current()
    var confirmLogout by rememberSaveable { mutableStateOf(false) }
    ScreenScaffold(stringResource(R.string.vendor_my_profile)) { pad ->
        StateContent(state, vm.profile::refresh, Modifier.padding(pad)) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("vendor_profile"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.genusit.prms.ui.common.Avatar(PhotoSubject.VENDOR, null, p.photoVersion, p.fullName, 56.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(p.fullName, style = MaterialTheme.typography.titleLarge)
                                Text(stringResource(R.string.role_vendor) + " · " + p.vendorCode, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        LabelValue(stringResource(R.string.vendor_code), p.vendorCode, bold = true)
                        LabelValue(stringResource(R.string.phone), p.mobile ?: "—")
                        p.whatsapp?.let { LabelValue(stringResource(R.string.vendor_whatsapp), it) }
                        p.email?.let { LabelValue(stringResource(R.string.email), it) }
                        LabelValue(stringResource(R.string.address), listOfNotNull(p.address, p.city, p.state, p.pincode).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "—" })
                        LabelValue(stringResource(R.string.vendor_registered_on), dateText(p.registeredAt))
                        LabelValue(stringResource(R.string.vendor_commission), pct(p.commissionPercent))
                        Text(stringResource(R.string.vendor_profile_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                ProfileRow(Icons.Filled.AccountBalance, stringResource(R.string.vendor_payment_details),
                    if (p.payment.payoutMethod == "bank") stringResource(R.string.vendor_method_bank) else stringResource(R.string.vendor_method_upi), openPayment, "vendor_open_payment")
                ProfileRow(Icons.Filled.Key, stringResource(R.string.change_password), null, openPassword, "vendor_open_password")
                ProfileRow(Icons.Filled.Notifications, stringResource(R.string.notifications), null, openNotifications, "vendor_open_notifications")
                AppCard {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Language, null, tint = Palette.Brand); Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("en" to "English", "hi" to "हिंदी", "mr" to "मराठी").forEach { (code, label) ->
                                FilterChip(selected = lang == code, onClick = { settings.setLanguage(code) }, label = { Text(label) })
                            }
                        }
                    }
                }
                com.genusit.prms.ui.common.NotificationPrefsCard()
                OutlinedButton(onClick = { confirmLogout = true }, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("vendor_logout")) {
                    Icon(Icons.AutoMirrored.Filled.Logout, null, tint = Palette.Danger); Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.logout), color = Palette.Danger)
                }
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.labelMedium, color = Palette.Muted)
            }
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        title = { Text(stringResource(R.string.logout)) },
        text = { Text(stringResource(R.string.logout_confirm)) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; settings.logout() }) { Text(stringResource(R.string.logout)) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ProfileRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String?, onClick: () -> Unit, tag: String) {
    AppCard(Modifier.testTag(tag), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Palette.Brand); Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                if (sub != null) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
        }
    }
}

/* ───────── Payment Details ───────── */

@Composable
fun VendorPaymentScreen(onBack: () -> Unit) {
    val vm = hiltViewModel<VendorProfileViewModel>()
    val state by vm.profile.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.vendor_payment_details), onBack = onBack) { pad ->
        StateContent(state, vm.profile::refresh, Modifier.padding(pad)) { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("vendor_payment"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (p.payment.canEdit) PaymentForm(vm, p) else PaymentView(p)
            }
        }
    }
}

@Composable
private fun PaymentView(p: VendorProfile) {
    val m = p.payment
    AppCard {
        Column {
            LabelValue(stringResource(R.string.vendor_payout_method), payoutMethodLabel(m.payoutMethod), bold = true)
            LabelValue(stringResource(R.string.vendor_upi_id), m.upiId ?: "—")
            LabelValue(stringResource(R.string.vendor_account_holder), m.accountHolder ?: "—")
            LabelValue(stringResource(R.string.vendor_bank_name), m.bankName ?: "—")
            LabelValue(stringResource(R.string.vendor_account_number), m.accountNumberMasked ?: "—")
            LabelValue(stringResource(R.string.vendor_ifsc), m.ifsc ?: "—")
        }
    }
    Text(stringResource(R.string.vendor_payment_admin_only), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PaymentForm(vm: VendorProfileViewModel, p: VendorProfile) {
    val m = p.payment
    val act by vm.paymentAction.state.collectAsStateWithLifecycle()
    var method by rememberSaveable { mutableStateOf(m.payoutMethod) }
    var upi by rememberSaveable { mutableStateOf(m.upiId.orEmpty()) }
    var holder by rememberSaveable { mutableStateOf(m.accountHolder.orEmpty()) }
    var bank by rememberSaveable { mutableStateOf(m.bankName.orEmpty()) }
    var account by rememberSaveable { mutableStateOf("") }
    var ifsc by rememberSaveable { mutableStateOf(m.ifsc.orEmpty()) }
    var localErr by rememberSaveable { mutableStateOf<String?>(null) }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.vendor_payout_method), style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = method == "upi", onClick = { method = "upi" }, label = { Text(stringResource(R.string.vendor_method_upi)) })
                FilterChip(selected = method == "bank", onClick = { method = "bank" }, label = { Text(stringResource(R.string.vendor_method_bank)) })
            }
            FormField(upi, { upi = it.trim().take(100) }, stringResource(R.string.vendor_upi_id), localErr == SaveVendorPaymentUseCase.UPI, KeyboardType.Email)
            FormField(holder, { holder = it.take(150) }, stringResource(R.string.vendor_account_holder), localErr == SaveVendorPaymentUseCase.BANK_NEEDS && holder.isBlank())
            FormField(bank, { bank = it.take(150) }, stringResource(R.string.vendor_bank_name))
            FormField(account, { account = it.filter { c -> c.isDigit() }.take(20) }, stringResource(R.string.vendor_account_number),
                localErr == SaveVendorPaymentUseCase.ACCOUNT, KeyboardType.Number,
                supporting = if (m.hasAccountNumber) stringResource(R.string.vendor_account_keep, m.accountNumberMasked.orEmpty()) else null)
            FormField(ifsc, { ifsc = it.uppercase().filter { c -> c.isLetterOrDigit() }.take(11) }, stringResource(R.string.vendor_ifsc), localErr == SaveVendorPaymentUseCase.IFSC)
            Text(stringResource(R.string.vendor_bank_optional), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            localErr?.let {
                Text(stringResource(when (it) {
                    SaveVendorPaymentUseCase.UPI -> R.string.vendor_err_upi
                    SaveVendorPaymentUseCase.IFSC -> R.string.vendor_err_ifsc
                    SaveVendorPaymentUseCase.ACCOUNT -> R.string.vendor_err_account
                    else -> R.string.vendor_err_bank_needs
                }), color = MaterialTheme.colorScheme.error)
            }
            ActionError(act)
            if (act is ActionState.Done<*>) SuccessText(stringResource(R.string.saved))
            Button(onClick = {
                val input = VendorPaymentInput(method, upi, holder, bank, account, ifsc)
                localErr = SaveVendorPaymentUseCase.validate(input, m.hasAccountNumber)
                if (localErr == null) vm.savePayment(input, m.hasAccountNumber)
            }, enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().testTag("vendor_payment_save")) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.save))
            }
        }
    }
}

/* ───────── Change password (forced after a temporary password) ───────── */

@Composable
fun VendorPasswordScreen(forced: Boolean, onBack: (() -> Unit)?, onDone: () -> Unit) {
    val vm = hiltViewModel<VendorProfileViewModel>()
    val act by vm.passwordAction.state.collectAsStateWithLifecycle()
    val settings: SettingsViewModel = hiltViewModel()
    var current by rememberSaveable { mutableStateOf("") }
    var next by rememberSaveable { mutableStateOf("") }
    var confirm by rememberSaveable { mutableStateOf("") }
    var localErr by rememberSaveable { mutableStateOf(0) }
    com.genusit.prms.ui.common.EventEffect(vm) { if (it == ScreenEvent.PasswordChanged) onDone() }
    ScreenScaffold(stringResource(R.string.change_password), onBack = onBack) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp).testTag("vendor_password"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (forced) AppCard { Text(stringResource(R.string.vendor_temp_password_note), color = Palette.Warning, fontWeight = FontWeight.SemiBold) }
            FormField(current, { current = it }, stringResource(R.string.current_password), localErr == 3, password = true, passwordToggle = true)
            FormField(next, { next = it }, stringResource(R.string.new_password), localErr == 1, password = true, passwordToggle = true)
            FormField(confirm, { confirm = it }, stringResource(R.string.confirm_password), localErr == 2, password = true, passwordToggle = true)
            if (localErr == 3) Text(stringResource(R.string.err_current_password), color = MaterialTheme.colorScheme.error)
            if (localErr == 1) Text(stringResource(R.string.err_password_short), color = MaterialTheme.colorScheme.error)
            if (localErr == 2) Text(stringResource(R.string.err_password_mismatch), color = MaterialTheme.colorScheme.error)
            ActionError(act)
            Button(onClick = {
                localErr = when { current.isEmpty() -> 3; next.length < 6 -> 1; next != confirm -> 2; else -> 0 }
                if (localErr == 0) vm.changePassword(current, next)
            }, enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().height(50.dp).testTag("vendor_password_save")) {
                if (act is ActionState.Running) CircularProgressIndicator(color = Color.White) else Text(stringResource(R.string.save))
            }
            if (forced) TextButton(onClick = { settings.logout() }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.logout), color = Palette.Danger)
            }
        }
    }
}
