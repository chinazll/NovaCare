package com.novacare.app.navigation

import android.os.Environment
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.novacare.app.settings.SettingsScreen
import com.novacare.feature.assistant.AssistantScreen
import com.novacare.feature.automation.AutomationScreen
import com.novacare.feature.clean.CleanScreen
import com.novacare.feature.freeze.FreezeScreen
import com.novacare.feature.home.HomeScreen

object Routes {
    const val HOME = "home"
    const val CLEAN = "clean"
    const val FREEZE = "freeze"
    const val AUTOMATION = "automation"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
}

/**
 * 三层导航（蓝图 §4.5.1）：
 * - L1 首页：普通用户止步于此（评分 + 一句话 + 一个按钮）
 * - L2 功能页：清理 / 冻结 / 自动化 / 助手
 * - L3 高级模式：设置里开启
 *
 * 转场：横向滑动 + 淡入淡出，300ms / cubic-bezier(0.16,1,0.3,1) 等价的
 * FastOutSlowIn。上一版没有任何转场，页面切换是硬切 —— 显得廉价。
 */
@Composable
fun NovaCareNavHost() {
    val navController = rememberNavController()
    val rootPath = remember {
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }

    val enterSpec = tween<IntOffset>(durationMillis = 320)
    val fadeSpec = tween<Float>(durationMillis = 220)

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        enterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = enterSpec,
            ) + fadeIn(fadeSpec)
        },
        exitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.Start,
                animationSpec = enterSpec,
            ) + fadeOut(fadeSpec)
        },
        popEnterTransition = {
            slideIntoContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = enterSpec,
            ) + fadeIn(fadeSpec)
        },
        popExitTransition = {
            slideOutOfContainer(
                AnimatedContentTransitionScope.SlideDirection.End,
                animationSpec = enterSpec,
            ) + fadeOut(fadeSpec)
        },
    ) {
        composable(Routes.HOME) {
            HomeScreen(onNavigate = { route -> navController.navigateTo(route) })
        }
        composable(Routes.CLEAN) { CleanScreen(rootPath = rootPath) }
        composable(Routes.FREEZE) { FreezeScreen(rootPath = rootPath) }
        composable(Routes.AUTOMATION) { AutomationScreen() }
        composable(Routes.ASSISTANT) { AssistantScreen(rootPath = rootPath) }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/**
 * 防重复导航。
 *
 * 上一版的 `navigate(route)` 在同一路由快速点击时会压入多个相同页面，
 * 用户按返回要连按好几次。这里加幂等保护。
 */
private fun androidx.navigation.NavHostController.navigateTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        launchSingleTop = true
    }
}
