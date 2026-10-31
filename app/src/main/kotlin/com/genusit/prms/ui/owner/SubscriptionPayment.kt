package com.genusit.prms.ui.owner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.genusit.prms.R
import com.genusit.prms.domain.model.GatewayStatus
import com.genusit.prms.domain.model.PaymentClaimsData
import com.genusit.prms.domain.model.PaymentOptions
import com.genusit.prms.domain.model.RenewalPaymentInput
import com.genusit.prms.domain.model.RenewalRequest
import com.genusit.prms.domain.usecase.GetGatewayStatusUseCase
import com.genusit.prms.domain.usecase.GetPaymentOptionsUseCase
import com.genusit.prms.domain.usecase.GetPaymentScreenshotUseCase
import com.genusit.prms.domain.usecase.GetStaticPaymentQrUseCase
import com.genusit.prms.domain.usecase.StartGatewayPaymentUseCase
import com.genusit.prms.domain.usecase.SubmitRenewalPaymentUseCase
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.RenewalRules
import com.genusit.prms.domain.util.UpiQr
import com.genusit.prms.platform.DocumentScanner
import com.genusit.prms.ui.common.ActionError
import com.genusit.prms.ui.common.ActionState
import com.genusit.prms.ui.common.AppCard
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.EventEffect
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.common.LabelValue
import com.genusit.prms.ui.common.PhotoPickerButtons
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.ScreenScaffold
import com.genusit.prms.ui.common.SectionHeader
import com.genusit.prms.ui.common.SessionExpiryHandler
import com.genusit.prms.ui.common.StateContent
import com.genusit.prms.ui.common.StatusChip
import com.genusit.prms.ui.common.SubmitButton
import com.genusit.prms.ui.common.dateText
import com.genusit.prms.ui.theme.Palette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject

/*
 * Pay a subscription invoice (app 1.9). Everything shown — package, duration, tax, final amount, UPI ID and the
 * upi://pay link — comes from the server. The QR code is drawn on the phone from that link. Opening a UPI app does
 * not count as payment: the owner submits the UTR with a screenshot, and the Super Admin verifies it. Online
 * payment opens the gateway's page in the browser; the server confirms the payment with the gateway.
 */

/** Payment screenshot compression (keeps text readable, small upload). */
private const val SHOT_MAX_SIDE = 1600
private const val SHOT_QUALITY = 85

