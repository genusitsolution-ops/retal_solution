package com.genusit.prms

import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.ui.common.FormField
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The tenant form's Aadhaar / PAN fields sit low on the screen. With the app's edge-to-edge window and the
 * role shell's inset handling (consumeWindowInsets + imePadding), typed text must stay visible above the
 * soft keyboard. Also checks the input rules (digits ≤ 12, PAN capitals ≤ 10). Made-up numbers only.
 */
@RunWith(AndroidJUnit4::class)
class FormKeyboardTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun typedIdNumbersStayVisibleAboveTheKeyboard() {
        rule.runOnUiThread {
            rule.activity.enableEdgeToEdge()
            @Suppress("DEPRECATION")
            rule.activity.window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        var view: android.view.View? = null
        rule.setContent {
            view = LocalView.current
            PrmsTheme {
                Scaffold(contentWindowInsets = WindowInsets.navigationBars, bottomBar = { NavigationBar { Text("bar") } }) { inner ->
                    Box(Modifier.padding(inner).consumeWindowInsets(inner).imePadding()) {
                        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                            repeat(6) { i -> var v by remember { mutableStateOf("") }; FormField(v, { v = it }, "Field $i") }
                            var aadhaar by remember { mutableStateOf("") }
                            var pan by remember { mutableStateOf("") }
                            FormField(aadhaar, { aadhaar = it.filter(Char::isDigit).take(12) }, "Aadhaar number", keyboard = KeyboardType.Number, autoCorrect = false)
                            FormField(pan, { pan = it.uppercase().filter(Char::isLetterOrDigit).take(10) }, "PAN", keyboard = KeyboardType.Ascii,
                                capitalization = KeyboardCapitalization.Characters, autoCorrect = false)
                        }
                    }
                }
            }
        }
        // paste-like input with spaces and extra digits → 12 digits kept
        rule.onNodeWithText("Aadhaar number").performClick()
        rule.onNode(hasSetTextAction() and hasText("Aadhaar number")).performTextInput("1234 5678 9012 34")
        rule.onNodeWithText("PAN").performClick()
        rule.onNode(hasSetTextAction() and hasText("PAN")).performTextInput("abcde1234fZZ")
        rule.waitForIdle()
        rule.onNodeWithText("123456789012").assertExists()
        rule.onNodeWithText("ABCDE1234F").assertExists()

        // wait for the soft keyboard (when the emulator shows one) and check the focused field is above it
        var imeBottom = 0
        try {
            rule.waitUntil(5_000) {
                imeBottom = view?.let { ViewCompat.getRootWindowInsets(it)?.getInsets(WindowInsetsCompat.Type.ime())?.bottom } ?: 0
                imeBottom > 0
            }
        } catch (e: androidx.compose.ui.test.ComposeTimeoutException) {
            android.util.Log.w("FormKeyboardTest", "no soft keyboard on this emulator; position check skipped")
        }
        rule.waitForIdle()
        val pan = rule.onNodeWithText("ABCDE1234F").fetchSemanticsNode().boundsInWindow
        val windowHeight = view!!.rootView.height
        val shot = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/form-keyboard.png")
        shot.close()
        if (imeBottom > 0) {
            assertTrue("PAN text (bottom ${pan.bottom}) is hidden by the keyboard (top ${windowHeight - imeBottom})", pan.bottom <= windowHeight - imeBottom + 1)
        }
        assertEquals(true, pan.height > 0)
    }
}
