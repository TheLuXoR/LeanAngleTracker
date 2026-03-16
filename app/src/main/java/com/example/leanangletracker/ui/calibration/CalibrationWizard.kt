package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.leanangletracker.CalibrationStep
import com.example.leanangletracker.R
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.ui.theme.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun CalibrationWizard(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onStartLeftMeasurement: () -> Unit,
    onStartRightMeasurement: () -> Unit
) {
    var showInfo by rememberSaveable { mutableStateOf(false) }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = { TextButton(onClick = { showInfo = false }) { Text("OK") } },
            title = { Text(stringResource(R.string.calibration_title)) },
            text = { Text(stringResource(R.string.calibration_info_text)) }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.calibration_title),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = { showInfo = true }) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Info,
                            contentDescription = "Info",
                            tint = TextSecondary
                        )
                    }
                }

                BikeTiltAnimation(
                    step = state.calibrationStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                )

                Text(
                    text = stringResource(state.instructionsResId),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )

                CalibrationAmplitudeBars(
                    leftAmp = state.leftCalibrationAmplitudeDeg,
                    rightAmp = state.rightCalibrationAmplitudeDeg,
                    currentAmp = state.currentStepAmplitudeDeg
                )

                if (state.qualityHintResId != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(state.qualityHintResId),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                when (state.calibrationStep) {
                    CalibrationStep.UPRIGHT -> CalibrationButton(stringResource(R.string.action_capture_upright), onCaptureUpright)
                    CalibrationStep.LEFT_READY -> CalibrationButton(stringResource(R.string.action_start_left_measurement), onStartLeftMeasurement)
                    CalibrationStep.RIGHT_READY -> CalibrationButton(stringResource(R.string.action_start_right_measurement), onStartRightMeasurement)
                    CalibrationStep.LEFT_MEASURING,
                    CalibrationStep.RIGHT_MEASURING -> {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.measurement_running), style = MaterialTheme.typography.labelLarge)
                    }
                    CalibrationStep.READY -> Unit
                }
                
                Text(
                    text = stringResource(R.string.warning_only_zero_position),
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorRed.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CalibrationButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
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
        CalibrationStep.LEFT_MEASURING -> -25f
        CalibrationStep.RIGHT_READY,
        CalibrationStep.RIGHT_MEASURING -> 25f
        else -> 0f
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val baseY = h * 0.8f
        
        // Ground
        drawLine(
            color = Color.White.copy(alpha = 0.1f),
            start = Offset(w * 0.1f, baseY),
            end = Offset(w * 0.9f, baseY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )

        val dynamicTilt = tilt * (0.6f + 0.4f * abs(phase))
        val radians = Math.toRadians(dynamicTilt.toDouble())
        val cx = w * 0.5f
        val cy = h * 0.55f
        val len = 100f
        val dx = (len * sin(radians)).toFloat()
        val dy = (len * cos(radians)).toFloat()

        // Bike representation
        drawLine(
            color = if (tilt != 0f) primaryColor else Color.White.copy(alpha = 0.6f),
            start = Offset(cx - dx * 0.5f, cy + dy * 0.5f),
            end = Offset(cx + dx, cy - dy),
            strokeWidth = 8.dp.toPx(),
            cap = StrokeCap.Round
        )
        
        drawCircle(
            color = secondaryColor.copy(alpha = 0.8f),
            radius = 12.dp.toPx(),
            center = Offset(cx - dx * 0.7f, cy + dy * 0.4f),
            style = Stroke(4.dp.toPx())
        )
    }
}

@Composable
private fun CalibrationAmplitudeBars(leftAmp: Float, rightAmp: Float, currentAmp: Float) {
    val maxAmp = maxOf(30f, leftAmp, rightAmp, currentAmp)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        AmpRow(label = stringResource(R.string.label_left_short), value = leftAmp, fraction = leftAmp / maxAmp, color = SecondaryBlue)
        AmpRow(label = stringResource(R.string.label_right_short), value = rightAmp, fraction = rightAmp / maxAmp, color = SecondaryBlue)
        AmpRow(label = stringResource(R.string.label_now_short), value = currentAmp, fraction = currentAmp / maxAmp, color = PrimaryOrange)
    }
}

@Composable
private fun AmpRow(label: String, value: Float, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(40.dp), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.background)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(color)
            )
        }
        Text(
            stringResource(R.string.value_degrees_one_decimal, value),
            modifier = Modifier.width(50.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End
        )
    }
}