@HiltViewModel
class SubscriptionPaymentViewModel @Inject constructor(
    handle: SavedStateHandle,
    getOptions: GetPaymentOptionsUseCase,
    private val submitPayment: SubmitRenewalPaymentUseCase,
    private val startGateway: StartGatewayPaymentUseCase,
    private val gatewayStatus: GetGatewayStatusUseCase,
    private val getScreenshot: GetPaymentScreenshotUseCase,
    private val getStaticQr: GetStaticPaymentQrUseCase,
    private val scanner: DocumentScanner,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val invoiceId = handle.intArg()
    val options = loadable { getOptions(invoiceId) }
    val submit = action<RenewalRequest?>()
    val gateway = action<String>()

    /** Online payment started from this screen: re-checked with the server when the owner comes back. */
    private val _gatewayId = MutableStateFlow(0)
    private val _gatewayResult = MutableStateFlow<GatewayStatus?>(null)
    val gatewayResult: StateFlow<GatewayStatus?> = _gatewayResult.asStateFlow()
    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    private val _shot = MutableStateFlow<File?>(null)
    val shot: StateFlow<File?> = _shot.asStateFlow()
    private val _shotBusy = MutableStateFlow(false)
    val shotBusy: StateFlow<Boolean> = _shotBusy.asStateFlow()

    fun newCaptureTarget(): Pair<File, Uri> = scanner.newCaptureTarget()

    fun pickScreenshot(source: Uri, captureFile: File?) {
        if (_shotBusy.value) return
        _shotBusy.value = true
        viewModelScope.launch {
            try {
                val f = scanner.compress(source, SHOT_MAX_SIDE, SHOT_QUALITY)
                _shot.value?.let { scanner.discard(it) }
                _shot.value = f
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // unreadable image: nothing kept
            } finally {
                scanner.discard(captureFile); _shotBusy.value = false
            }
        }
    }

    fun clearScreenshot() { _shot.value?.let { scanner.discard(it) }; _shot.value = null }

    fun send(utr: String, paidOn: String, amount: Double, note: String) {
        val f = _shot.value ?: return
        submit.run(onSuccess = { clearScreenshot(); sendEvent(ScreenEvent.Saved) }) {
            submitPayment(RenewalPaymentInput(invoiceId, utr, paidOn, amount, note.ifBlank { null }), f)
        }
    }

    /** Server creates the payment link for the invoice total; the screen opens it in the browser. */
    fun payOnline() = gateway.run(onSuccess = { gateway.reset() }) {
        val g = startGateway(invoiceId)
        _gatewayId.value = g.id
        _gatewayResult.value = null
        g.checkoutUrl.also { sendEvent(ScreenEvent.OpenUrl(it)) }
    }

    /** Called when the owner returns to the app: the server asks the gateway whether the payment went through. */
    fun recheckGateway() {
        val id = _gatewayId.value
        if (id == 0 || _checking.value) return
        _checking.value = true
        viewModelScope.launch {
            try {
                val s = gatewayStatus(id)
                _gatewayResult.value = s
                if (s.status != "created") { _gatewayId.value = 0; options.refresh() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
            } finally { _checking.value = false }
        }
    }

    suspend fun screenshotBytes(requestId: Int): ByteArray? = runCatching { getScreenshot(requestId) }.getOrNull()
    suspend fun staticQrBytes(): ByteArray? = runCatching { getStaticQr() }.getOrNull()

    override fun onCleared() {
        _shot.value?.let { scanner.discard(it) }
        super.onCleared()
    }
}

@Composable
fun SubscriptionPaymentScreen(invoiceId: Int, onBack: () -> Unit, onSubmitted: () -> Unit) {
    val model: SubscriptionPaymentViewModel = hiltViewModel()
    val state by model.options.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    EventEffect(model) {
        when (it) {
            ScreenEvent.Saved -> { Toast.makeText(ctx, ctx.getString(R.string.pay_request_sent), Toast.LENGTH_LONG).show(); onSubmitted() }
            is ScreenEvent.OpenUrl -> openLink(ctx, it.url, R.string.pay_no_browser)
            else -> Unit
        }
    }
    LifecycleResumeEffect(Unit) { model.recheckGateway(); onPauseOrDispose { } }

    ScreenScaffold(stringResource(R.string.pay_title), onBack = onBack) { pad ->
        StateContent(state, model.options::refresh, Modifier.padding(pad)) { d ->
            val p = d.payment
            if (p == null) {
                Column(Modifier.padding(16.dp)) { Text(stringResource(R.string.pay_no_invoice)) }
                return@StateContent
            }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                PaymentBreakdown(p)
                GatewayResultCard(model)
                p.pendingRequest?.let { pr -> NoticeCard(stringResource(R.string.pay_pending_request, pr.requestNo ?: ""), Palette.Info) }
                if (p.pendingRequest == null) p.lastRejection?.let { lr ->
                    NoticeCard(stringResource(R.string.pay_last_rejected, lr.requestNo ?: "", lr.reason ?: "") + " " + stringResource(R.string.pay_resubmit), Palette.Danger)
                }
                if (p.methods.gateway && p.pendingRequest == null) OnlinePayCard(model, p)
                if (p.methods.manualUpi) {
                    UpiCard(model, p)
                    if (p.pendingRequest == null) ManualPaymentForm(model, p)
                }
                if (!p.methods.manualUpi && !p.methods.gateway) NoticeCard(stringResource(R.string.pay_no_method), Palette.Warning)
                RequestsList(model, d)
            }
        }
    }
}

