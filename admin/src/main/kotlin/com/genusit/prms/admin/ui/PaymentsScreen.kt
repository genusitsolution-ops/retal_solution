package com.genusit.prms.admin.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.genusit.prms.admin.net.Payment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun PaymentsScreen(nav: NavHostController) {
    val api = appApi(); val session = appSession()
    var filter by rememberSaveable { mutableStateOf("pending") }
    val vm = viewModel(key = "payments-$filter") { ScreenModel(session) { api.payments(filter) } }
    val state by vm.state.collectAsStateWithLifecycle()
    Messages(vm.messages)
    LaunchedEffect(Unit) { vm.load(showSpinner = false) }
    val counts = (state as? Load.Data)?.value?.counts.orEmpty()

    Page("Payment verification", onRefresh = { vm.load(showSpinner = false) }, header = {
        Column(Modifier.padding(start = 14.dp, end = 14.dp, top = 10.dp)) {
            Chips(listOf("pending" to "To verify (${counts["pending"] ?: 0})", "approved" to "Approved (${counts["approved"] ?: 0})",
                "rejected" to "Rejected (${counts["rejected"] ?: 0})", "all" to "All"), filter) { filter = it }
        }
    }) {
        when (val s = state) {
            is Load.Data -> {
                if (s.value.payments.isEmpty()) item { Empty(if (filter == "pending") "No payment is waiting for verification." else "Nothing here.") }
                s.value.payments.forEach { p -> item(key = p.id) { PaymentCard(p) { nav.navigate("payment/${p.id}") } } }
            }
            else -> item { LoadContent(s, { vm.load() }) {} }
        }
    }
}

@Composable
private fun PaymentCard(p: Payment, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.owner.name, fontWeight = FontWeight.SemiBold)
                    SmallText("${p.requestNo} · ${p.invoice.requestedPackage ?: "—"} · ${dateTime(p.createdAt)}")
                }
                Column(horizontalAlignment = Alignment.End) { Text(money(p.amount), fontWeight = FontWeight.Bold, color = C.BrandDark); StatusChip(p.status) }
            }
            SmallText("UTR ${p.utr} · paid on ${date(p.paidOn)} · " + if (p.method == "gateway") "Online payment" else "UPI")
            val warn = warnings(p)
            if (warn.isNotEmpty()) SmallText("⚠ " + warn.joinToString(" · "), C.Danger)
        }
    }
}

/** Points the admin should check before approving. */
private fun warnings(p: Payment): List<String> = buildList {
    if (p.checks.shortPaid) add("paid less than the invoice (${money(p.invoiceTotal)})")
    else if (p.checks.amountDiffers) add("amount differs from the invoice (${money(p.invoiceTotal)})")
    if (p.checks.sameUtrCount > 0) add("UTR used in ${p.checks.sameUtrCount} other request(s)")
    p.checks.ocr?.let { o ->
        if (o.utrMatches == false) add("UTR on screenshot differs (${o.utr})")
        if (o.amountMatches == false) add("amount on screenshot differs (${o.amount?.let { money(it) }})")
        if (!o.status.isNullOrBlank() && o.status != "success") add("screenshot shows ${o.status}")
    }
    if (!p.hasScreenshot) add("no screenshot")
}

