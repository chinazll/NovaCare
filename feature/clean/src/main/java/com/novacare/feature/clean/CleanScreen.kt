package com.novacare.feature.clean

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderDelete
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.novacare.core.common.formatBytes
import com.novacare.core.domain.key
import com.novacare.core.model.CleanAdvice
import com.novacare.core.model.CleanRisk
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.KeyValueRow
import com.novacare.ui.designsystem.MiniRing
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaNowBar
import com.novacare.ui.designsystem.NovaProgressBar
import com.novacare.ui.designsystem.NowBarStatus
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RiskChip
import com.novacare.ui.designsystem.RingSpinner
import com.novacare.ui.designsystem.SecondaryAction
import com.novacare.ui.designsystem.SectionHeader
import com.novacare.ui.designsystem.SpatialLayer
import com.novacare.ui.designsystem.StaggerFlyIn
import com.novacare.ui.designsystem.StatCard

/**
 * 清理页（L2）。
 *
 * 【v0.7.2 视觉统一】与 HomeScreen 同一设计语言：
 *   - 顶部 NovaNowBar（替代老 PageHeader）
 *   - 所有顶层 item 包 StaggerFlyIn（替代整页 AnimatedVisibility 单组入场）
 *   - OverviewCard 用 SpatialLayer（替代普通 Surface）
 *   - 圆角统一 22dp；不再用 18/22/26 混用
 *   - LazyColumn 上下 padding：top 10dp / bottom 24dp（适配 NowBar 已有 padding）
 *
 * 布局节奏：
 *   1. 标题栏 —— 页面名 + 扫描耗时（真实读数，不是装饰）
 *   2. 状态区 —— 空闲入口 / 扫描中 / 失败，三态各有一套完整表达
 *   3. 概览区 —— 一个 MiniRing（可清理占比）+ 三张 StatCard（安全项 / 需确认 / 单项最大）
 *   4. 存储构成 —— 内核给出的真实分类，用 NovaProgressBar + KeyValueRow 逐条列出
 *   5. 提醒区 —— 引擎降级 / 缺授权，每一项都带可执行按钮，绝不静默
 *   6. 清单区 —— 按「风险等级」分组（安全 / 需确认 / 有风险），每组带全选与合计
 *   7. 执行区 —— 唯一的强调色 CTA，副标题给出「已选 N 项 · 预计释放 X」
 *   8. 结果区 —— 成功 / 失败 / 需手动 三类严格区分，不把"引导用户去设置页"谎报成"已释放"
 *
 * 关键设计决策：**分组维度用风险而不是目录**。
 * 用户做决定时问的不是"这堆文件在哪个目录"，而是"删了会不会出事"。
 */
@Composable
fun CleanScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: CleanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val includeRisky by viewModel.includeRisky.collectAsStateWithLifecycle()
    val moveToRecycleBin by viewModel.moveToRecycleBin.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()

    // 首次进入：先拿引擎可用性（不必等扫描），再从缓存/系统扫描出清单
    LaunchedEffect(rootPath) {
        viewModel.refreshEngineState()
        viewModel.scanNow(rootPath)
    }

    // 从系统设置页返回时重新检查引擎与权限状态
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshEngineState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AuroraBackground {
        // v0.7.2 视觉统一：移除 AnimatedVisibility 单组入场，
        // 改为每个子屏的 item 用 StaggerFlyIn 错峰飞入。
        when (val s = state) {
            CleanViewModel.UiState.Idle -> IdleScreen(
                modifier = modifier,
                engineAvailable = engineAvailable,
                onScan = { viewModel.scanNow(rootPath, force = true) },
            )

            CleanViewModel.UiState.Scanning -> ScanningScreen(modifier = modifier)

            is CleanViewModel.UiState.Failed -> FailedScreen(
                modifier = modifier,
                message = s.message,
                onRetry = { viewModel.scanNow(rootPath, force = true) },
            )

            is CleanViewModel.UiState.Executing -> ExecutingScreen(modifier = modifier)

            is CleanViewModel.UiState.Done -> DoneScreen(
                modifier = modifier,
                state = s,
                onRescan = { viewModel.rescan(rootPath) },
            )

            is CleanViewModel.UiState.Results -> ResultsScreen(
                modifier = modifier,
                state = s,
                selected = selected,
                includeRisky = includeRisky,
                moveToRecycleBin = moveToRecycleBin,
                onToggle = viewModel::toggle,
                onSelectAll = viewModel::selectAll,
                onIncludeRiskyChange = viewModel::setIncludeRisky,
                onMoveToRecycleBinChange = viewModel::setMoveToRecycleBin,
                onExecute = viewModel::execute,
                onRescan = { viewModel.scanNow(rootPath, force = true) },
                onGrant = viewModel::grant,
            )
        }
    }
}

