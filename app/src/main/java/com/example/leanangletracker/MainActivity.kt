package com.example.leanangletracker

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private enum class IntroStage { LOADING, ATTACH_PROMPT, TRANSITION_OUT, DONE }

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
                    var introStage by rememberSaveable { mutableStateOf(IntroStage.LOADING) }

                    LaunchedEffect(Unit) {
                        delay(900)
                        introStage = IntroStage.ATTACH_PROMPT
                    }

                    AnimatedContent(targetState = introStage, label = "intro_or_app") { stage ->
                        when (stage) {
                            IntroStage.LOADING,
                            IntroStage.ATTACH_PROMPT,
                            IntroStage.TRANSITION_OUT -> IntroScreen(
                                stage = stage,
                                onMountedConfirm = { introStage = IntroStage.TRANSITION_OUT },
                                onTransitionFinished = { introStage = IntroStage.DONE }
                            )

                            IntroStage.DONE -> {
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
                                        onStartLeftMeasurement = viewModel::startLeftMeasurement,
                                        onStartRightMeasurement = viewModel::startRightMeasurement
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroScreen(
    stage: IntroStage,
    onMountedConfirm: () -> Unit,
    onTransitionFinished: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "intro")
    val pulse by infinite.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )

    if (stage == IntroStage.TRANSITION_OUT) {
        LaunchedEffect(Unit) {
            delay(550)
            onTransitionFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF051524), Color(0xFF0A2A45))))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth().alpha(if (stage == IntroStage.TRANSITION_OUT) 0.35f else 1f)) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                PhoneBikeAnimation(modifier = Modifier.size(220.dp), pulse = pulse)

                when (stage) {
                    IntroStage.LOADING -> Text("Initialisiere…", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    else -> Text("Telefon am Motorrad befestigen", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }

                AnimatedVisibility(visible = stage == IntroStage.ATTACH_PROMPT) {
                    Button(onClick = onMountedConfirm, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                        Text("Befestigt", fontSize = 18.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhoneBikeAnimation(modifier: Modifier, pulse: Float) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val roadY = h * 0.78f

        drawLine(Color(0xCCFFFFFF), Offset(0f, roadY), Offset(w, roadY), strokeWidth = 8f)
        drawCircle(Color(0xFF2E3E52), radius = 24f, center = Offset(w * 0.27f, roadY - 18f), style = Stroke(8f))
        drawCircle(Color(0xFF2E3E52), radius = 24f, center = Offset(w * 0.73f, roadY - 18f), style = Stroke(8f))
        drawLine(Color(0xFF2E3E52), Offset(w * 0.27f, roadY - 18f), Offset(w * 0.54f, roadY - 70f), strokeWidth = 8f)
        drawLine(Color(0xFF2E3E52), Offset(w * 0.54f, roadY - 70f), Offset(w * 0.73f, roadY - 18f), strokeWidth = 8f)

        drawRoundRect(
            color = Color(0xFF9CD0FF).copy(alpha = pulse),
            topLeft = Offset(w * 0.42f, h * 0.24f),
            size = androidx.compose.ui.geometry.Size(w * 0.16f, h * 0.30f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(18f, 18f)
        )
    }
}

@Composable
private fun LeanAngleScreen(
    state: UiState,
    onOpenSettings: () -> Unit,
    onCaptureUpright: () -> Unit,
    onStartLeftMeasurement: () -> Unit,
    onStartRightMeasurement: () -> Unit
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
                onStartLeftMeasurement = onStartLeftMeasurement,
                onStartRightMeasurement = onStartRightMeasurement
            )
            return@Box
        }

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GaugeRoadView(state = state, modifier = Modifier.weight(1f).fillMaxSize())
                Button(
                    onClick = onOpenSettings,
                    modifier = Modifier.align(Alignment.Top).height(56.dp)
                ) { Text("Settings", fontSize = 18.sp) }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Lean Angle", color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
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
    onStartLeftMeasurement: () -> Unit,
    onStartRightMeasurement: () -> Unit
) {
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Kalibrierung") },
            text = {
                Text(
                    "Nur in Null-Lage tippen.\n" +
                        "Nach Start einer Messung das Motorrad ruhig in die Richtung neigen und wieder aufrichten."
                )
            }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Kalibrierung", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    InfoChip { showInfo = true }
                }

                BikeTiltAnimation(step = state.calibrationStep, modifier = Modifier.fillMaxWidth().height(130.dp))

                Text(text = state.instructions, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

                CalibrationAmplitudeBars(
                    leftAmp = state.leftCalibrationAmplitudeDeg,
                    rightAmp = state.rightCalibrationAmplitudeDeg,
                    currentAmp = state.currentStepAmplitudeDeg
                )

                Text(text = "⚠️ Nur in Null-Lage bedienen.", color = Color(0xFFC05757), fontSize = 15.sp)
                if (state.qualityHint.isNotBlank()) {
                    Text(text = state.qualityHint, color = MaterialTheme.colorScheme.primary, fontSize = 15.sp)
                }

                when (state.calibrationStep) {
                    CalibrationStep.UPRIGHT -> PrimaryActionButton("Aufrecht erfassen", onCaptureUpright)
                    CalibrationStep.LEFT_READY -> PrimaryActionButton("Links-Messung starten", onStartLeftMeasurement)
                    CalibrationStep.RIGHT_READY -> PrimaryActionButton("Rechts-Messung starten", onStartRightMeasurement)
                    CalibrationStep.LEFT_MEASURING,
                    CalibrationStep.RIGHT_MEASURING -> Text("Messung läuft…", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    CalibrationStep.READY -> Unit
                }
            }
        }
    }
}

