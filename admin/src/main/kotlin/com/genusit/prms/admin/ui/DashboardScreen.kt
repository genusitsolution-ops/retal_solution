package com.genusit.prms.admin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddBusiness
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.Dashboard

/** Home: the same live figures as before (admin/dashboard.php), laid out as summary cards. */
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
        item { Hero(session.adminName) }
        when (val s = state) {
            is Load.Data -> {
                val d = s.value
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("Pending approvals", d.pendingSignups.toString(), if (d.pendingSignups > 0) C.Warning else C.Success, Modifier.weight(1f),
                                Icons.Filled.HourglassTop, sub = if (d.pendingSignups > 0) "Needs review" else "All clear") { openTab("approvals") }
                            StatTile("Payments to verify", d.pendingPayments.toString(), if (d.pendingPayments > 0) C.Warning else C.Success, Modifier.weight(1f),
                                Icons.Filled.ReceiptLong, sub = if (d.pendingPayments > 0) "Check UTR" else "All clear") { openTab("payments") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatTile("Registered owners", d.owners.total.toString(), C.Brand, Modifier.weight(1f), Icons.Filled.People,
                                sub = "${d.owners.active} active") { openTab("owners") }
                            StatTile("Active vendors", if (d.vendors.ready) "${d.vendors.active} / ${d.vendors.total}" else "—", C.Teal, Modifier.weight(1f),
                                Icons.Filled.Storefront, sub = if (d.vendors.ready) "Active / total" else "Not set up") { openTab("vendors") }
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (d.vendors.ready) Quick("Add vendor", Icons.Filled.AddBusiness) { nav.navigate("vendor/new") }
                        Quick("Approvals", Icons.Filled.HowToReg) { openTab("approvals") }
                        Quick("Verify payments", Icons.Filled.Payments) { openTab("payments") }
                        Quick("Owners", Icons.Filled.People) { openTab("owners") }
                    }
                }
                item { OwnersByStatus(d) { openTab("owners") } }
                if (d.vendors.ready) item {
                    AppCard {
                        Column {
                            SectionTitle("Vendor commission", action = "Open vendors", onAction = { openTab("vendors") })
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                MiniStat("Pending approval", money(d.vendors.commissionPending), C.Warning, Modifier.weight(1f))
                                MiniStat("Approved, to pay", money(d.vendors.commissionApproved), C.Brand, Modifier.weight(1f))
                                MiniStat("Paid", money(d.vendors.commissionPaid), C.Success, Modifier.weight(1f))
                            }
                        }
                    }
                } else item {
                    AppCard { SmallText("Vendor management is not set up on the server yet (vendor migrations).", C.Warning) }
                }
                if (d.recentSignups.isNotEmpty()) {
                    item { SectionTitle("Waiting for approval", action = "See all", onAction = { openTab("approvals") }) }
                    d.recentSignups.forEach { r ->
                        item(key = "s${r.id}") {
                            AppCard(onClick = { openTab("approvals") }, padding = 14) {
                                PersonRow(r.fullName, "@${r.username} · ${dateTime(r.createdAt)}" + (r.referralCode?.let { " · $it" } ?: "")) { StatusChip("pending") }
                            }
                        }
                    }
                }
                if (d.recentPayments.isNotEmpty()) {
                    item { SectionTitle("Payments to verify", action = "See all", onAction = { openTab("payments") }) }
                    d.recentPayments.forEach { p ->
                        item(key = "p${p.id}") {
                            AppCard(onClick = { nav.navigate("payment/${p.id}") }, padding = 14) {
                                PersonRow(p.ownerName, "${p.requestNo ?: "#${p.id}"} · UTR ${p.utr ?: "—"}") {
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

@Composable
private fun Hero(name: String) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(C.BrandDark, C.Brand))).padding(18.dp)) {
        Column {
            Text("Welcome, $name", color = Color.White, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(2.dp))
            Text(longToday(), color = Color.White.copy(alpha = .8f), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Text("Rental Solution · Super Admin", color = Color.White.copy(alpha = .9f), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .16f)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
    }
}

@Composable
private fun Quick(label: String, icon: ImageVector, onClick: () -> Unit) =
    AssistChip(onClick = onClick, label = { Text(label) }, leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) }, shape = RoundedCornerShape(50),
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White, labelColor = C.BrandDark, leadingIconContentColor = C.Brand))

@Composable
private fun OwnersByStatus(d: Dashboard, open: () -> Unit) {
    val o = d.owners
    AppCard {
        Column {
            SectionTitle("Owners by status", action = "View owners", onAction = open)
            val total = (o.active + o.inactive + o.suspended).coerceAtLeast(1)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(C.Border)) {
                if (o.active > 0) Box(Modifier.weight(o.active.toFloat() / total).fillMaxHeight().background(C.Success))
                if (o.inactive > 0) Box(Modifier.weight(o.inactive.toFloat() / total).fillMaxHeight().background(Color(0xFFF59E0B)))
                if (o.suspended > 0) Box(Modifier.weight(o.suspended.toFloat() / total).fillMaxHeight().background(C.Danger))
            }
            Spacer(Modifier.height(6.dp))
            MetricRow("Active", o.active.toString(), C.Success, dot = C.Success)
            MetricRow("Inactive", o.inactive.toString(), C.Warning, dot = Color(0xFFF59E0B))
            MetricRow("Suspended", o.suspended.toString(), C.Danger, dot = C.Danger)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp).clip(RoundedCornerShape(10.dp)).background(C.Bg).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Self-registration", color = C.Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                when (d.signupMode) {
                    "approval" -> StatusChip("pending", "On, after approval")
                    "instant" -> StatusChip("active", "On, active immediately")
                    "off" -> StatusChip("suspended", "Off")
                    else -> StatusChip(null, d.signupMode)
                }
            }
        }
    }
}
