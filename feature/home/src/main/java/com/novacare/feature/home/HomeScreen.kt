package com.novacare.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.novacare.core.model.HealthDimension
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.ui.designsystem.AiOrb
import com.novacare.ui.designsystem.GlassPanel
import com.novacare.ui.designsystem.MotionTokens
import com.novacare.ui.designsystem.NovaCareColors
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 首页可跳转的目的地 —— 只列**首页有权引导去**的页。
 *
 * 刻意不包含 SETTINGS / AUTOMATION：它们各自已有稳定的底栏入口，
 * 而在首页再抄一遍会让人分不清"这两处是不是同一件事"。
 */
enum class HomeDestination { CLEAN, FREEZE }

/**
 * 首页 —— 只读的「设备健康总览 + 导航枢纽」。
 *
 * ============================================================
 * 【这一版改的是什么】
 * 上一版把"可清理 X GB + 一键释放"挪走了，但**没有建立信息架构**：
 *   - 「清理 / 冻结 / 自动化」三张入口卡跟底栏一字不差，
 *     同一屏出现两组一模一样的导航，用户得自己猜它们是不是同一回事；
 *   - 健康分拆了四个维度，其中「存储」归清理页、「应用」归冻结页，
 *     却没告诉用户"这一项低 → 该去哪处理"；
 *   - 扫描失败时永远停在"正在读取…"。
 *
 * 【现在的铁律】**底栏 tab 不在首页出现第二次。**
 * 例外只有两种值得破例的情况：
 *   1. 助手不在底栏，首页是它唯一的合法入口 → 保留一张卡；
 *   2. 健康维度低于阈值且真有依据（hints 里有话可说）→ 该行才出现跳转。
 * 一切没触发条件的导航入口都不存在。这样"首页是不是又在复读底栏"的问题消失。
 * ============================================================
 *
 * 结构（自上而下）：
 *   1. 顶条          内核就绪状态 + 时间
 *   2. 健康 Hero     总分 + 结论 + 评分依据 + 各维度（低分项可跳转）
 *   3. 设备状态      存储 / 内存 / 电池 三行只读读数
 *   4. AI 助手       唯一常驻入口
 *   5. 降级说明      扫描失败 / 内核不可用 / 权限缺失，全部带最短修复动作
 */
@Composable
fun HomeScreen(
    onOpenAssistant: () -> Unit,
    onNavigate: (HomeDestination) -> Unit,
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

            // ---- 2 ~ 4. 主体（按装载状态分支，失败也要说清楚） ----
            when (loadState) {
                HomeLoadState.Loading -> ReadingHero()

                is HomeLoadState.Failed -> FailedHero(
                    message = (loadState as HomeLoadState.Failed).message,
                    onRetry = {
                        NovaTap(view)
                        viewModel.refresh()
                    },
                )

                HomeLoadState.Ready -> {
                    HealthHero(
                        score = score,
                        basis = basis,
                        hints = hints,
                        onNavigate = { destination ->
                            NovaTap(view)
                            onNavigate(destination)
                        },
                    )

                    Spacer(Modifier.height(24.dp))

                    DeviceStatusCard(overview = overview)

                    Spacer(Modifier.height(24.dp))

                    AssistantEntry {
                        NovaTap(view)
                        onOpenAssistant()
                    }
                }
            }

            // ---- 5. 降级说明（引擎 / 权限） ----
            DegradedSection(
                engineAvailable = engineAvailable,
                missing = missing,
                onRetry = { viewModel.refresh() },
                onGrant = { capability ->
                    NovaTap(view)
                    viewModel.grant(capability)
                },
            )

            Spacer(Modifier.height(160.dp))
        }
    }
}

