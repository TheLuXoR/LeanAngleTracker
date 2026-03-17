package com.example.leanangletracker.ui.animation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

@Composable
fun PhoneMountAnimation(modifier: Modifier) {
    val infinite = rememberInfiniteTransition(label = "mount")

    val animProgress by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animProgress"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val centerY = h / 2f

        val baseWidth = 240.dp.toPx()
        val baseHeight = 240.dp.toPx()
        val scale = minOf(w / baseWidth, h / baseHeight).coerceAtLeast(0.1f)

        fun scaled(dpValue: Float) = dpValue.dp.toPx() * scale

        // Pre-calculate DP to PX values here while we are in DrawScope
        val startX = centerX + scaled(120f)
        val startY = h + scaled(100f)
        val bounceOffset = scaled(12f)

        // ... (rest of your existing drawing code for Yoke and Handlebars) ...

        // 3. Animating Phone
        val approach = (animProgress / 0.4f).coerceIn(0f, 1f)
        val click = ((animProgress - 0.4f) / 0.1f).coerceIn(0f, 1f)
        val display = ((animProgress - 0.5f) / 0.3f).coerceIn(0f, 1f)
        val fade = ((animProgress - 0.85f) / 0.15f).coerceIn(0f, 1f)

        val phoneAlpha = if (animProgress > 0.85f) 1f - fade else 1f

        withTransform({
            if (animProgress < 0.5f) {
                val currX = startX + (centerX - startX) * approach
                val currY = startY + (centerY - startY) * approach
                val scale = 1.8f - (0.8f * approach)
                val rotate = 25f * (1f - approach)

                translate(currX - centerX, currY - centerY)
                scale(scale, scale, Offset(centerX, centerY))
                rotate(rotate, Offset(centerX, centerY))
            } else if (click < 1f) {
                // Use the pre-calculated bounceOffset variable here
                translate(top = bounceOffset * (1f - click))
            }
        }) {
            // ... (rest of your phone drawing code) ...
            val pW = scaled(64f)
            val pH = scaled(110f)

            drawRoundRect(
                color = Color.Gray.copy(0.6f),
                topLeft = Offset(centerX - pW/2 +3, centerY - pH/2+3),
                size = Size(pW, pH),
                cornerRadius = CornerRadius(scaled(12f)),
                alpha = phoneAlpha
            )
            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(centerX - pW/2, centerY - pH/2),
                size = Size(pW, pH),
                cornerRadius = CornerRadius(scaled(12f)),
                alpha = phoneAlpha
            )


            // Screen Glow (Primary)
            val screenAlpha = if (animProgress > 0.45f) display * phoneAlpha else 0.2f * phoneAlpha
            val screenColor = if (animProgress > 0.45f) primary else Color(0xFF121416)

            drawRoundRect(
                color = screenColor,
                topLeft = Offset(centerX - pW/2 + scaled(3f), centerY - pH/2 + scaled(3f)),
                size = Size(pW - scaled(6f), pH - scaled(6f)),
                cornerRadius = CornerRadius(scaled(10f)),
                alpha = screenAlpha
            )

            // Stylized Telemetry UI
            if (animProgress > 0.6f) {
                val uiAlpha = display * phoneAlpha
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f * uiAlpha),
                    radius = scaled(20f),
                    center = Offset(centerX, centerY - scaled(15f)),
                    style = Stroke(scaled(2.5f))
                )
                drawLine(
                    color = secondary.copy(alpha = 0.8f * uiAlpha),
                    start = Offset(centerX, centerY - scaled(15f)),
                    end = Offset(centerX + scaled(12f), centerY - scaled(22f)),
                    strokeWidth = scaled(3f),
                    cap = StrokeCap.Round
                )
            }
        }

        // 4. Snap Impact Effect
        if (animProgress in 0.45f..0.7f) {
            val impact = (animProgress - 0.45f) / 0.25f
            drawCircle(
                color = primary.copy(alpha = 0.6f * (1f - impact)),
                radius = scaled(90f) * impact,
                center = Offset(centerX, centerY),
                style = Stroke(width = scaled(3f))
            )
        }
    }
}
