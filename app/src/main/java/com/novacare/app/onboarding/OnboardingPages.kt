package com.novacare.app.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.novacare.ui.designsystem.EmptyTone
import com.novacare.ui.designsystem.InlineNotice
import com.novacare.ui.designsystem.NovaCareTheme
import com.novacare.ui.designsystem.NovaTap
import com.novacare.ui.designsystem.SecondaryAction

// ============================================================
// Onboarding 四页内容
//
// 每页只讲一个主张：
//   1 欢迎   —— NovaCare 是什么
//   2 权限   —— 每项权限换来什么、不给会失去什么（最重要）
//   3 AI     —— 云端 AI 的闭环与 API Key 由你自己填
//   4 完成   —— 如实汇总授权结果 + 「开始使用」
// ============================================================

@Composable
internal fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp),
    ) {
        Text(
            text = "NovaCare",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        // 一道极细的强调色分隔 —— 全页唯一的彩色元素
        Box(
            modifier = Modifier
                .size(width = 40.dp, height = 2.dp)
                .background(NovaCareTheme.colors.accent, CircleShape),
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "看清手机里什么在占空间，\n并且在动手之前，先让你确认。",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "它会先读取设备的真实状态，再给出建议；清理、冻结这类动作始终由你按下最后一下。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            QuietChip("设备清理与优化")
            QuietChip("AI 助手（可选）")
        }
    }
}

@Composable
internal fun PermissionsPage(
    items: List<OnboardingPermissionItem>,
    onGrant: (OnboardingPermissionItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "这些权限，\n各自换来了什么",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "下面每一项都只用于它写明的用途。没授予的部分会如实降级 —— 不会假装能用。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))

        items.forEach { item ->
            PermissionRow(item = item, onGrant = onGrant)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
internal fun AiPage(cloudAiEnabled: Boolean, apiKeySet: Boolean) {
    val stateText = when {
        cloudAiEnabled && apiKeySet -> "当前：已开启 · 已填写 Key"
        cloudAiEnabled -> "当前：已开启，但还没填 Key —— 此时不会发起任何云端请求"
        apiKeySet -> "当前：Key 已填写，但云端对话未开启"
        else -> "当前：未开启 · 未填写 Key"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.SmartToy,
                contentDescription = null,
                tint = NovaCareTheme.colors.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "AI 助手",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "它按固定的顺序工作，每一步你都看得见：",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        AiStep(index = 1, text = "先看你手机的实际状态（本机读取，不出设备）")
        AiStep(index = 2, text = "需要更深的分析时，才去云端查一次")
        AiStep(index = 3, text = "结论回到本地执行，不把执行权交给云端")
        AiStep(index = 4, text = "动手之前先列出要做什么，你确认后才执行")

        Spacer(Modifier.height(20.dp))
        InlineNotice(
            text = "云端对话默认关闭。要用它，请在「设置 → AI → API Key」里填入你自己的 Key —— NovaCare 不提供、也不代管 Key。未填写前，App 不会发起任何网络请求。",
            tone = EmptyTone.Neutral,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stateText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
internal fun ReadyPage(
    items: List<OnboardingPermissionItem>,
    onGrant: (OnboardingPermissionItem) -> Unit,
) {
    val granted = items.filter { it.granted }
    val missing = items.filter { !it.granted }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "准备好了",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(12.dp))

        if (missing.isEmpty()) {
            Text(
                text = "需要的权限都已开启，可以完整使用了。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Text(
                text = "可以开始用了。下面 ${missing.size} 项还没开，对应功能会按说明降级 —— 之后随时可以在「设置」里补上。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(20.dp))

        if (granted.isNotEmpty()) {
            granted.forEach { item ->
                ReadyRow(
                    text = "${item.title} · 已开启",
                    tone = NovaCareTheme.colors.riskSafe,
                    withCheck = true,
                )
                Spacer(Modifier.height(8.dp))
            }
        }

        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            missing.forEach { item ->
                Column(Modifier.fillMaxWidth()) {
                    ReadyRow(
                        text = "${item.title} · 未开启",
                        tone = if (item.required) {
                            NovaCareTheme.colors.riskCaution
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        withCheck = false,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = item.consequence,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.required) {
                            NovaCareTheme.colors.riskCaution
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    SecondaryAction(text = item.actionLabel, onClick = { onGrant(item) })
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ============================================================
// 内部复用件
// ============================================================

/** 安静的小标签：只用发丝描边，不抢视觉 */
@Composable
private fun QuietChip(text: String) {
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, NovaCareTheme.colors.hairline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

/**
 * 权限说明行。
 *
 * 信息层级：标题 → 为什么需要 → 不授予的后果（只有未授予时才显示，用 caution 色）
 * → 一个真的能跳过去的按钮。不给用户「知道了但没地方点」的说明。
 */
@Composable
private fun PermissionRow(
    item: OnboardingPermissionItem,
    onGrant: (OnboardingPermissionItem) -> Unit,
) {
    val colors = NovaCareTheme.colors
    val view = LocalView.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, colors.hairline),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!item.required) {
                    Spacer(Modifier.width(8.dp))
                    QuietChip("可选")
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.why,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            if (item.granted) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = colors.riskSafe,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = item.actionLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.riskSafe,
                    )
                }
            } else {
                Text(
                    text = item.consequence,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.riskCaution,
                    modifier = Modifier.semantics {
                        contentDescription = "未授予${item.title}的影响：${item.consequence}"
                    },
                )
                Spacer(Modifier.height(12.dp))
                SecondaryAction(
                    text = item.actionLabel,
                    onClick = { NovaTap(view); onGrant(item) },
                )
            }
        }
    }
}

/** AI 闭环的一步：序号用极淡的圆底，靠排版而不是颜色制造节奏 */
@Composable
private fun AiStep(index: Int, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ReadyRow(text: String, tone: Color, withCheck: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (withCheck) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = tone,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tone,
        )
    }
}
