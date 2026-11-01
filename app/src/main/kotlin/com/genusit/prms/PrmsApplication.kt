package com.genusit.prms

import android.app.Application
import com.genusit.prms.platform.PushManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class PrmsApplication : Application() {
    @Inject lateinit var push: PushManager

    override fun onCreate() {
        super.onCreate()
        // 1.11: Firebase from the cached server values, so a message can be delivered while the app is closed
        push.initFromCache()
    }
}
