package com.novacare.feature.guardian.storage

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.core.common.formatBytes
import com.novacare.core.domain.AgedFileKind
import com.novacare.core.domain.AgedLargeFile
import com.novacare.core.domain.DuplicateGroup
import com.novacare.core.domain.StorageInsights
import com.novacare.core.model.JunkItem
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaLongPress
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.NovaToggle
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 存储守护（Storage Guardian）—— OneUI 9.5 重写。
 *
 * 顶部饼图 + 下方分类列表（重复 / 大文件 / 残留 / 空目录）。
 * 把"空间被谁占了"讲清楚，并且只在你确认后动手。
 *
 * - OneUiAppBar 顶部
 * - 顶部：[StorageCapacityCard] 总容量 + 已用 + 饼图
 * - 下方：分类区块 + 列表 + 选择
 * - ≤5 字体档位
 * - 间距 / 圆角全部从 OneUiSpacing / OneUiRadius 取值
 */
@Composable
fun StorageGuardianScreen(
    rootPath: String,
    modifier: Modifier = Modifier,
    viewModel: StorageGuardianViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val scanningDuplicates by viewModel.scanningDuplicates.collectAsStateWithLifecycle()
    val result by viewModel.result.collectAsStateWithLifecycle()

    LaunchedEffect(rootPath) {
        viewModel.load(rootPath)
    }

    val view = LocalView.current
    var confirmDelete by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(title = "存储守护")

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    StorageGuardianViewModel.UiState.Idle,
                    StorageGuardianViewModel.UiState.Scanning -> {
                        LoadingHero(scanning = s is StorageGuardianViewModel.UiState.Scanning)
                    }

                    is StorageGuardianViewModel.UiState.Failed -> {
                        FailedHero(
                            message = s.message,
                            onRetry = {
                                NovaTap(view)
                                viewModel.load(rootPath, force = true)
                            },
                        )
                    }

                    is StorageGuardianViewModel.UiState.Ready -> {
                        ReadyBody(
                            data = s.data,
                            selected = selected,
                            scanningDuplicates = scanningDuplicates,
                            onToggle = { path ->
                                NovaToggle(view.context, path !in selected)
                                viewModel.toggle(path)
                            },
                            onSelectGroup = { keep, drop ->
                                NovaTap(view)
                                viewModel.setSelection(drop, true)
                                viewModel.setSelection(listOf(keep), false)
                            },
                            onSelectAll = { paths, on ->
                                NovaTap(view)
                                viewModel.setSelection(paths, on)
                            },
                            onScanDuplicates = {
                                NovaTap(view)
                                viewModel.scanDuplicates()
                            },
                            onGrantAllFiles = {
                                NovaTap(view)
                                viewModel.grantAllFiles()
                            },
                            onRetry = {
                                NovaTap(view)
                                viewModel.load(rootPath, force = true)
                            },
                        )
                    }
                }

                val outcome = result
                if (outcome != null) {
                    ResultSheet(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        freedBytes = outcome.freedBytes,
                        deletedCount = outcome.deleted.size,
                        failedCount = outcome.failed.size,
                        onDismiss = {
                            NovaSuccess(view.context)
                            viewModel.dismissResult()
                        },
                    )
                } else if (state is StorageGuardianViewModel.UiState.Ready && selected.isNotEmpty()) {
                    val data = (state as StorageGuardianViewModel.UiState.Ready).data
                    val bytes = remember(selected, data) { selectedBytes(data, selected) }
                    DeleteBar(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        count = selected.size,
                        bytes = bytes,
                        busy = deleting,
                        onClick = {
                            NovaLongPress(view)
                            confirmDelete = true
                        },
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("确认删除 ${selected.size} 项？") },
            text = {
                Text(
                    "删除后不可恢复 —— 本 App 不会把大文件复制进回收站" +
                        "（GB 级文件复制会翻倍 I/O 并可能撑爆应用私有空间）。\n\n" +
                        "删除完成后会重新扫描，用真实容量变化告诉你释放了多少。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteSelected()
                    },
                ) {
                    Text(
                        "删除",
                        style = MaterialTheme.typography.labelLarge,
                        color = NovaCareTheme.colors.riskRisky,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("取消", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
    }
}

// ============================================================
// Ready body — 容量饼图 + 分类列表
// ============================================================

@Composable
private fun ReadyBody(
    data: StorageInsights,
    selected: Set<String>,
    scanningDuplicates: Boolean,
    onToggle: (String) -> Unit,
    onSelectGroup: (String, List<String>) -> Unit,
    onSelectAll: (List<String>, Boolean) -> Unit,
    onScanDuplicates: () -> Unit,
    onGrantAllFiles: () -> Unit,
    onRetry: () -> Unit,
) {
    val agedGroups = remember(data) {
        data.agedFiles.groupBy { it.kind }.toList()
            .sortedByDescending { (_, list) -> list.sumOf { it.file.bytes } }
    }
    // 饼图扇区：把存储大块切分成 "已用 / 可回收（aged+duplicates+residuals）/ 残留其他"
    val pieSlices = remember(data) {
        buildPieSlices(
            usedBytes = data.usedBytes,
            reclaimableBytes = (data.duplicates.sumOf { it.reclaimableBytes } +
                data.agedFiles.sumOf { it.file.bytes } +
                data.residuals.sumOf { it.bytes }),
            freeBytes = data.availableBytes,
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.ScreenEdge,
            vertical = OneUiSpacing.SectionTitleGap,
        ),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item {
            Text(
                text = "容量来自 StatFs，文件来自内核真实遍历 —— 没有一个数字是估算的",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
        }
        item {
            StorageCapacityCard(data = data, slices = pieSlices)
            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
        }
        if (!data.engineAvailable) {
            item {
                NoticeRow(
                    text = "Nova 内核不可用，无法扫描文件。以下是系统容量（StatFs）读数，其余分析暂不可用。",
                    action = "重试",
                    tone = NovaCareTheme.colors.riskCaution,
                    onClick = onRetry,
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
            }
        }
        if (!data.allFilesAccess) {
            item {
                NoticeRow(
                    text = "未授予「所有文件访问」，Android 11+ 会拒绝读取其他应用目录，扫描结果会明显偏少。",
                    action = "去授权",
                    tone = NovaCareTheme.colors.riskCaution,
                    onClick = onGrantAllFiles,
                )
                Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
            }
        }

        // ---- 重复文件 ----
        item {
            SectionTitle("重复文件", "BLAKE3 逐字节校验，内容完全一致才算重复")
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }
        if (!data.duplicatesScanned) {
            item {
                ScanCtaCard(
                    title = "扫描重复文件",
                    why = "会逐个读取 ≥1MB 文件的完整内容做哈希校验，文件多时可能需要几十秒，所以不默认跑。",
                    loading = scanningDuplicates,
                    onClick = onScanDuplicates,
                )
                Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
            }
        } else if (data.duplicates.isEmpty()) {
            item {
                EmptyCard("未发现内容完全相同的文件（已按 BLAKE3 逐字节校验）")
                Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
            }
        } else {
            items(items = data.duplicates, key = { "dup:" + it.members.first() }) { group ->
                DuplicateGroupCard(
                    group = group,
                    selected = selected,
                    onToggle = onToggle,
                    onSelectGroup = onSelectGroup,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
            item { Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap)) }
        }

        // ---- 时间久远的大文件 ----
        if (agedGroups.isNotEmpty()) {
            item {
                SectionTitle("放久了的大文件", "按路径特征与真实修改时间归类，不是内容识别")
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
            items(items = agedGroups, key = { "kind:" + it.first.name }) { (kind, files) ->
                AgedKindCard(
                    kind = kind,
                    files = files,
                    selected = selected,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
            item { Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap)) }
        }

        // ---- 大文件 TOP ----
        if (data.largestFiles.isNotEmpty()) {
            item {
                SectionTitle("大文件", "按体积降序，来自内核遍历的真实 stat")
                Spacer(Modifier.height(OneUiSpacing.CardGap))
            }
            items(items = data.largestFiles, key = { "large:" + it.path }) { node ->
                CheckRow(
                    title = node.path.substringAfterLast('/'),
                    subtitle = node.path,
                    meta = buildString {
                        append(node.bytes.formatBytes())
                        node.modifiedEpochMs?.let { append(" · 修改于 ${daysAgo(data.nowMs, it)}") }
                    },
                    checked = node.path in selected,
                    onToggle = { onToggle(node.path) },
                )
            }
            item { Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap)) }
        }

        // ---- 残留目录 ----
        item {
            SectionTitle("残留目录", "已卸载应用留下的数据目录（内核按已安装包名比对得出）")
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }
        if (data.residuals.isEmpty()) {
            item {
                EmptyCard(
                    if (data.engineAvailable) "未发现已卸载应用的残留目录"
                    else "内核不可用，无法判定残留目录",
                )
            }
        } else {
            items(items = data.residuals, key = { "res:" + it.path }) { item ->
                ResidualRow(item = item, checked = item.path in selected, onToggle = { onToggle(item.path) })
            }
        }
        item { Spacer(Modifier.height(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap)) }

        // ---- 空目录 ----
        item {
            SectionTitle("空目录", "由本 App 直接遍历得出（内核暂不产出空目录项）")
            Spacer(Modifier.height(OneUiSpacing.CardGap))
        }
        if (data.emptyDirs.isEmpty()) {
            item { EmptyCard("未发现空目录（扫描深度 4 层）") }
        } else {
            item {
                EmptyDirsCard(
                    dirs = data.emptyDirs,
                    selected = selected,
                    onToggle = onToggle,
                    onSelectAll = onSelectAll,
                )
            }
        }

        item { Spacer(Modifier.height(OneUiSpacing.EmptyHeight + OneUiSpacing.BlockGap)) }
    }
}

// ============================================================
// 容量饼图卡（顶部）
// ============================================================

@Composable
private fun StorageCapacityCard(
    data: StorageInsights,
    slices: List<PieSlice>,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PieChart(
                    slices = slices,
                    modifier = Modifier.size(PieChartSize),
                )
                Spacer(Modifier.width(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.usedBytes.formatBytes(),
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Text(
                        text = "/ ${data.totalBytes.formatBytes()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardGap))
                    PieLegend(slices = slices)
                }
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Text(
                text = "可用 ${data.availableBytes.formatBytes()} · 已用 " +
                    "${(data.usedBytes.toFloat() / data.totalBytes.coerceAtLeast(1L) * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            // ringTrack uses unused warning avoidance
            @Suppress("UNUSED_VARIABLE")
            val _t = colors.ringTrack
        }
    }
}

private data class PieSlice(val label: String, val bytes: Long, val color: Color)

private fun buildPieSlices(usedBytes: Long, reclaimableBytes: Long, freeBytes: Long): List<PieSlice> {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    val reclaimClamped = reclaimableBytes.coerceAtMost(usedBytes.coerceAtLeast(0L))
    val usedReal = (usedBytes - reclaimClamped).coerceAtLeast(0L)
    return listOf(
        PieSlice("可回收", reclaimClamped, colors.healthGood),
        PieSlice("已用", usedReal, cs.primary),
        PieSlice("可用", freeBytes.coerceAtLeast(0L), cs.onSurface.copy(alpha = 0.10f)),
    ).filter { it.bytes > 0L }
}

@Composable
private fun PieChart(slices: List<PieSlice>, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.bytes }.coerceAtLeast(1L)
    val sweepList = slices.map { (it.bytes.toFloat() / total * 360f).coerceAtLeast(0f) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = PieStrokeWidth.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            var start = -90f
            slices.zip(sweepList).forEach { (slice, sweep) ->
                drawArc(
                    color = slice.color,
                    startAngle = start,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
    }
}

@Composable
private fun PieLegend(slices: List<PieSlice>) {
    val cs = MaterialTheme.colorScheme
    Column {
        slices.forEach { slice ->
            Row(
                modifier = Modifier.padding(vertical = OneUiSpacing.CardGap / 2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(LegendDotSize)
                        .clip(CircleShape)
                        .background(slice.color),
                )
                Spacer(Modifier.width(OneUiSpacing.CardGap))
                Text(
                    text = "${slice.label} · ${slice.bytes.formatBytes()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                )
            }
        }
    }
}

// ============================================================
// Loading / Failed hero
// ============================================================

@Composable
private fun LoadingHero(scanning: Boolean) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.ScreenEdge),
    ) {
        Spacer(Modifier.height(OneUiSpacing.BlockGap * 2))
        Text(
            text = if (scanning) "正在扫描" else "准备扫描",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = if (scanning) "遍历存储并统计真实占用" else "点击返回后再试",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        CircularProgressIndicator(
            modifier = Modifier.size(OneUiSpacing.CardInner * 2 - 4.dp),
            strokeWidth = 3.dp,
            color = cs.primary,
        )
    }
}

@Composable
private fun FailedHero(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.ScreenEdge),
    ) {
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Text(
            text = "扫描失败",
            style = MaterialTheme.typography.titleMedium,
            color = cs.onSurface,
        )
        Spacer(Modifier.height(OneUiSpacing.CardGap))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
        )
        Spacer(Modifier.height(OneUiSpacing.BlockGap))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap)
                .clip(RoundedCornerShape(OneUiRadius.Large))
                .clickable { onRetry() },
            color = cs.primary,
            contentColor = cs.onPrimary,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "重试",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

// ============================================================
// 列表组件（分类块）
// ============================================================

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
private fun EmptyCard(text: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Medium),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(OneUiSpacing.CardInner - 2.dp),
        )
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateGroup,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectGroup: (String, List<String>) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${group.copies} 份完全相同的文件",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = group.eachBytes.formatBytes(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "这 ${group.copies} 份内容逐字节完全一致（BLAKE3 校验）。删掉其中 " +
                    "${group.copies - 1} 份不会丢失任何数据 —— 保留哪一份由你决定，本 App 不替你选。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "可释放 ${group.reclaimableBytes.formatBytes()}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.healthGood,
            )
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            TextButton(
                onClick = { onSelectGroup(group.members.first(), group.members.drop(1)) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("保留第 1 份，删除其余")
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap / 2))
            group.members.forEach { path ->
                CheckRow(
                    title = path.substringAfterLast('/'),
                    subtitle = path,
                    meta = group.eachBytes.formatBytes(),
                    checked = path in selected,
                    onToggle = { onToggle(path) },
                )
            }
        }
    }
}