@Composable
fun PaymentBreakdown(p: PaymentOptions) {
    val i = p.invoice
    AppCard {
        Column {
            Text(stringResource(R.string.pay_selected_package), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(i.packageName, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            LabelValue(stringResource(R.string.pay_duration), stringResource(R.string.n_days, i.durationDays))
            LabelValue(stringResource(R.string.pay_period), dateText(i.periodStart) + " – " + dateText(i.periodEnd))
            LabelValue(stringResource(R.string.invoice_no), i.invoiceNumber)
            LabelValue(stringResource(R.string.base_price_incl, i.includedUnits), Money.format(i.baseAmount))
            if (i.extraUnits > 0) LabelValue(stringResource(R.string.extra_rooms_calc, i.extraUnits, Money.format(i.extraUnitPrice)), Money.format(i.extraAmount))
            if (i.discountAmount > 0) LabelValue(stringResource(R.string.discount), "-" + Money.format(i.discountAmount))
            LabelValue("${i.taxLabel} ${trimNum(i.taxPercent)}%", Money.format(i.taxAmount))
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.pay_final_amount), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text(Money.format(i.totalAmount), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Palette.Brand,
                    modifier = Modifier.testTag("pay_total"))
            }
        }
    }
}

@Composable
private fun GatewayResultCard(model: SubscriptionPaymentViewModel) {
    val checking by model.checking.collectAsStateWithLifecycle()
    val r by model.gatewayResult.collectAsStateWithLifecycle()
    if (checking) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Brand); Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.pay_checking))
    }
    val res = r ?: return
    val (text, color) = when {
        res.status == "paid" && res.invoiceStatus == "paid" -> stringResource(R.string.pay_online_done) to Palette.Success
        res.status == "paid" -> stringResource(R.string.pay_online_received) to Palette.Success
        res.status == "created" -> stringResource(R.string.pay_online_not_yet) to Palette.Warning
        else -> stringResource(R.string.pay_online_failed) to Palette.Danger
    }
    NoticeCard(text, color)
}

@Composable
private fun OnlinePayCard(model: SubscriptionPaymentViewModel, p: PaymentOptions) {
    val act by model.gateway.state.collectAsStateWithLifecycle()
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CreditCard, null, tint = Palette.Brand); Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.pay_online_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(stringResource(R.string.pay_online_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ActionError(act)
            Button(onClick = model::payOnline, enabled = act !is ActionState.Running, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                if (act is ActionState.Running) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                else Text(stringResource(R.string.pay_online_button, Money.format(p.invoice.totalAmount)))
            }
        }
    }
}

@Composable
private fun UpiCard(model: SubscriptionPaymentViewModel, p: PaymentOptions) {
    AppCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pay_upi_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth())
            val upi = p.upi
            if (upi != null) {
                UpiQrPanel(upi)
            } else if (p.staticQr) {
                val img by produceState<Bitmap?>(null) { value = model.staticQrBytes()?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
                Box(Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp), contentAlignment = Alignment.Center) {
                    val b = img
                    if (b != null) Image(b.asImageBitmap(), contentDescription = stringResource(R.string.pay_qr_desc, Money.format(p.invoice.totalAmount)), modifier = Modifier.fillMaxSize())
                    else CircularProgressIndicator(color = Palette.Brand)
                }
                Text(stringResource(R.string.pay_exactly, Money.format(p.invoice.totalAmount)), fontWeight = FontWeight.Bold)
                p.payee?.takeIf { it.isNotBlank() }?.let { Text(it) }
            } else {
                Text(stringResource(R.string.pay_no_qr), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            p.instructions?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth()) }
            Text(stringResource(R.string.pay_upi_warning), style = MaterialTheme.typography.labelMedium, color = Palette.Warning)
        }
    }
}

