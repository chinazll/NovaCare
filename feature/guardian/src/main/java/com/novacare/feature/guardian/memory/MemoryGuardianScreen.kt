package com.novacare.feature.guardian.memory

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
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.system.MemoryProcessSource
import com.novacare.core.system.importanceLabel
import com.novacare.core.system.importanceWhy
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaLongPress
import com.novacare.ui.designsystem.NovaSuccess
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 内存守护（Memory Guardian）—— OneUI 9.5 重写。
 *
 * 顶部是每 3 秒刷新一次的真实内存读数，下面是进程占用排行，
 * 最底部是"一键回收"—— 但那个按钮旁边必须写着它到底能做什么、不能做什么。
 *
 * v0.21 增量：保留 /proc/self RSS（SelfRssCard）+ /proc/meminfo 细分账目
 * （MemInfoBreakdownCard）。这是本模块最难也最重要的部分：**不谎报能力**。
 * 市面上同类产品靠"一键加速"骗点击，我们不这么做。
 *
 * - OneUiAppBar 顶部
 * - ≤5 字体档位
 * - 间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值
 */
@Composable
fun MemoryGuardianScreen(
    modifier: Modifier = Modifier,
    viewModel: MemoryGuardianViewModel = hiltViewModel(),
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val release by viewModel.release.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "内存守护")

            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = OneUiSpacing.ScreenEdge,
                        vertical = OneUiSpacing.SectionTitleGap,
                    ),
                ) {
                    item {
                        Text(
                            text = "每 3 秒读一次系统真实内存账目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
                    }

                    val snap = snapshot
                    if (snap == null) {
                        item { LoadingBox() }
                    } else {
                        item {
                            MemoryGauge(overview = snap.overview)
                            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
                        }
                        item {
                            SelfRssCard(selfRssBytes = snap.overview.selfRssBytes)
                            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
                        }
                        if (snap.overview.memInfoAvailable) {
                            item {
                                MemInfoBreakdownCard(overview = snap.overview)
                                Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
                            }
                        }
                        item {
                            BoundaryCard(
                                selfCacheBytes = snap.selfCacheBytes,
                                onOpenApplicationSettings = {
                                    NovaTap(view)
                                    viewModel.openApplicationSettings()
                                },
                            )
                            Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
                        }
                        if (snap.limitedVisibility) {
                            item {
                                NoticeBlock(
                                    text = "系统只向第三方提供 ${snap.processes.size} 个进程 —— " +
                                        "Android 5.0 起完整进程列表不再开放，这不是本 App 的限制。",
                                    tone = NovaCareTheme.colors.riskCaution,
                                )
                                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
                            }
                        }
                        if (snap.processes.isEmpty()) {
                            item {
                                NoticeBlock(
                                    text = "系统未返回任何进程信息（部分 ROM 会屏蔽该接口），" +
                                        "因此无法给出占用排行。",
                                    tone = NovaCareTheme.colors.riskCaution,
                                )
                                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
                            }
                        }
                        if (snap.processes.isNotEmpty()) {
                            item {
                                SectionTitle("进程占用", "PSS 由 ActivityManager.getProcessMemoryInfo 实测")
                                Spacer(Modifier.height(OneUiSpacing.CardGap))
                            }
                            items(items = snap.processes, key = { it.pid }) { proc ->
                                ProcessRow(
                                    process = proc,
                                    label = viewModel.labelFor(proc),
                                    onOpenDetails = { pkg ->
                                        NovaTap(view)
                                        viewModel.openAppDetails(pkg)
                                    },
                                )
                            }
                        }
                        item { Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap)) }
                    }
                }

                ReleaseBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    release = release,
                    onRelease = {
                        NovaLongPress(view)
                        viewModel.release()
                    },
                    onDismiss = {
                        NovaSuccess(view.context)
                        viewModel.dismissRelease()
                    },
                )
            }
        }
    }
}

// ============================================================
// 卡：MemoryGauge / SelfRssCard / MemInfoBreakdownCard / BoundaryCard
// ============================================================

