package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.Building
import com.genusit.prms.domain.model.BuildingInput
import com.genusit.prms.domain.model.BuildingPage
import com.genusit.prms.domain.repository.BuildingsRepository
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject

/** 1.17 — the owner's properties (owner/buildings.php). */
class BuildingsRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : BuildingsRepository {

    override suspend fun list(query: String, page: Int, perPage: Int, status: String, withDues: Boolean): BuildingPage =
        caller.get(BuildingPage.serializer()) {
            api.buildings(query.takeIf { it.isNotBlank() }, page.coerceAtLeast(1), perPage.coerceIn(1, 50), status, if (withDues) 1 else null)
        }

    override suspend fun get(id: Int): Building = caller.get(Building.serializer()) { api.building(id) }

    override suspend fun create(input: BuildingInput): Building = caller.get(Building.serializer()) { api.createBuilding(body(input)) }

    override suspend fun update(id: Int, input: BuildingInput) = caller.run { api.updateBuilding(id, body(input)) }

    override suspend fun delete(id: Int) = caller.run { api.deleteBuilding(id) }

    override suspend fun select(id: Int) = caller.run { api.selectBuilding(id.coerceAtLeast(0)) }

    override suspend fun moveRooms(roomIds: List<Int>, toBuildingId: Int): Int {
        val data = caller.call {
            api.moveRooms(1, buildJsonObject {
                putJsonArray("room_ids") { roomIds.forEach { add(it) } }
                put("to_building_id", toBuildingId)
            })
        }
        return runCatching { data.jsonObject["moved"]!!.jsonPrimitive.int }.getOrDefault(roomIds.size)
    }

    private fun body(i: BuildingInput): JsonObject = buildJsonObject {
        put("name", i.name); put("city", i.city); put("property_type", i.type)
        put("area", i.area); put("address", i.address); put("pincode", i.pincode); put("description", i.description)
        i.status?.let { put("status", it) }
    }
}
