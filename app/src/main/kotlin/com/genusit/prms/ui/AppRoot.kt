package com.genusit.prms.ui

import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.fadeOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import com.genusit.prms.ui.common.AppViewModel
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.genusit.prms.R
import androidx.compose.ui.platform.testTag
import com.genusit.prms.domain.model.Role
import com.genusit.prms.ui.auth.LoginScreen
import com.genusit.prms.ui.common.NotificationsScreen
import com.genusit.prms.ui.common.ReceiptScreen
import com.genusit.prms.ui.common.SettingsScreen
import com.genusit.prms.ui.employee.EmployeeCollectScreen
import com.genusit.prms.ui.employee.EmployeeCollectionsScreen
import com.genusit.prms.ui.employee.EmployeeHomeScreen
import com.genusit.prms.ui.employee.EmployeeTenantsScreen
import com.genusit.prms.ui.owner.AddPropertyScreen
import com.genusit.prms.ui.owner.AllocationsScreen
import com.genusit.prms.ui.owner.ChargesScreen
import com.genusit.prms.ui.owner.EditPropertyScreen
import com.genusit.prms.ui.owner.EmployeeDetailScreen
import com.genusit.prms.ui.owner.EmployeeFormScreen
import com.genusit.prms.ui.owner.EmployeesScreen
import com.genusit.prms.ui.owner.GenerateInvoiceScreen
import com.genusit.prms.ui.owner.NewAllocationScreen
import com.genusit.prms.ui.owner.OwnerQueriesScreen
import com.genusit.prms.ui.owner.QueryDetailScreen
import com.genusit.prms.ui.owner.ReferralScreen
import com.genusit.prms.ui.owner.ReportsScreen
import com.genusit.prms.ui.owner.TenantFormScreen
import com.genusit.prms.ui.owner.InvoiceDetailScreen
import com.genusit.prms.ui.owner.OwnerHomeScreen
import com.genusit.prms.ui.owner.OwnerInvoicesScreen
import com.genusit.prms.ui.owner.OwnerMoreScreen
import com.genusit.prms.ui.owner.OwnerPaymentsScreen
import com.genusit.prms.ui.owner.OwnerTenantDetailScreen
import com.genusit.prms.ui.owner.TenantDocumentScreen
import com.genusit.prms.ui.owner.TenantHistoryScreen
import com.genusit.prms.ui.owner.OwnerTenantsScreen
import com.genusit.prms.ui.owner.PropertiesScreen
import com.genusit.prms.ui.owner.RecordPaymentScreen
import com.genusit.prms.ui.owner.SubscriptionScreen
import com.genusit.prms.ui.tenant.RaiseQueryScreen
import com.genusit.prms.ui.tenant.TenantHomeScreen
import com.genusit.prms.ui.tenant.TenantInvoiceDetailScreen
import com.genusit.prms.ui.tenant.TenantInvoicesScreen
import com.genusit.prms.ui.tenant.TenantMoreScreen
import com.genusit.prms.ui.tenant.TenantPaymentsScreen
import com.genusit.prms.ui.tenant.TenantQueriesScreen
import com.genusit.prms.ui.theme.Palette
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.animateColorAsState
import com.genusit.prms.ui.theme.NavIcons

/** Login gate: no session → login; otherwise the shell for the user's role. */
@Composable
fun AppRoot() {
    val app: AppViewModel = hiltViewModel()
    val loaded by app.loaded.collectAsStateWithLifecycle()
    val splashDone by app.splashDone.collectAsStateWithLifecycle()
    val brand by app.brand.collectAsStateWithLifecycle()
    val sessionState by app.session.collectAsStateWithLifecycle()
    val session = sessionState
    when {
        // 1.11: branded splash with the Super Admin's logo (cached file; built-in icon offline / before first download)
        !loaded || !splashDone -> BrandedSplash(brand)
        session == null -> LoginScreen()
        // keyed by token: logging out / in as someone else always starts with a fresh back stack
        else -> key(session.token) { RoleShell(session.role, session.token) }
    }
}

private data class Tab(val route: String, val icon: ImageVector, val label: Int)

