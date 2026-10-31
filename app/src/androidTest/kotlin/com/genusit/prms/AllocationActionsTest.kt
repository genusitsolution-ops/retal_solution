package com.genusit.prms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.ui.owner.AllocationActions
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Allocation card actions on a narrow card: every label stays on one line and every button is clickable. */
@RunWith(AndroidJUnit4::class)
class AllocationActionsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun vacateStaysOnOneLine() {
        val clicks = mutableListOf<String>()
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    // 280 dp ≈ the card content width on a small phone
                    Column(Modifier.padding(16.dp).width(280.dp)) {
                        AllocationActions(depositPending = true, onBill = { clicks += "bill" }, onDeposit = { clicks += "deposit" }, onVacate = { clicks += "vacate" })
                    }
                }
            }
        }
        val vacate = rule.onNodeWithText("Vacate").assertIsDisplayed().getUnclippedBoundsInRoot()
        val bill = rule.onNodeWithText("Generate bill").assertIsDisplayed().getUnclippedBoundsInRoot()
        // one text line: about as tall as the "Generate bill" label, and wider than tall
        val vw = vacate.right - vacate.left; val vh = vacate.bottom - vacate.top; val bh = bill.bottom - bill.top
        assertTrue("Vacate wraps (h=$vh, w=$vw)", vw > vh && vh <= bh + 1.dp)
        rule.onNodeWithText("Collect deposit").assertIsDisplayed()
        Thread.sleep(300)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/allocation-actions.png").close()
        rule.onNodeWithText("Generate bill").performClick(); rule.onNodeWithText("Collect deposit").performClick(); rule.onNodeWithText("Vacate").performClick()
        assertEquals(listOf("bill", "deposit", "vacate"), clicks)
    }
}