@Composable
private fun MemoryGauge(overview: MemoryProcessSource.MemoryOverview) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val ratio = overview.availableRatio
    val usedRatio = if (ratio == null) 0f else (1f - ratio).coerceIn(0f, 1f)
    val tone = when {
        ratio == null -> colors.healthFair
        overview.lowMemory -> colors.healthPoor
        ratio < 0.15f -> colors.healthPoor
        ratio < 0.3f -> colors.healthFair
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
                    text = overview.availableBytes.formatBytes(),
                    style = MaterialTheme.typography.titleLarge,
                    color = cs.onSurface,
                )
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                Text(
                    text = "可用 / 共 ${overview.totalBytes.formatBytes()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardInner - 4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ProgressBarHeight)
                    .clip(CircleShape)
                    .background(colors.ringTrack),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(usedRatio)
                        .height(ProgressBarHeight)
                        .clip(CircleShape)
                        .background(tone),
                )
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = buildString {
                    append("已用 ${overview.usedBytes.formatBytes()}")
                    overview.cachedBytes?.let { append(" · 其中可回收缓存 ${it.formatBytes()}") }
                    append(" · 低内存水位 ${overview.thresholdBytes.formatBytes()}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (overview.lowMemory) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = "系统已判定为低内存状态（MemoryInfo.lowMemory = true），" +
                        "此时系统会主动回收后台进程 —— 这通常意味着你开着的东西确实多。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.riskCaution,
                )
            }
        }
    }
}

@Composable
private fun SelfRssCard(selfRssBytes: Long) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Memory,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "本应用 RSS（/proc/self/status）",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = if (selfRssBytes > 0L) selfRssBytes.formatBytes() else "系统未提供 VmRSS",
                style = MaterialTheme.typography.titleLarge,
                color = cs.onSurface,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = buildString {
                    append("常驻物理内存（VmRSS，单位 bytes），不含被 swap 的部分。")
                    if (selfRssBytes <= 0L) {
                        append("本设备无法读取 /proc/self/status —— 极少数 ROM 沙箱化导致，不影响其他指标。")
                    } else {
                        append("回收本应用缓存只能减小 cacheDir（磁盘占用），不影响 RSS（物理内存占用）。")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemInfoBreakdownCard(overview: MemoryProcessSource.MemoryOverview) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Storage,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "/proc/meminfo 真实账目",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))

            MemInfoRow("MemTotal", overview.memTotalBytes, "内核可见的全部物理内存")
            MemInfoRow("MemAvailable", overview.memAvailableBytes, "内核估算的「还能给应用用的」")
            MemInfoRow("Buffers", overview.buffersBytes, "文件系统缓冲（可回收）")
            MemInfoRow("Cached", overview.cachedRawBytes, "页缓存（可回收）")
            if (overview.hasSwap) {
                MemInfoRow("SwapTotal", overview.swapTotalBytes, "交换分区总大小")
                MemInfoRow("SwapFree", overview.swapFreeBytes, "剩余交换空间")
            } else {
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                Text(
                    text = "本设备未配置 swap 区域（SwapTotal = 0）—— 不展示对应行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "单位 bytes；读自 /proc/meminfo（无需权限）。" +
                    "MemAvailable 与顶部「可用」口径不同 —— 内核视角 vs 系统视角。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MemInfoRow(label: String, bytes: Long, hint: String) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OneUiSpacing.CardGap / 2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
            )
            Text(
                text = hint,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
        Text(
            text = if (bytes > 0L) bytes.formatBytes() else "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (bytes > 0L) cs.onSurface else cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun BoundaryCard(
    selfCacheBytes: Long,
    onOpenApplicationSettings: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = "一键回收能做到什么",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "能做的：\n" +
                    "• 清理本应用自己的缓存（当前 ${selfCacheBytes.formatBytes()}）\n" +
                    "• 对本进程触发一次 GC 建议（不保证立即生效）\n" +
                    "• 实测并报告回收前后的可用内存\n\n" +
                    "做不到的（Android 的系统限制，不是本 App 偷懒）：\n" +
                    "• 强杀其他应用的进程（自 Android 5.0 起 killBackgroundProcesses 对" +
                    "他人生效不了，强行停止需要系统签名权限）\n" +
                    "• 真正禁止自启动 / 真正锁定后台\n" +
                    "• 清理其他应用的缓存目录（应用沙箱 + 分区存储）\n\n" +
                    "所以这里不会出现「加速 50%」这种数字 —— 那不是真的。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            TextButton(
                onClick = onOpenApplicationSettings,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("去系统「应用管理」自行处理")
            }
        }
    }
}