private object Routes {
    // owner
    const val OWNER_HOME = "owner/home"; const val OWNER_TENANTS = "owner/tenants"; const val OWNER_INVOICES = "owner/invoices"
    const val OWNER_MORE = "owner/more"; const val OWNER_COLLECT = "owner/collect"; const val OWNER_PROPERTIES = "owner/properties"
    // employee
    const val EMP_HOME = "emp/home"; const val EMP_TENANTS = "emp/tenants"; const val EMP_COLLECTIONS = "emp/collections"
    const val EMP_COLLECT = "emp/collect"
    // tenant
    const val TEN_HOME = "ten/home"; const val TEN_INVOICES = "ten/invoices"; const val TEN_PAYMENTS = "ten/payments"
    const val TEN_MORE = "ten/more"; const val TEN_NEW_QUERY = "ten/query/new"; const val TEN_QUERIES = "ten/queries"
    // shared
    const val SETTINGS = "settings"; const val NOTIFICATIONS = "notifications"
}

/** Unread notification count shown on the bell icons (1.11). */
val LocalUnread = androidx.compose.runtime.compositionLocalOf { 0 }

@Composable
private fun RoleShell(role: Role, token: String) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    // 1.11: push registration for this sign-in, unread badge, notification taps, Android 13+ permission
    val shell: com.genusit.prms.ui.common.ShellViewModel = hiltViewModel(key = "shell-$token")
    val unread by shell.unread.collectAsStateWithLifecycle()
    val pushAvailable by shell.push.available.collectAsStateWithLifecycle()
    com.genusit.prms.ui.common.NotificationPermissionPrompt(pushAvailable)
    val pendingRoute by com.genusit.prms.platform.PendingRoute.route.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            val r = com.genusit.prms.platform.PendingRoute.consume()
            val target = com.genusit.prms.domain.util.NotificationRoutes.target(role.api, r) ?: Routes.NOTIFICATIONS
            runCatching { nav.navigate(target) }
            shell.refreshUnread()
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { shell.refreshUnread() }
    }
    androidx.compose.runtime.CompositionLocalProvider(LocalUnread provides unread) { RoleShellContent(role, nav, current) }
}

