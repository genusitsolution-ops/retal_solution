package com.genusit.prms.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController

@Composable
fun OwnersScreen(nav: NavHostController) {
    val api = appApi(); val session = appSession()
    var filter by rememberSaveable { mutableStateOf("") }
    var typed by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val vm = viewModel(key = "owners-$filter-$query") { ScreenModel(session) { api.owners(query, filter) } }
    val state by vm.state.collectAsStateWithLifecycle()
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }
    val counts = (state as? Load.Data)?.value?.counts.orEmpty()

    Page("Owners", onRefresh = { vm.load(showSpinner = false) }, header = {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(typed, { typed = it }, singleLine = true, placeholder = { Text("Search name, username, phone, email") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = { if (typed != query) TextButton(onClick = { query = typed.trim() }) { Text("Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { query = typed.trim() }),
                modifier = Modifier.fillMaxWidth())
            Chips(listOf("" to "All (${counts["all"] ?: 0})", "active" to "Active (${counts["active"] ?: 0})",
                "inactive" to "Inactive (${counts["inactive"] ?: 0})", "suspended" to "Suspended (${counts["suspended"] ?: 0})"), filter) { filter = it }
        }
    }) {
        when (val s = state) {
            is Load.Data -> {
                if (s.value.owners.isEmpty()) item { Empty("No owner found.") }
                s.value.owners.forEach { o ->
                    item(key = o.id) {
                        AppCard(onClick = { nav.navigate("owner/${o.id}") }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(o.fullName, fontWeight = FontWeight.SemiBold)
                                        SmallText("@${o.username}" + (o.businessName?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
                                    }
                                    if (o.isVendor) { Icon(Icons.Filled.Storefront, "Vendor", tint = C.Success); Spacer(Modifier.width(6.dp)) }
                                    StatusChip(if (o.signupStatus == "pending") "pending" else o.status, if (o.signupStatus == "pending") "Awaiting approval" else null)
                                }
                                SmallText(listOfNotNull(o.phone, o.email).joinToString(" · "))
                                SmallText("Plan: ${o.packageName ?: "—"} · ${o.properties} properties · ${o.tenants} tenants · joined ${date(o.createdAt)}")
                            }
                        }
                    }
                }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}

@Composable
fun OwnerDetailScreen(nav: NavHostController, id: Int) {
    val api = appApi(); val session = appSession()
    val ctx = LocalContext.current
    val vm = viewModel(key = "owner-$id") { ScreenModel(session) { api.owner(id) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var changeStatus by remember { mutableStateOf(false) }
    var convert by remember { mutableStateOf(false) }
    Messages(vm.messages)
    val o = (state as? Load.Data)?.value

    if (changeStatus && o != null) {
        FormDialog("Change status of ${o.fullName}", emptyList(), "Save",
            intro = "Inactive or Suspended owners are signed out at once and cannot sign in until made Active again. When they try, they see a message asking them to contact you.",
            options = Triple("Status", o.status, listOf("active" to "Active", "inactive" to "Inactive", "suspended" to "Suspended")),
            onSubmit = { v -> val st = v["_choice"]!!; if (st != o.status) vm.act({ api.setOwnerStatus(o.id, st) }) }, onDismiss = { changeStatus = false })
    }
    if (convert && o != null) {
        FormDialog("Convert ${o.fullName} to vendor", listOf(
            Field("percent", "Commission %", required = true, keyboard = KeyboardType.Decimal, hint = "e.g. 10"),
            Field("remarks", "Remarks", multiline = true)), "Convert",
            intro = "The owner keeps their account and data. They earn this % of every renewal paid by owners who register with their referral code.",
            options = Triple("Vendor status", "active", listOf("active" to "Active", "inactive" to "Inactive")),
            onSubmit = { v -> vm.act({ api.convertToVendor(o.id, v["percent"]!!, v["_choice"]!!, v["remarks"]!!) }) }, onDismiss = { convert = false })
    }

    Page(o?.fullName ?: "Owner", nav, onRefresh = { vm.load(showSpinner = false) }) {
        when (val s = state) {
            is Load.Data -> {
                val d = s.value
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(d.fullName, fontWeight = FontWeight.Bold); SmallText("@${d.username}") }
                                StatusChip(if (d.signupStatus == "pending") "pending" else d.status, if (d.signupStatus == "pending") "Awaiting approval" else null)
                            }
                            InfoRow("Business", d.businessName)
                            InfoRow("Phone", d.phone)
                            InfoRow("Email", d.email)
                            InfoRow("Address", d.address)
                            InfoRow("Joined", date(d.createdAt))
                            InfoRow("Last sign-in", dateTime(d.lastLogin))
                            InfoRow("Referral code", d.referralCode)
                            ActionRow {
                                d.phone?.takeIf { it.isNotBlank() }?.let { p -> OutlinedButton(onClick = { dial(ctx, p) }) { Icon(Icons.Filled.Call, null); Spacer(Modifier.width(4.dp)); Text("Call") } }
                                d.email?.takeIf { it.isNotBlank() }?.let { e -> OutlinedButton(onClick = { email(ctx, e) }) { Icon(Icons.Filled.Email, null); Spacer(Modifier.width(4.dp)); Text("Email") } }
                            }
                            ActionRow {
                                if (d.signupStatus == "pending") SmallText("Waiting for approval — approve or reject it under Approvals.", C.Warning)
                                else androidx.compose.material3.Button(onClick = { changeStatus = true }, enabled = !busy) { Text("Change status") }
                            }
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Subscription")
                            InfoRow("Plan", d.subscription.packageName ?: d.packageName)
                            InfoRow("State", when (d.subscription.state) {
                                    "active" -> "Active"; "trial" -> "Free trial"; "grace" -> "Grace period (expired, short extra time)"
                                    "expired" -> "Expired"; "none" -> "No active plan"; else -> d.subscription.state.replaceFirstChar { it.uppercase() }
                                },
                                statusColors(if (d.subscription.restricted) "expired" else "active").first)
                            InfoRow("Valid until", date(d.subscription.endDate) + if (d.subscription.endDate != null) " · ${d.subscription.daysLeft} days left" else "")
                            InfoRow("Usage", "${d.properties} properties · ${d.tenants} tenants · ${d.employees} employees")
                        }
                    }
                }
                d.signup?.let { sg -> item {
                    AppCard {
                        Column {
                            SectionTitle("App registration")
                            InfoRow("Status", sg.status.replaceFirstChar { it.uppercase() })
                            InfoRow("Mode", if (sg.mode == "instant") "Active immediately" else "After approval")
                            InfoRow("Registered", dateTime(sg.createdAt))
                            InfoRow("Code used", sg.referralCode)
                            if (!sg.notes.isNullOrBlank()) InfoRow("Notes", sg.notes)
                        }
                    }
                } }
                d.referredBy?.let { r -> item {
                    AppCard(onClick = { nav.navigate("owner/${r.ownerId}") }) {
                        Column { SectionTitle("Referred by"); InfoRow("Owner", r.name, C.Brand, bold = true); InfoRow("Date", date(r.date)); InfoRow("Referral", r.status) }
                    }
                } }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Vendor")
                            val v = d.vendor
                            if (v != null) {
                                InfoRow("Status", v.status.replaceFirstChar { it.uppercase() }, statusColors(v.status).first, bold = true)
                                InfoRow("Commission", pct(v.commissionPercent))
                                InfoRow("Referrals", v.totals.referrals.toString())
                                InfoRow("Earned", money(v.totals.total))
                                InfoRow("Pending / approved", money(v.totals.pending) + " / " + money(v.totals.approved))
                                InfoRow("Paid", money(v.totals.paid), C.Success)
                                ActionRow { androidx.compose.material3.Button(onClick = { nav.navigate("vendor/${v.id}") }) { Text("Open vendor") } }
                            } else {
                                SmallText("Not a vendor. A vendor earns commission on renewals of owners who register with their referral code.")
                                ActionRow {
                                    androidx.compose.material3.Button(onClick = { convert = true }, enabled = !busy && d.signupStatus != "pending") {
                                        Icon(Icons.Filled.Storefront, null); Spacer(Modifier.width(6.dp)); Text("Convert to vendor")
                                    }
                                }
                            }
                        }
                    }
                }
                if (d.recentPayments.isNotEmpty()) {
                    item { SectionTitle("Recent payments") }
                    d.recentPayments.forEach { p ->
                        item(key = "p${p.id}") {
                            AppCard(onClick = { nav.navigate("payment/${p.id}") }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(p.requestNo ?: "#${p.id}", fontWeight = FontWeight.SemiBold); SmallText("UTR ${p.utr ?: "—"} · ${dateTime(p.createdAt)}") }
                                    Column(horizontalAlignment = Alignment.End) { Text(money(p.amount), fontWeight = FontWeight.Bold); StatusChip(p.status) }
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
