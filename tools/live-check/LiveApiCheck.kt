// Runs the app's real data layer (Retrofit + repositories) against a PRMS server.
// TEST DATA ONLY: it creates tenants/rooms/charges/agents and records ₹1 payments.
//   ./gradlew :data:jar  (or compile with kotlinc) && java ... LiveApiCheckKt https://host/rentalsolutionfinal/api/v1/ owner_user emp_user tenant_user password
import com.genusit.prms.data.remote.ApiCaller
import com.genusit.prms.data.remote.NetworkConfig
import com.genusit.prms.data.remote.PrmsNetwork
import com.genusit.prms.data.repository.*
import com.genusit.prms.domain.model.*
import com.genusit.prms.domain.util.IdempotencyKey
import com.genusit.prms.domain.model.EmployeeInput
import com.genusit.prms.domain.model.MeterReading
import com.genusit.prms.domain.model.NewAllocation
import com.genusit.prms.domain.model.NewCharge
import com.genusit.prms.domain.model.NewProperty
import com.genusit.prms.domain.model.NewQuery
import com.genusit.prms.domain.model.TenantInput
import com.genusit.prms.domain.error.ApiException
import com.genusit.prms.domain.util.Dates
import kotlinx.coroutines.runBlocking

private var pass = 0
private var fail = 0
private fun ok(cond: Boolean, name: String, detail: Any? = null) {
    if (cond) { pass++; println("PASS $name") } else { fail++; println("FAIL $name  >> $detail") }
}

fun main(args: Array<String>) = runBlocking {
    val base = args.getOrElse(0) { "http://127.0.0.1:8080/api/v1/" }
    val (ownerU, empU, tenU, pw) = listOf(args.getOrElse(1) { "owner1" }, args.getOrElse(2) { "emp1" }, args.getOrElse(3) { "tenant1" }, args.getOrElse(4) { "" })

    var token: String? = null
    var lang = "mr"
    val api = PrmsApi(base, { token }, { lang })

    try { api.login(ownerU, "wrong", "mr"); ok(false, "bad login rejected") } catch (e: ApiException) {
        ok(e.code == "INVALID_CREDENTIALS" && e.message.contains("अवैध"), "bad login → INVALID_CREDENTIALS, Marathi message", e.message)
    }
    val owner = api.login(ownerU, pw, "en"); token = owner.token; lang = "en"
    ok(owner.userType == "owner" && owner.token.length == 64, "owner login")
    val d = api.ownerDashboard(); ok(d.summary.properties >= 1, "owner dashboard decoded", d.summary)
    val props = api.ownerProperties(); ok(props.isNotEmpty(), "owner properties", props.size)
    val tenants = api.ownerTenants(); ok(tenants.isNotEmpty(), "owner tenants", tenants.size)
    val invs = api.ownerInvoices(); ok(invs.isNotEmpty(), "owner invoices", invs.size)
    val open = invs.firstOrNull { it.isOpen }
    if (open != null) {
        val det = api.ownerInvoice(open.id); ok(det.items.isNotEmpty() && det.balanceAmount > 0, "owner invoice detail with items", det.items.size)
        try { api.ownerRecordPayment(open.id, det.balanceAmount + 1, "cash", Dates.today(), "", "", api.newIdempotencyKey()); ok(false, "overpay") }
        catch (e: ApiException) { ok(e.code == "AMOUNT_EXCEEDS_BALANCE" && e.httpStatus == 422, "overpayment refused by server", e.code) }
        val key = api.newIdempotencyKey()
        val p1 = api.ownerRecordPayment(open.id, 1.0, "upi", Dates.today(), "APP-1", "android", key)
        val p2 = api.ownerRecordPayment(open.id, 1.0, "upi", Dates.today(), "APP-1", "android", key)
        ok(p1.receiptNumber.isNotEmpty() && p1.receiptNumber == p2.receiptNumber && p2.replayed, "idempotent payment replay returns same receipt", "$p1 / $p2")
        val rc = api.receipt(p1.receiptNumber); ok(rc.amount == 1.0 && rc.lines.isNotEmpty() && rc.shareText.isNotEmpty(), "receipt endpoint with share text", rc.receiptNumber)
    } else ok(false, "no open invoice in test data")
    val sub = api.subscription(); ok(sub.state.isNotEmpty() && sub.usage.properties >= 1, "subscription status decoded", sub.state)
    ok(api.ownerPayments().payments.size >= 0, "owner payments decoded")
    ok(api.notifications().items.size >= 0, "notifications decoded")
    ownerManageChecks(api)
    val meta = api.meta(); ok(meta.paymentModes.any { it.value == "cash" }, "meta payment modes")
    lang = "hi"; ok(api.meta().paymentModes.first { it.value == "cash" }.label == "नकद", "Accept-Language hi → Hindi labels")
    lang = "en"
    api.logout(); token = null
    try { token = owner.token; api.ownerDashboard(); ok(false, "token revoked") } catch (e: ApiException) { ok(e.isAuthError, "token revoked after logout", e.code) }

    val emp = api.login(empU, pw, "en"); token = emp.token
    val ed = api.employeeDashboard(); ok(ed.summary.assignedProperties >= 1, "employee dashboard decoded", ed.summary)
    val et = api.employeeTenants(); ok(et.isNotEmpty(), "employee tenants", et.size)
    val target = et.firstOrNull { it.pendingAmount > 0 }
    if (target != null) {
        val inv = target.invoices.first { it.isOpen }
        val pid = ed.assignedProperties.first { it.propertyCode == target.propertyCode }.id
        val key = api.newIdempotencyKey()
        val r1 = api.employeeCollect(target.id, pid, 0, 1.0, "cash", Dates.today(), "", "android test", key)
        val r2 = api.employeeCollect(target.id, pid, 0, 1.0, "cash", Dates.today(), "", "android test", key)
        ok(r1.receiptNumber == r2.receiptNumber, "employee collection idempotent", "$r1 / $r2")
        ok(api.employeeCollections().collections.any { it.receiptNumber == r1.receiptNumber }, "employee collections list shows new receipt")
        ok(inv.id > 0, "employee sees open invoice of assigned tenant")
    } else ok(false, "no tenant with dues for employee")
    ok(api.employeeProfile().username == empU, "employee profile")

    val ten = api.login(tenU, pw, "mr"); token = ten.token; lang = "mr"
    val td = api.tenantDashboard(); ok(td.tenant.fullName.isNotEmpty(), "tenant dashboard decoded", td.summary)
    val ti = api.tenantInvoices(); ok(ti.isNotEmpty() && ti[0].periodLabel != null, "tenant invoices with localized period", ti.firstOrNull()?.periodLabel)
    ok(api.tenantInvoice(ti[0].id).id == ti[0].id, "tenant invoice detail")
    val tp = api.tenantPayments(); ok(tp.payments.isNotEmpty(), "tenant payments")
    ok(api.receipt(tp.payments[0].receiptNumber).tenantName.isNotEmpty(), "tenant opens own receipt")
    val q = api.raiseQuery(NewQuery("App test", "Raised from Android core check", "low")); ok(q.ticketId?.startsWith("TKT-") == true, "tenant raises query", q)
    ok(api.tenantQueries().any { it.ticketId == q.ticketId }, "query listed")
    api.tenantReplyQuery(q.id, "More details from the app")
    val tq = api.tenantQuery(q.id); ok(tq.responses.any { it.responderType == "tenant" } && tq.description.isNotEmpty(), "tenant query detail + reply", tq.responses.size)
    val tprof = api.tenantProfile(); api.updateProfile(com.genusit.prms.domain.model.Role.TENANT, tprof.phone, tprof.email, null, null)
    ok(true, "tenant profile phone/email save")
    try { api.ownerDashboard(); ok(false, "role") } catch (e: ApiException) { ok(e.httpStatus == 403, "tenant blocked from owner API", e.code) }
    api.saveLanguage("en")

    // owner answers the tenant's ticket and closes it
    lang = "en"; token = api.login(ownerU, pw, "en").token
    api.ownerReplyQuery(q.id, "We will fix it tomorrow.", "in_progress")
    val oq = api.ownerQuery(q.id); ok(oq.status == "in_progress" && oq.responses.any { it.responderType == "owner" }, "owner replies to complaint", oq.status)
    api.ownerSetQueryStatus(q.id, "closed")
    token = ten.token
    try { api.tenantReplyQuery(q.id, "late reply"); ok(false, "reply on closed ticket") } catch (e: ApiException) { ok(e.httpStatus in 400..499, "tenant cannot reply on closed ticket", e.code) }

    println("\n$pass passed, $fail failed")
    if (fail > 0) kotlin.system.exitProcess(1)
}