@Composable
private fun RoleShellContent(role: Role, nav: NavHostController, current: String?) {

    val tabs: List<Tab>
    val start: String
    when (role) {
        Role.OWNER -> {
            // Home | Tenants | Bills | Properties | More — "collect payment" is on Home (quick actions) and the Bills screen's top bar
            tabs = listOf(
                Tab(Routes.OWNER_HOME, NavIcons.Home, R.string.nav_home),
                Tab(Routes.OWNER_TENANTS, NavIcons.Tenants, R.string.nav_tenants),
                Tab(Routes.OWNER_INVOICES, NavIcons.Bills, R.string.nav_invoices),
                Tab(Routes.OWNER_PROPERTIES, NavIcons.Properties, R.string.nav_properties),
                Tab(Routes.OWNER_MORE, NavIcons.More, R.string.nav_more),
            )
            start = Routes.OWNER_HOME
        }
        Role.EMPLOYEE -> {
            // 1.14: Collect is a tab (was the centre button); Settings is reached as "More"
            tabs = listOf(
                Tab(Routes.EMP_HOME, NavIcons.Home, R.string.nav_home),
                Tab(Routes.EMP_COLLECT, NavIcons.Collect, R.string.nav_collect),
                Tab(Routes.EMP_TENANTS, NavIcons.Tenants, R.string.nav_tenants),
                Tab(Routes.EMP_COLLECTIONS, NavIcons.History, R.string.nav_history),
                Tab(Routes.SETTINGS, NavIcons.More, R.string.nav_more),
            )
            start = Routes.EMP_HOME
        }
        else -> {
            // 1.14: Complaints is a tab (was the centre button); "Raise a query" is on that screen
            tabs = listOf(
                Tab(Routes.TEN_HOME, NavIcons.Home, R.string.nav_home),
                Tab(Routes.TEN_INVOICES, NavIcons.Bills, R.string.nav_invoices),
                Tab(Routes.TEN_PAYMENTS, NavIcons.Payments, R.string.nav_payments),
                Tab(Routes.TEN_QUERIES, NavIcons.Complaints, R.string.complaints),
                Tab(Routes.TEN_MORE, NavIcons.More, R.string.nav_more),
            )
            start = Routes.TEN_HOME
        }
    }
    // routes with optional arguments ("emp/collect?tenant=…") count as their tab
    val currentBase = current?.substringBefore('?')
    val showBar = tabs.any { it.route == currentBase }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = { if (showBar) BottomBar(tabs, currentBase, nav) },
    ) { inner ->
        NavHost(
            // consumeWindowInsets + imePadding: with edge-to-edge the window is not resized for the keyboard,
            // so every screen is lifted above it here and the focused field scrolls into view
            nav, startDestination = start, modifier = Modifier.padding(inner).consumeWindowInsets(inner).imePadding(),
            // short, light transitions: fade + a small slide (no heavy effects on everyday screens)
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 14 } },
            exitTransition = { fadeOut(tween(150)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(150)) + slideOutHorizontally(tween(220)) { it / 14 } },
        ) {
            val back: () -> Unit = { nav.popBackStack() }
            val openReceipt: (String) -> Unit = { nav.navigate("receipt/$it") }

            /* shared */
            composable(Routes.SETTINGS) { SettingsScreen(role, onBack = if (role == Role.EMPLOYEE) null else back) }
            composable(Routes.NOTIFICATIONS) {
                NotificationsScreen(onBack = back, open = { route ->
                    com.genusit.prms.domain.util.NotificationRoutes.target(role.api, route)?.let { t -> runCatching { nav.navigate(t) } }
                })
            }
            composable("receipt/{no}", listOf(navArgument("no") { type = NavType.StringType })) {
                ReceiptScreen(it.arguments?.getString("no").orEmpty(), onBack = back)
            }
            // one cash handover; role=owner (confirm / reject) or role=agent (cancel)
            composable("handover/{id}?role={role}", listOf(navArgument("id") { type = NavType.IntType }, navArgument("role") { type = NavType.StringType; defaultValue = "owner" })) {
                com.genusit.prms.ui.owner.SettlementDetailScreen(onBack = back, openReceipt = openReceipt)
            }

            /* owner */
            composable(Routes.OWNER_HOME) {
                OwnerHomeScreen(
                    openInvoice = { id -> nav.navigate("owner/invoice/$id") },
                    openInvoices = { nav.navigateTab(Routes.OWNER_INVOICES) },
                    openSubscription = { nav.navigate("owner/subscription") },
                    openNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    openProperties = { nav.navigateTab(Routes.OWNER_PROPERTIES) },
                    openTenants = { nav.navigateTab(Routes.OWNER_TENANTS) },
                    openDueInvoices = { nav.navigate("owner/bills/due") },
                    openDuesByTenant = { nav.navigate("owner/bills/tenants") },
                    openOpenQueries = { nav.navigate("owner/queries?status=open") },
                    openPayments = { nav.navigate("owner/payments?period=month") },
                    openReports = { nav.navigate("owner/reports") },
                    generateBill = { nav.navigate("owner/generate") },
                    addTenant = { nav.navigate("owner/tenant-form/0") },
                    collectPayment = { nav.navigate(Routes.OWNER_COLLECT) },
                    addProperty = { nav.navigate("owner/property/new") },
                    openSupport = { nav.navigate("owner/support") },
                    openVendor = { nav.navigate("owner/vendor") },
                )
            }
            composable(Routes.OWNER_TENANTS) { OwnerTenantsScreen(openTenant = { nav.navigate("owner/tenant/$it") }, addTenant = { nav.navigate("owner/tenant-form/0") }) }
            composable("owner/tenant/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                val tid = it.arguments?.getInt("id") ?: 0
                OwnerTenantDetailScreen(tid, onBack = back, openInvoice = { id -> nav.navigate("owner/invoice/$id") },
                    edit = { nav.navigate("owner/tenant-form/$tid") }, onDeleted = back,
                    openHistory = { nav.navigate("owner/tenant/$tid/history") },
                    openDocument = { type -> nav.navigate("owner/tenant/$tid/document/${type.apiValue}") },
                    openAllocation = { aid -> nav.navigate("owner/allocation/$aid") },
                    openOpening = { oid -> nav.navigate("owner/opening/$oid?allocationId=0") },
                    recordOpening = { aid -> nav.navigate("owner/opening/0?allocationId=$aid") })
            }
            // 1.12: opening balance of an existing (migrated) tenant — record (id 0 + allocationId) or view / edit
            composable("owner/opening/{id}?allocationId={allocationId}", listOf(navArgument("id") { type = NavType.IntType },
                navArgument("allocationId") { type = NavType.IntType; defaultValue = 0 })) {
                com.genusit.prms.ui.owner.OpeningBalanceScreen(onBack = back, onSaved = back, openInvoice = { id -> nav.navigate("owner/invoice/$id") })
            }
            composable("owner/tenant/{id}/history", listOf(navArgument("id") { type = NavType.IntType })) {
                TenantHistoryScreen(onBack = back, openInvoice = { id -> nav.navigate("owner/invoice/$id") }, openReceipt = openReceipt)
            }
            composable("owner/tenant/{id}/document/{type}", listOf(navArgument("id") { type = NavType.IntType }, navArgument("type") { type = NavType.StringType })) {
                TenantDocumentScreen(onBack = back)
            }
            composable("owner/tenant-form/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                TenantFormScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onSaved = back)
            }
            composable(Routes.OWNER_INVOICES) {
                OwnerInvoicesScreen(openInvoice = { nav.navigate("owner/invoice/$it") }, onCollect = { nav.navigate(Routes.OWNER_COLLECT) })
            }
            composable("owner/invoice/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                InvoiceDetailScreen(it.arguments?.getInt("id") ?: 0, onBack = back, recordPayment = { id -> nav.navigate("owner/pay/$id") }, openReceipt = openReceipt)
            }
            composable("owner/pay/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                RecordPaymentScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onDone = { rc -> nav.popBackStack(); openReceipt(rc) })
            }
            // bills already filtered (e.g. "due" from the dashboard), with a back arrow
            composable("owner/bills/{status}", listOf(navArgument("status") { type = NavType.StringType })) {
                OwnerInvoicesScreen(openInvoice = { id -> nav.navigate("owner/invoice/$id") }, onBack = back,
                    initialFilter = it.arguments?.getString("status").orEmpty())
            }
            composable(Routes.OWNER_COLLECT) { OwnerInvoicesScreen(openInvoice = { nav.navigate("owner/pay/$it") }, onlyOpen = true, onBack = back) }
            composable(Routes.OWNER_MORE) {
                OwnerMoreScreen(
                    open = { route -> if (route == Routes.OWNER_PROPERTIES) nav.navigateTab(route) else nav.navigate(route) },
                )
            }
            composable(Routes.OWNER_PROPERTIES) {
                // a bottom-bar tab: no back arrow when it is the current tab
                PropertiesScreen(onBack = null,
                    addProperty = { nav.navigate("owner/property/new") }, editProperty = { id -> nav.navigate("owner/property/edit/$id") })
            }
            composable("owner/property/edit/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                EditPropertyScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onDone = back)
            }
            composable("owner/charges") { ChargesScreen(onBack = back) }
            // 1.12.1: support person set by the Super Admin (Call / WhatsApp)
            composable("owner/support") { com.genusit.prms.ui.owner.ContactSupportScreen(onBack = back) }
            composable("owner/allocations") {
                AllocationsScreen(onBack = back, newAllocation = { nav.navigate("owner/allocation/new") }, generateInvoice = { id -> nav.navigate("owner/generate/$id") },
                    openDetail = { id -> nav.navigate("owner/allocation/$id") }, vacate = { id -> nav.navigate("owner/vacate/$id") })
            }
            composable("owner/allocation/new") { NewAllocationScreen(onBack = back, onDone = back) }
            // 1.11: allocation details (deposit history, settlement statement) and vacate with settlement
            composable("owner/allocation/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                com.genusit.prms.ui.owner.AllocationDetailScreen(onBack = back, vacate = { id -> nav.navigate("owner/vacate/$id") })
            }
            composable("owner/vacate/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                com.genusit.prms.ui.owner.VacateScreen(onBack = back, onDone = { id ->
                    nav.popBackStack()
                    nav.navigate("owner/allocation/$id") { launchSingleTop = true }
                })
            }
            composable("owner/generate") {
                com.genusit.prms.ui.owner.GenerateBillPickerScreen(onBack = back, pick = { id -> nav.navigate("owner/generate/$id") },
                    newAllocation = { nav.navigate("owner/allocation/new") })
            }
            composable("owner/generate/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                GenerateInvoiceScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onDone = { invId ->
                    // the saved bill replaces the generate steps (room picker included) in the back stack
                    val fromPicker = nav.previousBackStackEntry?.destination?.route == "owner/generate"
                    nav.navigate("owner/invoice/$invId") { popUpTo(if (fromPicker) "owner/generate" else "owner/generate/{id}") { inclusive = true } }
                })
            }
            composable("owner/employees") {
                EmployeesScreen(onBack = back, openEmployee = { id -> nav.navigate("owner/employee/$id") }, addEmployee = { nav.navigate("owner/employee-form/0") })
            }
            composable("owner/employee/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                val eid = it.arguments?.getInt("id") ?: 0
                EmployeeDetailScreen(eid, onBack = back, edit = { nav.navigate("owner/employee-form/$eid") }, openReceipt = openReceipt)
            }
            composable("owner/employee-form/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                EmployeeFormScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onSaved = back)
            }
            composable("owner/queries?status={status}", listOf(navArgument("status") { type = NavType.StringType; defaultValue = "" })) {
                OwnerQueriesScreen(onBack = back, openQuery = { id -> nav.navigate("owner/query/$id") }, initialFilter = it.arguments?.getString("status").orEmpty())
            }
            composable("owner/query/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                QueryDetailScreen(it.arguments?.getInt("id") ?: 0, Role.OWNER, onBack = back)
            }
            composable("owner/reports") { ReportsScreen(onBack = back, openInvoice = { id -> nav.navigate("owner/invoice/$id") }, openReceipt = openReceipt) }
            composable("owner/referrals") { ReferralScreen(onBack = back) }
            composable("owner/vendor") { com.genusit.prms.ui.owner.VendorDashboardScreen(onBack = back) }
            composable("owner/collections-hub") {
                com.genusit.prms.ui.owner.CollectionDashboardScreen(onBack = back, openEmployee = { id -> nav.navigate("owner/collections-hub/employee/$id") },
                    openSettlement = { id -> nav.navigate("handover/$id?role=owner") }, openReceipt = openReceipt)
            }
            composable("owner/collections-hub/employee/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                com.genusit.prms.ui.owner.EmployeeCollectionScreen(onBack = back, openSettlement = { id -> nav.navigate("handover/$id?role=owner") }, openReceipt = openReceipt)
            }
            composable("owner/property/new") { AddPropertyScreen(onBack = back, onSaved = { nav.popBackStack() }) }
            composable("owner/payments?period={period}", listOf(navArgument("period") { type = NavType.StringType; defaultValue = "3m" })) {
                OwnerPaymentsScreen(onBack = back, openReceipt = openReceipt)
            }
            composable("owner/subscription") { SubscriptionScreen(onBack = back, openPayment = { nav.navigate("owner/subscription/pay/$it") }) }
            composable("owner/subscription/pay/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) { e ->
                com.genusit.prms.ui.owner.SubscriptionPaymentScreen(e.arguments?.getInt("id") ?: 0, onBack = back, onSubmitted = back)
            }

            /* employee */
            composable(Routes.EMP_HOME) {
                EmployeeHomeScreen(
                    collect = { tenantId, invoiceId -> nav.navigate("emp/collect?tenant=$tenantId&invoice=$invoiceId") },
                    openReceipt = openReceipt,
                    openNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    openTenants = { nav.navigateTab(Routes.EMP_TENANTS) },
                    openCollections = { nav.navigateTab(Routes.EMP_COLLECTIONS) },
                    openHandover = { nav.navigate("emp/handover") },
                    openCollect = { nav.navigateTab(Routes.EMP_COLLECT) },
                )
            }
            composable("emp/handover") {
                com.genusit.prms.ui.employee.AgentHandoverScreen(onBack = back, submit = { nav.navigate("emp/handover/new") },
                    openSettlement = { id -> nav.navigate("handover/$id?role=agent") })
            }
            composable("emp/handover/new") {
                com.genusit.prms.ui.employee.SubmitHandoverScreen(onBack = back, onDone = { id ->
                    nav.navigate("handover/$id?role=agent") { popUpTo("emp/handover/new") { inclusive = true } }
                })
            }
            composable(Routes.EMP_TENANTS) { EmployeeTenantsScreen(collect = { t, i -> nav.navigate("emp/collect?tenant=$t&invoice=$i") }) }
            composable(Routes.EMP_COLLECTIONS) { EmployeeCollectionsScreen(openReceipt = openReceipt) }
            composable(
                "${Routes.EMP_COLLECT}?tenant={tenant}&invoice={invoice}",
                listOf(
                    navArgument("tenant") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("invoice") { type = NavType.IntType; defaultValue = 0 },
                ),
            ) {
                val preTenant = it.arguments?.getInt("tenant") ?: 0
                val preInvoice = it.arguments?.getInt("invoice") ?: 0
                EmployeeCollectScreen(
                    preTenant = preTenant,
                    preInvoice = preInvoice,
                    // opened from the Collect tab: no back arrow; opened for a chosen tenant: back to where it came from
                    onBack = if (preTenant == 0 && preInvoice == 0) null else back,
                    onDone = { rc -> nav.popBackStack(); openReceipt(rc) },
                )
            }

            /* tenant */
            composable(Routes.TEN_HOME) {
                TenantHomeScreen(
                    openInvoice = { nav.navigate("ten/invoice/$it") },
                    openNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    openInvoices = { nav.navigateTab(Routes.TEN_INVOICES) },
                    openPayments = { nav.navigateTab(Routes.TEN_PAYMENTS) },
                    openComplaints = { nav.navigateTab(Routes.TEN_QUERIES) },
                )
            }
            composable(Routes.TEN_INVOICES) { TenantInvoicesScreen(openInvoice = { nav.navigate("ten/invoice/$it") }) }
            composable("ten/invoice/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                TenantInvoiceDetailScreen(it.arguments?.getInt("id") ?: 0, onBack = back, openReceipt = openReceipt)
            }
            composable(Routes.TEN_PAYMENTS) { TenantPaymentsScreen(openReceipt = openReceipt) }
            composable(Routes.TEN_MORE) { TenantMoreScreen(open = { route -> if (route == Routes.TEN_QUERIES) nav.navigateTab(route) else nav.navigate(route) }) }
            composable("ten/deposit") { com.genusit.prms.ui.tenant.TenantDepositScreen(onBack = back) }
            composable(Routes.TEN_QUERIES) { TenantQueriesScreen(onBack = null, newQuery = { nav.navigate(Routes.TEN_NEW_QUERY) }, openQuery = { id -> nav.navigate("ten/query/$id") }) }
            composable("ten/query/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                QueryDetailScreen(it.arguments?.getInt("id") ?: 0, Role.TENANT, onBack = back)
            }
            composable(Routes.TEN_NEW_QUERY) { RaiseQueryScreen(onBack = back, onDone = { nav.popBackStack(); nav.navigateTab(Routes.TEN_QUERIES) }) }
        }
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

