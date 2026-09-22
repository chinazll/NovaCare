package com.novacare.feature.appdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App 详情屏（v0.21.0 新增）
 *
 * 真实展示：
 *   - 包名 / 大小 / 安装时间 / 最后使用 / 缓存大小 / 数据大小 / 权限数
 *   - 三个动作按钮：
 *     ① 清除缓存 —— 本应用自身走 PackageManager.deleteCache()；其他应用跳系统页
 *     ② 停用 —— PackageManager.setApplicationEnabledSetting（公开 API）
 *     ③ 卸载 —— Intent.ACTION_DELETE（必须由用户在系统页确认）
 *
 * 每个按钮都接一个**真实**动作，且诚实告知"能做什么 / 不能做什么"。
 *
 * UI 单一主张：本应用的全部元信息与操作入口。零伪造、零占位、零假按钮。
 */
@Composable
fun AppDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AppDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val notice by viewModel.actionNotice.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "应用详情",
                onBack = {
                    NovaTap(view)
                    onBack()
                },
                actions = {
                    TextButton(onClick = {
                        NovaTap(view)
                        viewModel.load()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("刷新", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            when (val s = state) {
                AppDetailViewModel.UiState.Loading -> LoadingState()
                is AppDetailViewModel.UiState.Failed -> FailedState(
                    message = s.message,
                    onOpenSystemPage = {
                        NovaTap(view)
                        viewModel.openAppDetailsSettings()
                    },
                )
                is AppDetailViewModel.UiState.Ready -> ReadyState(
                    detail = s.detail,
                    onClearCache = { NovaTap(view); viewModel.clearCache() },
                    onDisable = { NovaTap(view); viewModel.disableApp() },
                    onUninstall = { NovaTap(view); viewModel.uninstallApp() },
                    onOpenSystemPage = { NovaTap(view); viewModel.openAppDetailsSettings() },
                )
            }
        }
    }

    if (notice != null) {
        AlertDialog(
            onDismissRequest = { viewModel.consumeActionNotice() },
            title = { Text("操作提示") },
            text = { Text(notice ?: "") },
            confirmButton = {
                TextButton(onClick = { viewModel.consumeActionNotice() }) {
                    Text("知道了")
                }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun FailedState(message: String, onOpenSystemPage: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(OneUiRadius.Large),
            color = cs.errorContainer.copy(alpha = 0.4f),
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = cs.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "读取应用详情失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "常见原因：包已被卸载 / 数据不可见。\n" +
                        "可以尝试打开系统应用页查看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                TextButton(
                    onClick = onOpenSystemPage,
                    contentPadding = PaddingValues(horizontal = 0.dp),
                ) { Text("打开系统应用页") }
            }
        }
    }
}

@Composable
private fun ReadyState(
    detail: AppDetailViewModel.AppDetail,
    onClearCache: () -> Unit,
    onDisable: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSystemPage: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.CardGap,
        ),
    ) {
        item {
            HeaderCard(detail)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            SectionLabel("占用明细")
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            StorageCard(detail)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            SectionLabel("时间线")
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            TimelineCard(detail)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            SectionLabel("系统属性")
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            SystemCard(detail)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            SectionLabel("操作")
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            ActionsCard(
                detail = detail,
                onClearCache = onClearCache,
                onDisable = onDisable,
                onUninstall = onUninstall,
                onOpenSystemPage = onOpenSystemPage,
            )
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        item {
            SourceFootnote(detail.sourceLabel)
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun HeaderCard(detail: AppDetailViewModel.AppDetail) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(
                text = detail.label,
                style = MaterialTheme.typography.headlineMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = detail.packageName,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail.isSystem) {
                    TagPill(text = "系统应用", tone = cs.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                }
                if (!detail.isEnabled) {
                    TagPill(text = "已停用", tone = colors.riskCaution)
                } else {
                    TagPill(text = "已启用", tone = colors.healthGood)
                }
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = "版本 ${detail.versionName}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StorageCard(detail: AppDetailViewModel.AppDetail) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            MetricRow(label = "安装包", value = formatBytesOrDash(detail.apkBytes))
            Divider1px()
            MetricRow(label = "数据", value = formatBytesOrDash(detail.dataBytes))
            Divider1px()
            MetricRow(label = "缓存", value = formatBytesOrDash(detail.cacheBytes))
            Divider1px()
            MetricRow(
                label = "总计",
                value = formatBytesOrDash(detail.totalBytes),
                emphasized = true,
            )
            if (detail.apkBytes <= 0L && detail.cacheBytes <= 0L && detail.dataBytes <= 0L) {
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "三项均为 0 通常意味着 StorageStatsManager 不可用（API < 26 或权限不足）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun TimelineCard(detail: AppDetailViewModel.AppDetail) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            MetricRow(label = "安装时间", value = formatDateOrDash(detail.installTimeEpochMs))
            Divider1px()
            MetricRow(label = "最后更新", value = formatDateOrDash(detail.updateTimeEpochMs))
            Divider1px()
            MetricRow(
                label = "最后使用",
                value = if (detail.lastUsedEpochMs != null && detail.lastUsedEpochMs > 0L) {
                    formatDateOrDash(detail.lastUsedEpochMs)
                } else {
                    "未授予「使用情况访问」"
                },
            )
        }
    }
}

@Composable
private fun SystemCard(detail: AppDetailViewModel.AppDetail) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            MetricRow(
                label = "targetSdk",
                value = if (detail.targetSdk > 0) detail.targetSdk.toString() else "—",
            )
            Divider1px()
            MetricRow(label = "权限数", value = detail.permissionCount.toString())
            Divider1px()
            MetricRow(
                label = "类型",
                value = if (detail.isSystem) "系统应用" else "用户应用",
            )
        }
    }
}

