package com.example.leanangletracker

import android.content.res.Configuration
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.abs
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
                        onReset = viewModel::resetCalibration,
                        onToggleInvertLean = viewModel::setInvertLeanAngle,
                        onSetHistoryWindow = viewModel::setHistoryWindowSeconds
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
    onReset: () -> Unit,
    onToggleInvertLean: (Boolean) -> Unit,
    onSetHistoryWindow: (Int) -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    listOf(Color(0xFF071A2E), Color(0xFF0D2D4F), Color(0xFF071A2E))
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(modifier = Modifier.weight(1.3f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Header()
                    CombinedGaugeAndHistory(
                        state = state,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CalibrationCard(state)
                    SettingsCard(
                        invertLean = state.invertLeanAngle,
                        historyWindowSeconds = state.historyWindowSeconds,
                        onToggleInvertLean = onToggleInvertLean,
                        onSetHistoryWindow = onSetHistoryWindow
                    )
                    Controls(
                        state = state,
                        onStartCalibration = onStartCalibration,
                        onCaptureUpright = onCaptureUpright,
                        onFinishCalibration = onFinishCalibration,
                        onReset = onReset
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Header()
                CombinedGaugeAndHistory(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                CalibrationCard(state)
                SettingsCard(
                    invertLean = state.invertLeanAngle,
                    historyWindowSeconds = state.historyWindowSeconds,
                    onToggleInvertLean = onToggleInvertLean,
                    onSetHistoryWindow = onSetHistoryWindow
                )
                Controls(
                    state = state,
                    onStartCalibration = onStartCalibration,
                    onCaptureUpright = onCaptureUpright,
                    onFinishCalibration = onFinishCalibration,
                    onReset = onReset
                )
            }
        }
    }
}

@Composable
private fun Header() {
    Text(
        text = "Lean Angle Tracker",
        style = MaterialTheme.typography.headlineMedium,
        color = Color.White,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CalibrationCard(state: UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = state.instructions, fontSize = 17.sp, lineHeight = 22.sp)
            if (state.qualityHint.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = state.qualityHint, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun SettingsCard(
    invertLean: Boolean,
    historyWindowSeconds: Int,
    onToggleInvertLean: (Boolean) -> Unit,
    onSetHistoryWindow: (Int) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Settings", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Invert lean direction (default ON)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF35567F)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF35567F))
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSetHistoryWindow(historyWindowSeconds - 5) }) { Text("-", fontSize = 20.sp) }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${historyWindowSeconds}s", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(onClick = { onSetHistoryWindow(historyWindowSeconds + 5) }) { Text("+", fontSize = 20.sp) }
                }
            }
            Switch(
                checked = invertLean,
                onCheckedChange = onToggleInvertLean
            )
        }
    }
}

@Composable
private fun Controls(
    state: UiState,
    onStartCalibration: () -> Unit,
    onCaptureUpright: () -> Unit,
    onFinishCalibration: () -> Unit,
    onReset: () -> Unit
) {
    when (state.calibrationStep) {
        CalibrationStep.IDLE,
        CalibrationStep.READY -> Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text("Start Calibration", fontSize = 18.sp)
        }

        CalibrationStep.UPRIGHT -> Button(onClick = onCaptureUpright, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text("Capture Upright", fontSize = 18.sp)
        }

        CalibrationStep.WIGGLE -> Button(onClick = onFinishCalibration, modifier = Modifier.fillMaxWidth().height(58.dp)) {
            Text("Finish Calibration", fontSize = 18.sp)
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
    Button(onClick = onReset, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Text("Reset", fontSize = 17.sp)
    }
}

@Composable
private fun CombinedGaugeAndHistory(
    state: UiState,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (state.isCalibrated) "Live Lean" else "Calibration required",
                style = MaterialTheme.typography.titleLarge
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                TachoGauge(
                    currentDeg = state.leanAngleDeg,
                    maxLeftDeg = state.maxLeftDeg,
                    maxRightDeg = state.maxRightDeg,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 46.dp)
                )

                LeanHistoryGraph(
                    values = state.leanHistoryDeg,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(132.dp)
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
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(250.dp)) {
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

            drawLine(
                color = Color(0x6693C5FD),
                start = center,
                end = angleToTip(maxLeftDeg, 0.9f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0x6693C5FD),
                start = center,
                end = angleToTip(maxRightDeg, 0.9f),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )

            drawLine(
                color = Color(0xFFFF4D6D),
                start = center,
                end = angleToTip(currentDeg, 0.86f),
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
            fontSize = 26.sp
        )

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Max L ${"%.1f".format(abs(maxLeftDeg))}°", color = Color(0xFF9CC6FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Max R ${"%.1f".format(maxRightDeg)}°", color = Color(0xFF9CC6FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun LeanHistoryGraph(
    values: List<Float>,
    modifier: Modifier = Modifier
) {
    val upperBound = values.maxOrNull()?.coerceAtLeast(0f) ?: 0f
    val lowerBound = values.minOrNull()?.coerceAtMost(0f) ?: 0f
    val amplitude = maxOf(10f, abs(upperBound), abs(lowerBound))

    Box(modifier = modifier.background(Color(0xFFF3F8FF), RoundedCornerShape(10.dp))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            fun yFor(deg: Float): Float = centerY - (deg / amplitude) * (height * 0.42f)

            val topRef = yFor(upperBound)
            val bottomRef = yFor(lowerBound)

            drawLine(
                color = Color(0xFF9DBBE8),
                start = Offset(0f, topRef),
                end = Offset(width, topRef),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0xFFBCD4F6),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0xFF9DBBE8),
                start = Offset(0f, bottomRef),
                end = Offset(width, bottomRef),
                strokeWidth = 1.5f
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
                    color = Color(0xFF1F78FF),
                    style = Stroke(width = 4.5f, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = "Lean history",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp),
            color = Color(0xFF35567F),
            fontSize = 14.sp
        )
        Text(
            text = "Upper bound (Max R): ${"%.1f".format(upperBound)}°",
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 6.dp),
            color = Color(0xFF35567F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Lower bound (Max L): ${"%.1f".format(lowerBound)}°",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp),
            color = Color(0xFF35567F),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
