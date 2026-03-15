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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
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
                    var showSettings by rememberSaveable { mutableStateOf(false) }

                    if (state.isCalibrated && showSettings) {
                        SettingsScreen(
                            state = state,
                            onBack = { showSettings = false },
                            onToggleInvertLean = viewModel::setInvertLeanAngle,
                            onSetHistoryWindow = viewModel::setHistoryWindowSeconds,
                            onResetExtrema = viewModel::resetExtrema,
                            onStartCalibration = {
                                showSettings = false
                                viewModel.startCalibration()
                            }
                        )
                    } else {
                        LeanAngleScreen(
                            state = state,
                            onOpenSettings = { showSettings = true },
                            onCaptureUpright = viewModel::captureUpright,
                            onCaptureLeft = viewModel::captureLeftLean,
                            onCaptureRight = viewModel::captureRightLeanAndFinish
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LeanAngleScreen(
    state: UiState,
    onOpenSettings: () -> Unit,
    onCaptureUpright: () -> Unit,
    onCaptureLeft: () -> Unit,
    onCaptureRight: () -> Unit
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
        if (!state.isCalibrated) {
            CalibrationWizard(
                state = state,
                onCaptureUpright = onCaptureUpright,
                onCaptureLeft = onCaptureLeft,
                onCaptureRight = onCaptureRight
            )
            return@Box
        }

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GaugeRoadView(state = state, modifier = Modifier.weight(1f).fillMaxSize())
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .align(Alignment.Top)
                        .height(56.dp)
                ) {
                    Text("Settings", fontSize = 18.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Lean Angle",
                        color = Color.White,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Button(onClick = onOpenSettings, modifier = Modifier.height(50.dp)) {
                        Text("Settings", fontSize = 16.sp)
                    }
                }

                GaugeRoadView(state = state, modifier = Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun CalibrationWizard(
    state: UiState,
    onCaptureUpright: () -> Unit,
    onCaptureLeft: () -> Unit,
    onCaptureRight: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Kalibrierung erforderlich",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = state.instructions,
                    fontSize = 20.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (state.qualityHint.isNotBlank()) {
                    Text(
                        text = state.qualityHint,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 16.sp
                    )
                }

                when (state.calibrationStep) {
                    CalibrationStep.UPRIGHT -> Button(
                        onClick = onCaptureUpright,
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text("Aufrecht erfassen", fontSize = 18.sp)
                    }

                    CalibrationStep.LEFT -> Button(
                        onClick = onCaptureLeft,
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text("Links erfassen", fontSize = 18.sp)
                    }

                    CalibrationStep.RIGHT -> Button(
                        onClick = onCaptureRight,
                        modifier = Modifier.fillMaxWidth().height(58.dp)
                    ) {
                        Text("Rechts erfassen", fontSize = 18.sp)
                    }

                    CalibrationStep.READY -> Unit
                }
            }
        }
    }
}

@Composable
private fun GaugeRoadView(state: UiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            TachoGauge(
                currentDeg = state.leanAngleDeg,
                maxLeftDeg = state.maxLeftDeg,
                maxRightDeg = state.maxRightDeg,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 80.dp)
            )

            LeanHistoryGraph(
                values = state.leanHistoryDeg,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(170.dp)
            )
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
        Canvas(modifier = Modifier.size(330.dp)) {
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
                strokeWidth = 12f,
                cap = StrokeCap.Round
            )

            // Bike symbol at center, needle as bike orientation.
            drawCircle(color = Color(0xFF99FFFFFF), radius = 18f, center = center)
            drawCircle(color = Color(0xFFFF4D6D), radius = 8f, center = center)
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
            Text("Max L ${"%.1f".format(abs(maxLeftDeg))}°", color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Max R ${"%.1f".format(maxRightDeg)}°", color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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

    Box(modifier = modifier.background(Color(0xFF2A3747), RoundedCornerShape(12.dp))) {
        Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            val width = size.width
            val height = size.height
            val centerY = height / 2f

            fun yFor(deg: Float): Float = centerY - (deg / amplitude) * (height * 0.44f)

            val roadY = yFor(upperBound)
            drawLine(
                color = Color(0xCCFFFFFF),
                start = Offset(0f, roadY),
                end = Offset(width, roadY),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            // Center / helper lines
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(width, centerY),
                strokeWidth = 2f
            )
            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(0f, yFor(lowerBound)),
                end = Offset(width, yFor(lowerBound)),
                strokeWidth = 2f
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
                    color = Color(0xFF3CA4FF),
                    style = Stroke(width = 5f, cap = StrokeCap.Round)
                )
            }
        }

        Text(
            text = "History / Straße",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SettingsScreen(
    state: UiState,
    onBack: () -> Unit,
    onToggleInvertLean: (Boolean) -> Unit,
    onSetHistoryWindow: (Int) -> Unit,
    onResetExtrema: () -> Unit,
    onStartCalibration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onBack) { Text("Zurück") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lean invertieren", fontSize = 18.sp)
                    Switch(checked = state.invertLeanAngle, onCheckedChange = onToggleInvertLean)
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("History", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(onClick = { onSetHistoryWindow(state.historyWindowSeconds - 5) }) { Text("-") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("${state.historyWindowSeconds}s", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onSetHistoryWindow(state.historyWindowSeconds + 5) }) { Text("+") }
                }
            }
        }

        Button(onClick = onResetExtrema, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Max-Werte zurücksetzen", fontSize = 18.sp)
        }

        Button(onClick = onStartCalibration, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Kalibrierung neu starten", fontSize = 18.sp)
        }
    }
}
