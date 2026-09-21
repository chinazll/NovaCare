package com.novacare.core.ai

import com.novacare.core.model.AiTier
import com.novacare.core.model.IntentType
import com.novacare.core.model.ParsedIntent
import com.novacare.core.common.formatBytes
import javax.inject.Inject

/**
 * F5 —— 自然语言指令 → 结构化操作
 *
 * 例："帮我清理半年前的安装包，但别动微信"
 *   → intent=CLEAN_JUNK, olderThanDays=180, excludePackages={com.tencent.mm}
 *
 * 双层实现：
 * 1. [RuleBasedIntentParser]：本地规则，零网络、零延迟，覆盖常见说法（L1）
 * 2. [LlmIntentParser]：复杂说法交给 L3 云端模型（用户主动开启才可用）
 *
 * 解析不出意图时返回 UNKNOWN —— **绝不猜一个动作去执行**。
 */
interface IntentParser {
    suspend fun parse(text: String, knownApps: Map<String, String>): ParsedIntent
}

class RuleBasedIntentParser @Inject constructor() : IntentParser {

    private companion object {
        val EXCLUDE_MARKS = listOf("别动", "不要动", "保留", "除了", "不要", "排除", "跳过")
        val INCLUDE_MARKS = listOf("只", "仅")
        val CN_DIGITS = mapOf(
            '一' to 1, '两' to 2, '二' to 2, '三' to 3, '四' to 4,
            '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9,
        )
    }

    override suspend fun parse(text: String, knownApps: Map<String, String>): ParsedIntent {
        val intent = when {
            text.contains("冻结") || text.contains("休眠") || text.contains("停用") ->
                IntentType.FREEZE_UNUSED_APPS

            text.contains("缓存") -> IntentType.CLEAN_CACHE
            text.contains("垃圾") || text.contains("残留") || text.contains("清理") ||
                text.contains("清掉") || text.contains("删除") -> IntentType.CLEAN_JUNK

            text.contains("分析") || text.contains("扫描") || text.contains("看看") ||
                text.contains("多大") -> IntentType.ANALYZE_STORAGE

            else -> IntentType.UNKNOWN
        }

        // 按标点切句，逐句判断是「排除」还是「只包含」——避免跨句误判
        val excludes = LinkedHashSet<String>()
        val includes = LinkedHashSet<String>()
        for (seg in splitSegments(text)) {
            val apps = extractApps(seg, knownApps)
            if (apps.isEmpty()) continue
            when {
                EXCLUDE_MARKS.any { seg.contains(it) } -> excludes.addAll(apps)
                INCLUDE_MARKS.any { seg.contains(it) } -> includes.addAll(apps)
            }
        }

        return ParsedIntent(
            intent = intent,
            olderThanDays = extractDays(text),
            excludePackages = excludes,
            includePackages = includes,
            minBytes = extractMinBytes(text),
            rawText = text,
            source = AiTier.DETERMINISTIC,
            confidence = if (intent == IntentType.UNKNOWN) 0f else 0.7f,
        )
    }

