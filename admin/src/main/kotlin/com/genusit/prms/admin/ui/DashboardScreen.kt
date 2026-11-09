package com.genusit.prms.admin.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.Dashboard

@Composable
fun DashboardScreen(nav: NavHostController, onCounts: (Int, Int) -> Unit, onSignOut: () -> Unit, openTab: (String) -> Unit) {
    val api = appApi(); val session = appSession()
    val vm = viewModel(key = "dashboard") { ScreenModel(session) { api.dashboard() } }
    val state by vm.state.collectAsStateWithLifecycle()
    var askSignOut by remember { mutableStateOf(false) }
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }
    (state as? Load.Data<Dashboard>)?.value?.let { d -> LaunchedEffect(d) { onCounts(d.pendingSignups, d.pendingPayments) } }
    if (askSignOut) ConfirmDialog("Sign out?", "You will need your password to sign in again.", "Sign out", onConfirm = onSignOut, onDismiss = { askSignOut = false })

    Page("Dashboard", onRefresh = { vm.load(showSpinner = false) }, onSignOut = { askSignOut = true }) {
        when (val s = state) {
            is Load.Data -> {
                val d = s.value
                item { Text("Welcome, ${session.adminName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Pending approvals", d.pendingSignups.toString(), if (d.pendingSignups > 0) C.Warning else C.Success, Modifier.weight(1f),
                            Icons.Filled.HourglassTop) { openTab("approvals") }
                        StatTile("Payments to verify", d.pendingPayments.toString(), if (d.pendingPayments > 0) C.Warning else C.Success, Modifier.weight(1f),
                            Icons.Filled.ReceiptLong) { openTab("payments") }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile("Registered owners", d.owners.total.toString(), C.Brand, Modifier.weight(1f), Icons.Filled.People) { openTab("owners") }
                        StatTile("Active vendors", if (d.vendors.ready) "${d.vendors.active} / ${d.vendors.total}" else "—", C.Info, Modifier.weight(1f),
                            Icons.Filled.Storefront) { openTab("vendors") }
                    }
                }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Owners by status")
                            InfoRow("Active", d.owners.active.toString(), C.Success, bold = true)
                            InfoRow("Inactive", d.owners.inactive.toString(), C.Warning)
                            InfoRow("Suspended", d.owners.suspended.toString(), C.Danger)
                            InfoRow("Self-registration", when (d.signupMode) { "approval" -> "On, after approval"; "instant" -> "On, active immediately"; "off" -> "Off"; else -> d.signupMode })
                        }
                    }
                }
                if (d.vendors.ready) item {
                    AppCard {
                        Column {
                            SectionTitle("Vendor commission")
                            InfoRow("Pending approval", money(d.vendors.commissionPending), C.Warning, bold = true)
                            InfoRow("Approved, to pay", money(d.vendors.commissionApproved), C.Info, bold = true)
                            InfoRow("Paid", money(d.vendors.commissionPaid), C.Success)
                        }
                    }
                } else item {
                    AppCard { SmallText("Vendor management is not set up on the server yet (migration 2026_11_011).", C.Warning) }
                }
                if (d.recentSignups.isNotEmpty()) {
                    item { SectionTitle("Waiting for approval") }
                    d.recentSignups.forEach { r ->
                        item(key = "s${r.id}") {
                            AppCard(onClick = { openTab("approvals") }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(r.fullName, fontWeight = FontWeight.SemiBold); SmallText("@${r.username} · ${dateTime(r.createdAt)}") }
                                    StatusChip("pending")
                                }
                            }
                        }
                    }
                }
                if (d.recentPayments.isNotEmpty()) {
                    item { SectionTitle("Payments to verify") }
                    d.recentPayments.forEach { p ->
                        item(key = "p${p.id}") {
                            AppCard(onClick = { nav.navigate("payment/${p.id}") }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) { Text(p.ownerName, fontWeight = FontWeight.SemiBold); SmallText("${p.requestNo ?: "#${p.id}"} · UTR ${p.utr ?: "—"}") }
                                    Text(money(p.amount), fontWeight = FontWeight.Bold, color = C.BrandDark)
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
