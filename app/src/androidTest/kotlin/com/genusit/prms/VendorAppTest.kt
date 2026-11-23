package com.genusit.prms

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 1.17 independent vendor, end to end in the real app against the replay server (made-up vendor VEN10001 recorded from
 * the 1.17 server): sign in with the vendor code → forced change of the temporary password → vendor dashboard only.
 * Checks that no owner tab or owner screen exists, opens every vendor tab and logs out.
 * Runs only with the instrumentation argument vendorE2E=true (vendor-e2e workflow); skipped everywhere else.
 */
@RunWith(AndroidJUnit4::class)
class VendorAppTest {
    @get:Rule val rule = createEmptyComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(1500); rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/vendor-$name.png").close()
        Thread.sleep(600)
    }
    private fun has(text: String) = rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasTag(tag: String) = rule.onAllNodes(hasTestTag(tag)).fetchSemanticsNodes().isNotEmpty()
    private fun waitText(text: String, timeout: Long = 20_000) = rule.waitUntil(timeout) { has(text) }
    private fun waitTag(tag: String, timeout: Long = 20_000) = rule.waitUntil(timeout) { hasTag(tag) }
    private fun str(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)
    private val isTab = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
    private fun tabLabels(): List<String> = rule.onAllNodes(isTab).fetchSemanticsNodes().mapNotNull { n ->
        n.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }
    }
    private fun tab(label: String) { rule.onAllNodes(hasText(label) and isTab).onFirst().performClick(); rule.waitForIdle() }

    @Test fun vendorSeesOnlyVendorScreens() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("vendorE2E") == "true")
        val ins = InstrumentationRegistry.getInstrumentation()
        if (android.os.Build.VERSION.SDK_INT >= 33) ins.uiAutomation.grantRuntimePermission(ins.targetContext.packageName, android.Manifest.permission.POST_NOTIFICATIONS)
        ActivityScenario.launch(MainActivity::class.java)

        // Test 3: the vendor signs in to the existing app with the vendor code
        waitText(str(R.string.sign_in))
        rule.onAllNodes(hasSetTextAction())[0].performTextInput("VEN10001")
        rule.onAllNodes(hasSetTextAction())[1].performTextInput("demo-password")
        rule.onNode(hasText(str(R.string.sign_in)) and hasClickAction()).performClick()

        // temporary password from the Super Admin → must be changed first (no dashboard, no tabs yet)
        waitTag("vendor_password", 30_000); shot("01-change-temp-password")
        assertTrue("no bottom bar before the password is changed", tabLabels().isEmpty())
        val fields = rule.onAllNodes(hasSetTextAction())
        fields[0].performTextInput("Temp@1234"); fields[1].performTextInput("Vend@5678"); fields[2].performTextInput("Vend@5678")
        rule.onNode(hasTestTag("vendor_password_save")).performClick()

        // Test 4: only the vendor dashboard
        waitTag("vendor_home", 30_000); waitTag("vendor_code"); waitText("VEN10001")
        assertTrue(has("Test Vendor One"))
        shot("02-dashboard")
        val tabs = tabLabels()
        assertTrue("vendor tabs: $tabs", tabs == listOf(str(R.string.nav_home), str(R.string.vendor_nav_referrals), str(R.string.vendor_nav_commission),
            str(R.string.vendor_nav_payouts), str(R.string.vendor_nav_profile)))
        // Test 5: no owner menu anywhere
        for (owner in listOf(R.string.nav_tenants, R.string.nav_invoices, R.string.nav_properties, R.string.nav_more)) {
            assertFalse("owner tab ${str(owner)} must not exist", tabs.contains(str(owner)))
        }

        tab(str(R.string.vendor_nav_referrals)); waitTag("vendor_referrals"); shot("03-referrals")
        tab(str(R.string.vendor_nav_commission)); waitTag("vendor_commission"); shot("04-commission")
        tab(str(R.string.vendor_nav_payouts)); waitTag("vendor_payouts"); waitText("UTRTEST001"); shot("05-payouts")
        tab(str(R.string.vendor_nav_profile)); waitTag("vendor_profile"); shot("06-profile")
        rule.onNode(hasTestTag("vendor_open_payment")).performScrollTo().performClick(); waitTag("vendor_payment"); shot("07-payment-details")
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("input keyevent 4").close(); Thread.sleep(1200)

        // Test 5 (deep links): an owner screen opened from a notification route is not reachable for a vendor
        assertTrue(com.genusit.prms.domain.util.NotificationRoutes.target("vendor", "invoice/1") == null)

        // logout from My Profile
        waitTag("vendor_profile")
        rule.onNode(hasTestTag("vendor_logout")).performScrollTo().performClick()
        val inDialog = hasText(str(R.string.logout)) and hasClickAction() and hasAnyAncestor(isDialog())
        rule.waitUntil(10_000) { rule.onAllNodes(inDialog).fetchSemanticsNodes().isNotEmpty() }
        rule.onAllNodes(inDialog).onFirst().performClick()
        waitText(str(R.string.sign_in), 30_000); shot("08-logged-out")
    }
}
