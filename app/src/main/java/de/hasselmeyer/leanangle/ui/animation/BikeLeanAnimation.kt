package de.hasselmeyer.leanangle.ui.animation

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class BikeLean(val angle: Float) {
    UPRIGHT(0f),
    LEFT(-35f),
    RIGHT(35f),
    DONE(0f)
}

private val SineEaseInOut = Easing { fraction ->
    ((1 - cos(fraction * PI)) / 2).toFloat()
}

private const val CALIBRATION_ANGLE_VISUAL_SCALE = 3.25f
private const val INTRO_BIKE_FADE_IN_FRACTION = 0.25f


@Preview(widthDp = 288, heightDp = 288)
@Composable
fun IntroBikeLeanAnimation(
modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1f),
duration: Float = 600f,
startAnimation: Boolean = true,
) {

    var roadRevealStarted by remember { mutableStateOf(false) }
    var bikeAnimationStarted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        roadRevealStarted = true
    }
    LaunchedEffect(startAnimation) {
        if (startAnimation) {
            bikeAnimationStarted = true
        }
    }
    val scale by animateFloatAsState(
        targetValue = if (bikeAnimationStarted) 1f else 0.2f,
        animationSpec = keyframes {
            durationMillis= duration.toInt()
        }
    )

    val angle by animateFloatAsState(
        targetValue = if (bikeAnimationStarted) BikeLean.UPRIGHT.angle else BikeLean.RIGHT.angle,
        animationSpec = keyframes {
            durationMillis= duration.toInt()
            BikeLean.RIGHT.angle    at (duration*0.3f).toInt()
            BikeLean.RIGHT.angle * 0.5f at (duration*0.7f).toInt()
        }
    )

    val offset by animateOffsetAsState(
        targetValue = if (bikeAnimationStarted)Offset(0f,0.0f) else Offset(0.5f, -2.15f),
        animationSpec = keyframes {
            durationMillis= duration.toInt()
            Offset(-0.2f, -0.5f) at (duration*0.4f).toInt()
            Offset(-0.05f, -0.1f) at (duration*0.7f).toInt()
        }
    )
    val bikeAlpha by animateFloatAsState(
        targetValue = if (bikeAnimationStarted) 1f else 0f,
        animationSpec = keyframes {
            durationMillis = duration.toInt()
            1f at (duration * INTRO_BIKE_FADE_IN_FRACTION).toInt()
        },
        label = "introBikeAlpha"
    )
    val roadRevealProgress by animateFloatAsState(
        targetValue = if (roadRevealStarted) 1f else 0f,
        animationSpec = tween(
            durationMillis = (duration * 1.25f).toInt(),
            easing = FastOutSlowInEasing
        ),
        label = "introRoadReveal"
    )

    val onSurface = MaterialTheme.colorScheme.onSurface

    Road(
        modifier = modifier,
        onSurface = onSurface,
        revealProgress = roadRevealProgress
    )
    Bike(
        modifier = modifier,
        scale = scale,
        angle = angle,
        offset = offset,
        alpha = bikeAlpha
    )
}

