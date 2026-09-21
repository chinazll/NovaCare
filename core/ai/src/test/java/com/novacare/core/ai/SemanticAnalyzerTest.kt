package com.novacare.core.ai

import com.google.common.truth.Truth.assertThat
import com.novacare.core.model.FileClassificationRequest
import com.novacare.core.model.SemanticFileKind
import kotlinx.coroutines.runBlocking
import org.junit.Test

class SemanticAnalyzerTest {

    private val analyzer = RuleBasedSemanticAnalyzer()

    @Test
    fun capability_is_honest_about_not_being_a_model() {
        val capability = analyzer.capability()
        // 未集成端侧模型时必须如实说明，不能冒充 L2
        assertThat(capability.unavailableReason).isNotNull()
        assertThat(capability.engineName).contains("规则")
    }

    @Test
    fun classifies_screenshots_and_installers() = runBlocking {
        val result = analyzer.classifyFiles(
            listOf(
                FileClassificationRequest("/sdcard/DCIM/Screenshots/a.png", 1024, "png", null),
                FileClassificationRequest("/sdcard/Download/app.apk", 1024, "apk", null),
                FileClassificationRequest("/sdcard/Download/a.pdf", 1024, "pdf", null),
            ),
        )
        assertThat(result[0].kind).isEqualTo(SemanticFileKind.SCREENSHOT)
        assertThat(result[1].kind).isEqualTo(SemanticFileKind.INSTALLER)
        assertThat(result[2].kind).isEqualTo(SemanticFileKind.DOCUMENT)
    }

    @Test
    fun unknown_files_get_low_confidence() = runBlocking {
        val result = analyzer.classifyFiles(
            listOf(FileClassificationRequest("/sdcard/x.xyz", 10, "xyz", null)),
        )
        assertThat(result[0].kind).isEqualTo(SemanticFileKind.UNKNOWN)
        assertThat(result[0].confidence).isLessThan(0.5f)
    }
}
