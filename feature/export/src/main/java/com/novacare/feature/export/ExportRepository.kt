package com.novacare.feature.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.novacare.core.data.HistoryDao
import com.novacare.core.data.ScanHistoryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 导出仓库（v0.21.0 新增）
 *
 * 数据源：HistoryDao（清理历史 / 冻结历史 / 电池历史）
 * 目标文件：Downloads/NovaCare-YYYYMMDD.csv
 *
 * 实现说明：
 *   - 走 MediaStore.Downloads（API 29+）—— 不需要 WRITE_EXTERNAL_STORAGE 权限
 *   - 低版本回退到 Environment.getExternalStoragePublicDirectory()
 *
 * CSV 列：
 *   epoch_ms, kind, freed_bytes, item_count
 *
 * 注意：kind 字段直接用字符串（CLEAN / FREEZE / BATTERY），不做翻译，
 * 让用户能在 Excel 里直接 filter。
 */
@Singleton
class ExportRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val historyDao: HistoryDao,
) {

    /**
     * 预览数据：返回 (csv 文本, 行数)。不写文件，仅生成内容供 UI 展示。
     */
    suspend fun preview(limit: Int = 200): PreviewData = withContext(Dispatchers.IO) {
        val entries = historyDao.recent().take(limit)
        val csv = formatCsv(entries)
        PreviewData(
            csv = csv,
            rowCount = entries.size,
            truncated = entries.size == limit,
        )
    }

    /**
     * 把最近的 N 条历史写到 Downloads/NovaCare-YYYYMMDD.csv
     * @return 文件的 URI + 可读的展示路径
     */
    suspend fun exportToDownloads(limit: Int = 500): ExportResult = withContext(Dispatchers.IO) {
        val entries = historyDao.recent().take(limit)
        val csv = formatCsv(entries)
        val fileName = "NovaCare-${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())}.csv"

        val uri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeViaMediaStore(fileName, csv)
        } else {
            writeToPublicDownloadsLegacy(fileName, csv)
        }

        ExportResult(
            uri = uri,
            fileName = fileName,
            rowCount = entries.size,
        )
    }

    private fun writeViaMediaStore(fileName: String, csv: String): Uri {
        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "text/csv")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values)
            ?: error("MediaStore 写入失败：$collection")
        try {
            resolver.openOutputStream(uri)?.use { os ->
                os.write(csv.toByteArray(Charsets.UTF_8))
                os.flush()
            } ?: error("无法打开输出流：$uri")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } catch (e: Throwable) {
            // 失败回滚
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun writeToPublicDownloadsLegacy(fileName: String, csv: String): Uri {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) dir.mkdirs()
        val file = java.io.File(dir, fileName)
        file.writeText(csv, Charsets.UTF_8)
        return Uri.fromFile(file)
    }

    private fun formatCsv(entries: List<ScanHistoryEntity>): String = buildString {
        // 表头
        appendLine("epoch_ms,kind,freed_bytes,item_count,iso_time")
        val isoFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        for (e in entries) {
            // 注意：CSV 不做引号转义 —— kind/freed 都是字符串/数字，不会含逗号
            append(e.epochMs)
            append(',')
            append(e.kind)
            append(',')
            append(e.freedBytes)
            append(',')
            append(e.itemCount)
            append(',')
            append(isoFmt.format(Date(e.epochMs)))
            appendLine()
        }
    }

    data class PreviewData(
        val csv: String,
        val rowCount: Int,
        val truncated: Boolean,
    )

    data class ExportResult(
        val uri: Uri,
        val fileName: String,
        val rowCount: Int,
    )
}
