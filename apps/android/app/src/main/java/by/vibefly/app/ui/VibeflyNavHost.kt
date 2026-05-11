package by.vibefly.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import by.vibefly.app.R
import by.vibefly.app.ui.screens.AppDetailScreen
import by.vibefly.app.ui.screens.ChatScreen
import by.vibefly.app.ui.screens.DashboardScreen
import by.vibefly.app.ui.screens.MarketplaceScreen
import by.vibefly.app.ui.screens.SettingsScreen

/**
 * Корневой NavHost приложения с bottom-навигацией.
 */
@Composable
fun VibeflyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                BottomNavTabs.forEach { tab ->
                    val selected = backStackEntry?.destination?.hierarchy?.any {
                        it.route == tab.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
            }
        }
    ) { innerPadding ->
        VibeflyContent(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun VibeflyContent(
    navController: androidx.navigation.NavHostController,
    innerPadding: PaddingValues,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Dashboard,
        modifier = Modifier.padding(innerPadding),
    ) {
        composable(Routes.Dashboard) {
            DashboardScreen(
                onAppClick = { id -> navController.navigate(Routes.appDetail(id)) },
            )
        }
        composable(Routes.Chat) { ChatScreen() }
        composable(Routes.Marketplace) { MarketplaceScreen() }
        composable(Routes.Settings) { SettingsScreen() }
        composable(Routes.AppDetailPattern) { entry ->
            val id = entry.arguments?.getString("id").orEmpty()
            AppDetailScreen(
                appId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private data class BottomTab(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector,
)

private val BottomNavTabs = listOf(
    BottomTab(Routes.Dashboard, R.string.nav_dashboard, Icons.Outlined.Apps),
    BottomTab(Routes.Chat, R.string.nav_chat, Icons.Outlined.AutoAwesome),
    BottomTab(Routes.Marketplace, R.string.nav_marketplace, Icons.Outlined.Storefront),
    BottomTab(Routes.Settings, R.string.nav_settings, Icons.Outlined.Settings),
)
