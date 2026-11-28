package com.genusit.prms.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ToggleOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.BuildConfig
import com.genusit.prms.admin.net.Commission
import com.genusit.prms.admin.net.CommissionHistory
import com.genusit.prms.admin.net.UnpaidMonth
import com.genusit.prms.admin.net.Vendor
import com.genusit.prms.admin.net.payoutMethodLabel

val PayoutMethods = listOf("upi" to "UPI", "bank" to "Bank transfer", "cash" to "Cash", "other" to "Other")

@Composable
fun VendorsScreen(nav: NavHostController) {
    val api = appApi(); val session = appSession()
    var filter by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val vm = viewModel(key = "vendors-$filter-$query") { ScreenModel(session) { api.vendors(query, filter) } }
    val state by vm.state.collectAsStateWithLifecycle()
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }

    Page("Vendors", subtitle = "Referral partners and their commission", onRefresh = { vm.load(showSpinner = false) }, header = {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchBox(typed, { typed = it }, query, { query = it; typed = it }, "Search name, mobile or vendor code")
            Chips(listOf("" to "All", "active" to "Active", "inactive" to "Inactive", "suspended" to "Suspended"), filter) { filter = it }
        }
    }, fab = {
        ExtendedFloatingActionButton(onClick = { nav.navigate("vendor/new") }, modifier = Modifier.testTag("add_vendor"), containerColor = C.BrandDark, contentColor = Color.White,
            icon = { Icon(Icons.Filled.AddBusiness, null) }, text = { Text("Add vendor") })
    }) {
        when (val s = state) {
            is Load.Data -> {
                val t = s.value.totals
                item {
                    AppCard {
                        Column {
                            SectionTitle("Commission (all vendors)")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStat("Pending approval", money(t.pending), C.Warning, Modifier.weight(1f))
                                MiniStat("Approved, to pay", money(t.approved), C.Brand, Modifier.weight(1f))
                                MiniStat("Paid", money(t.paid), C.Success, Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (s.value.vendors.isEmpty()) item { Empty(if (query.isBlank() && filter.isBlank()) "No vendor yet. Tap “Add vendor” to register one." else "No vendor found.") }
                s.value.vendors.forEach { v -> item(key = v.id) { VendorCard(v) { nav.navigate("vendor/${v.id}") } } }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun VendorCard(v: Vendor, onClick: () -> Unit) {
    AppCard(onClick = onClick, padding = 14) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PersonRow(v.fullName, listOfNotNull(v.phone?.takeIf { it.isNotBlank() }, v.email?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { null }) { StatusChip(v.status) }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                v.code?.let { Tag(it) }
                if (v.isIndependent) Tag("Independent", C.Teal, C.TealSoft) else Tag("Owner account", C.Muted, C.Bg)
                Tag(pct(v.commissionPercent) + " commission", C.BrandDark, C.Bg)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniStat("Referrals", v.totals.referrals.toString(), C.Info, Modifier.weight(1f))
                MiniStat("Pending", money(v.totals.pending), C.Warning, Modifier.weight(1f))
                MiniStat("To pay", money(v.totals.approved), C.Brand, Modifier.weight(1f))
            }
        }
    }
}

private enum class VTab(val label: String) { COMMISSIONS("Commissions"), PAYOUT("Monthly payout"), OWNERS("Referred owners"), HISTORY("Payout history") }

/** The sign-in page the vendor is pointed to (same server as this app). */
fun loginUrl(): String = BuildConfig.API_BASE_URL.substringBefore("api/v1") + "login.php"

fun welcomeMessage(name: String, code: String, mobile: String, password: String) =
    "Welcome to Rental Solution, $name!\nYour vendor account is ready.\nVendor Code: $code\nLogin mobile: $mobile\nTemporary password: $password\n" +
        "Sign in to the Rental Solution app (or ${loginUrl()}) and change the password.\nShare your vendor code with property owners when they register."

/** Shows a new sign-in once (vendor code, mobile, temporary password) with copy / WhatsApp / share. */
@Composable
fun CredentialsDialog(title: String, name: String, code: String, mobile: String, whatsappNo: String?, password: String, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val msg = welcomeMessage(name, code, mobile, password)
    AlertDialog(onDismissRequest = {}, containerColor = Color.White, title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Give these to $name. The password is shown only now; the vendor must change it at the first sign-in.", color = C.Muted,
                    style = MaterialTheme.typography.bodyMedium)
                AppCard(padding = 12) {
                    Column {
                        Text("Vendor code", color = C.Muted, style = MaterialTheme.typography.labelMedium); Text(code, style = MonoStyle, color = C.BrandDark)
                        Spacer(Modifier.size(6.dp))
                        Text("Login mobile", color = C.Muted, style = MaterialTheme.typography.labelMedium); Text(mobile, style = MonoStyle)
                        Spacer(Modifier.size(6.dp))
                        Text("Temporary password", color = C.Muted, style = MaterialTheme.typography.labelMedium); Text(password, style = MonoStyle, color = C.Danger)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { copyText(ctx, "Vendor sign-in", msg) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Copy") }
                    OutlinedButton(onClick = { whatsapp(ctx, whatsappNo?.takeIf { it.isNotBlank() } ?: mobile, msg) }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp") }
                }
                OutlinedButton(onClick = { shareText(ctx, msg, "Send sign-in details") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Share (SMS, email…)") }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("Done") } })
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun VendorDetailScreen(nav: NavHostController, id: Int) {
    val api = appApi(); val session = appSession()
    val ctx = LocalContext.current
    val vm = viewModel(key = "vendor-$id") { ScreenModel(session) { api.vendor(id) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(VTab.COMMISSIONS) }
    var edit by remember { mutableStateOf(false) }
    var changeStatus by remember { mutableStateOf(false) }
    var askReset by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf<String?>(null) }
    var payMonth by remember { mutableStateOf<UnpaidMonth?>(null) }
    var cAction by remember { mutableStateOf<Pair<String, Commission>?>(null) }
    var history by remember { mutableStateOf<Pair<Commission, Load<CommissionHistory>>?>(null) }
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }   // back from Edit: show the saved values
    val d = (state as? Load.Data)?.value
    val defaultMethod = d?.profile?.payment?.payoutMethod?.takeIf { it.isNotBlank() } ?: "upi"

    // converted (owner-account) vendors: % / status / remarks as before
    if (edit && d != null) {
        FormDialog("Edit vendor", listOf(
            Field("percent", "Commission %", initial = plain(d.vendor.commissionPercent), required = true, keyboard = KeyboardType.Decimal),
            Field("remarks", "Remarks", initial = d.vendor.remarks.orEmpty(), multiline = true)), "Save",
            intro = "A new % applies to renewals confirmed from now on. Existing commissions keep their %.",
            options = Triple("Status", d.vendor.status, listOf("active" to "Active", "inactive" to "Inactive")),
            onSubmit = { v -> vm.act({ api.updateVendor(id, v["percent"]!!, v["_choice"]!!, v["remarks"]!!) }) }, onDismiss = { edit = false })
    }
    if (changeStatus && d != null) {
        FormDialog("Status of ${d.vendor.fullName}", listOf(Field("reason", "Reason", multiline = true, hint = "Required to suspend")), "Save",
            intro = "Inactive or suspended vendors are signed out at once and cannot sign in. Their history stays.",
            options = Triple("Status", d.vendor.status, listOf("active" to "Active", "inactive" to "Inactive", "suspended" to "Suspended")),
            requiredWhen = mapOf("reason" to setOf("suspended")),
            onSubmit = { v -> vm.act({ api.setVendorStatus(id, v["_choice"]!!, v["reason"]!!) }) }, onDismiss = { changeStatus = false })
    }
    if (askReset && d != null) {
        ConfirmDialog("Reset sign-in of ${d.vendor.fullName}?", "A new temporary password is created and shown once. The current password stops working and the vendor is signed out.",
            "Reset", danger = true, onConfirm = { vm.act({ val (m, pw) = api.resetVendorPassword(id); newPassword = pw; m }) }, onDismiss = { askReset = false })
    }
    newPassword?.let { pw ->
        val v = d?.vendor
        CredentialsDialog("New temporary password", v?.fullName.orEmpty(), v?.code.orEmpty(), d?.profile?.mobile ?: v?.phone.orEmpty(), d?.profile?.whatsapp, pw) { newPassword = null }
    }
    payMonth?.let { m ->
        FormDialog("Record payout — ${monthName(m.month)}", listOf(
            Field("date", "Paid on (YYYY-MM-DD)", initial = today(), required = true),
            Field("utr", "UTR / transfer reference", required = true),
            Field("remarks", "Remarks", multiline = true)), "Save payout",
            intro = "Approved: ${m.approvedN} commission(s), ${money(m.approvedAmount)}." + (if (m.pendingN > 0) " Not yet approved: ${m.pendingN}, ${money(m.pendingAmount)}." else "") +
                " Transfer the money first (bank / UPI); this only records it.",
            options = Triple("Paid by", defaultMethod, PayoutMethods),
            checks = if (m.pendingN > 0) listOf(Triple("approve_pending", "Also approve and pay the ${m.pendingN} not-yet-approved commission(s)", false)) else emptyList(),
            onSubmit = { v -> vm.act({ api.payMonth(id, m.month, v["date"]!!, v["utr"]!!, v["remarks"]!!, v["approve_pending"] == "1", v["_choice"]!!) }) },
            onDismiss = { payMonth = null })
    }
    cAction?.let { (action, c) ->
        val dismiss = { cAction = null }
        when (action) {
            "approve" -> FormDialog("Approve commission ${money(c.commissionAmount)}?", listOf(Field("remarks", "Remarks", multiline = true)), "Approve",
                intro = "${c.referredName ?: ""} · ${c.invoiceNumber ?: ""}", onSubmit = { v -> vm.act({ api.commissionAction("approve", c.id, mapOf("remarks" to v["remarks"]!!)) }) }, onDismiss = dismiss)
            "mark_paid" -> FormDialog("Mark ${money(c.commissionAmount)} as paid", listOf(
                Field("paid_date", "Paid on (YYYY-MM-DD)", initial = today(), required = true), Field("payout_reference", "UTR / reference"),
                Field("remarks", "Remarks", multiline = true)), "Mark paid",
                intro = "For one commission. For a whole month use “Record payout” on the Monthly payout tab.",
                options = Triple("Paid by", defaultMethod, PayoutMethods),
                onSubmit = { v -> vm.act({ api.commissionAction("mark_paid", c.id, v.filterKeys { it != "_choice" } + ("payout_method" to v["_choice"]!!)) }) }, onDismiss = dismiss)
            "reverse" -> FormDialog(if (c.status == "pending") "Reject commission ${money(c.commissionAmount)}?" else "Reverse commission ${money(c.commissionAmount)}?",
                listOf(Field("reason", "Reason", required = true, multiline = true)), if (c.status == "pending") "Reject" else "Reverse",
                danger = true, intro = if (c.status == "paid") "It was already paid — recover the amount from the vendor." else "The vendor will not be paid for this renewal. The record and its history are kept.",
                onSubmit = { v -> vm.act({ api.commissionAction("reverse", c.id, v) }) }, onDismiss = dismiss)
            "remark" -> FormDialog("Add remark", listOf(Field("remarks", "Remark", initial = c.adminRemarks.orEmpty(), required = true, multiline = true)), "Save",
                onSubmit = { v -> vm.act({ api.commissionAction("remark", c.id, v) }) }, onDismiss = dismiss)
        }
    }
    history?.let { (c, h) ->
        LaunchedEffect(c.id) {
            history = c to try { Load.Data(api.commissionHistory(c.id)) } catch (e: Exception) { Load.Error(e.message ?: "Could not load the history.") }
        }
        AlertDialog(onDismissRequest = { history = null }, containerColor = Color.White, title = { Text("History of C-${c.id}") },
            confirmButton = { TextButton(onClick = { history = null }) { Text("Close") } },
            text = {
                when (h) {
                    is Load.Data -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        h.value.history.forEach { r ->
                            Column {
                                Text("${r.action.replaceFirstChar { it.uppercase() }} · ${dateTime(r.createdAt)}", fontWeight = FontWeight.SemiBold)
                                SmallText(listOfNotNull(r.adminName ?: r.actorType, r.note?.takeIf { it.isNotBlank() }).joinToString(" — "))
                            }
                        }
                    }
                    is Load.Error -> Text(h.message)
                    else -> Text("Loading…")
                }
            })
    }

    Page(d?.vendor?.fullName ?: "Vendor", nav, subtitle = d?.vendor?.code, onRefresh = { vm.load(showSpinner = false) }) {
        when (val s = state) {
            is Load.Data -> {
                val v = s.value.vendor; val t = s.value.totals; val pr = s.value.profile
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Avatar(v.fullName, 52)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(v.fullName, style = MaterialTheme.typography.titleMedium, color = C.Ink)
                                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        if (v.isIndependent) Tag("Independent", C.Teal, C.TealSoft) else Tag("Owner account", C.Muted, C.Bg)
                                        Tag(pct(v.commissionPercent) + " commission", C.BrandDark, C.Bg)
                                    }
                                }
                                StatusChip(v.status)
                            }
                            v.code?.let { code ->
                                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Vendor referral code", color = C.Muted, style = MaterialTheme.typography.labelMedium)
                                        Text(code, style = MonoStyle.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = C.BrandDark)
                                    }
                                    TextButton(onClick = { copyText(ctx, "Vendor code", code) }) { Icon(Icons.Filled.ContentCopy, "Copy code", Modifier.size(18.dp)) }
                                    TextButton(onClick = { shareText(ctx, "Register on Rental Solution with my vendor code: $code", "Share vendor code") }) { Icon(Icons.Filled.Share, "Share code", Modifier.size(18.dp)) }
                                }
                            }
                            val phone = pr?.mobile ?: v.phone
                            ActionRow {
                                phone?.takeIf { it.isNotBlank() }?.let { p -> OutlinedButton(onClick = { dial(ctx, p) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Call, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Call") } }
                                (pr?.whatsapp ?: phone)?.takeIf { it.isNotBlank() }?.let { p -> OutlinedButton(onClick = { whatsapp(ctx, p) }, modifier = Modifier.weight(1f)) { Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("WhatsApp") } }
                                (pr?.email ?: v.email)?.takeIf { it.isNotBlank() }?.let { e -> OutlinedButton(onClick = { email(ctx, e) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Email, null, Modifier.size(18.dp)) } }
                            }
                            HorizontalDivider(Modifier.padding(top = 12.dp), color = C.Border)
                            if (v.isIndependent) ActionRow {
                                Button(onClick = { nav.navigate("vendor/$id/edit") }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Edit") }
                                OutlinedButton(onClick = { changeStatus = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Filled.ToggleOn, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Status") }
                                OutlinedButton(onClick = { askReset = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = C.Danger)) {
                                    Icon(Icons.Filled.Key, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Reset") }
                            } else ActionRow {
                                Button(onClick = { edit = true }, enabled = !busy, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Edit % / status") }
                                if (v.ownerId > 0) OutlinedButton(onClick = { nav.navigate("owner/${v.ownerId}") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) { Text("Owner details") }
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("Referred owners", t.referrals.toString(), C.Info, Modifier.weight(1f))
                            MiniStat("Paid renewals", t.paidRenewals.toString(), C.BrandDark, Modifier.weight(1f))
                            MiniStat("Total earned", money(t.total), C.Teal, Modifier.weight(1f))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MiniStat("Pending approval", money(t.pending), C.Warning, Modifier.weight(1f))
                            MiniStat("Approved, to pay", money(t.approved), C.Brand, Modifier.weight(1f))
                            MiniStat("Paid", money(t.paid), C.Success, Modifier.weight(1f))
                        }
                    }
                }
                if (pr != null) {
                    item {
                        AppCard {
                            Column {
                                SectionTitle("Profile")
                                InfoRow("Mobile (login)", pr.mobile)
                                InfoRow("WhatsApp", pr.whatsapp)
                                InfoRow("Email", pr.email)
                                InfoRow("Address", listOfNotNull(pr.address, pr.city, listOfNotNull(pr.state, pr.pincode).joinToString(" ").ifBlank { null }).filter { it.isNotBlank() }.joinToString(", "))
                                InfoRow("Registered", date(pr.registeredAt))
                                InfoRow("Sign-in", if (pr.mustChangePassword) "Temporary password — not changed yet" else "Own password set",
                                    if (pr.mustChangePassword) C.Warning else C.Success)
                                if (!v.remarks.isNullOrBlank()) InfoRow("Remarks", v.remarks)
                            }
                        }
                    }
                    item {
                        AppCard {
                            Column {
                                SectionTitle("Payment details")
                                val p = pr.payment
                                InfoRow("Preferred method", payoutMethodLabel(p.payoutMethod), bold = true)
                                InfoRow("UPI ID", p.upiId)
                                InfoRow("Account holder", p.accountHolder)
                                InfoRow("Bank", p.bankName)
                                InfoRow("Account no.", p.accountNumberMasked)
                                InfoRow("IFSC", p.ifsc)
                                InfoRow("Vendor can edit", if (p.canEdit) "Yes" else "No — only the Super Admin")
                            }
                        }
                    }
                } else if (!v.remarks.isNullOrBlank()) item { AppCard { InfoRow("Remarks", v.remarks) } }
                item { Chips(VTab.entries.map { it.name to it.label }, tab.name) { tab = VTab.valueOf(it) } }
                when (tab) {
                    VTab.PAYOUT -> {
                        if (s.value.unpaidMonths.isEmpty()) item { Empty("Nothing to pay. Every commission is paid or reversed.") }
                        s.value.unpaidMonths.forEach { m ->
                            item(key = "m${m.month}") {
                                AppCard {
                                    Column {
                                        Text(monthName(m.month), style = MaterialTheme.typography.titleSmall, color = C.Ink)
                                        MetricRow("Approved, to pay", "${m.approvedN} · ${money(m.approvedAmount)}", C.Brand)
                                        MetricRow("Not yet approved", "${m.pendingN} · ${money(m.pendingAmount)}", C.Warning)
                                        ActionRow { GreenButton("Record payout", enabled = !busy, modifier = Modifier.fillMaxWidth()) { payMonth = m } }
                                    }
                                }
                            }
                        }
                    }
                    VTab.COMMISSIONS -> {
                        if (s.value.commissions.isEmpty()) item { Empty("No commission yet. A commission appears when a referred owner's renewal payment is confirmed.") }
                        s.value.commissions.forEach { c ->
                            item(key = "c${c.id}") {
                                AppCard {
                                    Column {
                                        PersonRow(c.referredName ?: "—", "C-${c.id} · ${c.invoiceNumber ?: ""} · ${date(c.paymentConfirmedAt)}") { StatusChip(c.status, c.statusLabel) }
                                        Spacer(Modifier.size(6.dp))
                                        InfoRow("Renewal", money(c.renewalAmount) + if (c.eligibleAmount != c.renewalAmount) " (eligible ${money(c.eligibleAmount)})" else "")
                                        InfoRow("Commission", "${money(c.commissionAmount)} · ${pct(c.commissionPercent)}", C.BrandDark, bold = true)
                                        if (c.status == "paid" || c.paidAt != null) InfoRow("Paid", date(c.paidAt) + (c.payoutMethod?.let { " · ${payoutMethodLabel(it)}" } ?: "") + (c.payoutReference?.let { " · $it" } ?: ""), C.Success)
                                        if (!c.reversalReason.isNullOrBlank()) InfoRow(if (c.paidAt == null) "Rejected" else "Reversed", c.reversalReason, C.Danger)
                                        if (!c.adminRemarks.isNullOrBlank()) InfoRow("Remarks", c.adminRemarks)
                                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = C.Border)
                                        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                            if (c.status == "pending") TextButton(onClick = { cAction = "approve" to c }, enabled = !busy) { Text("Approve", color = C.Success) }
                                            if (c.status == "pending" || c.status == "approved") TextButton(onClick = { cAction = "mark_paid" to c }, enabled = !busy) { Text("Mark paid") }
                                            if (c.status != "reversed") TextButton(onClick = { cAction = "reverse" to c }, enabled = !busy) { Text(if (c.status == "pending") "Reject" else "Reverse", color = C.Danger) }
                                            TextButton(onClick = { cAction = "remark" to c }, enabled = !busy) { Text("Remark") }
                                            TextButton(onClick = { history = c to Load.Loading }) { Text("History") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    VTab.OWNERS -> {
                        if (s.value.referredOwners.isEmpty()) item { Empty("No owner has registered with this vendor's code yet.") }
                        s.value.referredOwners.forEach { r ->
                            item(key = "r${r.ownerId}-${r.referralDate}") {
                                AppCard(onClick = if (r.ownerId > 0) ({ nav.navigate("owner/${r.ownerId}") }) else null) {
                                    Column {
                                        PersonRow(r.ownerName, "Referred ${date(r.referralDate)}" + if (r.referralType == "owner") " (before becoming a vendor)" else "") {
                                            StatusChip(r.commissionStatus, r.commissionLabel)
                                        }
                                        Spacer(Modifier.size(6.dp))
                                        InfoRow("Trial", r.trialStatus)
                                        InfoRow("Renewal", (r.renewalLabel ?: "—") + if (r.renewalAmount > 0) " · ${money(r.renewalAmount)}" else "")
                                        InfoRow("Payment", r.paymentLabel, statusColors(r.paymentStatus).first)
                                        InfoRow("Commission", money(r.commissionAmount) + if (r.paidRenewals > 1) " (all renewals ${money(r.commissionTotal)})" else "", bold = true)
                                    }
                                }
                            }
                        }
                    }
                    VTab.HISTORY -> {
                        if (s.value.payouts.isEmpty()) item { Empty("No payout recorded yet.") }
                        s.value.payouts.forEach { p ->
                            item {
                                AppCard {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(date(p.paidAt), style = MaterialTheme.typography.titleSmall, color = C.Ink, modifier = Modifier.weight(1f))
                                            Text(money(p.amount), fontWeight = FontWeight.Bold, color = C.Success)
                                        }
                                        InfoRow("Method", payoutMethodLabel(p.payoutMethod))
                                        InfoRow("UTR / reference", p.payoutReference)
                                        InfoRow("Commissions", p.entries.toString())
                                        InfoRow("Recorded by", p.recordedBy)
                                        if (p.reversedAmount > 0) InfoRow("Reversed later", money(p.reversedAmount) + " — recover from vendor", C.Danger, bold = true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}
