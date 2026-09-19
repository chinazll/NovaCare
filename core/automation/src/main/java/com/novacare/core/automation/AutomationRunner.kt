package com.novacare.core.automation

import com.novacare.core.model.AutomationRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRunner @Inject constructor(
    private val repository: com.novacare.core.data.AutomationRepository,
    private val actions: AutomationActions,
) {

    suspend fun runDueRules(context: RuleEngine.RuleContext, nowMs: Long): List<Summary> =
        withContext(Dispatchers.Default) {
            repository.rules()
                .filter { RuleEngine.shouldRun(it, context, nowMs) }
                .map { rule ->
                    val onlySafe = RuleEngine.onlySafeItems(rule)
                    var freed = 0L
                    var ok = true
                    val messages = mutableListOf<String>()
                    for (action in RuleEngine.actionsOf(rule)) {
                        val r = actions.perform(action, onlySafe)
                        freed += r.freedBytes
                        ok = ok && r.success
                        messages.add(r.message)
                    }
                    repository.markRun(rule.id, nowMs)
                    Summary(rule.id, rule.name, freed, ok, messages.joinToString("；"))
                }
        }

    data class Summary(
        val ruleId: String,
        val ruleName: String,
        val freedBytes: Long,
        val success: Boolean,
        val message: String,
    )
}
