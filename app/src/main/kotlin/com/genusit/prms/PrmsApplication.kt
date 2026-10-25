package com.genusit.prms

import android.app.Application
import com.genusit.prms.data.AppContainer

class PrmsApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
