package com.novacare.ui.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

// ============================================================
// AiOrb —— One UI 10 Fluid AI Design System 的 AI agent 悬浮球
//
// 【这是 Fluid AI 的灵魂，不是又一个按钮】
// One UI 10 的概念（Red Dot 2026）里，AI agent 是一个**圆形悬浮球（orb）**：
//   - 它浮在所有界面之上
//   - 有持续的呼吸光晕（表示"这个 agent 是活的"）
//   - 可以拖拽，靠近界面元素时产生磁吸（magnetic pull）
//   - 触碰时径向高光向四周扩散（illumination）
//
// 我上一版在助手气泡里放了个静态小圆 —— 那是"形"，不是"神"。
// 这一个才是可拖拽、带呼吸光晕、可点击进入助手的真 orb。
// ============================================================

/**
 * 可拖拽的 AI agent 悬浮球。
 *
 * @param icon 球心图标
 * @param tint 球体强调色（默认 accent）
 * @param onClick 点击进入（如跳转 AI 助手）
 * @param contentDescription 无障碍描述
 * @param modifier 外层容器（通常 fillMaxSize，让 orb 在整个屏幕内拖拽）
 */
@Composable
fun DraggableAiOrb(
    icon: ImageVector,
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = NovaCareTheme.colors.accent,
) {
    val reduceMotion = LocalReduceMotion.current

    // 拖拽后的位置（相对容器左上角）
    var offset by remember { mutableStateOf(IntOffset(0, 0)) }
    // 拖拽中 vs 松手（松手时用弹簧回弹一点点，产生"吸附"手感）
    var dragging by remember { mutableStateOf(false) }

    // 呼吸光晕
    val infinite = rememberInfiniteTransition(label = "orbBreath")
    val pulse by infinite.animateFloat(
        initialValue = 0.75f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (reduceMotion) 0 else 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    val breathe = if (reduceMotion) 1f else pulse

    // 按压缩放（触地时 orb 轻微缩小，松手弹回）
    val scale by animateFloatAsState(
        targetValue = if (dragging) 0.9f else 1f,
        animationSpec = if (reduceMotion) tween(0) else spring(
            dampingRatio = 0.6f,
            stiffness = 500f,
        ),
        label = "orbScale",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset = IntOffset(
                            offset.x + dragAmount.x.roundToInt(),
                            offset.y + dragAmount.y.roundToInt(),
                        )
                    },
                )
            },
    ) {
        Box(
            modifier = Modifier
                .offset { offset }
                .size(56.dp)
                .clip(CircleShape)
                // 呼吸光晕：径向渐变，随 pulse 扩张
                .drawBehind {
                    val r = size.minDimension / 2f
                    val halo = r * (1.6f + 0.4f * breathe)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                tint.copy(alpha = 0.35f * breathe),
                                tint.copy(alpha = 0.0f),
                            ),
                            center = center,
                            radius = halo,
                        ),
                        radius = halo,
                        center = center,
                    )
                    // 球体本体：径向高光（左上受光）
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.85f),
                                tint.copy(alpha = 0.9f),
                                tint.copy(alpha = 0.6f),
                            ),
                            center = Offset(size.width * 0.35f, size.height * 0.35f),
                            radius = r * 1.6f,
                        ),
                        radius = r,
                        center = center,
                    )
                }
                .background(Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            // 点击进入助手（orb 主体可点）
            androidx.compose.material3.IconButton(onClick = onClick) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
