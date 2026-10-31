package com.genusit.prms.ui.owner

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import com.genusit.prms.domain.util.DocumentFields
import com.genusit.prms.domain.util.DocumentType
import com.genusit.prms.platform.DocumentScanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import com.genusit.prms.domain.model.CollectionsReport
import com.genusit.prms.domain.model.EmployeeInput
import com.genusit.prms.domain.model.MeterReading
import com.genusit.prms.domain.model.NewAllocation
import com.genusit.prms.domain.model.NewCharge
import com.genusit.prms.domain.model.NewProperty
import com.genusit.prms.domain.model.PendingReport
import com.genusit.prms.domain.model.RevenueRow
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.TenantInput
import com.genusit.prms.domain.model.TenantDocType
import com.genusit.prms.domain.model.SaveTenantResult
import com.genusit.prms.domain.model.DocumentFile
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.*
import com.genusit.prms.domain.util.Dates
import com.genusit.prms.ui.common.BaseViewModel
import com.genusit.prms.ui.common.ScreenEvent
import com.genusit.prms.ui.common.SessionExpiryHandler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/** Navigation argument of the current destination ("id" for owner/tenant/{id} etc.). */
internal fun SavedStateHandle.intArg(key: String = "id"): Int = get<Int>(key) ?: 0

/** Name of the signed-in user for the "Hello, …" headers. */
internal fun SessionRepository.userNameIn(vm: BaseViewModel): StateFlow<String> =
    session.map { it?.name.orEmpty() }.stateIn(vm.viewModelScope, SharingStarted.Eagerly, session.value?.name.orEmpty())

/* ═════════════════════════ home ═════════════════════════ */

@HiltViewModel
class OwnerHomeViewModel @Inject constructor(
    getDashboard: GetOwnerDashboardUseCase,
    session: SessionRepository,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val dashboard = loadable { getDashboard() }
    val userName: StateFlow<String> = session.userNameIn(this)
}

/* ═════════════════════════ tenants ═════════════════════════ */

@HiltViewModel
class OwnerTenantsViewModel @Inject constructor(
    getTenants: GetOwnerTenantsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val tenants = loadable { getTenants() }
}

@HiltViewModel
class OwnerTenantDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    getTenant: GetOwnerTenantUseCase,
    private val deleteTenant: DeleteTenantUseCase,
    private val uploadDocument: UploadTenantDocumentUseCase,
    private val scanner: DocumentScanner,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val tenant = loadable { getTenant(id) }
    val deleteAction = action<Unit>()
    /** Upload / replace an ID document (same storage as the web "Edit Tenant" form). */
    val uploadAction = action<TenantDocType>()

    fun delete() = deleteAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { deleteTenant(id) }

    fun newCaptureTarget(): Pair<File, Uri> = scanner.newCaptureTarget()

    /** Compresses the photo on the phone, uploads it, then reloads the tenant. Photos never outlive the upload. */
    fun upload(type: TenantDocType, source: Uri, consent: Boolean, captureFile: File?) =
        uploadAction.run(onSuccess = { tenant.refresh() }) {
            var compressed: File? = null
            try {
                compressed = scanner.compress(source)
                uploadDocument(id, type, compressed, "image/jpeg", consent)
                type
            } finally {
                scanner.discard(compressed); scanner.discard(captureFile)
            }
        }
}

/** Full history (same records as the web "Full History" page). */
@HiltViewModel
class TenantHistoryViewModel @Inject constructor(
    handle: SavedStateHandle,
    getHistory: GetTenantHistoryUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val history = loadable { getHistory(id) }
}

/** What the document viewer shows: an image in the app, or a PDF handed to a PDF viewer. */
sealed interface DocumentView {
    class Image(val bitmap: android.graphics.Bitmap) : DocumentView
    class Pdf(val uri: Uri) : DocumentView
}

