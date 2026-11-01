package com.genusit.prms

import android.provider.MediaStore
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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.genusit.prms.domain.model.DepositSummary
import com.genusit.prms.domain.model.DepositTxn
import com.genusit.prms.domain.model.SettlementParty
import com.genusit.prms.domain.model.SettlementPlace
import com.genusit.prms.domain.model.SettlementStatement
import com.genusit.prms.domain.model.StatementLine
import com.genusit.prms.domain.model.UpiDetails
import com.genusit.prms.ui.owner.DepositFigures
import com.genusit.prms.ui.owner.DepositTxnRow
import com.genusit.prms.ui.owner.QrImage
import com.genusit.prms.ui.owner.SettlementStatementCard
import com.genusit.prms.ui.theme.PrmsTheme
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.11: deposit figures, settlement statement, labelled QR saved to the Gallery and still scannable. */
@RunWith(AndroidJUnit4::class)
class SettlementUiTest {
    @get:Rule val rule = createComposeRule()

    private fun shot(name: String) {
        rule.waitForIdle(); Thread.sleep(1200)
        InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/$name.png").close()
    }

    // figures from the server's statement for the request's example (deposit ₹8,000 agreed, ₹6,000 collected)
    private val st = SettlementStatement(
        allocationId = 1, tenant = SettlementParty(1, "Test Tenant", "9876543210"), property = SettlementPlace(1, "R-101", "Pune"),
        allocationDate = "2026-09-01", vacateDate = "2026-11-01", rentBilled = 8734.0, rentPaid = 3000.0,
        previousOutstanding = 2400.0, currentOutstanding = 3334.0, outstanding = 5734.0, additionalCharges = 500.0,
        deposit = DepositSummary(1, 8000.0, 6000.0, 2000.0, 0.0, 0.0, 6000.0), depositAvailable = 6000.0,
        depositAppliedRent = 5734.0, depositAppliedCharges = 266.0, refundAmount = 0.0, tenantPayable = 234.0, finalAmount = -234.0,
        result = "payable", resultLabel = "Tenant pays ₹234.00",
        lines = listOf(
            StatementLine("deposit_agreed", "Security deposit agreed", 8000.0), StatementLine("deposit_collected", "Security deposit collected", 6000.0),
            StatementLine("deposit_pending", "Security deposit not collected", 2000.0), StatementLine("deposit_available", "Deposit held", 6000.0, "credit"),
            StatementLine("previous_outstanding", "Previous outstanding rent", 2400.0, "due"), StatementLine("current_outstanding", "Current outstanding rent", 3334.0, "due"),
            StatementLine("charge", "Painting — Wall repaint", 500.0, "due"), StatementLine("adjust_rent", "Deposit adjusted against rent", 5734.0),
            StatementLine("adjust_charges", "Deposit adjusted against other charges", 266.0), StatementLine("refund", "Refund payable to tenant", 0.0, "total"),
            StatementLine("payable", "Amount payable by tenant", 234.0, "total"), StatementLine("final", "Tenant pays ₹234.00", 234.0, "total"),
        ),
    )

    @Test fun depositAndStatement() {
        rule.setContent {
            PrmsTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DepositFigures(DepositSummary(1, 8000.0, 5000.0, 3000.0, 0.0, 0.0, 5000.0))
                        DepositTxnRow(DepositTxn(1, "collect", "Deposit collected", 5000.0, "2026-09-01", "cash", "Cash", null, null, "in", 5000.0))
                        SettlementStatementCard(st, "Room vacate settlement")
                    }
                }
            }
        }
        rule.onNodeWithText("₹8,000.00").assertIsDisplayed()
        rule.onNodeWithText("₹3,000.00").assertIsDisplayed()           // pending deposit (request's example)
        rule.onNodeWithText("Previous outstanding rent").assertExists()
        rule.onNodeWithText("Painting — Wall repaint").assertExists()
        rule.onNodeWithTag("settlement_result").assertExists()
        rule.onNodeWithText("Tenant pays ₹234.00").assertExists()
        shot("settlement-statement")
    }

    @Test fun labelledQrIsScannableAndSavedToGallery() {
        val uri = "upi://pay?pa=9000000000%40ybl&pn=Rental%20Solution%20Test&am=3538.82&cu=INR&tn=Subscription%20SUB-1"
        val upi = UpiDetails(upiId = "9000000000@ybl", payee = "Rental Solution Test", amount = 3538.82, reference = "SUB-1", uri = uri)
        val b = QrImage.labelled(upi)
        val px = IntArray(b.width * b.height); b.getPixels(px, 0, b.width, 0, 0, b.width, b.height)
        val text = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(b.width, b.height, px)))).text
        assertEquals("the saved picture decodes to exactly the server's link (amount ₹3,538.82)", uri, text)
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val saved = QrImage.saveToGallery(ctx, b, "upi-qr-test-" + System.currentTimeMillis())
        assertNotNull("saved through MediaStore", saved)
        ctx.contentResolver.query(saved!!, arrayOf(MediaStore.Images.Media.DISPLAY_NAME, MediaStore.Images.Media.SIZE), null, null, null).use { c ->
            assertTrue(c != null && c.moveToFirst() && c.getLong(1) > 1000)
        }
        // read back from the Gallery and decode again
        val back = ctx.contentResolver.openInputStream(saved).use { android.graphics.BitmapFactory.decodeStream(it) }
        val px2 = IntArray(back.width * back.height); back.getPixels(px2, 0, back.width, 0, 0, back.width, back.height)
        assertEquals(uri, QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(back.width, back.height, px2)))).text)
        ctx.contentResolver.delete(saved, null, null)
    }
}
