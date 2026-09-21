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
import com.novacare.app.onboarding.OnboardingScreen
import com.novacare.app.settings.SettingsScreen
import com.novacare.feature.assistant.AssistantScreen
import com.novacare.feature.automation.AutomationScreen
import com.novacare.feature.clean.CleanScreen
import com.novacare.feature.freeze.FreezeScreen
import com.novacare.feature.guardian.battery.BatteryGuardianScreen
import com.novacare.feature.guardian.memory.MemoryGuardianScreen
import com.novacare.feature.guardian.storage.StorageGuardianScreen
import com.novacare.feature.home.HomeDestination
import com.novacare.feature.home.HomeScreen
import com.novacare.ui.designsystem.NovaDock
import com.novacare.ui.designsystem.NovaDockItem

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val CLEAN = "clean"
    const val FREEZE = "freeze"
    const val AUTOMATION = "automation"
    const val ASSISTANT = "assistant"
    const val SETTINGS = "settings"

    // ---- 守护中心：按设备维度拆分（存储 / 内存 / 电池）----
    // 不进底栏：NovaDock 是固定宽度胶囊（未选 60dp / 选中 90dp / 间距 6dp），
    // 6 个 tab 需要 420dp，而 360dp 宽的设备只有 320dp 可用 —— 第 6 个会被压成 0 宽。
    // 因此走「设置 → 设备守护」入口，由 Routes 统一跳转。
    const val STORAGE = "guardian/storage"
    const val MEMORY = "guardian/memory"
    const val BATTERY = "guardian/battery"
}

/**
 * 底部导航项（5 格）。
 *
 * 【信息架构：每个 tab 有唯一且互斥的职责，必须能一句话说清】
 *   首页     —— 设备现在有多健康，哪一项该去处理。（只读，不执行任何操作）
 *   清理     —— 扫描出可释放的东西，勾选后释放。（唯一的清理执行入口）
 *   冻结     —— 让长期未用的应用停下来。（应用停用的唯一入口）
 *   自动化   —— 配置"什么条件下自动做什么"的规则。（规则的配置与启停）
 *   设置     —— 权限、内核、AI、外观。（偏好与能力的总开关）
 *
 * 【互斥规则】
 *   一个操作只在它所属的 tab 里发生，别处只能「引导过去」并说明理由。
 *   因此首页不出现执行按钮，也不重复列出这五个 tab——底栏已经列了一遍，
 *   首页再抄一遍等于让用户猜"这两个是不是同一回事"。
 *   首页破例只有两处：① 助手不在底栏，首页是它唯一入口；
 *   ② 健康维度确实偏低且真有依据时，该维度行才出现跳转。
 *
 * 【助手为什么不进底栏】
 *   底栏是「常驻、平级、高频」的位置，只放每天都会碰的东西。
 *   助手的能力依赖内核与 AI 配置，未配置时点进去只会看到空壳——
 *   把「可能不可用」的东西放进常驻导航，是给用户挖坑。
 *   它由首页的助手卡进入（走压栈，返回回首页）。这是「零伪造」原则的落点。
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
fun NovaCareNavHost(
    // 首次启动（onboardingCompleted == false）时由 MainActivity 传入 ONBOARDING。
    // 默认是 HOME，保证任何没传的地方行为跟以前一致。
    startDestination: String = Routes.HOME,
) {
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
            // 引导页不是五个主功能之一，底栏在它上面没有意义 —— 此时不渲染 dock，
            // 否则用户会在还没读完权限说明时就跑去点别的页。
            if (currentRoute != Routes.ONBOARDING) {
                NovaDock(
                    items = DockItems,
                    currentRoute = currentRoute,
                    onSelect = { route -> navController.switchTab(route) },
                )
            }
        },
    ) { innerPadding ->
        val bottomInset = innerPadding.calculateBottomPadding()
        NavHost(
            navController = navController,
            startDestination = startDestination,
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
            composable(Routes.ONBOARDING) {
                OnboardingScreen(
                    onFinished = {
                        // 引导是一次性的：完成后把自己从栈里连同弹出，
                        // 返回键不会再退回引导页。
                        // launchSingleTop 防止「开始使用」被快速连点时
                        // 多次压入 HOME，导致 Back 要退多次才能退出 App。
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Routes.HOME) {
                // 首页的两个出口语义不同，不能混用一个回调：
                //   - 去清理 / 冻结 → tab 切换（switchTab），底栏选中态必须与内容一致；
                //   - 去助手       → 压栈下钻，返回键退回首页。
                // 如果这里统一用 navigateTo 压栈，用户从首页跳进清理页后，
                // 底栏仍高亮「首页」而屏幕内容是清理页 —— 选中态与内容打架，
                // 这正是上一版"不知道自己在哪"的直接来源。
                HomeScreen(
                    onOpenAssistant = { navController.navigateTo(Routes.ASSISTANT) },
                    onNavigate = { destination -> navController.switchTab(routeOf(destination)) },
                )
            }
            composable(Routes.CLEAN) { CleanScreen(rootPath = rootPath) }
            composable(Routes.FREEZE) { FreezeScreen(rootPath = rootPath) }
            composable(Routes.AUTOMATION) { AutomationScreen() }
            composable(Routes.ASSISTANT) { AssistantScreen(rootPath = rootPath) }
            composable(Routes.SETTINGS) {
                // 设置现在既是底栏 tab、也是可能被 push 进来的页面。
                // 从底栏点进来时栈里只有它自己，此时 onBack 没有可退的页面——
                // 交给系统处理（Activity 退出）比弹回首页更符合直觉。
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenGuardian = { route -> navController.navigateTo(route) },
                )
            }
            // ---- 守护中心三个模块：压栈下钻，返回键退回设置页 ----
            composable(Routes.STORAGE) { StorageGuardianScreen(rootPath = rootPath) }
            composable(Routes.MEMORY) { MemoryGuardianScreen() }
            composable(Routes.BATTERY) { BatteryGuardianScreen() }
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
        // 锚点写死 HOME 而**不用** graph.startDestinationId：
        // startDestination 可能是 ONBOARDING（首次启动由 MainActivity 传入），
        // 而引导页完成后已经把自己 inclusive pop 掉了 ——此时按 startDestinationId 去 pop
        // 会在栈里找不到它，结果是连 HOME 一起清掉，切完 tab 按返回直接退出应用。
        // tab 的语义是「HOME 是根，其余 tab 互相替换」，所以锚点就该是 HOME。
        popUpTo(Routes.HOME) {
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

/** 首页的「去处理」目的地 → 底栏 tab 路由。首页只能引导去这两个执行页。 */
private fun routeOf(destination: HomeDestination): String = when (destination) {
    HomeDestination.CLEAN -> Routes.CLEAN
    HomeDestination.FREEZE -> Routes.FREEZE
}
