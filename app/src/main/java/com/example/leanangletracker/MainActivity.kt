package com.example.leanangletracker

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                    var introFinished by rememberSaveable { mutableStateOf(false) }

                    if (!introFinished) {
                        IntroAnimationScreen(onFinished = {
                            introFinished = true
                            viewModel.startCalibration()
                        })
                        return@Surface
                    }

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
                            onStartLeft = viewModel::startLeftMeasurement,
                            onStartRight = viewModel::startRightMeasurement
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroAnimationScreen(onFinished: () -> Unit) {
    var phase by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        delay(2300)
        phase = 1
        delay(900)
        onFinished()
    }

    val infinite = rememberInfiniteTransition(label = "intro")
    val floatY by infinite.animateFloat(
        initialValue = -6f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
        label = "phoneFloat"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF061828), Color(0xFF0D2D4F))))
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(visible = phase == 0) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Lean Angle Tracker", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                Box(modifier = Modifier.size(180.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        // bike/cockpit arc
                        drawArc(
                            color = Color(0x7756A5FF),
                            startAngle = 200f,
                            sweepAngle = 140f,
                            useCenter = false,
                            style = Stroke(width = 10f)
                        )
                        // phone mount placeholder
                        drawRoundRect(
                            color = Color(0xFF9CC6FF),
                            topLeft = Offset(size.width * 0.43f, size.height * 0.25f + floatY),
                            size = androidx.compose.ui.geometry.Size(size.width * 0.14f, size.height * 0.28f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                        )
                    }
                }
                Text("Telefon am Motorrad befestigen", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
        }

        AnimatedVisibility(visible = phase == 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Initialisiere Sensoren…", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Starte Kalibrierung", color = Color(0xFF9CC6FF), fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun LeanAngleScreen(
    state: UiState,
    onOpenSettings: () -> Unit,
    onCaptureUpright: () -> Unit,
    onStartLeft: () -> Unit,
    onStartRight: () -> Unit
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var showInfo by rememberSaveable { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Sicherheit") },
            text = {
                Text("Bediene das Telefon nur in aufrechter Nulllage. Für Links/Rechts-Messung das Motorrad kontrolliert neigen und wieder aufrichten.")
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF071A2E), Color(0xFF0D2D4F), Color(0xFF071A2E))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(14.dp)
    ) {
        if (!state.isCalibrated) {
            CalibrationWizard(
                state = state,
                onCaptureUpright = onCaptureUpright,
                onStartLeft = onStartLeft,
                onStartRight = onStartRight,
                onInfo = { showInfo = true }
            )
            return@Box
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GaugeRoadView(state = state, modifier = Modifier.weight(1f).fillMaxSize())
                Button(onClick = onOpenSettings, modifier = Modifier.align(Alignment.Top).height(56.dp)) {
                    Text("Settings", fontSize = 18.sp)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Lean", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Button(onClick = onOpenSettings, modifier = Modifier.height(50.dp)) { Text("Settings", fontSize = 16.sp) }
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
    onStartLeft: () -> Unit,
    onStartRight: () -> Unit,
    onInfo: () -> Unit
) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val bikeTilt by pulse.animateFloat(
        initialValue = -18f,
        targetValue = 18f,
        animationSpec = infiniteRepeatable(animation = tween(1200), repeatMode = RepeatMode.Reverse),
        label = "bikeTilt"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kalibrierung", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .background(Color.White, CircleShape)
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center
            ) {
                Text("i", color = Color(0xFF0D2D4F), fontWeight = FontWeight.Bold)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.instructions, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                if (state.qualityHint.isNotBlank()) {
                    Text(state.qualityHint, color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                }
                Text(if (state.uprightNow) "Nulllage erkannt" else "Bitte aufrichten", fontSize = 16.sp)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CalibrationAmplitudeBar("Links", state.leftCalibrationAmplitudeDeg)
                CalibrationAmplitudeBar("Aktuell", state.currentCalibrationAmplitudeDeg)
                CalibrationAmplitudeBar("Rechts", state.rightCalibrationAmplitudeDeg)
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(120.dp)) {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = Color(0xFFDFECFF), radius = size.minDimension * 0.45f, center = c)
                    val angle = when (state.calibrationStep) {
                        CalibrationStep.LEFT_MEASURING -> -bikeTilt
                        CalibrationStep.RIGHT_MEASURING -> bikeTilt
                        else -> 0f
                    }
                    val theta = Math.toRadians((angle - 90f).toDouble())
                    val tip = Offset(
                        x = c.x + (size.minDimension * 0.35f * cos(theta)).toFloat(),
                        y = c.y + (size.minDimension * 0.35f * sin(theta)).toFloat()
                    )
                    drawLine(Color(0xFF2F7FFF), c, tip, strokeWidth = 10f, cap = StrokeCap.Round)
                    drawCircle(Color(0xFF2F7FFF), radius = 10f, center = c)
                }
            }
        }

        when (state.calibrationStep) {
            CalibrationStep.UPRIGHT_CAPTURE -> Button(onClick = onCaptureUpright, enabled = state.uprightNow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Aufrecht bestätigen", fontSize = 18.sp)
            }

            CalibrationStep.LEFT_READY -> Button(onClick = onStartLeft, enabled = state.uprightNow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Links-Messung starten", fontSize = 18.sp)
            }

            CalibrationStep.RIGHT_READY -> Button(onClick = onStartRight, enabled = state.uprightNow, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Rechts-Messung starten", fontSize = 18.sp)
            }

            else -> Text("Messung läuft…", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CalibrationAmplitudeBar(label: String, amplitudeDeg: Float) {
    val clamped = (amplitudeDeg / 45f).coerceIn(0f, 1f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("$label  ${"%.1f".format(amplitudeDeg)}°", fontWeight = FontWeight.SemiBold)
        Box(modifier = Modifier.fillMaxWidth().height(14.dp).background(Color(0xFFE8EEF7), RoundedCornerShape(8.dp))) {
            Box(modifier = Modifier.fillMaxWidth(clamped).height(14.dp).background(Color(0xFF2F7FFF), RoundedCornerShape(8.dp)))
        }
    }
}

@Composable
private fun GaugeRoadView(state: UiState, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Box(modifier = Modifier.fillMaxSize().padding(10.dp)) {
            TachoGauge(
                currentDeg = state.leanAngleDeg,
                maxLeftDeg = state.maxLeftDeg,
                maxRightDeg = state.maxRightDeg,
                modifier = Modifier.fillMaxSize().padding(bottom = 82.dp)
            )

            LeanHistoryGraph(
                values = state.leanHistoryDeg,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(172.dp)
            )
        }
    }
}

@Composable
private fun TachoGauge(currentDeg: Float, maxLeftDeg: Float, maxRightDeg: Float, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(330.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.43f
            val maxDisplay = 65f

            drawCircle(color = Color(0xFFEEF6FF), radius = radius * 1.04f, center = center)
            drawCircle(color = Color(0xFF17263A), radius = radius, center = center)

            for (mark in -60..60 step 10) {
                val theta = Math.toRadians(mark.toDouble() - 90.0)
                val outer = Offset(center.x + (radius * 0.93f * cos(theta)).toFloat(), center.y + (radius * 0.93f * sin(theta)).toFloat())
                val innerFactor = if (mark % 30 == 0) 0.72f else 0.79f
                val inner = Offset(center.x + (radius * innerFactor * cos(theta)).toFloat(), center.y + (radius * innerFactor * sin(theta)).toFloat())
                drawLine(if (mark == 0) Color(0xFF40E0A0) else Color(0xFFD8E8FF), inner, outer, strokeWidth = if (mark % 30 == 0) 6f else 3f, cap = StrokeCap.Round)
            }

            fun angleToTip(deg: Float, lengthFactor: Float): Offset {
                val clamped = deg.coerceIn(-maxDisplay, maxDisplay)
                val theta = Math.toRadians(clamped.toDouble() - 90.0)
                return Offset(center.x + (radius * lengthFactor * cos(theta)).toFloat(), center.y + (radius * lengthFactor * sin(theta)).toFloat())
            }

            drawLine(Color(0x6693C5FD), center, angleToTip(maxLeftDeg, 0.9f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(Color(0x6693C5FD), center, angleToTip(maxRightDeg, 0.9f), strokeWidth = 6f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFF4D6D), center, angleToTip(currentDeg, 0.86f), strokeWidth = 12f, cap = StrokeCap.Round)

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
            modifier = Modifier.align(Alignment.BottomCenter).background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp
        )

        Row(
            modifier = Modifier.align(Alignment.TopCenter).background(Color(0xCC0A1A2B), RoundedCornerShape(8.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Max L ${"%.1f".format(abs(maxLeftDeg))}°", color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.width(12.dp))
            Text("Max R ${"%.1f".format(maxRightDeg)}°", color = Color(0xFF9CC6FF), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
            drawLine(Color(0xCCFFFFFF), Offset(0f, roadY), Offset(width, roadY), strokeWidth = 4f, cap = StrokeCap.Round)
            drawLine(Color(0x66FFFFFF), Offset(0f, centerY), Offset(width, centerY), strokeWidth = 2f)
            drawLine(Color(0x66FFFFFF), Offset(0f, yFor(lowerBound)), Offset(width, yFor(lowerBound)), strokeWidth = 2f)

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

        Text("History / Straße", modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "Upper bound (Max R): ${"%.1f".format(upperBound)}°",
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 6.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Lower bound (Max L): ${"%.1f".format(lowerBound)}°",
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 6.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
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
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Button(onClick = onBack) { Text("Zurück") }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
