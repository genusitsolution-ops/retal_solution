package com.genusit.prms

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.ui.owner.BillPreviewDialog
import com.genusit.prms.ui.owner.GenerateBillCard
import com.genusit.prms.ui.owner.PreviewLine
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Home "Generate bill" card and the bill preview (made-up tenant and amounts). */
@RunWith(AndroidJUnit4::class)
class BillUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun generateBillCardAndPreviewTotals() {
        var opened = 0; var saved = 0
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) { GenerateBillCard(onClick = { opened++ }) }
                    BillPreviewDialog(
                        tenant = "Test Tenant", room = "R1, Pune", period = "October 2026",
                        lines = listOf(PreviewLine("Rent", null, 3000.0), PreviewLine("Water", null, 200.0), PreviewLine("Electricity", "40 units × ₹8.00", 320.0)),
                        otherOutstanding = 750.0, saving = false, onConfirm = { saved++ }, onDismiss = {},
                    )
                }
            }
        }
        rule.onNodeWithText("₹3,520.00").assertIsDisplayed()          // 3000 + 200 + 40 × 8
        rule.onNodeWithText("40 units × ₹8.00").assertIsDisplayed()
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/bill-preview.png").close()
        rule.onNodeWithText("Save bill").performClick()
        assertEquals(1, saved)
        assertTrue(opened == 0)
    }
}