@Composable
private fun ActionsCard(
    detail: AppDetailViewModel.AppDetail,
    onClearCache: () -> Unit,
    onDisable: () -> Unit,
    onUninstall: () -> Unit,
    onOpenSystemPage: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            ActionRow(
                icon = Icons.Outlined.CleaningServices,
                title = "清除缓存",
                subtitle = if (detail.isSystem) {
                    "系统应用通常无法清理（沙箱保护）"
                } else {
                    "只能清理本应用自己的 cache 目录（其他应用跳系统页）"
                },
                enabled = true,
                onClick = onClearCache,
            )
            Divider1px()
            ActionRow(
                icon = Icons.Outlined.AcUnit,
                title = "停用",
                subtitle = "公开 API，可部分生效；系统应用 / 关键组件可能被 ROM 拒绝",
                enabled = !detail.isSystem,
                onClick = onDisable,
            )
            Divider1px()
            ActionRow(
                icon = Icons.Outlined.DeleteOutline,
                title = "卸载",
                subtitle = "跳系统确认页 —— 必须由你点确认，第三方无法静默卸载",
                enabled = !detail.isSystem,
                onClick = onUninstall,
            )
            Divider1px()
            ActionRow(
                icon = Icons.Outlined.Settings,
                title = "系统应用页",
                subtitle = "打开「应用信息」页，由你执行任何更精细的操作",
                enabled = true,
                onClick = onOpenSystemPage,
            )
        }
    }
}

@Composable
private fun SourceFootnote(text: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier
                .size(14.dp)
                .padding(top = 2.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun MetricRow(label: String, value: String, emphasized: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = if (emphasized) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = cs.onSurface,
        )
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Small))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.size(20.dp),
        )
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
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TagPill(text: String, tone: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .background(tone.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = tone,
        )
    }
}

@Composable
private fun Divider1px() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
    )
}

private fun formatBytesOrDash(bytes: Long): String =
    if (bytes > 0L) bytes.formatBytes() else "—"

private fun formatDateOrDash(epochMs: Long): String {
    if (epochMs <= 0L) return "—"
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return sdf.format(Date(epochMs))
}

// (clickable 直接用 androidx.compose.foundation.clickable Modifier 扩展)
