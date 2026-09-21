package com.novacare.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BatteryFull
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.app.navigation.Routes
import com.novacare.core.data.ThemeMode
import com.novacare.core.model.CloudModel
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 设置页 —— OneUI 9.5 真实设计语言（重写）。
 *
 * 顶部固定 OneUiAppBar（含 onBack）；下方分组列表。
 * 每行用 M3 ListItem 而非手搓 Row —— 让 M3 自动处理 leading/content/trailing 的
 * 高度对齐、minHeight、点击态。
 *
 * 子菜单"设备守护"用 [expandedGuardian] 局部 state 控制展开 /
 * 折叠 —— 这是首页以外二级页常用的"先展开再导航"模式。
 *
 * 弹出层只用真实 AlertDialog（不再用 AlertDialog 包一层假装 BasicAlertDialog）。
 *
 * 字号档位 ≤5：titleLarge / titleSmall / bodyMedium / bodySmall / labelLarge。
 * 间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值。
 */
@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    /** 跳到守护中心的某个模块（传入 Routes.STORAGE / MEMORY / BATTERY） */
    onOpenGuardian: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val capabilities by viewModel.capabilities.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()
    val shizukuAvailable by viewModel.shizukuAvailable.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val view = LocalView.current

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showThemeDialog by remember { mutableStateOf(false) }
    var expandedGuardian by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "设置", onBack = onBack)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (notice != null) {
                    NoticeCard(text = notice!!, onDismiss = { viewModel.consumeNotice() })
                    Spacer(Modifier.height(OneUiSpacing.CardInner))
                }

                // ---- 引擎 / 权限 ----
                SectionLabel("引擎与权限")
                SettingsList {
                    ToggleListItem(
                        icon = Icons.Outlined.Memory,
                        title = "Nova 内核",
                        subtitle = if (engineAvailable) "已加载 · $engineVersion"
                        else "未加载（Rust .so 缺失或 ABI 不匹配）",
                        checked = engineAvailable,
                        onCheckedChange = null,
                    )
                    ListDivider()
                    ToggleListItem(
                        icon = Icons.Outlined.AcUnit,
                        title = "Shizuku",
                        subtitle = if (shizukuAvailable) "已授权，可一键冻结 / 高级清理"
                        else "未授权 — 配置后可一键冻结",
                        checked = shizukuAvailable,
                        onCheckedChange = null,
                        onClick = { NovaTap(view); viewModel.requestShizuku() },
                    )
                    ListDivider()
                    capabilities.forEachIndexed { index, cap ->
                        ToggleListItem(
                            icon = when (cap.icon) {
                                SettingsViewModel.CapabilityIcon.USAGE -> Icons.Outlined.Info
                                SettingsViewModel.CapabilityIcon.FILES -> Icons.Outlined.Folder
                                SettingsViewModel.CapabilityIcon.NOTIFICATIONS -> Icons.Outlined.Notifications
                            },
                            title = cap.title,
                            subtitle = if (cap.granted) cap.why else cap.consequence,
                            checked = cap.granted,
                            onCheckedChange = null,
                            onClick = { NovaTap(view); viewModel.grant(cap.capability) },
                        )
                        if (index < capabilities.lastIndex) ListDivider()
                    }
                }

                // ---- 设备守护（可展开子菜单）----
                if (onOpenGuardian != null) {
                    Spacer(Modifier.height(OneUiSpacing.BlockGap))
                    SectionLabel("设备守护")
                    SettingsList {
                        ExpandableGuardianRow(
                            expanded = expandedGuardian,
                            onToggle = { expandedGuardian = !expandedGuardian },
                        )
                        if (expandedGuardian) {
                            ListDivider()
                            GuardianSubItem(
                                icon = Icons.Outlined.SdCard,
                                title = "存储守护",
                                subtitle = "大文件 / 重复文件 / 残留与空目录",
                                onClick = {
                                    NovaTap(view)
                                    onOpenGuardian(Routes.STORAGE)
                                },
                            )
                            GuardianSubItem(
                                icon = Icons.Outlined.Memory,
                                title = "内存守护",
                                subtitle = "实时内存与进程占用排行",
                                onClick = {
                                    NovaTap(view)
                                    onOpenGuardian(Routes.MEMORY)
                                },
                            )
                            GuardianSubItem(
                                icon = Icons.Outlined.BatteryFull,
                                title = "电池守护",
                                subtitle = "电量 / 温度 / 前台时长与省电建议",
                                onClick = {
                                    NovaTap(view)
                                    onOpenGuardian(Routes.BATTERY)
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // ---- 模式 ----
                SectionLabel("模式")
                SettingsList {
                    ToggleListItem(
                        icon = Icons.Outlined.Memory,
                        title = "高级模式",
                        subtitle = if (settings.advancedMode)
                            "已启用 · 一键清理所有应用缓存（pm trim-caches）"
                        else
                            "未启用 · 默认走系统设置页引导",
                        checked = settings.advancedMode,
                        onCheckedChange = { viewModel.setAdvanced(it) },
                    )
                }

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // ---- AI ----
                SectionLabel("AI")
                SettingsList {
                    ToggleListItem(
                        icon = Icons.Outlined.SmartToy,
                        title = "云端对话",
                        subtitle = if (settings.cloudAiEnabled)
                            "已开启 · 模型 ${settings.cloudModel.displayName}"
                        else
                            "未开启 · 助手只能识别固定问法",
                        checked = settings.cloudAiEnabled,
                        onCheckedChange = { viewModel.setCloudAi(it) },
                    )
                    ListDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val next = if (settings.cloudModel == CloudModel.DEEPSEEK)
                                    CloudModel.MINIMAX
                                else
                                    CloudModel.DEEPSEEK
                                viewModel.setModel(next)
                            }
                            .padding(horizontal = OneUiSpacing.CardInner),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "模型",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "切换云端对话使用的模型",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = settings.cloudModel.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                    }
                    ListDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showApiKeyDialog = true }
                            .padding(horizontal = OneUiSpacing.CardInner),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "API Key",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = if (settings.cloudApiKey.isNotBlank()) "已设置" else "未设置",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.CardGap),
                        )
                    }
                }

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // ---- 主题 ----
                SectionLabel("外观")
                SettingsList {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showThemeDialog = true }
                            .padding(horizontal = OneUiSpacing.CardInner),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.CardGap),
                        )
                        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                        Text(
                            text = "主题",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when (settings.themeMode) {
                                ThemeMode.SYSTEM -> "跟随系统"
                                ThemeMode.LIGHT -> "浅色"
                                ThemeMode.DARK -> "深色"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                    }
                }

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // ---- 关于 ----
                SectionLabel("关于")
                SettingsList {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = OneUiSpacing.CardInner),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "版本",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = "v0.21.0-alpha",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    ListDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { NovaTap(view); viewModel.openIssues() }
                            .padding(horizontal = OneUiSpacing.CardInner),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
                        Text(
                            text = "反馈问题",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.CardGap),
                        )
                    }
                }

                Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
            }
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("设置 API Key") },
            text = {
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setApiKey(apiKeyInput.trim())
                        apiKeyInput = ""
                        showApiKeyDialog = false
                    },
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showApiKeyDialog = false }) { Text("取消") }
            },
        )
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("主题") },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "跟随系统",
                        ThemeMode.LIGHT to "浅色",
                        ThemeMode.DARK to "深色",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = OneUiSpacing.SectionTitleGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(OneUiSpacing.CardGap))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
        )
    }
}