@Composable
private fun InfoChip(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(1.5.dp, Color.Gray, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("i", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PrimaryActionButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(58.dp)) {
        Text(text, fontSize = 18.sp)
    }
}

@Composable
private fun BikeTiltAnimation(step: CalibrationStep, modifier: Modifier = Modifier) {
    val infinite = rememberInfiniteTransition(label = "bike-tilt")
    val phase by infinite.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Reverse),
        label = "phase"
    )

    val tilt = when (step) {
        CalibrationStep.LEFT_READY,
        CalibrationStep.LEFT_MEASURING -> -22f

        CalibrationStep.RIGHT_READY,
        CalibrationStep.RIGHT_MEASURING -> 22f
        else -> 0f
    }

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.78f
        drawLine(Color(0xFF8392A5), Offset(0f, baseY), Offset(w, baseY), strokeWidth = 6f)

        val dynamicTilt = tilt * (0.7f + 0.3f * abs(phase))
        val radians = Math.toRadians(dynamicTilt.toDouble())
        val cx = w * 0.5f
        val cy = h * 0.56f
        val len = 84f
        val dx = (len * sin(radians)).toFloat()
        val dy = (len * cos(radians)).toFloat()

        drawLine(Color(0xFF2F3E52), Offset(cx - dx, cy + dy * 0.35f), Offset(cx + dx, cy - dy), strokeWidth = 10f, cap = StrokeCap.Round)
        drawCircle(Color(0xFF2F3E52), radius = 16f, center = Offset(cx - dx * 0.85f, cy + dy * 0.30f), style = Stroke(5f))
        drawCircle(Color(0xFF2F3E52), radius = 16f, center = Offset(cx + dx * 0.85f, cy - dy * 0.85f), style = Stroke(5f))
    }
}

@Composable
private fun CalibrationAmplitudeBars(leftAmp: Float, rightAmp: Float, currentAmp: Float) {
    val maxAmp = maxOf(20f, leftAmp, rightAmp, currentAmp)

    fun barWidth(value: Float): Float = (value / maxAmp).coerceIn(0f, 1f)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        AmpRow(label = "L", value = leftAmp, widthFraction = barWidth(leftAmp), color = Color(0xFF4EA3FF))
        AmpRow(label = "R", value = rightAmp, widthFraction = barWidth(rightAmp), color = Color(0xFF4EA3FF))
        AmpRow(label = "Now", value = currentAmp, widthFraction = barWidth(currentAmp), color = Color(0xFFFF7B7B))
    }
}

@Composable
private fun AmpRow(label: String, value: Float, widthFraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(36.dp), fontWeight = FontWeight.Bold)
        Box(modifier = Modifier.weight(1f).height(16.dp).background(Color(0xFFE6ECF3), RoundedCornerShape(6.dp))) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(16.dp)
                    .background(color, RoundedCornerShape(6.dp))
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("${"%.1f".format(value)}°", fontSize = 13.sp)
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

            drawLine(Color(0x6693C5FD), center, angleToTip(maxLeftDeg, 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0x6693C5FD), center, angleToTip(maxRightDeg, 0.9f), 6f, StrokeCap.Round)
            drawLine(Color(0xFFFF4D6D), center, angleToTip(currentDeg, 0.86f), 12f, StrokeCap.Round)

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

        Text("History / Straße", modifier = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("Upper bound (Max R): ${"%.1f".format(upperBound)}°", modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Text("Lower bound (Max L): ${"%.1f".format(lowerBound)}°", modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
    var showInfo by remember { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { Button(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text("Info") },
            text = { Text("Weniger ist mehr: Diese Seite enthält nur die wichtigsten Optionen.") }
        )
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                InfoChip { showInfo = true }
            }
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
