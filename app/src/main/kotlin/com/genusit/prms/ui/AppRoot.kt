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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.genusit.prms.R
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

/** Login gate: no session → login; otherwise the shell for the user's role. */
@Composable
fun AppRoot() {
    val app: AppViewModel = hiltViewModel()
    val loaded by app.loaded.collectAsStateWithLifecycle()
    val sessionState by app.session.collectAsStateWithLifecycle()
    val session = sessionState
    when {
        !loaded -> Box(Modifier.fillMaxSize().background(Palette.Canvas), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        session == null -> LoginScreen()
        // keyed by token: logging out / in as someone else always starts with a fresh back stack
        else -> key(session.token) { RoleShell(session.role) }
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
    const val TEN_MORE = "ten/more"; const val TEN_NEW_QUERY = "ten/query/new"
    // shared
    const val SETTINGS = "settings"; const val NOTIFICATIONS = "notifications"
}

@Composable
private fun RoleShell(role: Role) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route

    val tabs: List<Tab>
    /** Centre floating action (agent: collect rent, tenant: raise a complaint). The owner has a plain 5-item bar. */
    val fabRoute: String?
    val fabLabel: Int
    val start: String
    when (role) {
        Role.OWNER -> {
            // Home | Tenants | Bills | Properties | More — "collect payment" moved to the Bills screen's top bar
            tabs = listOf(
                Tab(Routes.OWNER_HOME, Icons.Filled.Home, R.string.nav_home),
                Tab(Routes.OWNER_TENANTS, Icons.Filled.Groups, R.string.nav_tenants),
                Tab(Routes.OWNER_INVOICES, Icons.AutoMirrored.Filled.ReceiptLong, R.string.nav_invoices),
                Tab(Routes.OWNER_PROPERTIES, Icons.Filled.Apartment, R.string.nav_properties),
                Tab(Routes.OWNER_MORE, Icons.Filled.Menu, R.string.nav_more),
            )
            fabRoute = null; fabLabel = R.string.collect_payment; start = Routes.OWNER_HOME
        }
        Role.EMPLOYEE -> {
            tabs = listOf(
                Tab(Routes.EMP_HOME, Icons.Filled.Home, R.string.nav_home),
                Tab(Routes.EMP_TENANTS, Icons.Filled.Groups, R.string.nav_tenants),
                Tab(Routes.EMP_COLLECTIONS, Icons.Filled.History, R.string.nav_collections),
                Tab(Routes.SETTINGS, Icons.Filled.Person, R.string.nav_profile),
            )
            fabRoute = Routes.EMP_COLLECT; fabLabel = R.string.collect_payment; start = Routes.EMP_HOME
        }
        else -> {
            tabs = listOf(
                Tab(Routes.TEN_HOME, Icons.Filled.Home, R.string.nav_home),
                Tab(Routes.TEN_INVOICES, Icons.AutoMirrored.Filled.ReceiptLong, R.string.nav_invoices),
                Tab(Routes.TEN_PAYMENTS, Icons.Filled.CurrencyRupee, R.string.nav_payments),
                Tab(Routes.TEN_MORE, Icons.Filled.Menu, R.string.nav_more),
            )
            fabRoute = Routes.TEN_NEW_QUERY; fabLabel = R.string.raise_query; start = Routes.TEN_HOME
        }
    }
    val showBar = tabs.any { it.route == current }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.navigationBars,
        bottomBar = { if (showBar) BottomBar(tabs, current, nav, centreGap = fabRoute != null) },
        floatingActionButton = {
            if (showBar && fabRoute != null) {
                // centre action docked into the bottom bar, as in the design reference
                FloatingActionButton(
                    onClick = { nav.navigate(fabRoute) },
                    shape = CircleShape,
                    containerColor = Palette.Brand,
                    contentColor = Color.White,
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    modifier = Modifier.offset(y = 44.dp).size(60.dp),
                ) { Icon(if (role == Role.TENANT) Icons.Filled.SupportAgent else Icons.Filled.Add, contentDescription = stringResource(fabLabel)) }
            }
        },
        floatingActionButtonPosition = FabPosition.Center,
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
            composable(Routes.NOTIFICATIONS) { NotificationsScreen(onBack = back) }
            composable("receipt/{no}", listOf(navArgument("no") { type = NavType.StringType })) {
                ReceiptScreen(it.arguments?.getString("no").orEmpty(), onBack = back)
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
                    openOpenQueries = { nav.navigate("owner/queries?status=open") },
                    openPayments = { nav.navigate("owner/payments?period=month") },
                    openReports = { nav.navigate("owner/reports") },
                )
            }
            composable(Routes.OWNER_TENANTS) { OwnerTenantsScreen(openTenant = { nav.navigate("owner/tenant/$it") }, addTenant = { nav.navigate("owner/tenant-form/0") }) }
            composable("owner/tenant/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                val tid = it.arguments?.getInt("id") ?: 0
                OwnerTenantDetailScreen(tid, onBack = back, openInvoice = { id -> nav.navigate("owner/invoice/$id") },
                    edit = { nav.navigate("owner/tenant-form/$tid") }, onDeleted = back,
                    openHistory = { nav.navigate("owner/tenant/$tid/history") },
                    openDocument = { type -> nav.navigate("owner/tenant/$tid/document/${type.apiValue}") })
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
            composable("owner/allocations") {
                AllocationsScreen(onBack = back, newAllocation = { nav.navigate("owner/allocation/new") }, generateInvoice = { id -> nav.navigate("owner/generate/$id") })
            }
            composable("owner/allocation/new") { NewAllocationScreen(onBack = back, onDone = back) }
            composable("owner/generate/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                GenerateInvoiceScreen(it.arguments?.getInt("id") ?: 0, onBack = back, onDone = { invId -> nav.popBackStack(); nav.navigate("owner/invoice/$invId") })
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
            composable("owner/property/new") { AddPropertyScreen(onBack = back, onSaved = { nav.popBackStack() }) }
            composable("owner/payments?period={period}", listOf(navArgument("period") { type = NavType.StringType; defaultValue = "3m" })) {
                OwnerPaymentsScreen(onBack = back, openReceipt = openReceipt)
            }
            composable("owner/subscription") { SubscriptionScreen(onBack = back) }

            /* employee */
            composable(Routes.EMP_HOME) {
                EmployeeHomeScreen(
                    collect = { tenantId, invoiceId -> nav.navigate("emp/collect?tenant=$tenantId&invoice=$invoiceId") },
                    openReceipt = openReceipt,
                    openNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
                    openTenants = { nav.navigateTab(Routes.EMP_TENANTS) },
                    openCollections = { nav.navigateTab(Routes.EMP_COLLECTIONS) },
                )
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
                EmployeeCollectScreen(
                    preTenant = it.arguments?.getInt("tenant") ?: 0,
                    preInvoice = it.arguments?.getInt("invoice") ?: 0,
                    onBack = back,
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
                )
            }
            composable(Routes.TEN_INVOICES) { TenantInvoicesScreen(openInvoice = { nav.navigate("ten/invoice/$it") }) }
            composable("ten/invoice/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                TenantInvoiceDetailScreen(it.arguments?.getInt("id") ?: 0, onBack = back, openReceipt = openReceipt)
            }
            composable(Routes.TEN_PAYMENTS) { TenantPaymentsScreen(openReceipt = openReceipt) }
            composable(Routes.TEN_MORE) { TenantMoreScreen(open = { route -> nav.navigate(route) }) }
            composable("ten/queries") { TenantQueriesScreen(onBack = back, newQuery = { nav.navigate(Routes.TEN_NEW_QUERY) }, openQuery = { id -> nav.navigate("ten/query/$id") }) }
            composable("ten/query/{id}", listOf(navArgument("id") { type = NavType.IntType })) {
                QueryDetailScreen(it.arguments?.getInt("id") ?: 0, Role.TENANT, onBack = back)
            }
            composable(Routes.TEN_NEW_QUERY) { RaiseQueryScreen(onBack = back, onDone = { nav.popBackStack(); nav.navigate("ten/queries") }) }
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

@Composable
private fun BottomBar(tabs: List<Tab>, current: String?, nav: NavHostController, centreGap: Boolean) {
    NavigationBar(containerColor = Color.White, tonalElevation = 6.dp) {
        tabs.forEachIndexed { index, tab ->
            if (centreGap && index == 2) {
                // gap for the centre floating action button
                NavigationBarItem(selected = false, onClick = {}, enabled = false, icon = { Spacer(Modifier.size(24.dp)) }, label = { Text("") })
            }
            NavigationBarItem(
                selected = current == tab.route,
                onClick = { nav.navigateTab(tab.route) },
                icon = { Icon(tab.icon, contentDescription = null) },
                label = { Text(stringResource(tab.label), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Palette.Brand, selectedTextColor = Palette.Brand,
                    indicatorColor = Palette.BrandSoft, unselectedIconColor = Palette.Muted, unselectedTextColor = Palette.Muted,
                ),
            )
        }
    }
}
