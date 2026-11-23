package com.genusit.prms.domain

import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.LoginOutcome
import com.genusit.prms.domain.model.LoginResult
import com.genusit.prms.domain.model.LoginUser
import com.genusit.prms.domain.model.Me
import com.genusit.prms.domain.model.Role
import com.genusit.prms.domain.model.Session
import com.genusit.prms.domain.model.VendorDashboard
import com.genusit.prms.domain.model.VendorPaymentInput
import com.genusit.prms.domain.model.VendorProfile
import com.genusit.prms.domain.repository.AuthRepository
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.repository.VendorAccountRepository
import com.genusit.prms.domain.usecase.LoginUseCase
import com.genusit.prms.domain.usecase.SaveVendorPaymentUseCase
import com.genusit.prms.domain.util.NotificationRoutes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/** 1.17: independent vendor — role from the login answer, server JSON, payment-detail rules, notification routes. */
class IndependentVendorTest {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    private class FakeSession : SessionRepository {
        val state = MutableStateFlow<Session?>(null)
        override val session: StateFlow<Session?> = state
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override val token: String? get() = state.value?.token
        override suspend fun load() {}
        override suspend fun save(session: Session) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private class FakeAuth(private val userType: String) : AuthRepository {
        override suspend fun login(username: String, password: String, lang: String) =
            LoginResult(token = "vtok", userType = userType, user = LoginUser(id = 3, fullName = "Test Vendor"))
        override suspend fun logout(token: String?, pushToken: String?) {}
        override suspend fun me(): Me = Me()
        override suspend fun saveLanguage(lang: String) {}
        override suspend fun updateProfile(role: Role, phone: String?, email: String?, currentPassword: String?, newPassword: String?) {}
    }

    @Test fun vendorRoleComesFromTheLoginAnswer() = runTest {
        assertEquals(Role.VENDOR, Role.from("vendor"))
        val session = FakeSession()
        val out = LoginUseCase(FakeAuth("vendor"), session)("VEN10001", "pw", "en")
        assertEquals(LoginOutcome.SignedIn(Session("vtok", Role.VENDOR, "Test Vendor")), out)
        assertEquals(Role.VENDOR, session.state.value?.role, "the vendor shell is chosen from the stored role, never OWNER")
    }

    @Test fun vendorDashboardJsonFromServer() {
        val body = """{"vendor":{"commission_percent":"10.00","status":"active","since":"2026-11-23","referral_code":"VEN10001","name":"Test Vendor",
            "vendor_type":"independent","share_url":"https://example.test/index.php?ref=VEN10001#contact"},
            "totals":{"referrals":2,"registrations":2,"referrals_waiting":1,"referrals_earning":1,"referrals_rejected":0,"total":"500.00","pending":0,"approved":0,"paid":500,"reversed":0,"paid_renewals":1},
            "referred_owners":[{"owner_name":"Ravi K.","referral_date":"2026-11-23 10:00:00","referral_state":"approved","commission_total":500,"commission_percent":null}],
            "commissions":[],"payouts":[{"paid_at":"2026-11-23","payout_reference":"UTR1","entries":1,"amount":500,"reversed_amount":0,"payout_method":"bank"}],
            "monthly":[],"profile":{"id":3,"vendor_code":"VEN10001","full_name":"Test Vendor","mobile":"9800000101","status":"active",
            "commission_percent":10,"must_change_password":false,"has_photo":false,"payment":{"payout_method":"upi","upi_id":"test@upi","account_number_masked":null,
            "has_account_number":false,"can_edit":true}},"share_text":"Use my code VEN10001"}"""
        val d = json.decodeFromString(VendorDashboard.serializer(), body)
        assertEquals("VEN10001", d.vendor.referralCode)
        assertEquals(2, d.totals.registrations); assertEquals(1, d.totals.referralsWaiting); assertEquals(1, d.totals.referralsEarning)
        assertEquals("approved", d.referredOwners.single().referralState)
        assertEquals("bank", d.payouts.single().payoutMethod)
        assertEquals("VEN10001", d.profile?.vendorCode); assertTrue(d.profile!!.payment.canEdit)
        assertEquals("Use my code VEN10001", d.shareText)
        // an owner's (converted vendor's) older answer has none of the new fields
        val old = json.decodeFromString(VendorDashboard.serializer(), """{"vendor":{"commission_percent":5,"status":"active","referral_code":"ABC123"},"totals":{"referrals":1}}""")
        assertNull(old.profile); assertEquals(0, old.totals.registrations)
    }

    @Test fun paymentDetailRulesMatchTheServer() {
        fun v(method: String = "upi", upi: String = "", holder: String = "", acc: String = "", ifsc: String = "", saved: Boolean = false) =
            SaveVendorPaymentUseCase.validate(VendorPaymentInput(method, upi, holder, "", acc, ifsc), saved)
        assertNull(v(), "everything optional for UPI")
        assertNull(v(upi = "name.1@okbank"))
        assertEquals(SaveVendorPaymentUseCase.UPI, v(upi = "not-a-upi"))
        assertEquals(SaveVendorPaymentUseCase.IFSC, v(ifsc = "SBIN123"))
        assertNull(v(ifsc = "sbin0001234"), "IFSC is upper-cased first")
        assertEquals(SaveVendorPaymentUseCase.ACCOUNT, v(acc = "12ab"))
        assertEquals(SaveVendorPaymentUseCase.BANK_NEEDS, v(method = "bank", holder = "A", ifsc = "SBIN0001234"), "bank needs an account number")
        assertNull(v(method = "bank", holder = "A", ifsc = "SBIN0001234", saved = true), "the saved (masked) account number is kept")
        assertNull(v(method = "bank", holder = "A", acc = "123456789012", ifsc = "SBIN0001234"))
    }

    @Test fun invalidPaymentDetailsAreNotSent() = runTest {
        var sent = false
        val repo = object : VendorAccountRepository {
            override suspend fun home() = VendorDashboard()
            override suspend fun profile() = VendorProfile()
            override suspend fun savePayment(input: VendorPaymentInput): VendorProfile { sent = true; return VendorProfile() }
            override suspend fun changePassword(current: String, new: String) {}
        }
        try { SaveVendorPaymentUseCase(repo)(VendorPaymentInput("upi", "bad", "", "", "", ""), false); fail("should throw") }
        catch (e: ApiException) { assertEquals("VALIDATION", e.code) }
        assertFalse(sent)
    }

    @Test fun vendorNotificationsOpenOnlyVendorScreens() {
        assertEquals("vendor/home", NotificationRoutes.target("vendor", "vendor/home"))
        assertEquals("notifications", NotificationRoutes.target("vendor", "notifications"))
        assertNull(NotificationRoutes.target("vendor", "invoice/5"), "no owner screen for a vendor")
        assertNull(NotificationRoutes.target("vendor", "subscription"))
    }
}
