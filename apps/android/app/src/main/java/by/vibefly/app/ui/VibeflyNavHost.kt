package by.vibefly.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import by.vibefly.app.ui.components.TabBar
import by.vibefly.app.ui.components.TabBarItem
import by.vibefly.app.ui.screens.AppDetailScreen
import by.vibefly.app.ui.screens.ChatScreen
import by.vibefly.app.ui.screens.DashboardScreen
import by.vibefly.app.ui.screens.MarketplaceScreen
import by.vibefly.app.ui.screens.SettingsScreen

/**
 * Корневой NavHost приложения со скевоморфным TabBar.
 *
 *  • Root-маршруты (Dashboard / Marketplace / Chat / Settings) — TabBar внизу
 *  • Drill-down (AppDetail) — TabBar скрыт, видна только nav bar самого экрана
 *
 * Каждый экран рисует свой IosNavBar внутри, поэтому здесь нет Scaffold с topBar.
 */
@Composable
fun VibeflyNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isRootRoute = currentRoute in RootRoutes

    Column(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Routes.Dashboard,
            modifier = Modifier.weight(1f),
        ) {
            composable(Routes.Dashboard) {
                DashboardScreen(
                    onAppClick = { id -> navController.navigate(Routes.appDetail(id)) },
                    onDeployClick = { navController.navigate(Routes.Marketplace) },
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

        if (isRootRoute) {
            val tabs = remember { skeuTabs() }
            TabBar(
                items = tabs,
                selectedKey = currentRoute ?: Routes.Dashboard,
                onSelect = { route -> navigateToTab(navController, route) },
            )
        }
    }
}

private val RootRoutes = setOf(
    Routes.Dashboard,
    Routes.Marketplace,
    Routes.Chat,
    Routes.Settings,
)

private fun skeuTabs(): List<TabBarItem> = listOf(
    TabBarItem(key = Routes.Dashboard, label = "Apps", glyph = "▥"),
    TabBarItem(key = Routes.Marketplace, label = "Market", glyph = "⊞"),
    TabBarItem(key = Routes.Chat, label = "Vibe AI", glyph = "✦"),
    TabBarItem(key = Routes.Settings, label = "Settings", glyph = "⚙"),
)

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
