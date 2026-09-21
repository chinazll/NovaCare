package com.novacare.core.automation

import com.novacare.core.model.ActionType

/**
 * 自动化动作的执行接口
 *
 * 由 :app 模块提供实现（因为执行动作需要扫描 / 清理 / 冻结等跨模块能力），
 * core:automation 只依赖抽象 —— 这样规则引擎可以脱离 UI 被单元测试。
 */
interface AutomationActions {

    suspend fun perform(action: ActionType, onlySafe: Boolean): Result

    data class Result(val freedBytes: Long, val message: String, val success: Boolean)
}
