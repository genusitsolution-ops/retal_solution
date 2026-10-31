package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.AgentHandover
import com.genusit.prms.domain.model.CollectionDashboard
import com.genusit.prms.domain.model.CollectionFilter
import com.genusit.prms.domain.model.EmployeeCollectionDetail
import com.genusit.prms.domain.model.HandoverInput
import com.genusit.prms.domain.model.SettlementDetail
import com.genusit.prms.domain.model.SettlementPage
import com.genusit.prms.domain.repository.AgentSettlementRepository
import com.genusit.prms.domain.repository.OwnerSettlementRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.add
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

private fun Int.orNullIfZero(): Int? = takeIf { it > 0 }

/** Body fields shared by "submit" (employee) and "record" (owner). */
internal fun JsonObjectBuilder.putHandover(input: HandoverInput) {
    if (input.collectionIds.isNotEmpty()) putJsonArray("collection_ids") { input.collectionIds.forEach { add(it) } }
    else put("amount", input.amount ?: 0.0)
    put("method", input.method.apiValue)
    put("handover_date", input.date)
    put("reference_no", input.reference.trim())
    put("remarks", input.remarks.trim())
}

internal fun handoverBody(action: String, input: HandoverInput, extra: JsonObjectBuilder.() -> Unit = {}): JsonObject =
    buildJsonObject { put("action", action); extra(); putHandover(input) }

class OwnerSettlementRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : OwnerSettlementRepository {

    override suspend fun dashboard(filter: CollectionFilter): CollectionDashboard = caller.get(CollectionDashboard.serializer()) {
        api.ownerSettlements(
            view = "dashboard", from = filter.from.orNullIfBlank(), to = filter.to.orNullIfBlank(),
            employeeId = filter.employeeId.orNullIfZero(), propertyId = filter.propertyId.orNullIfZero(), tenantId = filter.tenantId.orNullIfZero(),
            mode = filter.mode.orNullIfBlank(), collector = filter.collector.orNullIfBlank(), settlement = filter.settlement.orNullIfBlank(),
            page = filter.page, perPage = 50,
        )
    }

    override suspend fun employee(employeeId: Int, from: String, to: String, propertyId: Int, page: Int): EmployeeCollectionDetail =
        caller.get(EmployeeCollectionDetail.serializer()) {
            api.ownerSettlements(view = "employee", employeeId = employeeId, from = from.orNullIfBlank(), to = to.orNullIfBlank(),
                propertyId = propertyId.orNullIfZero(), page = page, perPage = 50)
        }

    override suspend fun settlements(status: String, employeeId: Int, page: Int): SettlementPage = caller.get(SettlementPage.serializer()) {
        api.ownerSettlements(view = "list", status = status.orNullIfBlank(), employeeId = employeeId.orNullIfZero(), page = page, perPage = 50)
    }

    override suspend fun detail(id: Int): SettlementDetail = caller.get(SettlementDetail.serializer()) { api.ownerSettlements(view = "detail", id = id) }

    override suspend fun confirm(id: Int, amountReceived: Double, note: String): SettlementDetail = caller.get(SettlementDetail.serializer()) {
        api.ownerSettlementAction(buildJsonObject { put("action", "confirm"); put("id", id); put("amount_received", amountReceived); put("note", note) }, null)
    }

    override suspend fun reject(id: Int, reason: String): SettlementDetail = caller.get(SettlementDetail.serializer()) {
        api.ownerSettlementAction(buildJsonObject { put("action", "reject"); put("id", id); put("reason", reason) }, null)
    }

    override suspend fun record(employeeId: Int, input: HandoverInput, idempotencyKey: String): SettlementDetail =
        caller.get(SettlementDetail.serializer()) {
            api.ownerSettlementAction(handoverBody("record", input) { put("employee_id", employeeId) }, idempotencyKey)
        }
}

class AgentSettlementRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : AgentSettlementRepository {
    override suspend fun balance(): AgentHandover = caller.get(AgentHandover.serializer()) { api.agentSettlements("balance") }

    override suspend fun settlements(status: String, page: Int): SettlementPage = caller.get(SettlementPage.serializer()) {
        api.agentSettlements("list", status = status.orNullIfBlank(), page = page)
    }

    override suspend fun detail(id: Int): SettlementDetail = caller.get(SettlementDetail.serializer()) { api.agentSettlements("detail", id = id) }

    override suspend fun submit(input: HandoverInput, idempotencyKey: String): SettlementDetail = caller.get(SettlementDetail.serializer()) {
        api.agentSettlementAction(handoverBody("submit", input), idempotencyKey)
    }

    override suspend fun cancel(id: Int): SettlementDetail = caller.get(SettlementDetail.serializer()) {
        api.agentSettlementAction(buildJsonObject { put("action", "cancel"); put("id", id) }, null)
    }
}

class PhotoRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : com.genusit.prms.domain.repository.PhotoRepository {
    override suspend fun upload(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?, file: java.io.File, consent: Boolean): String {
        val text = "text/plain".toMediaType()
        val part = okhttp3.MultipartBody.Part.createFormData("photo", "photo.jpg", file.asRequestBody("image/jpeg".toMediaType()))
        val data = caller.call {
            api.uploadProfilePhoto(subject.apiValue.toRequestBody(text), id?.toString()?.toRequestBody(text), (if (consent) "1" else "0").toRequestBody(text), part)
        }
        return (data as? JsonObject)?.get("photo_version")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            ?: throw com.genusit.prms.domain.error.ApiException(com.genusit.prms.domain.error.ApiException.BAD_RESPONSE, "photo_version", 200)
    }

    override suspend fun remove(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?) {
        caller.run { api.removeProfilePhoto(subject.apiValue, id) }
    }

    override suspend fun download(subject: com.genusit.prms.domain.model.PhotoSubject, id: Int?): ByteArray =
        caller.download { api.profilePhoto(subject.apiValue, id) }.first
}
