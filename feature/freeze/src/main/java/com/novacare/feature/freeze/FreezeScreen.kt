package com.novacare.feature.freeze

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
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
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeMethod
import com.novacare.core.model.FreezeResult
import com.novacare.core.model.FreezeRisk
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AuroraBackground
import com.novacare.ui.designsystem.EmptyState
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.KeyValueRow
import com.novacare.ui.designsystem.NovaCard
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaNowBar
import com.novacare.ui.designsystem.NowBarChip
import com.novacare.ui.designsystem.NowBarStatus
import com.novacare.ui.designsystem.PrimaryAction
import com.novacare.ui.designsystem.RingSpinner
import com.novacare.ui.designsystem.SecondaryAction
import com.novacare.ui.designsystem.SectionHeader
import com.novacare.ui.designsystem.SpatialLayer
import com.novacare.ui.designsystem.StaggerFlyIn
import com.novacare.ui.designsystem.StatCard

/**
 * 冻结页（L2）。
 *
 * 【v0.7.2 视觉统一】与 HomeScreen 同一设计语言：
 *   - 顶部 NovaNowBar（替代老 FreezeHeader）
 *   - 所有顶层 item 包 StaggerFlyIn（替代整页 AnimatedVisibility 单组入场）
 *   - 圆角统一 22dp
 *   - LazyColumn 上下 padding：top 10dp / bottom 24dp
 *
 * 布局节奏：
 *   1. 标题栏 —— 页面名 + 一份对"冻结到底做了什么"的诚实说明
 *   2. 能力区 —— 缺权限 / 缺 Shizuku，每一项都带可执行按钮
 *   3. 指标区 —— 三张 StatCard：应用总数、长期未用、已冻结
 *   4. 分组清单 —— 「建议冻结」/「需确认」/「全部应用（本次未判定为不常用）」
 *   5. 批量操作区 —— 已选 N 个 + 主 CTA
 *   6. 确认步骤 —— 执行前明确列出将受影响的 app，说明恢复方式
 *   7. 结果态 —— 区分"已通过 Shizuku 真实冻结"与"已打开系统设置页需你手动操作"
 *
 * 关键设计决策：**把"引导去设置页"和"已冻结"严格分开表达**。
 * 上一版把两者都算作成功，用户看到"成功"却回到桌面发现应用照常运行 ——
 * 这是最伤信任的一类谎报。
 */
@Composable
fun FreezeScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: FreezeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val frozen by viewModel.frozen.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystem.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    LaunchedEffect(rootPath) { viewModel.load(rootPath) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    AuroraBackground {
        // v0.7.2 视觉统一：移除 AnimatedVisibility 单组入场，
        // 改为每个子屏的 item 用 StaggerFlyIn 错峰飞入。
        when (val s = state) {
            FreezeViewModel.UiState.Idle -> FreezeIdle(
                modifier = modifier,
                onLoad = { viewModel.load(rootPath, force = true) },
            )

            FreezeViewModel.UiState.Scanning -> FreezeLoading(modifier = modifier)

            is FreezeViewModel.UiState.Failed -> FreezeFailed(
                modifier = modifier,
                message = s.message,
                onRetry = { viewModel.load(rootPath, force = true) },
            )

            is FreezeViewModel.UiState.Applying -> FreezeApplying(modifier = modifier, state = s)

            is FreezeViewModel.UiState.Applied -> FreezeApplied(
                modifier = modifier,
                state = s,
                onDismiss = viewModel::dismissResult,
            )

            is FreezeViewModel.UiState.Ready -> FreezeReady(
                modifier = modifier,
                state = s,
                frozen = frozen,
                selected = selected,
                includeSystem = includeSystem,
                pending = pending,
                onToggleSelected = viewModel::toggleSelected,
                onSelectAll = viewModel::selectAll,
                onIncludeSystemChange = viewModel::setIncludeSystem,
                onRequestFreeze = viewModel::requestFreeze,
                onRequestUnfreeze = viewModel::requestUnfreeze,
                onRequestBatch = viewModel::requestBatchFreeze,
                onCancelPending = viewModel::cancelPending,
                onConfirmPending = viewModel::confirmPending,
                onGrant = viewModel::grant,
                onReload = { viewModel.load(rootPath, force = true) },
            )
        }
    }
}

