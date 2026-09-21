package com.novacare.feature.guardian.battery

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.domain.AppUsageRank
import com.novacare.core.domain.BatteryInsights
import com.novacare.core.domain.GuardianAction
import com.novacare.core.domain.GuardianAdvice
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 电池守护（Battery Guardian）—— OneUI 9.5 重写。
 *
 * 三条纪律写死在 UI 上：
 *   1. 排行叫"前台时长"，不叫"耗电排行"—— Android 不向第三方提供真实耗电
 *   2. 每条建议都带"为什么"，且给出数据的出处
 *   3. 拿不到的数据（未授权 / 内核不可用）直接说拿不到，不留空白也不编数字
 *
 * v0.21 增量：建议区 + 前台时长排行 + 疑似后台活跃。
 *
 * - OneUiAppBar 顶部
 * - ≤5 字体档位
 * - 间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值
 */
@Composable
fun BatteryGuardianScreen(
    modifier: Modifier = Modifier,
    viewModel: BatteryGuardianViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "电池守护")
            when (val s = state) {
                BatteryGuardianViewModel.UiState.Idle,
                BatteryGuardianViewModel.UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(OneUiSpacing.CardInner * 2 - 4.dp()),
                            strokeWidth = 3.dp(),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                is BatteryGuardianViewModel.UiState.Failed -> {
                    Column(modifier = Modifier.padding(horizontal = OneUiSpacing.ScreenEdge)) {
                        Spacer(Modifier.height(OneUiSpacing.BlockGap))
                        Text(
                            text = "读取失败",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(OneUiSpacing.CardGap))
                        Text(
                            text = s.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is BatteryGuardianViewModel.UiState.Ready -> {
                    ReadyBody(
                        data = s.data,
                        statusLabel = { viewModel.statusLabel(it) },
                        healthLabel = { viewModel.healthLabel(it) },
                        onRunAction = { advice ->
                            NovaTap(view)
                            viewModel.runAction(advice)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadyBody(
    data: BatteryInsights,
    statusLabel: (String) -> String,
    healthLabel: (String) -> String,
    onRunAction: (GuardianAdvice) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.ScreenEdge,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
    ) {
        item {
            Text(
                text = "电量、温度、电压、电流均为系统 BatteryManager 实时读数",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
        }

        item {
            BatteryStatusCard(
                data = data,
                statusLabel = statusLabel,
                healthLabel = healthLabel,
            )
            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
        }

        if (!data.engineAvailable) {
            item {
                NoticeCard(
                    text = "Nova 内核不可用，电池健康评分暂不提供（不显示占位分数）。" +
                        "电量与温度等仍为系统真实读数。",
                    tone = NovaCareTheme.colors.riskCaution,
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
            }
        }

        // ---- 建议 ----
        if (data.advices.isNotEmpty()) {
            item {
                SectionTitle("建议", "每条都写清依据：凭什么给出这个建议")
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
            items(items = data.advices) { advice ->
                AdviceCard(advice = advice, onRun = { onRunAction(advice) })
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
            item { Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap)) }
        }

        // ---- 前台时长排行 ----
        item {
            SectionTitle(
                "前台时长排行 · 近 ${data.windowHours} 小时",
                "这是**使用时长**，不是耗电量 —— 见下方说明",
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }
        item {
            NoticeCard(
                text = "为什么不给「耗电排行」：Android 不向第三方应用提供按应用的真实耗电量" +
                    "（BatteryStats 需要系统签名权限，只有系统设置自己能算）。" +
                    "所以这里只能用前台时长作为代理指标 —— 它相关但不等于耗电，" +
                    "后台下载、推送唤醒这类耗电不会体现在时长里。",
                tone = NovaCareTheme.colors.riskCaution,
            )
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
        }
        if (!data.usagePermissionGranted) {
            item {
                NoticeCard(
                    text = "未授予「使用情况访问」，拿不到前台时长。这是 AppOps 权限，" +
                        "需要你到系统设置页手动开启；本 App 不会用其他数据凑一个假排行。",
                    tone = NovaCareTheme.colors.riskCaution,
                )
            }
        } else if (data.foregroundRanking.isEmpty()) {
            item {
                NoticeCard(
                    text = "近 ${data.windowHours} 小时内系统没有记录到任何应用的前台使用。",
                    tone = NovaCareTheme.colors.riskCaution,
                )
            }
        } else {
            items(items = data.foregroundRanking, key = { it.packageName }) { rank ->
                RankRow(rank = rank)
            }
        }

        // ---- 疑似后台活跃 ----
        if (data.backgroundSuspects.isNotEmpty()) {
            item {
                Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
                SectionTitle(
                    "疑似后台活跃",
                    "推断结论，依据直接写在每条下面 —— 可自行核对",
                )
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
            items(items = data.backgroundSuspects, key = { it.packageName }) { suspect ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(OneUiRadius.Medium),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(OneUiSpacing.CardInner - 2.dp())) {
                        Text(
                            text = suspect.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                        Text(
                            text = suspect.evidence,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
        }

        item { Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap)) }
    }
}

// ============================================================
// 卡：BatteryStatusCard / AdviceCard / RankRow
// ============================================================

@Composable
private fun BatteryStatusCard(
    data: BatteryInsights,
    statusLabel: (String) -> String,
    healthLabel: (String) -> String,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val tone = when {
        data.levelPercent <= 10 -> colors.healthPoor
        data.levelPercent <= 20 -> colors.healthFair
        else -> colors.healthGood
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${data.levelPercent}%",
                    style = MaterialTheme.typography.headlineLarge,
                    color = cs.onSurface,
                )
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                Text(
                    text = statusLabel(data.status),
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone,
                    modifier = Modifier.padding(bottom = 4.dp()),
                )
                if (data.plugged) {
                    Spacer(Modifier.width(OneUiSpacing.CardGap / 2 + 2.dp()))
                    Icon(
                        imageVector = Icons.Outlined.Bolt,
                        contentDescription = null,
                        tint = cs.primary,
                        modifier = Modifier
                            .size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap)
                            .padding(bottom = 2.dp()),
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.CardInner - 4.dp()))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("温度", "%.1f°C".format(data.temperatureCelsius), Modifier.weight(1f))
                MetricCell("电压", "${data.voltageMv} mV", Modifier.weight(1f))
                MetricCell(
                    title = "电流",
                    value = if (data.currentMa == 0) "系统未给出" else "${data.currentMa} mA",
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Row(modifier = Modifier.fillMaxWidth()) {
                MetricCell("电池状态", healthLabel(data.health), Modifier.weight(1f))
                data.engineHealthScore?.let {
                    MetricCell("内核健康评分", "$it / 100", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MetricCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp()))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun AdviceCard(advice: GuardianAdvice, onRun: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val tone = when (advice.tone) {
        GuardianAdvice.Tone.INFO -> cs.primary
        GuardianAdvice.Tone.CAUTION -> colors.riskCaution
        GuardianAdvice.Tone.RISK -> colors.riskRisky
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp())
                        .size(8.dp())
                        .clip(RoundedCornerShape(OneUiRadius.Pill))
                        .background(tone),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = advice.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = advice.why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            val label = advice.actionLabel
            if (advice.action != GuardianAction.NONE && label != null) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                TextButton(
                    onClick = onRun,
                    contentPadding = PaddingValues(0.dp()),
                ) {
                    Text(label)
                    Spacer(Modifier.width(OneUiSpacing.CardGap / 2))
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(OneUiSpacing.CardInner + 2.dp()),
                    )
                }
            }
        }
    }
}

@Composable
private fun RankRow(rank: AppUsageRank) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OneUiSpacing.CardInner - 4.dp()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rank.label,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                maxLines = 1,
            )
            if (rank.packageName != rank.label) {
                Text(
                    text = rank.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
        Text(
            text = formatDuration(rank.foregroundMs),
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun NoticeCard(text: String, tone: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(tone.copy(alpha = 0.08f))
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardInner - 4.dp(),
            ),
    ) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
        )
        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun SectionTitle(title: String, why: String) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.padding(top = OneUiSpacing.SectionTitleGap, bottom = 2.dp())) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(2.dp()))
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "不足 1 分钟"
    val minutes = ms / 60_000
    return when {
        minutes < 60 -> "$minutes 分钟"
        else -> {
            val h = minutes / 60
            val m = minutes % 60
            if (m == 0L) "$h 小时" else "$h 小时 $m 分"
        }
    }
}

private fun Int.dp() = androidx.compose.ui.unit.Dp(this.toFloat())
private fun Double.dp() = androidx.compose.ui.unit.Dp(this.toFloat())

private val BoltIconSize: Dp = OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap
