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
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SwapVert
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

// =========================================================================
// 首页 = 功能导航中枢（不再做"健康仪表盘"，那是 OneUI Health 的活）。
//
// 这一版是**真正**的重新设计：
//   - 不再假装显示 Health Score（那个我算得不算准）
//   - 每个 tile 一句话能做什么 + 当前状态摘要（不是装饰）
//   - 每个 tile 都可点跳到真屏幕
//   - 不再有假按钮、没"按了没反应"的情况
//
// 【UI/UX 原则 / OneUI 9 单主张】
//   - 一屏一屏单一主张：6 个功能入口 + 当前设备状态摘要 + 内核就绪
//   - 大圆角 16dp squircle（OneUI 9 实测）
//   - 不重复底栏（底栏 5 tab 这里不再列）
//   - 副文不超过 OneUI 31 字原则（中文按 31 字符约束）
// =========================================================================

/** 功能 tile 定义 —— 单一职责，可点跳转 */
private data class ModuleTile(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,        // OneUI：≤31 字
    val route: ModuleRoute,
)

private enum class ModuleRoute {
    CLEAN, FREEZE, AUTOMATION, STORAGE, MEMORY, BATTERY, ASSISTANT, SCREENTIME, TRAFFIC,
}

private val ModuleTiles = listOf(
    ModuleTile(
        icon = Icons.Outlined.CleaningServices,
        title = "清理",
        subtitle = "扫描缓存 / 大文件 / 残留",
        route = ModuleRoute.CLEAN,
    ),
    ModuleTile(
        icon = Icons.Outlined.AcUnit,
        title = "冻结",
        subtitle = "停用长期未用的应用",
        route = ModuleRoute.FREEZE,
    ),
    ModuleTile(
        icon = Icons.Outlined.Speed,
        title = "自动化",
        subtitle = "规则触发动作",
        route = ModuleRoute.AUTOMATION,
    ),
    ModuleTile(
        icon = Icons.Outlined.Storage,
        title = "存储守护",
        subtitle = "按类别分析存储占用",
        route = ModuleRoute.STORAGE,
    ),
    ModuleTile(
        icon = Icons.Outlined.Memory,
        title = "内存守护",
        subtitle = "应用内存占用排行",
        route = ModuleRoute.MEMORY,
    ),
    ModuleTile(
        icon = Icons.Outlined.BatteryStd,
        title = "电池守护",
        subtitle = "应用耗电排行",
        route = ModuleRoute.BATTERY,
    ),
    ModuleTile(
        icon = Icons.Outlined.AccessTime,
        title = "屏幕时长",
        subtitle = "最近 24h 应用前台时长",
        route = ModuleRoute.SCREENTIME,
    ),
    ModuleTile(
        icon = Icons.Outlined.SwapVert,
        title = "流量",
        subtitle = "自启动以来上下行汇总",
        route = ModuleRoute.TRAFFIC,
    ),
)

/** AI 助手是 placement 内单独的**提醒入口** —— 不是聊天（AI 没真接通） */
private val AssistantTile = ModuleTile(
    icon = Icons.Outlined.AutoAwesome,
    title = "本地建议",
    subtitle = "基于规则的清理建议（无云端）",
    route = ModuleRoute.ASSISTANT,
)

/** 首页可跳转的目的地 —— 与底栏 tab 路由一致 */
enum class HomeDestination { CLEAN, FREEZE, AUTOMATION }

/** 守护中心三模块 —— HomeScreen 也可达（点 metric bar 直接进） */
enum class GuardianHub { STORAGE, MEMORY, BATTERY }

/**
 * v0.20.0 新增的两个下钻入口：屏幕时长 / 流量。
 * 它们与守护中心类似 —— 不进底栏，由首页 tile 进入；返回键回首页。
 */
enum class InsightHub { SCREENTIME, TRAFFIC }

