package com.genusit.prms.admin.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.Signup

/** Owner registrations from the mobile app: approve (activates + starts the trial) or reject with a reason. */
@Composable
fun SignupsScreen(nav: NavHostController) {
    val api = appApi(); val session = appSession()
    var filter by rememberSaveable { mutableStateOf("pending") }
    val vm = viewModel(key = "signups-$filter") { ScreenModel(session) { api.signups(filter) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var approve by remember { mutableStateOf<Signup?>(null) }
    var reject by remember { mutableStateOf<Signup?>(null) }
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }

    approve?.let { s ->
        ConfirmDialog("Approve ${s.fullName}?", "The owner account becomes active, the free trial starts and the referral (if any) is recorded. The owner can sign in right away.",
            "Approve", onConfirm = { vm.act({ api.approveSignup(s.id) }) }, onDismiss = { approve = null })
    }
    reject?.let { s ->
        FormDialog("Reject ${s.fullName}?", listOf(Field("reason", "Reason", required = true, multiline = true, hint = "e.g. Duplicate or incomplete details")),
            "Reject", danger = true, intro = "The owner cannot sign in. When they try, they are told to contact you.",
            onSubmit = { v -> vm.act({ api.rejectSignup(s.id, v["reason"]!!) }) }, onDismiss = { reject = null })
    }

    val counts = (state as? Load.Data)?.value?.counts.orEmpty()
    Page("Owner approvals", onRefresh = { vm.load(showSpinner = false) }, header = {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            Chips(listOf("pending" to "Pending (${counts["pending"] ?: 0})", "approved" to "Approved (${counts["approved"] ?: 0})",
                "rejected" to "Rejected (${counts["rejected"] ?: 0})", "all" to "All"), filter) { filter = it }
        }
    }) {
        when (val s = state) {
            is Load.Data -> {
                val d = s.value
                if (d.mode != "approval") item {
                    AppCard { SmallText(if (d.mode == "instant") "Self-registration is set to “active immediately”: new owners do not need approval."
                                        else "Self-registration is off on the server.", C.Warning) }
                }
                if (d.signups.isEmpty()) item { Empty(if (filter == "pending") "No owner is waiting for approval." else "Nothing here.") }
                d.signups.forEach { r ->
                    item(key = r.id) {
                        AppCard(onClick = { nav.navigate("owner/${r.ownerId}") }) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(r.fullName, fontWeight = FontWeight.SemiBold)
                                        SmallText("@${r.username}")
                                    }
                                    StatusChip(r.status)
                                }
                                InfoRow("Phone", r.phone)
                                InfoRow("Email", r.email)
                                InfoRow("Registered", dateTime(r.createdAt))
                                InfoRow("Referral", r.referralCode?.let { c -> c + (r.referrerName?.let { " · $it" } ?: "") })
                                if (r.status != "pending") {
                                    InfoRow("Reviewed", dateTime(r.reviewedAt) + (r.reviewerName?.let { " · $it" } ?: ""))
                                    if (!r.notes.isNullOrBlank()) InfoRow(if (r.status == "rejected") "Reason" else "Notes", r.notes)
                                }
                                if (r.status == "pending") ActionRow {
                                    GreenButton("Approve", enabled = !busy, modifier = Modifier.weight(1f)) { approve = r }
                                    RedOutlineButton("Reject", enabled = !busy, modifier = Modifier.weight(1f)) { reject = r }
                                } else Row { TextButton(onClick = { nav.navigate("owner/${r.ownerId}") }) { Text("Owner details") } }
                            }
                        }
                    }
                }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}