// ============================================================
// 1. 空闲 / 加载 / 失败
// ============================================================

@Composable
private fun FreezeIdle(modifier: Modifier, onLoad: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = "未读取应用列表",
                    status = NowBarStatus.Idle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "intro") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SpatialLayer(corner = 22.dp) {
                        Column {
                            Text(
                                text = "冻结 ≠ 卸载",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "冻结会让系统停止这个应用的后台活动，桌面图标与数据都保留，" +
                                    "需要时打开一次即可恢复正常。它适合那些装了很久却几乎不用的应用。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(12.dp))
                            ManifestRow("判断依据", "系统使用记录 + 待机分级", Icons.Outlined.Timer)
                            ManifestRow("不做什么", "不会卸载、不会清除你的数据", Icons.Outlined.Shield)
                            ManifestRow("恢复方式", "在系统设置里重新启用，或在此页解冻", Icons.Outlined.LockOpen)
                        }
                    }
                }
            }
        }
        item(key = "cta") {
            StaggerFlyIn(index = 2) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(
                        text = "读取应用列表",
                        subtitle = "只读取已装应用与使用记录，不改动任何应用",
                        onClick = onLoad,
                    )
                }
            }
        }
    }
}

@Composable
private fun FreezeLoading(modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = "正在读取应用列表…",
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
                                text = "正在统计应用占用与使用记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "未授予「使用情况访问」时，最后使用时间会显示为未知，而不是 0 天。",
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

@Composable
private fun FreezeFailed(modifier: Modifier, message: String, onRetry: () -> Unit) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = "读取未完成",
                    status = NowBarStatus.Error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        item(key = "error") {
            StaggerFlyIn(index = 1) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    EmptyState(
                        title = "没能读到应用列表",
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
                    PrimaryAction(text = "重新读取", onClick = onRetry)
                }
            }
        }
    }
}

@Composable
private fun FreezeApplying(
    modifier: Modifier,
    state: FreezeViewModel.UiState.Applying,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = if (state.freezing) "正在冻结" else "正在解冻",
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
                                text = state.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = state.packageName,
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
    }
}

// ============================================================
// 2. 就绪（主屏）
// ============================================================

