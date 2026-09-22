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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SdCard
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.CloudModel
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// SettingsScreen — 严格按 samsung.com/one-ui/comp/list.html 实测重写
//
// 模式：
//   - 顶部 OneUiAppBar（56dp + WindowInsets.statusBars）
//   - 一组 settings 是一个 squircle 浮卡（OneUiListSection）
//   - 每行是 OneUiListRow：圆形 tinted icon + 主标题 + 副文 + 右侧 switch/chevron
//   - subheader：13sp Medium 灰字，独立 padding 8/8
//   - 卡之间 gap 16dp
//   - 弹窗用真 M3 AlertDialog
// =========================================================================

@Composable
fun SettingsScreen(
    onBack: (() -> Unit)? = null,
    onOpenGuardian: ((String) -> Unit)? = null,
    onOpenOneClick: (() -> Unit)? = null,
    onOpenExport: (() -> Unit)? = null,
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
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                OneUiAppBar(title = "设置", onBack = onBack)
            }

            if (notice != null) {
                item {
                    NoticeCard(text = notice!!, onDismiss = { viewModel.consumeNotice() })
                    Spacer(Modifier.height(OneUiSpacing.CardInner))
                }
            }

            // === 引擎 / 权限 ===
            item { SectionLabel("引擎与权限") }
            item {
                OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                    OneUiListRow(
                        icon = Icons.Outlined.Build,
                        iconTint = Tints.engine,
                        title = "Nova 内核",
                        subtitle = if (engineAvailable) "已加载 · $engineVersion" else "未加载",
                        trailing = {
                            StatusDot(
                                text = if (engineAvailable) "就绪" else "离线",
                                color = if (engineAvailable) Tints.engine else MaterialTheme.colorScheme.error,
                            )
                        },
                    )
                    OneUiListRow(
                        icon = Icons.Outlined.Tune,
                        iconTint = Tints.shizuku,
                        title = "Shizuku",
                        subtitle = if (shizukuAvailable) "已授权" else "未授权 — 配置后可一键冻结",
                        onClick = { NovaTap(view); viewModel.requestShizuku() },
                        trailing = {
                            StatusDot(
                                text = if (shizukuAvailable) "就绪" else "去授权",
                                color = if (shizukuAvailable) Tints.shizuku else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                    capabilities.forEachIndexed { index, cap ->
                        OneUiListRow(
                            icon = when (cap.icon) {
                                SettingsViewModel.CapabilityIcon.USAGE -> Icons.Outlined.Info
                                SettingsViewModel.CapabilityIcon.FILES -> Icons.Outlined.Folder
                                SettingsViewModel.CapabilityIcon.NOTIFICATIONS -> Icons.Outlined.Notifications
                            },
                            iconTint = if (cap.granted) Tints.engine else MaterialTheme.colorScheme.error,
                            title = cap.title,
                            subtitle = if (cap.granted) cap.why else cap.consequence,
                            onClick = { NovaTap(view); viewModel.grant(cap.capability) },
                            trailing = {
                                StatusDot(
                                    text = if (cap.granted) "已授权" else "去开启",
                                    color = if (cap.granted) Tints.engine else MaterialTheme.colorScheme.primary,
                                )
                            },
                            showDivider = index < capabilities.lastIndex,
                        )
                    }
                }
            }

            // === 设备守护 ===
            if (onOpenGuardian != null) {
                item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
                item { SectionLabel("设备守护") }
                item {
                    OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                        OneUiListRow(
                            icon = Icons.Outlined.Storage,
                            iconTint = Tints.engine,
                            title = "存储守护",
                            subtitle = "大文件 / 重复文件 / 残留与空目录",
                            onClick = { NovaTap(view); onOpenGuardian(Routes.STORAGE) },
                            trailing = { ChevronRight() },
                        )
                        OneUiListRow(
                            icon = Icons.Outlined.Memory,
                            iconTint = Tints.shizuku,
                            title = "内存守护",
                            subtitle = "实时内存与进程占用排行",
                            onClick = { NovaTap(view); onOpenGuardian(Routes.MEMORY) },
                            showDivider = false,
                            trailing = { ChevronRight() },
                        )
                        OneUiListRow(
                            icon = Icons.Outlined.BatteryStd,
                            iconTint = Tints.engine,
                            title = "电池守护",
                            subtitle = "电量 / 温度 / 前台时长与省电建议",
                            onClick = { NovaTap(view); onOpenGuardian(Routes.BATTERY) },
                            showDivider = false,
                            trailing = { ChevronRight() },
                        )
                        OneUiListRow(
                            icon = Icons.Outlined.Speed,
                            iconTint = Tints.engine,
                            title = "一键体检",
                            subtitle = "5 步扫描 · 综合得分",
                            onClick = if (onOpenOneClick != null) {
                                { NovaTap(view); onOpenOneClick() }
                            } else null,
                            showDivider = false,
                            trailing = { ChevronRight() },
                        )
                    }
                }
            }

            // === 模式 ===
            item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
            item { SectionLabel("模式") }
            item {
                OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                    OneUiListRow(
                        icon = Icons.Outlined.Tune,
                        iconTint = Tints.engine,
                        title = "高级模式",
                        subtitle = if (settings.advancedMode) "已启用" else "默认走系统设置页引导",
                        onClick = { viewModel.setAdvanced(!settings.advancedMode) },
                        trailing = {
                            Switch(
                                checked = settings.advancedMode,
                                onCheckedChange = { viewModel.setAdvanced(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        },
                    )
                }
            }

            // === 外观 ===
            item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
            item { SectionLabel("外观") }
            item {
                OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                    OneUiListRow(
                        icon = Icons.Outlined.Tune,
                        iconTint = Tints.engine,
                        title = "主题",
                        subtitle = themeModeLabel(settings.themeMode),
                        onClick = { showThemeDialog = true },
                        trailing = { ChevronRight() },
                    )
                }
            }

            // === AI ===
            item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
            item { SectionLabel("AI") }
            item {
                OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                    OneUiListRow(
                        icon = Icons.Outlined.AutoAwesome,
                        iconTint = Tints.engine,
                        title = "云端对话",
                        subtitle = if (settings.cloudAiEnabled) "已开启 · ${settings.cloudModel.displayName}" else "未开启",
                        onClick = { viewModel.setCloudAi(!settings.cloudAiEnabled) },
                        trailing = {
                            Switch(
                                checked = settings.cloudAiEnabled,
                                onCheckedChange = { viewModel.setCloudAi(it) },
                            )
                        },
                    )
                    OneUiListRow(
                        icon = Icons.Outlined.Speed,
                        iconTint = Tints.shizuku,
                        title = "模型",
                        subtitle = "切换云端对话使用的模型",
                        onClick = {
                            val next = if (settings.cloudModel == CloudModel.DEEPSEEK) CloudModel.MINIMAX else CloudModel.DEEPSEEK
                            viewModel.setModel(next)
                        },
                        trailing = {
                            Text(
                                text = settings.cloudModel.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                            ChevronRight()
                        },
                    )
                    OneUiListRow(
                        icon = Icons.Outlined.Info,
                        iconTint = Tints.engine,
                        title = "API Key",
                        subtitle = if (settings.cloudApiKey.isNotBlank()) "已设置" else "未设置",
                        onClick = { showApiKeyDialog = true },
                        showDivider = false,
                        trailing = { ChevronRight() },
                    )
                }
            }

            // === 数据 ===
            if (onOpenExport != null) {
                item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
                item { SectionLabel("数据") }
                item {
                    OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                        OneUiListRow(
                            icon = Icons.Outlined.Folder,
                            iconTint = Tints.engine,
                            title = "导出 CSV",
                            subtitle = "清理 / 冻结历史 → Downloads/NovaCare.csv",
                            onClick = { NovaTap(view); onOpenExport() },
                            showDivider = false,
                            trailing = { ChevronRight() },
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(OneUiSpacing.BlockGap * 4)) }
        }
    }

    if (showApiKeyDialog) {
        AlertDialog(
            onDismissRequest = { showApiKeyDialog = false },
            title = { Text("设置 API Key") },
            text = {
                Column {
                    Text(
                        text = "API Key 写入 SettingsRepository（未进 Keystore）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardInner))
                    TextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        placeholder = { Text("sk-…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.setApiKey(apiKeyInput)
                    apiKeyInput = ""
                    showApiKeyDialog = false
                }) { Text("保存") }
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
                        com.novacare.core.data.ThemeMode.LIGHT,
                        com.novacare.core.data.ThemeMode.DARK,
                        com.novacare.core.data.ThemeMode.SYSTEM,
                    ).forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.setThemeMode(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = themeModeLabel(mode),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            if (settings.themeMode == mode) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("关闭") }
            },
        )
    }
}

private fun modeLabel(mode: String): String = when (mode) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

private fun themeModeLabel(mode: com.novacare.core.data.ThemeMode): String = when (mode) {
    com.novacare.core.data.ThemeMode.LIGHT -> "浅色"
    com.novacare.core.data.ThemeMode.DARK -> "深色"
    com.novacare.core.data.ThemeMode.SYSTEM -> "跟随系统"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = OneUiSpacing.BlockGap, end = OneUiSpacing.BlockGap, top = OneUiSpacing.CardInner, bottom = OneUiSpacing.SectionTitleGap),
    )
}

@Composable
private fun ChevronRight() {
    Icon(
        imageVector = Icons.Outlined.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun StatusDot(text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color = color, shape = RoundedCornerShape(4.dp)),
        )
        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeCard(text: String, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.BlockGap)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f), shape = RoundedCornerShape(OneUiRadius.Small))
            .padding(OneUiSpacing.CardInner),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = "关闭",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.clickable { onDismiss() }.padding(OneUiSpacing.SectionTitleGap),
        )
    }
}

// 一次性 icon tint —— 走主题，参考 Samsung OneUI 9 Setting 卡片配色
private object Tints {
    val engine = Color(0xFF2F6FED)        // OneUI 9 蓝（device care accent）
    val shizuku = Color(0xFF1B7A46)      // OneUI 9 绿（已就绪状态）
}

private object Routes {
    const val STORAGE = "guardian/storage"
    const val MEMORY = "guardian/memory"
    const val BATTERY = "guardian/battery"
}