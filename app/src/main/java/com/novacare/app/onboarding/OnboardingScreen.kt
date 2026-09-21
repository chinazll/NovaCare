package com.novacare.app.onboarding

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 首次启动引导（Onboarding）。
 *
 * OneUI 9 重写后的纪律：
 *   - 三页：欢迎 / 权限 / AI 设置
 *   - 单 CTA：每页只允许一个主操作
 *   - 顶部无 OneUiAppBar —— 全屏页用 windowInsetsPadding 处理系统栏
 *   - 字体档位 ≤5：titleLarge / titleSmall / bodyMedium / labelLarge / labelSmall
 *   - 间距 / 圆角全部从 OneUiSpacing 取值
 *   - 跳过的二次确认用 M3 AlertDialog
 *   - 每行点「去开启」触发 OnboardingViewModel.grant，对应真实授权
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

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var page by remember { mutableIntStateOf(0) }
    var showSkipConfirm by remember { mutableStateOf(false) }
    val totalPages = 3
    val isLastPage = page == totalPages - 1

    BackHandler(enabled = page > 0) {
        page -= 1
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = OneUiSpacing.ScreenEdge),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isLastPage) {
                    Text(
                        text = "跳过",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClick = { showSkipConfirm = true },
                            )
                            .padding(
                                horizontal = OneUiSpacing.SectionTitleGap,
                                vertical = OneUiSpacing.CardGap,
                            ),
                    )
                }
            }

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (page) {
                    0 -> WelcomePageBody()
                    1 -> PermissionsPageBody(items = items, onGrant = viewModel::grant)
                    else -> AiPageBody(
                        cloudAiEnabled = cloudAiEnabled,
                        apiKeySet = apiKeySet,
                    )
                }
            }

            Spacer(Modifier.height(OneUiSpacing.BlockGap))

            PageIndicatorBody(count = totalPages, selected = page)
            Spacer(Modifier.height(OneUiSpacing.CardInner))

            PrimaryCtaBody(
                text = if (isLastPage) "开始使用" else "继续",
                onClick = {
                    if (isLastPage) {
                        viewModel.complete(onFinished)
                    } else {
                        page += 1
                    }
                },
            )

            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }
    }

    if (showSkipConfirm) {
        AlertDialog(
            onDismissRequest = { showSkipConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showSkipConfirm = false
                    viewModel.complete(onFinished)
                }) {
                    Text(
                        "确认跳过",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaCareTheme.colors.riskCaution,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSkipConfirm = false }) {
                    Text("再看看", style = MaterialTheme.typography.labelLarge)
                }
            },
            title = {
                Text(
                    text = "跳过引导？",
                    style = MaterialTheme.typography.titleSmall,
                )
            },
            text = {
                Text(
                    text = "未授权的能力（使用情况 / 通知 / Shizuku）会直接表现为" +
                        "对应功能不可用。可以稍后在设置里补开。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
    }
}

@Composable
private fun PageIndicatorBody(count: Int, selected: Int) {
    val accent = NovaCareTheme.colors.accent
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            Box(
                modifier = Modifier
                    .padding(horizontal = OneUiSpacing.CardGap / 2)
                    .size(if (active) OneUiSpacing.CardInner else OneUiSpacing.CardGap / 2)
                    .clip(CircleShape)
                    .background(
                        if (active) accent
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.32f),
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryCtaBody(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap)
            .clip(MaterialTheme.shapes.extraLarge)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        color = cs.primary,
        contentColor = cs.onPrimary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun WelcomePageBody() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "NovaCare",
            style = MaterialTheme.typography.titleLarge,
            color = NovaCareTheme.colors.accent,
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        Text(
            text = "把存储、内存、电池三件事，讲清楚、做到位",
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        Text(
            text = "我们不画饼。给每一个数字都配上来源，给每一条建议都配上依据，" +
                "做不到的事会直接写出来。",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun PermissionsPageBody(
    items: List<OnboardingPermissionItem>,
    onGrant: (OnboardingPermissionItem) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val caution = NovaCareTheme.colors.riskCaution
    val good = NovaCareTheme.colors.healthGood
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "需要你授权几件事",
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Text(
            text = "点「去开启」会跳到系统设置页；回来后会自动重新检查状态。",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))

        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = OneUiSpacing.SectionTitleGap),
                verticalAlignment = Alignment.Top,
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = OneUiSpacing.SectionTitleGap)
                        .size(OneUiSpacing.SectionTitleGap)
                        .clip(CircleShape)
                        .background(
                            if (item.granted) good
                            else if (item.required) caution
                            else cs.onSurfaceVariant.copy(alpha = 0.32f),
                        ),
                )
                Spacer(Modifier.size(OneUiSpacing.CardInner))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                    Text(
                        text = item.consequence,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!item.granted && item.required) caution
                        else cs.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(OneUiSpacing.CardInner))
                Text(
                    text = item.actionLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (item.granted) good else NovaCareTheme.colors.accent,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = { if (!item.granted) onGrant(item) },
                        )
                        .padding(
                            horizontal = OneUiSpacing.SectionTitleGap,
                            vertical = OneUiSpacing.CardGap,
                        ),
                )
            }
        }
    }
}

@Composable
private fun AiPageBody(cloudAiEnabled: Boolean, apiKeySet: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "AI 助手",
            style = MaterialTheme.typography.titleLarge,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Text(
            text = if (cloudAiEnabled && apiKeySet) {
                "已配置云端对话 —— 你可以直接问自然语言。"
            } else {
                "未配置云端 key 时，助手只能识别固定问法。" +
                    "配置后获得真正的多轮对话。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )

        Spacer(Modifier.height(OneUiSpacing.CardInner))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                BulletBody("云端：流式多轮对话，能看你的设备状态再回答")
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                BulletBody("本地：离线、确定性，只识别固定问法")
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                BulletBody("两者都不替你执行：所有动作都需你点确认")
            }
        }

        Spacer(Modifier.height(OneUiSpacing.CardInner))

        Text(
            text = "完成后可在 设置 → AI 里开启云端对话 / 填 API Key",
            style = MaterialTheme.typography.labelLarge,
            color = NovaCareTheme.colors.accent,
        )
    }
}

@Composable
private fun BulletBody(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = OneUiSpacing.SectionTitleGap)
                .size(OneUiSpacing.SectionTitleGap)
                .clip(CircleShape)
                .background(NovaCareTheme.colors.accent),
        )
        Spacer(Modifier.size(OneUiSpacing.SectionTitleGap))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
        )
    }
}
