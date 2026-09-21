package com.novacare.feature.freeze

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.model.FreezeCandidate
import com.novacare.core.model.FreezeRisk
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.GlassPanel
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing
import com.novacare.ui.designsystem.OneUiAppBar

/**
 * 冻结页 —— OneUI 9.5 真实设计语言。
 *
 * 单一主张（"冻结长期未用的应用"）+ 列表 + 单一 CTA。
 */
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
                FreezeViewModel.UiState.Idle -> IdleHero(
                    onScan = { NovaTap(view); viewModel.load(rootPath, force = true) }
                )

                FreezeViewModel.UiState.Scanning -> ScanningHero()

                is FreezeViewModel.UiState.Failed -> FailedHero(
                    message = s.message,
                    onRetry = { NovaTap(view); viewModel.load(rootPath, force = true) },
                )

                is FreezeViewModel.UiState.Applying -> ApplyingHero(s.label, s.freezing)

                is FreezeViewModel.UiState.Applied -> ResultBanner(
                    label = s.label,
                    success = s.result.success,
                    freezing = s.freezing,
                    method = s.result.method.name,
                    onDismiss = { viewModel.dismissResult() },
                )

                is FreezeViewModel.UiState.Ready -> ReadyView(
                    candidates = s.candidates,
                    allApps = s.allApps,
                    selected = selected,
                    frozen = frozen,
                    includeSystem = includeSystem,
                    shizukuAvailable = s.shizukuAvailable,
                    usagePermissionGranted = s.usagePermissionGranted,
                    engineAvailable = s.engineAvailable,
                    onToggleSelected = { pkg -> NovaToggle(view.context, true); viewModel.toggleSelected(pkg) },
                    onSelectAll = { all -> NovaTap(view); viewModel.selectAll(all) },
                    onIncludeSystemChange = { inc -> NovaTap(view); viewModel.setIncludeSystem(inc) },
                    onBatchFreeze = { NovaTap(view); viewModel.requestBatchFreeze() },
                    onUnfreeze = { pkg -> NovaTap(view); viewModel.requestUnfreeze(pkg) },
                    onGrantShizuku = { viewModel.grant(MissingCapability.USAGE_STATS) },
                    onRetry = { NovaTap(view); viewModel.load(rootPath, force = true) },
                )
            }
        }
    }
}

@Composable
private fun IdleHero(onScan: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "看看哪些应用长期没被打开",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onScan,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "读取应用列表",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                )
            }
        }
    }
}

@Composable
private fun ScanningHero() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "正在读取",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = "读取你设备上的应用与最近使用情况",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = cs.primary,
        )
    }
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "读取失败",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = onRetry,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("重试", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600))
            }
        }
    }
}

@Composable
private fun ApplyingHero(label: String, freezing: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = if (freezing) "正在冻结" else "正在解冻",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(40.dp))
        androidx.compose.material3.CircularProgressIndicator(
            modifier = Modifier.size(32.dp),
            strokeWidth = 3.dp,
            color = cs.primary,
        )
    }
}

@Composable
private fun ResultBanner(
    label: String,
    success: Boolean,
    freezing: Boolean,
    method: String,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val view = LocalView.current
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = when {
                !success -> if (freezing) "冻结失败" else "解冻失败"
                else -> if (freezing) "已冻结 $label" else "已解冻 $label"
            },
            style = MaterialTheme.typography.titleMedium,
            color = if (success) colors.healthGood else colors.riskRisky,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = if (method == "SHIZUKU_SUSPEND")
                "Shizuku 已写入系统状态"
            else
                "已引导至系统设置页，请按指示完成最后一步",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(OneUiRadius.Large)),
            color = cs.primary,
            contentColor = cs.onPrimary,
            onClick = { NovaSuccess(view.context); onDismiss() },
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("完成", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600))
            }
        }
    }
}

