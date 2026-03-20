package com.example.leanangletracker.ui.calibration

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.R
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.animation.BikeLeanAnimation
import com.example.leanangletracker.ui.animation.PhoneMountAnimation
import com.example.leanangletracker.ui.theme.*

@Composable
internal fun CalibrationWizard(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit
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
                            imageVector = Icons.Default.Info,
                            contentDescription = "Info",
                            tint = TextSecondary
                        )
                    }
                }

                BikeLeanAnimation(
                    step = state.calibrationStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
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
                    BikeLean.UPRIGHT -> CalibrationButton(stringResource(R.string.action_confirm_bike_upright), onCaptureUpright)
                    BikeLean.LEFT_READY,
                    BikeLean.RIGHT_READY,
                    BikeLean.LEFT_MEASURING,
                    BikeLean.RIGHT_MEASURING -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            
                            RecognitionProgress(
                                tiltProgress = state.tiltRecognitionProgress,
                                uprightProgress = state.uprightRecognitionProgress
                            )
                            
                            OutlinedButton(onClick = onContinueFallback, modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = stringResource(
                                        if (state.calibrationStep == BikeLean.LEFT_MEASURING) {
                                            R.string.action_continue_to_right
                                        } else {
                                            R.string.action_finish_calibration
                                        }
                                    )
                                )
                            }
                        }
                    }
                    BikeLean.READY -> Unit
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
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}



@Composable
private fun RecognitionProgress(tiltProgress: Float, uprightProgress: Float) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ProgressItem(label = stringResource(R.string.label_tilt_recognition_progress), progress = tiltProgress, color = MaterialTheme.colorScheme.primary)
        ProgressItem(label = stringResource(R.string.label_return_upright_progress), progress = uprightProgress, color = AccentGreen)
    }
}

@Composable
private fun ProgressItem(label: String, progress: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = MaterialTheme.colorScheme.background
        )
    }
}

@Composable
private fun CalibrationAmplitudeBars(leftAmp: Float, rightAmp: Float, currentAmp: Float) {
    val maxAmp = maxOf(40f, leftAmp, rightAmp, currentAmp)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        AmpRow(label = "LEFT", value = leftAmp, fraction = leftAmp / maxAmp, color = SecondaryBlue)
        AmpRow(label = "RIGHT", value = rightAmp, fraction = rightAmp / maxAmp, color = SecondaryBlue)
        AmpRow(label = "LIVE", value = currentAmp, fraction = currentAmp / maxAmp, color = PrimaryOrange)
    }
}

@Composable
private fun AmpRow(label: String, value: Float, fraction: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.width(48.dp), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Black, color = TextSecondary)
        Box(
            modifier = Modifier
                .weight(1f)
                .height(14.dp)
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
            text = "${value.toInt()}°",
            modifier = Modifier.width(44.dp).padding(start = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.End,
            fontWeight = FontWeight.Bold
        )
    }
}