/** Owner management endpoints used by the app 1.1 screens. Creates and removes its own test records. */
private suspend fun ownerManageChecks(api: PrmsApi) {
    val stamp = (System.currentTimeMillis() / 1000 % 1_000_000).toString()

    // tenant create / edit / delete (no history → delete allowed)
    val t = api.addTenant(TenantInput("App Tenant $stamp", "98765${stamp.padStart(5, '0').takeLast(5)}", email = "app$stamp@example.com"))
    ok(t.id > 0, "add tenant", t)
    api.updateTenant(t.id, TenantInput("App Tenant $stamp Edited", "98765${stamp.padStart(5, '0').takeLast(5)}", address = "Pune"))
    val te = api.ownerTenantEditable(t.id); ok(te.fullName.endsWith("Edited") && te.address == "Pune", "edit tenant", te)
    try { api.addTenant(TenantInput("", "1")); ok(false, "tenant validation") } catch (e: ApiException) { ok(e.httpStatus == 422, "tenant validation error is 422", e.code) }

    // property create (may hit the package room limit) / edit
    var propId = 0
    try {
        propId = api.addProperty(NewProperty("APP$stamp", "Lane 1", "Pune", "Maharashtra", "411001", 2500.0, 5000.0)).id
        ok(propId > 0, "add property")
    } catch (e: ApiException) {
        ok(e.code == "PLAN_PROPERTY_LIMIT", "add property (or plan limit)", e.code)
        propId = api.ownerProperties().firstOrNull { it.status == "available" }?.id ?: 0
    }
    if (propId > 0) {
        val p = api.ownerProperty(propId)
        api.updateProperty(propId, NewProperty(p.propertyCode, p.address, p.city, p.state, p.pincode, p.baseRent, p.depositAmount), "available")
        ok(api.ownerProperty(propId).status == "available", "edit property")
    }

    // charges
    val ch = api.addCharge(NewCharge("App water $stamp", "fixed", fixedAmount = 50.0)); ok(ch.id > 0, "add fixed charge")
    val meter = api.addCharge(NewCharge("App meter $stamp", "variable", ratePerUnit = 10.0, unitLabel = "unit")); ok(meter.id > 0, "add variable charge")
    ok(api.ownerCharges().any { it.id == meter.id && it.isVariable && it.ratePerUnit == 10.0 }, "charges list decoded")

    // allocation → deposit → invoice context/generate/cancel → close
    if (propId > 0) {
        val al = api.allocate(NewAllocation(propId, t.id, Dates.today(), 5000.0, 1000.0, listOf(ch.id, meter.id)))
        ok(al.allocationId > 0, "allocate tenant to room", al)
        try { api.collectDeposit(al.allocationId, 99999.0); ok(false, "deposit over") } catch (e: ApiException) { ok(e.code == "AMOUNT_EXCEEDS_BALANCE", "deposit above agreed refused", e.code) }
        val dep = api.collectDeposit(al.allocationId, 1500.0); ok(dep.depositCollected == 2500.0 && dep.depositBalance == 2500.0, "collect deposit", dep)
        ok(api.ownerAllocations("active").any { it.id == al.allocationId }, "allocations list")
        val today = Dates.today(); val m = today.substring(5, 7).toInt(); val y = today.substring(0, 4).toInt()
        val ctx = api.invoiceContext(al.allocationId, m, y)
        ok(!ctx.alreadyExists && ctx.fixedCharges.any { it.tcId > 0 } && ctx.variableCharges.size == 1, "invoice context", ctx)
        val fixedIds = ctx.fixedCharges.map { it.tcId }
        val v = ctx.variableCharges.first()
        val gen = api.generateInvoice(al.allocationId, m, y, fixedIds, listOf(MeterReading(v.tcId, 100.0, 112.0)))
        val expected = ctx.baseRent + ctx.fixedCharges.sumOf { it.amount } + 12 * v.rate
        ok(gen.id > 0 && kotlin.math.abs(gen.total - expected) < 0.01, "generate invoice total computed on server", "${gen.total} vs $expected")
        ok(api.invoiceContext(al.allocationId, m, y).alreadyExists, "duplicate month detected")
        api.cancelInvoice(gen.id, "app test")
        ok(api.ownerInvoice(gen.id).status == "cancelled", "cancel unpaid invoice")
        val cl = api.closeAllocation(al.allocationId, "app test exit"); ok(cl.settlementAmount == 2500.0, "close allocation with settlement", cl)
        try { api.deleteTenant(t.id); ok(false, "delete tenant with history") } catch (e: ApiException) { ok(e.httpStatus == 409, "tenant with history cannot be deleted", e.code) }
    }
    api.deactivateCharge(ch.id); api.deactivateCharge(meter.id)
    ok(api.ownerCharges().none { it.id == ch.id } && api.ownerCharges(true).first { it.id == ch.id }.status == "inactive", "deactivate charge")
    api.setChargeStatus(ch.id, true); ok(api.ownerCharges().any { it.id == ch.id }, "re-activate charge"); api.setChargeStatus(ch.id, false)
    val t2 = api.addTenant(TenantInput("App Temp $stamp", "91234${stamp.padStart(5, '0').takeLast(5)}"))
    api.deleteTenant(t2.id); ok(api.ownerTenants().none { it.id == t2.id }, "delete tenant without history")

    // employees
    val e = api.addEmployee(EmployeeInput("App Agent $stamp", "9000${stamp.padStart(6, '0').takeLast(6)}", username = "appemp$stamp", password = "Test@1234"))
    ok(e.id > 0, "add employee")
    if (propId > 0) { api.assignProperties(e.id, listOf(propId)); ok(api.ownerEmployee(e.id).assignedProperties.any { it.id == propId }, "assign property to employee") }
    api.setEmployeeStatus(e.id, false); ok(api.ownerEmployees().first { it.id == e.id }.status == "inactive", "deactivate employee")
    ok(!api.deleteEmployee(e.id).deactivated, "delete employee without collections")

    // reports, referrals, profile
    val cr = api.collectionsReport("2026-01-01", Dates.today()); ok(cr.totalCollected >= 0 && cr.filters.from == "2026-01-01", "collections report", cr.totalCollected)
    ok(api.revenueReport("2026-01-01", Dates.today()).all { it.label.isNotEmpty() }, "revenue report")
    val pr = api.pendingReport(); ok(pr.totalPending >= 0, "pending report", pr.totalPending)
    val rf = api.referrals(); ok(!rf.enabled || (rf.code?.startsWith("RS") == true && rf.shareText != null), "referral summary", rf.code)
    val me = api.me(); api.updateProfile(Role.OWNER, me.phone, me.email, null, null); ok(api.me().email == me.email, "owner profile save", me.email)
    try { api.updateProfile(Role.OWNER, null, null, "wrong", "newpass1"); ok(false, "wrong pw") } catch (e2: ApiException) { ok(e2.code == "WRONG_PASSWORD", "owner password change needs current password", e2.code) }
}