    private fun splitSegments(text: String): List<String> =
        text.split(Regex("[，,；;。、]")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun extractApps(segment: String, knownApps: Map<String, String>): Set<String> {
        if (segment.isBlank()) return emptySet()
        val out = LinkedHashSet<String>()
        for ((label, pkg) in knownApps) {
            if (segment.contains(label)) out.add(pkg)
        }
        return out
    }

    /** 支持「半年前」「三个月前」「30 天前」「一周前」 */
    private fun extractDays(text: String): Int? {
        if (text.contains("半年")) return 180
        if (text.contains("一年") || text.contains("1 年")) return 365
        if (text.contains("一周") || text.contains("一星期")) return 7
        if (text.contains("一个月")) return 30
        if (text.contains("一天")) return 1

        val months = Regex("(\\d+)\\s*个月").find(text)
        if (months != null) return months.groupValues[1].toIntOrNull()?.times(30)

        // 中文数字：三个月 / 两个月
        val cnMonths = Regex("([一二两三四五六七八九十]+)\\s*个月").find(text)
        if (cnMonths != null) {
            val n = parseCnNumber(cnMonths.groupValues[1])
            if (n != null) return n * 30
        }

        val days = Regex("(\\d+)\\s*天").find(text)
        if (days != null) return days.groupValues[1].toIntOrNull()

        val cnDays = Regex("([一二两三四五六七八九十]+)\\s*天").find(text)
        return cnDays?.let { parseCnNumber(it.groupValues[1]) }
    }

    /** 解析 1..99 的中文数字，无法解析时返回 null */
    private fun parseCnNumber(raw: String): Int? {
        if (raw.isBlank()) return null
        val tens = raw.indexOf('十')
        return when {
            tens < 0 -> raw.firstOrNull()?.let { CN_DIGITS[it] } ?: raw.toIntOrNull()
            raw.length == 1 -> 10
            else -> {
                val high = if (tens == 0) 1 else CN_DIGITS[raw[tens - 1]] ?: return null
                val low = raw.substring(tens + 1).firstOrNull()?.let { CN_DIGITS[it] } ?: 0
                high * 10 + low
            }
        }
    }

    private fun extractMinBytes(text: String): Long? {
        // 必须忽略大小写：用户随手打 "500mb" / "1Gb" 时旧的大小写敏感正则匹配不到，
        // 「清理大于 500MB 的文件」这条约束被静默丢弃。
        val mb = Regex("(\\d+)\\s*mb", RegexOption.IGNORE_CASE).find(text)
        if (mb != null) return mb.groupValues[1].toLongOrNull()?.times(1024 * 1024)
        val gb = Regex("(\\d+)\\s*gb", RegexOption.IGNORE_CASE).find(text)
        return gb?.groupValues?.get(1)?.toLongOrNull()?.times(1024 * 1024 * 1024)
    }
}

/** L3：把复杂说法交给云端模型；未开启或解析失败时**如实降级**给规则解析器 */
class LlmIntentParser(
    private val llm: CloudLlmProvider,
    private val fallback: IntentParser,
) : IntentParser {

    /** 云端模型是否已配置（未配置则必须走本地规则解析） */
    fun llmConfigured(): Boolean = llm.isConfigured

    override suspend fun parse(text: String, knownApps: Map<String, String>): ParsedIntent {
        if (!llm.isConfigured) return fallback.parse(text, knownApps)

        val systemPrompt = """
            你是 NovaCare 的指令解析器。把用户的自然语言转成 JSON，字段：
            {"intent":"CLEAN_CACHE|CLEAN_JUNK|FREEZE_UNUSED_APPS|ANALYZE_STORAGE|UNKNOWN",
             "olderThanDays":数字或null,"excludePackages":["包名"],"confidence":0-1}
            只输出 JSON，不要解释。已知应用：${knownApps.entries.joinToString { "${it.key}=${it.value}" }}
        """.trimIndent()

        val raw = llm.complete(systemPrompt, text) ?: return fallback.parse(text, knownApps)
        val parsed = runCatching { parseJson(raw) }.getOrNull()
        return parsed?.copy(rawText = text, source = AiTier.CLOUD) ?: fallback.parse(text, knownApps)
    }

    private fun parseJson(raw: String): ParsedIntent {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        require(start >= 0 && end > start)
        val body = raw.substring(start, end + 1)

        fun str(field: String): String? =
            Regex("\"$field\"\\s*:\\s*\"([^\"]*)\"").find(body)?.groupValues?.get(1)

        fun num(field: String): Int? =
            Regex("\"$field\"\\s*:\\s*(\\d+)").find(body)?.groupValues?.get(1)?.toIntOrNull()

        // 置信度可能是 0.9 这样的小数；只按整数解析会把 0.9 截成 0，置信度恒失真。
        fun dec(field: String): Double? =
            Regex("\"$field\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)")
                .find(body)?.groupValues?.get(1)?.toDoubleOrNull()

        val pkgs = Regex("\"excludePackages\"\\s*:\\s*\\[([^]]*)]")
            .find(body)
            ?.groupValues?.get(1)
            ?.let { Regex("\"([^\"]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() }
            ?: emptySet()

        return ParsedIntent(
            intent = IntentType.entries.firstOrNull { it.name == str("intent") }
                ?: IntentType.UNKNOWN,
            olderThanDays = num("olderThanDays"),
            excludePackages = pkgs,
            rawText = "",
            source = AiTier.CLOUD,
            confidence = (dec("confidence") ?: 0.7).toFloat().coerceIn(0f, 1f),
        )
    }
}
