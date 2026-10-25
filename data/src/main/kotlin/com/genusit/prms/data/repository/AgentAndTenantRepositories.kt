package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.EmployeeCollections
import com.genusit.prms.domain.model.EmployeeDashboard
import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.model.InvoiceSummary
import com.genusit.prms.domain.model.PaymentResult
import com.genusit.prms.domain.model.Profile
import com.genusit.prms.domain.model.TenantDashboard
import com.genusit.prms.domain.model.TenantPayments
import com.genusit.prms.domain.model.TenantRow
import com.genusit.prms.domain.repository.AgentRepository
import com.genusit.prms.domain.repository.TenantPortalRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class AgentRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : AgentRepository {
    override suspend fun dashboard(): EmployeeDashboard = caller.get(EmployeeDashboard.serializer()) { api.agentDashboard() }
    override suspend fun tenants(): List<TenantRow> = caller.get(ListSerializer(TenantRow.serializer())) { api.agentTenants() }
    override suspend fun collections(from: String?, to: String?): EmployeeCollections =
        caller.get(EmployeeCollections.serializer()) { api.agentCollections(from.orNullIfBlank(), to.orNullIfBlank()) }

    override suspend fun collect(
        tenantId: Int, propertyId: Int, invoiceId: Int, amount: Double, mode: String, date: String,
        reference: String, remarks: String, idempotencyKey: String,
    ): PaymentResult {
        val body = buildJsonObject {
            put("tenant_id", tenantId); put("property_id", propertyId)
            if (invoiceId > 0) put("invoice_id", invoiceId)
            put("amount", amount); put("payment_mode", mode); put("collection_date", date)
            put("reference_number", reference); put("remarks", remarks)
        }
        return caller.get(PaymentResult.serializer()) { api.agentCollect(body, idempotencyKey) }
    }

    override suspend fun profile(): Profile = caller.get(Profile.serializer()) { api.agentProfile() }
}

class TenantPortalRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : TenantPortalRepository {
    override suspend fun dashboard(): TenantDashboard = caller.get(TenantDashboard.serializer()) { api.tenantDashboard() }
    override suspend fun invoices(): List<InvoiceSummary> = caller.get(ListSerializer(InvoiceSummary.serializer())) { api.tenantInvoices(null) }
    override suspend fun invoice(id: Int): InvoiceDetail = caller.get(InvoiceDetail.serializer()) { api.tenantInvoices(id) }
    override suspend fun payments(): TenantPayments = caller.get(TenantPayments.serializer()) { api.tenantPayments() }
    override suspend fun profile(): Profile = caller.get(Profile.serializer()) { api.tenantProfile() }
}
