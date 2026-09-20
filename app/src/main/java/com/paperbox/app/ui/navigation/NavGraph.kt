package com.paperbox.app.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.hilt.navigation.compose.hiltViewModel
import com.paperbox.app.data.api.dataStore
import com.paperbox.app.data.api.PrefsKeys
import com.paperbox.app.ui.auth.LoginScreen
import com.paperbox.app.ui.quote.QuoteResultPage
import com.paperbox.app.ui.quote.QuoteScreen
import com.paperbox.app.ui.quote.QuoteViewModel
import com.paperbox.app.ui.sizeguide.SizeGuideScreen
import com.paperbox.app.ui.materials.MaterialsScreen
import com.paperbox.app.ui.media.MediaViewerScreen
import com.paperbox.app.ui.analysis.AnalysisScreen
import com.paperbox.app.ui.settings.SettingsScreen
import com.paperbox.app.R
import com.paperbox.app.ui.components.BottomNavBar
import com.paperbox.app.ui.components.BottomNavItem
import java.net.URLDecoder

/** 报价结果页 —— 整屏推入，不在底部 tab 里 */
const val QUOTE_RESULT_ROUTE = "quote_result"

sealed class Screen(val route: String, val title: String) {
    data object Quote : Screen("quote", "报价")
    data object SizeGuide : Screen("sizeguide", "规格")
    data object Materials : Screen("materials", "素材")
    data object Analysis : Screen("analysis", "对账")
    data object Profile : Screen("settings", "个人")
}

val bottomTabs = listOf(
    Screen.Quote,
    Screen.Materials,   // 素材在规格前面
    Screen.SizeGuide,
    Screen.Analysis,
    Screen.Profile
)

/** 将 Screen 映射为 BottomNavItem */
private fun Screen.toBottomNavItem(): BottomNavItem {
    val iconRes = when (this) {
        Screen.Quote -> R.drawable.ic_nav_quote
        Screen.SizeGuide -> R.drawable.ic_nav_sizeguide
        Screen.Materials -> R.drawable.ic_nav_materials
        Screen.Analysis -> R.drawable.ic_nav_analysis
        Screen.Profile -> R.drawable.ic_nav_profile
    }
    val iconResFilled = when (this) {
        Screen.Quote -> R.drawable.ic_nav_quote_filled
        Screen.SizeGuide -> R.drawable.ic_nav_sizeguide_filled
        Screen.Materials -> R.drawable.ic_nav_materials_filled
        Screen.Analysis -> R.drawable.ic_nav_analysis_filled
        Screen.Profile -> R.drawable.ic_nav_profile_filled
    }
    return BottomNavItem(route = route, label = title, iconRes = iconRes, iconResFilled = iconResFilled)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = androidx.compose.ui.platform.LocalContext.current

    val dataStore = context.dataStore
    val tokenFlow = dataStore.data.collectAsState(initial = null)
    val isLoggedIn = tokenFlow.value?.get(PrefsKeys.TOKEN)?.isNotEmpty() == true

    if (!isLoggedIn) {
        LoginScreen(onLoginSuccess = { })
        return
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // 全屏页：素材查看页和报价结果页都不要底部导航栏
    val isFullScreen = currentRoute?.startsWith("media_viewer") == true ||
        currentRoute == QUOTE_RESULT_ROUTE

    // 切底部 tab 的统一入口，和底部栏点击用的是同一套行为
    val navigateToTab: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    // 单一 NavHost，根据是否是全屏页决定是否包裹 Scaffold
    val navHost = @Composable { modifier: Modifier ->
        NavHost(
            navController = navController,
            startDestination = Screen.Quote.route,
            modifier = modifier
        ) {
            composable(Screen.Quote.route) {
                QuoteScreen(
                    onGenerateQuote = { navController.navigate(QUOTE_RESULT_ROUTE) },
                    onOpenSizeGuide = { navigateToTab(Screen.SizeGuide.route) }
                )
            }
            composable(QUOTE_RESULT_ROUTE) { entry ->
                // 必须显式传 owner，否则 hiltViewModel() 会新建第二个
                // QuoteViewModel（表单全 0、result 为 null），页面一片空白
                val parentEntry = remember(entry) {
                    runCatching { navController.getBackStackEntry(Screen.Quote.route) }.getOrNull()
                }
                if (parentEntry == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    val quoteViewModel: QuoteViewModel = hiltViewModel(parentEntry)
                    val state by quoteViewModel.uiState.collectAsState()

                    // 进程死亡重建时表单会丢，没有结果就直接退回报价页
                    LaunchedEffect(state.result) {
                        if (state.result == null) navController.popBackStack()
                    }

                    QuoteResultPage(
                        state = state,
                        onBack = { navController.popBackStack() },
                        onSave = { quoteViewModel.saveQuoteRecord() },
                        onClearTraceCode = { quoteViewModel.clearTraceCode() },
                        onClearError = { quoteViewModel.clearError() },
                        onToggleLanguage = { quoteViewModel.toggleLanguage() }
                    )
                }
            }
            composable(Screen.SizeGuide.route) { SizeGuideScreen() }
            composable(Screen.Materials.route) { MaterialsScreen(navController = navController) }
            composable(Screen.Analysis.route) { AnalysisScreen() }
            composable(Screen.Profile.route) { SettingsScreen() }

            composable("media_viewer/{materialId}/{materialType}") { backStackEntry ->
                val materialId = backStackEntry.arguments?.getString("materialId") ?: ""
                val materialType = URLDecoder.decode(
                    backStackEntry.arguments?.getString("materialType") ?: "",
                    "UTF-8"
                )
                val materialsJson: String? = navController.previousBackStackEntry?.savedStateHandle?.get<String>("materials_json")
                val currentIndex: Int = navController.previousBackStackEntry?.savedStateHandle?.get<Int>("current_index") ?: 0
                MediaViewerScreen(
                    materialId = materialId,
                    materialType = materialType,
                    materialsJson = materialsJson,
                    currentIndex = currentIndex,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    if (isFullScreen) {
        // 全屏页：无 Scaffold，铺满整屏
        navHost(Modifier.fillMaxSize())
    } else {
        // 普通页面：有底部导航栏
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                BottomNavBar(
                    items = bottomTabs.map { it.toBottomNavItem() },
                    selectedRoute = currentRoute ?: Screen.Quote.route,
                    onItemSelected = navigateToTab
                )
            }
        ) { innerPadding ->
            navHost(Modifier.padding(innerPadding))
        }
    }
}
