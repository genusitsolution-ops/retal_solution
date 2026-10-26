package com.genusit.prms.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit description of the PRMS REST API v1 (paths are relative to `…/api/v1/`).
 *
 * Every endpoint answers with the same JSON envelope
 * `{"status":"success|error","code":…,"message":…,"data":…,"errors":…}`, so responses are
 * returned raw and unwrapped in one place by [ApiCaller] (status codes, localized server
 * messages, lenient decoding). Request bodies are serialized by the kotlinx.serialization
 * converter. A null `@Query`/`@Header` value is left out of the request.
 */
interface PrmsApiService {

    /* ───────── auth & common ───────── */

    @POST("auth/login.php")
    suspend fun login(@Body body: JsonObject): Response<ResponseBody>

    /** [authorization] overrides the session token (used when a Super Admin is signed out right after login). */
    @POST("auth/logout.php")
    suspend fun logout(@Body body: JsonObject, @Header("Authorization") authorization: String?): Response<ResponseBody>

    @GET("auth/me.php")
    suspend fun me(): Response<ResponseBody>

    @PUT("auth/me.php")
    suspend fun updateMe(@Body body: JsonObject): Response<ResponseBody>

    @GET("common/meta.php")
    suspend fun meta(): Response<ResponseBody>

    @GET("common/notifications.php")
    suspend fun notifications(): Response<ResponseBody>

    @POST("common/notifications.php")
    suspend fun updateNotifications(@Body body: JsonObject): Response<ResponseBody>

    @GET("common/receipt.php")
    suspend fun receipt(@Query("receipt") receiptNumber: String): Response<ResponseBody>

    /* ───────── owner: dashboard, reports, referrals ───────── */

    @GET("owner/dashboard.php")
    suspend fun ownerDashboard(): Response<ResponseBody>

    /** [type] = collections | revenue | pending */
    @GET("owner/reports.php")
    suspend fun ownerReport(@Query("type") type: String, @Query("from") from: String?, @Query("to") to: String?): Response<ResponseBody>

    @GET("owner/referrals.php")
    suspend fun ownerReferrals(): Response<ResponseBody>

    /* ───────── owner: rooms ───────── */

    @GET("owner/properties.php")
    suspend fun ownerProperties(@Query("id") id: Int?): Response<ResponseBody>

    @POST("owner/properties.php")
    suspend fun addProperty(@Body body: JsonObject): Response<ResponseBody>