/** Views a tenant's Aadhaar / PAN document fetched with the owner's token; the cached copy is removed on exit. */
@HiltViewModel
class TenantDocumentViewModel @Inject constructor(
    handle: SavedStateHandle,
    getDocument: GetTenantDocumentUseCase,
    private val scanner: DocumentScanner,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val type: TenantDocType = TenantDocType.entries.firstOrNull { it.apiValue == handle.get<String>("type") } ?: TenantDocType.AADHAAR
    private var cached: File? = null
    val document = loadable {
        val f: DocumentFile = getDocument(id, type)
        if (f.isPdf) {
            val (file, uri) = scanner.cacheForViewing(f.bytes, "pdf")
            scanner.discard(cached); cached = file
            DocumentView.Pdf(uri)
        } else {
            val bmp = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                android.graphics.BitmapFactory.decodeByteArray(f.bytes, 0, f.bytes.size)
            } ?: throw com.genusit.prms.domain.error.ApiException(com.genusit.prms.domain.error.ApiException.BAD_RESPONSE, "image", 200)
            DocumentView.Image(bmp)
        }
    }

    override fun onCleared() { scanner.discard(cached); super.onCleared() }
}

/** Reading an ID document photo in the tenant form. */
sealed interface ScanState {
    data object Idle : ScanState
    data object Reading : ScanState
    /** Suggestions to review; nothing is copied into the form until the user confirms. */
    data class Found(val type: DocumentType, val fields: DocumentFields) : ScanState
    data object Unreadable : ScanState
    /** The file could not be opened as an image (nothing is uploaded). */
    data object InvalidImage : ScanState
}

