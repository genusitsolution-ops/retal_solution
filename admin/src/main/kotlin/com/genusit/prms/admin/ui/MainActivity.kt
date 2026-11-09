package com.genusit.prms.admin.ui

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.genusit.prms.admin.AdminApp
import com.genusit.prms.admin.BuildConfig
import kotlinx.coroutines.flow.SharedFlow

val LocalSnack = staticCompositionLocalOf { SnackbarHostState() }
val LocalApp = staticCompositionLocalOf<AdminApp> { error("no app") }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Release: hide the app's content from screenshots and the recent-apps preview (owner data, payment proofs).
        if (!BuildConfig.DEBUG) window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as AdminApp
        setContent {
            AdminTheme {
                CompositionLocalProvider(LocalApp provides app) {
                    val signedIn by app.session.signedIn.collectAsState()
                    if (signedIn) Home() else LoginScreen()
                }
            }
        }
    }
}

/** Shows a screen model's messages (action results, errors) in the snackbar. */
@Composable
fun Messages(flow: SharedFlow<String>) {
    val snack = LocalSnack.current
    LaunchedEffect(flow) { flow.collect { snack.showSnackbar(it) } }
}

private enum class Tab(val route: String, val label: String) {
    DASH("dashboard", "Dashboard"), APPROVALS("approvals", "Approvals"), OWNERS("owners", "Owners"),
    VENDORS("vendors", "Vendors"), PAYMENTS("payments", "Payments")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Home() {
    val app = LocalApp.current
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.DASH) }
    var badges by remember { mutableStateOf(0 to 0) }
    CompositionLocalProvider(LocalSnack provides snack) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t; nav.navigate(t.route) { popUpTo(0); launchSingleTop = true } },
                            icon = {
                                val n = when (t) { Tab.APPROVALS -> badges.first; Tab.PAYMENTS -> badges.second; else -> 0 }
                                BadgedBox(badge = { if (n > 0) Badge { Text(n.toString()) } }) {
                                    Icon(when (t) {
                                        Tab.DASH -> Icons.Filled.Dashboard; Tab.APPROVALS -> Icons.Filled.HowToReg; Tab.OWNERS -> Icons.Filled.People
                                        Tab.VENDORS -> Icons.Filled.Storefront; Tab.PAYMENTS -> Icons.Filled.Payments
                                    }, contentDescription = t.label)
                                }
                            },
                            label = { Text(t.label, maxLines = 1) },
                        )
                    }
                }
            },
        ) { pad ->
            NavHost(nav, startDestination = Tab.DASH.route, modifier = Modifier.padding(pad)) {
                composable(Tab.DASH.route) {
                    DashboardScreen(nav, onCounts = { s, p -> badges = s to p }, onSignOut = { app.session.clear() },
                        openTab = { t -> tab = Tab.entries.first { it.route == t }; nav.navigate(t) { popUpTo(0); launchSingleTop = true } })
                }
                composable(Tab.APPROVALS.route) { SignupsScreen(nav) }
                composable(Tab.OWNERS.route) { OwnersScreen(nav) }
                composable(Tab.VENDORS.route) { VendorsScreen(nav) }
                composable(Tab.PAYMENTS.route) { PaymentsScreen(nav) }
                composable("owner/{id}", listOf(navArgument("id") { type = NavType.IntType })) { OwnerDetailScreen(nav, it.arguments!!.getInt("id")) }
                composable("vendor/{id}", listOf(navArgument("id") { type = NavType.IntType })) { VendorDetailScreen(nav, it.arguments!!.getInt("id")) }
                composable("payment/{id}", listOf(navArgument("id") { type = NavType.IntType })) { PaymentDetailScreen(nav, it.arguments!!.getInt("id")) }
            }
        }
    }
}

/** Top bar + list body used by every screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Page(
    title: String, nav: NavHostController? = null, onRefresh: (() -> Unit)? = null, onSignOut: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null, body: LazyListScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, maxLines = 1) },
            navigationIcon = { if (nav != null) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (onRefresh != null) IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, "Refresh") }
                if (onSignOut != null) IconButton(onClick = onSignOut) { Icon(Icons.AutoMirrored.Filled.Logout, "Sign out") }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = C.BrandDark, titleContentColor = Color.White,
                navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
        )
        header?.invoke()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(14.dp, 10.dp, 14.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp), content = body)
    }
}

@Composable
fun appApi() = LocalApp.current.api

@Composable
fun appSession() = LocalApp.current.session

@Suppress("unused")
@Composable
private fun ctx() = LocalContext.current
