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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.WavingHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 首次启动引导（Onboarding）—— OneUI 9 三页重写。
 *
 * 纪律：
 *   - 三页：欢迎 / 权限 / AI 配置
 *   - 每页：居中 48dp 圆形 tinted icon + 标题 + 描述 + 底部 CTA pill 按钮
 *   - 权限页与 AI 页用 OneUiListSection 承载列表项
 *   - 跳过的二次确认用 M3 AlertDialog
 *   - 每行点「去开启」触发 OnboardingViewModel.grant，对应真实授权
 *   - 字体档位 ≤5，间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值
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

// ============================================================
// 页内容
// ============================================================

@Composable
private fun WelcomePageBody() {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        PageHero(
            icon = Icons.Outlined.WavingHand,
            tint = OnboardingTints.engine,
            title = "欢迎使用 NovaCare",
            description = "把存储、内存、电池三件事，讲清楚、做到位。\n\n" +
                "我们不画饼。给每一个数字都配上来源，给每一条建议都配上依据，" +
                "做不到的事会直接写出来。",
        )
    }
}

@Composable
private fun PermissionsPageBody(
    items: List<OnboardingPermissionItem>,
    onGrant: (OnboardingPermissionItem) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHero(
            icon = Icons.Outlined.LockOpen,
            tint = OnboardingTints.shizuku,
            title = "需要你授权几件事",
            description = "点「去开启」会跳到系统设置页；回来后会自动重新检查状态。",
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        OneUiListSection(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                OneUiListRow(
                    icon = Icons.Outlined.LockOpen,
                    iconTint = if (item.granted) OnboardingTints.engine
                    else if (item.required) NovaCareTheme.colors.riskCaution
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    title = item.title,
                    subtitle = if (item.granted) "已开启" else item.consequence,
                    trailing = {
                        if (item.granted) {
                            Icon(
                                imageVector = Icons.Outlined.Check,
                                contentDescription = null,
                                tint = OnboardingTints.engine,
                                modifier = Modifier.size(OneUiSpacing.CardInner + OneUiSpacing.CardGap / 2),
                            )
                        } else {
                            Text(
                                text = item.actionLabel,
                                style = MaterialTheme.typography.labelLarge,
                                color = NovaCareTheme.colors.accent,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        role = Role.Button,
                                        onClick = { onGrant(item) },
                                    )
                                    .padding(
                                        horizontal = OneUiSpacing.SectionTitleGap,
                                        vertical = OneUiSpacing.CardGap,
                                    ),
                            )
                        }
                    },
                    showDivider = index < items.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun AiPageBody(cloudAiEnabled: Boolean, apiKeySet: Boolean) {
    val stateText = when {
        cloudAiEnabled && apiKeySet -> "已开启 · 已填写 Key"
        cloudAiEnabled -> "已开启，但还没填 Key —— 此时不会发起任何云端请求"
        apiKeySet -> "Key 已填写，但云端对话未开启"
        else -> "未开启 · 未填写 Key"
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        PageHero(
            icon = Icons.Outlined.AutoAwesome,
            tint = OnboardingTints.engine,
            title = "AI 助手（可选）",
            description = "它按固定的顺序工作，每一步你都看得见：\n" +
                "看本机状态 → 需要时云端查一次 → 结论回本地执行 → 你确认后才动手。\n\n" +
                "云端对话默认关闭。要开启，请在「设置 → AI → API Key」里填入你自己的 Key。",
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        OneUiListSection(modifier = Modifier.fillMaxWidth()) {
            OneUiListRow(
                icon = Icons.Outlined.AutoAwesome,
                iconTint = OnboardingTints.engine,
                title = "云端对话",
                subtitle = if (cloudAiEnabled) "已开启 · 可发起云端多轮对话" else "未开启 —— 仅本地识别固定问法",
                showDivider = true,
                trailing = {
                    StateBadge(
                        text = if (cloudAiEnabled) "已开启" else "未开启",
                        granted = cloudAiEnabled,
                    )
                },
            )
            OneUiListRow(
                icon = Icons.Outlined.Check,
                iconTint = OnboardingTints.engine,
                title = "API Key",
                subtitle = if (apiKeySet) "已写入 SettingsRepository（未进 Keystore）" else "未设置 —— 不发起任何网络请求",
                showDivider = false,
                trailing = {
                    StateBadge(
                        text = if (apiKeySet) "已设置" else "未设置",
                        granted = apiKeySet,
                    )
                },
            )
        }
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        Text(
            text = "当前：$stateText",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================
// 复用件
// ============================================================

@Composable
private fun PageHero(
    icon: ImageVector,
    tint: Color,
    title: String,
    description: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(HeroIconSize)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(HeroIconGlyph),
            )
        }
        Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StateBadge(text: String, granted: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
        color = if (granted) OnboardingTints.engine
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
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
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.size(OneUiSpacing.CardGap))
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                modifier = Modifier.size(OneUiSpacing.CardInner + OneUiSpacing.CardGap / 2),
            )
        }
    }
}

// ============================================================
// 调色 / 衍生尺寸
// ============================================================

/** OneUI 9 调色 —— 与 SettingsScreen / Battery / Memory / Storage 一致 */
private object OnboardingTints {
    val engine: Color = Color(0xFF2F6FED)
    val shizuku: Color = Color(0xFF1B7A46)
}

/** Hero 图标外圈 48dp = BlockGap * 2 */
private val HeroIconSize = OneUiSpacing.BlockGap * 2

/** Hero 图标字形 28dp = BlockGap + CardGap */
private val HeroIconGlyph = OneUiSpacing.BlockGap + OneUiSpacing.CardGap