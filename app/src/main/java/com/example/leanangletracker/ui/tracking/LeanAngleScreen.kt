package com.example.leanangletracker.ui.tracking

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF071A2E), Color(0xFF0D2D4F), Color(0xFF071A2E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
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

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GaugeRoadView(state = trackingState, modifier = Modifier.weight(1f).fillMaxSize())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onOpenSettings, modifier = Modifier.height(56.dp)) {
                        Text(stringResource(R.string.action_settings), fontSize = 18.sp)
                    }
                    if (trackingState.hasTrackData) {
                        Button(onClick = onFinishRide, modifier = Modifier.height(56.dp)) {
                            Text(stringResource(R.string.action_finish_ride), fontSize = 16.sp)
                        }
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.screen_title_lean_angle), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (trackingState.hasTrackData) {
                            Button(onClick = onFinishRide, modifier = Modifier.height(50.dp)) {
                                Text(stringResource(R.string.action_finish_ride), fontSize = 14.sp)
                            }
                        }
                        Button(onClick = onOpenSettings, modifier = Modifier.height(50.dp)) {
                            Text(stringResource(R.string.action_settings), fontSize = 16.sp)
                        }
                    }
                }
                GaugeRoadView(state = trackingState, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun GaugeRoadView(state: TrackingUiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            TachoGauge(
                currentDeg = state.leanAngleDeg,
                maxLeftDeg = state.maxLeftDeg,
                maxRightDeg = state.maxRightDeg,
                speedKmh = state.speedKmh,
                showSpeed = state.gpsActive,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
            )

            LeanHistoryGraph(
                values = state.leanHistoryDeg,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(220.dp)
            )
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.height(320.dp).width(320.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f
            val maxDisplay = 65f

            drawCircle(color = Color(0xFFEEF6FF), radius = radius * 1.04f, center = center)
            drawCircle(color = Color(0xFF17263A), radius = radius, center = center)

            for (mark in -60..60 step 10) {
                val theta = Math.toRadians(mark.toDouble() - 90.0)
                val outer = Offset(
                    x = center.x + (radius * 0.93f * cos(theta)).toFloat(),
                    y = center.y + (radius * 0.93f * sin(theta)).toFloat()
                )
                val innerFactor = if (mark % 30 == 0) 0.72f else 0.79f
                val inner = Offset(
                    x = center.x + (radius * innerFactor * cos(theta)).toFloat(),
                    y = center.y + (radius * innerFactor * sin(theta)).toFloat()
                )
                drawLine(
                    color = if (mark == 0) Color(0xFF40E0A0) else Color(0xFFD8E8FF),
                    start = inner,
                    end = outer,
                    strokeWidth = if (mark % 30 == 0) 6f else 3f,
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

            drawLine(Color(0x6693C5FD), center, angleToTip(maxLeftDeg, 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0x6693C5FD), center, angleToTip(maxRightDeg, 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0xFFFF4D6D), center, angleToTip(currentDeg, 0.86f), 12f, StrokeCap.Round)

            drawCircle(color = Color(0xFF99FFFFFF), radius = 18f, center = center)
            drawCircle(color = Color(0xFFFF4D6D), radius = 8f, center = center)
        }

        if (showSpeed) {
            Text(
                text = stringResource(R.string.value_speed_kmh, speedKmh),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 105.dp)
                    .background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                color = Color(0xFF8BFFDA),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp
            )
        }

        val direction = when {
            currentDeg > 1f -> stringResource(R.string.direction_right)
            currentDeg < -1f -> stringResource(R.string.direction_left)
            else -> stringResource(R.string.direction_up)
        }

        Text(
            text = stringResource(R.string.value_current_degrees_with_direction, currentDeg, direction),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (showSpeed) 60.dp else 16.dp)
                .background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.value_max_left, abs(maxLeftDeg)), color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(12.dp))
            Text(stringResource(R.string.value_max_right, maxRightDeg), color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LeanHistoryGraph(values: List<Float>, modifier: Modifier = Modifier) {
    val upperBound = values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val lowerBound = values.minOrNull()?.coerceAtMost(0f) ?: 0f
    val amplitude = maxOf(10f, abs(upperBound), abs(lowerBound))

    Box(modifier = modifier.background(Color(0xFF2A3747), RoundedCornerShape(12.dp))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            fun yFor(deg: Float): Float = centerY - (deg / amplitude) * (height * 0.44f)

            val roadY = yFor(upperBound)
            drawLine(Color(0xCCFFFFFF), Offset(0f, roadY), Offset(width, roadY), 4f, StrokeCap.Round)
            drawLine(Color(0x66FFFFFF), Offset(0f, centerY), Offset(width, centerY), 2f)
            drawLine(Color(0x66FFFFFF), Offset(0f, yFor(lowerBound)), Offset(width, yFor(lowerBound)), 2f)

            if (values.size >= 2) {
                val stepX = width / (values.size - 1)
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = index * stepX
                    val y = yFor(value.coerceIn(-amplitude, amplitude))
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = Color(0xFF3CA4FF), style = Stroke(width = 5f, cap = StrokeCap.Round))
            }
        }

        Text(stringResource(R.string.history_title), modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(stringResource(R.string.history_upper_bound, upperBound), modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.history_lower_bound, lowerBound), modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