@Composable
fun HomeScreen(
    onOpenAssistant: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
    onOpenGuardian: (GuardianHub) -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    onOpenTraffic: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val engineAvailable by viewModel.engineAvailable.collectAsStateWithLifecycle()
    val overview by viewModel.overview.collectAsStateWithLifecycle()
    val missing by viewModel.missing.collectAsStateWithLifecycle()

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
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "NovaCare",
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (engineAvailable) NovaCareTheme.colors.healthGood
                                else NovaCareTheme.colors.riskCaution
                            ),
                    )
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OneUiSpacing.BlockGap),
            ) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))

                // 1. 设备状态摘要（只读，不假装可点）
                DeviceStatusSummary(overview = overview)

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // 2. 功能模块网格（OneUI 8.5 Settings 用法：分组列表 + chevron）
                SectionLabel("功能")

                ModuleTiles.forEachIndexed { index, tile ->
                    ModuleListItem(
                        tile = tile,
                        onClick = {
                            NovaTap(view)
                            when (tile.route) {
                                ModuleRoute.CLEAN -> onNavigate(HomeDestination.CLEAN)
                                ModuleRoute.FREEZE -> onNavigate(HomeDestination.FREEZE)
                                ModuleRoute.AUTOMATION -> onNavigate(HomeDestination.AUTOMATION)
                                ModuleRoute.STORAGE -> onOpenGuardian(GuardianHub.STORAGE)
                                ModuleRoute.MEMORY -> onOpenGuardian(GuardianHub.MEMORY)
                                ModuleRoute.BATTERY -> onOpenGuardian(GuardianHub.BATTERY)
                                ModuleRoute.ASSISTANT -> onOpenAssistant()
                                ModuleRoute.SCREENTIME -> onOpenScreenTime()
                                ModuleRoute.TRAFFIC -> onOpenTraffic()
                            }
                        },
                    )
                    if (index < ModuleTiles.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .padding(start = 56.dp),
                        )
                    }
                }

                Spacer(Modifier.height(OneUiSpacing.BlockGap))

                // 3. 本地建议（不是云端 AI —— 老实说）
                SectionLabel("建议")
                ModuleListItem(
                    tile = AssistantTile,
                    onClick = {
                        NovaTap(view)
                        onOpenAssistant()
                    },
                )

                // 4. 降级说明 —— 引擎 / 权限缺失，每项带下一步动作
                if (!engineAvailable || missing.isNotEmpty()) {
                    Spacer(Modifier.height(OneUiSpacing.BlockGap))
                    SectionLabel("设置")
                    DegradedList(
                        engineAvailable = engineAvailable,
                        missing = missing,
                        onRetry = { viewModel.refresh() },
                        onGrant = { capability ->
                            NovaTap(view)
                            viewModel.grant(capability)
                        },
                    )
                }

                Spacer(Modifier.height(160.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = OneUiSpacing.SectionTitleGap),
    )
}

/** OneUI 9 Settings 列表行：图标 + 主标题 + 副标题 + chevron */
@Composable
private fun ModuleListItem(
    tile: ModuleTile,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Small))
            .clickable(onClick = onClick)
            .padding(vertical = OneUiSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(OneUiRadius.Small))
                .background(cs.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tile.icon,
                contentDescription = null,
                tint = cs.primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(OneUiSpacing.CardInner))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tile.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
                color = cs.onSurface,
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
            contentDescription = null,
            tint = cs.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** 设备状态摘要 —— 只读（不假装可点） */
@Composable
private fun DeviceStatusSummary(overview: HomeViewModel.Overview?) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(cs.surfaceContainer)
            .padding(OneUiSpacing.CardInner),
    ) {
        Text(
            text = "当前状态",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))

        if (overview == null) {
            Text(
                text = "尚未读取设备读数",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            return
        }

        StatusLine(
            label = "存储",
            value = if (overview.totalBytes > 0L) {
                "已用 ${overview.usedBytes.toReadableMB()} / ${overview.totalBytes.toReadableMB()}"
            } else "未获取",
        )
        StatusLine(
            label = "内存",
            value = if (overview.memoryTotalBytes > 0L) {
                "可用 ${overview.memoryAvailableBytes.toReadableMB()} / ${overview.memoryTotalBytes.toReadableMB()}"
            } else "未获取",
        )
        StatusLine(
            label = "电池",
            value = if (overview.batteryPercent > 0) {
                "${overview.batteryPercent}%" + if (overview.batteryTemperatureTenths > 0) {
                    " · ${overview.batteryTemperatureTenths / 10f}°C"
                } else ""
            } else "未获取",
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurface,
        )
    }
}

/** 降级说明 —— 引擎 / 权限，按 OneUI 列表行 + chevron 模式 */
@Composable
private fun DegradedList(
    engineAvailable: Boolean,
    missing: List<com.novacare.core.system.MissingCapability>,
    onRetry: () -> Unit,
    onGrant: (com.novacare.core.system.MissingCapability) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    if (!engineAvailable) {
        DegradedItem(
            title = "内核不可用",
            subtitle = "清理 / 守护的深度分析会受影响",
            accent = colors.riskCaution,
            actionLabel = "重试",
            onClick = onRetry,
        )
    }

    missing.forEach { capability ->
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        val (title, why) = when (capability) {
            com.novacare.core.system.MissingCapability.USAGE_STATS ->
                "使用情况访问" to "停用清单依赖此权限"
            com.novacare.core.system.MissingCapability.ALL_FILES ->
                "所有文件访问" to "扫描深度受限"
            com.novacare.core.system.MissingCapability.NOTIFICATIONS ->
                "通知权限" to "后台任务结果无提醒"
        }
        DegradedItem(
            title = title,
            subtitle = why,
            accent = cs.primary,
            actionLabel = "去开启",
            onClick = { onGrant(capability) },
        )
    }
}

@Composable
private fun DegradedItem(
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    actionLabel: String,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Small))
            .background(accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = OneUiSpacing.CardInner, vertical = OneUiSpacing.CardGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = accent,
        )
    }
}

// 紧凑字节显示 —— OneUI 8.5 用 MB / GB，不写大数
private fun Long.toReadableMB(): String {
    if (this <= 0L) return "0 B"
    val mb = this / 1024L / 1024L
    return when {
        mb < 1L -> "$this B"
        mb < 1024L -> "$mb MB"
        else -> "${"%.1f".format(mb / 1024f)} GB"
    }
}