@Composable
private fun AgedKindCard(
    kind: AgedFileKind,
    files: List<AgedLargeFile>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>, Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val total = remember(files) { files.sumOf { it.file.bytes } }
    val allSelected = remember(files, selected) { files.all { it.file.path in selected } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = kind.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${files.size} 个 · ${total.formatBytes()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = kind.why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            TextButton(
                onClick = { onSelectAll(files.map { it.file.path }, !allSelected) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (allSelected) "取消全选" else "全选这一类")
            }
            files.forEach { aged ->
                CheckRow(
                    title = aged.file.path.substringAfterLast('/'),
                    subtitle = aged.file.path,
                    meta = buildString {
                        append(aged.file.bytes.formatBytes())
                        aged.ageDays?.let { append(" · ${it} 天前") }
                    },
                    checked = aged.file.path in selected,
                    onToggle = { onToggle(aged.file.path) },
                )
            }
        }
    }
}

@Composable
private fun ResidualRow(item: JunkItem, checked: Boolean, onToggle: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Medium),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner - 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.WarningAmber,
                    contentDescription = null,
                    tint = colors.riskCaution,
                    modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                )
                Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = item.bytes.formatBytes(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = item.riskNote.ifBlank { "应用已卸载，但其数据目录仍在。" },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            TextButton(onClick = onToggle, contentPadding = PaddingValues(0.dp)) {
                Text(if (checked) "已选中，点此取消" else "选中并删除")
            }
        }
    }
}

