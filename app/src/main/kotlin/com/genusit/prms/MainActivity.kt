package com.genusit.prms

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.genusit.prms.ui.AppRoot
import com.genusit.prms.ui.theme.PrmsTheme
import dagger.hilt.android.AndroidEntryPoint

/** AppCompatActivity (not plain ComponentActivity) so per-app language switching works on all API levels. */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PrmsTheme { AppRoot() }
        }
    }
}
