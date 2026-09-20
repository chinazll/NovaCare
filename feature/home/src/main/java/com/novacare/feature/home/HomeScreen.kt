package com.novacare.feature.home

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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.model.DimensionScore
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.NovaCareColors
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页 —— 设备健康总览 + 快捷入口（OneUI 9.5/10 真实设计语言）。
 *
 * ============================================================
 * 【信息架构重做 · 首页与清理 tab 去重】
 *
 * 上一版首页 = 「清理功能的另一个入口」：Hero 显示「可清理 X GB」+ 一键释放，
 * 与「清理」tab 完全重复。用户点开首页和点开清理 tab 看到的是同一件事。
 *
 * 参照三星 Good Lock / Good Guardians / Sam Helper：
 *   - Good Lock：主界面只做「总览 + 导航」，具体功能下沉到模块；
 *   - Good Guardians：每个模块聚焦一个维度，诊断与一键优化分离；
 *   - Sam Helper：首页 = 硬件健康一眼看清 + 分类功能入口。
 *
 * 新结构（自上而下）：
 *   1. 顶条            内核就绪状态 + 时间，不抢戏
 *   2. 健康评分 Hero    大数字健康分 + verdict + 4 维度评分条
 *   3. 存储概览（只读）  已用/总量 + 进度条 + 剩余/应用数/不常用数
 *   4. 快捷入口          AI 助手主卡 + 清理/冻结/自动化三个模块入口
 *   5. 最近活动摘要      一行真实读数 + 建议
 *   6. 权限/引擎提示     仅在有问题时插一行（紧凑）
 *
 * 首页**绝不做**清理动作 —— 清理/冻结只在各自 tab 里执行。
 * ============================================================
 */
@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val score by viewModel.score.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val summary by viewModel.summary.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val engineVersion by viewModel.engineVersion.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current

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
            Spacer(Modifier.height(56.dp))

            // ---- 1. 顶条 ----
            TopStatusBar(
                engineAvailable = engineAvailable,
                engineVersion = engineVersion,
            )

            Spacer(Modifier.height(28.dp))

            // ---- 2. 健康评分 Hero ----
            HealthHero(score = score)

            Spacer(Modifier.height(24.dp))

            // ---- 3. 存储概览（只读） ----
            StorageOverview(overview = overview)

            Spacer(Modifier.height(24.dp))

            // ---- 4. 快捷入口 ----
            QuickEntries(
                onNavigate = { route ->
                    NovaTap(view)
                    onNavigate(route)
                },
            )

            // ---- 5. 最近活动摘要 ----
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(20.dp))
                SummaryRow(summary = summary)
            }

            // ---- 6. 引擎 / 权限提示（仅在有问题时显示） ----
            if (!engineAvailable || missing.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                if (!engineAvailable) {
                    EngineWarning { viewModel.refreshCapabilities() }
                }
                missing.forEach { capability ->
                    Spacer(Modifier.height(8.dp))
                    PermissionRow(
                        capability = capability,
                        onGrant = {
                            NovaTap(view)
                            viewModel.grant(capability)
                        },
                    )
                }
            }

            Spacer(Modifier.height(160.dp))
        }
    }
}

// ============================================================
// 顶条（系统状态细字）
// ============================================================
@Composable
private fun TopStatusBar(
    engineAvailable: Boolean,
    engineVersion: String,
) {
    val colors = NovaCareTheme.colors
    val timeText = remember {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(
                    if (engineAvailable) colors.healthGood
                    else colors.riskCaution,
                ),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                !engineAvailable -> "内核不可用"
                engineVersion.isBlank() -> "内核初始化"
                else -> "内核就绪"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ============================================================
// 健康评分 Hero —— 单一主张：设备现在有多健康
// ============================================================
@Composable
private fun HealthHero(score: HealthScore?) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    Column {
        Text(
            text = "设备健康",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = score?.total?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.W200),
                color = score?.let { scoreColor(it.total, colors) } ?: cs.onSurface,
                maxLines = 1,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = score?.verdict ?: "正在评估",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        if (score != null) {
            Spacer(Modifier.height(16.dp))
            score.dimensions.forEach { dim ->
                DimensionRow(dim)
            }
        }
    }
}

@Composable
private fun DimensionRow(dim: DimensionScore) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val fraction = (dim.score.coerceIn(0, 100)) / 100f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dim.dimension.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(cs.onSurface.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(scoreColor(dim.score, colors)),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text = "${dim.score}",
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
            color = cs.onSurface,
            modifier = Modifier.width(24.dp),
        )
    }
}

