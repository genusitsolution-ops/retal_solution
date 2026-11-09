package com.genusit.prms.domain

import com.genusit.prms.domain.model.AllocationCreated
import com.genusit.prms.domain.model.CreatedId
import com.genusit.prms.domain.model.OwnerDashboard
import com.genusit.prms.domain.model.SaveTenantResult
import com.genusit.prms.domain.model.TenantDetail
import com.genusit.prms.domain.model.VendorDashboard
import com.genusit.prms.domain.model.WhatsAppMessage
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 1.13: tenant WhatsApp messages and the Vendor Dashboard, parsed exactly as the server sends them. */
class OnboardingVendorTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    @Test fun tenantCreatedCarriesWelcome() {
        val c = json.decodeFromString(CreatedId.serializer(),
            """{"id":12,"documents":{"aadhaar":true},"whatsapp":{"text":"*Owner*\nUsername: asha.v","url":"https://wa.me/919876500101?text=x"}}""")
        assertEquals(12, c.id)
        assertEquals(WhatsAppMessage("*Owner*\nUsername: asha.v", "https://wa.me/919876500101?text=x"), c.whatsapp)
        // older servers: no field → no prompt
        assertNull(json.decodeFromString(CreatedId.serializer(), """{"id":3,"documents":{}}""").whatsapp)
        // invalid tenant number → text without a link
        assertNull(json.decodeFromString(CreatedId.serializer(), """{"id":3,"whatsapp":{"text":"t","url":null}}""").whatsapp!!.url)
    }

    @Test fun allocationAndDetailCarryMessage() {
        val a = json.decodeFromString(AllocationCreated.serializer(), """{"allocation_id":"5","whatsapp":{"text":"Deposit pending: ₹5,000.00","url":"https://wa.me/91x"}}""")
        assertEquals(5, a.allocationId)
        assertTrue(a.whatsapp!!.text.contains("Deposit pending"))
        assertNull(json.decodeFromString(AllocationCreated.serializer(), """{"allocation_id":5}""").whatsapp)
        val t = json.decodeFromString(TenantDetail.serializer(), """{"id":4,"full_name":"A","whatsapp":{"text":"hello","url":null}}""")
        assertEquals("hello", t.whatsapp!!.text)
    }

    @Test fun saveTenantKeepsWelcomeOnlyWhenAdding() = kotlinx.coroutines.test.runTest {
        val msg = WhatsAppMessage("welcome", "https://wa.me/91x")
        val repo = object : com.genusit.prms.domain.repository.TenantAdminRepository {
            override suspend fun tenants() = emptyList<com.genusit.prms.domain.model.TenantRow>()
            override suspend fun tenant(id: Int) = TenantDetail()
            override suspend fun editable(id: Int) = com.genusit.prms.domain.model.TenantEditable()
            override suspend fun add(t: com.genusit.prms.domain.model.TenantInput, idempotencyKey: String?) = CreatedId(9, whatsapp = msg)
            override suspend fun update(id: Int, t: com.genusit.prms.domain.model.TenantInput) = emptyMap<String, Boolean>()
            override suspend fun delete(id: Int) {}
            override suspend fun history(id: Int) = com.genusit.prms.domain.model.TenantHistory()
            override suspend fun document(id: Int, type: com.genusit.prms.domain.model.TenantDocType) = com.genusit.prms.domain.model.DocumentFile(ByteArray(0), "")
            override suspend fun uploadDocument(id: Int, type: com.genusit.prms.domain.model.TenantDocType, file: java.io.File, mimeType: String, consent: Boolean) {}
            override suspend fun uploadDraft(type: com.genusit.prms.domain.model.TenantDocType, file: java.io.File, mimeType: String, consent: Boolean, replaceToken: String?) = "t"
            override suspend fun deleteDraft(type: com.genusit.prms.domain.model.TenantDocType, token: String) {}
        }
        val save = com.genusit.prms.domain.usecase.SaveTenantUseCase(repo)
        val input = com.genusit.prms.domain.model.TenantInput(fullName = "A", phone = "9000000001")
        assertEquals(SaveTenantResult(9, true, msg), save(0, input, "k"))
        assertNull(save(9, input, null).whatsapp)
    }

    @Test fun ownerDashboardVendorFlag() {
        assertFalse(json.decodeFromString(OwnerDashboard.serializer(), """{"summary":{}}""").isVendor)
        assertTrue(json.decodeFromString(OwnerDashboard.serializer(), """{"summary":{},"is_vendor":true}""").isVendor)
    }

    @Test fun vendorDashboardParsesServerResponse() {
        // trimmed copy of a real owner/vendor.php response (made-up test accounts)
        val d = json.decodeFromString(VendorDashboard.serializer(), """
            {"monthly":[{"month":"2026-11","renewals":1,"renewal_revenue":499,"commission":49.9,"pending":49.9,"approved":0,"paid":0,"reversed":0},
                        {"month":"2026-10","renewals":1,"renewal_revenue":499,"commission":49.9,"pending":0,"approved":0,"paid":49.9,"reversed":0}],
             "vendor":{"commission_percent":10,"status":"active","since":"2026-11-09 15:06:37","referral_code":"RSU6BT4S"},
             "totals":{"paid_renewals":2,"total":99.8,"pending":49.9,"approved":0,"paid":49.9,"reversed":0,"referrals":"2"},
             "referred_owners":[{"owner_name":"Ref2 Owner","referral_date":"2026-11-09 15:06:37","referral_code":"RSU6BT4S","trial_status":"On Free Trial","plan_state":"trial",
                 "renewal_status":"renewed","renewal_label":"Renewed","renewal_amount":"499.00","payment_status":"confirmed","payment_label":"Payment Confirmed","paid_renewals":1,
                 "commission_percent":null,"commission_amount":49.9,"commission_total":49.9,"commission_status":"pending","commission_label":"Commission Pending"}],
             "commissions":[{"id":2,"referred_owner":"Ref2 Owner","invoice_number":"SUB-202611-0004","renewal_amount":499,"eligible_amount":499,"commission_percent":10,
                 "commission_amount":49.9,"payment_confirmed_at":"2026-11-09 15:06:37","status":"paid","status_label":"Commission Paid","approved_at":null,"paid_at":"2026-11-09",
                 "payout_reference":"NEFT-1","reversal_reason":null}],
             "payouts":[{"paid_at":"2026-11-09","payout_reference":"NEFT-1","entries":1,"amount":49.9,"reversed_amount":0}]}
        """.trimIndent())
        assertEquals(10.0, d.vendor.commissionPercent)
        assertEquals("RSU6BT4S", d.vendor.referralCode)
        assertEquals(2, d.totals.referrals)
        assertEquals(99.8, d.totals.total)
        assertEquals(listOf("2026-11", "2026-10"), d.monthly.map { it.month })
        assertEquals(49.9, d.monthly[1].paid)
        assertEquals(499.0, d.referredOwners[0].renewalAmount)
        assertNull(d.referredOwners[0].commissionPercent)
        assertEquals("NEFT-1", d.commissions[0].payoutReference)
        assertEquals(1, d.payouts[0].entries)
    }
}
