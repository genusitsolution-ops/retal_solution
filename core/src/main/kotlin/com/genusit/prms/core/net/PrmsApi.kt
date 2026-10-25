package com.genusit.prms.core.net

import com.genusit.prms.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URLEncoder
import java.util.UUID

/**
 * Error raised for every non-success API answer.
 * [code] is the stable machine code from the server (e.g. AMOUNT_EXCEEDS_BALANCE,
 * SUBSCRIPTION_EXPIRED, TOKEN_EXPIRED); [message] is already localized by the server
 * in the language sent with Accept-Language.
 */
class ApiException(
    val code: String,
    override val message: String,
    val httpStatus: Int,
    val fieldErrors: JsonElement? = null,
) : Exception(message) {
    val isAuthError: Boolean get() = httpStatus == 401
    val isSubscriptionBlock: Boolean get() = code == "SUBSCRIPTION_EXPIRED" || httpStatus == 402
    val isNetwork: Boolean get() = code == NETWORK
    /** Server answered with a technical problem (redirect, HTML instead of JSON) – not the user's fault. */
    val isServerMisconfig: Boolean get() = code == BAD_RESPONSE || code == SERVER_REDIRECT

    companion object {
        const val NETWORK = "NETWORK_ERROR"
        const val BAD_RESPONSE = "BAD_RESPONSE"
        /** HTTP 3xx that could not be followed safely (see [PrmsApi] redirect policy). */
        const val SERVER_REDIRECT = "SERVER_REDIRECT"
    }
}

/** Minimal HTTP abstraction so the client is testable and has no third-party network dependency. */
interface HttpTransport {
    fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult
}

/** [location] = the Location header of a 3xx answer (null otherwise). */
data class HttpResult(val status: Int, val body: String, val location: String? = null)

