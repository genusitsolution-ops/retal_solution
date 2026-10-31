package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.*
import com.genusit.prms.domain.repository.AllocationRepository
import com.genusit.prms.domain.repository.BillingRepository
import com.genusit.prms.domain.repository.PropertyRepository
import com.genusit.prms.domain.repository.ReportRepository
import com.genusit.prms.domain.repository.SubscriptionRepository
import com.genusit.prms.domain.repository.TeamRepository
import com.genusit.prms.domain.repository.TenantAdminRepository
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

/** Serializes a request model exactly as app 1.1 did (encodeDefaults, same field names). */
internal fun <T> ApiCaller.body(serializer: KSerializer<T>, value: T): JsonObject =
    json.encodeToJsonElement(serializer, value).jsonObject

/** Blank filters are left out of the query string, as before. */
internal fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

class ReportRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : ReportRepository {
    override suspend fun ownerDashboard(): OwnerDashboard = caller.get(OwnerDashboard.serializer()) { api.ownerDashboard() }
    override suspend fun collections(from: String?, to: String?): CollectionsReport =
        caller.get(CollectionsReport.serializer()) { api.ownerReport("collections", from.orNullIfBlank(), to.orNullIfBlank()) }
    override suspend fun revenue(from: String?, to: String?): List<RevenueRow> =
        caller.get(ListSerializer(RevenueRow.serializer())) { api.ownerReport("revenue", from.orNullIfBlank(), to.orNullIfBlank()) }
    override suspend fun pending(): PendingReport = caller.get(PendingReport.serializer()) { api.ownerReport("pending", null, null) }
    override suspend fun referrals(): ReferralSummary = caller.get(ReferralSummary.serializer()) { api.ownerReferrals() }
}

class PropertyRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : PropertyRepository {
    override suspend fun properties(): List<Property> = caller.get(ListSerializer(Property.serializer())) { api.ownerProperties(null) }
    override suspend fun property(id: Int): Property = caller.get(Property.serializer()) { api.ownerProperties(id) }
    override suspend fun add(p: NewProperty): CreatedId = caller.get(CreatedId.serializer()) { api.addProperty(caller.body(NewProperty.serializer(), p)) }
    override suspend fun update(id: Int, p: NewProperty, status: String?) {
        val body = buildJsonObject {
            put("address", p.address); put("city", p.city); put("state", p.state); put("pincode", p.pincode)
            put("base_rent", p.baseRent); put("deposit_amount", p.depositAmount)
            status?.let { put("status", it) }
        }
        caller.run { api.updateProperty(id, body) }
    }
    override suspend fun delete(id: Int) { caller.run { api.deleteProperty(id) } }
}

class TenantAdminRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : TenantAdminRepository {
    override suspend fun tenants(): List<TenantRow> = caller.get(ListSerializer(TenantRow.serializer())) { api.ownerTenants(null) }
    override suspend fun tenant(id: Int): TenantDetail = caller.get(TenantDetail.serializer()) { api.ownerTenants(id) }
    override suspend fun editable(id: Int): TenantEditable = caller.get(TenantEditable.serializer()) { api.ownerTenants(id) }
    override suspend fun add(t: TenantInput, idempotencyKey: String?): CreatedId =
        caller.get(CreatedId.serializer()) { api.addTenant(caller.body(TenantInput.serializer(), t), idempotencyKey) }
    override suspend fun update(id: Int, t: TenantInput): Map<String, Boolean> {
        val data = caller.call { api.updateTenant(id, caller.body(TenantInput.serializer(), t)) }
        return if (data is kotlinx.serialization.json.JsonObject) caller.decode(CreatedId.serializer(), data).documents else emptyMap()
    }
    override suspend fun delete(id: Int) { caller.run { api.deleteTenant(id) } }
    override suspend fun history(id: Int): TenantHistory = caller.get(TenantHistory.serializer()) { api.tenantHistory(id) }
    override suspend fun document(id: Int, type: TenantDocType): DocumentFile =
        caller.download { api.tenantDocument(id, type.apiValue) }.let { (bytes, mime) -> DocumentFile(bytes, mime) }
    override suspend fun uploadDraft(type: TenantDocType, file: java.io.File, mimeType: String, consent: Boolean, replaceToken: String?): String {
        val text = "text/plain".toMediaType()
        val part = okhttp3.MultipartBody.Part.createFormData("file", "${type.apiValue}.jpg", file.asRequestBody(mimeType.toMediaType()))
        val data = caller.call {
            api.uploadDraftDocument("1".toRequestBody(text), type.apiValue.toRequestBody(text), (if (consent) "1" else "0").toRequestBody(text),
                replaceToken?.toRequestBody(text), part)
        }
        return (data as? kotlinx.serialization.json.JsonObject)?.get("draft_token")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: throw com.genusit.prms.domain.error.ApiException(com.genusit.prms.domain.error.ApiException.BAD_RESPONSE, "draft_token", 200)
    }
    override suspend fun deleteDraft(type: TenantDocType, token: String) { caller.run { api.deleteDraftDocument(token, type.apiValue) } }
    override suspend fun uploadDocument(id: Int, type: TenantDocType, file: java.io.File, mimeType: String, consent: Boolean) {
        val text = "text/plain".toMediaType()
        val part = okhttp3.MultipartBody.Part.createFormData("file", "${type.apiValue}.${if (mimeType.contains("pdf")) "pdf" else if (mimeType.contains("png")) "png" else "jpg"}",
            file.asRequestBody(mimeType.toMediaType()))
        caller.run {
            api.uploadTenantDocument(id.toString().toRequestBody(text), type.apiValue.toRequestBody(text), (if (consent) "1" else "0").toRequestBody(text), part)
        }
    }
}

class BillingRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : BillingRepository {
    override suspend fun invoices(status: String?): List<InvoiceSummary> =
        caller.get(ListSerializer(InvoiceSummary.serializer())) { api.ownerInvoices(status.orNullIfBlank()) }

    override suspend fun invoice(id: Int): InvoiceDetail = caller.get(InvoiceDetail.serializer()) { api.ownerInvoice(id) }

    override suspend fun recordPayment(
        invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult {
        val body = buildJsonObject {
            put("invoice_id", invoiceId); put("amount", amount); put("payment_method", mode)
            put("payment_date", date); put("reference_number", reference); put("remarks", remarks)
        }
        return caller.get(PaymentResult.serializer()) { api.ownerInvoiceAction(body, idempotencyKey) }
    }

    override suspend fun payments(from: String?, to: String?): OwnerPayments =
        caller.get(OwnerPayments.serializer()) { api.ownerPayments(from.orNullIfBlank(), to.orNullIfBlank()) }

    override suspend fun invoiceContext(allocationId: Int, month: Int, year: Int): InvoiceContext =
        caller.get(InvoiceContext.serializer()) { api.invoiceContext("context", allocationId, month, year) }

    override suspend fun generateInvoice(
        allocationId: Int, month: Int, year: Int, fixedChargeIds: List<Int>, readings: List<MeterReading>,
        skipVarIds: List<Int>, extras: List<com.genusit.prms.domain.model.ExtraCharge>,
    ): GeneratedInvoice {
        val body = buildJsonObject {
            put("action", "generate"); put("allocation_id", allocationId); put("billing_month", month); put("billing_year", year)
            put("fixed_charge_ids", buildJsonArray { fixedChargeIds.forEach { add(it) } })
            putJsonObject("var") {
                readings.forEach { r -> putJsonObject(r.tcId.toString()) { put("prev", r.previous); put("curr", r.current) } }
            }
            put("skip_var_ids", buildJsonArray { skipVarIds.forEach { add(it) } })
            // charges not yet on the tenancy: "every month" ones are attached; meter ones carry their reading
            put("attach_ids", buildJsonArray { extras.filter { it.everyMonth }.forEach { add(it.chargeId) } })
            put("adhoc_fixed_ids", buildJsonArray { extras.filter { it.current == null && !it.everyMonth }.forEach { add(it.chargeId) } })
            putJsonObject("adhoc_var") {
                extras.filter { it.current != null }.forEach { e -> putJsonObject(e.chargeId.toString()) { put("prev", e.previous ?: 0.0); put("curr", e.current) } }
            }
        }
        return caller.get(GeneratedInvoice.serializer()) { api.ownerInvoiceAction(body, null) }
    }

    override suspend fun cancelInvoice(invoiceId: Int, reason: String) {
        caller.run { api.ownerInvoiceAction(buildJsonObject { put("action", "cancel"); put("invoice_id", invoiceId); put("reason", reason) }, null) }
    }

    override suspend fun charges(includeInactive: Boolean): List<Charge> =
        caller.get(ListSerializer(Charge.serializer())) { api.ownerCharges(if (includeInactive) 1 else null) }

    override suspend fun setChargeStatus(id: Int, active: Boolean) {
        caller.run { api.updateCharge(id, buildJsonObject { put("status", if (active) "active" else "inactive") }) }
    }

    override suspend fun addCharge(c: NewCharge): CreatedId =
        caller.get(CreatedId.serializer()) { api.addCharge(caller.body(NewCharge.serializer(), c)) }

    override suspend fun deactivateCharge(id: Int) { caller.run { api.deactivateCharge(id) } }
}

class AllocationRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : AllocationRepository {
    override suspend fun allocations(status: String?): List<Allocation> =
        caller.get(ListSerializer(Allocation.serializer())) { api.ownerAllocations(status.orNullIfBlank()) }
    override suspend fun allocate(a: NewAllocation): AllocationCreated =
        caller.get(AllocationCreated.serializer()) { api.allocate(caller.body(NewAllocation.serializer(), a)) }
    override suspend fun close(id: Int, notes: String): AllocationClosed =
        caller.get(AllocationClosed.serializer()) { api.closeAllocation(id, buildJsonObject { put("settlement_notes", notes) }) }
    override suspend fun collectDeposit(id: Int, amount: Double): DepositCollected =
        caller.get(DepositCollected.serializer()) { api.allocationAction(id, buildJsonObject { put("action", "collect_deposit"); put("amount", amount) }) }
}

class TeamRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : TeamRepository {
    override suspend fun employees(): List<EmployeeRow> = caller.get(ListSerializer(EmployeeRow.serializer())) { api.ownerEmployees(null) }
    override suspend fun employee(id: Int): EmployeeDetail = caller.get(EmployeeDetail.serializer()) { api.ownerEmployees(id) }
    override suspend fun add(e: EmployeeInput): CreatedId = caller.get(CreatedId.serializer()) { api.addEmployee(caller.body(EmployeeInput.serializer(), e)) }
    override suspend fun update(id: Int, e: EmployeeInput) { caller.run { api.updateEmployee(id, caller.body(EmployeeInput.serializer(), e)) } }
    override suspend fun setStatus(id: Int, active: Boolean) {
        caller.run { api.updateEmployee(id, buildJsonObject { put("status", if (active) "active" else "inactive") }) }
    }
    override suspend fun delete(id: Int): EmployeeRemoved = caller.get(EmployeeRemoved.serializer()) { api.deleteEmployee(id) }
    override suspend fun assignProperties(employeeId: Int, propertyIds: List<Int>) {
        caller.run { api.employeeAction("assign", employeeId, buildJsonObject { put("property_ids", buildJsonArray { propertyIds.forEach { add(it) } }) }) }
    }
}

class SubscriptionRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : SubscriptionRepository {
    override suspend fun status(): SubscriptionStatus = caller.get(SubscriptionStatus.serializer()) { api.subscription() }
    override suspend fun requestRenewal() { caller.run { api.subscriptionAction(buildJsonObject { put("action", "request_renewal") }) } }

