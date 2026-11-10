package com.genusit.prms

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasContentDescriptionExact
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipe
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

    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

    /** Lazy lists only build visible rows: swipe up until [text] appears. */
    private fun scrollTo(text: String) {
        repeat(10) {
            if (has(text)) {
                // bring it clear of the bottom navigation bar before tapping
                rule.onRoot().performTouchInput { swipe(center, center.copy(y = center.y - height * 0.25f), 300) }
                rule.waitForIdle(); Thread.sleep(400)
                if (!has(text)) rule.onRoot().performTouchInput { swipe(center, center.copy(y = center.y + height * 0.2f), 300) }
                return
            }
            rule.onRoot().performTouchInput { swipeUp() }; rule.waitForIdle(); Thread.sleep(400)
        }
        waitText(text, 5_000)
    }

    private fun toTop() { repeat(6) { rule.onRoot().performTouchInput { swipeDown() } }; rule.waitForIdle() }

    /** Waits for the first of [names] that shows up and returns it. */
    private fun waitAny(names: List<String>, timeout: Long = 20_000): String {
        var hit = ""
        rule.waitUntil(timeout) { names.firstOrNull { has(it) }?.also { hit = it } != null }
        return hit
    }

    private fun tab(label: String) {
        // bottom-bar items only (1.14: a tab has Role.Tab), so a same-named card on the screen is never tapped instead
        val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
        rule.onAllNodes(hasText(label) and isTab).onFirst().performClick(); rule.waitForIdle()
    }

    /** A string of the app in [lang] ("en", "hi", "mr"). */
    private fun str(id: Int, lang: String): String {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val conf = android.content.res.Configuration(ctx.resources.configuration).apply { setLocale(java.util.Locale(lang)) }
        return ctx.createConfigurationContext(conf).getString(id)
    }

    private fun signIn(user: String, lang: String) {
        rule.onAllNodes(hasSetTextAction())[0].performTextInput(user)
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("demo-password")
        rule.onNode(hasText(str(R.string.sign_in, lang)) and hasClickAction()).performClick()
    }

    /** Settings → Log out → confirm (the dialog's button is the last "Log out"). */
    private fun logOut(lang: String) {
        val label = str(R.string.logout, lang)
        scrollTo(label); first(label).performClick(); rule.waitForIdle()
        rule.onAllNodes(hasText(label) and hasClickAction()).onLast().performClick()
        waitText(str(R.string.username_or_email, lang), 20_000)
    }

    @Test fun captureStoreScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("storeShots") == "true")
        val ins = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) ins.uiAutomation.grantRuntimePermission(ins.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java)

        waitText("Username or email"); shot("08-login")
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("rajesh")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("demo-password")
        rule.onNode(hasText("Sign in") and hasClickAction()).performClick()

        waitText("Hello", 30_000); waitText("Collected", 30_000)
        shot("01-home")

        tab("Tenants"); waitText("Amit Verma"); shot("02-tenants")

        val names = listOf("Kavita Singh", "Amit Verma", "Priya Joshi", "Neha Gupta", "Sandeep Yadav", "Rohit Patel", "Manish Tiwari")
        tab("Bills"); val who = waitAny(names); shot("03-bills")
        first(who).performClick(); waitText("Total"); shot("04-bill-detail")
        back()

        tab("Properties"); waitText("A-101"); shot("05-rooms")

        tab("More"); waitText("More", 10_000); scrollTo("Reports")
        first("Reports").performClick(); waitText("Collect", 20_000); shot("06-reports")
        back()
        toTop(); scrollTo("Contact Support"); first("Contact Support").performClick()
        waitText("Call Support"); shot("07-support")
        back()

        // 1.14: the redesigned Add Tenant form and the employee / tenant screens, in Hindi and Marathi too
        tab("Tenants"); waitText("Amit Verma")
        rule.onNode(hasContentDescriptionExact("Add tenant")).performClick(); waitText("Tenant details"); shot("09-add-tenant")
        back()
        tab("More"); waitText("More", 10_000); scrollTo("Settings"); first("Settings").performClick(); waitText("Log out")
        logOut("en")

        // tenant (Hindi)
        rule.onAllNodesWithText("हिंदी").onFirst().performClick(); rule.waitForIdle(); waitText(str(R.string.sign_in, "hi")); shot("10-login-hi")
        signIn("priya.j", "hi")
        waitText(str(R.string.total_due, "hi"), 30_000); shot("11-tenant-home-hi")
        tab(str(R.string.nav_invoices, "hi")); Thread.sleep(1500); shot("12-tenant-bills-hi")
        tab(str(R.string.nav_payments, "hi")); shot("13-tenant-payments-hi")
        tab(str(R.string.complaints, "hi")); waitText("Water leakage"); shot("14-tenant-complaints-hi")
        tab(str(R.string.nav_more, "hi")); shot("15-tenant-more-hi")
        first(str(R.string.settings, "hi")).performClick(); waitText(str(R.string.logout, "hi"))
        logOut("hi")

        // collection agent (Marathi)
        rule.onAllNodesWithText("मराठी").onFirst().performClick(); rule.waitForIdle(); waitText(str(R.string.sign_in, "mr"))
        signIn("ramesh.k", "mr")
        waitText(str(R.string.collected_this_month, "mr"), 30_000); shot("16-agent-home-mr")
        tab(str(R.string.nav_collect, "mr")); waitText("Sandeep"); shot("17-agent-collect-mr")
        tab(str(R.string.nav_tenants, "mr")); waitText("Sandeep"); shot("18-agent-tenants-mr")
        tab(str(R.string.nav_history, "mr")); shot("19-agent-history-mr")
        tab(str(R.string.nav_more, "mr")); waitText(str(R.string.logout, "mr"), 10_000); shot("20-agent-more-mr")
        logOut("mr")
        rule.onAllNodesWithText("EN").onFirst().performClick(); rule.waitForIdle()
    }
}
