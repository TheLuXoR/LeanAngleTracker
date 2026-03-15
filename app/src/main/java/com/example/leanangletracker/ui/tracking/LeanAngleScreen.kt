package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.TrackingUiState
import com.example.leanangletracker.ui.calibration.CalibrationWizard
import com.example.leanangletracker.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun LeanAngleScreen(
    trackingState: TrackingUiState,
    calibrationState: CalibrationUiState,
    onOpenSettings: () -> Unit,
    onFinishRide: () -> Unit,
    modifier: Modifier = Modifier,
    onCaptureUpright: () -> Unit,
    onStartLeftMeasurement: () -> Unit,
    onStartRightMeasurement: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (!calibrationState.isCalibrated) {
            CalibrationWizard(
                state = calibrationState,
                onCaptureUpright = onCaptureUpright,
                onStartLeftMeasurement = onStartLeftMeasurement,
                onStartRightMeasurement = onStartRightMeasurement
            )
            return@Box
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.screen_title_lean_angle),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (trackingState.gpsActive) {
                        Text(
                            text = "GPS ACTIVE",
                            style = MaterialTheme.typography.labelMedium,
                            color = AccentGreen
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (trackingState.hasTrackData) {
                        IconButton(
                            onClick = onFinishRide,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(ErrorRed.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Finish", tint = ErrorRed)
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    TachoGauge(
                        currentDeg = trackingState.leanAngleDeg,
                        maxLeftDeg = trackingState.maxLeftDeg,
                        maxRightDeg = trackingState.maxRightDeg,
                        speedKmh = trackingState.speedKmh,
                        showSpeed = trackingState.gpsActive,
                        modifier = Modifier.weight(1.2f).fillMaxHeight()
                    )
                    LeanHistoryGraph(
                        values = trackingState.leanHistoryDeg,
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            } else {
                TachoGauge(
                    currentDeg = trackingState.leanAngleDeg,
                    maxLeftDeg = trackingState.maxLeftDeg,
                    maxRightDeg = trackingState.maxRightDeg,
                    speedKmh = trackingState.speedKmh,
                    showSpeed = trackingState.gpsActive,
                    modifier = Modifier.weight(1.5f).fillMaxWidth()
                )
                LeanHistoryGraph(
                    values = trackingState.leanHistoryDeg,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TachoGauge(
    currentDeg: Float,
    maxLeftDeg: Float,
    maxRightDeg: Float,
    speedKmh: Float,
    showSpeed: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.45f
                val maxDisplay = 65f

                // Gauge Background
                drawCircle(
                    color = GaugeBackground,
                    radius = radius,
                    center = center
                )
                
                // Border
                drawCircle(
                    color = Color.Blue.copy(0.3f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 4.dp.toPx())
                )

                // Scale marks
                for (mark in -60..60 step 10) {
                    val theta = Math.toRadians(mark.toDouble() - 90.0)
                    val isMajor = mark % 30 == 0
                    val outer = Offset(
                        x = center.x + (radius * 0.95f * cos(theta)).toFloat(),
                        y = center.y + (radius * 0.95f * sin(theta)).toFloat()
                    )
                    val innerFactor = if (isMajor) 0.82f else 0.88f
                    val inner = Offset(
                        x = center.x + (radius * innerFactor * cos(theta)).toFloat(),
                        y = center.y + (radius * innerFactor * sin(theta)).toFloat()
                    )
                    drawLine(
                        color = if (mark == 0) AccentGreen else GaugeScale.copy(alpha = 0.6f),
                        start = inner,
                        end = outer,
                        strokeWidth = if (isMajor) 4.dp.toPx() else 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }

                fun angleToTip(deg: Float, lengthFactor: Float): Offset {
                    val clamped = deg.coerceIn(-maxDisplay, maxDisplay)
                    val theta = Math.toRadians(clamped.toDouble() - 90.0)
                    return Offset(
                        x = center.x + (radius * lengthFactor * cos(theta)).toFloat(),
                        y = center.y + (radius * lengthFactor * sin(theta)).toFloat()
                    )
                }

                // Max indications
                drawLine(SecondaryBlue.copy(alpha = 0.4f), center, angleToTip(maxLeftDeg, 0.9f), 4.dp.toPx(), StrokeCap.Round)
                drawLine(SecondaryBlue.copy(alpha = 0.4f), center, angleToTip(maxRightDeg, 0.9f), 4.dp.toPx(), StrokeCap.Round)
                
                // Current needle
                drawLine(
                    brush = Brush.linearGradient(listOf(GaugeNeedle, PrimaryOrange)),
                    start = center,
                    end = angleToTip(currentDeg, 0.88f),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // Center hub
                drawCircle(color = Color.White, radius = 12.dp.toPx(), center = center)
                drawCircle(color = GaugeNeedle, radius = 6.dp.toPx(), center = center)
            }

            // Speed Display
            if (showSpeed) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = speedKmh.toInt().toString(),
                        style = MaterialTheme.typography.headlineLarge.copy(fontSize = 48.sp),
                        color = Color.White
                    )
                    Text(
                        text = "km/h",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }

            // Degree Display
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val direction = when {
                    currentDeg > 1f -> "RIGHT"
                    currentDeg < -1f -> "LEFT"
                    else -> "CENTER"
                }
                Text(
                    text = "${abs(currentDeg).toInt()}°",
                    style = MaterialTheme.typography.headlineLarge.copy(fontSize = 56.sp),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = direction,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 2.sp
                )
            }

            // Max Values
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp)
            ) {
                MaxValItem("MAX L", abs(maxLeftDeg))
                MaxValItem("MAX R", maxRightDeg)
            }
        }
    }
}

@Composable
private fun MaxValItem(label: String, value: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
        Text(
            text = "${value.toInt()}°",
            style = MaterialTheme.typography.titleLarge,
            color = SecondaryBlue
        )
    }
}

@Composable
private fun LeanHistoryGraph(values: List<Float>, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.history_title).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            
            Spacer(modifier = Modifier.height(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                val upperBound = values.maxOrNull()?.coerceAtLeast(10f) ?: 10f
                val lowerBound = values.minOrNull()?.coerceAtMost(-10f) ?: -10f
                val amplitude = maxOf(20f, abs(upperBound), abs(lowerBound))

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val width = size.width
                    val height = size.height
                    val centerY = height / 2f

                    fun yFor(deg: Float): Float = centerY - (deg / amplitude) * (height * 0.45f)

                    // Grid Lines
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, centerY), Offset(width, centerY), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, yFor(amplitude/2)), Offset(width, yFor(amplitude/2)), 1.dp.toPx())
                    drawLine(Color.White.copy(alpha = 0.9f), Offset(0f, yFor(-amplitude/2)), Offset(width, yFor(-amplitude/2)), 1.dp.toPx())

                    if (values.size >= 2) {
                        val stepX = width / (values.size - 1)
                        val path = Path()
                        values.forEachIndexed { index, value ->
                            val x = index * stepX
                            val y = yFor(value.coerceIn(-amplitude, amplitude))
                            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }
                        drawPath(
                            path = path,
                            brush = Brush.verticalGradient(
                                listOf(PrimaryOrange, SecondaryBlue)
                            ),
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }
            }
        }
    }
}
