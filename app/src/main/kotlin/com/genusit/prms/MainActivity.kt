package com.genusit.prms

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.CompositionLocalProvider
import com.genusit.prms.ui.AppRoot
import com.genusit.prms.ui.common.LocalAppContainer
import com.genusit.prms.ui.theme.PrmsTheme

/** AppCompatActivity (not plain ComponentActivity) so per-app language switching works on all API levels. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as PrmsApplication).container
        setContent {
            CompositionLocalProvider(LocalAppContainer provides container) {
                PrmsTheme { AppRoot() }
            }
        }
    }
}
