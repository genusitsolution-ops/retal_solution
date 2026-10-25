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
}

/** Add (id = 0) or edit a tenant. */
@HiltViewModel
class TenantFormViewModel @Inject constructor(
    handle: SavedStateHandle,
    getEditable: GetEditableTenantUseCase,
    private val saveTenant: SaveTenantUseCase,
    private val uploadDocument: UploadTenantDocumentUseCase,
    private val scanner: DocumentScanner,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val id = handle.intArg()
    /** Existing values for the edit form (not loaded when adding). */
    val existing = loadable(autoLoad = id != 0) { getEditable(id) }
    val saveAction = action<Unit>()

    private val _scan = MutableStateFlow<ScanState>(ScanState.Idle)
    val scan: StateFlow<ScanState> = _scan.asStateFlow()
    /** Compressed photo of the last document read (kept in the private cache only). */
    private var documentImage: File? = null

    private var documentType: DocumentType? = null
    private var documentUploadFailed = false

    /**
     * Saves the tenant, then stores the photo of the document that was read (the tenant's consent was
     * given before the photo could be taken) — like choosing the Aadhaar file in the web form.
     * A failed photo upload never undoes the save; the owner is told and can upload it from the tenant page.
     */
    fun save(input: TenantInput) = saveAction.run(onSuccess = {
        sendEvent(if (documentUploadFailed) ScreenEvent.SavedDocumentFailed else ScreenEvent.Saved)
    }) {
        val tenantId = saveTenant(id, input)
        val image = documentImage
        val type = documentType
        documentUploadFailed = false
        if (image != null && type != null && tenantId > 0) {
            try {
                uploadDocument(tenantId, if (type == DocumentType.PAN) TenantDocType.PAN else TenantDocType.AADHAAR, image, "image/jpeg", consent = true)
                scanner.discard(image); documentImage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                documentUploadFailed = true
            }
        }
    }

    fun newCaptureTarget(): Pair<File, Uri> = scanner.newCaptureTarget()

    /** OCR runs off the main thread; [captureFile] (the raw camera photo) is deleted afterwards. */
    fun read(source: Uri, type: DocumentType, captureFile: File? = null) {
        if (_scan.value is ScanState.Reading) return
        _scan.value = ScanState.Reading
        viewModelScope.launch {
            _scan.value = try {
                val r = scanner.scan(source, type)
                scanner.discard(documentImage)
                documentImage = r.image
                documentType = type
                if (r.fields.isEmpty) ScanState.Unreadable else ScanState.Found(type, r.fields)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ScanState.Unreadable
            } finally {
                scanner.discard(captureFile)
            }
        }
    }

    fun dismissScan() { _scan.value = ScanState.Idle }

    override fun onCleared() {
        // identity-document photos never outlive the form
        scanner.discard(documentImage)
        super.onCleared()
    }
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
    getPayments: GetOwnerPaymentsUseCase,
    expiry: SessionExpiryHandler,
) : BaseViewModel(expiry) {
    val payments = loadable { getPayments() }
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
    fun delete() = deleteAction.run(onSuccess = { sendEvent(ScreenEvent.Saved) }) { deleteProperty(id) }
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