/** Same call names as the app 1.1 client, implemented with the app's Retrofit repositories. */
class PrmsApi(baseUrl: String, token: () -> String?, lang: () -> String) {
    private val config = NetworkConfig(baseUrl, "PRMS-LiveCheck", debugLogging = false)
    val json = PrmsNetwork.json()
    private val service = PrmsNetwork.service(PrmsNetwork.retrofit(config, PrmsNetwork.okHttp(config, { token() }, { lang() }), json))
    private val caller = ApiCaller(json)
    private val auth = AuthRepositoryImpl(service, caller)
    private val common = CommonRepositoryImpl(service, caller)
    private val props = PropertyRepositoryImpl(service, caller)
    private val tenants = TenantAdminRepositoryImpl(service, caller)
    private val billing = BillingRepositoryImpl(service, caller)
    private val allocs = AllocationRepositoryImpl(service, caller)
    private val team = TeamRepositoryImpl(service, caller)
    private val queries = QueryRepositoryImpl(service, caller)
    private val reports = ReportRepositoryImpl(service, caller)
    private val subs = SubscriptionRepositoryImpl(service, caller)
    private val agent = AgentRepositoryImpl(service, caller)
    private val portal = TenantPortalRepositoryImpl(service, caller)

    fun newIdempotencyKey() = IdempotencyKey.new()
    suspend fun login(u: String, p: String, l: String) = auth.login(u, p, l)
    suspend fun logout() = auth.logout()
    suspend fun me() = auth.me()
    suspend fun saveLanguage(l: String) = auth.saveLanguage(l)
    suspend fun updateProfile(role: Role, phone: String?, email: String?, cur: String?, new: String?) = auth.updateProfile(role, phone, email, cur, new)
    suspend fun meta() = common.meta()
    suspend fun notifications() = common.notifications()
    suspend fun receipt(no: String) = common.receipt(no)
    suspend fun ownerDashboard() = reports.ownerDashboard()
    suspend fun ownerProperties() = props.properties()
    suspend fun ownerProperty(id: Int) = props.property(id)
    suspend fun addProperty(p: NewProperty) = props.add(p)
    suspend fun updateProperty(id: Int, p: NewProperty, status: String?) = props.update(id, p, status)
    suspend fun ownerTenants() = tenants.tenants()
    suspend fun ownerTenant(id: Int) = tenants.tenant(id)
    suspend fun ownerTenantEditable(id: Int) = tenants.editable(id)
    suspend fun addTenant(t: TenantInput) = tenants.add(t)
    suspend fun updateTenant(id: Int, t: TenantInput) = tenants.update(id, t)
    suspend fun deleteTenant(id: Int) = tenants.delete(id)
    suspend fun ownerInvoices(status: String? = null) = billing.invoices(status)
    suspend fun ownerInvoice(id: Int) = billing.invoice(id)
    suspend fun ownerRecordPayment(id: Int, amount: Double, mode: String, date: String, ref: String, remarks: String, key: String) =
        billing.recordPayment(id, amount, mode, date, ref, remarks, key)
    suspend fun ownerPayments() = billing.payments()
    suspend fun invoiceContext(a: Int, m: Int, y: Int) = billing.invoiceContext(a, m, y)
    suspend fun generateInvoice(a: Int, m: Int, y: Int, ids: List<Int>, r: List<MeterReading>) = billing.generateInvoice(a, m, y, ids, r)
    suspend fun cancelInvoice(id: Int, reason: String) = billing.cancelInvoice(id, reason)
    suspend fun ownerCharges(includeInactive: Boolean = false) = billing.charges(includeInactive)
    suspend fun setChargeStatus(id: Int, active: Boolean) = billing.setChargeStatus(id, active)
    suspend fun addCharge(c: NewCharge) = billing.addCharge(c)
    suspend fun deactivateCharge(id: Int) = billing.deactivateCharge(id)
    suspend fun ownerAllocations(status: String? = null) = allocs.allocations(status)
    suspend fun allocate(a: NewAllocation) = allocs.allocate(a)
    suspend fun closeAllocation(id: Int, notes: String) = allocs.close(id, notes)
    suspend fun collectDeposit(id: Int, amount: Double) = allocs.collectDeposit(id, amount)
    suspend fun ownerEmployees() = team.employees()
    suspend fun ownerEmployee(id: Int) = team.employee(id)
    suspend fun addEmployee(e: EmployeeInput) = team.add(e)
    suspend fun setEmployeeStatus(id: Int, active: Boolean) = team.setStatus(id, active)
    suspend fun deleteEmployee(id: Int) = team.delete(id)
    suspend fun assignProperties(id: Int, ids: List<Int>) = team.assignProperties(id, ids)
    suspend fun ownerQuery(id: Int) = queries.ownerQuery(id)
    suspend fun ownerReplyQuery(id: Int, msg: String, status: String?) = queries.ownerReply(id, msg, status)
    suspend fun ownerSetQueryStatus(id: Int, status: String) = queries.ownerSetStatus(id, status)
    suspend fun tenantQueries() = queries.tenantQueries()
    suspend fun tenantQuery(id: Int) = queries.tenantQuery(id)
    suspend fun tenantReplyQuery(id: Int, msg: String) = queries.tenantReply(id, msg)
    suspend fun raiseQuery(q: NewQuery) = queries.raise(q)
    suspend fun collectionsReport(from: String?, to: String?) = reports.collections(from, to)
    suspend fun revenueReport(from: String?, to: String?) = reports.revenue(from, to)
    suspend fun pendingReport() = reports.pending()
    suspend fun referrals() = reports.referrals()
    suspend fun subscription() = subs.status()
    suspend fun employeeDashboard() = agent.dashboard()
    suspend fun employeeTenants() = agent.tenants()
    suspend fun employeeCollections() = agent.collections()
    suspend fun employeeCollect(t: Int, p: Int, i: Int, amount: Double, mode: String, date: String, ref: String, remarks: String, key: String) =
        agent.collect(t, p, i, amount, mode, date, ref, remarks, key)
    suspend fun employeeProfile() = agent.profile()
    suspend fun tenantDashboard() = portal.dashboard()
    suspend fun tenantInvoices() = portal.invoices()
    suspend fun tenantInvoice(id: Int) = portal.invoice(id)
    suspend fun tenantPayments() = portal.payments()
    suspend fun tenantProfile() = portal.profile()
}
