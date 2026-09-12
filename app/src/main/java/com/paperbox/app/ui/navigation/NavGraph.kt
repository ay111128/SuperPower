package com.paperbox.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.paperbox.app.data.api.dataStore
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.ui.auth.LoginScreen
import com.paperbox.app.ui.quote.QuoteScreen
import com.paperbox.app.ui.sizeguide.SizeGuideScreen
import com.paperbox.app.ui.materials.MaterialsScreen
import com.paperbox.app.ui.media.MediaViewerScreen
import com.paperbox.app.ui.analysis.AnalysisScreen
import com.paperbox.app.ui.settings.SettingsScreen

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Quote : Screen("quote", "报价", Icons.Default.Calculate)
    data object SizeGuide : Screen("sizeguide", "规格", Icons.Default.Straighten)
    data object Materials : Screen("materials", "素材", Icons.Default.Image)
    data object Analysis : Screen("analysis", "对账", Icons.Default.Analytics)
    data object Settings : Screen("settings", "设置", Icons.Default.Settings)
}

val bottomTabs = listOf(
    Screen.Quote,
    Screen.SizeGuide,
    Screen.Materials,
    Screen.Analysis,
    Screen.Settings
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    // 检查登录状态
    val dataStore = context.dataStore
    val tokenFlow = dataStore.data.collectAsState(initial = null)
    val isLoggedIn = tokenFlow.value?.get(PrefsKeys.TOKEN)?.isNotEmpty() == true

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { /* DataStore 更新后会自动触发 recomposition */ })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomTabs.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Quote.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Quote.route) { QuoteScreen() }
            composable(Screen.SizeGuide.route) { SizeGuideScreen() }
            composable(Screen.Materials.route) { MaterialsScreen(navController = navController) }
            composable(Screen.Analysis.route) { AnalysisScreen() }
            composable(Screen.Settings.route) { SettingsScreen() }

            // 素材查看/播放
            composable("media_viewer/{materialId}/{materialType}") { backStackEntry ->
                val materialId = backStackEntry.arguments?.getString("materialId") ?: ""
                val materialType = backStackEntry.arguments?.getString("materialType") ?: ""
                MediaViewerScreen(
                    materialId = materialId,
                    materialType = materialType,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