// ============================================================
// 1. 顶条
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
// 2. 健康 Hero —— 单一主张：设备现在有多健康，以及哪一项该去处理
// ============================================================
@Composable
private fun HealthHero(
    score: HealthScore?,
    basis: String,
    hints: Map<HealthDimension, String>,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    // 维度为空 = 一个都评不出来，此时不给分数，只给结论
    val scored = score?.takeIf { it.dimensions.isNotEmpty() }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AiOrb(size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "设备健康",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = scored?.total?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.W200),
                color = scored?.let { scoreColor(it.total, colors) } ?: cs.onSurface,
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

        if (basis.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = basis,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }

        if (scored != null) {
            Spacer(Modifier.height(16.dp))
            scored.dimensions.forEachIndexed { index, dim ->
                key(dim.dimension) {
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(MotionTokens.standard) +
                            slideInVertically(
                                animationSpec = MotionTokens.standardOffset,
                                initialOffsetY = { (index + 1) * 14 },
                            ),
                    ) {
                        DimensionRow(
                            dim = dim,
                            hint = hints[dim.dimension],
                            onNavigate = onNavigate,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DimensionRow(
    dim: DimensionScore,
    hint: String?,
    onNavigate: (HomeDestination) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val fraction = (dim.score.coerceIn(0, 100)) / 100f

    // 只有「确实偏低」且「真有依据」才变成可跳转的行。
    // 全绿时不出现任何箭头 —— 免得首页变成底栏的复读机。
    val destination = destinationFor(dim.dimension)
    val actionable = hint != null && destination != null && dim.score < GOOD_THRESHOLD

    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 6.dp)
        .let { base ->
            if (actionable) {
                base.clip(RoundedCornerShape(10.dp)).clickable { onNavigate(destination!!) }
            } else {
                base
            }
        }

    Column(modifier = rowModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
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
            if (actionable) {
                Text(
                    text = "处理 →",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                    color = cs.primary,
                )
            }
        }

        // 可处理的行显示「为什么去」，其余沿用评分器给的一句话结论。
        // 别在有理由却不可点时显示理由 —— 看起来像能按却按不动。
        val caption = if (actionable) hint!! else dim.summary
        Spacer(Modifier.height(2.dp))
        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(start = 64.dp),
        )
    }
}

/** 哪个维度低了该去哪一页；没有对应落地页的维度只展示读数 */
private fun destinationFor(dimension: HealthDimension): HomeDestination? = when (dimension) {
    HealthDimension.STORAGE -> HomeDestination.CLEAN
    HealthDimension.APP -> HomeDestination.FREEZE
    HealthDimension.MEMORY, HealthDimension.BATTERY -> null
}

private const val GOOD_THRESHOLD = 85

private fun scoreColor(total: Int, colors: NovaCareColors): androidx.compose.ui.graphics.Color = when {
    total >= GOOD_THRESHOLD -> colors.healthGood
    total >= 60 -> colors.riskCaution
    else -> colors.riskRisky
}

// ============================================================
// 3. 设备状态 —— 三行只读读数，不挂任何操作
// ============================================================
@Composable
private fun DeviceStatusCard(overview: HomeViewModel.Overview?) {
    val cs = MaterialTheme.colorScheme

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "设备状态",
                style = MaterialTheme.typography.titleSmall,
                color = cs.onSurface,
            )

            if (overview == null) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "尚无读数",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                return@Column
            }

            Spacer(Modifier.height(14.dp))

            // 存储：有总量才有百分比，否则如实说读不到
            StorageRow(overview = overview)

            Spacer(Modifier.height(14.dp))

            MeterRow(
                label = "内存",
                value = if (overview.memoryTotalBytes > 0L) {
                    "可用 ${overview.memoryAvailableBytes.formatBytes()} / ${overview.memoryTotalBytes.formatBytes()}"
                } else {
                    "未获取"
                },
                fraction = if (overview.memoryTotalBytes > 0L) {
                    (overview.memoryAvailableBytes.toFloat() / overview.memoryTotalBytes)
                        .coerceIn(0f, 1f)
                } else {
                    0f
                },
                measurable = overview.memoryTotalBytes > 0L,
            )

            Spacer(Modifier.height(14.dp))

            MeterRow(
                label = "电池",
                value = buildString {
                    append("${overview.batteryPercent}%")
                    if (overview.batteryTemperatureTenths > 0) {
                        append(" · ${overview.batteryTemperatureTenths / 10f}°C")
                    }
                    append(" · ${batteryHealthLabel(overview.batteryHealth)}")
                },
                fraction = (overview.batteryPercent / 100f).coerceIn(0f, 1f),
                measurable = overview.batteryPercent > 0,
            )
        }
    }
}

