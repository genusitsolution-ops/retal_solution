package com.genusit.prms.domain

import com.genusit.prms.domain.util.PaymentScreenshotParser
import com.genusit.prms.domain.util.RenewalRules
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.10: values read from typical UPI screenshots (text as on-device OCR returns it, made-up numbers). */
class PaymentScreenshotParserTest {

    @Test fun phonePeSuccess() {
        val r = PaymentScreenshotParser.parse(listOf(
            "Transaction Successful", "10:42 pm on 28 Oct 2026", "Paid to", "RENTAL SOLUTION", "₹3,538.82",
            "Transfer Details", "Transaction ID", "T2610282242118812345678", "UTR: 430112345678", "Debited from", "XXXXXX1234", "PhonePe",
        ))
        assertEquals("430112345678", r.utr); assertEquals(3538.82, r.amount); assertEquals("2026-10-28", r.paidOn)
        assertEquals("success", r.status); assertEquals("phonepe", r.app)
        assertTrue(RenewalRules.validUtr(r.utr!!))
    }

    @Test fun googlePayCompleted() {
        val r = PaymentScreenshotParser.parse(listOf(
            "To Rental Solution", "₹1,499", "Completed", "Oct 30, 2026, 9:15 am", "UPI transaction ID", "4302 1987 6543", "To: rentals@okaxis", "Google Pay",
        ))
        assertEquals("430219876543", r.utr); assertEquals(1499.0, r.amount); assertEquals("2026-10-30", r.paidOn)
        assertEquals("success", r.status); assertEquals("gpay", r.app)
    }

    @Test fun paytmWithRsAndNumericDate() {
        val r = PaymentScreenshotParser.parse(listOf("Paytm", "Payment Successful", "Rs. 2500.00", "UPI Ref No: 431055512345", "31/10/2026 08:01 PM"))
        assertEquals("431055512345", r.utr); assertEquals(2500.0, r.amount); assertEquals("2026-10-31", r.paidOn); assertEquals("paytm", r.app)
    }

    @Test fun failedAndPendingAreReported() {
        assertEquals("failed", PaymentScreenshotParser.parse(listOf("Payment Failed", "₹999", "PhonePe")).status)
        assertEquals("failed", PaymentScreenshotParser.parse(listOf("Payment declined for security reasons", "PhonePe")).status)
        assertEquals("pending", PaymentScreenshotParser.parse(listOf("Payment Pending", "₹999")).status)
    }

    @Test fun phoneNumberIsNotAUtrAndNothingIsInvented() {
        val r = PaymentScreenshotParser.parse(listOf("Call 9876543210 for help", "Hello"))
        assertNull(r.utr); assertNull(r.amount); assertNull(r.paidOn); assertNull(r.status)
        assertTrue(r.isEmpty)
    }

    @Test fun transactionIdUsedWhenNoUtr() {
        val r = PaymentScreenshotParser.parse(listOf("Transaction ID", "T2610282242118812345678"))
        assertEquals("T2610282242118812345678".take(22), r.utr)
    }
}
