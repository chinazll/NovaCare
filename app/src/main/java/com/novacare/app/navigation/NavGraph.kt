package com.novacare.app.navigation

import android.os.Environment
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.novacare.app.settings.SettingsScreen
import com.novacare.feature.assistant.AssistantScreen
import com.novacare.feature.automation.AutomationScreen
import com.novacare.feature.clean.CleanScreen
import com.novacare.feature.freeze.FreezeScreen
import com.novacare.feature.home.HomeScreen
import com.novacare.ui.designsystem.NovaDock
import com.novacare.ui.designsystem.NovaDockItem

object Routes {
    const val HOME = "home"
    const val CLEAN = "clean"
    const val FREEZE = "freeze"
    const val AUTOMATION = "automation"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"
}

/**
 * 底部导航项（5 格）。
 *
 * 【为什么是这 5 个，助手为什么不在里面】
 *   底栏是「常驻、平级、高频」的位置，所以只放用户每天都会碰的动词。
 *   - 首页：状态与总入口
 *   - 清理：本 App 的核心动词
 *   - 冻结：第二核心动词
 *   - 自动化：低频但仍属「设置一次、长期生效」的主功能
 *   - 设置：必须有稳定入口，否则用户找不到权限开关
 *
 *   助手（ASSISTANT）**刻意不进底栏**：
 *   它的能力依赖引擎与 AI 配置，未配置时点进去只会看到空壳——
 *   把「可能不可用」的东西放进常驻导航，是给用户挖坑。
 *   它由首页的动作卡进入，入口处即会说明可用性。这是「零伪造」原则的落点。
 */
private val DockItems = listOf(
    NovaDockItem(
        route = Routes.HOME,
        label = "首页",
        icon = Icons.Outlined.Home,
        selectedIcon = Icons.Filled.Home,
    ),
    NovaDockItem(
        route = Routes.CLEAN,
        label = "清理",
        icon = Icons.Outlined.CleaningServices,
        selectedIcon = Icons.Filled.CleaningServices,
    ),
    NovaDockItem(
        route = Routes.FREEZE,
        label = "冻结",
        icon = Icons.Outlined.AcUnit,
        selectedIcon = Icons.Outlined.AcUnit,
    ),
    NovaDockItem(
        route = Routes.AUTOMATION,
        label = "自动化",
        icon = Icons.Outlined.Speed,
        selectedIcon = Icons.Filled.Speed,
    ),
    NovaDockItem(
        route = Routes.SETTINGS,
        label = "设置",
        icon = Icons.Outlined.Settings,
        selectedIcon = Icons.Filled.Settings,
    ),
)

/**
 * 应用根导航。
 *
 * 【结构变化 / 对照上一版】
 *   上一版：裸 `NavHost`，没有任何底部导航 —— 六个页面全靠首页卡片间接跳转，
 *          装上后「不知道从哪用」。这是可用性硬伤，不是美观问题。
 *   现在：  `Scaffold(bottomBar = NovaDock)` 包裹 `NavHost`。
 *          所有页面共用一条常驻导航，任何位置都能一步到任何主功能。
 *
 * 【为什么用 Scaffold 而不是把 dock 叠在 NavHost 外面】
 *   Scaffold 会把底栏高度通过 `innerPadding` 交给内容层。
 *   内容层据此留出底部空间，滚动到底时最后一项不会被 dock 盖住。
 *   若把 dock 单纯叠在上面，每个页面都得自己猜 dock 有多高——必然出错。
 *
 * 【转场退让】
 *   dock 引入后，横向滑动转场会与「底部胶囊固定不动」产生轻微割裂感。
 *   这里把转场收敛为**淡入 + 极短位移**（不再整屏横滑）：
 *   底栏是不动的锚，内容在锚上方轻推一下换页。这更接近 One UI 的做法，
 *   也让「五个平级页面」的语义成立——平级页面之间不该有方向性滑动。
 */
@Composable
fun NovaCareNavHost() {
    val navController = rememberNavController()
    val rootPath = remember {
        Environment.getExternalStorageDirectory()?.absolutePath ?: "/storage/emulated/0"
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val fadeSpec = tween<Float>(durationMillis = 200)
    // 位移极轻（整宽的 1/16）—— 平级页面之间不该有方向性的整屏横滑，
    // 否则「五个 tab 是同一层」的语义会被破坏，只剩「又开了一个页面」。
    val shiftSpec = tween<IntOffset>(durationMillis = 240)

    Scaffold(
        // 内容自己处理状态栏内边距（各页有其自己的 header 布局），
        // 底栏由 NovaDock 内部处理导航栏内边距。
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NovaDock(
                items = DockItems,
                currentRoute = currentRoute,
                onSelect = { route -> navController.switchTab(route) },
            )
        },
    ) { innerPadding ->
        val bottomInset = innerPadding.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier
                .padding(bottom = bottomInset)
                .consumeWindowInsets(PaddingValues(bottom = bottomInset)),
            enterTransition = {
                fadeIn(fadeSpec) + slideIntoContainer(
                    AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = shiftSpec,
                    initialOffset = { it / 16 },
                )
            },
            exitTransition = { fadeOut(fadeSpec) },
            popEnterTransition = { fadeIn(fadeSpec) },
            popExitTransition = { fadeOut(fadeSpec) },
        ) {
            composable(Routes.HOME) {
                HomeScreen(onNavigate = { route -> navController.navigateTo(route) })
            }
            composable(Routes.CLEAN) { CleanScreen(rootPath = rootPath) }
            composable(Routes.FREEZE) { FreezeScreen(rootPath = rootPath) }
            composable(Routes.AUTOMATION) { AutomationScreen() }
            composable(Routes.ASSISTANT) { AssistantScreen(rootPath = rootPath) }
            composable(Routes.SETTINGS) {
                // 设置现在既是底栏 tab、也是可能被 push 进来的页面。
                // 从底栏点进来时栈里只有它自己，此时 onBack 没有可退的页面——
                // 交给系统处理（Activity 退出）比弹回首页更符合直觉。
                SettingsScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/**
 * 底栏 tab 切换：**不堆栈**。
 *
 * 与 `navigateTo` 的关键区别：
 *   - `navigate` 会持续压栈 → 从「清理」点到「设置」再到「首页」，
 *     按三次返回要退三次，且后退顺序与点击顺序相反。这对平级 tab 是错的。
 *   - tab 之间应该「互相替换」：用 `popUpTo(startDestination) { saveState }`
 *     把中间态清掉，并把状态存起来，回到首页时恢复原样。
 *
 * 这样后退按钮在首页才退出应用，符合 Android 平台预期。
 */
private fun NavHostController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.startDestinationId) {
            // 保存被替换页面的滚动位置与状态，切回来时不用重新加载
            saveState = true
        }
        launchSingleTop = true
        // 重复选择同一 tab 时恢复其已保存状态，而非重建
        restoreState = true
    }
}

/**
 * 详情页推入（从首页卡片进入「清理 / 冻结 / 助手」等）。
 *
 * 与 tab 切换不同：这类跳转是一次「下钻」，应当压栈，返回键能退回首页。
 * 上一版的 `navigate(route)` 在同路由快速点击时会压入多个相同页面，
 * 用户要连按好几次返回——这里用 `launchSingleTop` 做幂等保护。
 */
private fun NavHostController.navigateTo(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) { launchSingleTop = true }
}