/** Add (id = 0) or edit a tenant. */
@HiltViewModel
class TenantFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    getEditable: GetEditableTenantUseCase,
    private val saveTenant: SaveTenantUseCase,
    private val uploadDraft: UploadDraftDocumentUseCase,
    private val deleteDraft: DeleteDraftDocumentUseCase,
    private val scanner: DocumentScanner,
    private val uploadPhoto: com.genusit.prms.domain.usecase.UploadProfilePhotoUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id = handle.intArg()

    /** New tenant's profile photo: compressed on the phone now, uploaded once the tenant is saved. */
    data class PendingPhoto(val file: File, val consent: Boolean)
    private val _photo = MutableStateFlow<PendingPhoto?>(null)
    val photo: StateFlow<PendingPhoto?> = _photo.asStateFlow()
    private val _photoBusy = MutableStateFlow(false)
    val photoBusy: StateFlow<Boolean> = _photoBusy.asStateFlow()

    fun pickPhoto(source: Uri, captureFile: File?, consent: Boolean) {
        if (_photoBusy.value) return
        _photoBusy.value = true
        viewModelScope.launch {
            try {
                val f = scanner.compress(source, com.genusit.prms.ui.common.PhotoSpec.MAX_SIDE, com.genusit.prms.ui.common.PhotoSpec.QUALITY)
                _photo.value?.let { scanner.discard(it.file) }
                _photo.value = PendingPhoto(f, consent)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // unreadable image: nothing is kept
            } finally {
                scanner.discard(captureFile); _photoBusy.value = false
            }
        }
    }

    fun clearPhoto() { _photo.value?.let { scanner.discard(it.file) }; _photo.value = null }

    /** Existing values for the edit form (not loaded when adding). */
    val existing = loadable(autoLoad = id != 0) { getEditable(id) }
    val saveAction = action<SaveTenantResult>()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()

    /** Upload state of the Aadhaar / PAN photo (uploaded as a draft as soon as it is captured). */
    private val _uploads = MutableStateFlow<Map<DocumentType, DocUpload>>(emptyMap())
    val uploads: StateFlow<Map<DocumentType, DocUpload>> = _uploads.asStateFlow()
    private val uploadJobs = mutableMapOf<DocumentType, kotlinx.coroutines.Job>()

    /** True while a photo is still being sent — the form waits so the tenant never misses its document. */
    val uploading: Boolean get() = _uploads.value.values.any { it is DocUpload.Uploading }

    /**
     * Saves the tenant with the uploaded drafts attached. The idempotency key of [saveAction] is reused when
     * the owner retries after a failure, so a retried "add" never creates a second tenant.
     */
    fun save(input: TenantInput) {
        if (uploading) return
        val tokens = _uploads.value.mapValues { (it.value as? DocUpload.Uploaded)?.token }
        val withDocs = input.copy(aadhaarDraft = tokens[DocumentType.AADHAAR], panDraft = tokens[DocumentType.PAN])
        saveAction.run(onSuccess = { r ->
            _uploads.value.values.forEach { scanner.discard(it.preview) }
            _uploads.value = emptyMap()
            val pending = _photo.value
            if (pending == null) {
                sendEvent(if (r.documentsAttached) ScreenEvent.Saved else ScreenEvent.SavedDocumentFailed)
            } else viewModelScope.launch {
                // the tenant exists now: attach the photo (a failure keeps the tenant and says so)
                val ok = try { uploadPhoto(com.genusit.prms.domain.model.PhotoSubject.TENANT, r.id, pending.file, pending.consent); true }
                catch (e: CancellationException) { throw e } catch (e: Exception) { expiry.onError(e); false }
                clearPhoto()
                sendEvent(when { !r.documentsAttached -> ScreenEvent.SavedDocumentFailed; !ok -> ScreenEvent.SavedPhotoFailed; else -> ScreenEvent.Saved })
            }
        }) { key -> saveTenant(id, withDocs, key) }
    }

    fun newCaptureTarget(): Pair<File, Uri> = scanner.newCaptureTarget()

    /**
     * Photo captured or chosen: compress + straighten it, read it with on-device OCR (suggestions to review)
     * and upload it right away. [captureFile] (the raw camera photo) is deleted afterwards.
     * The capture buttons are enabled only after the tenant's consent is ticked.
     */
    fun read(source: Uri, type: DocumentType, captureFile: File? = null) {
        if (_scan.value is ScanState.Reading) return
        _scan.value = ScanState.Reading
        viewModelScope.launch {
            _scan.value = try {
                val r = scanner.scan(source, type)
                startUpload(type, r.image)
                if (r.fields.isEmpty) ScanState.Unreadable else ScanState.Found(type, r.fields)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanState.InvalidImage
            } finally {
                scanner.discard(captureFile)
            }
        }
    }

    /** Sends the photo again after a failed upload. */
    fun retryUpload(type: DocumentType) {
        val failed = _uploads.value[type] as? DocUpload.Failed ?: return
        startUpload(type, failed.preview)
    }

    /** Removes the photo from the form (and the draft from the server). */
    fun removeDocument(type: DocumentType) {
        uploadJobs.remove(type)?.cancel()
        val current = _uploads.value[type] ?: return
        _uploads.value = _uploads.value - type
        scanner.discard(current.preview)
        (current as? DocUpload.Uploaded)?.let { up ->
            viewModelScope.launch { runCatching { deleteDraft(type.toDocType(), up.token) } }
        }
    }

    private fun startUpload(type: DocumentType, image: File) {
        val previous = _uploads.value[type]
        val replaceToken = (previous as? DocUpload.Uploaded)?.token
        if (previous != null && previous.preview != image) scanner.discard(previous.preview)
        uploadJobs.remove(type)?.cancel()
        _uploads.value = _uploads.value + (type to DocUpload.Uploading(image))
        uploadJobs[type] = viewModelScope.launch {
            val next = try {
                // success is shown only after the server confirms it stored the file
                DocUpload.Uploaded(image, uploadDraft(type.toDocType(), image, consent = true, replaceToken = replaceToken))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                expiry.onError(e)
                DocUpload.Failed(image, com.genusit.prms.ui.common.UiError.from(e))
            }
            _uploads.value = _uploads.value + (type to next)
        }
    }

    fun dismissScan() { _scan.value = ScanState.Idle }

    override fun onCleared() {
        // identity-document photos never outlive the form (unused server drafts expire after 24 h)
        _uploads.value.values.forEach { scanner.discard(it.preview) }
        _photo.value?.let { scanner.discard(it.file) }
        super.onCleared()
    }
}

private fun DocumentType.toDocType(): TenantDocType = if (this == DocumentType.PAN) TenantDocType.PAN else TenantDocType.AADHAAR

