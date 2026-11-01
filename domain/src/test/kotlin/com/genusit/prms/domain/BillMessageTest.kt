package com.genusit.prms.domain

import com.genusit.prms.domain.model.DueMonth
import com.genusit.prms.domain.model.InvoiceDetail
import com.genusit.prms.domain.model.InvoiceItem
import com.genusit.prms.domain.util.BillMessage
import com.genusit.prms.domain.util.BillMessageLabels
import com.genusit.prms.domain.util.ReceiptMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 1.10: the WhatsApp bill text carries the previous months and the total payable (example from the request). */
class BillMessageTest {
    private val labels = BillMessageLabels("Rent bill", "Tenant", "Room", "Invoice No.", "Due date", "This month", "Paid",
        "Previous balance", "Total payable", "View bill", "Base rent")

    private val nov = InvoiceDetail(
        id = 12, invoiceNumber = "INV-202611-0012", billingMonth = 11, billingYear = "2026", dueDate = "2026-11-05",
        totalAmount = 3334.0, paidAmount = 0.0, balanceAmount = 3334.0, status = "pending", periodLabel = "November 2026",
        tenantName = "Test Tenant", tenantPhone = "98765 43210", propertyCode = "R-101", city = "Pune", ownerName = "Test Owner",
        items = listOf(InvoiceItem(itemType = "base_rent", description = "Base Rent", amount = 3000.0), InvoiceItem(itemType = "fixed", description = "Water", amount = 334.0)),
        previousDues = listOf(DueMonth(invoiceId = 11, periodLabel = "October 2026", balance = 2400.0, status = "partial")),
        previousBalance = 2400.0, currentBalance = 3334.0, totalPayable = 5734.0, billUrl = "https://example.test/bill.php?i=12&k=abc",
    )

    @Test fun carryForwardInMessage() {
        val t = BillMessage.text(nov, labels, "5 Nov 2026")
        assertTrue("Previous balance (October 2026): ₹2,400.00" in t, t)
        assertTrue("*Total payable: ₹5,734.00*" in t, t)
        assertTrue("This month: ₹3,334.00" in t)
        assertTrue("Base rent: ₹3,000.00" in t)
        assertTrue(t.endsWith("View bill: https://example.test/bill.php?i=12&k=abc"))
        assertTrue("Invoice No.: INV-202611-0012" in t && "Tenant: Test Tenant" in t)
    }

    @Test fun olderServerWithoutCarryForward() {
        val old = nov.copy(previousDues = emptyList(), previousBalance = 0.0, totalPayable = null, billUrl = null, paidAmount = 1000.0, balanceAmount = 2334.0, status = "partial")
        val t = BillMessage.text(old, labels, "5 Nov 2026")
        assertTrue("*Total payable: ₹2,334.00*" in t, t)
        assertTrue("Paid: ₹1,000.00" in t)
        assertFalse("View bill" in t); assertFalse("Previous balance" in t)
    }

    @Test fun whatsAppLinkUsesRegisteredNumber() {
        val url = ReceiptMessage.whatsAppUrl(nov.tenantPhone, BillMessage.text(nov, labels, "5 Nov 2026"))!!
        assertTrue(url.startsWith("https://wa.me/919876543210?text="))
        assertFalse(" " in url)
        assertEquals(null, ReceiptMessage.whatsAppUrl("", "x"))
    }
}
