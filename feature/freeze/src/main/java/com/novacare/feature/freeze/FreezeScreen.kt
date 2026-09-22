package com.novacare.feature.freeze

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeRisk
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// FreezeScreen — OneUI 9 重写版
//
// 模式：
//   - 顶部 OneUiAppBar
//   - Idle / Scanning / Failed / Applied 状态：单 squircle card（OneUiListSection）
//   - Ready 状态：全部 app 都包在一个 OneUiListSection 里
//     每行 = 36dp 圆形 tinted icon（首字母）+ 应用名 + daysLabel + M3 Switch
//     点击行 = 展开到包信息行（含"解冻/停用"按钮）
//   - sticky bottom CTA（OneUiListSection 风格保持一致）
// =========================================================================

@Composable
fun FreezeScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: FreezeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val frozen by viewModel.frozen.collectAsStateWithLifecycle()
    val includeSystem by viewModel.includeSystem.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (state is FreezeViewModel.UiState.Idle) viewModel.load(rootPath)
    }

    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "冻结")

            when (val s = state) {
                FreezeViewModel.UiState.Idle -> IdleBody(
                    onScan = { NovaTap(view); viewModel.load(rootPath, force = true) },
                )

                FreezeViewModel.UiState.Scanning -> StatusBody(
                    title = "正在读取",
                    subtitle = "读取你设备上的应用与最近使用情况",
                )

                is FreezeViewModel.UiState.Failed -> FailedBody(
                    message = s.message,
                    onRetry = { NovaTap(view); viewModel.load(rootPath, force = true) },
                )

                is FreezeViewModel.UiState.Applying -> StatusBody(
                    title = if (s.freezing) "正在冻结" else "正在解冻",
                    subtitle = s.label,
                )

                is FreezeViewModel.UiState.Applied -> AppliedBody(
                    label = s.label,
                    success = s.result.success,
                    freezing = s.freezing,
                    method = s.result.method.name,
                    onDismiss = { viewModel.dismissResult() },
                )

                is FreezeViewModel.UiState.Ready -> ReadyBody(
                    candidates = s.candidates,
                    allApps = s.allApps,
                    selected = selected,
                    frozen = frozen,
                    includeSystem = includeSystem,
                    shizukuAvailable = s.shizukuAvailable,
                    usagePermissionGranted = s.usagePermissionGranted,
                    engineAvailable = s.engineAvailable,
                    onToggleSelected = { pkg ->
                        NovaToggle(view.context, true)
                        viewModel.toggleSelected(pkg)
                    },
                    onSelectAll = { all -> NovaTap(view); viewModel.selectAll(all) },
                    onIncludeSystemChange = { inc ->
                        NovaTap(view); viewModel.setIncludeSystem(inc)
                    },
                    onBatchFreeze = { NovaTap(view); viewModel.requestBatchFreeze() },
                    onUnfreeze = { pkg -> NovaTap(view); viewModel.requestUnfreeze(pkg) },
                    onGrantShizuku = { viewModel.grant(MissingCapability.USAGE_STATS) },
                    onRetry = { NovaTap(view); viewModel.load(rootPath, force = true) },
                )
            }
        }
    }
}

// ============================================================
// 状态视图
// ============================================================

