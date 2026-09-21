package com.novacare.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SuggestionEngineTest {

    private val now = 1_700_000_000_000L
    private val day = 86_400_000L

    @Test
    fun small_reclaimable_is_never_suggested() {
        assertThat(SuggestionEngine.suggest(null, 10L * 1024 * 1024, now)).isNull()
    }

    @Test
    fun first_time_suggests_cleaning() {
        val s = SuggestionEngine.suggest(null, 800L * 1024 * 1024, now)
        assertThat(s).isNotNull()
        assertThat(s!!.worthDoing).isTrue()
    }

    @Test
    fun after_a_week_suggests_again() {
        val s = SuggestionEngine.suggest(now - 10 * day, 800L * 1024 * 1024, now)
        assertThat(s!!.worthDoing).isTrue()
    }

    @Test
    fun right_after_cleaning_do_not_nag() {
        val s = SuggestionEngine.suggest(now - 1 * day, 800L * 1024 * 1024, now)
        assertThat(s!!.worthDoing).isFalse()
    }
}