@Composable
fun PaymentDetailScreen(nav: NavHostController, id: Int) {
    val api = appApi(); val session = appSession()
    val vm = viewModel(key = "payment-$id") { ScreenModel(session) { api.payment(id) } }
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    var approve by remember { mutableStateOf(false) }
    var reject by remember { mutableStateOf(false) }
    var remarks by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<Load<ImageBitmap?>?>(null) }
    var fullScreen by remember { mutableStateOf(false) }
    Messages(vm.messages)
    val p = (state as? Load.Data)?.value

    LaunchedEffect(p?.id, p?.hasScreenshot) {
        if (p != null && p.hasScreenshot && shot == null) {
            shot = Load.Loading
            shot = try {
                val (bytes, type) = api.screenshot(p.id)
                if (type.startsWith("image/")) Load.Data(withContext(Dispatchers.Default) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() })
                else Load.Error("The proof is a PDF. Open Renewal Requests on the website to view it.")
            } catch (e: Exception) { Load.Error(e.message ?: "Could not load the screenshot.") }
        }
    }

    if (approve && p != null) {
        val checks = mutableListOf(Triple("verified", "I found this payment of ${money(p.amount)} (UTR ${p.utr}) in the bank / UPI statement", false))
        if (p.checks.shortPaid) checks += Triple("accept", "Accept the difference: paid ${money(p.amount)}, invoice ${money(p.invoiceTotal)}", false)
        FormDialog("Approve payment?", listOf(Field("note", "Note for the record", multiline = true)), "Approve",
            intro = "Approving marks invoice ${p.invoice.number ?: ""} paid and activates the ${p.invoice.requestedPackage ?: "requested"} plan for ${p.owner.name}. A screenshot alone is not proof.",
            checks = checks, requiredChecks = if (p.checks.shortPaid) setOf("verified", "accept") else setOf("verified"),
            onSubmit = { v -> vm.act({ api.approvePayment(p.id, v["note"]!!, v["accept"] == "1") }) }, onDismiss = { approve = false })
    }
    if (reject && p != null) {
        FormDialog("Reject payment?", listOf(Field("reason", "Reason (sent to the owner)", required = true, multiline = true, hint = "e.g. Not found in the bank statement")),
            "Reject", danger = true, onSubmit = { v -> vm.act({ api.rejectPayment(p.id, v["reason"]!!) }) }, onDismiss = { reject = false })
    }
    if (remarks && p != null) {
        FormDialog("Internal remarks", listOf(Field("remarks", "Remarks (not shown to the owner)", initial = p.adminRemarks.orEmpty(), multiline = true)), "Save",
            onSubmit = { v -> vm.act({ api.paymentRemarks(p.id, v["remarks"]!!) }) }, onDismiss = { remarks = false })
    }
    val img = (shot as? Load.Data)?.value
    if (fullScreen && img != null) ZoomImage(img) { fullScreen = false }

    Page(p?.requestNo ?: "Payment", nav, onRefresh = { vm.load(showSpinner = false) }) {
        when (val s = state) {
            is Load.Data -> {
                val d = s.value
                item {
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) { Text(money(d.amount), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); SmallText("Claimed by the owner") }
                                StatusChip(d.status)
                            }
                            InfoRow("Invoice total", money(d.invoiceTotal), if (d.checks.amountDiffers) C.Danger else Color.Unspecified, bold = d.checks.amountDiffers)
                            InfoRow("UTR", d.utr, bold = true)
                            InfoRow("Paid on", date(d.paidOn))
                            InfoRow("Method", if (d.method == "gateway") "Online payment" + (d.gatewayStatus?.let { " ($it)" } ?: "") else "UPI")
                            InfoRow("Submitted", dateTime(d.createdAt) + " · " + (if (d.source == "app") "app" else "web"))
                            if (!d.ownerNote.isNullOrBlank()) InfoRow("Owner's note", d.ownerNote)
                        }
                    }
                }
                item {
                    AppCard {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            SectionTitle("Checks")
                            val w = warnings(d)
                            if (w.isEmpty()) CheckLine(true, "Amount matches the invoice; no other request uses this UTR")
                            w.forEach { CheckLine(false, it.replaceFirstChar { c -> c.uppercase() }) }
                            d.checks.ocr?.let { o ->
                                SmallText("Read from the screenshot by the owner's app" + (o.app?.let { " (${it.uppercase()})" } ?: "") + ":")
                                if (o.utr != null) CheckLine(o.utrMatches == true, "UTR on screenshot: ${o.utr}")
                                if (o.amount != null) CheckLine(o.amountMatches == true, "Amount on screenshot: ${money(o.amount)}")
                                if (o.status != null) CheckLine(o.status == "success", "Status on screenshot: ${o.status}")
                            }
                            SmallText("Not proof of payment — always check your bank / UPI statement for this UTR and amount.", C.Warning)
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Screenshot")
                            when (val sh = shot) {
                                null -> SmallText(if (d.hasScreenshot) "Loading…" else "No screenshot was attached.")
                                is Load.Loading -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                                is Load.Error -> SmallText(sh.message, C.Danger)
                                is Load.Data -> sh.value?.let { bmp ->
                                    Image(bmp, "Payment screenshot", contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).clip(RoundedCornerShape(10.dp)))
                                    TextButton(onClick = { fullScreen = true }) { Text("Open full screen (pinch to zoom)") }
                                } ?: SmallText("The file could not be shown as an image.", C.Danger)
                            }
                        }
                    }
                }
                item {
                    AppCard(onClick = { nav.navigate("owner/${d.owner.id}") }) {
                        Column {
                            SectionTitle("Owner")
                            InfoRow("Name", d.owner.name, C.Brand, bold = true)
                            InfoRow("Username", d.owner.username)
                            InfoRow("Phone", d.owner.phone)
                            InfoRow("Email", d.owner.email)
                            InfoRow("Account", d.owner.status)
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Plan requested")
                            InfoRow("Invoice", d.invoice.number)
                            InfoRow("Current plan", d.invoice.currentPackage)
                            InfoRow("Requested", d.invoice.requestedPackage, bold = true)
                            InfoRow("Duration", "${d.invoice.durationDays} days")
                            InfoRow("Invoice status", d.invoice.status)
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            SectionTitle("Review")
                            if (d.status != "pending") {
                                InfoRow("Reviewed", dateTime(d.reviewedAt) + (d.reviewerName?.let { " · $it" } ?: ""))
                                if (!d.reviewNote.isNullOrBlank()) InfoRow(if (d.status == "rejected") "Reason" else "Note", d.reviewNote)
                            }
                            InfoRow("Internal remarks", d.adminRemarks)
                            ActionRow { OutlinedButton(onClick = { remarks = true }, enabled = !busy) { Text("Edit remarks") } }
                            if (d.status == "pending") ActionRow {
                                GreenButton("Approve", enabled = !busy, modifier = Modifier.weight(1f)) { approve = true }
                                RedOutlineButton("Reject", enabled = !busy, modifier = Modifier.weight(1f)) { reject = true }
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
private fun CheckLine(ok: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null, tint = if (ok) C.Success else C.Danger, modifier = Modifier.padding(end = 6.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = if (ok) Color.Unspecified else C.Danger)
    }
}

@Composable
private fun ZoomImage(img: ImageBitmap, onClose: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            Image(img, "Payment screenshot", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ -> scale = (scale * zoom).coerceIn(1f, 6f); ox += pan.x; oy += pan.y }
                }.graphicsLayer(scaleX = scale, scaleY = scale, translationX = ox, translationY = oy))
            TextButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) { Text("Close", color = Color.White) }
        }
    }
}