@Composable
private fun FreezeReady(
    modifier: Modifier,
    state: FreezeViewModel.UiState.Ready,
    frozen: Set<String>,
    selected: Set<String>,
    includeSystem: Boolean,
    pending: FreezeViewModel.PendingAction?,
    onToggleSelected: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
    onRequestFreeze: (String) -> Unit,
    onRequestUnfreeze: (String) -> Unit,
    onRequestBatch: () -> Unit,
    onCancelPending: () -> Unit,
    onConfirmPending: () -> Unit,
    onGrant: (MissingCapability) -> Unit,
    onReload: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val recommended = state.candidates.filter { it.risk == FreezeRisk.SAFE && it.app.packageName !in frozen }
    val caution = state.candidates.filter { it.risk == FreezeRisk.CAUTION && it.app.packageName !in frozen }
    val risky = state.candidates.filter { it.risk == FreezeRisk.RISKY && it.app.packageName !in frozen }
    val frozenCandidates = state.candidates.filter { it.app.packageName in frozen }
    val longUnused = state.allApps.count { (it.daysUnused ?: 0) >= 30 }
    // 长按触发的单应用操作抽屉（One UI 9.5 的「长按展开动作菜单」范式）
    var actionSheetCandidate by remember { mutableStateOf<FreezeCandidate?>(null) }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = "已装 ${state.allApps.size} 个应用 · Shizuku " +
                        if (state.shizukuAvailable) "已就绪" else "未连接",
                    status = when {
                        !state.shizukuAvailable -> NowBarStatus.Warning
                        state.shizukuAvailable && state.advancedMode -> NowBarStatus.Healthy
                        else -> NowBarStatus.Idle
                    },
                    trailing = {
                        NowBarChip(
                            text = "重读",
                            onClick = onReload,
                            accent = NovaCareTheme.colors.accent,
                        )
                    },
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // ---------- 能力缺口 ----------
        if (!state.usagePermissionGranted) {
            item(key = "usage-notice") {
                StaggerFlyIn(index = 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "缺少「使用情况访问」权限 —— 读取不到任何应用的最后使用时间，" +
                                "因此下面的候选只能依据系统待机分级筛选，且无法告诉你「多久没用过」。" +
                                "这是本页最主要的判断依据，建议先授权。",
                            tone = EmptyTone.Error,
                            actionText = "去开启",
                            onAction = { onGrant(MissingCapability.USAGE_STATS) },
                            icon = Icons.Outlined.Timer,
                        )
                    }
                }
            }
        }

        if (!state.engineAvailable) {
            item(key = "engine-notice") {
                StaggerFlyIn(index = if (!state.usagePermissionGranted) 2 else 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "扫描内核不可用：应用占用空间这类读数无法获取，列表中的大小会显示为未知。",
                            tone = EmptyTone.Warning,
                            actionText = "返回",
                            onAction = onReload,
                        )
                    }
                }
            }
        }

        item(key = "method-notice") {
            StaggerFlyIn(index = 2) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    InlineNotice(
                        text = if (state.shizukuAvailable && state.advancedMode) {
                            "当前为高级模式：冻结会通过 Shizuku 直接执行，可立即生效并可一键解冻。"
                        } else if (state.shizukuAvailable) {
                            "检测到 Shizuku 已就绪，但「高级模式」未开启。" +
                                "当前冻结会改为打开系统设置页由你手动停用（官方路径，零风险）。"
                        } else {
                            "未连接 Shizuku：冻结会打开该应用的系统设置页，由你手动停用。" +
                                "这是官方路径、零风险，但每一步都需要你亲自操作 —— " +
                                "本页不会把「已打开设置页」当作「已冻结」来上报。"
                        },
                        tone = if (state.shizukuAvailable && state.advancedMode) {
                            EmptyTone.Success
                        } else {
                            EmptyTone.Neutral
                        },
                        icon = Icons.Outlined.Science,
                    )
                }
            }
        }

        // ---------- 指标 ----------
        item(key = "stats") {
            StaggerFlyIn(index = 3) {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            title = "已装应用",
                            value = state.allApps.size.toString(),
                            unit = "个",
                            eyebrow = "全部",
                            subtitle = "含 ${state.allApps.count { it.app.isSystem }} 个系统应用",
                            icon = Icons.Outlined.PersonOutline,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "长期未用",
                            value = if (state.usagePermissionGranted) longUnused.toString() else "—",
                            unit = if (state.usagePermissionGranted) "个" else null,
                            eyebrow = "≥30 天",
                            subtitle = if (state.usagePermissionGranted) {
                                "有使用记录且超过 30 天未打开"
                            } else {
                                "缺「使用情况访问」，无法统计"
                            },
                            icon = Icons.Outlined.Schedule,
                            disabledReason = if (state.usagePermissionGranted) null else "缺少使用情况访问权限",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatCard(
                            title = "已冻结",
                            value = if (frozen.isEmpty()) "0" else frozen.size.toString(),
                            unit = "个",
                            eyebrow = "本次会话",
                            subtitle = if (frozen.isEmpty()) {
                                "尚无通过 Shizuku 真实冻结的应用"
                            } else {
                                "可在此页随时解冻"
                            },
                            icon = Icons.Outlined.Block,
                            accent = colors.accent,
                            modifier = Modifier.weight(1f),
                        )
                        StatCard(
                            title = "建议冻结",
                            value = recommended.size.toString(),
                            unit = "个",
                            eyebrow = "有依据",
                            subtitle = "系统分级为很少使用，且无近期活动",
                            icon = Icons.Outlined.Speed,
                            accent = colors.riskSafe,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // ---------- 空态 ----------
        if (state.candidates.isEmpty()) {
            item(key = "empty") {
                StaggerFlyIn(index = 4) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        EmptyState(
                            title = "没有识别出不常用的应用",
                            message = buildString {
                                append("已检查 ")
                                append(state.allApps.size)
                                append(" 个已装应用。")
                                when {
                                    !state.usagePermissionGranted ->
                                        append("但由于缺少「使用情况访问」权限，本次判断是不完整的 —— 授权后重读会有更准确的结果。")

                                    includeSystem -> append("包括系统应用在内，没有任何一个符合判定条件。")

                                    else -> append("这些应用近期都有使用记录，或系统未将其标记为不常用。开关下方的「纳入系统应用」可以看看是否有可处理项。")
                                }
                            },
                            icon = Icons.Outlined.CheckCircleOutline,
                            tone = if (state.usagePermissionGranted) EmptyTone.Success else EmptyTone.Warning,
                            actionText = "重新读取",
                            onAction = onReload,
                        )
                    }
                }
            }
        }

        // ---------- 建议冻结 ----------
        candidateGroup(
            keyPrefix = "recommended",
            title = "建议冻结",
            hint = "${recommended.size} 个 · 可安全冻结",
            candidates = recommended,
            frozen = frozen,
            selected = selected,
            onToggleSelected = onToggleSelected,
            onRequestFreeze = onRequestFreeze,
            onRequestUnfreeze = onRequestUnfreeze,
            onLongPress = { c -> actionSheetCandidate = c },
            emptyHint = "目前没有符合「可安全冻结」条件的应用",
            startIndex = 5,
        )

        // ---------- 需确认 ----------
        candidateGroup(
            keyPrefix = "caution",
            title = "需确认",
            hint = "${caution.size} 个 · 近期仍有活动",
            candidates = caution,
            frozen = frozen,
            selected = selected,
            onToggleSelected = onToggleSelected,
            onRequestFreeze = onRequestFreeze,
            onRequestUnfreeze = onRequestUnfreeze,
            onLongPress = { c -> actionSheetCandidate = c },
            emptyHint = "没有需要额外确认的应用",
            startIndex = 8,
        )

        // ---------- 不建议冻结 ----------
        if (risky.isNotEmpty()) {
            candidateGroup(
                keyPrefix = "risky",
                title = "不建议冻结",
                hint = "${risky.size} 个 · 系统组件",
                candidates = risky,
                frozen = frozen,
                selected = selected,
                onToggleSelected = onToggleSelected,
                onRequestFreeze = onRequestFreeze,
                onRequestUnfreeze = onRequestUnfreeze,
                onLongPress = { c -> actionSheetCandidate = c },
                allowSelection = false,
                emptyHint = null,
                startIndex = 11,
            )
        }

        // ---------- 已冻结 ----------
        if (frozenCandidates.isNotEmpty()) {
            item(key = "frozen-header") {
                StaggerFlyIn(index = 14) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SectionHeader(title = "已冻结", trailing = "${frozenCandidates.size} 个")
                    }
                }
            }
            items(
                count = frozenCandidates.size,
                key = { index -> "frozen-${frozenCandidates[index].app.packageName}" },
            ) { index ->
                StaggerFlyIn(index = 15 + index) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        AppRow(
                            candidate = frozenCandidates[index],
                            isFrozen = true,
                            checked = false,
                            selectable = false,
                            onToggle = {},
                            onAction = { onRequestUnfreeze(frozenCandidates[index].app.packageName) },
                            onLongPress = { actionSheetCandidate = frozenCandidates[index] },
                        )
                    }
                }
            }
        }

        // ---------- 全部应用补充说明 ----------
        item(key = "all-header") {
            StaggerFlyIn(index = 30) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SectionHeader(title = "本次未被判定为不常用", trailing = "${state.allApps.size} 个已装")
                }
            }
        }
        item(key = "all-note") {
            StaggerFlyIn(index = 31) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    NovaCard {
                        Text(
                            text = "这些应用没有出现在上面的候选里，说明系统认为它们仍在日常使用范围内。" +
                                "若你确信某个应用已经很久没用，那多半是因为它的使用记录不完整 —— " +
                                "请先确认「使用情况访问」权限是否已开启。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        KeyValueRow(
                            key = "判断依据",
                            value = if (state.usagePermissionGranted) "使用记录 + 待机分级" else "仅待机分级（不完整）",
                        )
                        KeyValueRow(
                            key = "判定阈值",
                            value = "系统分级为 RARE / RESTRICTED / NEVER，或 ≥30 天未打开",
                        )
                        KeyValueRow(
                            key = "纳入系统应用",
                            value = if (includeSystem) "已纳入" else "未纳入",
                        )
                    }
                }
            }
        }

        item(key = "include-system") {
            StaggerFlyIn(index = 32) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    FreezeToggleRow(
                        title = "纳入系统应用",
                        description = "系统组件通常不建议冻结。打开后会一并列出，风险等级会如实标注。",
                        checked = includeSystem,
                        onCheckedChange = onIncludeSystemChange,
                    )
                }
            }
        }

        // ---------- 批量操作 ----------
        if (selected.isNotEmpty()) {
            item(key = "batch") {
                StaggerFlyIn(index = 95) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        PrimaryAction(
                            text = "冻结选中的 ${selected.size} 个应用",
                            subtitle = if (state.shizukuAvailable && state.advancedMode) {
                                "将通过 Shizuku 直接执行，可随时解冻"
                            } else {
                                "将逐个打开系统设置页，需要你手动停用"
                            },
                            onClick = onRequestBatch,
                        )
                    }
                }
            }
        } else {
            item(key = "batch-hint") {
                StaggerFlyIn(index = 95) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = "勾选应用后可以一次性处理。默认不会勾选任何「不建议冻结」的项目。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item(key = "footer") {
            StaggerFlyIn(index = 100) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = "使用记录与待机分级都来自系统本身，本页不额外采集任何数据。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // 确认步骤：以浮层形式出现，不打断列表滚动位置
    pending?.let { action ->
        ConfirmOverlay(
            action = action,
            shizukuAvailable = state.shizukuAvailable && state.advancedMode,
            onCancel = onCancelPending,
            onConfirm = onConfirmPending,
        )
    }

    // 长按触发的单应用操作抽屉（One UI 9.5「长按展开动作菜单」范式）
    actionSheetCandidate?.let { candidate ->
        AppActionSheet(
            candidate = candidate,
            isFrozen = candidate.app.packageName in frozen,
            shizukuAvailable = state.shizukuAvailable && state.advancedMode,
            onDismiss = { actionSheetCandidate = null },
            onFreeze = {
                onRequestFreeze(candidate.app.packageName)
                actionSheetCandidate = null
            },
            onUnfreeze = {
                onRequestUnfreeze(candidate.app.packageName)
                actionSheetCandidate = null
            },
        )
    }
}

// ============================================================
// 3. 结果态
// ============================================================

@Composable
private fun FreezeApplied(
    modifier: Modifier,
    state: FreezeViewModel.UiState.Applied,
    onDismiss: () -> Unit,
) {
    val result: FreezeResult = state.result
    // 只有 Shizuku 路径才真正改变了系统状态
    val reallyApplied = result.success && result.method == FreezeMethod.SHIZUKU_SUSPEND

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 0.dp, end = 0.dp, top = 10.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "now-bar") {
            StaggerFlyIn(index = 0) {
                NovaNowBar(
                    title = "冻结",
                    subtitle = if (state.freezing) "冻结结果" else "解冻结果",
                    status = when {
                        reallyApplied -> NowBarStatus.Healthy
                        result.success -> NowBarStatus.Warning
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
                        title = when {
                            reallyApplied && state.freezing -> "已冻结 ${state.label}"
                            reallyApplied -> "已解冻 ${state.label}"
                            result.success -> "已打开系统设置页"
                            else -> "操作未能完成"
                        },
                        message = buildString {
                            append(result.message)
                            append("\n执行方式：")
                            append(result.method.displayName)
                            if (!reallyApplied && result.success) {
                                append("\n")
                                append("说明：本应用没有直接停用它的权限，因此没有把它计为「已冻结」。")
                                append("请在系统设置页里完成停用；如果那里也没有停用入口，该应用受系统保护，无法冻结。")
                            }
                        },
                        icon = when {
                            reallyApplied -> Icons.Outlined.CheckCircleOutline
                            result.success -> Icons.Outlined.TouchApp
                            else -> Icons.Outlined.ErrorOutline
                        },
                        tone = when {
                            reallyApplied -> EmptyTone.Success
                            result.success -> EmptyTone.Warning
                            else -> EmptyTone.Error
                        },
                    )
                }
            }
        }

        item(key = "detail") {
            StaggerFlyIn(index = 2) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    NovaCard {
                        KeyValueRow(key = "应用", value = state.label)
                        KeyValueRow(key = "包名", value = result.packageName)
                        KeyValueRow(key = "执行方式", value = result.method.displayName)
                        KeyValueRow(
                            key = "系统状态",
                            value = if (reallyApplied) "已变更" else "未变更",
                            valueColor = if (reallyApplied) {
                                NovaCareTheme.colors.riskSafe
                            } else {
                                NovaCareTheme.colors.riskCaution
                            },
                        )
                    }
                }
            }
        }

        if (!reallyApplied && result.success) {
            item(key = "guide") {
                StaggerFlyIn(index = 3) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        InlineNotice(
                            text = "在系统设置页中找到「停用」或「强行停止」并确认。若该页没有停用入口，" +
                                "说明它是受保护的系统应用，无法被冻结。",
                            tone = EmptyTone.Neutral,
                            icon = Icons.Outlined.HourglassEmpty,
                        )
                    }
                }
            }
        }

        item(key = "back") {
            StaggerFlyIn(index = 99) {
                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    PrimaryAction(text = "返回应用列表", onClick = onDismiss)
                }
            }
        }
    }
}

