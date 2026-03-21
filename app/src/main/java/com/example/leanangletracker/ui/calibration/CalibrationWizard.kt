package com.example.leanangletracker.ui.calibration

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.example.leanangletracker.ui.theme.*

@Composable
internal fun CalibrationWizard(
    state: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit
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
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically)
        ) {
            // Header
            Text(
                text = "Kalibrierung",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            // Animation Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(32.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                // Determine if we should show the "return to upright" animation
                val isReturningUpright = state.uprightRecognitionProgress > 0f || state.tiltRecognitionProgress >= 1f
                
                // Use key to restart the animation when phase changes
                key(isReturningUpright, state.calibrationStep) {
                    CalibrationBikeLeanAnimation(
                        modifier = Modifier.fillMaxSize(0.8f),
                        // Note: CalibrationBikeLeanAnimation animates from bikeAnimationTo -> bikeAnimationFrom 
                        // based on its internal 'start' state logic.
                        bikeAnimationFrom = if (isReturningUpright) BikeLean.UPRIGHT else state.calibrationStep,
                        bikeAnimationTo = if (isReturningUpright) (if (state.calibrationStep == BikeLean.LEFT) BikeLean.LEFT else BikeLean.RIGHT) else BikeLean.UPRIGHT
                    )
                }
                
                // Progress Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    CalibrationProgressIndicator(
                        leftProgress = state.leftProgress,
                        leftMax = state.leftMax,
                        rightProgress = state.rightProgress,
                        rightMax = state.rightMax
                    )
                }
            }

            // Instructions
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val instructionText = if (state.tiltRecognitionProgress >= 1f) {
                    "Sehr gut! Jetzt richte das Motorrad wieder auf."
                } else {
                    stringResource(state.instructionsResId)
                }

                Text(
                    text = instructionText,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                Text(
                    text = if (state.calibrationStep == BikeLean.UPRIGHT) 
                        "Halte das Smartphone stabil in der Halterung." 
                    else 
                        "Erst nach links, dann nach rechts neigen.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )
            }

            // Action Button
            Box(modifier = Modifier.height(80.dp), contentAlignment = Alignment.Center) {
                if (state.calibrationStep == BikeLean.UPRIGHT && !state.isCalibrated) {
                    Button(
                        onClick = onCaptureUpright,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(16.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.action_confirm_bike_upright),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (state.calibrationStep != BikeLean.DONE) {
                    TextButton(onClick = onContinueFallback) {
                        Text(
                            text = "Schritt überspringen",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalibrationProgressIndicator(
    leftProgress: Float,
    leftMax: Float,
    rightProgress: Float,
    rightMax: Float
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.1f)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(leftMax)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(leftProgress)
                    .fillMaxHeight()
                    .background(if (leftMax >= 1f) AccentGreen else MaterialTheme.colorScheme.primary)
            )
        }

        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(Color.White.copy(alpha = 0.3f))
        )

        // Right Half
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(rightMax)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth(rightProgress)
                    .fillMaxHeight()
                    .background(if (rightMax >= 1f) AccentGreen else MaterialTheme.colorScheme.primary)
            )
        }
    }
}