@Composable
private fun IdleBody(onScan: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                OneUiListRow(
                    icon = Icons.Outlined.AcUnit,
                    iconTint = FreezeTints.idle,
                    title = "看看哪些应用长期没被打开",
                    subtitle = "读取你设备上的应用与最近使用情况，列出可冻结的应用",
                    onClick = null,
                )
            }
        }
        item {
            PrimaryPillCta(text = "读取应用列表", enabled = true, onClick = onScan)
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun StatusBody(title: String, subtitle: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                OneUiListRow(
                    icon = Icons.Outlined.AcUnit,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = title,
                    subtitle = subtitle,
                    onClick = null,
                )
            }
        }
        item {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(OneUiSpacing.CardInner * 3),
                    strokeWidth = OneUiSpacing.CardGap / 2,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun FailedBody(message: String, onRetry: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                OneUiListRow(
                    icon = Icons.Outlined.AcUnit,
                    iconTint = NovaCareTheme.colors.riskRisky,
                    title = "读取失败",
                    subtitle = message,
                    onClick = null,
                )
            }
        }
        item {
            PrimaryPillCta(text = "重试", enabled = true, onClick = onRetry)
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun AppliedBody(
    label: String,
    success: Boolean,
    freezing: Boolean,
    method: String,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val view = LocalView.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(OneUiSpacing.BlockGap),
    ) {
        item {
            OneUiListSection {
                Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                    Text(
                        text = when {
                            !success -> if (freezing) "冻结失败" else "解冻失败"
                            else -> if (freezing) "已冻结 $label" else "已解冻 $label"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (success) colors.healthGood else colors.riskRisky,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = if (method == "SHIZUKU_SUSPEND")
                            "Shizuku 已写入系统状态"
                        else
                            "已引导至系统设置页，请按指示完成最后一步",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
        item {
            PrimaryPillCta(text = "完成", enabled = true, onClick = { NovaTap(view); onDismiss() })
        }
        item {
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

// ============================================================
// ReadyBody —— 全部 app 一个 OneUiListSection
// ============================================================

@Composable
private fun ReadyBody(
    candidates: List<FreezeCandidate>,
    allApps: List<FreezeCandidate>,
    selected: Set<String>,
    frozen: Set<String>,
    includeSystem: Boolean,
    shizukuAvailable: Boolean,
    usagePermissionGranted: Boolean,
    engineAvailable: Boolean,
    onToggleSelected: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
    onBatchFreeze: () -> Unit,
    onUnfreeze: (String) -> Unit,
    onGrantShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val safeCandidates = candidates.filter { it.risk == FreezeRisk.SAFE }
    val safeSelectedCount = selected.size
    val allSafeSelected = safeCandidates.isNotEmpty() && safeSelectedCount == safeCandidates.size

    var expandedPackage by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = OneUiSpacing.BlockGap,
                vertical = OneUiSpacing.SectionTitleGap,
            ),
            verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap),
        ) {
            // 概览行：长期未用安全项 + 总数
            item {
                OneUiListSection {
                    OneUiListRow(
                        icon = Icons.Outlined.AcUnit,
                        iconTint = FreezeTints.idle,
                        title = "${safeCandidates.size} 个长期未用 · 共 ${allApps.size} 个应用",
                        subtitle = "已选 $safeSelectedCount 个 · ${frozen.size} 个已冻结",
                        onClick = null,
                    )
                }
            }

            // 权限 / 引擎提示
            if (!usagePermissionGranted || !shizukuAvailable || !engineAvailable) {
                item {
                    NoticeStack(
                        usagePermissionGranted = usagePermissionGranted,
                        shizukuAvailable = shizukuAvailable,
                        engineAvailable = engineAvailable,
                        onGrantShizuku = onGrantShizuku,
                        onRetry = onRetry,
                    )
                }
            }

            // 全选 / 含系统 行
            item {
                OneUiListSection {
                    ChipRow(
                        allSelected = allSafeSelected,
                        includeSystem = includeSystem,
                        onSelectAll = onSelectAll,
                        onIncludeSystemChange = onIncludeSystemChange,
                    )
                }
            }

            // 清单主体
            if (candidates.isEmpty()) {
                item {
                    OneUiListSection {
                        OneUiListRow(
                            icon = Icons.Outlined.AcUnit,
                            iconTint = cs.onSurfaceVariant,
                            title = when {
                                !usagePermissionGranted -> "无法识别长期未用应用"
                                !shizukuAvailable -> "未配置 Shizuku，无法冻结"
                                else -> "暂未发现值得冻结的应用"
                            },
                            subtitle = when {
                                !usagePermissionGranted -> "请在系统设置授予「使用情况访问」"
                                !shizukuAvailable -> "配置 Shizuku 后即可一键冻结"
                                else -> "你常用的应用都很活跃"
                            },
                            onClick = null,
                        )
                    }
                }
            } else {
                item {
                    OneUiListSection {
                        candidates.forEachIndexed { index, candidate ->
                            val expanded = expandedPackage == candidate.app.packageName
                            CandidateRow(
                                candidate = candidate,
                                selected = candidate.app.packageName in selected,
                                frozen = candidate.app.packageName in frozen,
                                expanded = expanded,
                                onToggle = { onToggleSelected(candidate.app.packageName) },
                                onTap = {
                                    expandedPackage =
                                        if (expanded) null else candidate.app.packageName
                                },
                                onUnfreeze = { onUnfreeze(candidate.app.packageName) },
                                showDivider = index < candidates.lastIndex,
                            )
                        }
                    }
                }
            }

            item {
                Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap))
            }
        }

        if (selected.isNotEmpty()) {
            BottomFreezeBar(
                count = selected.size,
                onBatchFreeze = onBatchFreeze,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

// ============================================================
// 复用件
// ============================================================

@Composable
private fun CandidateRow(
    candidate: FreezeCandidate,
    selected: Boolean,
    frozen: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTap: () -> Unit,
    onUnfreeze: () -> Unit,
    showDivider: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val view = LocalView.current
    val accent = if (frozen) FreezeTints.frozen else FreezeTints.idle
    val firstLetter = candidate.app.label.firstOrNull()?.uppercase() ?: "?"

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTap() }
                .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.ListRowVertical),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(AvatarSize)
                    .clip(RoundedCornerShape(OneUiRadius.Pill))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = firstLetter,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                    color = accent,
                )
            }
            Spacer(Modifier.width(OneUiSpacing.CardInner))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.app.label,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                Text(
                    text = when {
                        frozen -> "已冻结"
                        candidate.daysUnused == null -> "无使用记录"
                        else -> "${candidate.daysUnused} 天未用"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (frozen) accent else cs.onSurfaceVariant,
                )
            }
            if (candidate.risk != FreezeRisk.SAFE) {
                RiskChip(risk = candidate.risk)
                Spacer(Modifier.width(OneUiSpacing.CardInner))
            }
            if (frozen) {
                Surface(
                    color = accent.copy(alpha = 0.18f),
                    modifier = Modifier
                        .clip(RoundedCornerShape(OneUiRadius.Pill))
                        .clickable { NovaTap(view); onUnfreeze() },
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = OneUiSpacing.SectionTitleGap,
                            vertical = OneUiSpacing.CardGap,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.AcUnit,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(RiskIconSize),
                        )
                        Spacer(Modifier.width(OneUiSpacing.CardGap / 2))
                        Text(
                            text = "解冻",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                        )
                    }
                }
            } else {
                // Switch 是状态指示器（整行点击是真实动作）
                Switch(
                    checked = selected,
                    onCheckedChange = null,
                    enabled = false,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = cs.onPrimary,
                        checkedTrackColor = cs.primary,
                    ),
                )
            }
        }

        if (expanded) {
            PackageDetail(
                packageName = candidate.app.packageName,
                reason = candidate.reason,
                risk = candidate.risk,
                frozen = frozen,
                onUnfreeze = onUnfreeze,
                onToggle = onToggle,
            )
        }

        if (showDivider) {
            Spacer(Modifier.height(OneUiSpacing.ListRowVertical))
            Box(
                modifier = Modifier
                    .padding(start = OneUiSpacing.CardInner * 3 + OneUiSpacing.CardGap)
                    .fillMaxWidth()
                    .height(OneUiSpacing.CardGap / 2)
                    .background(cs.onSurface.copy(alpha = 0.06f)),
            )
        }
    }
}