@Composable
private fun StorageRow(overview: HomeViewModel.Overview) {
    val cs = MaterialTheme.colorScheme
    val measurable = overview.totalBytes > 0L
    val fraction = if (measurable) {
        (overview.usedBytes.toFloat() / overview.totalBytes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "存储",
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (measurable) {
                        "已用 ${overview.usedBytes.formatBytes()} / ${overview.totalBytes.formatBytes()}"
                    } else {
                        "未获取"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (measurable) "剩余 ${overview.freeBytes.formatBytes()}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.onSurface.copy(alpha = 0.08f)),
            ) {
                if (measurable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.primary),
                    )
                }
            }
        }
    }
}

@Composable
private fun MeterRow(
    label: String,
    value: String,
    fraction: Float,
    measurable: Boolean,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(cs.onSurface.copy(alpha = 0.08f)),
            ) {
                if (measurable) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(cs.primary),
                    )
                }
            }
        }
    }
}

private fun batteryHealthLabel(raw: String): String = when (raw) {
    "good" -> "状态正常"
    "overheat" -> "过热"
    "dead" -> "已损坏"
    "over_voltage" -> "电压过高"
    "cold" -> "温度过低"
    else -> "系统未给出健康结论"
}

// ============================================================
// 4. AI 助手 —— 首页唯一常驻入口（助手不在底栏）
// ============================================================
@Composable
private fun AssistantEntry(onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme

    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        tint = cs.primary,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AiOrb(size = 30.dp)
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
}

// ============================================================
// 装载状态的分支视图
// ============================================================
@Composable
private fun ReadingHero() {
    val cs = MaterialTheme.colorScheme
    Text(
        text = "设备健康",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "正在读取设备状态",
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W300),
        color = cs.onSurface,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "存储、内存、电池三项读数来自系统，不预估、不填充。",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    Text(
        text = "设备健康",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "读不到",
        style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.W300),
        color = colors.riskCaution,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = "本次扫描没有拿到任何一项读数，所以这里不给分数，也不用上一次的结果凑数。",
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = cs.onSurfaceVariant,
    )
    Spacer(Modifier.height(16.dp))
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.onSurface.copy(alpha = 0.06f))
            .clickable { onRetry() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = "重试",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = cs.primary,
        )
    }
}

// ============================================================
// 5. 降级说明 —— 引擎 / 权限，每项都给最短修复动作
// ============================================================
@Composable
private fun DegradedSection(
    engineAvailable: Boolean,
    missing: List<MissingCapability>,
    onRetry: () -> Unit,
    onGrant: (MissingCapability) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors

    if (!engineAvailable) {
        Spacer(Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.riskCaution.copy(alpha = 0.08f))
                .clickable { onRetry() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "内核不可用",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.riskCaution,
                )
                Text(
                    text = "存储与内存的读数仍取自系统；垃圾识别、回收量估算不可用，不会显示这部分数字。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "重试",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
                color = colors.riskCaution,
            )
        }
    }

    missing.forEach { capability ->
        Spacer(Modifier.height(8.dp))
        PermissionRow(capability = capability, onGrant = { onGrant(capability) })
    }
}

@Composable
private fun PermissionRow(
    capability: MissingCapability,
    onGrant: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val (title, why) = when (capability) {
        MissingCapability.USAGE_STATS ->
            "使用情况访问" to "缺少它就无法判断哪些应用长期没被打开，应用维度不计入健康分"
        MissingCapability.ALL_FILES ->
            "所有文件访问" to "缺少它只能扫到部分目录，清理页的结果会偏少"
        MissingCapability.NOTIFICATIONS ->
            "通知权限" to "长任务的执行结果无法在通知里提醒"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.riskCaution.copy(alpha = 0.06f))
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
        Spacer(Modifier.width(10.dp))
        Text(
            text = "去开启",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.W600),
            color = cs.primary,
        )
    }
}

// (end of file)
