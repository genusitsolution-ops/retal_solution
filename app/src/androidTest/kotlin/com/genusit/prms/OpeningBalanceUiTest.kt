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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.Charge
import com.genusit.prms.domain.model.DepositSummary
import com.genusit.prms.domain.model.OpeningBalanceInfo
import com.genusit.prms.domain.model.OpeningLine
import com.genusit.prms.domain.model.Property
import com.genusit.prms.domain.model.SupportInfo
import com.genusit.prms.domain.model.TenantMoneySummary
import com.genusit.prms.ui.owner.ExistingTenantSection
import com.genusit.prms.ui.owner.SupportCard
import com.genusit.prms.ui.owner.TenantMoneyCard
import com.genusit.prms.ui.owner.previousAmount
import com.genusit.prms.ui.owner.supportDialIntent
import com.genusit.prms.ui.owner.supportWhatsAppIntent
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.12.1: simple existing-tenant form (previous outstanding, tenant since, room), Contact Support card, tenant money summary. */
@RunWith(AndroidJUnit4::class)
class OpeningBalanceUiTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(1200)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png").close()
    }

    @Test fun existingTenantFormAsksOnlyThreeDetails() {
        val prop = mutableStateOf(0)
        val since = mutableStateOf("2025-06-01")
        val prev = mutableStateOf("")
        val chosen = mutableStateMapOf<Int, Boolean>()
        val rooms = listOf(Property(id = 1, propertyCode = "R1", city = "Indore", baseRent = 4000.0, status = "available"))
        val charges = listOf(Charge(id = 4, name = "Electricity", chargeType = "fixed", fixedAmount = 500.0))
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExistingTenantSection(rooms, charges, prop.value, { prop.value = it }, since.value, { since.value = it }, chosen, { id, on -> chosen[id] = on },
                            prev.value, { prev.value = it }, tried = true)
                    }
                }
            }
        }
        rule.onAllNodesWithText("Previous Outstanding Balance (₹)").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("Tenant Since (YYYY-MM-DD)").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("Room Allocation").onFirst().performScrollTo().assertIsDisplayed()
        // no deposit fields and no month-wise lines any more
        assertEquals(0, rule.onAllNodesWithText("Deposit", substring = true).fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("Add line", substring = true).fetchSemanticsNodes().size)
        rule.runOnIdle { prev.value = "5000"; prop.value = 1 }
        rule.waitForIdle()
        shot("existing-tenant-form")
        assertEquals(5000.0, previousAmount(prev.value)!!, 0.001)
        assertEquals(0.0, previousAmount("0")!!, 0.001)       // zero is allowed
        assertNull(previousAmount(""))                           // empty is not
        assertNull(previousAmount("-5"))                         // negative is not
    }

    @Test fun supportCardShowsCallAndWhatsApp() {
        val wa = "https://wa.me/919876543211?text=Hello%2C%20I%20am%20using%20Rental%20Solution."
        val on = SupportInfo(enabled = true, configured = true, name = "Priya Support", phone = "98765 43210", whatsapp = "9876543211",
            hours = "Mon–Sat, 10 AM – 7 PM", available = true, availabilityLabel = "Available", telUrl = "tel:+919876543210", whatsappUrl = wa)
        val state = mutableStateOf(on)
        rule.setContent { PrmsTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { SupportCard(state.value) } } } }
        rule.onNodeWithText("Priya Support").assertIsDisplayed()
        rule.onNodeWithText("Call Support").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("WhatsApp Support").performScrollTo().assertIsDisplayed()
        shot("contact-support")
        // the buttons open the dialer / WhatsApp with exactly the server's number and message; nothing is dialled or sent
        val dial = supportDialIntent(on.telUrl!!)
        assertEquals(android.content.Intent.ACTION_DIAL, dial.action); assertEquals("tel:+919876543210", dial.dataString)
        val chat = supportWhatsAppIntent(on.whatsappUrl!!)
        assertEquals(android.content.Intent.ACTION_VIEW, chat.action); assertEquals(wa, chat.dataString)
        assertTrue(chat.data!!.getQueryParameter("text")!!.startsWith("Hello, I am using Rental Solution"))
        // the Super Admin switches support off: the actions disappear, a message is shown
        rule.runOnIdle { state.value = SupportInfo(enabled = false, configured = false, message = "Support contact is not available right now. Please try again later.") }
        rule.onNodeWithText("Support contact is not available right now. Please try again later.").assertIsDisplayed()
        assertEquals(0, rule.onAllNodesWithText("Call Support").fetchSemanticsNodes().size)
        assertEquals(0, rule.onAllNodesWithText("WhatsApp Support").fetchSemanticsNodes().size)
        shot("contact-support-off")
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
        shot("tenant-money-summary")
        rule.onAllNodesWithText("₹8,500.00").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("₹8,000.00").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("₹3,000.00").onFirst().performScrollTo().assertIsDisplayed()   // deposit pending on its own row
        rule.onAllNodesWithText("Opening Balance – Migrated · R1").onFirst().performScrollTo().assertIsDisplayed()
    }
}
