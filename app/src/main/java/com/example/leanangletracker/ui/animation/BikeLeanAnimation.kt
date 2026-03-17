package com.example.leanangletracker.ui.animation

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationStep
import kotlin.math.cos
import kotlin.math.sin

private val SineEaseInOut = Easing { fraction ->
    ((1 - Math.cos(fraction * Math.PI)) / 2).toFloat()
}

@Composable
fun BikeLeanAnimation(step: CalibrationStep, modifier: Modifier = Modifier, approachProgress: Float = 1f) {
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
            brush = Brush.verticalGradient(listOf(onSurface.copy(alpha = 0.05f), onSurface.copy(alpha = 0.15f)))
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
            rotate(degrees = leanAngle + (if (targetLean != 0f) breathing * 1.5f else 0f), pivot = Offset(centerX, groundY))
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
            drawLine(forkColor, Offset(centerX - 18.dp.toPx(), groundY - 40.dp.toPx()), Offset(centerX - 22.dp.toPx(), groundY - 130.dp.toPx()), 8.dp.toPx())
            drawLine(forkColor, Offset(centerX + 18.dp.toPx(), groundY - 40.dp.toPx()), Offset(centerX + 22.dp.toPx(), groundY - 130.dp.toPx()), 8.dp.toPx())
            
            val fairingPath = Path().apply {
                moveTo(centerX, groundY - 150.dp.toPx())
                lineTo(centerX - 45.dp.toPx(), groundY - 120.dp.toPx())
                lineTo(centerX - 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 35.dp.toPx(), groundY - 70.dp.toPx())
                lineTo(centerX + 45.dp.toPx(), groundY - 120.dp.toPx())
                close()
            }
            drawPath(fairingPath, color = primary)
            drawCircle(Color.White.copy(alpha = 0.9f), 12.dp.toPx(), Offset(centerX, groundY - 110.dp.toPx()))
            drawLine(Color(0xFF2D2F31), Offset(centerX - 70.dp.toPx(), groundY - 135.dp.toPx()), Offset(centerX + 70.dp.toPx(), groundY - 135.dp.toPx()), 6.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun IntroBikeAnimation(modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "intro-bike")
    
    // Animate from 0 to -45 degrees slowly
    val leanAngle by infinite.animateFloat(
        initialValue = 0f,
        targetValue = -45f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = SineEaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "leanAngle"
    )

    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val centerX = w / 2f
        val groundY = h * 0.85f

        // 1. Arc Indicator (Protractor style)
        val arcRadius = 150.dp.toPx()
        drawArc(
            color = onSurface.copy(alpha = 0.1f),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - arcRadius, groundY - arcRadius),
            size = Size(arcRadius * 2, arcRadius * 2),
            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
        )
        
        // Scale marks
        for (a in 0..180 step 15) {
            val rad = Math.toRadians(a.toDouble())
            val isMajor = a % 45 == 0
            val length = if (isMajor) 15.dp.toPx() else 8.dp.toPx()
            val startX = centerX + (arcRadius * cos(rad)).toFloat()
            val startY = groundY - (arcRadius * sin(rad)).toFloat()
            val endX = centerX + ((arcRadius + length) * cos(rad)).toFloat()
            val endY = groundY - ((arcRadius + length) * sin(rad)).toFloat()
            
            drawLine(
                color = onSurface.copy(alpha = if (isMajor) 0.3f else 0.15f),
                start = Offset(startX, startY),
                end = Offset(endX, endY),
                strokeWidth = (if (isMajor) 3.dp else 1.5.dp).toPx()
            )
        }

        // 2. Active angle indicator line
        val indRad = Math.toRadians((leanAngle - 90).toDouble())
        drawLine(
            color = secondary.copy(alpha = 0.4f),
            start = Offset(centerX, groundY),
            end = Offset(centerX + (arcRadius * sin(indRad)).toFloat(), groundY - (arcRadius * cos(indRad)).toFloat()),
            strokeWidth = 4.dp.toPx(),
            cap = StrokeCap.Round
        )

        // 3. Iconic Bike (App Icon Style)
        withTransform({
            rotate(degrees = leanAngle, pivot = Offset(centerX, groundY))
        }) {
            // Shadow
            drawOval(Color.Black.copy(alpha = 0.3f), Offset(centerX - 30.dp.toPx(), groundY - 8.dp.toPx()), Size(60.dp.toPx(), 16.dp.toPx()))

            // Tire
            drawRoundRect(Color(0xFF121416), Offset(centerX - 18.dp.toPx(), groundY - 70.dp.toPx()), Size(36.dp.toPx(), 70.dp.toPx()), CornerRadius(18.dp.toPx()))

            // Fairing Body (Simplified, Iconic)
            val fairingPath = Path().apply {
                moveTo(centerX, groundY - 170.dp.toPx())
                lineTo(centerX - 55.dp.toPx(), groundY - 140.dp.toPx())
                lineTo(centerX - 40.dp.toPx(), groundY - 60.dp.toPx())
                lineTo(centerX + 40.dp.toPx(), groundY - 60.dp.toPx())
                lineTo(centerX + 55.dp.toPx(), groundY - 140.dp.toPx())
                close()
            }
            drawPath(fairingPath, color = primary)
            
            // Design highlight
            val detailPath = Path().apply {
                moveTo(centerX - 40.dp.toPx(), groundY - 130.dp.toPx())
                lineTo(centerX, groundY - 155.dp.toPx())
                lineTo(centerX + 40.dp.toPx(), groundY - 130.dp.toPx())
            }
            drawPath(detailPath, color = Color.White.copy(alpha = 0.2f), style = Stroke(4.dp.toPx(), cap = StrokeCap.Round))

            // Headlight
            drawCircle(Color.White, 16.dp.toPx(), Offset(centerX, groundY - 120.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.2f), 24.dp.toPx(), Offset(centerX, groundY - 120.dp.toPx()))

            // Handlebars
            drawLine(Color(0xFF2D2F31), Offset(centerX - 85.dp.toPx(), groundY - 145.dp.toPx()), Offset(centerX + 85.dp.toPx(), groundY - 145.dp.toPx()), 10.dp.toPx(), StrokeCap.Round)
        }
    }
}