// ============================================================
// 1. 空闲态：扫描入口
// ============================================================

@Composable
private fun IdleScreen(
    modifier: Modifier,
    engineAvailable: Boolean,
    onScan: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = if (engineAvailable) {
                        "内核就绪 · 未扫描"
                    } else {
                        "内核不可用 · 仅显示系统真实读数"
                    },
                    status = if (engineAvailable) {
                        NowBarStatus.Idle
                    } else {
                        NowBarStatus.Error
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "hero") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SpatialLayer(corner = 22.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            MiniRing(
                                value = 0f,
                                label = "待扫描",
                                centerText = "—",
                                color = NovaCareTheme.colors.accent,
                                diameter = 104.dp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "还没有这台设备的扫描结果",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "扫描只在本机进行。得到的每一项都会标注它占多大、删了有什么代价、" +
                                    "风险几级 —— 默认只勾选确定安全的那些。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        if (!engineAvailable) {
            item(key = "engine-notice") {
                StaggerFlyIn(index = 2) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "扫描内核不可用：本页只能基于系统文件接口给出结果，覆盖面会明显小于完整扫描。",
                            tone = EmptyTone.Warning,
                            actionText = "重试连接",
                            onAction = onScan,
                        )
                    }
                }
            }
        }

        item(key = "cta") {
            StaggerFlyIn(index = 3) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(
                        text = "开始扫描",
                        subtitle = "只读扫描，不会自动删除任何文件",
                        onClick = onScan,
                    )
                }
            }
        }
    }
}

// ============================================================
// 2. 扫描中
// ============================================================

@Composable
private fun ScanningScreen(modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = "正在扫描…",
                    status = NowBarStatus.Idle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "spinner") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SpatialLayer(corner = 22.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RingSpinner(
                                color = NovaCareTheme.colors.accent,
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = "正在读取缓存、残留与临时文件",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "同时统计应用占用与存储构成。文件越多耗时越长，请保持应用在前台。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        item(key = "skeleton") {
            StaggerFlyIn(index = 2) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    NovaCard {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            repeat(4) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(NovaCareTheme.colors.ringTrack),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.55f)
                                                .height(12.dp)
                                                .clip(CircleShape)
                                                .background(NovaCareTheme.colors.ringTrack),
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(0.85f)
                                                .height(10.dp)
                                                .clip(CircleShape)
                                                .background(NovaCareTheme.colors.ringTrack.copy(alpha = 0.6f)),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 3. 失败态
// ============================================================

@Composable
private fun FailedScreen(
    modifier: Modifier,
    message: String,
    onRetry: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = "扫描未完成",
                    status = NowBarStatus.Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "error") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyState(
                        title = "扫描没能跑完",
                        message = message,
                        icon = Icons.Outlined.ErrorOutline,
                        tone = EmptyTone.Error,
                    )
                }
            }
        }
        item(key = "retry") {
            StaggerFlyIn(index = 2) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(text = "重新扫描", onClick = onRetry)
                }
            }
        }
    }
}

// ============================================================
// 4. 执行中
// ============================================================

