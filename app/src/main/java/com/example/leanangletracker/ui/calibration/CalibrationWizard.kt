package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.animation.CalibrationBikeLeanAnimation
import com.example.leanangletracker.ui.theme.TextSecondary

@Composable
fun CalibrationWizardLandscape(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit // Ignored in manual mode
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
        ) {
            // Header
            Text(
                text = stringResource(R.string.calibration_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Animation Area
                val animTarget = when (state.calibrationStep) {
                    BikeLean.LEFT -> BikeLean.LEFT
                    BikeLean.RIGHT -> BikeLean.RIGHT
                    else -> BikeLean.UPRIGHT
                }

                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    CalibrationBikeLeanAnimation(
                        modifier = Modifier.fillMaxHeight(0.7f).aspectRatio(1f),
                        bikeAnimationFrom = animTarget,
                        bikeAnimationTo = BikeLean.UPRIGHT
                    )
                }

                // Instructions & Controls
                Column(
                    modifier = Modifier.weight(1.5f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CalibrationStatusCard(state)

                    // Progress Area
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CalibrationProgressIndicator(state)
                    }

                    // Action Button
                    if (state.calibrationStep != BikeLean.DONE) {
                        Button(
                            onClick = onCaptureUpright,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (state.currentProgress > 0.5f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (state.currentProgress > 0.5f) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            val buttonText = when (state.calibrationStep) {
                                BikeLean.UPRIGHT -> stringResource(R.string.calibration_action_fix_center)
                                BikeLean.LEFT -> stringResource(R.string.calibration_action_left_saved)
                                BikeLean.RIGHT -> stringResource(R.string.calibration_action_right_saved)
                                else -> ""
                            }
                            Text(text = buttonText, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CalibrationWizardPortrait(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit // Ignored in manual mode
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)
        ) {
            Text(
                text = stringResource(R.string.calibration_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.2f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                val animTarget = when (state.calibrationStep) {
                    BikeLean.LEFT -> BikeLean.LEFT
                    BikeLean.RIGHT -> BikeLean.RIGHT
                    else -> BikeLean.UPRIGHT
                }

                CalibrationBikeLeanAnimation(
                    modifier = Modifier.fillMaxSize(0.7f),
                    bikeAnimationFrom = animTarget,
                    bikeAnimationTo = BikeLean.UPRIGHT
                )

                // Overlay actual angle
                if (state.calibrationStep != BikeLean.DONE && state.calibrationStep != BikeLean.UPRIGHT) {
                    Text(
                        text = "${state.currentAngleDeg.toInt()}°",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                    )
                }

                Box(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalibrationProgressIndicator(state)
                }
            }

            CalibrationStatusCard(state)

            if (state.calibrationStep != BikeLean.DONE) {
                Button(
                    onClick = onCaptureUpright,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    shape = RoundedCornerShape(20.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    val buttonText = when (state.calibrationStep) {
                        BikeLean.UPRIGHT -> stringResource(R.string.calibration_action_fix_center)
                        BikeLean.LEFT -> stringResource(R.string.calibration_action_left_confirmed)
                        BikeLean.RIGHT -> stringResource(R.string.calibration_action_right_confirmed)
                        else -> ""
                    }
                    Text(text = buttonText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun CalibrationStatusCard(state: CalibrationUiState) {
    val backgroundColor by animateColorAsState(
        targetValue = if (state.isWrongDirection) Color(0xFF8B0000).copy(alpha = 0.2f) 
                     else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(backgroundColor)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (state.isWrongDirection) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red)
                Text(
                    text = stringResource(R.string.calibration_error_wrong_direction),
                    color = Color.Red,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        val instructionText = when (state.calibrationStep) {
            BikeLean.UPRIGHT -> stringResource(R.string.calibration_instr_upright)
            BikeLean.LEFT -> stringResource(R.string.calibration_instr_tilt_left)
            BikeLean.RIGHT -> stringResource(R.string.calibration_instr_tilt_right)
            else -> stringResource(R.string.calibration_instr_ready)
        }

        Text(
            text = instructionText,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = when(state.calibrationStep) {
                BikeLean.UPRIGHT -> stringResource(R.string.calibration_hint_firm_mount)
                BikeLean.LEFT, BikeLean.RIGHT -> stringResource(R.string.calibration_hint_min_angle)
                else -> stringResource(R.string.calibration_hint_ready)
            },
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun CalibrationProgressIndicator(state: CalibrationUiState) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.1f)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Half
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.CenterEnd
            ) {
                // Peak Background
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.leftMax)
                        .fillMaxHeight()
                        .background(Color.Green.copy(alpha = 0.2f))
                )
                // Current Progress
                if (state.calibrationStep == BikeLean.LEFT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.currentProgress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }

            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.5f)))

            // Right Half
            Box(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                // Peak Background
                Box(
                    modifier = Modifier
                        .fillMaxWidth(state.rightMax)
                        .fillMaxHeight()
                        .background(Color.Green.copy(alpha = 0.2f))
                )
                // Current Progress
                if (state.calibrationStep == BikeLean.RIGHT) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(state.currentProgress)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
        
        // Progress label
        if (state.calibrationStep == BikeLean.LEFT || state.calibrationStep == BikeLean.RIGHT) {
            Text(
                text = stringResource(R.string.calibration_progress_label, (state.currentProgress * 100).toInt()),
                style = MaterialTheme.typography.labelSmall,
                color = if (state.currentProgress > 0.6f) Color.Green else TextSecondary,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
