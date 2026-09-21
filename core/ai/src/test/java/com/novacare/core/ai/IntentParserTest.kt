package com.novacare.core.ai

import com.google.common.truth.Truth.assertThat
import com.novacare.core.model.IntentType
import kotlinx.coroutines.runBlocking
import org.junit.Test

class IntentParserTest {

    private val parser = RuleBasedIntentParser()
    private val knownApps = mapOf("微信" to "com.tencent.mm", "抖音" to "com.ss.android.ugc.aweme")

    private fun parse(text: String) = runBlocking { parser.parse(text, knownApps) }

    @Test
    fun parses_cache_cleaning() {
        val intent = parse("清理半年没用的缓存")
        assertThat(intent.intent).isEqualTo(IntentType.CLEAN_CACHE)
        assertThat(intent.olderThanDays).isEqualTo(180)
    }

    @Test
    fun parses_exclusions() {
        val intent = parse("清理三个月前的缓存，别动微信")
        assertThat(intent.intent).isEqualTo(IntentType.CLEAN_CACHE)
        assertThat(intent.olderThanDays).isEqualTo(90)
        assertThat(intent.excludePackages).containsExactly("com.tencent.mm")
    }

    @Test
    fun parses_freeze_intent() {
        assertThat(parse("冻结不常用的应用").intent).isEqualTo(IntentType.FREEZE_UNUSED_APPS)
    }

    @Test
    fun parses_analyze_intent() {
        assertThat(parse("看看存储都被什么占了").intent).isEqualTo(IntentType.ANALYZE_STORAGE)
    }

    @Test
    fun unknown_text_never_guesses_an_action() {
        val intent = parse("今天天气怎么样")
        assertThat(intent.intent).isEqualTo(IntentType.UNKNOWN)
        assertThat(intent.confidence).isEqualTo(0f)
    }

    @Test
    fun parses_day_based_threshold() {
        assertThat(parse("清理 30 天前的垃圾").olderThanDays).isEqualTo(30)
    }

    @Test
    fun parses_size_threshold() {
        assertThat(parse("清理超过 500MB 的垃圾").minBytes).isEqualTo(500L * 1024 * 1024)
    }
}
