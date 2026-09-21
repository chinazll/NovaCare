package com.novacare.ui.designsystem

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

// ============================================================
// NovaCare 振动反馈 —— 用户感知「不一样」的 50% 来源
//
// One UI / Samsung 自家 App 的体感差异，绝大部分来自振动：
// tap、长按、越过阈值、success 都有不同节奏的微振。
// Compose 默认没有这套,这里按 Google Haptics 官方分级实现五档 API。
//
// 实现策略：
//   - contextClick / longPress / virtualKey: 用 View.performHapticFeedback
//     （API 23+,系统接管振幅通道,与系统级触觉一致）
//   - success / 强调: Vibrator + VibrationEffect（API 26+,精确控制时长）
//
// 所有调用都是「no-op safe」:View 不可用 / Vibrator 不可用 / 设备无振动器 → 静默失败
// ============================================================

/**
 * 轻触反馈 —— 列表项点击、tab 切换、chip 触发。
 *
 * 对应 HapticFeedbackConstants.CONTEXT_CLICK：比 CLICK 更轻、更脆，
 * One UI 在所有列表项轻点时统一走 contextClick。
 */
fun NovaTap(view: View) {
    if (view.isHapticFeedbackEnabled) {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

/**
 * 中等振动 —— 长按、危险动作前置。
 *
 * 比 NovaTap 重一档,对应 HapticFeedbackConstants.LONG_PRESS。
 * 用在冻结/解冻/删除/开启高级模式这类「会改系统状态」动作之前。
 */
fun NovaLongPress(view: View) {
    if (view.isHapticFeedbackEnabled) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
}

/**
 * 成功反馈 —— 关键动作完成（清理执行完毕、计划创建成功、AI 首个 token 到达）。
 *
 * 实现：API 26+ 用 VibrationEffect.createOneShot(15ms, DEFAULT_AMPLITUDE)；
 * 旧设备降级到震动 15ms —— 没有真正的效果但不会崩。
 *
 * @param context 用于获取 Vibrator（必须有 Activity / Application Context）
 */
fun NovaSuccess(context: Context) {
    val vibrator = context.resolveVibrator() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = VibrationEffect.createOneShot(15L, VibrationEffect.DEFAULT_AMPLITUDE)
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(15L)
    }
}

/**
 * 切换振动 —— 开关 / 勾选切换。
 *
 * 关键：开启和关闭是**不同**的振感,而不是同一个声音重复。
 * 开启：两下短振（15ms + 25ms）—— 传达"已锁定"
 * 关闭：一下单振（10ms）—— 传达"已释放"
 *
 * 用 toggle 参数切换,让用户「用手感受到」状态方向。
 */
fun NovaToggle(context: Context, checked: Boolean) {
    val vibrator = context.resolveVibrator() ?: return
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val effect = if (checked) {
            VibrationEffect.createWaveform(
                longArrayOf(0L, 15L, 30L, 25L),
                intArrayOf(0, VibrationEffect.DEFAULT_AMPLITUDE, 0, 200),
                -1,
            )
        } else {
            VibrationEffect.createOneShot(10L, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        vibrator.vibrate(effect)
    } else {
        @Suppress("DEPRECATION")
        vibrator.vibrate(
            if (checked) longArrayOf(0L, 15L, 30L, 25L) else longArrayOf(0L, 10L),
            -1,
        )
    }
}

/**
 * 阈值越过振动 —— swipe 列表项 / 拖拽到 commit 点。
 *
 * 一声短促的「咔哒」感,对应 HapticFeedbackConstants.VIRTUAL_KEY。
 * 与 NovaTap 的区别：更"硬",传达"动作已锁定,无法回退"的体感。
 */
fun NovaSwipeThreshold(view: View) {
    if (view.isHapticFeedbackEnabled) {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }
}

// --- 工具函数 ---

/**
 * 获取 Vibrator。
 *
 * API 31+: 用 VibratorManager（vibrator 必须通过 manager 拿,旧路径 deprecated）。
 * 旧设备:用 Context.VIBRATOR_SERVICE（旧常量,vibrate 仍可工作）。
 *
 * 设备没有振动器（平板、电视） → 返回 null,调用方静默 no-op。
 */
private fun Context.resolveVibrator(): Vibrator? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}