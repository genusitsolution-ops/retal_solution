package com.genusit.prms.domain

import com.genusit.prms.domain.util.RenewalRules
import com.genusit.prms.domain.util.RenewalRules.Problem
import com.genusit.prms.domain.util.UpiQr
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class RenewalRulesTest {
    private val today = LocalDate.of(2026, 10, 31)

    @Test fun utrRulesMatchTheServer() {
        assertEquals("412345678901", RenewalRules.normalizeUtr("4123 4567-8901"))
        assertTrue(RenewalRules.validUtr("4123 4567 8901"))
        assertTrue(RenewalRules.validUtr("neft-abc123"))
        assertFalse(RenewalRules.validUtr("12345"))
        assertFalse(RenewalRules.validUtr("1234567890123456789012345"))
        assertFalse(RenewalRules.validUtr("4123#45678901"))
    }

    @Test fun formProblems() {
        assertEquals(emptySet(), RenewalRules.problems("412345678901", "2026-10-31", 999.0, hasScreenshot = true, screenshotRequired = true, today = today))
        assertEquals(setOf(Problem.SCREENSHOT), RenewalRules.problems("412345678901", "2026-10-31", 999.0, false, true, today))
        assertEquals(emptySet(), RenewalRules.problems("412345678901", "2026-10-31", 999.0, false, false, today))
        assertEquals(setOf(Problem.DATE), RenewalRules.problems("412345678901", "2026-11-01", 999.0, true, true, today))
        assertEquals(setOf(Problem.DATE), RenewalRules.problems("412345678901", "2026-07-01", 999.0, true, true, today))
        assertEquals(setOf(Problem.UTR, Problem.AMOUNT), RenewalRules.problems("12", "2026-10-30", 0.0, true, true, today))
        assertTrue(Problem.AMOUNT in RenewalRules.problems("412345678901", "2026-10-30", null, true, true, today))
        assertTrue(RenewalRules.isShort(3000.0, 3538.82))
        assertFalse(RenewalRules.isShort(3538.82, 3538.82))
    }

    /** The QR code the app draws decodes back to exactly the server's UPI link, with the exact amount. */
    @Test fun qrCodeCarriesTheExactUpiLink() {
        val uri = "upi://pay?pa=rentalsol%40testbank&pn=Rental%20Solution%20Test&am=3538.82&cu=INR&tn=Subscription%20SUB-202610-0002-2FF0&tr=SUB-202610-0002-2FF0"
        val m = UpiQr.matrix(uri, 400)
        val px = IntArray(m.width * m.height) { i -> if (m.get(i % m.width, i / m.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(m.width, m.height, px)))).text
        assertEquals(uri, decoded)
        assertEquals(3538.82, UpiQr.amount(decoded)!!, 0.0001)
        assertEquals("rentalsol@testbank", UpiQr.param(decoded, "pa"))
        assertEquals("Rental Solution Test", UpiQr.param(decoded, "pn"))
        assertEquals("SUB-202610-0002-2FF0", UpiQr.param(decoded, "tr"))
        assertFailsWith<IllegalArgumentException> { UpiQr.matrix("https://example.com") }
    }
}