// ============================================================
// 复用件
// ============================================================

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(
            start = OneUiSpacing.ScreenEdge,
            bottom = OneUiSpacing.SectionTitleGap,
        ),
    )
}

@Composable
private fun SettingsList(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.ScreenEdge)
            .clip(RoundedCornerShape(OneUiRadius.Large)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column { content() }
    }
}

@Composable
private fun ListDivider() {
    Box(
        modifier = Modifier
            .padding(start = OneUiSpacing.CardInner + OneUiSpacing.CardGap)
            .fillMaxWidth()
            .height(OneUiSpacing.SectionTitleGap / 5)
            .clip(RoundedCornerShape(OneUiSpacing.SectionTitleGap / 5)),
    )
}

@Composable
private fun ToggleListItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    onClick: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val dangerColor = NovaCareTheme.colors.riskRisky
    val subtitleColor = if (!checked) dangerColor else cs.onSurfaceVariant
    ListItem(
        modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
        leadingContent = { IconBox(icon) },
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = subtitleColor,
            )
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = onCheckedChange != null,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = cs.onPrimary,
                    checkedTrackColor = cs.primary,
                ),
            )
        },
        colors = ListItemDefaults.colors(containerColor = cs.surfaceContainer),
    )
}

@Composable
private fun ExpandableGuardianRow(
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(horizontal = OneUiSpacing.CardInner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.width(OneUiSpacing.CardInner + OneUiSpacing.CardGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "设备守护",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (expanded) "点击收起" else "点击展开 — 存储 / 内存 / 电池",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(OneUiSpacing.BlockGap - OneUiSpacing.CardGap)
                .then(
                    if (expanded) Modifier else Modifier,
                ),
        )
    }
}

@Composable
private fun GuardianSubItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = OneUiSpacing.CardInner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 缩进：让子项明显在父项之下
        Spacer(Modifier.width(OneUiSpacing.CardInner * 2 + OneUiSpacing.BlockGap / 2))
        IconBox(icon)
        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap + OneUiSpacing.CardGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.CardGap),
        )
    }
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(OneUiSpacing.CardInner * 2)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(OneUiSpacing.CardInner + OneUiSpacing.CardGap),
        )
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = OneUiSpacing.ScreenEdge,
                vertical = OneUiSpacing.SectionTitleGap,
            )
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable { onDismiss() }
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
