package com.genusit.prms.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.genusit.prms.MainActivity
import com.genusit.prms.R
import com.genusit.prms.di.ApplicationScope
import com.genusit.prms.domain.model.PushClientConfig
import com.genusit.prms.domain.repository.CommonRepository
import com.genusit.prms.domain.repository.PushTokenProvider
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.GetAppConfigUseCase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Push notifications (1.11), Firebase Cloud Messaging.
 *
 * The APK carries no Firebase project: the server publishes the public client values (project id, app id,
 * API key, sender id) in app_config. They are cached here so Firebase can start with the process — FCM may
 * start the app in the background to deliver a message — and a Super Admin can switch projects without a new APK
 * (takes effect on the next app start). The private service-account key stays on the server.
 *
 * After sign-in the device token is registered for the user (server: common/push.php); on sign-out it is
 * removed (sent with the logout call). Sending is done by the server after each business event.
 */
@Singleton
class PushManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getConfig: GetAppConfigUseCase,
    private val common: CommonRepository,
    private val session: SessionRepository,
    @ApplicationScope private val scope: CoroutineScope,
) : PushTokenProvider {
    private val prefs = context.getSharedPreferences("push", Context.MODE_PRIVATE)
    private val _incoming = MutableSharedFlow<String?>(extraBufferCapacity = 8)
    /** A push arrived while the app runs (route); the unread badge and lists refresh. */
    val incoming: SharedFlow<String?> = _incoming.asSharedFlow()
    private val _available = MutableStateFlow(prefs.getString(K_PROJECT, null) != null)
    /** Push is configured on the server (the app asks for the notification permission only then). */
    val available: StateFlow<Boolean> = _available.asStateFlow()

    override fun currentToken(): String? = prefs.getString(K_TOKEN, null)

    /** Application.onCreate: start Firebase from the cached server values (if any). Never throws. */
    fun initFromCache() {
        createChannels()
        val o = cachedOptions() ?: return
        runCatching { if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, o) }
    }

    private fun cachedOptions(): FirebaseOptions? {
        val project = prefs.getString(K_PROJECT, null) ?: return null
        return FirebaseOptions.Builder()
            .setProjectId(project)
            .setApplicationId(prefs.getString(K_APP, null) ?: return null)
            .setApiKey(prefs.getString(K_KEY, null) ?: return null)
            .setGcmSenderId(prefs.getString(K_SENDER, null) ?: return null)
            .build()
    }

    /** After sign-in (and on each app start while signed in): refresh the server values and register this phone. */
    fun onSignedIn() {
        scope.launch {
            try {
                val cfg = getConfig().push
                if (cfg == null || !cfg.isComplete) { _available.value = false; return@launch }
                save(cfg)
                _available.value = true
                if (FirebaseApp.getApps(context).isEmpty()) FirebaseApp.initializeApp(context, cachedOptions()!!)
                val token = fetchToken()
                register(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // no network / push not set up: the in-app list and badge still work
            }
        }
    }

    private fun save(c: PushClientConfig) {
        prefs.edit().putString(K_PROJECT, c.projectId).putString(K_APP, c.appIdFor(context.packageName))
            .putString(K_KEY, c.apiKey).putString(K_SENDER, c.senderId).apply()
    }

    private suspend fun fetchToken(): String = suspendCancellableCoroutine { cont ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }

    private suspend fun register(token: String) {
        prefs.edit().putString(K_TOKEN, token).apply()
        if (session.session.value == null) return
        common.registerPushToken(token, appVersion())
    }

    /** Firebase rotated the token. */
    fun onNewToken(token: String) {
        prefs.edit().putString(K_TOKEN, token).apply()
        scope.launch { runCatching { register(token) } }
    }

    /** Message received while the app is in the foreground (or a data message): show it, refresh badge/lists. */
    fun onMessage(title: String?, body: String?, route: String?, category: String?, id: String?) {
        _incoming.tryEmit(route)
        if (title.isNullOrBlank() && body.isNullOrBlank()) return
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(EXTRA_ROUTE, route).putExtra(EXTRA_ID, id)
        val pi = PendingIntent.getActivity(context, (id ?: route ?: "").hashCode(), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(context, channelFor(category))
            .setSmallIcon(R.drawable.ic_stat_notify)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify((id ?: route ?: "n").hashCode(), n) }
    }

    private fun channelFor(category: String?) = when (category) {
        "billing", "payments" -> CH_BILLING
        "subscription", "platform" -> CH_ACCOUNT
        else -> CH_ACTIVITY
    }

    /** The server sends channel ids billing / activity / account; they must exist before a message arrives. */
    fun createChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            Triple(CH_BILLING, R.string.channel_billing, NotificationManager.IMPORTANCE_HIGH),
            Triple(CH_ACTIVITY, R.string.channel_activity, NotificationManager.IMPORTANCE_DEFAULT),
            Triple(CH_ACCOUNT, R.string.channel_account, NotificationManager.IMPORTANCE_HIGH),
        ).forEach { (id, name, imp) -> nm.createNotificationChannel(NotificationChannel(id, context.getString(name), imp).apply { setShowBadge(true) }) }
    }

    private fun appVersion(): String = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: ""

    companion object {
        const val EXTRA_ROUTE = "route"
        const val EXTRA_ID = "notification_id"
        const val CH_BILLING = "billing"
        const val CH_ACTIVITY = "activity"
        const val CH_ACCOUNT = "account"
        private const val K_PROJECT = "project"; private const val K_APP = "app"; private const val K_KEY = "key"
        private const val K_SENDER = "sender"; private const val K_TOKEN = "token"
    }
}

/** A notification the user tapped; the signed-in shell opens the matching screen once. */
object PendingRoute {
    private val _route = MutableStateFlow<String?>(null)
    val route: StateFlow<String?> = _route.asStateFlow()
    fun offer(intent: Intent?) {
        val r = intent?.getStringExtra(PushManager.EXTRA_ROUTE)
        if (!r.isNullOrBlank()) { _route.value = r; intent.removeExtra(PushManager.EXTRA_ROUTE) }
    }
    fun consume(): String? = _route.value.also { _route.value = null }
}
