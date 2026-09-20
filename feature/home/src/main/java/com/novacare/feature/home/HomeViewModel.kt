package com.novacare.feature.home

import android.app.Application
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.ai.SuggestionEngine
import com.novacare.core.common.formatBytes
import com.novacare.core.data.HistoryDao
import com.novacare.core.domain.DeviceSnapshot
import com.novacare.core.domain.DeviceSnapshotCache
import com.novacare.core.domain.HealthScoreUseCase
import com.novacare.core.domain.ScanDeviceUseCase
import com.novacare.core.engine.NovaEngine
import com.novacare.core.model.HealthScore
import com.novacare.core.system.MissingCapability
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 首页 —— 设备健康总览（不再承担清理动作）
 *
 * ============================================================
 * 【信息架构重做 · 首页与清理 tab 去重】
 *
 * 上一版首页 = 「清理功能的另一个入口」：Hero 显示「可清理 X GB」+ 一键释放，
 * 与「清理」tab 完全重复。用户点开首页和点开清理 tab 看到的是同一件事。
 *
 * 参照三星 Good Lock / Good Guardians / Sam Helper 的设计：
 *   - Good Lock：主界面只做「总览 + 导航」，具体功能下沉到模块；
 *   - Good Guardians：每个模块聚焦一个维度，诊断与一键优化分离；
 *   - Sam Helper：首页 = 硬件健康一眼看清 + 分类功能入口。
 *
 * 因此首页收敛为：设备健康评分 + 存储只读概览 + 快捷入口导航。
 * 清理 / 冻结的具体动作**只**在各自的 tab 里做，首页绝不做清理动作。
 * ============================================================
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val scan: ScanDeviceUseCase,
    private val healthScore: HealthScoreUseCase,
    private val cache: DeviceSnapshotCache,
    private val engine: NovaEngine,
    private val permissions: SystemPermissions,
    private val history: HistoryDao,
) : ViewModel() {

    /** 首页存储概览读数（只读，打开即显示，不必先点按钮） */
    data class Overview(
        val usedBytes: Long,
        val totalBytes: Long,
        val freeBytes: Long,
        val junkSafeBytes: Long,
        val junkTotalBytes: Long,
        val staleAppCount: Int,
        val appCount: Int,
    )

    private val _score = MutableStateFlow<HealthScore?>(null)
    val score: StateFlow<HealthScore?> = _score.asStateFlow()

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary.asStateFlow()

    /** 引擎不可用 —— UI 必须显式告知，不能假装正常 */
    private val _engineAvailable = MutableStateFlow(true)
    val engineAvailable: StateFlow<Boolean> = _engineAvailable.asStateFlow()

    private val _engineVersion = MutableStateFlow("")
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    /** 缺失的能力（权限引导卡的数据源） */
    private val _missing = MutableStateFlow<List<MissingCapability>>(emptyList())
    val missing: StateFlow<List<MissingCapability>> = _missing.asStateFlow()

    private val _overview = MutableStateFlow<Overview?>(null)
    val overview: StateFlow<Overview?> = _overview.asStateFlow()

    init {
        refreshCapabilities()
        // 首页先给健康读数，不要求用户先点按钮 —— 打开就有信息
        viewModelScope.launch {
            val snapshot = cache.last ?: runCatching { scan(rootPath()) }.getOrNull()
            if (snapshot != null) {
                cache.put(snapshot)
                refreshScore(snapshot)
            } else {
                _engineAvailable.value = engine.isAvailable
                _engineVersion.value = engine.version()
            }
        }
    }

    /** 权限状态可能在用户去设置页后变化，回到前台时重查 */
    fun refreshCapabilities() {
        _missing.value = permissions.missingCapabilities()
        _engineAvailable.value = engine.isAvailable
        _engineVersion.value = engine.version()
    }

    /** 由 UI 调用：跳转授权页 */
    fun grant(capability: MissingCapability) = permissions.launchGrantFor(capability)

    private suspend fun refreshScore(snapshot: DeviceSnapshot): HealthScore {
        // 电池健康度依赖内核；不可用时 HealthScoreUseCase 会降级为真实电量而非伪造
        val score = healthScore(snapshot, batteryScore = null)
        _score.value = score

        _engineAvailable.value = snapshot.engineAvailable
        _engineVersion.value = engine.version()

        val used = snapshot.storage.totalBytes - snapshot.storage.availableBytes
        _overview.value = Overview(
            usedBytes = used,
            totalBytes = snapshot.storage.totalBytes,
            freeBytes = snapshot.storage.availableBytes,
            junkSafeBytes = snapshot.junk?.safeBytes ?: 0L,
            junkTotalBytes = snapshot.junk?.totalBytes ?: 0L,
            staleAppCount = snapshot.apps.count {
                val days = it.daysSinceLastUse(snapshot.nowMs)
                days == null || days >= 30
            },
            appCount = snapshot.apps.size,
        )

        val suggestion = SuggestionEngine.suggest(
            lastCleanMs(),
            snapshot.junk?.safeBytes ?: 0L,
            snapshot.nowMs,
        )
        _summary.value = buildString {
            if (!snapshot.engineAvailable) {
                append("内核不可用，当前仅显示系统真实读数")
            } else {
                append("已用 ")
                append(used.formatBytes())
                append(" / ")
                append(snapshot.storage.totalBytes.formatBytes())
            }
            if (suggestion != null) {
                append(" · ")
                append(suggestion.title)
            }
        }
        return score
    }

    /**
     * 最近一次成功清理的时间戳（只读）。
     *
     * 首页不再执行清理，也不再写清理历史（那是「清理」tab 的职责），
     * 但「距上次清理多久」仍用于健康摘要，这里保留只读查询。
     */
    private suspend fun lastCleanMs(): Long? =
        runCatching { history.lastCleanEpochMs() }.getOrNull()

    private fun rootPath(): String =
        Environment.getExternalStorageDirectory()?.absolutePath
            ?: app.getExternalFilesDir(null)?.absolutePath
            ?: "/storage/emulated/0"
}