// ============================================================
// 复用组件
// ============================================================

@Composable
private fun ManifestRow(
    key: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = NovaCareTheme.colors.accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = key,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** 一个候选分组（LazyListScope 扩展，三组共用同一套渲染） */
private fun androidx.compose.foundation.lazy.LazyListScope.candidateGroup(
    keyPrefix: String,
    title: String,
    hint: String,
    candidates: List<FreezeCandidate>,
    frozen: Set<String>,
    selected: Set<String>,
    onToggleSelected: (String) -> Unit,
    onRequestFreeze: (String) -> Unit,
    onRequestUnfreeze: (String) -> Unit,
    onLongPress: (FreezeCandidate) -> Unit,
    allowSelection: Boolean = true,
    emptyHint: String?,
    startIndex: Int = 0,
) {
    item(key = "$keyPrefix-header") {
        StaggerFlyIn(index = startIndex) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                SectionHeader(title = title, trailing = hint)
            }
        }
    }

    if (candidates.isEmpty()) {
        if (emptyHint != null) {
            item(key = "$keyPrefix-empty") {
                StaggerFlyIn(index = startIndex + 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Text(
                            text = emptyHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
            }
        }
        return
    }

    if (allowSelection) {
        val selectable = candidates.filter { it.app.packageName !in frozen }
        if (selectable.isNotEmpty()) {
            val allSelected = selectable.all { it.app.packageName in selected }
            item(key = "$keyPrefix-selectall") {
                StaggerFlyIn(index = startIndex + 1) {
                    Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                        SecondaryAction(
                            text = if (allSelected) {
                                "取消全选（$title）"
                            } else {
                                "全选 $title（${selectable.size} 个）"
                            },
                            onClick = {
                                selectable.forEach { candidate ->
                                    val key = candidate.app.packageName
                                    val isSelected = key in selected
                                    if (allSelected && isSelected) onToggleSelected(key)
                                    if (!allSelected && !isSelected) onToggleSelected(key)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    items(
        count = candidates.size,
        key = { index -> "$keyPrefix-${candidates[index].app.packageName}" },
    ) { index ->
        StaggerFlyIn(index = startIndex + 2 + index) {
            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                val candidate = candidates[index]
                val isFrozen = candidate.app.packageName in frozen
                AppRow(
                    candidate = candidate,
                    isFrozen = isFrozen,
                    checked = candidate.app.packageName in selected,
                    selectable = allowSelection && !isFrozen,
                    onToggle = { onToggleSelected(candidate.app.packageName) },
                    onAction = {
                        if (isFrozen) {
                            onRequestUnfreeze(candidate.app.packageName)
                        } else {
                            onRequestFreeze(candidate.app.packageName)
                        }
                    },
                    onLongPress = { onLongPress(candidate) },
                )
            }
        }
    }
}

/** 应用行：图标占位 + 名称 + 副信息 + 风险 + 勾选 + 操作
 *
 * 长按行为：One UI 9.5 的「长按展开动作菜单」范式 —— 长按 500ms 触发
 * 一个底部浮起的操作抽屉（AppActionSheet），里面给到「冻结/解冻」
 * 「查看详情」「取消」三个动作，避免把按钮全部塞进列表行导致视觉拥挤。
 */
@Composable
private fun AppRow(
    candidate: FreezeCandidate,
    isFrozen: Boolean,
    checked: Boolean,
    selectable: Boolean,
    onToggle: () -> Unit,
    onAction: () -> Unit,
    onLongPress: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val riskColor = when (candidate.risk) {
        FreezeRisk.SAFE -> colors.riskSafe
        FreezeRisk.CAUTION -> colors.riskCaution
        FreezeRisk.RISKY -> colors.riskRisky
    }

    NovaCard(
        onClick = if (selectable) onToggle else null,
        modifier = Modifier.pointerInput(candidate.app.packageName) {
            // 长按触发动作抽屉。detectTapGestures 的 onLongPress 默认 ~500ms，
            // 与 One UI 系统级长按时长一致；点击不会被长按吞掉（onTap 仍正常）。
            detectTapGestures(
                onLongPress = { onLongPress() },
            )
        },
    ) {
        Row(verticalAlignment = Alignment.Top) {
            // 应用图标占位：首字母 + 语义色底，不加载真实图标（避免列表滚动时抖动）
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(riskColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = candidate.app.label.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = riskColor,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.app.label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (isFrozen) {
                        Spacer(Modifier.width(8.dp))
                        SurfaceTag(text = "已冻结", color = colors.accent)
                    }
                }

                Spacer(Modifier.height(2.dp))
                Text(
                    text = candidate.app.packageName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = candidate.daysLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = riskColor,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (candidate.app.sizeBytes > 0) {
                            "占用 ${candidate.app.sizeBytes.formatBytes()}"
                        } else {
                            "占用未知"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = candidate.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SurfaceTag(text = candidate.risk.displayName, color = riskColor)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = candidate.risk.explain,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (selectable) {
                        AppSelectIndicator(
                            checked = checked,
                            contentDesc = if (checked) {
                                "取消选择 ${candidate.app.label}"
                            } else {
                                "选择 ${candidate.app.label}"
                            },
                            onClick = onToggle,
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    SecondaryAction(
                        text = if (isFrozen) "解冻" else "冻结",
                        onClick = onAction,
                    )
                }
            }
        }
    }
}

@Composable
private fun SurfaceTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun AppSelectIndicator(
    checked: Boolean,
    contentDesc: String,
    onClick: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val boxSize = 22.dp
    Box(
        modifier = Modifier
            .size(boxSize)
            .clip(MaterialTheme.shapes.extraSmall)
            .background(if (checked) colors.accent else Color.Transparent)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClick = onClick,
            )
            .semantics { contentDescription = contentDesc; role = Role.Checkbox },
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
                    cornerRadius = CornerRadius(6.dp.toPx()),
                    style = Stroke(width = strokePx),
                )
            }
        }
    }
}

@Composable
private fun FreezeToggleRow(
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
                    .background(if (checked) colors.accent.copy(alpha = 0.85f) else colors.ringTrack)
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

/**
 * 长按触发的单应用操作抽屉（One UI 9.5 底部动作菜单风格）。
 *
 * 与批量 [ConfirmOverlay] 的差异：
 *   - 单应用操作，结构是「应用标识 + 三个动作按钮」，不是「确认条」
 *   - 顶部一个 28dp 大圆角的「把手」视觉提示 —— 模仿 One UI BottomSheet 的 grabber
 *   - 动作区上方有一段 InlineNotice，把 Shizuku 状态直白写出来，
 *     避免「点完才知道要走系统设置页」的欺骗感
 */
@Composable
private fun AppActionSheet(
    candidate: FreezeCandidate,
    isFrozen: Boolean,
    shizukuAvailable: Boolean,
    onDismiss: () -> Unit,
    onFreeze: () -> Unit,
    onUnfreeze: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    val riskColor = when (candidate.risk) {
        FreezeRisk.SAFE -> colors.riskSafe
        FreezeRisk.CAUTION -> colors.riskCaution
        FreezeRisk.RISKY -> colors.riskRisky
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // 遮罩：点击即关闭（与原 ConfirmOverlay 行为一致）
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.softShadow)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )

        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
            // 不规则圆角：上边大圆角，模拟 One UI 底部动作菜单的「软着陆」
            shape = RoundedCornerShape(
                topStart = 28.dp,
                topEnd = 28.dp,
                bottomStart = 0.dp,
                bottomEnd = 0.dp,
            ),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = BorderStroke(1.dp, colors.hairline),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            ) {
                // 顶部把手 —— One UI 底部 sheet 的视觉锚点
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(colors.hairline),
                )

                Spacer(Modifier.height(14.dp))

                // 应用标识：图标占位 + 名称 + 包名
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(riskColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = candidate.app.label.take(1).uppercase(),
                            style = MaterialTheme.typography.titleMedium,
                            color = riskColor,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = candidate.app.label,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = candidate.app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 把 Shizuku 状态直白说出来 —— One UI 风格的「动作可见性」
                Text(
                    text = if (isFrozen) {
                        "已冻结 · 可随时解冻恢复运行"
                    } else if (shizukuAvailable) {
                        "冻结将通过 Shizuku 直接执行，立即生效"
                    } else {
                        "未连接 Shizuku：冻结会打开系统设置页，由你手动停用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFrozen || shizukuAvailable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        colors.riskCaution
                    },
                )

                Spacer(Modifier.height(18.dp))

                // 三个动作：主操作 / 次操作 / 取消（One UI action sheet 的标准布局）
                PrimaryAction(
                    text = if (isFrozen) "解冻 ${candidate.app.label}" else "冻结 ${candidate.app.label}",
                    subtitle = if (isFrozen) {
                        "应用会立刻恢复运行"
                    } else if (shizukuAvailable) {
                        "可通过 Shizuku 直接执行"
                    } else {
                        "需手动在系统设置页停用"
                    },
                    onClick = {
                        if (isFrozen) onUnfreeze() else onFreeze()
                    },
                )

                Spacer(Modifier.height(8.dp))

                if (!isFrozen) {
                    SecondaryAction(
                        text = "查看详情",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            // 这一项故意"不做事"：应用行的副信息已经把详情摊开了。
                            // 保留按钮是为了 One UI 的「动作清单完整性」—— 用户预期长按
                            // 能展开一个完整动作集，缺一项会显得不专业。
                            onDismiss()
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // 取消 —— 始终放在最底部，与 One UI BottomSheet 风格一致
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(MaterialTheme.shapes.extraLarge)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onDismiss,
                        )
                        .semantics { contentDescription = "关闭动作抽屉" },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "取消",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** 确认步骤：执行前明确列出影响范围与恢复方式 */
@Composable
private fun ConfirmOverlay(
    action: FreezeViewModel.PendingAction,
    shizukuAvailable: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = NovaCareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NovaCard {
            Text(
                text = if (action.freezing) {
                    "确认冻结 ${action.labels.size} 个应用？"
                } else {
                    "确认解冻 ${action.labels.size} 个应用？"
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            action.labels.take(6).forEach { label ->
                Text(
                    text = "· $label",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (action.labels.size > 6) {
                Text(
                    text = "……以及另外 ${action.labels.size - 6} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            InlineNotice(
                text = if (shizukuAvailable) {
                    if (action.freezing) {
                        "将通过 Shizuku 执行 pm suspend —— 可逆，随时可在本页解冻。应用数据不会被删除。"
                    } else {
                        "将通过 Shizuku 解除冻结，应用会立刻恢复正常运行。"
                    }
                } else {
                    "将逐个打开系统设置页，由你手动操作。本应用无法代你停用其他应用，" +
                        "因此操作完成后列表不会标记为「已冻结」。"
                },
                tone = if (shizukuAvailable) EmptyTone.Neutral else EmptyTone.Warning,
                icon = if (shizukuAvailable) Icons.Outlined.Science else Icons.Outlined.TouchApp,
            )
            Spacer(Modifier.height(14.dp))
            PrimaryAction(
                text = if (action.freezing) "确认冻结" else "确认解冻",
                onClick = onConfirm,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryAction(
                text = "先不处理",
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