@Composable
private fun ExecutingScreen(modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = "正在执行…",
                    status = NowBarStatus.Warning,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "running") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SpatialLayer(corner = 22.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            RingSpinner(
                                color = NovaCareTheme.colors.accent,
                                modifier = Modifier.size(44.dp),
                                strokeWidth = 3.dp,
                            )
                            Spacer(Modifier.height(18.dp))
                            Text(
                                text = "正在处理选中的项目",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "请不要关闭应用。无法代为清理的项目会改成引导你前往系统设置页，" +
                                    "不会计入释放量。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// 5. 结果清单（主屏）
// ============================================================

@Composable
private fun ResultsScreen(
    modifier: Modifier,
    state: CleanViewModel.UiState.Results,
    selected: Set<String>,
    includeRisky: Boolean,
    moveToRecycleBin: Boolean,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onIncludeRiskyChange: (Boolean) -> Unit,
    onMoveToRecycleBinChange: (Boolean) -> Unit,
    onExecute: () -> Unit,
    onRescan: () -> Unit,
    onGrant: (MissingCapability) -> Unit,
) {
    val plan = state.plan
    val colors = NovaCareTheme.colors
    val safeAdvices = plan.advices.filter { it.risk == CleanRisk.SAFE }
    val cautionAdvices = plan.advices.filter { it.risk == CleanRisk.CAUTION }
    val riskyAdvices = plan.advices.filter { it.risk == CleanRisk.RISKY }
    val selectedBytes = plan.advices.filter { it.key() in selected }.sumOf { it.recommendedBytes }
    val junkRatio = if (state.storageTotalBytes > 0) {
        plan.totalReclaimableBytes.toFloat() / state.storageTotalBytes.toFloat()
    } else {
        0f
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = "扫描耗时 ${plan.scanDurationMs} ms · 共命中 ${plan.advices.size} 项",
                    status = when {
                        !state.engineAvailable -> NowBarStatus.Error
                        plan.totalReclaimableBytes > 0L -> NowBarStatus.Healthy
                        else -> NowBarStatus.Idle
                    },
                    trailing = {
                        com.novacare.ui.designsystem.NowBarChip(
                            text = "重扫",
                            onClick = onRescan,
                            accent = NovaCareTheme.colors.accent,
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ---------- 引擎降级 / 授权缺口 ----------
        if (!state.engineAvailable) {
            item(key = "engine-notice") {
                StaggerFlyIn(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "扫描内核不可用 —— 下方的清单仅来自系统文件接口，" +
                                "应用的缓存与卸载残留无法被识别。不显示任何估算数字。",
                            tone = EmptyTone.Error,
                            actionText = "重试",
                            onAction = onRescan,
                        )
                    }
                }
            }
        }
        if (!state.usagePermissionGranted) {
            item(key = "usage-notice") {
                StaggerFlyIn(index = if (!state.engineAvailable) 2 else 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "未授予「使用情况访问」：无法判断哪些缓存属于长期未打开的应用，" +
                                "因此应用缓存这类项目不会出现在清单里。",
                            tone = EmptyTone.Warning,
                            actionText = "去开启",
                            onAction = { onGrant(MissingCapability.USAGE_STATS) },
                            icon = Icons.Outlined.Timer,
                        )
                    }
                }
            }
        }

        // ---------- 概览 ----------
        if (plan.advices.isEmpty()) {
            item(key = "empty") {
                StaggerFlyIn(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptyState(
                            title = "没有找到可以清理的内容",
                            message = buildString {
                                append("已扫描 ")
                                append(state.storageUsedBytes.formatBytes())
                                append(" 已用空间。")
                                if (!state.engineAvailable) {
                                    append("注意：内核不可用，本次扫描的覆盖面不完整。")
                                } else if (!includeRisky) {
                                    append("如需包含需确认项，打开下方的开关后可以再次扫描。")
                                }
                            },
                            icon = Icons.Outlined.CheckCircleOutline,
                            tone = if (state.engineAvailable) EmptyTone.Success else EmptyTone.Warning,
                            actionText = "重新扫描",
                            onAction = onRescan,
                        )
                    }
                }
            }
            item(key = "include-risky-empty") {
                StaggerFlyIn(index = 2) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ToggleRow(
                            title = "包含需确认项",
                            description = "日志、卸载残留等项目默认不参与清理，打开后可一并查看",
                            checked = includeRisky,
                            onCheckedChange = onIncludeRiskyChange,
                        )
                    }
                }
            }
            return@LazyColumn
        }

        item(key = "overview") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    OverviewCard(
                        reclaimableBytes = plan.totalReclaimableBytes,
                        ratio = junkRatio,
                        totalBytes = state.storageTotalBytes,
                        usedBytes = state.storageUsedBytes,
                    )
                }
            }
        }

        item(key = "stat-row") {
            StaggerFlyIn(index = 2) {
                Row(modifier = Modifier.padding(horizontal = 20.dp)) {
                    StatCard(
                        title = "安全可清",
                        value = safeAdvices.sumOf { it.recommendedBytes }.formatBytes(),
                        eyebrow = "默认勾选",
                        unit = null,
                        subtitle = "${safeAdvices.size} 项 · 删除无感知",
                        icon = Icons.Outlined.CheckCircleOutline,
                        accent = colors.riskSafe,
                        onClick = { onSelectAll(true) },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    StatCard(
                        title = "需确认",
                        value = cautionAdvices.sumOf { it.recommendedBytes }.formatBytes(),
                        eyebrow = "手动开启",
                        subtitle = if (cautionAdvices.isEmpty()) {
                            "本次未发现"
                        } else {
                            "${cautionAdvices.size} 项 · 可能影响后台功能"
                        },
                        icon = Icons.Outlined.WarningAmber,
                        accent = colors.riskCaution,
                        onClick = { onIncludeRiskyChange(true) },
                        disabledReason = if (cautionAdvices.isEmpty()) "本次扫描未命中此类项目" else null,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // ---------- 存储构成 ----------
        if (state.storageCategories.isNotEmpty()) {
            item(key = "storage-header") {
                StaggerFlyIn(index = 3) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(
                            title = "存储构成",
                            trailing = "内核分类${state.storageCategories.size} 类",
                        )
                    }
                }
            }
            item(key = "storage-card") {
                StaggerFlyIn(index = 4) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        NovaCard {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                state.storageCategories.take(8).forEach { category ->
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        NovaProgressBar(
                                            progress = category.ratio.toFloat().coerceIn(0f, 1f),
                                            color = colors.accent.copy(alpha = 0.85f),
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        KeyValueRow(
                                            key = category.name,
                                            value = "${category.bytes.formatBytes()} · " +
                                                "${"%.1f".format(category.ratio * 100)}%",
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))
                                }
                                Spacer(Modifier.height(2.dp))
                                KeyValueRow(
                                    key = "已用 / 总量",
                                    value = "${state.storageUsedBytes.formatBytes()} / " +
                                        state.storageTotalBytes.formatBytes(),
                                )
                            }
                        }
                    }
                }
            }
        } else if (state.engineAvailable) {
            item(key = "storage-empty") {
                StaggerFlyIn(index = 3) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "内核没有返回存储分类明细。这不代表磁盘是空的 —— " +
                                "只是本次扫描没能拿到分类数据。",
                            tone = EmptyTone.Neutral,
                            icon = Icons.Outlined.PieChart,
                        )
                    }
                }
            }
        }

        // ---------- 清单：按风险等级分组 ----------
        item(key = "tools-header") {
            StaggerFlyIn(index = 5) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(title = "清理范围")
                }
            }
        }

        item(key = "tools") {
            StaggerFlyIn(index = 6) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    ToggleRow(
                        title = "包含需确认 / 有风险项",
                        description = "默认只列确定安全的内容。打开后可以自行判断并勾选。",
                        checked = includeRisky,
                        onCheckedChange = onIncludeRiskyChange,
                    )
                    Spacer(Modifier.height(12.dp))
                    ToggleRow(
                        title = "移入回收站而不是直接删除",
                        description = if (moveToRecycleBin) {
                            "文件会被移入回收站，7 天内可撤销"
                        } else {
                            "当前由清理引擎决定删除方式；可在此确认撤销窗口"
                        },
                        checked = moveToRecycleBin,
                        onCheckedChange = onMoveToRecycleBinChange,
                    )
                }
            }
        }

        adviceGroup(
            keyPrefix = "safe",
            title = "可安全清理",
            hint = "命中 ${safeAdvices.size} 项 · ${safeAdvices.sumOf { it.recommendedBytes }.formatBytes()}",
            advices = safeAdvices,
            selected = selected,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
        )

        adviceGroup(
            keyPrefix = "caution",
            title = "需要确认",
            hint = "命中 ${cautionAdvices.size} 项 · ${cautionAdvices.sumOf { it.recommendedBytes }.formatBytes()}",
            advices = cautionAdvices,
            selected = selected,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            emptyHint = if (includeRisky) {
                "这一类本次没有命中项目"
            } else {
                "当前未展开：打开上方的「包含需确认项」后会重新计算"
            },
        )

        adviceGroup(
            keyPrefix = "risky",
            title = "有风险",
            hint = "命中 ${riskyAdvices.size} 项 · ${riskyAdvices.sumOf { it.recommendedBytes }.formatBytes()}",
            advices = riskyAdvices,
            selected = selected,
            onToggle = onToggle,
            onSelectAll = onSelectAll,
            emptyHint = "这一类本次没有命中项目",
        )

        // ---------- 执行 ----------
        item(key = "execute") {
            StaggerFlyIn(index = 99) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(
                        text = if (selected.isEmpty()) "请至少选择一项" else "执行清理",
                        subtitle = if (selected.isEmpty()) {
                            "共 ${plan.advices.size} 项可处理，当前一项都没选"
                        } else {
                            "已选 ${selected.size} 项 · 预计释放 ${selectedBytes.formatBytes()}"
                        },
                        enabled = selected.isNotEmpty(),
                        onClick = onExecute,
                    )
                }
            }
        }

        item(key = "footer") {
            StaggerFlyIn(index = 100) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "所有判断都在本机完成。清单中的每一项都能回溯它的来源与代价。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 单个风险分组的清单块（可复用的 LazyListScope 扩展） */
private fun androidx.compose.foundation.lazy.LazyListScope.adviceGroup(
    keyPrefix: String,
    title: String,
    hint: String,
    advices: List<CleanAdvice>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    emptyHint: String? = null,
) {
    item(key = "$keyPrefix-header") {
        SectionHeader(title = title, trailing = hint)
    }

    if (advices.isEmpty()) {
        if (emptyHint != null) {
            item(key = "$keyPrefix-empty") {
                Text(
                    text = emptyHint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
        }
        return
    }

    val allSelected = advices.all { it.key() in selected }

    item(key = "$keyPrefix-selectall") {
        SecondaryAction(
            text = if (allSelected) "取消全选（$title）" else "全选 $title（${advices.size} 项）",
            onClick = { onSelectAll(!allSelected) },
        )
    }

    items(
        count = advices.size,
        key = { index -> "$keyPrefix-${advices[index].key()}" },
    ) { index ->
        val advice = advices[index]
        AdviceRow(
            advice = advice,
            checked = advice.key() in selected,
            onToggle = { onToggle(advice.key()) },
        )
    }
}

// ============================================================
// 6. 完成态
// ============================================================

@Composable
private fun DoneScreen(
    modifier: Modifier,
    state: CleanViewModel.UiState.Done,
    onRescan: () -> Unit,
) {
    val result = state.result
    val noFailure = result.failed.isEmpty() && result.needsManual.isEmpty()

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "清理",
                    subtitle = if (result.freedBytes > 0) {
                        "已释放 ${result.freedBytes.formatBytes()}"
                    } else {
                        "执行完成 · 未释放空间"
                    },
                    status = when {
                        noFailure -> NowBarStatus.Healthy
                        result.freedBytes > 0 -> NowBarStatus.Warning
                        else -> NowBarStatus.Error
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item(key = "summary") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyState(
                        title = if (result.freedBytes > 0) {
                            "已释放 ${result.freedBytes.formatBytes()}"
                        } else {
                            "本次没有释放空间"
                        },
                        message = buildString {
                            append("成功 ${result.succeeded.size} 项")
                            if (result.failed.isNotEmpty()) append(" · 失败 ${result.failed.size} 项")
                            if (result.needsManual.isNotEmpty()) {
                                append(" · 需你手动完成 ${result.needsManual.size} 项")
                            }
                        },
                        icon = if (noFailure) {
                            Icons.Outlined.CheckCircleOutline
                        } else {
                            Icons.Outlined.WarningAmber
                        },
                        tone = when {
                            noFailure -> EmptyTone.Success
                            result.freedBytes > 0 -> EmptyTone.Warning
                            else -> EmptyTone.Error
                        },
                    )
                }
            }
        }

        if (result.recycleBinPath != null) {
            item(key = "recycle") {
                StaggerFlyIn(index = 2) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "已移入回收站：${result.recycleBinPath}。7 天内可从回收站还原。",
                            tone = EmptyTone.Neutral,
                            icon = Icons.Outlined.Restore,
                        )
                    }
                }
            }
        }

        if (result.needsManual.isNotEmpty()) {
            item(key = "manual-notice") {
                StaggerFlyIn(index = 3) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "以下 ${result.needsManual.size} 项需要你在系统设置页里手动清除，" +
                                "因此**没有**计入上面的释放量。本应用无法代第三方应用清缓存。",
                            tone = EmptyTone.Warning,
                            icon = Icons.Outlined.FolderDelete,
                        )
                    }
                }
            }
            item(key = "manual-header") {
                StaggerFlyIn(index = 4) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "需手动完成", trailing = "${result.needsManual.size} 项")
                    }
                }
            }
            items(
                count = result.needsManual.size,
                key = { index -> "manual-$index" },
            ) { index ->
                StaggerFlyIn(index = 5) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ResultNameRow(
                            name = result.needsManual[index],
                            icon = Icons.Outlined.FolderDelete,
                            tint = NovaCareTheme.colors.riskCaution,
                        )
                    }
                }
            }
        }

        if (result.failed.isNotEmpty()) {
            item(key = "failed-header") {
                StaggerFlyIn(index = 6) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "执行失败", trailing = "${result.failed.size} 项")
                    }
                }
            }
            items(
                count = result.failed.size,
                key = { index -> "failed-$index" },
            ) { index ->
                StaggerFlyIn(index = 7) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ResultNameRow(
                            name = result.failed[index],
                            icon = Icons.Outlined.ErrorOutline,
                            tint = NovaCareTheme.colors.riskRisky,
                        )
                    }
                }
            }
        }

        if (result.succeeded.isNotEmpty()) {
            item(key = "succeeded-header") {
                StaggerFlyIn(index = 8) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "已处理", trailing = "${result.succeeded.size} 项")
                    }
                }
            }
            items(
                count = result.succeeded.size,
                key = { index -> "ok-$index" },
            ) { index ->
                StaggerFlyIn(index = 9) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        ResultNameRow(
                            name = result.succeeded[index],
                            icon = Icons.Outlined.CheckCircleOutline,
                            tint = NovaCareTheme.colors.riskSafe,
                        )
                    }
                }
            }
        }

        item(key = "again") {
            StaggerFlyIn(index = 99) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(
                        text = "重新扫描",
                        subtitle = "看看还剩多少可以清理",
                        onClick = onRescan,
                    )
                }
            }
        }
    }
}