/** Upload state of one document photo in the tenant form. */
sealed interface DocUpload {
    val preview: File
    data class Uploading(override val preview: File) : DocUpload
    data class Uploaded(override val preview: File, val token: String) : DocUpload
    data class Failed(override val preview: File, val error: com.genusit.prms.ui.common.UiError) : DocUpload
}

/* ═════════════════════════ invoices & payments ═════════════════════════ */

@HiltViewModel
class OwnerInvoicesViewModel @Inject constructor(
    getInvoices: GetOwnerInvoicesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val invoices = loadable { getInvoices() }
}

@HiltViewModel
class InvoiceDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    getInvoice: GetOwnerInvoiceUseCase,
    private val cancelInvoice: CancelInvoiceUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val invoice = loadable { getInvoice(id) }
    val cancelAction = action<Unit>()

    fun cancel(reason: String) = cancelAction.run(onSuccess = { cancelAction.reset(); invoice.refresh() }) { cancelInvoice(id, reason) }
}

@HiltViewModel
class RecordPaymentViewModel @Inject constructor(
    handle: SavedStateHandle,
    getInvoice: GetOwnerInvoiceUseCase,
    getMeta: GetMetaUseCase,
    private val recordPayment: RecordPaymentUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val invoice = loadable { getInvoice(id) }
    /** Payment modes with server-localized labels. */
    val meta = loadable { getMeta() }
    val payAction = action<com.genusit.prms.domain.model.PaymentResult>()

    fun pay(invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String) =
        payAction.run(onSuccess = { sendEvent(ScreenEvent.ReceiptReady(it.receiptNumber)) }) { key ->
            recordPayment(invoiceId, amount, mode, date, reference, remarks, key)
        }
}

@HiltViewModel
class OwnerPaymentsViewModel @Inject constructor(
    handle: SavedStateHandle,
    getPayments: GetOwnerPaymentsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    /** "month" = exactly the dashboard's "collected this month" (1st … last day of this month). */
    private val _period = MutableStateFlow(PaymentPeriod.from(handle.get<String>("period")))
    val period: StateFlow<PaymentPeriod> = _period.asStateFlow()
    val payments = paramLoadable<PaymentPeriod, com.genusit.prms.domain.model.OwnerPayments> { p ->
        val (from, to) = p.range()
        getPayments(from, to)
    }.also { it.load(_period.value) }

    fun select(p: PaymentPeriod) { _period.value = p; payments.load(p) }
}

enum class PaymentPeriod(val key: String) {
    MONTH("month"), LAST_3_MONTHS("3m"), YEAR("year");
    fun range(): Pair<String, String> = when (this) {
        MONTH -> Dates.thisMonth()
        LAST_3_MONTHS -> Dates.lastMonths(3)
        YEAR -> Dates.thisYear()
    }
    companion object { fun from(key: String?) = entries.firstOrNull { it.key == key } ?: LAST_3_MONTHS }
}

/* ═════════════════════════ rooms ═════════════════════════ */

@HiltViewModel
class PropertiesViewModel @Inject constructor(
    getProperties: GetPropertiesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val properties = loadable { getProperties() }
}

@HiltViewModel
class AddPropertyViewModel @Inject constructor(
    private val addProperty: AddPropertyUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val saveAction = action<com.genusit.prms.domain.model.CreatedId>()

    fun save(p: NewProperty) = saveAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { addProperty(p) }
}

@HiltViewModel
class EditPropertyViewModel @Inject constructor(
    handle: SavedStateHandle,
    getProperty: GetPropertyUseCase,
    private val updateProperty: UpdatePropertyUseCase,
    private val deleteProperty: DeletePropertyUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val property = loadable { getProperty(id) }
    val saveAction = action<Unit>()
    val deleteAction = action<Unit>()

    fun save(p: NewProperty, status: String?) = saveAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { updateProperty(id, p, status) }
    /** The server re-checks occupancy and history inside a transaction; its reason is shown when it refuses. */
    fun delete() = deleteAction.run(onSuccess = { sendEvent(ScreenEvent.Deleted) }) { deleteProperty(id) }
}

/* ═════════════════════════ charges ═════════════════════════ */