/** QR code (drawn from the server's upi://pay link), payee, UPI ID with copy, "Open UPI app" and the reference. */
@Composable
fun UpiQrPanel(upi: com.genusit.prms.domain.model.UpiDetails) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val qr by produceState<Bitmap?>(null, upi.uri) { value = withContext(Dispatchers.Default) { runCatching { qrBitmap(upi.uri) }.getOrNull() } }
        val desc = stringResource(R.string.pay_qr_desc, Money.format(upi.amount))
        Box(Modifier.size(240.dp).clip(RoundedCornerShape(12.dp)).background(Color.White).padding(8.dp)
            .testTag("upi_qr").semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
            val b = qr
            if (b != null) Image(b.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else CircularProgressIndicator(color = Palette.Brand)
        }
        Text(stringResource(R.string.pay_scan_hint, Money.format(upi.amount)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(upi.payee, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(upi.upiId, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f, fill = false))
            TextButton(onClick = { copy(ctx, upi.upiId) }) { Icon(Icons.Filled.ContentCopy, null); Spacer(Modifier.width(4.dp)); Text(stringResource(R.string.pay_copy_upi)) }
        }
        OutlinedButton(onClick = { openUpiApp(ctx, upi.uri) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.PhoneAndroid, null); Spacer(Modifier.width(6.dp)); Text(stringResource(R.string.pay_open_upi_app))
        }
        Text(stringResource(R.string.pay_reference, upi.reference), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ManualPaymentForm(model: SubscriptionPaymentViewModel, p: PaymentOptions) {
    val act by model.submit.state.collectAsStateWithLifecycle()
    val shot by model.shot.collectAsStateWithLifecycle()
    val shotBusy by model.shotBusy.collectAsStateWithLifecycle()
    var amount by rememberSaveable { mutableStateOf(String.format(java.util.Locale.US, "%.2f", p.invoice.totalAmount)) }
    var utr by rememberSaveable { mutableStateOf("") }
    var paidOn by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var note by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val amt = amount.toDoubleOrNull()
    val problems = RenewalRules.problems(utr, paidOn, amt, shot != null, p.screenshotRequired)
    fun bad(x: RenewalRules.Problem) = tried && x in problems
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.pay_submit_title), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.pay_submit_sub), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FormField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(12) }, stringResource(R.string.pay_amount_paid), bad(RenewalRules.Problem.AMOUNT), KeyboardType.Decimal,
                supporting = when {
                    bad(RenewalRules.Problem.AMOUNT) -> stringResource(R.string.pay_err_amount)
                    RenewalRules.isShort(amt, p.invoice.totalAmount) -> stringResource(R.string.pay_amount_short, Money.format(p.invoice.totalAmount))
                    else -> null
                })
            FormField(utr, { utr = it.take(30) }, stringResource(R.string.pay_utr), bad(RenewalRules.Problem.UTR), autoCorrect = false,
                supporting = stringResource(if (bad(RenewalRules.Problem.UTR)) R.string.pay_err_utr else R.string.pay_utr_hint))
            FormField(paidOn, { paidOn = it.filter { c -> c.isDigit() || c == '-' }.take(10) }, stringResource(R.string.pay_date), bad(RenewalRules.Problem.DATE), KeyboardType.Number,
                supporting = stringResource(if (bad(RenewalRules.Problem.DATE)) R.string.pay_err_date else R.string.pay_date_hint))
            Text(stringResource(if (p.screenshotRequired) R.string.pay_screenshot_required else R.string.pay_screenshot_optional), fontWeight = FontWeight.SemiBold,
                color = if (bad(RenewalRules.Problem.SCREENSHOT)) MaterialTheme.colorScheme.error else Color.Unspecified)
            val f = shot
            if (f != null) {
                val bmp by produceState<Bitmap?>(null, f) { value = withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = 4 }) }.getOrNull() } }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                        bmp?.let { Image(it.asImageBitmap(), contentDescription = stringResource(R.string.pay_screenshot_attached), contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.pay_screenshot_attached), modifier = Modifier.weight(1f))
                    TextButton(onClick = model::clearScreenshot) { Text(stringResource(R.string.photo_remove), color = Palette.Danger) }
                }
            }
            if (shotBusy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Palette.Brand)
            PhotoPickerButtons(consentRequired = false, enabled = !shotBusy && act !is ActionState.Running, newCaptureTarget = model::newCaptureTarget,
                onPicked = { uri, file, _ -> model.pickScreenshot(uri, file) })
            if (bad(RenewalRules.Problem.SCREENSHOT)) Text(stringResource(R.string.pay_err_screenshot), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
            FormField(note, { note = it.take(500) }, stringResource(R.string.pay_remarks), singleLine = false, minLines = 2)
            ActionError(act)
            SubmitButton(stringResource(R.string.pay_submit_button), act) {
                tried = true
                if (problems.isEmpty() && !shotBusy && amt != null) model.send(utr, paidOn, amt, note)
            }
            Text(stringResource(R.string.pay_submit_note), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RequestsList(model: SubscriptionPaymentViewModel, d: PaymentClaimsData) {
    if (d.claims.isEmpty()) return
    var viewing by remember { mutableStateOf<RenewalRequest?>(null) }
    SectionHeader(stringResource(R.string.renewal_requests))
    d.claims.forEach { c -> RenewalRequestCard(c) { viewing = c } }
    viewing?.let { c ->
        val img by produceState<Bitmap?>(null, c.id) { value = model.screenshotBytes(c.id)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) } }
        AlertDialog(onDismissRequest = { viewing = null }, confirmButton = { TextButton(onClick = { viewing = null }) { Text(stringResource(R.string.close)) } },
            title = { Text(c.requestNo ?: "") },
            text = {
                Box(Modifier.fillMaxWidth().heightIn(min = 200.dp), contentAlignment = Alignment.Center) {
                    val b = img
                    if (b != null) Image(b.asImageBitmap(), contentDescription = stringResource(R.string.pay_screenshot), modifier = Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                    else CircularProgressIndicator(color = Palette.Brand)
                }
            })
    }
}

/** One renewal request row: number, package, amount, reference, status (and the reason when rejected). */
@Composable
fun RenewalRequestCard(c: RenewalRequest, onViewScreenshot: (() -> Unit)? = null) {
    AppCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.requestNo ?: ("#" + c.id), fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                    Text(listOfNotNull(c.packageName, dateText(c.createdAt)).joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(Money.format(c.amount), fontWeight = FontWeight.Bold)
                    val (code, label) = when (c.status) {
                        "approved" -> "paid" to stringResource(R.string.req_approved)
                        "rejected" -> "expired" to stringResource(R.string.req_rejected)
                        else -> "pending" to stringResource(R.string.req_pending)
                    }
                    StatusChip(code, label)
                }
            }
            Text((if (c.method == "gateway") stringResource(R.string.pay_method_online) else "UPI") + " · " + c.utr,
                style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (c.status == "rejected" && !c.reviewNote.isNullOrBlank()) Text(stringResource(R.string.req_reason, c.reviewNote!!), color = Palette.Danger, style = MaterialTheme.typography.bodyMedium)
            if (c.hasScreenshot && onViewScreenshot != null) TextButton(onClick = onViewScreenshot) { Text(stringResource(R.string.pay_view_screenshot)) }
        }
    }
}

