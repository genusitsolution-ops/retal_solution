package com.genusit.prms.ui.owner

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.genusit.prms.R
import com.genusit.prms.domain.model.HistoryInvoice
import com.genusit.prms.domain.model.TenantDocType
import com.genusit.prms.domain.util.Money
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.EmptyState
import com.genusit.prms.ui.common.IconBadge
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import java.io.File

/* ═════════════════════════ documents card (tenant detail) ═════════════════════════ */

/**
 * Aadhaar and PAN documents of a tenant: view what is on the server, or upload / replace it
 * (the same files the web panel shows). Uploading asks for the tenant's consent first.
 */
@Composable
fun TenantDocumentsCard(
    hasAadhaar: Boolean,
    hasPan: Boolean,
    uploadState: ActionState<TenantDocType>,
    onView: (TenantDocType) -> Unit,
    newCaptureTarget: () -> Pair<File, Uri>,
    onUpload: (type: TenantDocType, uri: Uri, consent: Boolean, captureFile: File?) -> Unit,
) {
    var uploadType by rememberSaveable { mutableStateOf<TenantDocType?>(null) }
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Badge, Palette.Accent)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.tenant_documents), style = MaterialTheme.typography.titleMedium)
            }
            DocumentRow(stringResource(R.string.doc_aadhaar), hasAadhaar, uploadState is ActionState.Running,
                onView = { onView(TenantDocType.AADHAAR) }, onUpload = { uploadType = TenantDocType.AADHAAR })
            HorizontalDivider()
            DocumentRow(stringResource(R.string.doc_pan), hasPan, uploadState is ActionState.Running,
                onView = { onView(TenantDocType.PAN) }, onUpload = { uploadType = TenantDocType.PAN })
            if (uploadState is ActionState.Running) Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Brand)
                Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.doc_uploading), style = MaterialTheme.typography.labelMedium)
            }
            if (uploadState is ActionState.Done) Text(stringResource(R.string.doc_uploaded_ok), color = Palette.Success, style = MaterialTheme.typography.labelMedium)
            ActionError(uploadState)
        }
    }
    uploadType?.let { t ->
        DocumentUploadDialog(t, newCaptureTarget, onDismiss = { uploadType = null }, onPicked = { uri, consent, file ->
            uploadType = null; onUpload(t, uri, consent, file)
        })
    }
}

@Composable
private fun DocumentRow(label: String, uploaded: Boolean, busy: Boolean, onView: () -> Unit, onUpload: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StatusChip(if (uploaded) "active" else "none", stringResource(if (uploaded) R.string.uploaded else R.string.not_uploaded))
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (uploaded) OutlinedButton(onClick = onView, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Filled.Visibility, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.view))
            }
            OutlinedButton(onClick = onUpload, enabled = !busy, modifier = Modifier.heightIn(min = 44.dp)) {
                Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp))
                Text(stringResource(if (uploaded) R.string.doc_replace else R.string.doc_upload))
            }
        }
    }
}

/** Consent first, then camera or gallery. The photo is compressed on the phone before it is sent. */
@Composable
private fun DocumentUploadDialog(
    type: TenantDocType,
    newCaptureTarget: () -> Pair<File, Uri>,
    onDismiss: () -> Unit,
    onPicked: (uri: Uri, consent: Boolean, captureFile: File?) -> Unit,
) {
    var consent by rememberSaveable { mutableStateOf(false) }
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUri by rememberSaveable { mutableStateOf<String?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = pendingPath?.let(::File); val uri = pendingUri?.let(Uri::parse)
        pendingPath = null; pendingUri = null
        if (ok && uri != null) onPicked(uri, consent, file) else file?.delete()
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onPicked(uri, consent, null)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (type == TenantDocType.PAN) R.string.doc_upload_pan else R.string.doc_upload_aadhaar)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.doc_upload_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = consent, onCheckedChange = { consent = it })
                    Text(stringResource(R.string.doc_consent), style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = consent, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                        val (file, uri) = newCaptureTarget()
                        pendingPath = file.absolutePath; pendingUri = uri.toString()
                        camera.launch(uri)
                    }) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_camera)) }
                    OutlinedButton(enabled = consent, modifier = Modifier.weight(1f).heightIn(min = 48.dp), onClick = {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.doc_gallery)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/* ═════════════════════════ document viewer ═════════════════════════ */

@Composable
fun TenantDocumentScreen(onBack: () -> Unit) {
    val model: TenantDocumentViewModel = hiltViewModel()
    val state by model.document.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val title = stringResource(if (model.type == TenantDocType.PAN) R.string.doc_pan else R.string.doc_aadhaar)
    ScreenScaffold(title, onBack = onBack) { pad ->
        StateContent(state, model.document::refresh, Modifier.padding(pad)) { doc ->
            when (doc) {
                is DocumentView.Image -> Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                    Image(doc.bitmap.asImageBitmap(), contentDescription = title, contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface))
                }
                is DocumentView.Pdf -> {
                    val open = {
                        val i = Intent(Intent.ACTION_VIEW).setDataAndType(doc.uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { ctx.startActivity(i) } catch (e: ActivityNotFoundException) {
                            Toast.makeText(ctx, ctx.getString(R.string.doc_no_pdf_app), Toast.LENGTH_LONG).show()
                        }
                    }
                    LaunchedEffect(doc) { open() }
                    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        IconBadge(Icons.Filled.PictureAsPdf, Palette.Danger)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.doc_pdf_hint), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = open) { Text(stringResource(R.string.doc_open_pdf)) }
                    }
                }
            }
        }
    }
}

