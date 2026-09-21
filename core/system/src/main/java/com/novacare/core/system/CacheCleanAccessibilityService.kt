package com.novacare.core.system

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * 无障碍缓存清理服务（无 Shizuku 时的单 App 缓存清理降级方案）。
 *
 * 背景：Android 没有「只清单个第三方 App 缓存」的公开 API（详见 PERMISSION_CHANNELS.md）。
 * 无 Shizuku 时的诚实做法是引导用户到系统「应用详情设置页」手动点「清除缓存」。
 * 本服务把这一步自动化：当用户主动发起「清理某 App 缓存」时，在设置页自动找到并
 * 点击「清除缓存」按钮（Sam Helper / AppManager 的常见做法）。
 *
 * 保守设计（无障碍点击有风险，必须守住）：
 *   1. 只有用户在 App 内主动发起（[startClearCache] 设置待办包名）才会动作，绝不后台自行点击。
 *   2. 只处理系统设置包（AOSP / Google / OneUI）的事件。
 *   3. 一次待办只点一次，点击后立即清除待办并防重入。
 *   4. 用途已如实写入 accessibility_service_config.xml 的 description。
 */
class CacheCleanAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pending = pendingPackage ?: return // 无待办 → 绝不动作

        // 只处理系统设置页（AOSP / Google / OneUI）
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in SETTINGS_PACKAGES) return

        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> Unit
            else -> return
        }

        // 防重入：同一待办在时间窗内只点一次
        val now = System.currentTimeMillis()
        if (pending == lastClickedPackage && now - lastClickedAt < CLICK_DEBOUNCE_MS) return

        val root = rootInActiveWindow ?: return
        try {
            val target = findClearCacheNode(root) ?: return

            target.performAction(AccessibilityNodeInfo.ACTION_CLICK)

            // 无论是否点成功，都清除待办，避免反复尝试卡住
            lastClickedPackage = pending
            lastClickedAt = now
            pendingPackage = null
        } finally {
            // rootInActiveWindow 与 getChild() 取到的节点由调用方负责回收，
            // 旧实现从不 recycle —— 每次窗口变化都泄漏一整棵节点树。
            root.recycle()
        }
    }

    override fun onInterrupt() = Unit

    /** 在节点树中查找「清除缓存」按钮（可点击且已启用优先） */
    private fun findClearCacheNode(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var fallback: AccessibilityNodeInfo? = null
        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()
            val text = node.text?.toString()?.trim().orEmpty()
            val desc = node.contentDescription?.toString()?.trim().orEmpty()
            if (matchesCacheLabel(text) || matchesCacheLabel(desc)) {
                if (node.isClickable && node.isEnabled) return node
                if (fallback == null && node.isClickable) fallback = node
            }
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return fallback
    }

    private fun matchesCacheLabel(text: String): Boolean =
        CLEAR_CACHE_LABELS.any { text.contains(it, ignoreCase = true) }

    companion object {
        /** 待清理的目标包名；null = 无待办（服务不动作） */
        @Volatile
        private var pendingPackage: String? = null

        @Volatile
        private var lastClickedPackage: String? = null

        @Volatile
        private var lastClickedAt: Long = 0L

        private const val CLICK_DEBOUNCE_MS = 1500L

        /** 系统设置包（AOSP/Google 与 OneUI） */
        private val SETTINGS_PACKAGES = setOf(
            "com.android.settings",
            "com.samsung.android.settings",
        )

        /** 常见「清除缓存」按钮文案（多语言，尽量覆盖） */
        private val CLEAR_CACHE_LABELS = listOf(
            "清除缓存",
            "清空缓存",
            "Clear cache",
            "キャッシュを消去",
        )

        /** 无障碍服务是否已启用 */
        fun isEnabled(context: Context): Boolean = runCatching {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return@runCatching false
            val name = CacheCleanAccessibilityService::class.java.name
            enabled.split(':').any { entry ->
                entry.equals("${context.packageName}/$name", ignoreCase = true) ||
                    entry.equals("${context.packageName}/.$name", ignoreCase = true) ||
                    entry.endsWith(name, ignoreCase = true)
            }
        }.getOrDefault(false)

        /** 跳转到无障碍设置页（开启入口） */
        fun settingsIntent(context: Context): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        /**
         * 发起「清理某 App 缓存」流程：设置待办 → 打开该 App 的系统应用详情页，
         * 服务在详情页出现后自动点「清除缓存」。
         *
         * @return true = 已成功打开设置页；false = 打开失败（此时不会留下待办）。
         */
        fun startClearCache(context: Context, packageName: String): Boolean {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:$packageName"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return runCatching {
                pendingPackage = packageName
                context.startActivity(intent)
                true
            }.onFailure {
                pendingPackage = null // 打开失败则回滚待办
            }.getOrDefault(false)
        }
    }
}
