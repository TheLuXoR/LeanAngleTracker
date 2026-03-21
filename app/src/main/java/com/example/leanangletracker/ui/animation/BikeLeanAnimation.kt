package com.example.leanangletracker.ui.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos

enum class BikeLean(val angle: Float) {
    UPRIGHT(0f),
    LEFT(-35f),
    RIGHT(35f),
    DONE(0f)
}

private val SineEaseInOut = Easing { fraction ->
    ((1 - cos(fraction * PI)) / 2).toFloat()
}


@Composable
fun IntroBikeLeanAnimation(
modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f),
duration: Float = 600f
) {

    var start by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        start = true
    }
    val scale by animateFloatAsState(
        targetValue = if (start) 1f else 0.2f,
        animationSpec = keyframes {
            durationMillis= duration.toInt()
        }
    )

    val angle by animateFloatAsState(
        targetValue = if (start) BikeLean.UPRIGHT.angle else BikeLean.RIGHT.angle,
        animationSpec = keyframes {
            durationMillis= duration.toInt()
            BikeLean.RIGHT.angle    at (duration*0.3f).toInt()
            BikeLean.RIGHT.angle * 0.5f at (duration*0.7f).toInt()
        }
    )

    val offset by animateOffsetAsState(
        targetValue = if (start)Offset(0f,0.0f) else Offset(0.5f, -2.15f),
        animationSpec = keyframes {
            durationMillis= duration.toInt()
            Offset(-0.2f, -0.5f) at (duration*0.4f).toInt()
            Offset(-0.05f, -0.1f) at (duration*0.7f).toInt()
        }
    )

    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Road(modifier = modifier, onSurface = onSurface)
    Bike(modifier = modifier, scale = scale, angle= angle, offset = offset)
}

@Composable
fun CalibrationBikeLeanAnimation(
    modifier: Modifier = Modifier,
    bikeAnimationFrom: BikeLean,
    bikeAnimationTo: BikeLean,
    duration: Float = 600f
) {

    var start by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        start = true
    }

    val angle by animateFloatAsState(
        targetValue = if (start) bikeAnimationFrom.angle else bikeAnimationTo.angle,
        animationSpec = keyframes {
            durationMillis= duration.toInt()
        }
    )


    val primary = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface

    Bike(modifier = modifier, angle= angle)
}