@Composable
private fun PackageDetail(
    packageName: String,
    reason: String,
    risk: FreezeRisk,
    frozen: Boolean,
    onUnfreeze: () -> Unit,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val view = LocalView.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = OneUiSpacing.CardInner * 3 + OneUiSpacing.CardGap,
                end = OneUiSpacing.CardInner,
                bottom = OneUiSpacing.ListRowVertical,
            ),
    ) {
        Text(
            text = packageName,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (reason.isNotBlank()) {
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = risk.explain,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        if (frozen) {
            SecondaryPillCta(text = "解冻", onClick = { NovaTap(view); onUnfreeze() })
        } else {
            PrimaryPillCta(
                text = "加入待冻结",
                enabled = risk != FreezeRisk.RISKY,
                onClick = { NovaTap(view); onToggle() },
            )
        }
    }
}

@Composable
private fun RiskChip(risk: FreezeRisk) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val (label, color) = when (risk) {
        FreezeRisk.SAFE -> return
        FreezeRisk.CAUTION -> "需确认" to cs.tertiary
        FreezeRisk.RISKY -> "有风险" to colors.riskRisky
    }
    Surface(
        color = color.copy(alpha = 0.18f),
        modifier = Modifier.clip(RoundedCornerShape(OneUiRadius.Pill)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(
                horizontal = OneUiSpacing.SectionTitleGap,
                vertical = OneUiSpacing.CardGap / 2,
            ),
        )
    }
}