@HiltViewModel
class ChargesViewModel @Inject constructor(
    getCharges: GetChargesUseCase,
    private val setChargeStatus: SetChargeStatusUseCase,
    private val addCharge: AddChargeUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val charges = loadable { getCharges(includeInactive = true) }
    val toggle = action<Unit>()
    val addAction = action<com.genusit.prms.domain.model.CreatedId>()

    fun setActive(id: Int, active: Boolean) =
        toggle.run(onSuccess = { charges.refresh(); toggle.reset() }) { setChargeStatus(id, active) }

    fun add(c: NewCharge) =
        addAction.run(onSuccess = { addAction.reset(); charges.refresh(); sendEvent(ScreenEvent.ChargeAdded) }) { addCharge(c) }
}

/* ═════════════════════════ allocations ═════════════════════════ */

@HiltViewModel
class AllocationsViewModel @Inject constructor(
    getAllocations: GetAllocationsUseCase,
    private val collectDeposit: CollectDepositUseCase,
    private val closeAllocation: CloseAllocationUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    /** Keyed by the status filter ("active" | "closed"). */
    val allocations = paramLoadable<String, List<com.genusit.prms.domain.model.Allocation>> { status -> getAllocations(status) }
    val deposit = action<Unit>()
    val closeAction = action<com.genusit.prms.domain.model.AllocationClosed>()

    fun collectDepositFor(id: Int, amount: Double) =
        deposit.run(onSuccess = { allocations.refresh() }) { collectDeposit(id, amount); Unit }

    fun close(id: Int, notes: String) =
        closeAction.run(onSuccess = { allocations.refresh() }) { closeAllocation(id, notes) }
}

@HiltViewModel
class NewAllocationViewModel @Inject constructor(
    getProperties: GetPropertiesUseCase,
    getTenants: GetOwnerTenantsUseCase,
    getCharges: GetChargesUseCase,
    private val allocateRoom: AllocateRoomUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val properties = loadable { getProperties() }
    val tenants = loadable { getTenants() }
    val charges = loadable { getCharges() }
    val allocateAction = action<Unit>()

    fun allocate(a: NewAllocation) = allocateAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { allocateRoom(a); Unit }
}

/* ═════════════════════════ generate bill ═════════════════════════ */

data class BillingPeriod(val month: Int, val year: Int)

@HiltViewModel
class GenerateInvoiceViewModel @Inject constructor(
    private val handle: SavedStateHandle,
    getContext: GetInvoiceContextUseCase,
    private val generateInvoice: GenerateInvoiceUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val allocationId = handle.intArg()
    private val today = Dates.today()
    /** Selected billing month/year; survives process death through the SavedStateHandle. */
    val month: StateFlow<Int> = handle.getStateFlow("month", today.substring(5, 7).toInt())
    val year: StateFlow<Int> = handle.getStateFlow("year", today.substring(0, 4).toInt())
    val context = paramLoadable<BillingPeriod, com.genusit.prms.domain.model.InvoiceContext> { p -> getContext(allocationId, p.month, p.year) }
    val generateAction = action<com.genusit.prms.domain.model.GeneratedInvoice>()

    init { context.load(BillingPeriod(month.value, year.value)) }

    fun setPeriod(month: Int, year: Int) {
        handle["month"] = month
        handle["year"] = year
        context.load(BillingPeriod(month, year))
    }

    fun generate(fixedChargeIds: List<Int>, readings: List<MeterReading>) {
        val m = month.value; val y = year.value
        generateAction.run(onSuccess = { sendEvent(ScreenEvent.InvoiceCreated(it.id)) }) {
            generateInvoice(allocationId, m, y, fixedChargeIds, readings)
        }
    }

    /** Saves the bill exactly as previewed: charges on the tenancy, skipped meters and not-yet-added charges. */
    fun generate(ctx: com.genusit.prms.domain.model.InvoiceContext, sel: com.genusit.prms.domain.usecase.BillSelection) {
        val m = month.value; val y = year.value
        val calc = com.genusit.prms.domain.usecase.BillCalculator
        generateAction.run(onSuccess = { sendEvent(ScreenEvent.InvoiceCreated(it.id)) }) {
            generateInvoice(allocationId, m, y, calc.fixedIds(ctx, sel), calc.readings(ctx, sel), calc.skipped(ctx, sel), calc.extras(ctx, sel))
        }
    }
}

