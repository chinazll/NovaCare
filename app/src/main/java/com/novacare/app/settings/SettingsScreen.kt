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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 设置页 —— OneUI 9.5 真实设计语言。
 *
 * 列表 + 分组，每行 = 图标 + 标题 + 副标 + 右侧（toggle / 版本 / chevron）。
 * 不再用 "卡片包一切"，回归 OneUI 经典的列表布局。
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
        ) {
            OneUiAppBar(title = "设置")

            Spacer(Modifier.height(24.dp))

            if (notice != null) {
                NoticeCard(text = notice!!, onDismiss = { viewModel.consumeNotice() })
                Spacer(Modifier.height(16.dp))
            }

            // ---- 引擎 / 权限 ----
            SectionLabel("引擎与权限")
            SettingsList {
                ToggleRow(
                    icon = Icons.Outlined.Memory,
                    title = "Nova 内核",
                    subtitle = if (engineAvailable) "已加载 · $engineVersion" else "未加载（Rust .so 缺失或 ABI 不匹配）",
                    checked = engineAvailable,
                    enabled = false,
                    onChange = {},
                )
                HorizontalDivider()
                ToggleRow(
                    icon = Icons.Outlined.AcUnit,
                    title = "Shizuku",
                    subtitle = if (shizukuAvailable) "已授权，可一键冻结 / 高级清理" else "未授权 — 配置后可一键冻结",
                    checked = shizukuAvailable,
                    // Switch 是状态指示器，不是开关 —— 真要授权请点整行。
                    onChange = null,
                    onClickAction = { NovaTap(view); viewModel.requestShizuku() },
                )
                HorizontalDivider()
                capabilities.forEachIndexed { index, cap ->
                    ToggleRow(
                        icon = when (cap.icon) {
                            SettingsViewModel.CapabilityIcon.USAGE -> Icons.Outlined.Info
                            SettingsViewModel.CapabilityIcon.FILES -> Icons.Outlined.Folder
                            SettingsViewModel.CapabilityIcon.NOTIFICATIONS -> Icons.Outlined.Notifications
                        },
                        title = cap.title,
                        subtitle = if (cap.granted) cap.why else cap.consequence,
                        checked = cap.granted,
                        // Switch 是状态指示器 —— 真要授权请点整行；空 onCheckedChange
                        // 会让 Switch 显示 checked 状态但不响应点击（避免「按了没反应」的错觉）。
                        onChange = null,
                        onClickAction = { NovaTap(view); viewModel.grant(cap.capability) },
                    )
                    if (index < capabilities.lastIndex) HorizontalDivider()
                }
            }

            // ---- 设备守护 ----
            // 三个模块不进底栏（NovaDock 固定宽度胶囊放不下第 6 个 tab），
            // 统一从这里进入。每项都写清"它管什么"，避免用户点进去发现不是自己要的。
            if (onOpenGuardian != null) {
                Spacer(Modifier.height(24.dp))
                SectionLabel("设备守护")
                SettingsList {
                    NavRow(
                        icon = Icons.Outlined.SdCard,
                        title = "存储守护",
                        subtitle = "大文件 / 重复文件 / 残留与空目录",
                        onClick = { NovaTap(view); onOpenGuardian(Routes.STORAGE) },
                    )
                    HorizontalDivider()
                    NavRow(
                        icon = Icons.Outlined.Memory,
                        title = "内存守护",
                        subtitle = "实时内存与进程占用排行",
                        onClick = { NovaTap(view); onOpenGuardian(Routes.MEMORY) },
                    )
                    HorizontalDivider()
                    NavRow(
                        icon = Icons.Outlined.BatteryFull,
                        title = "电池守护",
                        subtitle = "电量 / 温度 / 前台时长与省电建议",
                        onClick = { NovaTap(view); onOpenGuardian(Routes.BATTERY) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 模式 ----
            SectionLabel("模式")
            SettingsList {
                ToggleRow(
                    icon = Icons.Outlined.Memory,
                    title = "高级模式",
                    subtitle = if (settings.advancedMode)
                        "已启用 · 一键清理所有应用缓存（pm trim-caches）"
                    else
                        "未启用 · 默认走系统设置页引导",
                    checked = settings.advancedMode,
                    onChange = { viewModel.setAdvanced(it) },
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- AI ----
            SectionLabel("AI")
            SettingsList {
                ToggleRow(
                    icon = Icons.Outlined.SmartToy,
                    title = "云端对话",
                    subtitle = if (settings.cloudAiEnabled)
                        "已开启 · 模型 ${settings.cloudModel.displayName}"
                    else
                        "未开启 · 助手只能识别固定问法",
                    checked = settings.cloudAiEnabled,
                    onChange = { viewModel.setCloudAi(it) },
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable {
                            val next = if (settings.cloudModel == CloudModel.DEEPSEEK)
                                CloudModel.MINIMAX
                            else
                                CloudModel.DEEPSEEK
                            viewModel.setModel(next)
                        }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "模型",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = settings.cloudModel.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showApiKeyDialog = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "API Key",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (settings.cloudApiKey.isNotBlank()) "已设置" else "未设置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 主题 ----
            SectionLabel("外观")
            SettingsList {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { showThemeDialog = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBox(icon = Icons.Outlined.Palette)
                    Spacer(Modifier.width(12.dp))
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
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 关于 ----
            SectionLabel("关于")
            SettingsList {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "版本",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "0.9.0-alpha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clickable { NovaTap(view); viewModel.openIssues() }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
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
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(Modifier.height(160.dp))
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
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
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
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SettingsList(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(content = content)
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    /**
     * 真正的"开关"行为回调。
     * 为 null 时 Switch 会以只读方式显示 [checked] 状态，但不响应点击
     * （Material 3 Switch 接受可空的 onCheckedChange，传入 null 即非交互）。
     * 这种情况通常配合 [onClickAction] 用 —— Switch 只展示状态，整行点击才真正动作。
     */
    onChange: ((Boolean) -> Unit)? = null,
    onClickAction: (() -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val dangerColor = colors.riskRisky

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .let { if (onClickAction != null) it.clickable { onClickAction() } else it }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) cs.onSurface else cs.onSurface.copy(alpha = 0.5f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (!checked && enabled) dangerColor else cs.onSurfaceVariant,
                maxLines = 2,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = cs.onPrimary,
                checkedTrackColor = cs.primary,
            ),
        )
    }
}

@Composable
private fun NavRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBox(icon = icon)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun IconBox(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun HorizontalDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.primary.copy(alpha = 0.08f))
            .clickable { onDismiss() }
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

// (end of file)