@Composable
private fun NoticeStack(
    usagePermissionGranted: Boolean,
    shizukuAvailable: Boolean,
    engineAvailable: Boolean,
    onGrantShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val view = LocalView.current
    val colors = NovaCareTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(OneUiSpacing.CardGap)) {
        if (!usagePermissionGranted) {
            NoticeRow(
                text = "未授予「使用情况访问」，无法识别长期未用应用",
                action = "授权",
                tone = colors.riskCaution,
                onClick = { NovaTap(view); onRetry() },
            )
        }
        if (!shizukuAvailable) {
            NoticeRow(
                text = "未配置 Shizuku，将无法一键冻结（会引导至系统设置）",
                action = "配置",
                tone = colors.riskCaution,
                onClick = { NovaTap(view); onGrantShizuku() },
            )
        }
        if (!engineAvailable) {
            NoticeRow(
                text = "内核不可用，仅显示已装应用清单",
                action = "重试",
                tone = colors.riskCaution,
                onClick = { NovaTap(view); onRetry() },
            )
        }
    }
}

@Composable
private fun NoticeRow(
    text: String,
    action: String,
    tone: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(tone.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardGap,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium,
            color = tone,
        )
    }
}

@Composable
private fun ChipRow(
    allSelected: Boolean,
    includeSystem: Boolean,
    onSelectAll: (Boolean) -> Unit,
    onIncludeSystemChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.SectionTitleGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChipToggle(
            text = if (allSelected) "全不选" else "全选",
            selected = false,
            onClick = { onSelectAll(!allSelected) },
        )
        Spacer(Modifier.width(OneUiSpacing.CardGap))
        ChipToggle(
            text = if (includeSystem) "✓ 含系统" else "含系统应用",
            selected = includeSystem,
            onClick = { onIncludeSystemChange(!includeSystem) },
        )
    }
}

@Composable
private fun ChipToggle(text: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable { onClick() },
        color = if (selected) cs.primary.copy(alpha = 0.12f)
        else cs.onSurface.copy(alpha = 0.06f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.primary else cs.onSurface,
            modifier = Modifier.padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.SectionTitleGap,
            ),
        )
    }
}

@Composable
private fun PrimaryPillCta(text: String, enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CtaHeight)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable(enabled = enabled) { onClick() },
        color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) cs.onPrimary else cs.onSurface.copy(alpha = 0.38f),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SecondaryPillCta(text: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(CtaHeight)
            .clip(RoundedCornerShape(OneUiRadius.Pill))
            .clickable { onClick() },
        color = cs.onSurface.copy(alpha = 0.06f),
        contentColor = cs.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun BottomFreezeBar(count: Int, onBatchFreeze: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cs.surface,
        shadowElevation = OneUiSpacing.CardGap,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            PrimaryPillCta(
                text = "冻结 $count 个应用",
                enabled = true,
                onClick = onBatchFreeze,
            )
        }
    }
}

// ============================================================
// 颜色 / 尺寸 —— 由 token 派生，0 自由 dp
// ============================================================

private object FreezeTints {
    val idle = Color(0xFF7B61FF)
    val frozen = Color(0xFF1B7A46)
}

/** 头像 = CardInner * 2 + CardGap = 40dp */
private val AvatarSize: Dp = OneUiSpacing.CardInner * 2 + OneUiSpacing.CardGap

/** 风险 chip 内的图标 = CardGap = 8dp */
private val RiskIconSize: Dp = OneUiSpacing.CardGap

/** 主 CTA 高度 = CardInner*4 - CardGap = 56dp */
private val CtaHeight: Dp = OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap
