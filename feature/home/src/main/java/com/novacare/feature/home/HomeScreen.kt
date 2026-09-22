package com.novacare.feature.home

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.model.HealthDimension
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.NovaCareColors
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiListRow
import com.novacare.ui.designsystem.OneUiListSection
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// HomeScreen — 严格按 OneUI 9 真截图重写
//
// 来源：research/oneui9-ref/components_lists_img-01.png + structure_basicstructure_img-01.png
// =========================================================================

enum class HomeDestination { CLEAN, FREEZE, AUTOMATION }

enum class GuardianHub { STORAGE, MEMORY, BATTERY }

@Composable
fun HomeScreen(
    onOpenAssistant: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    onOpenGuardian: (GuardianHub) -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenScreenTime: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenTraffic: () -> Unit = {},
    @Suppress("UNUSED_PARAMETER") onOpenOneClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val loadState by viewModel.loadState.collectAsStateWithLifecycle()
    val score by viewModel.score.collectAsStateWithLifecycle()
    val basis by viewModel.basis.collectAsStateWithLifecycle()
    val hints by viewModel.hints.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshCapabilities()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val view = LocalView.current

    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = OneUiSpacing.BlockGap * 4),
        ) {
            item { OneUiAppBar(title = "NovaCare") }

            when (loadState) {
                HomeLoadState.Loading -> item { HealthHeroPlaceholder() }
                is HomeLoadState.Failed -> item {
                    FailedHero(
                        message = (loadState as HomeLoadState.Failed).message,
                        onRetry = {
                            NovaTap(view)
                            viewModel.refresh()
                        },
                    )
                }
                HomeLoadState.Ready -> {
                    item {
                        HealthHero(
                            score = score,
                            basis = basis,
                            hints = hints,
                            onNavigate = { destination ->
                                NovaTap(view)
                                onNavigate(destination)
                            },
                        )
                    }

                    item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }

                    item { SectionLabel("当前状态") }
                    item {
                        OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                            DeviceStatusRows(overview = overview)
                        }
                    }

                    item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }

                    item { SectionLabel("一键操作") }
                    item {
                        OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                            OneUiListRow(
                                icon = Icons.Outlined.CleaningServices,
                                iconTint = ModuleTints.clean,
                                title = "清理",
                                subtitle = "扫缓存 · 大文件 · 残留",
                                onClick = { NovaTap(view); onNavigate(HomeDestination.CLEAN) },
                                trailing = { ChevronRight() },
                            )
                            OneUiListRow(
                                icon = Icons.Outlined.AcUnit,
                                iconTint = ModuleTints.freeze,
                                title = "冻结",
                                subtitle = "停用长期未用应用",
                                onClick = { NovaTap(view); onNavigate(HomeDestination.FREEZE) },
                                trailing = { ChevronRight() },
                            )
                            OneUiListRow(
                                icon = Icons.Outlined.Storage,
                                iconTint = ModuleTints.storage,
                                title = "存储守护",
                                subtitle = "按类别分析存储占用",
                                onClick = { NovaTap(view); onOpenGuardian(GuardianHub.STORAGE) },
                                trailing = { ChevronRight() },
                            )
                            OneUiListRow(
                                icon = Icons.Outlined.Memory,
                                iconTint = ModuleTints.memory,
                                title = "内存守护",
                                subtitle = "应用内存占用排行",
                                onClick = { NovaTap(view); onOpenGuardian(GuardianHub.MEMORY) },
                                trailing = { ChevronRight() },
                            )
                            OneUiListRow(
                                icon = Icons.Outlined.BatteryStd,
                                iconTint = ModuleTints.battery,
                                title = "电池守护",
                                subtitle = "应用耗电排行",
                                onClick = { NovaTap(view); onOpenGuardian(GuardianHub.BATTERY) },
                                showDivider = false,
                                trailing = { ChevronRight() },
                            )
                        }
                    }

                    item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }

                    item { SectionLabel("自动化与建议") }
                    item {
                        OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                            OneUiListRow(
                                icon = Icons.Outlined.Speed,
                                iconTint = ModuleTints.automation,
                                title = "自动化",
                                subtitle = "规则触发动作",
                                onClick = { NovaTap(view); onNavigate(HomeDestination.AUTOMATION) },
                                trailing = { ChevronRight() },
                            )
                            OneUiListRow(
                                icon = Icons.Outlined.AutoAwesome,
                                iconTint = ModuleTints.assistant,
                                title = "本地建议",
                                subtitle = "基于规则，不连云端",
                                onClick = { NovaTap(view); onOpenAssistant() },
                                showDivider = false,
                                trailing = { ChevronRight() },
                            )
                        }
                    }

                    if (!engineAvailable || missing.isNotEmpty()) {
                        item { Spacer(Modifier.height(OneUiSpacing.BlockGap)) }
                        item { SectionLabel("需要处理") }
                        item {
                            OneUiListSection(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
                                DegradedRows(
                                    engineAvailable = engineAvailable,
                                    missing = missing,
                                    onRetry = { viewModel.refresh() },
                                    onGrant = { capability ->
                                        NovaTap(view)
                                        viewModel.grant(capability)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private object ModuleTints {
    val clean = Color(0xFF2F6FED)
    val freeze = Color(0xFF7B61FF)
    val storage = Color(0xFF00A6A6)
    val memory = Color(0xFFFF8A4C)
    val battery = Color(0xFF1B7A46)
    val automation = Color(0xFFD18BFE)
    val assistant = Color(0xFFE03E8E)
}

@Composable
private fun HealthHero(
    score: HealthScore?,
    basis: String,
    hints: Map<HealthDimension, String>,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val scored = score?.takeIf { it.dimensions.isNotEmpty() }

    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = scored?.total?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.W200),
                color = scored?.let { scoreColor(it.total, colors) } ?: cs.onSurface,
            )
            Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
            Text(
                text = score?.verdict ?: "正在评估",
                style = MaterialTheme.typography.titleMedium,
                color = cs.onSurfaceVariant,
            )
        }
        if (basis.isNotBlank()) {
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = basis,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        if (scored != null) {
            Spacer(Modifier.height(OneUiSpacing.CardInner))
            scored.dimensions.forEach { dim ->
                DimensionRow(dim = dim, hint = hints[dim.dimension], onNavigate = onNavigate)
            }
        }
    }
}

@Composable
private fun HealthHeroPlaceholder() {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "正在读取设备状态",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Text(
            text = "存储 / 内存 / 电池 来自系统，不预估。",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.BlockGap)) {
        Text(
            text = "读不到",
            style = MaterialTheme.typography.titleMedium,
            color = cs.error,
        )
        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.CardInner))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(OneUiRadius.Small))
                .background(cs.primary.copy(alpha = 0.10f))
                .clickable { onRetry() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(text = "重试", color = cs.primary, style = MaterialTheme.typography.labelMedium)
        }
    }
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
        imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun DeviceStatusRows(overview: HomeViewModel.Overview?) {
    if (overview == null) {
        OneUiListRow(
            icon = Icons.Outlined.Memory,
            iconTint = MaterialTheme.colorScheme.onSurfaceVariant,
            title = "尚无读数",
            subtitle = "授权后下次进入自动加载",
            trailing = null,
        )
        return
    }
    OneUiListRow(
        icon = Icons.Outlined.Storage,
        iconTint = ModuleTints.storage,
        title = "存储",
        subtitle = if (overview.totalBytes > 0L) {
            "已用 ${overview.usedBytes.formatBytes()} / ${overview.totalBytes.formatBytes()}"
        } else "未获取",
        trailing = null,
    )
    OneUiListRow(
        icon = Icons.Outlined.Memory,
        iconTint = ModuleTints.memory,
        title = "内存",
        subtitle = if (overview.memoryTotalBytes > 0L) {
            "可用 ${overview.memoryAvailableBytes.formatBytes()} / ${overview.memoryTotalBytes.formatBytes()}"
        } else "未获取",
        trailing = null,
    )
    OneUiListRow(
        icon = Icons.Outlined.BatteryStd,
        iconTint = ModuleTints.battery,
        title = "电池",
        subtitle = if (overview.batteryPercent > 0) {
            "${overview.batteryPercent}%" + if (overview.batteryTemperatureTenths > 0) {
                                    " · ${overview.batteryTemperatureTenths / 10f}°C"
                                } else ""
        } else "未获取",
        showDivider = false,
        trailing = null,
    )
}

@Composable
private fun DimensionRow(
    dim: com.novacare.core.model.DimensionScore,
    hint: String?,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val fraction = (dim.score.coerceIn(0, 100)) / 100f
    val destination = destinationFor(dim.dimension)
    val actionable = hint != null && destination != null && dim.score < GOOD_THRESHOLD

    val rowMod = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(OneUiRadius.Small))
        .let { base -> if (actionable) base.clickable { destination?.let(onNavigate) } else base }
        .padding(vertical = 8.dp)

    Column(modifier = rowMod) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = dim.dimension.displayName,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
                modifier = Modifier.width(64.dp),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.onSurface.copy(alpha = 0.06f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(scoreColor(dim.score, colors)),
                )
            }
            Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
            Text(
                text = "${dim.score}",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (actionable) hint!! else dim.summary,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 64.dp),
        )
    }
}

@Composable
private fun DegradedRows(
    engineAvailable: Boolean,
    missing: List<MissingCapability>,
    onRetry: () -> Unit,
    onGrant: (MissingCapability) -> Unit,
) {
    if (!engineAvailable) {
        OneUiListRow(
            icon = Icons.Outlined.Memory,
            iconTint = MaterialTheme.colorScheme.error,
            title = "内核不可用",
            subtitle = "扫描无法运行",
            onClick = onRetry,
            trailing = null,
        )
    }
    missing.forEach { capability ->
        OneUiListRow(
            icon = Icons.Outlined.AutoAwesome,
            iconTint = MaterialTheme.colorScheme.primary,
            title = when (capability) {
                MissingCapability.USAGE_STATS -> "使用情况访问"
                MissingCapability.ALL_FILES -> "所有文件访问"
                MissingCapability.NOTIFICATIONS -> "通知权限"
            },
            subtitle = when (capability) {
                MissingCapability.USAGE_STATS -> "冻结清单依赖此权限"
                MissingCapability.ALL_FILES -> "扫描深度受限"
                MissingCapability.NOTIFICATIONS -> "后台任务结果无提醒"
            },
            onClick = { onGrant(capability) },
            showDivider = missing.indexOf(capability) < missing.lastIndex || !engineAvailable,
            trailing = null,
        )
    }
}

private fun destinationFor(dimension: HealthDimension): HomeDestination? = when (dimension) {
    HealthDimension.STORAGE -> HomeDestination.CLEAN
    HealthDimension.APP -> HomeDestination.FREEZE
    HealthDimension.MEMORY, HealthDimension.BATTERY -> null
}

private const val GOOD_THRESHOLD = 85

private fun scoreColor(total: Int, colors: NovaCareColors): Color = when {
    total >= GOOD_THRESHOLD -> colors.healthGood
    total >= 60 -> colors.riskCaution
    else -> colors.riskRisky
}