private fun scoreColor(total: Int, colors: NovaCareColors): androidx.compose.ui.graphics.Color = when {
    total >= 85 -> colors.healthGood
    total >= 60 -> colors.riskCaution
    else -> colors.riskRisky
}

// ============================================================
// 存储概览（只读，无任何清理动作）
// ============================================================
@Composable
private fun StorageOverview(overview: HomeViewModel.Overview?) {
    val cs = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "存储空间",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )
            if (overview == null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "正在读取…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                return@Column
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "已用 ${overview.usedBytes.formatBytes()} / ${overview.totalBytes.formatBytes()}",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            val fraction = if (overview.totalBytes > 0L) {
                (overview.usedBytes.toFloat() / overview.totalBytes).coerceIn(0f, 1f)
            } else 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.onSurface.copy(alpha = 0.08f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(cs.primary),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                StatItem("剩余", overview.freeBytes.formatBytes(), Modifier.weight(1f))
                StatItem("应用", "${overview.appCount} 个", Modifier.weight(1f))
                StatItem("不常用", "${overview.staleAppCount} 个", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.W600),
            color = cs.onSurface,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

// ============================================================
// 快捷入口 —— AI 助手主卡 + 三个模块入口
// ============================================================
@Composable
private fun QuickEntries(onNavigate: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme

    // AI 助手主卡：首页唯一「突出」的入口
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        color = cs.primary,
        contentColor = cs.onPrimary,
        onClick = { onNavigate("assistant") },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Star,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "问问 AI 助手",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "读设备状态 · 给建议 · 帮你动手",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onPrimary.copy(alpha = 0.85f),
                )
            }
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }

    Spacer(Modifier.height(12.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        EntryCard(
            title = "清理",
            icon = Icons.Outlined.CleaningServices,
            onClick = { onNavigate("clean") },
            modifier = Modifier.weight(1f),
        )
        EntryCard(
            title = "冻结",
            icon = Icons.Outlined.AcUnit,
            onClick = { onNavigate("freeze") },
            modifier = Modifier.weight(1f),
        )
        EntryCard(
            title = "自动化",
            icon = Icons.Outlined.Speed,
            onClick = { onNavigate("automation") },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun EntryCard(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp)),
        color = cs.surfaceContainer,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W500),
                color = cs.onSurface,
            )
        }
    }
}

// ============================================================
// 最近活动摘要（一行真实读数 + 建议）
// ============================================================
@Composable
private fun SummaryRow(summary: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

// ============================================================
// 引擎 / 权限提示
// ============================================================
@Composable
private fun EngineWarning(onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.riskCaution.copy(alpha = 0.08f))
            .clickable { onRetry() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "内核不可用，仅显示真实读数",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.riskCaution,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "重试",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = colors.riskCaution,
        )
    }
}

@Composable
private fun PermissionRow(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "识别长期未用应用"
        MissingCapability.ALL_FILES ->
            "所有文件访问" to "完整扫描存储"
        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "长时间任务展示进度"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(cs.onSurface.copy(alpha = 0.04f))
            .clickable { onGrant() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = "开启",
            style = MaterialTheme.typography.labelMedium,
            color = cs.primary,
        )
    }
}

// (end of file)
