package com.novacare.core.common

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FormatTest {

    @Test
    fun bytes_under_1kb_shows_raw() {
        assertThat(0L.formatBytes()).isEqualTo("0 B")
        assertThat(512L.formatBytes()).isEqualTo("512 B")
    }

    @Test
    fun bytes_uses_1024_base() {
        assertThat(1024L.formatBytes()).isEqualTo("1.0 KB")
        assertThat(1024L * 1024).isEqualTo(1_048_576L)
        assertThat((1024L * 1024 * 3).formatBytes()).isEqualTo("3.0 MB")
    }

    @Test
    fun bytes_large_values_drop_decimals() {
        assertThat((1024L * 1024 * 1024 * 128).formatBytes()).isEqualTo("128 GB")
    }

    @Test
    fun negative_bytes_never_crash() {
        assertThat((-1L).formatBytes()).isEqualTo("-")
    }

    @Test
    fun days_human_readable() {
        assertThat(0L.formatDays()).isEqualTo("今天")
        assertThat(1L.formatDays()).isEqualTo("1 天前")
        assertThat(60L.formatDays()).isEqualTo("2 个月前")
    }

    @Test
    fun outcome_map_and_err() {
        val ok: Outcome<Int> = Outcome.Ok(2)
        assertThat(ok.map { it * 3 }.getOrNull()).isEqualTo(6)
        val err: Outcome<Int> = Outcome.Err(AppError("X", "boom"))
        assertThat(err.getOrNull()).isNull()
        assertThat(err.map { it * 3 }).isInstanceOf(Outcome.Err::class.java)
    }
}
