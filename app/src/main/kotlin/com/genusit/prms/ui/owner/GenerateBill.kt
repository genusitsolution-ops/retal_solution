package com.genusit.prms.ui.owner

import android.content.Context
import android.content.Intent
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.Allocation
import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.usecase.GetAllocationsUseCase
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.UiState
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/* ═════════════════════════ dashboard shortcut ═════════════════════════ */

/** Prominent "Generate bill" action on the owner's Home screen. */
@Composable
fun GenerateBillCard(onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.fillMaxWidth().shadow(8.dp, shape, ambientColor = Palette.Accent, spotColor = Palette.Accent).clip(shape)
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF4F46E5), Color(0xFF7C3AED))))
            .clickable(onClick = onClick, role = Role.Button),
    ) {
        Box(Modifier.align(Alignment.CenterEnd).offset(x = 30.dp).size(110.dp).clip(CircleShape).background(Color.White.copy(alpha = .08f)))
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.generate_bill), color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.generate_bill_sub), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.White)
        }
    }
}

/* ═════════════════════════ step 1: choose an occupied room ═════════════════════════ */

@HiltViewModel
class BillRoomsViewModel @Inject constructor(
    getAllocations: GetAllocationsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    /** Only rooms with an active tenant can be billed (the server checks the same). */
    val rooms = loadable { getAllocations("active").filter { it.isActive } }
}

@Composable
fun GenerateBillPickerScreen(onBack: () -> Unit, pick: (allocationId: Int) -> Unit, newAllocation: () -> Unit) {
    val vm = hiltViewModel<BillRoomsViewModel>().rooms
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { if (state !is UiState.Loading) vm.refresh() }
    ScreenScaffold(stringResource(R.string.generate_bill), onBack = onBack) { pad ->
        StateContent(state, vm::refresh, Modifier.padding(pad)) { list ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { StepHeader(1, stringResource(R.string.bill_step_room)) }
                if (list.isEmpty()) {
                    item { EmptyState(stringResource(R.string.bill_no_occupied_rooms), Icons.Filled.MeetingRoom) }
                    item { OutlinedButton(onClick = newAllocation, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.allocate_room)) } }
                }
                items(list, key = { it.id }) { a -> RoomRow(a) { pick(a.id) } }
            }
        }
    }
}

@Composable
private fun RoomRow(a: Allocation, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Filled.MeetingRoom, Palette.Info)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(listOfNotNull(a.propertyCode, a.city).joinToString(", "), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.tenantName ?: "—", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(a.baseRent, decimals = 0) + stringResource(R.string.per_month), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.since_date, dateText(a.allocationDate)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun StepHeader(step: Int, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(Palette.Brand), contentAlignment = Alignment.Center) {
            Text(step.toString(), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/* ═════════════════════════ step 3: preview before saving ═════════════════════════ */

/** One line of the preview (the server recalculates everything when the bill is saved). */
data class PreviewLine(val label: String, val detail: String?, val amount: Double)

@Composable
fun BillPreviewDialog(
    tenant: String, room: String, period: String, lines: List<PreviewLine>, otherOutstanding: Double,
    saving: Boolean, onConfirm: () -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, tint = Palette.Brand) },
        title = { Text(stringResource(R.string.bill_preview)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(tenant, fontWeight = FontWeight.SemiBold)
                Text("$room · $period", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                HorizontalDivider()
                lines.forEach { l ->
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(l.label)
                            l.detail?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Text(Money.format(l.amount), fontWeight = FontWeight.Medium)
                    }
                }
                HorizontalDivider()
                Row {
                    Text(stringResource(R.string.total), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text(Money.format(lines.sumOf { it.amount }), fontWeight = FontWeight.Bold, color = Palette.Brand)
                }
                if (otherOutstanding > 0) Text(stringResource(R.string.bill_other_dues_note, Money.format(otherOutstanding)), color = Palette.Danger, style = MaterialTheme.typography.labelMedium)
                Text(stringResource(R.string.bill_preview_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !saving) { Text(stringResource(R.string.save_bill), fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !saving) { Text(stringResource(R.string.edit)) } },
    )
}

/* ═════════════════════════ share / print a bill ═════════════════════════ */

/** Share and Print (or save as PDF through Android's print dialog) for a saved bill. */
@Composable
fun BillShareActions(inv: InvoiceDetail) {
    val ctx = LocalContext.current
    val months = stringArrayResource(R.array.months).toList()
    val labels = BillLabels(
        bill = stringResource(R.string.invoice), tenant = stringResource(R.string.tenant), room = stringResource(R.string.property),
        period = stringResource(R.string.period), due = stringResource(R.string.due_date), total = stringResource(R.string.total),
        paid = stringResource(R.string.paid), balance = stringResource(R.string.balance), status = stringResource(R.string.status),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = { shareBill(ctx, inv, labels, months) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Share, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.share))
        }
        OutlinedButton(onClick = { printBill(ctx, inv, labels, months) }, modifier = Modifier.weight(1f)) {
            Icon(Icons.Filled.Print, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.print_pdf))
        }
    }
}

data class BillLabels(val bill: String, val tenant: String, val room: String, val period: String, val due: String,
                      val total: String, val paid: String, val balance: String, val status: String)

private fun dateOf(v: String?, months: List<String>) = com.genusit.prms.domain.util.Dates.display(v, months)

fun billText(inv: InvoiceDetail, l: BillLabels, months: List<String>): String = buildString {
    appendLine("${l.bill} ${inv.invoiceNumber}")
    inv.tenantName?.let { appendLine("${l.tenant}: $it") }
    appendLine("${l.room}: ${listOfNotNull(inv.propertyCode, inv.city).joinToString(", ")}")
    inv.periodLabel?.let { appendLine("${l.period}: $it") }
    inv.dueDate?.let { appendLine("${l.due}: ${dateOf(it, months)}") }
    appendLine()
    inv.items.forEach { appendLine("${it.description}: ${Money.format(it.amount)}") }
    appendLine()
    appendLine("${l.total}: ${Money.format(inv.totalAmount)}")
    if (inv.paidAmount > 0) appendLine("${l.paid}: ${Money.format(inv.paidAmount)}")
    appendLine("${l.balance}: ${Money.format(inv.balanceAmount)}")
    inv.statusLabel?.let { appendLine("${l.status}: $it") }
    inv.ownerName?.let { append("— $it") }
}

private fun shareBill(ctx: Context, inv: InvoiceDetail, l: BillLabels, months: List<String>) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, billText(inv, l, months))
    ctx.startActivity(Intent.createChooser(send, null))
}