class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
) : HttpTransport {
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult {
        val conn = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = connectTimeoutMs
            conn.readTimeout = readTimeoutMs
            conn.instanceFollowRedirects = false
            conn.useCaches = false
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status >= 400) conn.errorStream else conn.inputStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            val location = if (status in 300..399) conn.getHeaderField("Location") else null
            return HttpResult(status, text, location)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Typed client for the PRMS REST API v1 (see docs/API.md).
 * @param baseUrl e.g. "https://example.com/rentalsolutionfinal/api/v1/" (trailing slash required)
 * @param tokenProvider current bearer token or null
 * @param langProvider current app language: "en" | "hi" | "mr" (sent as Accept-Language)
 */
class PrmsApi(
    private val baseUrl: String,
    private val tokenProvider: () -> String?,
    private val langProvider: () -> String,
    private val transport: HttpTransport = UrlConnectionTransport(),
    private val userAgent: String = "PRMS-Android",
    /** Called whenever a server redirect was followed, so debug builds can report the misconfiguration. */
    private val onRedirect: ((status: Int, from: String, to: String) -> Unit)? = null,
) {
    @OptIn(ExperimentalSerializationApi::class)
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    init {
        require(baseUrl.endsWith("/")) { "baseUrl must end with /" }
    }

    /* ───────── plumbing ───────── */

    private suspend fun call(
        method: String,
        path: String,
        query: Map<String, Any?> = emptyMap(),
        body: JsonElement? = null,
        idempotencyKey: String? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val qs = query.filterValues { it != null && it.toString().isNotEmpty() }
            .map { (k, v) -> enc(k) + "=" + enc(v.toString()) }
            .joinToString("&")
        val url = baseUrl + path + if (qs.isNotEmpty()) "?$qs" else ""
        val headers = LinkedHashMap<String, String>()
        headers["Accept"] = "application/json"
        headers["Accept-Language"] = langProvider()
        headers["User-Agent"] = userAgent
        tokenProvider()?.takeIf { it.isNotBlank() }?.let { headers["Authorization"] = "Bearer $it" }
        if (body != null) headers["Content-Type"] = "application/json; charset=utf-8"
        if (idempotencyKey != null) headers["Idempotency-Key"] = idempotencyKey

        var target = url
        var res = send(method, target, headers, body?.toString())
        var hops = 0
        while (res.status in REDIRECT_CODES) {
            val next = safeRedirectTarget(target, res.location)
            if (next == null || hops >= MAX_REDIRECTS) {
                throw ApiException(ApiException.SERVER_REDIRECT, "HTTP ${res.status} → ${res.location ?: "(no Location header)"}", res.status)
            }
            onRedirect?.invoke(res.status, target, next)
            target = next
            hops++
            // Same method, headers and body (incl. Idempotency-Key): a 3xx means the request
            // never reached the PHP script, so resending it cannot record anything twice.
            res = send(method, target, headers, body?.toString())
        }
        if (res.status in 300..399) { // e.g. 303 "see other": never replay a POST as something else
            throw ApiException(ApiException.SERVER_REDIRECT, "HTTP ${res.status} → ${res.location ?: "(no Location header)"}", res.status)
        }
        parseEnvelope(res)
    }

    private fun send(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult =
        try {
            transport.execute(method, url, headers, body)
        } catch (e: SocketTimeoutException) {
            throw ApiException(ApiException.NETWORK, "timeout: $url", 0)
        } catch (e: IOException) {
            // Keep the exception type (UnknownHost / Connect / SSLHandshake …) for diagnostics.
            throw ApiException(ApiException.NETWORK, "${e.javaClass.simpleName}: ${e.message ?: "network"}", 0)
        }

    /**
     * Redirect policy (never "follow anything"):
     *  - only to the configured host or its www / non-www twin,
     *  - never from https down to http,
     *  - only to a path inside the configured API folder (e.g. /rentalsolutionfinal/api/v1/…).
     * This covers the usual cPanel .htaccess canonical rules (add/strip "www", strip ".php")
     * while refusing any redirect to a login page, another site or plain HTTP.
     * Returns the absolute target URL, or null when the redirect is not safe.
     */
    internal fun safeRedirectTarget(from: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val base = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        val to = runCatching { URI(from).resolve(location.trim()) }.getOrNull() ?: return null
        val baseHost = base.host?.lowercase()?.removePrefix("www.") ?: return null
        val toHost = to.host?.lowercase()?.removePrefix("www.") ?: return null
        if (toHost != baseHost) return null
        if (base.scheme.equals("https", true) && !to.scheme.equals("https", true)) return null
        if (base.port != to.port) return null
        val basePath = base.rawPath ?: "/"
        val toPath = to.rawPath ?: return null
        if (!toPath.startsWith(basePath)) return null
        return to.toString()
    }

    internal fun parseEnvelope(res: HttpResult): JsonElement {
        val root = try {
            json.parseToJsonElement(res.body).jsonObject
        } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, "HTTP ${res.status}", res.status)
        }
        val status = root["status"]?.jsonPrimitive?.contentOrNull
        if (status == "success" && res.status in 200..299) return root["data"] ?: JsonNull
        val code = root["code"]?.jsonPrimitive?.contentOrNull ?: "HTTP_${res.status}"
        val msg = root["message"]?.jsonPrimitive?.contentOrNull ?: "HTTP ${res.status}"
        throw ApiException(code, msg, res.status, root["errors"])
    }

    private fun <T> decode(ser: KSerializer<T>, el: JsonElement): T =
        try { json.decodeFromJsonElement(ser, el) } catch (e: Exception) {
            throw ApiException(ApiException.BAD_RESPONSE, e.message ?: "decode", 200)
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        val REDIRECT_CODES = setOf(301, 302, 307, 308) // 303 means "switch to GET" – never replayed
        const val MAX_REDIRECTS = 2
    }

    /** New random key for a money-changing request; reuse it when retrying the same request. */
    fun newIdempotencyKey(): String = "and-" + UUID.randomUUID().toString()

    /* ───────── auth & common ───────── */

    suspend fun login(username: String, password: String, lang: String): LoginResult {
        val body = buildJsonObject { put("username", username); put("password", password); put("lang", lang) }
        return decode(LoginResult.serializer(), call("POST", "auth/login.php", body = body))
    }

    suspend fun logout() { call("POST", "auth/logout.php", body = JsonObject(emptyMap())) }

    suspend fun me(): Me = decode(Me.serializer(), call("GET", "auth/me.php"))

    suspend fun saveLanguage(lang: String) {
        call("PUT", "auth/me.php", body = buildJsonObject { put("lang", lang) })
    }

    suspend fun meta(): Meta = decode(Meta.serializer(), call("GET", "common/meta.php"))

    suspend fun notifications(): NotificationList = decode(NotificationList.serializer(), call("GET", "common/notifications.php"))

    suspend fun markAllNotificationsRead() {
        call("POST", "common/notifications.php", body = buildJsonObject { put("all", true) })
    }

    suspend fun receipt(receiptNumber: String): Receipt =
        decode(Receipt.serializer(), call("GET", "common/receipt.php", mapOf("receipt" to receiptNumber)))

    /* ───────── owner ───────── */

    suspend fun ownerDashboard(): OwnerDashboard = decode(OwnerDashboard.serializer(), call("GET", "owner/dashboard.php"))

    suspend fun ownerProperties(): List<Property> = decode(ListSerializer(Property.serializer()), call("GET", "owner/properties.php"))

    suspend fun addProperty(p: NewProperty): CreatedId =
        decode(CreatedId.serializer(), call("POST", "owner/properties.php", body = json.encodeToJsonElement(NewProperty.serializer(), p)))

    suspend fun ownerTenants(): List<TenantRow> = decode(ListSerializer(TenantRow.serializer()), call("GET", "owner/tenants.php"))

    suspend fun ownerTenant(id: Int): TenantDetail = decode(TenantDetail.serializer(), call("GET", "owner/tenants.php", mapOf("id" to id)))

    suspend fun ownerInvoices(status: String? = null): List<InvoiceSummary> =
        decode(ListSerializer(InvoiceSummary.serializer()), call("GET", "owner/invoices.php", mapOf("status" to status)))

    suspend fun ownerInvoice(id: Int): InvoiceDetail = decode(InvoiceDetail.serializer(), call("GET", "owner/invoices.php", mapOf("id" to id)))

    /** Owner records rent received against one invoice. Never pays more than the balance (server enforces). */
    suspend fun ownerRecordPayment(
        invoiceId: Int, amount: Double, mode: String, date: String, reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult {
        val body = buildJsonObject {
            put("invoice_id", invoiceId); put("amount", amount); put("payment_method", mode)
            put("payment_date", date); put("reference_number", reference); put("remarks", remarks)
        }
        return decode(PaymentResult.serializer(), call("POST", "owner/invoices.php", body = body, idempotencyKey = idempotencyKey))
    }

    suspend fun ownerPayments(from: String? = null, to: String? = null): OwnerPayments =
        decode(OwnerPayments.serializer(), call("GET", "owner/payments.php", mapOf("from" to from, "to" to to)))

    suspend fun ownerQueries(): List<Query> = decode(ListSerializer(Query.serializer()), call("GET", "owner/queries.php"))

    suspend fun subscription(): SubscriptionStatus = decode(SubscriptionStatus.serializer(), call("GET", "owner/subscription.php"))

    suspend fun requestRenewal() {
        call("POST", "owner/subscription.php", body = buildJsonObject { put("action", "request_renewal") })
    }

    /* ───────── owner: manage (app 1.1) ───────── */

    suspend fun ownerProperty(id: Int): Property = decode(Property.serializer(), call("GET", "owner/properties.php", mapOf("id" to id)))

    /** [status] may be "available" or "maintenance"; null keeps the current status. */
    suspend fun updateProperty(id: Int, p: NewProperty, status: String?) {
        val body = buildJsonObject {
            put("address", p.address); put("city", p.city); put("state", p.state); put("pincode", p.pincode)
            put("base_rent", p.baseRent); put("deposit_amount", p.depositAmount)
            status?.let { put("status", it) }
        }
        call("PUT", "owner/properties.php", mapOf("id" to id), body)
    }

    /** Refused by the server with HAS_HISTORY when the room has allocations or invoices. */
    suspend fun deleteProperty(id: Int) { call("DELETE", "owner/properties.php", mapOf("id" to id)) }

    suspend fun ownerTenantEditable(id: Int): TenantEditable =
        decode(TenantEditable.serializer(), call("GET", "owner/tenants.php", mapOf("id" to id)))

    suspend fun addTenant(t: TenantInput): CreatedId =
        decode(CreatedId.serializer(), call("POST", "owner/tenants.php", body = json.encodeToJsonElement(TenantInput.serializer(), t)))

    suspend fun updateTenant(id: Int, t: TenantInput) {
        call("PUT", "owner/tenants.php", mapOf("id" to id), json.encodeToJsonElement(TenantInput.serializer(), t))
    }

    /** Only for tenants without any rent history (server answers HAS_HISTORY otherwise). */
    suspend fun deleteTenant(id: Int) { call("DELETE", "owner/tenants.php", mapOf("id" to id)) }

    suspend fun ownerCharges(includeInactive: Boolean = false): List<Charge> =
        decode(ListSerializer(Charge.serializer()), call("GET", "owner/charges.php", mapOf("all" to if (includeInactive) 1 else null)))

    suspend fun setChargeStatus(id: Int, active: Boolean) {
        call("PUT", "owner/charges.php", mapOf("id" to id), buildJsonObject { put("status", if (active) "active" else "inactive") })
    }

    suspend fun addCharge(c: NewCharge): CreatedId =
        decode(CreatedId.serializer(), call("POST", "owner/charges.php", body = json.encodeToJsonElement(NewCharge.serializer(), c)))

    /** Deactivates the charge (history stays on old invoices). */
    suspend fun deactivateCharge(id: Int) { call("DELETE", "owner/charges.php", mapOf("id" to id)) }

    suspend fun ownerAllocations(status: String? = null): List<Allocation> =
        decode(ListSerializer(Allocation.serializer()), call("GET", "owner/allocations.php", mapOf("status" to status)))

    suspend fun allocate(a: NewAllocation): AllocationCreated =
        decode(AllocationCreated.serializer(), call("POST", "owner/allocations.php", body = json.encodeToJsonElement(NewAllocation.serializer(), a)))

    /** Closes the allocation; the server computes the settlement (deposit collected − pending rent). */
    suspend fun closeAllocation(id: Int, notes: String): AllocationClosed =
        decode(AllocationClosed.serializer(), call("PUT", "owner/allocations.php", mapOf("id" to id), buildJsonObject { put("settlement_notes", notes) }))

    suspend fun collectDeposit(id: Int, amount: Double): DepositCollected =
        decode(DepositCollected.serializer(), call("POST", "owner/allocations.php", mapOf("id" to id),
            buildJsonObject { put("action", "collect_deposit"); put("amount", amount) }))

    suspend fun invoiceContext(allocationId: Int, month: Int, year: Int): InvoiceContext =
        decode(InvoiceContext.serializer(), call("GET", "owner/invoices.php",
            mapOf("action" to "context", "allocation_id" to allocationId, "billing_month" to month, "billing_year" to year)))

    /** Generates a rent invoice. Totals are computed on the server from the owner's charges. */
    suspend fun generateInvoice(allocationId: Int, month: Int, year: Int, fixedChargeIds: List<Int>, readings: List<MeterReading>): GeneratedInvoice {
        val body = buildJsonObject {
            put("action", "generate"); put("allocation_id", allocationId); put("billing_month", month); put("billing_year", year)
            put("fixed_charge_ids", buildJsonArray { fixedChargeIds.forEach { add(it) } })
            putJsonObject("var") {
                readings.forEach { r -> putJsonObject(r.tcId.toString()) { put("prev", r.previous); put("curr", r.current) } }
            }
        }
        return decode(GeneratedInvoice.serializer(), call("POST", "owner/invoices.php", body = body))
    }

    /** Only unpaid invoices can be cancelled (server answers NOT_CANCELLABLE otherwise). */
    suspend fun cancelInvoice(invoiceId: Int, reason: String) {
        call("POST", "owner/invoices.php", body = buildJsonObject { put("action", "cancel"); put("invoice_id", invoiceId); put("reason", reason) })
    }

    suspend fun ownerEmployees(): List<EmployeeRow> = decode(ListSerializer(EmployeeRow.serializer()), call("GET", "owner/employees.php"))

    suspend fun ownerEmployee(id: Int): EmployeeDetail = decode(EmployeeDetail.serializer(), call("GET", "owner/employees.php", mapOf("id" to id)))

    suspend fun addEmployee(e: EmployeeInput): CreatedId =
        decode(CreatedId.serializer(), call("POST", "owner/employees.php", body = json.encodeToJsonElement(EmployeeInput.serializer(), e)))

    suspend fun updateEmployee(id: Int, e: EmployeeInput) {
        call("PUT", "owner/employees.php", mapOf("id" to id), json.encodeToJsonElement(EmployeeInput.serializer(), e))
    }

    suspend fun setEmployeeStatus(id: Int, active: Boolean) {
        call("PUT", "owner/employees.php", mapOf("id" to id), buildJsonObject { put("status", if (active) "active" else "inactive") })
    }

    /** Deletes the employee, or deactivates when collections exist ([EmployeeRemoved.deactivated]). */
    suspend fun deleteEmployee(id: Int): EmployeeRemoved =
        decode(EmployeeRemoved.serializer(), call("DELETE", "owner/employees.php", mapOf("id" to id)))

    suspend fun assignProperties(employeeId: Int, propertyIds: List<Int>) {
        call("POST", "owner/employees.php", mapOf("action" to "assign", "id" to employeeId),
            buildJsonObject { put("property_ids", buildJsonArray { propertyIds.forEach { add(it) } }) })
    }

    suspend fun ownerQuery(id: Int): QueryDetail = decode(QueryDetail.serializer(), call("GET", "owner/queries.php", mapOf("id" to id)))

    suspend fun ownerReplyQuery(id: Int, message: String, status: String?) {
        call("POST", "owner/queries.php", mapOf("id" to id), buildJsonObject { put("message", message); status?.let { put("status", it) } })
    }

    suspend fun ownerSetQueryStatus(id: Int, status: String) {
        call("PUT", "owner/queries.php", mapOf("id" to id), buildJsonObject { put("status", status) })
    }

    suspend fun collectionsReport(from: String?, to: String?): CollectionsReport =
        decode(CollectionsReport.serializer(), call("GET", "owner/reports.php", mapOf("type" to "collections", "from" to from, "to" to to)))

    suspend fun revenueReport(from: String?, to: String?): List<RevenueRow> =
        decode(ListSerializer(RevenueRow.serializer()), call("GET", "owner/reports.php", mapOf("type" to "revenue", "from" to from, "to" to to)))

    suspend fun pendingReport(): PendingReport = decode(PendingReport.serializer(), call("GET", "owner/reports.php", mapOf("type" to "pending")))

    suspend fun referrals(): ReferralSummary = decode(ReferralSummary.serializer(), call("GET", "owner/referrals.php"))

    /** Owner profile (phone/email/password) via auth/me.php. */
    suspend fun updateOwnerProfile(phone: String?, email: String?, currentPassword: String?, newPassword: String?): ProfileSaved {
        val body = buildJsonObject {
            phone?.let { put("phone", it) }
            email?.let { put("email", it) }
            if (!newPassword.isNullOrEmpty()) { put("current_password", currentPassword ?: ""); put("password", newPassword) }
        }
        return decode(ProfileSaved.serializer(), call("PUT", "auth/me.php", body = body))
    }

    /* ───────── employee ───────── */

    suspend fun employeeDashboard(): EmployeeDashboard = decode(EmployeeDashboard.serializer(), call("GET", "employee/dashboard.php"))

    suspend fun employeeTenants(): List<TenantRow> = decode(ListSerializer(TenantRow.serializer()), call("GET", "employee/tenants.php"))

    suspend fun employeeCollections(from: String? = null, to: String? = null): EmployeeCollections =
        decode(EmployeeCollections.serializer(), call("GET", "employee/collections.php", mapOf("from" to from, "to" to to)))

    /**
     * Employee records a collection. With [invoiceId] = 0 the server applies the money
     * to the oldest unpaid invoices first. The same [idempotencyKey] on a retry returns the
     * first receipt instead of recording twice.
     */
    suspend fun employeeCollect(
        tenantId: Int, propertyId: Int, invoiceId: Int, amount: Double, mode: String, date: String,
        reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult {
        val body = buildJsonObject {
            put("tenant_id", tenantId); put("property_id", propertyId)
            if (invoiceId > 0) put("invoice_id", invoiceId)
            put("amount", amount); put("payment_mode", mode); put("collection_date", date)
            put("reference_number", reference); put("remarks", remarks)
        }
        return decode(PaymentResult.serializer(), call("POST", "employee/collections.php", body = body, idempotencyKey = idempotencyKey))
    }

    suspend fun employeeProfile(): Profile = decode(Profile.serializer(), call("GET", "employee/profile.php"))

    /* ───────── tenant ───────── */

    suspend fun tenantDashboard(): TenantDashboard = decode(TenantDashboard.serializer(), call("GET", "tenant/dashboard.php"))

    suspend fun tenantInvoices(): List<InvoiceSummary> = decode(ListSerializer(InvoiceSummary.serializer()), call("GET", "tenant/invoices.php"))

    suspend fun tenantInvoice(id: Int): InvoiceDetail = decode(InvoiceDetail.serializer(), call("GET", "tenant/invoices.php", mapOf("id" to id)))

    suspend fun tenantPayments(): TenantPayments = decode(TenantPayments.serializer(), call("GET", "tenant/payments.php"))

    suspend fun tenantQueries(): List<Query> = decode(ListSerializer(Query.serializer()), call("GET", "tenant/queries.php"))

    suspend fun tenantQuery(id: Int): QueryDetail = decode(QueryDetail.serializer(), call("GET", "tenant/queries.php", mapOf("id" to id)))

    /** Refused by the server when the ticket is closed. */
    suspend fun tenantReplyQuery(id: Int, message: String) {
        call("POST", "tenant/queries.php", mapOf("id" to id), buildJsonObject { put("message", message) })
    }

    suspend fun raiseQuery(q: NewQuery): CreatedId =
        decode(CreatedId.serializer(), call("POST", "tenant/queries.php", body = json.encodeToJsonElement(NewQuery.serializer(), q)))

    suspend fun tenantProfile(): Profile = decode(Profile.serializer(), call("GET", "tenant/profile.php"))

    /** Profile update for the logged-in role. A password change needs [currentPassword]. */
    suspend fun updateProfile(role: Role, phone: String?, email: String?, currentPassword: String?, newPassword: String?) {
        if (role == Role.OWNER) { updateOwnerProfile(phone, email, currentPassword, newPassword); return }
        val path = when (role) { Role.TENANT -> "tenant/profile.php"; Role.EMPLOYEE -> "employee/profile.php"; else -> return }
        val body = buildJsonObject {
            phone?.let { put("phone", it) }
            email?.let { put("email", it) }
            if (!newPassword.isNullOrEmpty()) { put("current_password", currentPassword ?: ""); put("password", newPassword) }
        }
        call("PUT", path, body = body)
    }
}
