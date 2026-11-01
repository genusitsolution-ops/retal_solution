package com.genusit.prms.data.repository

import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.PrmsApiService
import com.genusit.prms.domain.model.Meta
import com.genusit.prms.domain.model.NotificationList
import com.genusit.prms.domain.model.Receipt
import com.genusit.prms.domain.model.NotificationPrefs
import com.genusit.prms.domain.model.UnreadCount
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

    override suspend fun unreadCount(): Int = caller.get(UnreadCount.serializer()) { api.notificationCount() }.unread
    override suspend fun markNotificationRead(id: Int) { caller.run { api.updateNotifications(buildJsonObject { put("id", id) }) } }
    override suspend fun notificationPrefs(): NotificationPrefs = caller.get(NotificationPrefs.serializer()) { api.pushPrefs() }
    override suspend fun saveNotificationPrefs(prefs: Map<String, Boolean>): NotificationPrefs = caller.get(NotificationPrefs.serializer()) {
        api.pushAction(buildJsonObject { put("action", "prefs"); put("prefs", buildJsonObject { prefs.forEach { (k, v) -> put(k, v) } }) })
    }
    override suspend fun registerPushToken(token: String, appVersion: String) {
        caller.run { api.pushAction(buildJsonObject { put("action", "register"); put("token", token); put("platform", "android"); put("app_version", appVersion) }) }
    }
    override suspend fun unregisterPushToken(token: String) {
        caller.run { api.pushAction(buildJsonObject { put("action", "unregister"); put("token", token) }) }
    }
}