private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

private var printing: WebView? = null

/** Renders the bill as simple HTML and hands it to Android printing (printer or "Save as PDF"). */
private fun printBill(ctx: Context, inv: InvoiceDetail, l: BillLabels, months: List<String>) {
    val rows = inv.items.joinToString("") { "<tr><td>${esc(it.description)}</td><td class=r>${esc(Money.format(it.amount))}</td></tr>" }
    val html = """<html><head><meta charset="utf-8"><style>
        body{font-family:sans-serif;color:#0f172a;padding:24px} h1{color:#4f46e5;font-size:22px;margin:0}
        table{width:100%;border-collapse:collapse;margin-top:16px} td{padding:8px;border-bottom:1px solid #e2e8f0} .r{text-align:right}
        .t td{font-weight:bold;border-top:2px solid #0f172a} .m{color:#64748b;font-size:13px}</style></head><body>
        <h1>${esc(l.bill)} ${esc(inv.invoiceNumber)}</h1>
        <p class=m>${esc(inv.ownerName ?: "")}${inv.ownerPhone?.let { " · " + esc(it) } ?: ""}</p>
        <p>${esc(l.tenant)}: <b>${esc(inv.tenantName ?: "")}</b><br>${esc(l.room)}: ${esc(listOfNotNull(inv.propertyCode, inv.city).joinToString(", "))}<br>
        ${esc(l.period)}: ${esc(inv.periodLabel ?: "")}<br>${esc(l.due)}: ${esc(dateOf(inv.dueDate, months))}</p>
        <table>$rows<tr class=t><td>${esc(l.total)}</td><td class=r>${esc(Money.format(inv.totalAmount))}</td></tr>
        <tr><td>${esc(l.paid)}</td><td class=r>${esc(Money.format(inv.paidAmount))}</td></tr>
        <tr><td><b>${esc(l.balance)}</b></td><td class=r><b>${esc(Money.format(inv.balanceAmount))}</b></td></tr></table>
        <p class=m>${esc(l.status)}: ${esc(inv.statusLabel ?: inv.status)}</p></body></html>"""
    val web = WebView(ctx).also { printing = it }   // kept until the print job has the document
    web.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView, url: String?) {
            val pm = ctx.getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(inv.invoiceNumber, view.createPrintDocumentAdapter(inv.invoiceNumber), PrintAttributes.Builder().build())
        }
    }
    web.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
}
