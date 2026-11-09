package com.genusit.prms.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.Commission
import com.genusit.prms.admin.net.CommissionHistory
import com.genusit.prms.admin.net.UnpaidMonth

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

    Page("Vendors", onRefresh = { vm.load(showSpinner = false) }, header = {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("Search vendor or referral code") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (typed != query) TextButton(onClick = { query = typed.trim() }) { Text("Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = typed.trim() }),
                modifier = Modifier.fillMaxWidth())
            Chips(listOf("" to "All", "active" to "Active", "inactive" to "Inactive"), filter) { filter = it }
        }
    }) {
        when (val s = state) {
            is Load.Data -> {
                val t = s.value.totals
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Pending", money(t.pending), C.Warning, Modifier.weight(1f))
                        StatTile("To pay", money(t.approved), C.Info, Modifier.weight(1f))
                        StatTile("Paid", money(t.paid), C.Success, Modifier.weight(1f))
                    }
                }
                item { SmallText("To make an owner a vendor, open the owner under Owners and tap “Convert to vendor”.") }
                if (s.value.vendors.isEmpty()) item { Empty("No vendor yet.") }
                s.value.vendors.forEach { v ->
                    item(key = v.id) {
                        AppCard(onClick = { nav.navigate("vendor/${v.id}") }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(v.fullName, fontWeight = FontWeight.SemiBold); SmallText("@${v.username} · code ${v.referralCode ?: "—"}") }
                                    StatusChip(v.status)
                                }
                                SmallText("${pct(v.commissionPercent)} commission · ${v.totals.referrals} referrals · ${v.totals.paidRenewals} paid renewals")
                                SmallText("Earned ${money(v.totals.total)} · pending ${money(v.totals.pending)} · to pay ${money(v.totals.approved)} · paid ${money(v.totals.paid)}")
                            }
                        }
                    }
                }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}

private enum class VTab(val label: String) { PAYOUT("Monthly payout"), COMMISSIONS("Commissions"), OWNERS("Referred owners"), HISTORY("Payout history") }

