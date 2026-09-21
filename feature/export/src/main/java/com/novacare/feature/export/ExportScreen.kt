package com.novacare.feature.export

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Refresh
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.OneUiAppBar
import com.novacare.ui.designsystem.OneUiRadius
import com.novacare.ui.designsystem.OneUiSpacing

/**
 * 数据导出屏（v0.21.0 新增）
 *
 * UI 单一主张：把 HistoryDao 里的清理 / 冻结 / 电池历史导出为 CSV。
 *
 * 流程：
 *   - 加载：ExportRepository.preview() 生成预览 CSV（不写文件）
 *   - 预览：UI 展示行数 + 前若干行（让用户知道将导出什么）
 *   - 导出：点「导出 CSV」→ 写 Downloads/NovaCare-YYYYMMDD.csv → 触发 ACTION_SEND 选应用分享
 *
 * 真实原则：
 *   - 没数据时显示「暂无历史记录」+ 说明为什么（"扫一次清理 / 冻结就有了"），不编数据
 *   - 导出失败时不假装成功，弹错误说明
 */
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            OneUiAppBar(
                title = "数据导出",
                onBack = {
                    NovaTap(view)
                    onBack()
                },
                actions = {
                    TextButton(onClick = {
                        NovaTap(view)
                        viewModel.loadPreview()
                    }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "刷新",
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("刷新", style = MaterialTheme.typography.labelLarge)
                    }
                },
            )

            when (val s = state) {
                ExportViewModel.UiState.Loading -> LoadingState()
                is ExportViewModel.UiState.Preview -> PreviewState(
                    csv = s.csv,
                    rowCount = s.rowCount,
                    truncated = s.truncated,
                    onExport = { NovaTap(view); viewModel.export() },
                )
                is ExportViewModel.UiState.Exported -> ExportedState(
                    fileName = s.fileName,
                    rowCount = s.rowCount,
                    onExport = { NovaTap(view); viewModel.export() },
                )
                is ExportViewModel.UiState.Failed -> FailedState(
                    message = s.message,
                    onRetry = { NovaTap(view); viewModel.export() },
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PreviewState(
    csv: String,
    rowCount: Int,
    truncated: Boolean,
    onExport: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = OneUiSpacing.BlockGap,
            vertical = OneUiSpacing.CardGap,
        ),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OneUiRadius.Large),
                color = cs.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                    Text(
                        text = if (rowCount == 0) "暂无历史记录" else "预览 · $rowCount 行",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = buildString {
                            append("将导出近 500 条清理 / 冻结 / 电池历史。\n")
                            append("格式：epoch_ms,kind,freed_bytes,item_count,iso_time\n")
                            append("目标位置：Downloads/NovaCare-YYYYMMDD.csv\n\n")
                            append("导出后会弹出分享选单 —— 你可以选择：\n")
                            append("• 通过邮件 / 微信 / 飞书发给自己\n")
                            append("• 保存到云盘（OneDrive / Google Drive 等）\n")
                            append("• 任意支持 ACTION_SEND text/csv 的应用")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardInner))
                    if (truncated) {
                        Text(
                            text = "预览仅展示前 200 行；导出时取最近 500 行。",
                            style = MaterialTheme.typography.bodySmall,
                            color = NovaCareTheme.colors.riskCaution,
                        )
                        Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    }
                    ExportButton(
                        enabled = rowCount > 0,
                        onClick = onExport,
                    )
                }
            }
            Spacer(Modifier.height(OneUiSpacing.BlockGap))
        }

        if (rowCount > 0) {
            item {
                SectionLabel("预览前 50 行")
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
            }
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(OneUiRadius.Large),
                    color = cs.surfaceContainer,
                ) {
                    Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                        val previewLines = csv.lineSequence().take(50).toList()
                        previewLines.forEachIndexed { index, line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (index == 0) cs.onSurfaceVariant else cs.onSurface,
                                fontWeight = if (index == 0) FontWeight.W600 else FontWeight.Normal,
                            )
                        }
                        if (csv.lineCount() > 50) {
                            Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                            Text(
                                text = "…共 ${csv.lineCount()} 行（只显示前 50 行）",
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
            }
        } else {
            item {
                EmptyHint()
                Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
            }
        }
    }
}

@Composable
private fun ExportedState(fileName: String, rowCount: Int, onExport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val colors = NovaCareTheme.colors
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = OneUiSpacing.BlockGap, vertical = OneUiSpacing.CardGap),
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(OneUiRadius.Large),
                color = cs.surfaceContainer,
            ) {
                Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = colors.healthGood,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "已导出",
                            style = MaterialTheme.typography.titleMedium,
                            color = cs.onSurface,
                        )
                    }
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = "$fileName · $rowCount 行",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                    Text(
                        text = "文件已写入 Downloads 目录。\n分享选单已自动弹出，可发到任意应用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(OneUiSpacing.CardInner))
                    TextButton(onClick = onExport, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text("再次导出")
                    }
                }
            }
            Spacer(Modifier.height(OneUiSpacing.EmptyHeight))
        }
    }
}

@Composable
private fun FailedState(message: String, onRetry: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = OneUiSpacing.BlockGap),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(OneUiRadius.Large),
            color = cs.errorContainer.copy(alpha = 0.4f),
        ) {
            Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = cs.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "导出失败",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                }
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(OneUiSpacing.SectionTitleGap))
                TextButton(onClick = onRetry, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text("重试")
                }
            }
        }
    }
}

@Composable
private fun EmptyHint() {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(OneUiRadius.Large),
        color = cs.surfaceContainer,
    ) {
        Column(modifier = Modifier.padding(OneUiSpacing.CardInner)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "为什么是空的",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "数据库里还没有清理 / 冻结 / 电池的执行记录。\n" +
                            "去做一次扫描 / 清理 / 冻结，回到这里就能导出历史。\n\n" +
                            "本 App 不写假数据 —— 没有真实记录就是空。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExportButton(enabled: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(OneUiRadius.Medium),
        color = if (enabled) cs.primary else cs.onSurface.copy(alpha = 0.12f),
        contentColor = if (enabled) cs.onPrimary else cs.onSurfaceVariant,
        onClick = onClick,
        enabled = enabled,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Download,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "导出 CSV",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.W600),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.W600),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

/** 行数 = 换行符数 + 1（处理最后一行没换行的情况） */
private fun String.lineCount(): Int = if (isEmpty()) 0 else count { it == '\n' } + 1