/* ═════════════════════════ collection agents ═════════════════════════ */

@HiltViewModel
class EmployeesViewModel @Inject constructor(
    getEmployees: GetEmployeesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val employees = loadable { getEmployees() }
}

/** Add (id = 0) or edit a collection agent. */
@HiltViewModel
class EmployeeFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    getEmployee: GetEmployeeUseCase,
    private val saveEmployee: SaveEmployeeUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id = handle.intArg()
    val existing = loadable(autoLoad = id != 0) { getEmployee(id) }
    val saveAction = action<Unit>()

    fun save(input: EmployeeInput) = saveAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { saveEmployee(id, input) }
}

@HiltViewModel
class EmployeeDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    getEmployee: GetEmployeeUseCase,
    getProperties: GetPropertiesUseCase,
    private val assignProperties: AssignPropertiesUseCase,
    private val setEmployeeStatus: SetEmployeeStatusUseCase,
    private val deleteEmployee: DeleteEmployeeUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    val employee = loadable { getEmployee(id) }
    val properties = loadable { getProperties() }
    val assignAction = action<Unit>()
    val status = action<Unit>()
    val removeAction = action<com.genusit.prms.domain.model.EmployeeRemoved>()

    fun assign(propertyIds: List<Int>) = assignAction.run(onSuccess = { employee.refresh() }) { assignProperties(id, propertyIds) }
    fun setActive(active: Boolean) = status.run(onSuccess = { employee.refresh() }) { setEmployeeStatus(id, active) }
    fun remove() = removeAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { deleteEmployee(id) }
}

/* ═════════════════════════ complaints ═════════════════════════ */

@HiltViewModel
class OwnerQueriesViewModel @Inject constructor(
    getQueries: GetOwnerQueriesUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val queries = loadable { getQueries() }
}

/** Ticket conversation; the role comes from the signed-in session (owner or tenant). */
@HiltViewModel
class QueryDetailViewModel @Inject constructor(
    handle: SavedStateHandle,
    session: SessionRepository,
    getDetail: GetQueryDetailUseCase,
    private val replyToQuery: ReplyToQueryUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    private val id = handle.intArg()
    private val role: Role = session.session.value?.role ?: Role.TENANT
    val detail = loadable { getDetail(role, id) }
    val reply = action<Unit>()

    /** Same rules as before: a message (owner may add a status), or an owner-only status change. */
    fun send(message: String, newStatus: String?) {
        val hasMessage = message.isNotBlank()
        if (!hasMessage && !(newStatus != null && role == Role.OWNER)) return
        reply.run(onSuccess = { reply.reset(); detail.refresh(); sendEvent(ScreenEvent.ReplySent) }) {
            replyToQuery(role, id, message, newStatus); Unit
        }
    }
}

/* ═════════════════════════ reports, referrals, subscription ═════════════════════════ */

data class DateRange(val from: String, val to: String)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    getCollections: GetCollectionsReportUseCase,
    getRevenue: GetRevenueReportUseCase,
    getPending: GetPendingReportUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val collections = paramLoadable<DateRange, CollectionsReport> { r -> getCollections(r.from, r.to) }
    val revenue = paramLoadable<DateRange, List<RevenueRow>> { r -> getRevenue(r.from, r.to) }
    val pending = paramLoadable<Unit, PendingReport> { getPending() }
}

@HiltViewModel
class ReferralViewModel @Inject constructor(
    getReferrals: GetReferralsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val referrals = loadable { getReferrals() }
}

@HiltViewModel
class SubscriptionViewModel @Inject constructor(
    getSubscription: GetSubscriptionUseCase,
    private val requestRenewalUseCase: RequestRenewalUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val subscription = loadable { getSubscription() }
    val renewal = action<Unit>()

    fun requestRenewal() = renewal.run(onSuccess = { subscription.refresh() }) { requestRenewalUseCase() }
}
