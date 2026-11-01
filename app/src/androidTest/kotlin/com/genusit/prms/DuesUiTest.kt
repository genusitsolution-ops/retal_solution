package com.genusit.prms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.DueMonth
import com.genusit.prms.domain.model.HandoverBalance
import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.model.InvoiceItem
import com.genusit.prms.domain.model.TenantDues
import com.genusit.prms.domain.model.UpiApp
import com.genusit.prms.domain.model.UpiDetails
import com.genusit.prms.ui.common.TenantDuesRow
import com.genusit.prms.ui.employee.HandoverHomeCard
import com.genusit.prms.ui.owner.BillShareActions
import com.genusit.prms.ui.owner.InvoiceBody
import com.genusit.prms.ui.owner.UpiQrPanel
import com.genusit.prms.ui.theme.PrmsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.10 screens with server-shaped values (made-up tenant): carry-forward bill, dues by tenant, employee cash, UPI app buttons. */
@RunWith(AndroidJUnit4::class)
class DuesUiTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(1200)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png").close()
    }

    private val nov = InvoiceDetail(
        id = 12, invoiceNumber = "INV-202611-0012", billingMonth = 11, billingYear = "2026", invoiceDate = "2026-11-01", dueDate = "2026-11-05",
        totalAmount = 3334.0, paidAmount = 0.0, balanceAmount = 3334.0, status = "pending", statusLabel = "Pending", periodLabel = "November 2026",
        tenantName = "Test Tenant", tenantPhone = "98765 43210", propertyCode = "R-101", city = "Pune", ownerName = "Test Owner",
        items = listOf(InvoiceItem(id = 1, itemType = "base_rent", description = "Base Rent", amount = 3000.0), InvoiceItem(id = 2, itemType = "fixed", description = "Water", amount = 334.0)),
        previousDues = listOf(DueMonth(invoiceId = 11, invoiceNumber = "INV-202610-0011", periodLabel = "October 2026", balance = 2400.0, status = "partial")),
        previousBalance = 2400.0, currentBalance = 3334.0, totalPayable = 5734.0, billUrl = "https://example.test/bill.php?i=12&k=abc",
    )

    @Test fun billShowsPreviousBalanceAndTotalPayable() {
        rule.setContent {
            PrmsTheme { Surface(Modifier.fillMaxSize()) { InvoiceBody(nov, showTenant = true, openReceipt = {}) { BillShareActions(nov) } } }
        }
        rule.onNodeWithText("Previous balance (October 2026)").assertIsDisplayed()
        rule.onAllNodesWithText("₹5,734.00").onFirst().assertIsDisplayed()
        rule.onAllNodesWithText("Total payable").onFirst().assertIsDisplayed()
        rule.onNodeWithText("Payments are applied to the oldest month first.").assertExists()
        shot("bill-carry-forward")
        // the buttons are further down the (lazy) list: scroll to them
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Share via WhatsApp"))
        rule.onNodeWithText("Share via WhatsApp").assertIsDisplayed()
        rule.onNodeWithText("Print / PDF").assertIsDisplayed()
        shot("bill-whatsapp-button")
    }

    @Test fun duesRowAndEmployeeCash() {
        val opened = mutableListOf<Int>()
        val d = TenantDues(tenantId = 4, tenantName = "Test Tenant", propertyCode = "R-101", totalDue = 5734.0, openCount = 2, status = "overdue",
            statusLabel = "Overdue", latestInvoiceId = 12, monthsLabel = "October 2026, November 2026")
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HandoverHomeCard({}, HandoverBalance(collections = 3, collected = 9000.0, confirmed = 5000.0, awaitingConfirmation = 1500.0, outstanding = 4000.0, available = 2500.0))
                        TenantDuesRow(d, onClick = { opened += d.latestInvoiceId })
                    }
                }
            }
        }
        rule.onNodeWithText("Cash with you").assertIsDisplayed()
        rule.onNodeWithText("₹9,000.00").assertIsDisplayed()
        rule.onNodeWithText("₹4,000.00").assertIsDisplayed()
        rule.onNodeWithText("Hand over cash · ₹2,500.00").assertIsDisplayed()
        rule.onNodeWithText("₹5,734.00").assertIsDisplayed()
        rule.onNodeWithText("R-101 · 2 unpaid bills").assertIsDisplayed()
        shot("employee-cash-dues")
        rule.onNodeWithText("Test Tenant").performClick()
        assertEquals(listOf(12), opened)
    }

    @Test fun nothingToHandOverDisablesButton() {
        rule.setContent { PrmsTheme { Surface { HandoverHomeCard({}, HandoverBalance(collected = 1000.0, confirmed = 1000.0)) } } }
        rule.onNodeWithText("There is no collected money left to hand over.").assertIsNotEnabled()
    }

    @Test fun upiAppButtonsUseTheInvoiceAmount() {
        val uri = "upi://pay?pa=9000000000%40ybl&pn=Rental%20Solution%20Test&am=3538.82&cu=INR&tn=Subscription%20SUB-1"
        val upi = UpiDetails(upiId = "9000000000@ybl", payee = "Rental Solution Test", amount = 3538.82, reference = "SUB-1", uri = uri, isMerchant = false,
            apps = listOf(UpiApp("phonepe", "PhonePe", "com.phonepe.app"), UpiApp("gpay", "Google Pay", "com.google.android.apps.nbu.paisa.user"), UpiApp("paytm", "Paytm", "net.one97.paytm")))
        rule.setContent { PrmsTheme { Surface(Modifier.fillMaxSize()) { Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) { UpiQrPanel(upi) } } } }
        rule.onNodeWithText("Pay using PhonePe").assertExists()
        rule.onNodeWithText("Pay using Google Pay").assertExists()
        rule.onNodeWithText("Pay using Paytm").assertExists()
        rule.onNodeWithText("Other UPI app").assertExists()
        rule.onNodeWithText("Save / share QR image").assertExists()
        shot("upi-app-buttons")
    }
}
