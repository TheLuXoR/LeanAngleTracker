package com.example.leanangletracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    LeanAngleScreen(
                        state = state,
                        onStartCalibration = viewModel::startCalibration,
                        onCaptureUpright = viewModel::captureUpright,
                        onFinishCalibration = viewModel::finishCalibration,
                        onReset = viewModel::resetCalibration
                    )
                }
            }
        }
    }
}

@Composable
private fun LeanAngleScreen(
    state: UiState,
    onStartCalibration: () -> Unit,
    onCaptureUpright: () -> Unit,
    onFinishCalibration: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF071A2E), Color(0xFF0D2D4F), Color(0xFF071A2E))
                )
            )
            .padding(horizontal = 16.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Lean Angle Tracker",
            style = MaterialTheme.typography.headlineSmall,
            color = Color.White,
            fontWeight = FontWeight.SemiBold
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isCalibrated) "Live Lean" else "Calibration required",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                TachoGauge(
                    currentDeg = state.leanAngleDeg,
                    maxLeftDeg = state.maxLeftDeg,
                    maxRightDeg = state.maxRightDeg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.instructions)
                if (state.qualityHint.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = state.qualityHint, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            LeanHistoryGraph(
                values = state.leanHistoryDeg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .padding(10.dp)
            )
        }

        when (state.calibrationStep) {
            CalibrationStep.IDLE,
            CalibrationStep.READY -> Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth()) {
                Text("Start Calibration")
            }

            CalibrationStep.UPRIGHT -> Button(onClick = onCaptureUpright, modifier = Modifier.fillMaxWidth()) {
                Text("Capture Upright")
            }

            CalibrationStep.WIGGLE -> Button(onClick = onFinishCalibration, modifier = Modifier.fillMaxWidth()) {
                Text("Finish Calibration")
            }
        }

        Button(onClick = onReset, modifier = Modifier.fillMaxWidth()) {
            Text("Reset")
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(260.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f
            val maxDisplay = 65f

            drawCircle(
                color = Color(0xFFEEF6FF),
                radius = radius * 1.04f,
                center = center
            )
            drawCircle(
                color = Color(0xFF17263A),
                radius = radius,
                center = center
            )

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

            val leftPeakTip = angleToTip(maxLeftDeg, 0.9f)
            val rightPeakTip = angleToTip(maxRightDeg, 0.9f)
            drawLine(
                color = Color(0x6693C5FD),
                start = center,
                end = leftPeakTip,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0x6693C5FD),
                start = center,
                end = rightPeakTip,
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            val currentTip = angleToTip(currentDeg, 0.86f)
            drawLine(
                color = Color(0xFFFF4D6D),
                start = center,
                end = currentTip,
                strokeWidth = 10f,
                cap = StrokeCap.Round
            )

            drawCircle(color = Color(0xFFFF4D6D), radius = 10f, center = center)
            drawCircle(color = Color.White, radius = 4f, center = center)
        }

        val direction = when {
            currentDeg > 1f -> "R"
            currentDeg < -1f -> "L"
            else -> "UP"
        }

        Text(
            text = "${"%.1f".format(currentDeg)}° $direction",
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun LeanHistoryGraph(values: List<Float>, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(Color(0xFFF3F8FF), RoundedCornerShape(10.dp))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f
            val maxAbs = 65f

            drawLine(
                color = Color(0xFFBCD4F6),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )

            if (values.size < 2) return@Canvas

            val stepX = width / (values.size - 1)
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = index * stepX
                val y = centerY - (value.coerceIn(-maxAbs, maxAbs) / maxAbs) * (height * 0.45f)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }

            drawPath(
                path = path,
                color = Color(0xFF1F78FF),
                style = Stroke(width = 5f, cap = StrokeCap.Round)
            )
        }

        Text(
            text = "Lean history",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 6.dp),
            color = Color(0xFF35567F),
            fontSize = 12.sp
        )
    }
}
