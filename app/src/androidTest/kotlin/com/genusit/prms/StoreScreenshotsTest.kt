package com.genusit.prms

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Google Play store screenshots of the real app (debug build pointed at a replay server with a made-up demo owner).
 * Runs only with the instrumentation argument storeShots=true (the store-screens workflow); skipped everywhere else.
 */
@RunWith(AndroidJUnit4::class)
class StoreScreenshotsTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(2500); rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/store-$name.png").close()
        Thread.sleep(800)
    }

    private fun waitText(text: String, timeout: Long = 20_000) =
        rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }

    private fun first(text: String): SemanticsNodeInteraction = rule.onAllNodesWithText(text, substring = true).onFirst()

    private fun back() { InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close(); Thread.sleep(1500); rule.waitForIdle() }

    private fun tab(label: String) { rule.onAllNodesWithText(label).onFirst().performClick(); rule.waitForIdle() }

    @Test fun captureStoreScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeShots") == "true")
        val ins = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) ins.uiAutomation.grantRuntimePermission(ins.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java)

        waitText("Username or email"); shot("08-login")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("rajesh")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("demo-password")
        rule.onNode(hasText("Sign in") and hasClickAction()).performClick()

        waitText("Hello"); waitText("Recent bills", 30_000)
        shot("01-home")

        tab("Tenants"); waitText("Amit Verma"); shot("02-tenants")

        tab("Bills"); waitText("Kavita Singh"); shot("03-bills")
        first("Kavita Singh").performClick(); waitText("Total"); shot("04-bill-detail")
        back()

        tab("Properties"); waitText("A-101"); shot("05-rooms")

        tab("More"); waitText("Reports")
        first("Reports").performScrollTo().performClick(); waitText("Collect", 20_000); shot("06-reports")
        back()
        waitText("Contact Support"); first("Contact Support").performScrollTo().performClick()
        waitText("Call Support"); shot("07-support")
        back()

        tab("Home"); waitText("Hello")
    }
}
