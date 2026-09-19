package com.novacare.optimizer.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * NovaCare 语义色板（单一定义处）
 *
 * 修复 P3-5：此前 UI 层到处散落 `Color(0xFF5B8DEF)` 之类的字面量，
 * 既破坏主题一致性，也让深色模式适配无从下手。
 * 所有非 Material Theme 直接提供的颜色一律在此定义，UI 只引用这里的常量。
 */
object NovaSemanticColors {

    // ===== 四个健康维度（与 One UI 设备管家一致：冷色=系统资源，暖色=需关注） =====
    val Storage = Color(0xFF5B8DEF)   // 蓝 —— 存储
    val Memory = Color(0xFF9B6BDF)    // 紫 —— 内存
    val Battery = Color(0xFF2FA36B)   // 绿 —— 电池
    val AppSafety = Color(0xFFE8912D) // 橙 —— 应用防护

    // ===== 语义状态 =====
    val Success = Color(0xFF2FA36B)
    val Warning = Color(0xFFE8912D)
    val Danger = Color(0xFFE5484D)

    /** 评分取色：与 ScoreRing 保持同一套阈值 */
    fun scoreColor(score: Int): Color = when {
        score >= 85 -> Success
        score >= 60 -> Warning
        else -> Danger
    }
}
