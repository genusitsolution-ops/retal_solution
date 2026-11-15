package com.genusit.prms

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.usecase.WhatsAppChoice.BUSINESS
import com.genusit.prms.domain.usecase.WhatsAppChoice.WHATSAPP
import com.genusit.prms.ui.common.WhatsAppAppCard
import com.genusit.prms.ui.common.WhatsAppApps
import com.genusit.prms.ui.common.WhatsAppChooserHost
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 1.15: WhatsApp / WhatsApp Business choice, on a real emulator with two stand-in apps installed under the real package
 * names (com.whatsapp, com.whatsapp.w4b — built by .github/scripts/wa-stubs.sh, they only accept wa.me links).
 * Intents are captured as Android starts them, so the exact phone number and message passed to the app are checked.
 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 26)
class WhatsAppChoiceUiTest {
    @get:Rule val rule = createComposeRule()

    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val ctx get() = inst.targetContext
    private val text = "Rent bill for November 2026: ₹3,760.00\nRoom R1 – please pay by the 10th."
    private val url = "https://wa.me/919876500101?text=" + Uri.encode(text)
    private val started = mutableListOf<Intent>()
    private var block = true

    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.`package` == WHATSAPP || intent.`package` == BUSINESS) {
                synchronized(started) { started += Intent(intent) }
                if (block) return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
            return null
        }
    }

    private fun sh(cmd: String): String =
        inst.uiAutomation.executeShellCommand(cmd).let { pfd -> android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText() }

    private fun install(vararg pkgs: String) {
        for (p in listOf(WHATSAPP, BUSINESS)) {
            if (p in pkgs) sh("pm install -r /data/local/tmp/${if (p == BUSINESS) "wa-business" else "wa"}-stub.apk")
            else sh("pm uninstall $p")
        }
        assertEquals(pkgs.toSet(), WhatsAppApps.installed(ctx))
    }

    @Before fun setUp() {
        assertTrue("stand-in apps missing: run .github/scripts/wa-stubs.sh first", sh("ls /data/local/tmp/").contains("wa-business-stub.apk"))
        inst.addMonitor(monitor)
        WhatsAppApps.save(ctx, null); WhatsAppApps.pending = null
    }

    @After fun tearDown() {
        inst.removeMonitor(monitor)
        WhatsAppApps.save(ctx, null); WhatsAppApps.pending = null
        sh("pm uninstall $WHATSAPP"); sh("pm uninstall $BUSINESS")
    }

    private fun showChooser() = rule.setContent { PrmsTheme { Surface { WhatsAppChooserHost() } } }

    private fun assertSent(pkg: String) {
        val i = synchronized(started) { started.last() }
        assertEquals(pkg, i.`package`)
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals(url, i.dataString)                                    // link passed on unchanged
        assertEquals("/919876500101", i.data!!.path)                       // tenant's number
        assertEquals(text, i.data!!.getQueryParameter("text"))             // message, ₹ and line break intact
    }

    @Test fun bothInstalledNothingSaved_asksOnce_thenUsesTheChoice() {
        install(WHATSAPP, BUSINESS)
        showChooser()
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        rule.onNodeWithText("Send with").assertExists()
        rule.onNodeWithText("Choose the app for WhatsApp messages", substring = true).assertExists()
        rule.onNodeWithText("WhatsApp Business").performClick()
        rule.waitForIdle()
        assertSent(BUSINESS)
        assertEquals(BUSINESS, WhatsAppApps.saved(ctx))
        // saved on the phone: in the app's preferences file, so it survives closing and reopening the app
        val file = File(ctx.applicationInfo.dataDir, "shared_prefs/whatsapp.xml")
        assertTrue(file.readText().contains(BUSINESS))
        // next message: no question, straight to WhatsApp Business
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        rule.waitForIdle()
        assertNull(WhatsAppApps.pending)
        assertEquals(2, started.size); assertSent(BUSINESS)
        rule.onAllNodes(hasText("Send with")).fetchSemanticsNodes().let { assertTrue(it.isEmpty()) }
    }

    @Test fun savedWhatsApp_opensWhatsApp() {
        install(WHATSAPP, BUSINESS)
        WhatsAppApps.save(ctx, WHATSAPP)
        showChooser()
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        rule.waitForIdle()
        assertNull(WhatsAppApps.pending)
        assertSent(WHATSAPP)
    }

    @Test fun savedAppUninstalled_offersTheOtherApp() {
        install(WHATSAPP)
        WhatsAppApps.save(ctx, BUSINESS)
        showChooser()
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        rule.onNodeWithText("WhatsApp Business is not installed on this phone. Send with:").assertExists()
        rule.onAllNodes(hasText("WhatsApp Business")).fetchSemanticsNodes().let { assertTrue("no button for the missing app", it.isEmpty()) }
        rule.onNodeWithText("WhatsApp").performClick()
        rule.waitForIdle()
        assertSent(WHATSAPP)
        assertEquals(WHATSAPP, WhatsAppApps.saved(ctx))
    }

    @Test fun cancelSendsNothingAndSavesNothing() {
        install(WHATSAPP, BUSINESS)
        showChooser()
        rule.runOnUiThread { WhatsAppApps.open(ctx, url) }
        rule.onNodeWithText("Cancel").performClick()
        rule.waitForIdle()
        assertTrue(started.isEmpty()); assertNull(WhatsAppApps.saved(ctx)); assertNull(WhatsAppApps.pending)
    }

    @Test fun onlyOneInstalledNothingSaved_opensItWithoutAsking() {
        install(BUSINESS)
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        assertNull(WhatsAppApps.pending)
        assertSent(BUSINESS)
        assertNull("nothing chosen by the owner, nothing saved", WhatsAppApps.saved(ctx))
    }

    @Test fun neitherInstalled_returnsFalseSoTheShareSheetIsUsed() {
        install()
        WhatsAppApps.save(ctx, BUSINESS)
        rule.runOnUiThread { assertFalse(WhatsAppApps.open(ctx, url)) }
        assertNull(WhatsAppApps.pending); assertTrue(started.isEmpty())
    }

    @Test fun settingsCard_savesTheChoiceAndMarksMissingApps() {
        install(WHATSAPP)
        rule.setContent { PrmsTheme { Surface { Column(Modifier.padding(16.dp)) { WhatsAppAppCard() } } } }
        rule.onNodeWithText("WhatsApp app for messages").assertExists()
        rule.onNodeWithText("Not chosen yet", substring = true).assertExists()
        rule.onNodeWithText("Not installed on this phone").assertExists()   // WhatsApp Business
        rule.onNode(hasText("WhatsApp Business")).assertIsNotEnabled()
        rule.onNodeWithText("WhatsApp").performClick()
        rule.onNodeWithText("WhatsApp").assertIsSelected()
        assertEquals(WHATSAPP, WhatsAppApps.saved(ctx))
        Thread.sleep(400)
        sh("screencap -p /data/local/tmp/whatsapp-settings.png")
    }

    @Test fun realLaunch_opensTheChosenApp() {
        install(WHATSAPP, BUSINESS)
        WhatsAppApps.save(ctx, BUSINESS)
        block = false
        rule.runOnUiThread { assertTrue(WhatsAppApps.open(ctx, url)) }
        assertSent(BUSINESS)
        // Android really opened the WhatsApp Business stand-in with the link
        val resumed = Regex("""(?:mResumedActivity|topResumedActivity|ResumedActivity)[^\n]*$BUSINESS/""")
        var top = ""
        for (n in 1..20) { top = sh("dumpsys activity activities"); if (resumed.containsMatchIn(top)) break; Thread.sleep(500) }
        sh("screencap -p /data/local/tmp/whatsapp-business-opened.png")
        assertTrue("WhatsApp Business not opened", resumed.containsMatchIn(top))
    }
}
