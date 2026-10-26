package com.genusit.prms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.HandoverMethod
import com.genusit.prms.domain.model.SettlementSummary
import com.genusit.prms.ui.employee.HandoverHomeCard
import com.genusit.prms.ui.owner.MethodChips
import com.genusit.prms.ui.owner.SettlementCard
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Cash handover cards (made-up employee and amounts): submitted ≠ confirmed, partial and rejected are shown distinctly. */
@RunWith(AndroidJUnit4::class)
class HandoverUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun handoverCardsAndMethodChoice() {
        var opened = 0
        var method by mutableStateOf(HandoverMethod.CASH)
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        HandoverHomeCard(onClick = { opened++ })
                        SettlementCard(SettlementSummary(id = 1, settlementNumber = "ST-TEST-1", employeeName = "Test Agent", amountSubmitted = 3000.0,
                            status = "submitted", statusLabel = "Awaiting confirmation", handoverDate = "2026-10-26", methodLabel = "Cash"), showEmployee = true) {}
                        SettlementCard(SettlementSummary(id = 2, settlementNumber = "ST-TEST-2", employeeName = "Test Agent", amountSubmitted = 1000.0, amountConfirmed = 600.0,
                            status = "partially_confirmed", statusLabel = "Partly confirmed", handoverDate = "2026-10-26", methodLabel = "Cash"), showEmployee = true) {}
                        SettlementCard(SettlementSummary(id = 3, settlementNumber = "ST-TEST-3", employeeName = "Test Agent", amountSubmitted = 2000.0,
                            status = "rejected", statusLabel = "Rejected", reviewNote = "Not received in bank", handoverDate = "2026-10-26", methodLabel = "UPI"), showEmployee = true) {}
                        MethodChips(method) { method = it }
                    }
                }
            }
        }
        rule.onNodeWithText("Awaiting confirmation").assertIsDisplayed()
        rule.onNodeWithText("Confirmed ₹600.00").assertIsDisplayed()
        rule.onNodeWithText("Not received in bank").assertIsDisplayed()
        rule.onNodeWithText("Bank transfer").performClick()
        rule.waitForIdle()
        assertEquals(HandoverMethod.BANK_TRANSFER, method)
        rule.onNodeWithText("Hand over cash").performClick()
        assertEquals(1, opened)
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/handover-cards.png").close()
    }
}
