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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.TrackingUiState
import com.example.leanangletracker.ui.calibration.CalibrationWizard
import com.example.leanangletracker.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
@Preview
private fun Gauge() {
    TachoGauge(10f, 10f, 10f)
}

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
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Finish",
                                tint = ErrorRed
                            )
                        }
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
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
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight()
                    )
                    LeanHistoryGraph(
                        values = trackingState.leanHistoryDeg,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            } else {
                TachoGauge(
                    currentDeg = trackingState.leanAngleDeg,
                    maxLeftDeg = trackingState.maxLeftDeg,
                    maxRightDeg = trackingState.maxRightDeg,
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxWidth()
                )
                Spacer(Modifier.weight(1f))
                LeanHistoryGraph(
                    values = trackingState.leanHistoryDeg,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
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
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .aspectRatio(2.0f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize(0.85f)) {
                    val center = Offset(size.width / 2f, size.height)
                    val radius = min(size.width / 2f, size.height)
                    val maxDisplay = 65f

                    // Gauge Background
                    drawArc(
                        color = GaugeBackground,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2)
                    )
                    // Border
                    drawArc(
                        color = Color.Blue.copy(0.3f),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
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
                    drawLine(
                        SecondaryBlue.copy(alpha = 0.4f),
                        center,
                        angleToTip(maxLeftDeg, 0.9f),
                        4.dp.toPx(),
                        StrokeCap.Round
                    )
                    drawLine(
                        SecondaryBlue.copy(alpha = 0.4f),
                        center,
                        angleToTip(maxRightDeg, 0.9f),
                        4.dp.toPx(),
                        StrokeCap.Round
                    )

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

                // Max Values
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(24.dp)
                ) {
                    MaxValItem("MAX L", abs(maxLeftDeg))
                    Spacer(modifier.weight(1f))
                    MaxValItem("MAX R", maxRightDeg)
                }
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
            val lowerBound = values.maxOrNull()?.coerceAtLeast(10f) ?: 10f
            val upperBound = values.minOrNull()?.coerceAtMost(-10f) ?: -10f
            val amplitude = maxOf(20f, abs(upperBound), abs(lowerBound))

            Row {
                Text(
                    stringResource(R.string.history_title).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.history_upper_bound, upperBound),
                    modifier = Modifier
                        .padding(end = 8.dp, top = 2.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }


            Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                fun yFor(deg: Float): Float = centerY - (deg / amplitude) * (height * 0.45f)

                // Grid Lines
                val roadY = yFor(upperBound)
                drawLine(
                    Color(0x66FFFFFF),
                    Offset(0f, roadY),
                    Offset(width, roadY),
                    4f,
                    StrokeCap.Round
                )
                drawLine(Color(0xCCFFFFFF), Offset(0f, centerY), Offset(width, centerY), 2f)
                drawLine(
                    Color(0x66FFFFFF),
                    Offset(0f, yFor(lowerBound)),
                    Offset(width, yFor(lowerBound)),
                    2f
                )


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
            Text(
                stringResource(R.string.history_lower_bound, lowerBound),
                modifier = Modifier
                    .padding(end = 8.dp).align(Alignment.End),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