// ============================================================
// 复用组件
// ============================================================

/** 可清理量的视觉主角：环 + 三个真实读数 */
@Composable
private fun OverviewCard(
    reclaimableBytes: Long,
    ratio: Float,
    totalBytes: Long,
    usedBytes: Long,
) {
    val colors = NovaCareTheme.colors
    // v0.7.2 视觉统一：OverviewCard 改用 SpatialLayer 包裹，让氛围光从底部
    // 透上来（accentLight = accent @ 18% alpha），与 Home 的 Hero 同语言。
    SpatialLayer(corner = 22.dp, accentLight = colors.accent.copy(alpha = 0.18f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniRing(
                value = ratio.coerceIn(0f, 1f),
                label = "占总量",
                centerText = "${(ratio * 100).toInt()}%",
                color = colors.accent,
                diameter = 96.dp,
            )
            Spacer(Modifier.width(18.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reclaimableBytes.formatBytes(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "可回收总量",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                NovaProgressBar(
                    progress = if (totalBytes > 0) {
                        (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                    } else {
                        0f
                    },
                    color = colors.accentDim,
                )
                Spacer(Modifier.height(8.dp))
                KeyValueRow(key = "已用空间", value = usedBytes.formatBytes())
                KeyValueRow(key = "设备总量", value = totalBytes.formatBytes())
            }
        }
    }
}

/** 一条清理建议：勾选框 + 摘要 + 路径 + 代价披露 + 风险标签 */
@Composable
private fun AdviceRow(
    advice: CleanAdvice,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    NovaCard(onClick = onToggle) {
        Row(verticalAlignment = Alignment.Top) {
            SelectIndicator(
                checked = checked,
                contentDesc = if (checked) "取消选择 ${advice.targetLabel}" else "选择 ${advice.targetLabel}",
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = advice.targetLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = advice.recommendedBytes.formatBytes(),
                        style = MaterialTheme.typography.titleSmall,
                        color = NovaCareTheme.colors.accent,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = advice.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (advice.recommendedBytes < advice.totalBytes) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "该目录共 ${advice.totalBytes.formatBytes()}，" +
                            "本次建议只处理其中 ${advice.recommendedBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = NovaCareTheme.colors.riskCaution,
                    )
                }

                if (advice.costNote.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    InlineNotice(
                        text = advice.costNote,
                        tone = when (advice.risk) {
                            CleanRisk.SAFE -> EmptyTone.Neutral
                            CleanRisk.CAUTION -> EmptyTone.Warning
                            CleanRisk.RISKY -> EmptyTone.Error
                        },
                        icon = Icons.Outlined.WarningAmber,
                    )
                }

                advice.targetPath?.let { path ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = path,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RiskChip(risk = advice.risk)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = advice.risk.explain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 自绘勾选框：不引入 Material Checkbox 的默认风格，保持与设计系统一致的圆角与强调色 */
@Composable
private fun SelectIndicator(
    checked: Boolean,
    contentDesc: String,
) {
    val colors = NovaCareTheme.colors
    val boxSize = 22.dp
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (checked) colors.accent else Color.Transparent)
            .semantics {
                contentDescription = contentDesc
                role = Role.Checkbox
            },
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            Icon(
                imageVector = Icons.Outlined.CheckBox,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(17.dp),
            )
        } else {
            Canvas(modifier = Modifier.size(boxSize)) {
                val strokePx = 1.6.dp.toPx()
                drawRoundRect(
                    color = colors.hairline.copy(alpha = 0.75f),
                    topLeft = Offset(strokePx / 2f, strokePx / 2f),
                    size = Size(size.width - strokePx, size.height - strokePx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

/** 说明型开关行（自绘，避免 Material Switch 的默认视觉破坏克制调性） */
@Composable
private fun ToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = NovaCareTheme.colors
    NovaCard(onClick = { onCheckedChange(!checked) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (checked) colors.accent.copy(alpha = 0.85f) else colors.ringTrack,
                    )
                    .semantics {
                        contentDescription = "$title，${if (checked) "已开启" else "已关闭"}"
                        role = Role.Switch
                    },
                contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(
                            if (checked) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ),
                )
            }
        }
    }
}

/** 结果名单里的一行 */
@Composable
private fun ResultNameRow(
    name: String,
    icon: ImageVector,
    tint: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
