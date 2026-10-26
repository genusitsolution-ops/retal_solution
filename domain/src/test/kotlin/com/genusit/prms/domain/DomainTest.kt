package com.genusit.prms.domain

import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.model.*
import com.genusit.prms.domain.repository.AuthRepository
import com.genusit.prms.domain.repository.QueryRepository
import com.genusit.prms.domain.repository.SessionRepository
import com.genusit.prms.domain.usecase.LoginUseCase
import com.genusit.prms.domain.usecase.LogoutUseCase
import com.genusit.prms.domain.usecase.ReplyToQueryUseCase
import com.genusit.prms.domain.util.IdempotencyKey
import com.genusit.prms.domain.util.Money
import com.genusit.prms.domain.util.Validators
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class DomainTest {

    @Test fun moneyFormatting() {
        assertEquals("₹3,269.00", Money.format(3269.0))
        assertEquals("₹1,23,456.50", Money.format(123456.5))
        assertEquals("₹1,00,00,000.00", Money.format(10000000.0))
        assertEquals("-₹1,500.00", Money.format(-1500.0))
        assertEquals("₹0.01", Money.format(0.005), "HALF_UP rounding")
        assertEquals("₹1.3L", Money.compact(125000.0))
        assertEquals("₹45.3K", Money.compact(45300.0))
        assertEquals(1500.5, Money.parse("1,500.50"))
        assertNull(Money.parse("12.345"), "max 2 decimals")
        assertNull(Money.parse("abc"))
    }

    @Test fun validators() {
        assertEquals(Validators.AmountError.EMPTY, Validators.amount(""))
        assertEquals(Validators.AmountError.NOT_POSITIVE, Validators.amount("0"))
        assertEquals(Validators.AmountError.NOT_POSITIVE, Validators.amount("-5"))
        assertEquals(Validators.AmountError.EXCEEDS_BALANCE, Validators.amount("1001", 1000.0))
        assertNull(Validators.amount("1000", 1000.0))
        assertTrue(Validators.phone("+91 98765 43210"))
        assertFalse(Validators.phone("12345"))
    }

    @Test fun idempotencyKeysAreUnique() {
        val a = IdempotencyKey.new(); val b = IdempotencyKey.new()
        assertTrue(a.startsWith("and-") && a != b)
    }

    /* ───────── login rules ───────── */

    private class FakeSession : SessionRepository {
        val state = MutableStateFlow<Session?>(null)
        override val session: StateFlow<Session?> = state
        override val loaded: StateFlow<Boolean> = MutableStateFlow(true)
        override val token: String? get() = state.value?.token
        override suspend fun load() {}
        override suspend fun save(session: Session) { state.value = session }
        override suspend fun clear() { state.value = null }
    }

    private class FakeAuth(private val userType: String, private val failLogin: ApiException? = null) : AuthRepository {
        val loggedOutTokens = mutableListOf<String?>()
        var loginArgs: Triple<String, String, String>? = null
        override suspend fun login(username: String, password: String, lang: String): LoginResult {
            loginArgs = Triple(username, password, lang)
            failLogin?.let { throw it }
            return LoginResult(token = "tok", userType = userType, user = LoginUser(id = 1, fullName = "Abhi Kumar"))
        }
        override suspend fun logout(token: String?) { loggedOutTokens += token; throw ApiException("X", "server down", 500) }
        override suspend fun me(): Me = Me()
        override suspend fun saveLanguage(lang: String) {}
        override suspend fun updateProfile(role: Role, phone: String?, email: String?, currentPassword: String?, newPassword: String?) {}
    }

    @Test fun ownerAgentAndTenantAreSignedIn() = runTest {
        for ((type, role) in listOf("owner" to Role.OWNER, "employee" to Role.EMPLOYEE, "tenant" to Role.TENANT)) {
            val session = FakeSession(); val auth = FakeAuth(type)
            val out = LoginUseCase(auth, session)("  abhi  ", "pw", "mr")
            assertEquals(LoginOutcome.SignedIn(Session("tok", role, "Abhi Kumar")), out)
            assertEquals(Session("tok", role, "Abhi Kumar"), session.state.value)
            assertEquals(Triple("abhi", "pw", "mr"), auth.loginArgs, "username trimmed, password untouched")
        }
    }

    @Test fun superAdminIsSentToWebPanelAndItsTokenRevoked() = runTest {
        for (type in listOf("admin", "something_new")) {
            val session = FakeSession(); val auth = FakeAuth(type)
            assertEquals(LoginOutcome.UseWebPanel, LoginUseCase(auth, session)("root", "pw", "en"))
            assertNull(session.state.value, "no session stored")
            assertEquals(listOf<String?>("tok"), auth.loggedOutTokens, "the fresh token is revoked (logout failure ignored)")
        }
    }

    @Test fun wrongPasswordPropagatesServerError() = runTest {
        val auth = FakeAuth("owner", ApiException("INVALID_CREDENTIALS", "Invalid username or password", 401))
        try { LoginUseCase(auth, FakeSession())("abhi", "bad", "en"); fail("should throw") }
        catch (e: ApiException) { assertEquals("INVALID_CREDENTIALS", e.code) }
    }

    @Test fun logoutClearsSessionEvenWhenServerFails() = runTest {
        val session = FakeSession().apply { save(Session("tok", Role.OWNER, "A")) }
        LogoutUseCase(FakeAuth("owner"), session)()
        assertNull(session.state.value)
    }

    /* ───────── complaint reply rules ───────── */

    private class FakeQueries : QueryRepository {
        val calls = mutableListOf<String>()
        override suspend fun ownerQueries() = emptyList<Query>()
        override suspend fun ownerQuery(id: Int) = QueryDetail()
        override suspend fun ownerReply(id: Int, message: String, status: String?) { calls += "ownerReply:$id:$message:$status" }
        override suspend fun ownerSetStatus(id: Int, status: String) { calls += "ownerStatus:$id:$status" }
        override suspend fun tenantQueries() = emptyList<Query>()
        override suspend fun tenantQuery(id: Int) = QueryDetail()
        override suspend fun tenantReply(id: Int, message: String) { calls += "tenantReply:$id:$message" }
        override suspend fun raise(q: NewQuery) = CreatedId(1)
    }

    @Test fun replyRulesMatchTheScreen() = runTest {
        val repo = FakeQueries(); val reply = ReplyToQueryUseCase(repo)
        assertTrue(reply(Role.OWNER, 7, "  Fixed  ", "resolved"))
        assertTrue(reply(Role.TENANT, 7, "Thanks", "closed"))      // tenants cannot change status
        assertTrue(reply(Role.OWNER, 7, "", "in_progress"))         // status only
        assertFalse(reply(Role.TENANT, 7, " ", "closed"))           // nothing a tenant may send
        assertFalse(reply(Role.OWNER, 7, "", null))
        assertEquals(listOf("ownerReply:7:Fixed:resolved", "tenantReply:7:Thanks", "ownerStatus:7:in_progress"), repo.calls)
    }

    @Test fun idMaskMatchesWebPanel() {
        assertEquals("XXXXXXXX0123", com.genusit.prms.domain.util.IdMask.mask("1234 1234 0123"))
        assertEquals(null, com.genusit.prms.domain.util.IdMask.mask("  "))
        assertEquals("ABC", com.genusit.prms.domain.util.IdMask.mask("ABC"))
    }

    @Test fun periodsMatchTheServerRules() {
        val d = java.time.LocalDate.of(2026, 10, 25)
        assertEquals("2026-10-01" to "2026-10-31", com.genusit.prms.domain.util.Dates.thisMonth(d))
        assertEquals("2026-08-01" to "2026-10-25", com.genusit.prms.domain.util.Dates.lastMonths(3, d))
        assertEquals("2026-01-01" to "2026-10-25", com.genusit.prms.domain.util.Dates.thisYear(d))
        assertEquals("2026-02-01" to "2026-02-28", com.genusit.prms.domain.util.Dates.thisMonth(java.time.LocalDate.of(2026, 2, 10)))
    }

    @Test fun saveTenantReportsMissingDocuments() = kotlinx.coroutines.test.runTest {
        val repo = object : com.genusit.prms.domain.repository.TenantAdminRepository {
            var addKey: String? = null
            override suspend fun tenants() = emptyList<com.genusit.prms.domain.model.TenantRow>()
            override suspend fun tenant(id: Int) = com.genusit.prms.domain.model.TenantDetail()
            override suspend fun editable(id: Int) = com.genusit.prms.domain.model.TenantEditable()
            override suspend fun add(t: com.genusit.prms.domain.model.TenantInput, idempotencyKey: String?) =
                com.genusit.prms.domain.model.CreatedId(7, documents = mapOf("aadhaar" to true, "pan" to false)).also { addKey = idempotencyKey }
            override suspend fun update(id: Int, t: com.genusit.prms.domain.model.TenantInput) = mapOf("aadhaar" to true)
            override suspend fun delete(id: Int) {}
            override suspend fun history(id: Int) = com.genusit.prms.domain.model.TenantHistory()
            override suspend fun document(id: Int, type: com.genusit.prms.domain.model.TenantDocType) = com.genusit.prms.domain.model.DocumentFile(ByteArray(0), "")
            override suspend fun uploadDocument(id: Int, type: com.genusit.prms.domain.model.TenantDocType, file: java.io.File, mimeType: String, consent: Boolean) {}
            override suspend fun uploadDraft(type: com.genusit.prms.domain.model.TenantDocType, file: java.io.File, mimeType: String, consent: Boolean, replaceToken: String?) = "t"
            override suspend fun deleteDraft(type: com.genusit.prms.domain.model.TenantDocType, token: String) {}
        }
        val save = com.genusit.prms.domain.usecase.SaveTenantUseCase(repo)
        val base = com.genusit.prms.domain.model.TenantInput(fullName = "A", phone = "9000000001")
        assertEquals(com.genusit.prms.domain.model.SaveTenantResult(7, true), save(0, base.copy(aadhaarDraft = "x"), "k"))
        assertEquals("k", repo.addKey)
        assertEquals(com.genusit.prms.domain.model.SaveTenantResult(7, false), save(0, base.copy(aadhaarDraft = "x", panDraft = "y"), "k"))
        assertEquals(com.genusit.prms.domain.model.SaveTenantResult(3, true), save(3, base.copy(aadhaarDraft = "x"), null))
        assertEquals(com.genusit.prms.domain.model.SaveTenantResult(7, true), save(0, base, "k"))
    }

    @Test fun registerInputChecks() {
        val uc = com.genusit.prms.domain.usecase.RegisterOwnerUseCase(object : com.genusit.prms.domain.repository.PublicRepository {
            override suspend fun appConfig() = com.genusit.prms.domain.model.AppConfig()
            override suspend fun register(input: com.genusit.prms.domain.model.RegisterInput) = com.genusit.prms.domain.model.RegisterResult()
            override suspend fun image(url: String) = ByteArray(0)
        })
        val ok = com.genusit.prms.domain.model.RegisterInput("Asha Patil", "9123456789", "asha@example.com", "asha.p", "Rental2026")
        assertEquals(emptySet(), uc.check(ok, "Rental2026"))
        val bad = uc.check(com.genusit.prms.domain.model.RegisterInput("A", "12", "nope", "a", "short"), "other")
        assertEquals(com.genusit.prms.domain.usecase.RegisterOwnerUseCase.Problem.entries.toSet(), bad)
        assertTrue(com.genusit.prms.domain.usecase.RegisterOwnerUseCase.Problem.PASSWORD in uc.check(ok.copy(password = "onlyletters"), "onlyletters"))
    }
}