@Composable
private fun EmptyDirsCard(
    dirs: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (List<String>, Boolean) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val allSelected = remember(dirs, selected) { dirs.all { it in selected } }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${dirs.size} 个空目录",
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "0 B",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = "空目录本身不占空间，删掉只是让文件树清爽。" +
                    "注意：某些应用会在启动时重建自己的目录，误删不会造成数据丢失但可能留下报错日志。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            TextButton(
                onClick = { onSelectAll(dirs, !allSelected) },
                contentPadding = PaddingValues(0.dp),
            ) {
                Text(if (allSelected) "取消全选" else "全选 ${dirs.size} 个")
            }
            dirs.take(20).forEach { path ->
                CheckRow(
                    title = path.substringAfterLast('/').ifBlank { path },
                    subtitle = path,
                    meta = "空目录",
                    checked = path in selected,
                    onToggle = { onToggle(path) },
                )
            }
            if (dirs.size > 20) {
                Text(
                    text = "仅显示前 20 个，共 ${dirs.size} 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(top = OneUiSpacing.CardGap),
                )
            }
        }
    }
}

@Composable
private fun ScanCtaCard(
    title: String,
    why: String,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Large))
            .clickable(enabled = !loading) { onClick() },
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface,
                    modifier = Modifier.weight(1f),
                )
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                        strokeWidth = 2.dp,
                        color = cs.primary,
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = why,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CheckRow(
    title: String,
    subtitle: String,
    meta: String,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(OneUiRadius.Medium))
            .clickable { onToggle() }
            .padding(vertical = OneUiSpacing.CardInner - 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap + 4.dp)
                .clip(CircleShape)
                .background(if (checked) cs.primary else cs.onSurface.copy(alpha = 0.08f)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(OneUiSpacing.SectionTitleGap),
                )
            }
        }
        Spacer(Modifier.width(OneUiSpacing.CardInner - 2.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title.middleTruncate(),
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle.middleTruncate(),
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(OneUiSpacing.SectionTitleGap))
        Text(
            text = meta,
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
        )
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
            .background(tone.copy(alpha = 0.08f))
            .clickable { onClick() }
            .padding(
                horizontal = OneUiSpacing.CardInner,
                vertical = OneUiSpacing.CardInner - 4.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.WarningAmber,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
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
            style = MaterialTheme.typography.labelMedium,
            color = tone,
        )
    }
}