@Preview(heightDp = 200, widthDp = 200)
@Composable
private fun Bike(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    angle: Float = 0f,
    offset: Offset = Offset(0f,0f),
    primary: Color = MaterialTheme.colorScheme.primary
) {


    Canvas(modifier = modifier.scale(scale).rotate(angle)) {
        var w = size.width
        var h = size.height
        val groundY = size.height * 0.85f + offset.y * size.height
        val centerX = size.width /2 + offset.x * size.width

        // Shadow
        drawOval(
                color = Color.Black.copy(.3f),
                topLeft = Offset(centerX - w * 0.05f, groundY - h * 0.0125f),
                size = Size(w * 0.1f, h * 0.025f)
            )

        // Tire
        drawRoundRect(
                color = Color(0xFF1A1C1E),
                topLeft = Offset(centerX - w * 0.035f, groundY - h * 0.15f),
                size = Size(w * 0.07f, h * 0.15f),
                cornerRadius = CornerRadius(w * 0.035f)
            )

        // Forks
        val forkColor = Color(0xFF454749)
        val forkWidth = w * 0.02f
        drawLine(
                forkColor,
                Offset(centerX - w * 0.045f, groundY - h * 0.05f),
                Offset(centerX - w * 0.055f, groundY - h * 0.3f),
                strokeWidth = forkWidth
            )
        drawLine(
                forkColor,
                Offset(centerX + w * 0.045f, groundY - h * 0.05f),
                Offset(centerX + w * 0.055f, groundY - h * 0.3f),
                strokeWidth = forkWidth
            )

        // Handlebars
        drawLine(
            color = Color(0xFF2D2F31),
            start = Offset(centerX - w * 0.15f, groundY - h * 0.3375f),
            end = Offset(centerX + w * 0.15f, groundY - h * 0.3375f),
            strokeWidth = w * 0.015f,
            cap = StrokeCap.Round
        )
        // Main Fairing Body
        val fairingPath = Path().apply {
            moveTo(centerX, groundY - h * 0.375f)
            lineTo(centerX - w * 0.1125f, groundY - h * 0.3f)
            lineTo(centerX - w * 0.0875f, groundY - h * 0.175f)
            lineTo(centerX + w * 0.0875f, groundY - h * 0.175f)
            lineTo(centerX + w * 0.1125f, groundY - h * 0.3f)
            close()
        }
        drawPath(fairingPath, color = primary)


        // Windshield
        val shieldPath = Path().apply {
            moveTo(centerX - w * 0.0625f, groundY - h * 0.3375f)
            lineTo(centerX + w * 0.0625f, groundY - h * 0.3375f)
            lineTo(centerX + w * 0.0375f, groundY - h * 0.4125f)
            lineTo(centerX - w * 0.0375f, groundY - h * 0.4125f)
            close()
        }
        drawPath(shieldPath, color = primary.copy(alpha = 0.4f))

        // Headlight
        drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = w * 0.03f,
                center = Offset(centerX, groundY - h * 0.275f)
            )

        // Mirror Mounts & Mirrors
        drawRoundRect(
            Color(0xFF2D2F31),
            Offset(centerX - w * 0.18f, groundY - h * 0.3875f),
            Size(w * 0.045f, h * 0.03f),
            CornerRadius(w * 0.01f)
        )
        drawRoundRect(
            Color(0xFF2D2F31),
            Offset(centerX + w * 0.138f, groundY - h * 0.3875f),
            Size(w * 0.045f, h * 0.03f),
        )
    }
}


@Composable
private fun Road(
    modifier: Modifier = Modifier,
    onSurface: Color = Color.Black,
) {
    Canvas(modifier = modifier) {
        fun x(p: Float) = p * size.width
        fun y(p: Float) = p * size.height

        val roadPath = Path().apply {
            smoothCurve(
                listOf(
                    Offset(x(.38f), y(1f)),
                    Offset(x(.35f), y(0.75f)),
                    Offset(x(.3f), y(0.4f)),
                    Offset(x(.5f), y(0.3f)),
                    Offset(x(.7f), y(0.25f)),
                    Offset(x(.9f), y(0.2f)),
                    Offset(x(.8f), y(0.1f)),
                    Offset(x(.65f), y(0f)),
                    Offset(x(.68f), y(0f)),
                    Offset(x(.92f), y(0.15f)),
                    Offset(x(.93f), y(0.23f)),
                    Offset(x(.7f), y(0.3f)),
                    Offset(x(.5f), y(0.38f)),
                    Offset(x(.5f), y(0.48f)),
                    Offset(x(.65f), y(1f)),
                )
            )
            close()
        }

        drawPath(
            path = roadPath,
            brush = Brush.verticalGradient(
                listOf(
                    onSurface.copy(alpha = 0.15f),
                    onSurface.copy(alpha = 0.9f),
                    onSurface.copy(alpha = 0.9f),
                    onSurface.copy(alpha = 0.9f),
                    onSurface.copy(alpha = 0.0f)
                )
            )
        )
    }
}

private fun Path.smoothCurve(points: List<Offset>) {
    if (points.size < 2) return
    moveTo(points.first().x, points.first().y)
    for (i in 0 until points.size - 1) {
        val p0 = points[i]
        val p1 = points[i + 1]
        val midX = (p0.x + p1.x) / 2f
        val midY = (p0.y + p1.y) / 2f
        quadraticBezierTo(p0.x, p0.y, midX, midY)
    }
    val last = points.last()
    lineTo(last.x, last.y)
}
