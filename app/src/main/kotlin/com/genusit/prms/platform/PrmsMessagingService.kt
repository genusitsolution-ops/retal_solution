package com.genusit.prms.platform

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Receives FCM messages (1.11). In the background Android shows the server's notification itself and a tap
 * opens MainActivity with the message data (route) as extras; in the foreground it arrives here.
 */
@AndroidEntryPoint
class PrmsMessagingService : FirebaseMessagingService() {
    @Inject lateinit var push: PushManager

    override fun onNewToken(token: String) = push.onNewToken(token)

    override fun onMessageReceived(message: RemoteMessage) {
        val d = message.data
        push.onMessage(
            title = message.notification?.title ?: d["title"],
            body = message.notification?.body ?: d["body"],
            route = d["route"], category = d["category"], id = d["notification_id"],
        )
    }
}
