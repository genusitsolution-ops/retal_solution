package com.genusit.prms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.DepositSummary
import com.genusit.prms.domain.model.OpeningBalanceInfo
import com.genusit.prms.domain.model.OpeningLine
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.TenantMoneySummary
import com.genusit.prms.domain.util.OpeningRules
import com.genusit.prms.ui.owner.ExistingTenantSection
import com.genusit.prms.ui.owner.LineDraft
import com.genusit.prms.ui.owner.OpeningDraft
import com.genusit.prms.ui.owner.TenantMoneyCard
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.12: existing-tenant form (room, deposit, month-wise previous balance) and the tenant money summary. */
@RunWith(AndroidJUnit4::class)
class OpeningBalanceUiTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(1200)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png").close()
    }

    @Test fun existingTenantFormComputesTheOpeningBalance() {
        val draft = OpeningDraft()
        val prop = mutableStateOf(0)
        val join = mutableStateOf("2025-06-01")
        val chosen = mutableStateMapOf<Int, Boolean>()
        val rooms = listOf(Property(id = 1, propertyCode = "R1", city = "Indore", baseRent = 4000.0, status = "available"))
        val charges = listOf(Charge(id = 4, name = "Electricity", chargeType = "fixed", fixedAmount = 500.0))
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExistingTenantSection(rooms, charges, prop.value, { prop.value = it }, join.value, { join.value = it }, chosen, { id, on -> chosen[id] = on },
                            draft, tried = true, problems = draft.problems(join.value, withDeposit = true))
                    }
                }
            }
        }
        rule.runOnIdle {
            draft.asOf = "2026-10-31"; draft.billingStart = "2026-11"
            draft.lines.clear()
            draft.lines += LineDraft("rent", "2026-09", "", "4000")
            draft.lines += LineDraft("rent", "2026-10", "", "4000")
            draft.lines += LineDraft("electricity", "2026-10", "", "500")
            draft.depositAgreed = "8000"; draft.depositCollected = "5000"
        }
        rule.waitForIdle()
        rule.onAllNodesWithText("₹8,500.00").onFirst().performScrollTo().assertIsDisplayed()       // total previous outstanding
        rule.onAllNodesWithText("₹3,000.00").onFirst().performScrollTo().assertIsDisplayed() // deposit pending
        shot("existing-tenant-form")
        val input = draft.toInput(withDeposit = true)
        assertEquals(8500.0, OpeningRules.totals(input.lines).total, 0.001)
        assertEquals(5000.0, input.depositCollected!!, 0.001)
        assertTrue(draft.problems("2025-06-01", withDeposit = true).isEmpty())
        // a month billed in Rental Solution cannot be part of the previous balance
        rule.runOnIdle { draft.lines += LineDraft("rent", "2026-11", "", "4000") }
        assertTrue(OpeningRules.Problem.LINE_MONTH_BILLED in draft.problems("2025-06-01", withDeposit = true))
    }

    @Test fun moneySummaryOfAMigratedTenant() {
        val summary = TenantMoneySummary("existing", "Existing tenant (migrated)", 8500.0, 3500.0, 4500.0, 5000.0, 8000.0,
            DepositSummary(1, 8000.0, 5000.0, 3000.0, 0.0, 0.0, 5000.0), "2026-11-02")
        val ob = OpeningBalanceInfo(id = 1, allocationId = 1, label = "Opening Balance – Migrated", propertyCode = "R1", asOfDate = "2026-10-31",
            billingStart = "2026-11", billingStartLabel = "November 2026", totalOutstanding = 8500.0, paid = 5000.0, remaining = 3500.0,
            lines = listOf(OpeningLine("rent", "2026-09", "", 4000.0, "Rent", "September 2026"), OpeningLine("rent", "2026-10", "", 4000.0, "Rent", "October 2026"),
                OpeningLine("electricity", "2026-10", "", 500.0, "Electricity", "October 2026")))
        var opened = 0
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                        TenantMoneyCard(summary, listOf(ob), 1, openOpening = { opened = it }, recordOpening = {})
                    }
                }
            }
        }
        rule.onAllNodesWithText("Existing tenant (migrated)").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("₹8,500.00").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("₹8,000.00").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("₹3,000.00").onFirst().performScrollTo().assertIsDisplayed()   // deposit pending on its own row
        rule.onAllNodesWithText("Opening Balance – Migrated · R1").onFirst().performScrollTo().assertIsDisplayed()
        shot("tenant-money-summary")
    }
}
