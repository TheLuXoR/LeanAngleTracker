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

        // Pre-calculate DP to PX values here while we are in DrawScope
        val startX = centerX + 120.dp.toPx() // Added .toPx() here
        val startY = h + 100.dp.toPx()
        val bounceOffset = 12.dp.toPx()

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
            val pW = 64.dp.toPx()
            val pH = 110.dp.toPx()

            drawRoundRect(
                color = Color.Black,
                topLeft = Offset(centerX - pW/2, centerY - pH/2),
                size = Size(pW, pH),
                cornerRadius = CornerRadius(12.dp.toPx()),
                alpha = phoneAlpha
            )

            // Screen Glow (Primary)
            val screenAlpha = if (animProgress > 0.45f) display * phoneAlpha else 0.2f * phoneAlpha
            val screenColor = if (animProgress > 0.45f) primary else Color(0xFF121416)

            drawRoundRect(
                color = screenColor,
                topLeft = Offset(centerX - pW/2 + 3.dp.toPx(), centerY - pH/2 + 3.dp.toPx()),
                size = Size(pW - 6.dp.toPx(), pH - 6.dp.toPx()),
                cornerRadius = CornerRadius(10.dp.toPx()),
                alpha = screenAlpha
            )

            // Stylized Telemetry UI
            if (animProgress > 0.6f) {
                val uiAlpha = display * phoneAlpha
                drawCircle(
                    color = Color.White.copy(alpha = 0.4f * uiAlpha),
                    radius = 20.dp.toPx(),
                    center = Offset(centerX, centerY - 15.dp.toPx()),
                    style = Stroke(2.5.dp.toPx())
                )
                drawLine(
                    color = secondary.copy(alpha = 0.8f * uiAlpha),
                    start = Offset(centerX, centerY - 15.dp.toPx()),
                    end = Offset(centerX + 12.dp.toPx(), centerY - 22.dp.toPx()),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }

        // 4. Snap Impact Effect
        if (animProgress in 0.45f..0.7f) {
            val impact = (animProgress - 0.45f) / 0.25f
            drawCircle(
                color = primary.copy(alpha = 0.6f * (1f - impact)),
                radius = 90.dp.toPx() * impact,
                center = Offset(centerX, centerY),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}