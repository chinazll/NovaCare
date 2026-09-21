package com.novacare.feature.traffic

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
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SwapVert
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
import com.novacare.core.system.TrafficSource
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 流量屏幕
 *
 * 单一主张：自设备启动以来的累计上下行字节数 + per-UID top 10 + per-interface 汇总。
 *
 * 诚实底线：
 *   - 所有数字都来自 TrafficStats / /proc/net/dev 实测，没有"估算"
 *   - 设备重启后归零 —— 在标题与说明里如实标注
 *   - "本应用"一行独立展示，便于对比"我自己 vs 别人"（不假装能监控所有包）
 */
@Composable
fun TrafficScreen(
    modifier: Modifier = Modifier,
    viewModel: TrafficViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "流量",
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
                TrafficViewModel.UiState.Loading -> LoadingState()
                TrafficViewModel.UiState.Empty -> EmptyState()
                is TrafficViewModel.UiState.Failed -> FailedState(
                    message = s.message,
                    onRetry = {
                        NovaTap(view)
                        viewModel.refresh()
                    },
                )
                is TrafficViewModel.UiState.Ready -> ReadyState(
                    summary = s.summary,
                    self = s.self,
                )
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
private fun EmptyState() {
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
                        imageVector = Icons.Outlined.NetworkCheck,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text(
                        text = "自设备启动以来尚无网络流量",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = "TrafficStats 是设备级累计值，开机后归零。" +
                        "若设备刚开机、或飞行模式后未联网，会显示为 0 —— 这是真实状态，不是 bug。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
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
                        text = "读取流量失败",
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
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun ReadyState(summary: TrafficSource.Summary, self: TrafficSource.UidTraffic) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.CardGap,
        ),
    ) {
        item {
            TotalCard(summary = summary)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        if (summary.interfaces.isNotEmpty()) {
            item {
                Text(
                    text = "按网络接口",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = OneUiSpacing.SectionTitleGap),
                )
            }
            item {
                InterfacesCard(summary.interfaces)
                Spacer(Modifier.height(OneUiSpacing.BlockGap))
            }
        }

        item {
            Text(
                text = "本应用（NovaCare）",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(vertical = OneUiSpacing.SectionTitleGap),
            )
        }
        item {
            SelfCard(self = self)
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        if (summary.topApps.isNotEmpty()) {
            item {
                Text(
                    text = "应用排行 · TOP ${summary.topApps.size}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = OneUiSpacing.SectionTitleGap),
                )
                Text(
                    text = "只有系统愿意给本进程读 UID 流量的应用才会出现。" +
                        "其他应用按 -1 拒绝，已一律过滤 —— 我们不编「别人的流量排行」。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
            itemsIndexed(summary.topApps, key = { _, it -> it.uid }) { index, app ->
                UidRow(rank = index + 1, app = app)
                if (index < summary.topApps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(cs.onSurface.copy(alpha = 0.06f)),
                    )
                }
            }
        }

        item { Spacer(Modifier.height(OneUiSpacing.EmptyHeight)) }
    }
}

@Composable
private fun TotalCard(summary: TrafficSource.Summary) {
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
                    imageVector = Icons.Outlined.SwapVert,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "自设备启动累计",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = summary.totalBytes.formatBytes(),
                style = MaterialTheme.typography.displaySmall,
                color = colors.healthGood,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                ByteStat(
                    label = "下载",
                    bytes = summary.totalRxBytes,
                    color = colors.healthGood,
                    modifier = Modifier.weight(1f),
                )
                ByteStat(
                    label = "上传",
                    bytes = summary.totalTxBytes,
                    color = cs.primary,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = "设备重启后归零 —— 跨重启做「每日用量」需要系统级日志，" +
                    "Android 不会把这个开放给第三方。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ByteStat(
    label: String,
    bytes: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Text(
            text = bytes.formatBytes(),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
            color = color,
        )
    }
}

@Composable
private fun InterfacesCard(interfaces: List<TrafficSource.InterfaceTraffic>) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            interfaces.forEachIndexed { idx, iface ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = OneUiSpacing.CardGap),
                ) {
                    Text(
                        text = iface.name,
                        style = MaterialTheme.typography.bodyLarge,
                        color = cs.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "↓ ${iface.rxBytes.formatBytes()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurface,
                        )
                        Text(
                            text = "↑ ${iface.txBytes.formatBytes()}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }
                if (idx < interfaces.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(cs.onSurface.copy(alpha = 0.06f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelfCard(self: TrafficSource.UidTraffic) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Text(text = self.label, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            Spacer(Modifier.height(4.dp))
            Text(text = self.packageName, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                ByteStat("下载", self.rxBytes, cs.primary, modifier = Modifier.weight(1f))
                ByteStat("上传", self.txBytes, cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UidRow(rank: Int, app: TrafficSource.UidTraffic) {
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
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
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
                text = "↓ ${app.rxBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Text(
                text = "↑ ${app.txBytes.formatBytes()}",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}
