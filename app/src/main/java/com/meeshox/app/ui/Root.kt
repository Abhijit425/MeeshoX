package com.meeshox.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.meeshox.app.ui.screens.*
import com.meeshox.app.ui.theme.MeeshoXTheme

private data class Tab(val route: String, val label: String, val icon: ImageVector)
private val tabs = listOf(
    Tab("home", "Home", Icons.Filled.Home),
    Tab("categories", "Categories", Icons.Filled.Category),
    Tab("ai", "AI Search", Icons.Filled.AutoAwesome),
    Tab("wishlist", "Wishlist", Icons.Filled.Favorite),
    Tab("profile", "Profile", Icons.Filled.Person)
)

@Composable
fun MeeshoXRoot(vm: ShopViewModel) {
    val prefs by vm.prefs.collectAsState()
    val seeded by vm.seeded.collectAsState()
    val uid by vm.uid.collectAsState()
    MeeshoXTheme(dark = prefs?.darkTheme ?: false) {
        when {
            prefs == null || !seeded || uid < 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            uid == 0L -> AuthScreen(vm)
            else -> MainShell(vm)
        }
    }
}

fun NavHostController.go(route: String) = navigate(route) { launchSingleTop = true }

@Composable
fun MainShell(vm: ShopViewModel) {
    val nav = rememberNavController()
    val snack = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snack.showSnackbar(it) } }
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (tabs.any { it.route == current }) NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = current == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = { Icon(t.icon, t.label) },
                        label = { Text(t.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(bottom = pad.calculateBottomPadding())) {
            composable("home") { HomeScreen(vm, nav) }
            composable("categories") { CategoriesScreen(vm, nav) }
            composable("ai") { AiHubScreen(vm, nav) }
            composable("wishlist") { WishlistScreen(vm, nav) }
            composable("profile") { ProfileScreen(vm, nav) }
            composable("products?cat={cat}&q={q}&deals={deals}", arguments = listOf(
                navArgument("cat") { type = NavType.LongType; defaultValue = 0L },
                navArgument("q") { type = NavType.StringType; defaultValue = "" },
                navArgument("deals") { type = NavType.BoolType; defaultValue = false }
            )) { e ->
                ProductListScreen(vm, nav, e.arguments?.getLong("cat") ?: 0L, e.arguments?.getString("q") ?: "", e.arguments?.getBoolean("deals") ?: false)
            }
            composable("product/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                ProductDetailScreen(vm, nav, e.arguments!!.getLong("id"))
            }
            composable("search") { SearchScreen(vm, nav) }
            composable("cart") { CartScreen(vm, nav) }
            composable("checkout?buyNow={buyNow}&size={size}&color={color}", arguments = listOf(
                navArgument("buyNow") { type = NavType.LongType; defaultValue = 0L },
                navArgument("size") { type = NavType.StringType; defaultValue = "" },
                navArgument("color") { type = NavType.StringType; defaultValue = "" }
            )) { e ->
                CheckoutScreen(vm, nav, e.arguments?.getLong("buyNow") ?: 0L, e.arguments?.getString("size") ?: "", e.arguments?.getString("color") ?: "")
            }
            composable("success/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> OrderSuccessScreen(vm, nav, e.arguments!!.getLong("id")) }
            composable("orders") { OrdersScreen(vm, nav) }
            composable("order/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e -> OrderDetailScreen(vm, nav, e.arguments!!.getLong("id")) }
            composable("addresses") { AddressesScreen(vm, nav) }
            composable("assistant?mode={mode}", arguments = listOf(navArgument("mode") { type = NavType.StringType; defaultValue = "" })) { e ->
                AssistantScreen(vm, nav, e.arguments?.getString("mode") ?: "")
            }
            composable("visual") { VisualSearchScreen(vm, nav) }
            composable("compare") { CompareScreen(vm, nav) }
            composable("budget") { BudgetScreen(vm, nav) }
            composable("deals") { DealRadarScreen(vm, nav) }
            composable("support") { SupportScreen(vm, nav) }
            composable("notifications") { NotificationsScreen(vm, nav) }
            composable("seller") { SellerScreen(vm, nav) }
            composable("seller_add") { SellerAddProductScreen(vm, nav) }
            composable("lists") { SharedListsScreen(vm, nav) }
            composable("memory") { ShoppingMemoryScreen(vm, nav) }
            composable("reorder") { SmartReorderScreen(vm, nav) }
            composable("admin") { AdminLiteScreen(vm, nav) }
        }
    }
}
