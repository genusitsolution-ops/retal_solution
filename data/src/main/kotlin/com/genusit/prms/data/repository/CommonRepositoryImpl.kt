package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.Meta
import com.genusit.prms.domain.model.NotificationList
import com.genusit.prms.domain.model.Receipt
import com.genusit.prms.domain.repository.CommonRepository
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

class CommonRepositoryImpl @Inject constructor(
    private val api: PrmsApiService,
    private val caller: ApiCaller,
) : CommonRepository {
    override suspend fun meta(): Meta = caller.get(Meta.serializer()) { api.meta() }
    override suspend fun notifications(): NotificationList = caller.get(NotificationList.serializer()) { api.notifications() }
    override suspend fun markAllNotificationsRead() { caller.run { api.updateNotifications(buildJsonObject { put("all", true) }) } }
    override suspend fun receipt(receiptNumber: String): Receipt = caller.get(Receipt.serializer()) { api.receipt(receiptNumber) }
}