@Composable
private fun NoticeCard(text: String, color: Color) {
    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(color.copy(alpha = .1f)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Info, null, tint = color); Spacer(Modifier.width(10.dp))
        Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

private fun trimNum(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

/** Black-and-white QR bitmap of the server's UPI link. */
fun qrBitmap(uri: String, size: Int = 600): Bitmap {
    val m = UpiQr.matrix(uri, size)
    val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
    return Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}

private fun copy(ctx: Context, text: String) {
    (ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager)?.setPrimaryClip(ClipData.newPlainText("UPI ID", text))
    Toast.makeText(ctx, ctx.getString(R.string.pay_copied), Toast.LENGTH_SHORT).show()
}

/** Opens Google Pay / PhonePe / Paytm / BHIM … with the amount filled in. Opening the app is not proof of payment. */
private fun openUpiApp(ctx: Context, uri: String) {
    try {
        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse(uri)), ctx.getString(R.string.pay_choose_upi_app)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(R.string.pay_no_upi_app), Toast.LENGTH_LONG).show()
    }
}

private fun openLink(ctx: Context, url: String, errorRes: Int) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (_: android.content.ActivityNotFoundException) {
        Toast.makeText(ctx, ctx.getString(errorRes), Toast.LENGTH_LONG).show()
    }
}
