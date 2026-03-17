package com.example.leanangletracker.ui.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
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
import com.example.leanangletracker.CalibrationStep


private val SineEaseInOut = Easing { fraction ->
    ((1 - Math.cos(fraction * Math.PI)) / 2).toFloat()
}

@Composable
fun BikeLeanAnimation(step: CalibrationStep, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "bike-lean")

    val targetLean = when (step) {
        CalibrationStep.LEFT_READY,
        CalibrationStep.LEFT_MEASURING -> -35f
        CalibrationStep.RIGHT_READY,
        CalibrationStep.RIGHT_MEASURING -> 35f
        else -> 0f
    }

    val leanAngle by animateFloatAsState(
        targetValue = targetLean,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "leanAngle"
    )

    val breathing by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1500, easing = SineEaseInOut), RepeatMode.Reverse),
        label = "breathing"
    )

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val groundY = h * 0.85f

        // 1. Draw Road (Perspective)
        val roadPath = Path().apply {
            moveTo(centerX - 40.dp.toPx(), groundY)
            lineTo(centerX + 40.dp.toPx(), groundY)
            lineTo(centerX + 120.dp.toPx(), h)
            lineTo(centerX - 120.dp.toPx(), h)
            close()
        }
        drawPath(
            path = roadPath,
            brush = Brush.verticalGradient(listOf(onSurface.copy(alpha = 0.05f), onSurface.copy(alpha = 0.15f)))
        )

        // 2. Animate Bike (Front view)
        withTransform({
            // Tilt around the tire contact point on the ground
            rotate(degrees = leanAngle + (if(targetLean != 0f) breathing * 2f else 0f), pivot = Offset(centerX, groundY))
        }) {
            // Shadow
            drawOval(
                color = Color.Black.copy(alpha = 0.2f),
                topLeft = Offset(centerX - 20.dp.toPx(), groundY - 5.dp.toPx()),
                size = Size(40.dp.toPx(), 10.dp.toPx())
            )

            // Tire
            drawRoundRect(
                color = Color(0xFF1A1C1E),
                topLeft = Offset(centerX - 14.dp.toPx(), groundY - 60.dp.toPx()),
                size = Size(28.dp.toPx(), 60.dp.toPx()),
                cornerRadius = CornerRadius(14.dp.toPx())
            )

            // Forks
            val forkColor = Color(0xFF454749)
            drawLine(forkColor, Offset(centerX - 18.dp.toPx(), groundY - 40.dp.toPx()), Offset(centerX - 22.dp.toPx(), groundY - 130.dp.toPx()), strokeWidth = 8.dp.toPx())
            drawLine(forkColor, Offset(centerX + 18.dp.toPx(), groundY - 40.dp.toPx()), Offset(centerX + 22.dp.toPx(), groundY - 130.dp.toPx()), strokeWidth = 8.dp.toPx())

            // Main Fairing Body
            val fairingPath = Path().apply {
                moveTo(centerX, groundY - 150.dp.toPx())
                lineTo(centerX - 45.dp.toPx(), groundY - 120.dp.toPx())
                lineTo(centerX - 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 45.dp.toPx(), groundY - 120.dp.toPx())
                close()
            }
            drawPath(fairingPath, color = primary)

            // Windshield
            val shieldPath = Path().apply {
                moveTo(centerX - 25.dp.toPx(), groundY - 135.dp.toPx())
                lineTo(centerX + 25.dp.toPx(), groundY - 135.dp.toPx())
                lineTo(centerX + 15.dp.toPx(), groundY - 165.dp.toPx())
                lineTo(centerX - 15.dp.toPx(), groundY - 165.dp.toPx())
                close()
            }
            drawPath(shieldPath, color = primary.copy(alpha = 0.4f))

            // Headlight
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = 12.dp.toPx(),
                center = Offset(centerX, groundY - 110.dp.toPx())
            )

            // Handlebars
            drawLine(
                color = Color(0xFF2D2F31),
                start = Offset(centerX - 70.dp.toPx(), groundY - 135.dp.toPx()),
                end = Offset(centerX + 70.dp.toPx(), groundY - 135.dp.toPx()),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round
            )

            // Mirror Mounts & Mirrors
            drawRoundRect(Color(0xFF2D2F31), Offset(centerX - 75.dp.toPx(), groundY - 155.dp.toPx()), Size(18.dp.toPx(), 12.dp.toPx()), CornerRadius(4.dp.toPx()))
            drawRoundRect(Color(0xFF2D2F31), Offset(centerX + 57.dp.toPx(), groundY - 155.dp.toPx()), Size(18.dp.toPx(), 12.dp.toPx()), CornerRadius(4.dp.toPx()))
        }
    }
}