/** Floating rounded bar: two-tone icons, the current tab on a lavender pill (1.14). */
@Composable
private fun BottomBar(tabs: List<Tab>, current: String?, nav: NavHostController) {
    val shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
    Box(
        Modifier.fillMaxWidth().shadow(18.dp, shape, ambientColor = Palette.Shadow, spotColor = Palette.Shadow)
            .clip(shape).background(Color.White).selectableGroup(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { tab ->
                val selected = current == tab.route
                val label = stringResource(tab.label)
                val bg by animateColorAsState(if (selected) Palette.BrandSoft else Color.Transparent, tween(200), label = "tab")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).background(bg)
                        .selectable(selected = selected, role = androidx.compose.ui.semantics.Role.Tab, onClick = { nav.navigateTab(tab.route) })
                        .padding(vertical = 7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(if (selected) 27.dp else 25.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = if (selected) Palette.NavSelected else Palette.Muted)
                }
            }
        }
    }
}

/** App splash: the published logo, name and tagline on navy with the gold and teal sweep (1.14). */
@Composable
private fun BrandedSplash(brand: com.genusit.prms.platform.Branding) {
    com.genusit.prms.ui.common.LightStatusBarIcons()
    Box(Modifier.fillMaxSize().background(Palette.HeaderGradient), contentAlignment = Alignment.Center) {
        com.genusit.prms.ui.common.BrandWave(Modifier.fillMaxWidth().fillMaxHeight(.42f).align(Alignment.TopCenter), fromTop = true, alpha = .55f)
        com.genusit.prms.ui.common.BrandWave(Modifier.fillMaxWidth().fillMaxHeight(.38f).align(Alignment.BottomCenter))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
            Box(Modifier.size(150.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Brush.radialGradient(
                listOf(Palette.GoldBright.copy(alpha = .35f), Color.Transparent))), contentAlignment = Alignment.Center) {
                com.genusit.prms.ui.auth.BrandLogo(brand.loginLogo ?: brand.logo, 112.dp, Modifier.testTag("splash_logo"))
            }
            Spacer(Modifier.height(16.dp))
            Text(brand.name ?: stringResource(R.string.app_name), color = Color.White, fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (brand.showTagline) Text(brand.tagline ?: stringResource(R.string.login_tagline), color = Palette.GoldBright,
                style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(1.dp).background(androidx.compose.ui.graphics.Brush.horizontalGradient(
                listOf(Color.Transparent, Palette.Gold, Color.Transparent))))
            Spacer(Modifier.height(30.dp))
            CircularProgressIndicator(color = Palette.GoldBright, trackColor = Color.White.copy(alpha = .15f), strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.loading), color = Color.White.copy(alpha = .85f), style = MaterialTheme.typography.labelMedium)
        }
    }
}