// ============================================================
// 列表行 / 工具件
// ============================================================

@Composable
private fun ProcessRow(
    process: MemoryProcessSource.ProcessMemory,
    label: String,
    onOpenDetails: (String) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable { expanded = !expanded }
            .padding(vertical = OneUiSpacing.SectionTitleGap),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (process.isSelf) "$label（本应用）" else label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = cs.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = importanceLabel(process.importance),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
            Text(
                text = if (process.pssBytes < 0) "系统未给出" else process.pssBytes.formatBytes(),
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant,
            )
        }
        if (expanded) {
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = importanceWhy(process.importance),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            if (process.packages.isNotEmpty()) {
                Spacer(Modifier.height(OneUiSpacing.CardGap))
                Text(
                    text = "包名：${process.packages.joinToString(", ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
            val pkg = process.packages.firstOrNull()
            if (pkg != null && !process.isSelf) {
                Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
                TextButton(
                    onClick = { onOpenDetails(pkg) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("打开它的系统详情页")
                }
                Text(
                    text = "「强行停止」只能由你在系统页里点 —— 第三方应用没有这个权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun NoticeBlock(text: String, tone: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .background(tone.copy(alpha = 0.08f))
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardInner - 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
    Column(modifier = Modifier.padding(top = OneUiSpacing.SectionTitleGap, bottom = OneUiSpacing.CardGap / 2)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = why,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingBox() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = OneUiSpacing.EmptyHeight - OneUiSpacing.BlockGap),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(OneUiSpacing.CardInner * 2 - 4.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ReleaseBar(
    modifier: Modifier = Modifier,
    release: MemoryGuardianViewModel.ReleaseResult,
    onRelease: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cs.surface,
        shadowElevation = OneUiSpacing.CardGap,
    ) {
        Column(modifier = Modifier.padding(horizontal = OneUiSpacing.ScreenEdge, vertical = OneUiSpacing.CardInner)) {
            when (release) {
                is MemoryGuardianViewModel.ReleaseResult.Done -> {
                    val delta = release.availableAfterBytes - release.availableBeforeBytes
                    val increased = delta > 0
                    Text(
                        text = if (increased) "可用内存增加 ${delta.formatBytes()}"
                        else "可用内存没有增加（${delta.formatBytes()}）",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (increased) colors.healthGood else colors.healthFair,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                    Text(
                        text = buildString {
                            append("本应用缓存释放 ${release.freedSelfCacheBytes.formatBytes()}")
                            if (!increased) {
                                append("\n这是正常结果：Android 的内存回收由系统统一调度，")
                                append("第三方应用无法强制系统释放其他进程的内存。")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    TextButton(onClick = onDismiss) { Text("知道了") }
                }

                MemoryGuardianViewModel.ReleaseResult.Working -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                            strokeWidth = 2.dp,
                            color = cs.primary,
                        )
                        Spacer(Modifier.width(OneUiSpacing.CardInner - 2.dp))
                        Text(
                            text = "正在清理本应用缓存并重新测量…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant,
                        )
                    }
                }

                MemoryGuardianViewModel.ReleaseResult.Idle -> {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap)
                            .clip(RoundedCornerShape(OneUiRadius.Large))
                            .clickable { onRelease() },
                        color = cs.primary,
                        contentColor = cs.onPrimary,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CleaningServices,
                                contentDescription = null,
                                modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                            )
                            Spacer(Modifier.width(OneUiSpacing.CardGap))
                            Text(
                                text = "回收我能回收的",
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                    Text(
                        text = "按钮文案为什么这么保守：因为它真的只能回收本应用的缓存。" +
                            "强杀别人的进程，Android 不允许。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val ProgressBarHeight: Dp = OneUiSpacing.CardGap
