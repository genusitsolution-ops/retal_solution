package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.NewQuery
import com.genusit.prms.domain.model.Query
import com.genusit.prms.domain.model.QueryDetail
import com.genusit.prms.domain.repository.QueryRepository
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class QueryRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : QueryRepository {
    override suspend fun ownerQueries(): List<Query> = caller.get(ListSerializer(Query.serializer())) { api.ownerQueries(null) }
    override suspend fun ownerQuery(id: Int): QueryDetail = caller.get(QueryDetail.serializer()) { api.ownerQueries(id) }
    override suspend fun ownerReply(id: Int, message: String, status: String?) {
        caller.run { api.ownerReplyQuery(id, buildJsonObject { put("message", message); status?.let { put("status", it) } }) }
    }
    override suspend fun ownerSetStatus(id: Int, status: String) {
        caller.run { api.ownerUpdateQuery(id, buildJsonObject { put("status", status) }) }
    }
    override suspend fun tenantQueries(): List<Query> = caller.get(ListSerializer(Query.serializer())) { api.tenantQueries(null) }
    override suspend fun tenantQuery(id: Int): QueryDetail = caller.get(QueryDetail.serializer()) { api.tenantQueries(id) }
    override suspend fun tenantReply(id: Int, message: String) {
        caller.run { api.tenantReplyQuery(id, buildJsonObject { put("message", message) }) }
    }
    override suspend fun raise(q: NewQuery): CreatedId = caller.get(CreatedId.serializer()) { api.raiseQuery(caller.body(NewQuery.serializer(), q)) }
}
