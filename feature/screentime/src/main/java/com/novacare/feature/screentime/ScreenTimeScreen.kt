package com.novacare.feature.screentime

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Refresh
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
import com.novacare.core.system.ScreenTimeSource
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 屏幕时长屏幕
 *
 * 单一主张：本应用最近 24h 的前台使用时长 top 10。其它一切都在别处。
 *
 * 数据真实原则：
 *   - 已授权 → 显示真实数字（"前台 1h 23m" + top 10 列表）
 *   - 未授权 → 一键引导到系统设置页
 *   - ROM 屏蔽 / 异常 → 明说"系统未提供事件"，不编数据
 */
@Composable
fun ScreenTimeScreen(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScreenTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "屏幕时长",
                actions = {
                    TextButton(onClick = {
                        NovaTap(view)
                        viewModel.refresh()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("刷新", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            when (val s = state) {
                ScreenTimeViewModel.UiState.Loading -> LoadingState()
                ScreenTimeViewModel.UiState.NeedsPermission -> PermissionState(onGrant = {
                    NovaTap(view)
                    onGrant()
                })
                is ScreenTimeViewModel.UiState.Failed -> FailedState(
                    message = s.message,
                    onRetry = {
                        NovaTap(view)
                        viewModel.refresh()
                    },
                )
                is ScreenTimeViewModel.UiState.Ready -> ReadyState(summary = s.summary)
            }
        }
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
private fun PermissionState(onGrant: () -> Unit) {
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
            color = cs.surfaceContainer,
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "需要「使用情况访问」权限",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = "Android 把「统计应用前台时长」的接口放在系统设置里，没有运行时弹窗。\n\n" +
                        "点击下方按钮跳到设置页，找到 NovaCare 并打开「允许使用情况访问」。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                TextButton(onClick = onGrant, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("去系统设置开启")
                }
            }
        }
    }
}

@Composable
private fun FailedState(message: String, onRetry: () -> Unit) {
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
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "系统未提供数据",
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
                    text = "部分 ROM 屏蔽了 UsageStatsManager 的事件流，" +
                        "这是系统限制，不是 App 问题。返回首页前可重试一次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun ReadyState(summary: ScreenTimeSource.Summary) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val isEmpty = summary.appCount == 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.CardGap,
        ),
    ) {
        item {
            TotalCard(
                totalForegroundMs = summary.totalForegroundMs,
                appCount = summary.appCount,
                windowStartEpochMs = summary.windowStartEpochMs,
                windowEndEpochMs = summary.windowEndEpochMs,
            )
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        if (isEmpty) {
            item {
                EmptyNotice()
            }
            return@LazyColumn
        }

        item {
            Text(
                text = "应用排行 · TOP ${summary.appCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = OneUiSpacing.SectionTitleGap),
            )
        }

        itemsIndexed(summary.apps, key = { _, it -> it.packageName }) { index, app ->
            AppRow(rank = index + 1, app = app)
            if (index < summary.apps.lastIndex) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(cs.onSurface.copy(alpha = 0.06f)),
                )
            }
        }

        item { Spacer(Modifier.height(OneUiSpacing.EmptyHeight)) }
    }
}

@Composable
private fun TotalCard(
    totalForegroundMs: Long,
    appCount: Int,
    windowStartEpochMs: Long,
    windowEndEpochMs: Long,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.BarChart,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "最近 24 小时 · 前台时长",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = formatDuration(totalForegroundMs),
                style = MaterialTheme.typography.displaySmall,
                color = colors.healthGood,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "覆盖 $appCount 个应用 · 窗口 ${formatRange(windowStartEpochMs, windowEndEpochMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = "数据来源 UsageStatsManager.queryEvents —— 真实前台时间戳，" +
                    "不估算、不轮询第三方包。设备刚启动 / 长时间未解锁会显示空，" +
                    "那是真的没数据，不是 bug。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AppRow(rank: Int, app: ScreenTimeSource.AppUsage) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OneUiSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(OneUiRadius.Small))
                .background(cs.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "$rank",
                style = MaterialTheme.typography.labelMedium,
                color = cs.primary,
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatDuration(app.foregroundMs),
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
            Text(
                text = if (app.lastForegroundEpochMs > 0L) {
                    "最近 ${formatRelative(app.lastForegroundEpochMs)}"
                } else {
                    "无最近前台"
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyNotice() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(
                text = "暂无前台事件",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "已开启 Usage Access，但系统未返回任何 24h 内的事件。" +
                    "可能是刚开机不到 1 分钟，或设备长期锁屏未解锁。",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

// ============================================================
// 格式化工具（屏幕时长专用）
// ============================================================

/** "1h 23m" / "23m" / "12s" —— 中文场景下不要写 1:23:45 那种不直观的格式 */
internal fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "0s"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return when {
        hours > 0L -> "${hours}h ${minutes}m"
        minutes > 0L -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

/** "12:00 – 13:00"（按 HH:mm）—— 避免显示绝对时间戳让用户重新心算 */
internal fun formatRange(startMs: Long, endMs: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    return "${sdf.format(java.util.Date(startMs))} – ${sdf.format(java.util.Date(endMs))}"
}

/** "刚刚 / N 分钟前 / N 小时前 / 昨天 HH:mm" —— 短描述 */
internal fun formatRelative(epochMs: Long): String {
    val deltaMs = (System.currentTimeMillis() - epochMs).coerceAtLeast(0L)
    val mins = deltaMs / 60_000L
    return when {
        mins < 1L -> "刚刚"
        mins < 60L -> "${mins} 分钟前"
        mins < 24L * 60L -> "${mins / 60L} 小时前"
        else -> {
            val sdf = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.US)
            sdf.format(java.util.Date(epochMs))
        }
    }
}