@Composable
fun VendorDetailScreen(nav: NavHostController, id: Int) {
    val api = appApi(); val session = appSession()
    val vm = viewModel(key = "vendor-$id") { ScreenModel(session) { api.vendor(id) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(VTab.PAYOUT) }
    var edit by remember { mutableStateOf(false) }
    var payMonth by remember { mutableStateOf<UnpaidMonth?>(null) }
    var cAction by remember { mutableStateOf<Pair<String, Commission>?>(null) }
    var history by remember { mutableStateOf<Pair<Commission, Load<CommissionHistory>>?>(null) }
    Messages(vm.messages)
    val d = (state as? Load.Data)?.value

    if (edit && d != null) {
        FormDialog("Edit vendor", listOf(
            Field("percent", "Commission %", initial = d.vendor.commissionPercent.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() }, required = true, keyboard = KeyboardType.Decimal),
            Field("remarks", "Remarks", initial = d.vendor.remarks.orEmpty(), multiline = true)), "Save",
            intro = "A new % applies to renewals confirmed from now on. Existing commissions keep their %.",
            options = Triple("Status", d.vendor.status, listOf("active" to "Active", "inactive" to "Inactive")),
            onSubmit = { v -> vm.act({ api.updateVendor(id, v["percent"]!!, v["_choice"]!!, v["remarks"]!!) }) }, onDismiss = { edit = false })
    }
    payMonth?.let { m ->
        val n = m.approvedN; val amt = m.approvedAmount
        FormDialog("Record payout — ${monthName(m.month)}", listOf(
            Field("date", "Paid on (YYYY-MM-DD)", initial = today(), required = true),
            Field("utr", "UTR / transfer reference", required = true),
            Field("remarks", "Remarks", multiline = true)), "Save payout",
            intro = "Approved: $n commission(s), ${money(amt)}." + (if (m.pendingN > 0) " Not yet approved: ${m.pendingN}, ${money(m.pendingAmount)}." else "") +
                " Transfer the money first (bank / UPI); this only records it. Nothing is sent by the app.",
            checks = if (m.pendingN > 0) listOf(Triple("approve_pending", "Also approve and pay the ${m.pendingN} not-yet-approved commission(s)", false)) else emptyList(),
            onSubmit = { v -> vm.act({ api.payMonth(id, m.month, v["date"]!!, v["utr"]!!, v["remarks"]!!, v["approve_pending"] == "1") }) },
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
                onSubmit = { v -> vm.act({ api.commissionAction("mark_paid", c.id, v) }) }, onDismiss = dismiss)
            "reverse" -> FormDialog("Reverse commission ${money(c.commissionAmount)}?", listOf(Field("reason", "Reason", required = true, multiline = true)), "Reverse",
                danger = true, intro = if (c.status == "paid") "It was already paid — recover the amount from the vendor." else "The vendor will not be paid for this renewal.",
                onSubmit = { v -> vm.act({ api.commissionAction("reverse", c.id, v) }) }, onDismiss = dismiss)
            "remark" -> FormDialog("Add remark", listOf(Field("remarks", "Remark", initial = c.adminRemarks.orEmpty(), required = true, multiline = true)), "Save",
                onSubmit = { v -> vm.act({ api.commissionAction("remark", c.id, v) }) }, onDismiss = dismiss)
        }
    }
    history?.let { (c, h) ->
        LaunchedEffect(c.id) {
            history = c to try { Load.Data(api.commissionHistory(c.id)) } catch (e: Exception) { Load.Error(e.message ?: "Could not load the history.") }
        }
        AlertDialog(onDismissRequest = { history = null }, title = { Text("History of C-${c.id}") }, confirmButton = { TextButton(onClick = { history = null }) { Text("Close") } },
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

    Page(d?.vendor?.fullName ?: "Vendor", nav, onRefresh = { vm.load(showSpinner = false) }) {
        when (val s = state) {
            is Load.Data -> {
                val v = s.value.vendor; val t = s.value.totals
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(v.fullName, fontWeight = FontWeight.Bold); SmallText("@${v.username} · ${v.phone ?: ""}") }
                                StatusChip(v.status)
                            }
                            InfoRow("Commission", pct(v.commissionPercent), C.Brand, bold = true)
                            InfoRow("Referral code", v.referralCode)
                            InfoRow("Vendor since", date(v.convertedAt))
                            InfoRow("Owner account", v.ownerStatus)
                            if (!v.remarks.isNullOrBlank()) InfoRow("Remarks", v.remarks)
                            ActionRow {
                                Button(onClick = { edit = true }, enabled = !busy) { Text("Edit % / status") }
                                OutlinedButton(onClick = { nav.navigate("owner/${v.ownerId}") }) { Text("Owner details") }
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Referrals", t.referrals.toString(), C.Brand, Modifier.weight(1f))
                        StatTile("Paid renewals", t.paidRenewals.toString(), C.Info, Modifier.weight(1f))
                        StatTile("Earned", money(t.total), C.Success, Modifier.weight(1f))
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Pending", money(t.pending), C.Warning, Modifier.weight(1f))
                        StatTile("To pay", money(t.approved), C.Info, Modifier.weight(1f))
                        StatTile("Paid", money(t.paid), C.Success, Modifier.weight(1f))
                    }
                }
                item { Chips(VTab.entries.map { it.name to it.label }, tab.name) { tab = VTab.valueOf(it) } }
                when (tab) {
                    VTab.PAYOUT -> {
                        if (s.value.unpaidMonths.isEmpty()) item { Empty("Nothing to pay. Every commission is paid or reversed.") }
                        s.value.unpaidMonths.forEach { m ->
                            item(key = "m${m.month}") {
                                AppCard {
                                    Column {
                                        Text(monthName(m.month), fontWeight = FontWeight.Bold)
                                        InfoRow("Approved, to pay", "${m.approvedN} · ${money(m.approvedAmount)}", C.Info, bold = true)
                                        InfoRow("Not yet approved", "${m.pendingN} · ${money(m.pendingAmount)}", C.Warning)
                                        ActionRow { GreenButton("Record payout", enabled = !busy) { payMonth = m } }
                                    }
                                }
                            }
                        }
                    }
                    VTab.COMMISSIONS -> {
                        if (s.value.commissions.isEmpty()) item { Empty("No commission yet.") }
                        s.value.commissions.forEach { c ->
                            item(key = "c${c.id}") {
                                AppCard {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) { Text(c.referredName ?: "—", fontWeight = FontWeight.SemiBold); SmallText("C-${c.id} · ${c.invoiceNumber ?: ""} · ${date(c.paymentConfirmedAt)}") }
                                            StatusChip(c.status, c.statusLabel)
                                        }
                                        InfoRow("Renewal", money(c.renewalAmount) + if (c.eligibleAmount != c.renewalAmount) " (eligible ${money(c.eligibleAmount)})" else "")
                                        InfoRow("Commission", "${money(c.commissionAmount)} · ${pct(c.commissionPercent)}", bold = true)
                                        if (c.status == "paid" || c.paidAt != null) InfoRow("Paid", date(c.paidAt) + (c.payoutReference?.let { " · $it" } ?: ""), C.Success)
                                        if (!c.reversalReason.isNullOrBlank()) InfoRow("Reversed", c.reversalReason, C.Danger)
                                        if (!c.adminRemarks.isNullOrBlank()) InfoRow("Remarks", c.adminRemarks)
                                        HorizontalDivider(Modifier.padding(vertical = 6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            if (c.status == "pending") TextButton(onClick = { cAction = "approve" to c }, enabled = !busy) { Text("Approve") }
                                            if (c.status == "pending" || c.status == "approved") TextButton(onClick = { cAction = "mark_paid" to c }, enabled = !busy) { Text("Mark paid") }
                                            if (c.status != "reversed") TextButton(onClick = { cAction = "reverse" to c }, enabled = !busy) { Text("Reverse", color = C.Danger) }
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
                            item(key = "r${r.ownerId}") {
                                AppCard(onClick = { nav.navigate("owner/${r.ownerId}") }) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Column(Modifier.weight(1f)) { Text(r.ownerName, fontWeight = FontWeight.SemiBold); SmallText("Referred ${date(r.referralDate)}" + if (r.referralType == "owner") " (before becoming a vendor)" else "") }
                                            StatusChip(r.commissionStatus, r.commissionLabel)
                                        }
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
                                            Text(date(p.paidAt), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                            Text(money(p.amount), fontWeight = FontWeight.Bold, color = C.Success)
                                        }
                                        InfoRow("UTR / reference", p.payoutReference)
                                        InfoRow("Entries", p.entries.toString())
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