@Composable
fun CalibrationBikeLeanAnimation(
    modifier: Modifier = Modifier,
    measuredAngleDeg: Float,
    targetDirection: BikeLean? = null,
    showReturnUpright: Boolean = false
) {
    val angle by animateFloatAsState(
        targetValue = (measuredAngleDeg * CALIBRATION_ANGLE_VISUAL_SCALE).coerceIn(-45f, 45f),
        animationSpec = tween(durationMillis = 100),
        label = "calibrationBikeAngle"
    )

    Box(modifier = modifier) {
        if (showReturnUpright) {
            CalibrationReturnUprightGuidance(
                modifier = Modifier.fillMaxSize(),
                measuredAngleDeg = measuredAngleDeg
            )
        } else if (targetDirection == BikeLean.LEFT || targetDirection == BikeLean.RIGHT) {
            CalibrationDirectionArrow(
                modifier = Modifier.fillMaxSize(),
                direction = targetDirection,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        // Pivot for calibration is at the tire contact point (approx 0.85 y)
        Bike(
            modifier = Modifier.fillMaxSize(),
            angle = angle,
            transformOrigin = TransformOrigin(0.5f, 0.85f)
        )
    }
}

@Composable
private fun CalibrationReturnUprightGuidance(
    modifier: Modifier,
    measuredAngleDeg: Float
) {
    val pulseTransition = rememberInfiniteTransition(label = "returnUprightPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650),
            repeatMode = RepeatMode.Reverse
        ),
        label = "returnUprightAlpha"
    )
    val successColor = Color(0xFF22C55E)
    val returnDirection = if (measuredAngleDeg <= 0f) BikeLean.RIGHT else BikeLean.LEFT

    Canvas(modifier = modifier) {
        val centerX = size.width / 2f
        drawLine(
            color = successColor.copy(alpha = pulseAlpha * 0.65f),
            start = Offset(centerX, size.height * 0.17f),
            end = Offset(centerX, size.height * 0.88f),
            strokeWidth = size.minDimension * 0.018f,
            cap = StrokeCap.Round
        )
        drawCircle(
            color = successColor.copy(alpha = pulseAlpha * 0.18f),
            radius = size.minDimension * 0.38f,
            center = Offset(centerX, size.height * 0.56f),
            style = Stroke(width = size.minDimension * 0.035f)
        )
    }

    CalibrationDirectionArrow(
        modifier = modifier,
        direction = returnDirection,
        color = successColor.copy(alpha = pulseAlpha)
    )
}

@Composable
private fun CalibrationDirectionArrow(
    modifier: Modifier,
    direction: BikeLean,
    color: Color
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.34f
        val center = Offset(size.width / 2f, size.height * 0.62f)
        val isLeft = direction == BikeLean.LEFT
        val startAngleDeg = if (isLeft) -55f else -125f
        val sweepAngleDeg = if (isLeft) -75f else 75f
        val endAngleDeg = startAngleDeg + sweepAngleDeg
        val strokeWidth = size.minDimension * 0.025f

        drawArc(
            color = color,
            startAngle = startAngleDeg,
            sweepAngle = sweepAngleDeg,
            useCenter = false,
            topLeft = Offset(center.x - radius, center.y - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        val endRadians = Math.toRadians(endAngleDeg.toDouble())
        val tip = Offset(
            x = center.x + radius * cos(endRadians).toFloat(),
            y = center.y + radius * sin(endRadians).toFloat()
        )
        val tangentAngleDeg = endAngleDeg + if (isLeft) -90f else 90f
        val arrowLength = size.minDimension * 0.10f
        val wingAngleDeg = 35f

        fun arrowWing(angleDeg: Float): Offset {
            val radians = Math.toRadians(angleDeg.toDouble())
            return Offset(
                x = tip.x - arrowLength * cos(radians).toFloat(),
                y = tip.y - arrowLength * sin(radians).toFloat()
            )
        }

        drawLine(
            color = color,
            start = tip,
            end = arrowWing(tangentAngleDeg - wingAngleDeg),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = tip,
            end = arrowWing(tangentAngleDeg + wingAngleDeg),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}


@Preview(heightDp = 200, widthDp = 200)
@Composable
private fun Bike(
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    angle: Float = 0f,
    offset: Offset = Offset(0f,0f),
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    primary: Color = MaterialTheme.colorScheme.primary,
    alpha: Float = 1f,
) {


    Canvas(
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            rotationZ = angle
            this.alpha = alpha
            this.transformOrigin = transformOrigin
        }
    ) {
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
            lineTo(centerX - w * 0.0925f, groundY - h * 0.3f)
            lineTo(centerX - w * 0.0775f, groundY - h * 0.175f)
            lineTo(centerX + w * 0.0775f, groundY - h * 0.175f)
            lineTo(centerX + w * 0.0925f, groundY - h * 0.3f)
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
    revealProgress: Float = 1f,
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

        val startRadius = size.minDimension / 3f
        val endRadius = hypot(size.width, size.height) / 2f
        val revealRadius = startRadius + (endRadius - startRadius) * revealProgress
        val revealPath = Path().apply {
            addOval(
                Rect(
                    left = size.width / 2f - revealRadius,
                    top = size.height / 2f - revealRadius,
                    right = size.width / 2f + revealRadius,
                    bottom = size.height / 2f + revealRadius
                )
            )
        }

        clipPath(revealPath) {
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
