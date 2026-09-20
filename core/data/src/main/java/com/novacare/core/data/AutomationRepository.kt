package com.novacare.core.data

import com.novacare.core.model.AutomationRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationRepository @Inject constructor(
    private val dao: RuleDao,
) {
    fun observeRules(): Flow<List<AutomationRule>> =
        dao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

    suspend fun rules(): List<AutomationRule> = dao.all().mapNotNull { it.toDomain() }

    suspend fun save(rule: AutomationRule) = dao.upsert(rule.toEntity())

    suspend fun delete(id: String) = dao.delete(id)

    suspend fun markRun(id: String, nowMs: Long) {
        val entity = dao.all().firstOrNull { it.id == id } ?: return
        dao.update(entity.copy(lastRunEpochMs = nowMs))
    }

    suspend fun ensureDefaultRule() {
        if (dao.all().isEmpty()) dao.upsert(defaultWeeklyRule().toEntity())
    }
}