    override suspend fun selectPackage(packageId: Int): SelectedPackage = caller.get(SelectedPackage.serializer()) {
        api.subscriptionAction(buildJsonObject { put("action", "select_package"); put("package_id", packageId) })
    }

    override suspend fun paymentOptions(invoiceId: Int): PaymentClaimsData = caller.get(PaymentClaimsData.serializer()) { api.paymentClaims(invoiceId) }

    override suspend fun submitPayment(input: RenewalPaymentInput, screenshot: java.io.File): RenewalRequest? {
        val text = "text/plain".toMediaType()
        val type = if (screenshot.name.endsWith(".png", true)) "image/png" else "image/jpeg"
        val part = okhttp3.MultipartBody.Part.createFormData("screenshot", "payment." + (if (type == "image/png") "png" else "jpg"), screenshot.asRequestBody(type.toMediaType()))
        return caller.get(SubmittedRequest.serializer()) {
            api.submitPaymentClaim(
                input.invoiceId.toString().toRequestBody(text), input.utr.toRequestBody(text), input.paidOn.toRequestBody(text),
                String.format(java.util.Locale.US, "%.2f", input.amount).toRequestBody(text), (input.note ?: "").toRequestBody(text), part,
            )
        }.claim
    }

    override suspend fun startGateway(invoiceId: Int): GatewayStart = caller.get(GatewayStart.serializer()) {
        api.startGatewayPayment(buildJsonObject { put("invoice_id", invoiceId) })
    }

    override suspend fun gatewayStatus(id: Int): GatewayStatus = caller.get(GatewayStatus.serializer()) { api.gatewayPayment(id) }

    override suspend fun screenshot(requestId: Int): ByteArray = caller.download { api.paymentScreenshot(requestId) }.first

    override suspend fun staticQr(): ByteArray = caller.download { api.paymentQrImage() }.first
}