    @PUT("owner/properties.php")
    suspend fun updateProperty(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @DELETE("owner/properties.php")
    suspend fun deleteProperty(@Query("id") id: Int): Response<ResponseBody>

    /* ───────── owner: tenants ───────── */

    @GET("owner/tenants.php")
    suspend fun ownerTenants(@Query("id") id: Int?): Response<ResponseBody>

    @POST("owner/tenants.php")
    suspend fun addTenant(@Body body: JsonObject, @Header("Idempotency-Key") idempotencyKey: String?): Response<ResponseBody>

    @PUT("owner/tenants.php")
    suspend fun updateTenant(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @DELETE("owner/tenants.php")
    suspend fun deleteTenant(@Query("id") id: Int): Response<ResponseBody>

    /* ───────── public (login screen) ───────── */

    @GET("common/app_config.php")
    suspend fun appConfig(): Response<ResponseBody>

    @POST("auth/register.php")
    suspend fun register(@Body body: JsonObject): Response<ResponseBody>

    @GET
    suspend fun download(@retrofit2.http.Url url: String): Response<ResponseBody>

    @GET("owner/tenant_history.php")
    suspend fun tenantHistory(@Query("id") id: Int): Response<ResponseBody>

    /** Binary document (image or PDF); errors still come back as the JSON envelope. */
    @GET("owner/tenant_document.php")
    suspend fun tenantDocument(@Query("id") id: Int, @Query("type") type: String): Response<ResponseBody>

    @Multipart
    @POST("owner/tenant_document.php")
    suspend fun uploadDraftDocument(
        @Part("draft") draft: RequestBody,
        @Part("type") type: RequestBody,
        @Part("consent") consent: RequestBody,
        @Part("replace_draft") replaceDraft: RequestBody?,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    @DELETE("owner/tenant_document.php")
    suspend fun deleteDraftDocument(@Query("draft") token: String, @Query("type") type: String): Response<ResponseBody>

    @Multipart
    @POST("owner/tenant_document.php")
    suspend fun uploadTenantDocument(
        @Part("id") id: RequestBody,
        @Part("type") type: RequestBody,
        @Part("consent") consent: RequestBody,
        @Part file: MultipartBody.Part,
    ): Response<ResponseBody>

    /* ───────── owner: invoices & payments ───────── */

    @GET("owner/invoices.php")
    suspend fun ownerInvoices(@Query("status") status: String?): Response<ResponseBody>

    @GET("owner/invoices.php")
    suspend fun ownerInvoice(@Query("id") id: Int): Response<ResponseBody>

    @GET("owner/invoices.php")
    suspend fun invoiceContext(
        @Query("action") action: String,
        @Query("allocation_id") allocationId: Int,
        @Query("billing_month") month: Int,
        @Query("billing_year") year: Int,
    ): Response<ResponseBody>

    /** Record payment (with [idempotencyKey]), generate (`action=generate`) or cancel (`action=cancel`). */
    @POST("owner/invoices.php")
    suspend fun ownerInvoiceAction(@Body body: JsonObject, @Header("Idempotency-Key") idempotencyKey: String?): Response<ResponseBody>

    @GET("owner/payments.php")
    suspend fun ownerPayments(@Query("from") from: String?, @Query("to") to: String?): Response<ResponseBody>

    /* ───────── owner: charges ───────── */

    @GET("owner/charges.php")
    suspend fun ownerCharges(@Query("all") all: Int?): Response<ResponseBody>

    @PUT("owner/charges.php")
    suspend fun updateCharge(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @POST("owner/charges.php")
    suspend fun addCharge(@Body body: JsonObject): Response<ResponseBody>

    @DELETE("owner/charges.php")
    suspend fun deactivateCharge(@Query("id") id: Int): Response<ResponseBody>

    /* ───────── owner: allocations ───────── */

    @GET("owner/allocations.php")
    suspend fun ownerAllocations(@Query("status") status: String?): Response<ResponseBody>

    @POST("owner/allocations.php")
    suspend fun allocate(@Body body: JsonObject): Response<ResponseBody>

    @PUT("owner/allocations.php")
    suspend fun closeAllocation(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    /** `{"action":"collect_deposit","amount":…}` */
    @POST("owner/allocations.php")
    suspend fun allocationAction(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    /* ───────── owner: collection agents ───────── */

    @GET("owner/employees.php")
    suspend fun ownerEmployees(@Query("id") id: Int?): Response<ResponseBody>

    @POST("owner/employees.php")
    suspend fun addEmployee(@Body body: JsonObject): Response<ResponseBody>

    @PUT("owner/employees.php")
    suspend fun updateEmployee(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @DELETE("owner/employees.php")
    suspend fun deleteEmployee(@Query("id") id: Int): Response<ResponseBody>

    /** [action] = "assign" with `{"property_ids":[…]}` */
    @POST("owner/employees.php")
    suspend fun employeeAction(@Query("action") action: String, @Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    /* ───────── owner: collections & cash handovers ───────── */

    /** [view] = dashboard | employee | list | detail; empty filters are left out. */
    @GET("owner/settlements.php")
    suspend fun ownerSettlements(
        @Query("view") view: String,
        @Query("id") id: Int? = null,
        @Query("from") from: String? = null,
        @Query("to") to: String? = null,
        @Query("employee_id") employeeId: Int? = null,
        @Query("property_id") propertyId: Int? = null,
        @Query("tenant_id") tenantId: Int? = null,
        @Query("mode") mode: String? = null,
        @Query("collector") collector: String? = null,
        @Query("settlement") settlement: String? = null,
        @Query("status") status: String? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") perPage: Int? = null,
    ): Response<ResponseBody>

    /** `{"action":"confirm"|"reject"|"record", …}` */
    @POST("owner/settlements.php")
    suspend fun ownerSettlementAction(@Body body: JsonObject, @Header("Idempotency-Key") idempotencyKey: String?): Response<ResponseBody>

    /* ───────── owner: complaints & subscription ───────── */

    @GET("owner/queries.php")
    suspend fun ownerQueries(@Query("id") id: Int?): Response<ResponseBody>

    @POST("owner/queries.php")
    suspend fun ownerReplyQuery(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @PUT("owner/queries.php")
    suspend fun ownerUpdateQuery(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @GET("owner/subscription.php")
    suspend fun subscription(): Response<ResponseBody>

    @POST("owner/subscription.php")
    suspend fun subscriptionAction(@Body body: JsonObject): Response<ResponseBody>

    /* ───────── collection agent ───────── */

    @GET("employee/dashboard.php")
    suspend fun agentDashboard(): Response<ResponseBody>

    @GET("employee/tenants.php")
    suspend fun agentTenants(): Response<ResponseBody>

    @GET("employee/collections.php")
    suspend fun agentCollections(@Query("from") from: String?, @Query("to") to: String?): Response<ResponseBody>

    @POST("employee/collections.php")
    suspend fun agentCollect(@Body body: JsonObject, @Header("Idempotency-Key") idempotencyKey: String?): Response<ResponseBody>

    /** [view] = balance | list | detail */
    @GET("employee/settlements.php")
    suspend fun agentSettlements(
        @Query("view") view: String, @Query("id") id: Int? = null, @Query("status") status: String? = null, @Query("page") page: Int? = null,
    ): Response<ResponseBody>

    /** `{"action":"submit"|"cancel", …}` */
    @POST("employee/settlements.php")
    suspend fun agentSettlementAction(@Body body: JsonObject, @Header("Idempotency-Key") idempotencyKey: String?): Response<ResponseBody>

    @GET("employee/profile.php")
    suspend fun agentProfile(): Response<ResponseBody>

    @PUT("employee/profile.php")
    suspend fun updateAgentProfile(@Body body: JsonObject): Response<ResponseBody>

    /* ───────── tenant ───────── */

    @GET("tenant/dashboard.php")
    suspend fun tenantDashboard(): Response<ResponseBody>

    @GET("tenant/invoices.php")
    suspend fun tenantInvoices(@Query("id") id: Int?): Response<ResponseBody>

    @GET("tenant/payments.php")
    suspend fun tenantPayments(): Response<ResponseBody>

    @GET("tenant/queries.php")
    suspend fun tenantQueries(@Query("id") id: Int?): Response<ResponseBody>

    @POST("tenant/queries.php")
    suspend fun raiseQuery(@Body body: JsonObject): Response<ResponseBody>

    @POST("tenant/queries.php")
    suspend fun tenantReplyQuery(@Query("id") id: Int, @Body body: JsonObject): Response<ResponseBody>

    @GET("tenant/profile.php")
    suspend fun tenantProfile(): Response<ResponseBody>

    @PUT("tenant/profile.php")
    suspend fun updateTenantProfile(@Body body: JsonObject): Response<ResponseBody>
}
