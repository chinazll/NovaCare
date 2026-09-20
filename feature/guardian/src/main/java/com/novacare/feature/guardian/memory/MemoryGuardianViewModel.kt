package com.novacare.feature.guardian.memory

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.novacare.core.system.MemoryProcessSource
import com.novacare.core.system.SystemPermissions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 内存守护 ViewModel
 *
 * 【一键回收到底能做什么 —— 必须说清的能力边界】
 *   Android 上第三方应用**不能**强杀其他应用的进程：
 *     - `ActivityManager.killBackgroundProcesses()` 自 Android 5.0 起
 *       对其他应用的进程基本无效（系统按自己的策略回收）
 *     - `FORCE_STOP_PACKAGES` 是系统签名级权限，拿不到
 *     - "真正禁自启 / 真正锁后台"同样需要系统或 root 权限
 *
 *   所以这里的"回收"只做三件**真实会发生**的事：
 *     1. 清理本应用自己的缓存目录（可测量）
 *     2. 对本进程调用 System.gc()（只是给 JVM 的建议，不保证发生）
 *     3. 读一次系统内存状态，如实报告前后变化
 *   其余一律引导到系统设置页让用户自己操作。
 *   绝不出现"加速 50%""一键提速"这类话术。
 */
@HiltViewModel
class MemoryGuardianViewModel @Inject constructor(
    private val source: MemoryProcessSource,
    private val permissions: SystemPermissions,
) : ViewModel() {

    data class Snapshot(
        val overview: MemoryProcessSource.MemoryOverview,
        val processes: List<MemoryProcessSource.ProcessMemory>,
        val selfCacheBytes: Long,
        /** 系统只给了很少的进程（第三方可见性受限） */
        val limitedVisibility: Boolean,
    )

    sealed interface ReleaseResult {
        data object Idle : ReleaseResult
        data object Working : ReleaseResult
        data class Done(
            val freedSelfCacheBytes: Long,
            val availableBeforeBytes: Long,
            val availableAfterBytes: Long,
        ) : ReleaseResult
    }

    private val _snapshot = MutableStateFlow<Snapshot?>(null)
    val snapshot: StateFlow<Snapshot?> = _snapshot.asStateFlow()

    private val _release = MutableStateFlow<ReleaseResult>(ReleaseResult.Idle)
    val release: StateFlow<ReleaseResult> = _release.asStateFlow()

    private var polling: Job? = null

    init {
        startPolling()
    }

    private fun startPolling() {
        polling?.cancel()
        polling = viewModelScope.launch {
            while (isActive) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun refresh() {
        val processes = source.processes()
        _snapshot.value = Snapshot(
            overview = source.overview(),
            processes = processes,
            selfCacheBytes = source.selfCacheBytes(),
            // 系统只肯给 3 个及以下进程时，明确标记为"可见性受限"
            limitedVisibility = processes.size in 1..LIMITED_VISIBILITY_THRESHOLD,
        )
    }

    /**
     * 执行真实可回收的动作。
     *
     * 前后的可用内存都是**实测**的 ActivityManager 读数：
     * 变少了或没变就照实说，不粉饰成"已优化"。
     */
    fun release() {
        if (_release.value is ReleaseResult.Working) return
        viewModelScope.launch {
            _release.value = ReleaseResult.Working
            val before = source.overview().availableBytes
            val freed = source.clearSelfCache()
            // 只对本进程生效的 GC 建议 —— 明确不是"清理别的 App"
            System.gc()
            delay(MEASURE_DELAY_MS)
            val after = source.overview().availableBytes
            _release.value = ReleaseResult.Done(
                freedSelfCacheBytes = freed,
                availableBeforeBytes = before,
                availableAfterBytes = after,
            )
            refresh()
        }
    }

    fun dismissRelease() {
        _release.value = ReleaseResult.Idle
    }

    fun labelFor(process: MemoryProcessSource.ProcessMemory): String {
        val pkg = process.packages.firstOrNull()
        return if (pkg != null) source.appLabel(pkg) else process.processName
    }

    /**
     * 打开某个应用的系统详情页。
     *
     * 为什么要跳走：Android 不允许第三方调用"强行停止"，
     * 只有用户自己在系统页里点才有这个权限。这里如实把用户送到能操作的地方。
     */
    fun openAppDetails(packageName: String) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .setData(Uri.parse("package:$packageName"))
        permissions.launchSettings(intent)
    }

    /** 打开系统「应用管理」列表页（用户可逐个自行处理） */
    fun openApplicationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        permissions.launchSettings(intent)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
        /** 等系统把内存账目更新完再测，避免读到"删除前的旧值" */
        const val MEASURE_DELAY_MS = 700L
        const val LIMITED_VISIBILITY_THRESHOLD = 3
    }
}