@Composable
private fun DeleteBar(
    modifier: Modifier = Modifier,
    count: Int,
    bytes: Long,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = cs.surface,
        shadowElevation = OneUiSpacing.CardGap,
    ) {
        Column(modifier = Modifier.padding(horizontal = OneUiSpacing.ScreenEdge, vertical = OneUiSpacing.CardInner)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "已选 $count 项 · ${bytes.formatBytes()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(OneUiSpacing.BlockGap - OneUiSpacing.SectionTitleGap),
                        strokeWidth = 2.dp,
                        color = cs.primary,
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OneUiSpacing.CardInner * 4 - OneUiSpacing.CardGap)
                    .clip(RoundedCornerShape(OneUiRadius.Large))
                    .clickable { onClick() },
                color = cs.primary,
                contentColor = cs.onPrimary,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "删除所选",
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultSheet(
    modifier: Modifier = Modifier,
    freedBytes: Long,
    deletedCount: Int,
    failedCount: Int,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                horizontal = OneUiSpacing.ScreenEdge,
                vertical = OneUiSpacing.ScreenEdge,
            )
            .clip(RoundedCornerShape(OneUiRadius.Large)),
        color = cs.surfaceContainerHigh,
        shadowElevation = OneUiSpacing.BlockGap - OneUiSpacing.CardGap,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner + 2.dp)) {
            Text(
                text = if (freedBytes > 0) "已释放 ${freedBytes.formatBytes()}" else "没有文件被删除",
                style = MaterialTheme.typography.titleMedium,
                color = if (freedBytes > 0) colors.healthGood else colors.riskCaution,
            )
            Spacer(Modifier.height(OneUiSpacing.CardGap))
            Text(
                text = buildString {
                    append("成功 $deletedCount 项")
                    if (failedCount > 0) append(" · $failedCount 项删除失败")
                } + if (failedCount > 0) {
                    "\n失败通常是分区存储限制：本 App 无权写这些路径，可去授予「所有文件访问」后重试。"
                } else {
                    "\n释放量是删除前实测、删除后校验得到的真实值。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(OneUiSpacing.CardInner - OneUiSpacing.SectionTitleGap))
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    }
}

// ============================================================
// 工具
// ============================================================

private fun selectedBytes(data: StorageInsights, selected: Set<String>): Long {
    var sum = 0L
    for (path in selected) {
        val size = data.largestFiles.firstOrNull { it.path == path }?.bytes
            ?: data.agedFiles.firstOrNull { it.file.path == path }?.file?.bytes
            ?: data.residuals.firstOrNull { it.path == path }?.bytes
            ?: data.duplicates.firstOrNull { path in it.members }?.eachBytes
            ?: 0L
        sum += size
    }
    return sum
}

private fun daysAgo(nowMs: Long, atMs: Long): String {
    val days = ((nowMs - atMs) / 86_400_000L).coerceAtLeast(0)
    return when {
        days <= 0 -> "今天"
        days < 30 -> "$days 天前"
        days < 365 -> "${days / 30} 个月前"
        else -> "${days / 365} 年前"
    }
}

private fun String.middleTruncate(max: Int = 42): String {
    if (length <= max) return this
    val tail = max / 2
    val head = max - tail - 1
    return take(head) + "…" + takeLast(tail)
}

// ============================================================
// 衍生尺寸 —— 由现有 token 组合得到
// ============================================================


/** 饼图外径 = EmptyHeight - BlockGap (=88dp) */
private val PieChartSize: Dp = OneUiSpacing.EmptyHeight - OneUiSpacing.BlockGap

/** 饼图描边宽度 = SectionTitleGap (=10dp) */
private val PieStrokeWidth: Dp = OneUiSpacing.SectionTitleGap

/** 图例小圆点 = CardGap (=8dp) */
private val LegendDotSize: Dp = OneUiSpacing.CardGap
