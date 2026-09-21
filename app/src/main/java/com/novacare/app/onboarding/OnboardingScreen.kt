package com.novacare.app.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.GlassPanel
import com.novacare.ui.designsystem.LocalReduceMotion
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.PrimaryAction
import kotlinx.coroutines.launch

private const val ONBOARDING_PAGE_COUNT = 4

/**
 * 首次启动引导（Onboarding）入口。
 *
 * 接线方式（NavGraph 侧）：
 * ```
 * composable(Routes.ONBOARDING) {
 *     OnboardingScreen(
 *         onFinished = {
 *             navController.navigate(Routes.HOME) {
 *                 popUpTo(Routes.ONBOARDING) { inclusive = true }
 *             }
 *         },
 *     )
 * }
 * ```
 * 是否显示由 `SettingsRepository.Settings.onboardingCompleted` 决定；
 * 完成或跳过后本页内部会把它置为 true，之后不再显示。
 *
 * @param onFinished 完成或跳过后的回调（内部已先落库，调用方只负责导航）
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val items by viewModel.permissionItems.collectAsStateWithLifecycle()
    val cloudAiEnabled by viewModel.cloudAiEnabled.collectAsStateWithLifecycle()
    val apiKeySet by viewModel.apiKeySet.collectAsStateWithLifecycle()

    // 从系统设置页返回时重查实况：否则用户授了权，回来看到的还是"未开启"
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val pagerState = rememberPagerState(pageCount = { ONBOARDING_PAGE_COUNT })
    val scope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val view = LocalView.current

    val isLastPage = pagerState.currentPage == ONBOARDING_PAGE_COUNT - 1

    // "中间断掉"修复 / 引导 Back 键：默认行为是把 Onboarding 整页从栈里弹出后
    // 直接退出 App —— 用户在最后一页误按 Back，会失去 4 页阅读进度，重新打开
    // 还得再翻一遍。这里在非第一页时拦截 Back：先翻到上一页再放行。第一页保留
    // 默认退出行为，让用户随时可以离开。
    BackHandler(enabled = pagerState.currentPage > 0) {
        scope.launch {
            if (reduceMotion) {
                pagerState.scrollToPage(pagerState.currentPage - 1)
            } else {
                pagerState.animateScrollToPage(pagerState.currentPage - 1)
            }
        }
    }

    AuroraBackground(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets.safeDrawing,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 24.dp),
            ) {
                // ---- 顶部：跳过（最后一页不显示，那里就是终点）----
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    if (!isLastPage) {
                        Text(
                            text = "跳过",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    role = Role.Button,
                                    onClick = {
                                        NovaTap(view)
                                        viewModel.complete(onFinished)
                                    },
                                )
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) { page ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.emphasized) +
                            slideInHorizontally(
                                animationSpec = MotionTokens.emphasizedOffset,
                                initialOffsetX = { it / 12 },
                            ),
                    ) {
                        when (page) {
                            0 -> WelcomePage()
                            1 -> PermissionsPage(items = items, onGrant = viewModel::grant)
                            2 -> AiPage(cloudAiEnabled = cloudAiEnabled, apiKeySet = apiKeySet)
                            3 -> ReadyPage(items = items, onGrant = viewModel::grant)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp)) {
                        PageIndicator(
                            count = ONBOARDING_PAGE_COUNT,
                            selected = pagerState.currentPage,
                        )
                        Spacer(Modifier.height(20.dp))
                        PrimaryAction(
                            text = if (isLastPage) "开始使用" else "继续",
                            onClick = {
                                NovaTap(view)
                                if (isLastPage) {
                                    viewModel.complete(onFinished)
                                } else {
                                    scope.launch {
                                        if (reduceMotion) {
                                            pagerState.scrollToPage(pagerState.currentPage + 1)
                                        } else {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 页码指示点。
 *
 * 当前点用强调色并拉宽成短横（One UI 的做法），其余是极淡的圆点 ——
 * 不用位移弹簧做「跳动指示器」，那会在这种安静的页面里显得多余。
 */
@Composable
private fun PageIndicator(count: Int, selected: Int) {
    val colors = NovaCareTheme.colors
    val shape = CircleShape
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(
                        width = if (active) 18.dp else 7.dp,
                        height = 7.dp,
                    )
                    .background(
                        color = if (active) {
                            colors.accent
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f)
                        },
                        shape = shape,
                    ),
            )
        }
    }
}
