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
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.PayInvoice
import com.genusit.prms.domain.model.PaymentOptions
import com.genusit.prms.domain.model.PayMethods
import com.genusit.prms.domain.model.PlanDisplayStatus
import com.genusit.prms.domain.model.PlanPackage
import com.genusit.prms.domain.model.Quote
import com.genusit.prms.domain.model.SubscriptionStatus
import com.genusit.prms.domain.model.UpiDetails
import com.genusit.prms.domain.model.Usage
import com.genusit.prms.ui.owner.PackageCard
import com.genusit.prms.ui.owner.PaymentBreakdown
import com.genusit.prms.ui.owner.PlanHero
import com.genusit.prms.ui.owner.UpiQrPanel
import com.genusit.prms.ui.owner.qrBitmap
import com.genusit.prms.ui.theme.PrmsTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Subscription renewal screens with the values the server sends (amounts are the server's, not the app's). */
@RunWith(AndroidJUnit4::class)
class SubscriptionUiTest {
    @get:Rule val rule = createComposeRule()

    private val uri = "upi://pay?pa=rentalsol%40testbank&pn=Rental%20Solution%20Test&am=3538.82&cu=INR&tn=Subscription%20SUB-202610-0002-2FF0&tr=SUB-202610-0002-2FF0"
    private val pro = PlanPackage(id = 3, name = "Professional", features = listOf("Unlimited receipts", "Priority support"), maxProperties = 50, maxTenants = 500,
        monthlyPrice = 2999.0, durationDays = 90, taxPercent = 18.0, isFeatured = true,
        quote = Quote(packageId = 3, packageName = "Professional", cycleDays = 90, includedUnits = 50, baseAmount = 2999.0, taxPercent = 18.0, taxAmount = 539.82, totalAmount = 3538.82))

    @Test fun planStatusAndPackages() {
        val chosen = mutableListOf<Int>()
        val s = SubscriptionStatus(state = "trial", isTrial = true, packageName = "Starter", startDate = "2026-10-30", endDate = "2026-12-28", daysLeft = 58,
            usage = Usage(properties = 2, tenants = 2, maxProperties = 6, maxTenants = 30), packageId = 2, packages = listOf(pro),
            displayStatus = PlanDisplayStatus("verification_pending", null, listOf("verification_pending", "trial")))
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PlanHero(s)
                        PackageCard(pro, "GST", current = false, choosing = false, isOpen = false) { chosen += pro.id }
                    }
                }
            }
        }
        rule.onNodeWithText("Starter").assertIsDisplayed()
        rule.onNodeWithText("Admin verification pending").assertIsDisplayed()
        rule.onNodeWithText("Trial").assertIsDisplayed()
        rule.onNodeWithText("58").assertIsDisplayed()
        rule.onNodeWithText("₹3,538.82").assertIsDisplayed()
        rule.onNodeWithText("GST 18%").assertIsDisplayed()
        rule.onNodeWithText("Priority support").assertIsDisplayed()
        Thread.sleep(300)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/subscription-plans.png").close()
        rule.onNodeWithText("Choose this plan").performClick()
        assertEquals(listOf(3), chosen)
    }

    @Test fun paymentBreakdownAndUpiQr() {
        val upi = UpiDetails(upiId = "rentalsol@testbank", payee = "Rental Solution Test", amount = 3538.82, reference = "SUB-202610-0002-2FF0", uri = uri)
        val p = PaymentOptions(invoice = PayInvoice(id = 5, invoiceNumber = "SUB-202610-0002-2FF0", packageName = "Professional", durationDays = 90,
            periodStart = "2026-12-29", periodEnd = "2027-03-28", includedUnits = 50, baseAmount = 2999.0, taxPercent = 18.0, taxAmount = 539.82, totalAmount = 3538.82),
            methods = PayMethods(manualUpi = true, gateway = false), upi = upi)
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PaymentBreakdown(p)
                        UpiQrPanel(upi)
                    }
                }
            }
        }
        rule.onNodeWithTag("pay_total").assertIsDisplayed()
        rule.onNodeWithText("Professional").assertIsDisplayed()
        rule.onNodeWithText("90 days").assertIsDisplayed()
        rule.waitUntil(5000) { runCatching { rule.onNodeWithContentDescription("UPI QR code for ₹3,538.82").assertExists(); true }.getOrDefault(false) }
        rule.onNodeWithText("rentalsol@testbank").assertExists()
        rule.onNodeWithText("Open UPI app").assertExists()
        Thread.sleep(500)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/subscription-pay.png").close()
    }

    /** The bitmap the app draws decodes back to exactly the server's link, so UPI apps get the exact amount. */
    @Test fun qrBitmapDecodesToTheServerLink() {
        val b = qrBitmap(uri)
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(b.width, b.height, px)))).text
        assertEquals(uri, text)
        assertTrue(text.contains("am=3538.82") && text.contains("cu=INR"))
    }
}