@Composable
private fun ReadyView(
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
    val candidatesSafeCount = candidates.count { it.risk == FreezeRisk.SAFE }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.BlockGap),
            ) {
                Text(
                    text = "${candidatesSafeCount} 个长期未用 · 共 ${allApps.size} 个应用",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            if (!usagePermissionGranted || !shizukuAvailable || !engineAvailable) {
                Spacer(Modifier.height(OneUiSpacing.CardInner))
                NoticeBanner(
                    usagePermissionGranted = usagePermissionGranted,
                    shizukuAvailable = shizukuAvailable,
                    engineAvailable = engineAvailable,
                    onGrantShizuku = onGrantShizuku,
                    onRetry = onRetry,
                )
            }

            Spacer(Modifier.height(OneUiSpacing.CardInner))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OneUiSpacing.BlockGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Chip(
                    text = if (selected.size == candidatesSafeCount && candidatesSafeCount > 0) "全不选" else "全选",
                    onClick = {
                        onSelectAll(!(selected.size == candidatesSafeCount && candidatesSafeCount > 0))
                    },
                )
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                Chip(
                    text = if (includeSystem) "✓ 含系统" else "含系统应用",
                    selected = includeSystem,
                    onClick = { onIncludeSystemChange(!includeSystem) },
                )
            }

            Spacer(Modifier.height(OneUiSpacing.CardGap))

            if (candidates.isEmpty()) {
                EmptyList(
                    usagePermissionGranted = usagePermissionGranted,
                    shizukuAvailable = shizukuAvailable,
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(items = candidates, key = { it.app.packageName }) { c ->
                        key(c.app.packageName) {
                            AnimatedVisibility(
                                visible = true,
                                enter = fadeIn(MotionTokens.standard) +
                                    slideInVertically(
                                        animationSpec = MotionTokens.standardOffset,
                                        initialOffsetY = { it / 10 },
                                    ),
                            ) {
                                Column {
                                    CandidateRow(
                                        candidate = c,
                                        selected = c.app.packageName in selected,
                                        frozen = c.app.packageName in frozen,
                                        onToggle = { onToggleSelected(c.app.packageName) },
                                        onUnfreeze = { onUnfreeze(c.app.packageName) },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(120.dp))
                    }
                }
            }
        }

        if (selected.isNotEmpty()) {
            GlassPanel(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(OneUiRadius.Large)),
                        color = cs.primary,
                        contentColor = cs.onPrimary,
                        onClick = onBatchFreeze,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "冻结 ${selected.size} 个应用",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NoticeBanner(
    usagePermissionGranted: Boolean,
    shizukuAvailable: Boolean,
    engineAvailable: Boolean,
    onGrantShizuku: () -> Unit,
    onRetry: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val view = LocalView.current
    Column {
        if (!usagePermissionGranted) {
            NoticeRow(
                text = "未授予「使用情况访问」，无法识别长期未用应用",
                action = "授权",
                tone = colors.riskCaution,
                onClick = { NovaTap(view); onRetry() },
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }
        if (!shizukuAvailable) {
            NoticeRow(
                text = "未配置 Shizuku，将无法一键冻结（会引导至系统设置）",
                action = "配置",
                tone = colors.riskCaution,
                onClick = { NovaTap(view); onGrantShizuku() },
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
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
            .padding(horizontal = OneUiSpacing.BlockGap)
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(tone.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = action,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = tone,
        )
    }
}

@Composable
private fun EmptyList(usagePermissionGranted: Boolean, shizukuAvailable: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    ) {
        Text(
            text = when {
                !usagePermissionGranted -> "无法识别长期未用应用"
                !shizukuAvailable -> "未配置 Shizuku，无法冻结"
                else -> "暂未发现值得冻结的应用"
            },
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = when {
                !usagePermissionGranted -> "请在系统设置授予「使用情况访问」"
                !shizukuAvailable -> "配置 Shizuku 后即可一键冻结"
                else -> "你常用的应用都很活跃"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun CandidateRow(
    candidate: FreezeCandidate,
    selected: Boolean,
    frozen: Boolean,
    onToggle: () -> Unit,
    onUnfreeze: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable { if (frozen) onUnfreeze() else onToggle() }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(OneUiRadius.Medium))
                .background(cs.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = candidate.app.label.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
                color = cs.primary,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = candidate.app.label,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    frozen -> "已冻结"
                    candidate.daysUnused == null -> "无使用记录"
                    else -> "${candidate.daysUnused} 天未用"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (frozen) cs.primary else cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (candidate.risk != FreezeRisk.SAFE) {
            RiskChip(risk = candidate.risk)
            Spacer(Modifier.width(12.dp))
        }
        if (frozen) {
            Surface(
                color = cs.primary.copy(alpha = 0.12f),
                modifier = Modifier
                    .clip(RoundedCornerShape(9999.dp))
                    .clickable { onUnfreeze() },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AcUnit,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "解冻",
                        style = MaterialTheme.typography.labelMedium,
                        color = cs.primary,
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (selected) cs.primary else cs.onSurface.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = cs.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskChip(risk: FreezeRisk) {
    val cs = MaterialTheme.colorScheme
    val (label, color) = when (risk) {
        FreezeRisk.SAFE -> return
        FreezeRisk.CAUTION -> "需确认" to cs.tertiary
        FreezeRisk.RISKY -> "有风险" to NovaCareTheme.colors.riskRisky
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.clip(RoundedCornerShape(9999.dp)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
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
private fun Chip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(9999.dp))
            .clickable { onClick() },
        color = if (selected) cs.primary.copy(alpha = 0.12f) else cs.onSurface.copy(alpha = 0.06f),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) cs.primary else cs.onSurface,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

// (end of file)
