package com.example.leanangletracker.ui.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp

enum class BikeLean {
    UPRIGHT,
    LEFT_READY,
    LEFT_MEASURING,
    RIGHT_READY,
    RIGHT_MEASURING,
    READY
}

private val SineEaseInOut = Easing { fraction ->
    ((1 - Math.cos(fraction * Math.PI)) / 2).toFloat()
}


@Composable
fun BikeLeanAnimation(step: BikeLean, modifier: Modifier = Modifier, approachProgress: Float = 1f) {
    val infinite = rememberInfiniteTransition(label = "bike-lean")

    val targetLean = when (step) {
        BikeLean.LEFT_READY,
        BikeLean.LEFT_MEASURING -> -35f
        BikeLean.RIGHT_READY,
        BikeLean.RIGHT_MEASURING -> 35f
        else -> 0f
    }

    val leanAngle by animateFloatAsState(
        targetValue = targetLean,
        // Even slower transition for calibration as requested
        animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing),
        label = "leanAngle"
    )

    val breathing by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // Very slow breathing
        animationSpec = infiniteRepeatable(tween(800, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "breathing"
    )

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val groundY = h * 0.85f
        val roadTopY = groundY - 60.dp.toPx()

        // 1. Draw Road
        val roadPath = Path().apply {
            moveTo(centerX - 15.dp.toPx(), roadTopY)
            lineTo(centerX + 15.dp.toPx(), roadTopY)
            lineTo(centerX + 120.dp.toPx(), h)
            lineTo(centerX - 120.dp.toPx(), h)
            close()
        }
        drawPath(
            path = roadPath,
            brush = Brush.verticalGradient(
                listOf(
                    onSurface.copy(alpha = 0.05f),
                    onSurface.copy(alpha = 0.15f)
                )
            )
        )

        // Vanishing point for the approach animation is slightly above roadTopY
        // because the road has a width of 30dp at roadTopY and 240dp at h.
        val dy = h - roadTopY
        val vanishingPointY = roadTopY - (dy / 7f)

        // 2. Bike
        withTransform({
            // Apply approach animation: scale and translate
            // We scale from the vanishing point
            scale(approachProgress, approachProgress, pivot = Offset(centerX, vanishingPointY))

            // Apply lean and breathing
            rotate(
                degrees = leanAngle + (if (targetLean != 0f) breathing * 1.5f else 0f),
                pivot = Offset(centerX, groundY)
            )
        }) {
            // Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.2f * approachProgress),
                topLeft = Offset(centerX - 20.dp.toPx(), groundY - 5.dp.toPx()),
                size = Size(40.dp.toPx(), 10.dp.toPx())
            )

            // Bike Body
            drawRoundRect(
                color = Color(0xFF1A1C1E),
                topLeft = Offset(centerX - 14.dp.toPx(), roadTopY),
                size = Size(28.dp.toPx(), 60.dp.toPx()),
                cornerRadius = CornerRadius(14.dp.toPx())
            )

            val forkColor = Color(0xFF454749)
            drawLine(
                forkColor,
                Offset(centerX - 18.dp.toPx(), groundY - 40.dp.toPx()),
                Offset(centerX - 22.dp.toPx(), groundY - 130.dp.toPx()),
                8.dp.toPx()
            )
            drawLine(
                forkColor,
                Offset(centerX + 18.dp.toPx(), groundY - 40.dp.toPx()),
                Offset(centerX + 22.dp.toPx(), groundY - 130.dp.toPx()),
                8.dp.toPx()
            )

            val fairingPath = Path().apply {
                moveTo(centerX, groundY - 150.dp.toPx())
                lineTo(centerX - 45.dp.toPx(), groundY - 120.dp.toPx())
                lineTo(centerX - 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 45.dp.toPx(), groundY - 120.dp.toPx())
                close()
            }
            drawPath(fairingPath, color = primary)
            drawCircle(
                Color.White.copy(alpha = 0.9f),
                12.dp.toPx(),
                Offset(centerX, groundY - 110.dp.toPx())
            )
            drawLine(
                Color(0xFF2D2F31),
                Offset(centerX - 70.dp.toPx(), groundY - 135.dp.toPx()),
                Offset(centerX + 70.dp.toPx(), groundY - 135.dp.toPx()),
                6.dp.toPx(),
                StrokeCap.Round
            )
        }
    }
}