/* ═════════════════════════ full history ═════════════════════════ */

@Composable
fun TenantHistoryScreen(onBack: () -> Unit, openInvoice: (Int) -> Unit, openReceipt: (String) -> Unit) {
    val model: TenantHistoryViewModel = hiltViewModel()
    val state by model.history.state.collectAsStateWithLifecycle()
    ScreenScaffold(stringResource(R.string.full_history), onBack = onBack) { pad ->
        StateContent(state, model.history::refresh, Modifier.padding(pad)) { h ->
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 32.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text(h.tenant.fullName, style = MaterialTheme.typography.titleLarge)
                }
                item {
                    AppCard {
                        Column {
                            Text(stringResource(R.string.financial_summary), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            LabelValue(stringResource(R.string.total_billed), Money.format(h.summary.totalBilled))
                            LabelValue(stringResource(R.string.total_paid), Money.format(h.summary.totalPaid), Palette.Success, bold = true)
                            LabelValue(stringResource(R.string.outstanding), Money.format(h.summary.totalOutstanding),
                                if (h.summary.totalOutstanding > 0) Palette.Danger else Palette.Success, bold = true)
                            LabelValue(stringResource(R.string.invoices), "${h.summary.invoiceCount} · ${stringResource(R.string.st_paid)} ${h.summary.paidCount} · ${stringResource(R.string.st_pending)} ${h.summary.unpaidCount}")
                            LabelValue(stringResource(R.string.late_payments), h.summary.latePayments.toString(), if (h.summary.latePayments > 0) Palette.Warning else androidx.compose.ui.graphics.Color.Unspecified)
                        }
                    }
                }
                item {
                    AppCard {
                        Column {
                            Text(stringResource(R.string.security_deposit), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            LabelValue(stringResource(R.string.deposit_agreed), Money.format(h.deposits.totalAgreed))
                            LabelValue(stringResource(R.string.deposit_received), Money.format(h.deposits.totalCollected), Palette.Success)
                            LabelValue(stringResource(R.string.deposit_refunded), Money.format(h.deposits.totalRefunded))
                            LabelValue(stringResource(R.string.deposit_pending), Money.format(h.deposits.totalPending), if (h.deposits.totalPending > 0) Palette.Warning else androidx.compose.ui.graphics.Color.Unspecified)
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.tenancy_history)) }
                if (h.allocations.isEmpty()) item { EmptyState(stringResource(R.string.no_allocations), Icons.Filled.History) }
                items(h.allocations, key = { "a${it.id}" }) { a ->
                    AppCard {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(listOfNotNull(a.propertyCode, a.city).joinToString(", "), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                StatusChip(a.status, a.statusLabel)
                            }
                            LabelValue(stringResource(R.string.move_in), dateText(a.allocationDate))
                            LabelValue(stringResource(R.string.move_out), if (a.deallocationDate != null) dateText(a.deallocationDate) else "—")
                            LabelValue(stringResource(R.string.tenancy_days), a.tenancyDays.toString())
                            LabelValue(stringResource(R.string.rent_per_month), Money.format(a.baseRent))
                            LabelValue(stringResource(R.string.deposit_agreed), Money.format(a.depositAgreed))
                            if (a.deallocationDate != null) {
                                LabelValue(stringResource(R.string.settlement), Money.format(a.settlementAmount))
                                if (!a.settlementNotes.isNullOrBlank()) Text(a.settlementNotes!!, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.payment_history)) }
                if (h.payments.isEmpty()) item { EmptyState(stringResource(R.string.no_payments), Icons.Filled.CreditCard) }
                items(h.payments, key = { "p${it.id}" }) { p ->
                    AppCard(onClick = if (p.receiptNumber.isNotBlank()) ({ openReceipt(p.receiptNumber) }) else null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(p.receiptNumber, fontWeight = FontWeight.SemiBold)
                                Text(listOfNotNull(dateText(p.paymentDate), p.periodLabel, p.paymentMethodLabel, p.referenceNumber?.takeIf { it.isNotBlank() }).joinToString(" · "),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(Money.format(p.amount), color = Palette.Success, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                item { SectionHeader(stringResource(R.string.all_invoices)) }
                if (h.invoices.isEmpty()) item { EmptyState(stringResource(R.string.no_invoices), Icons.Filled.Badge) }
                items(h.invoices, key = { "i${it.id}" }) { inv -> HistoryInvoiceRow(inv) { openInvoice(inv.id) } }
            }
        }
    }
}

@Composable
private fun HistoryInvoiceRow(inv: HistoryInvoice, onClick: () -> Unit) {
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(inv.invoiceNumber, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(inv.periodLabel, inv.propertyCode).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Money.format(inv.totalAmount), fontWeight = FontWeight.Bold)
                if (inv.balanceAmount > 0) Text(Money.format(inv.balanceAmount), color = Palette.Danger, style = MaterialTheme.typography.labelMedium)
                StatusChip(inv.status, inv.statusLabel)
            }
        }
    }
}
