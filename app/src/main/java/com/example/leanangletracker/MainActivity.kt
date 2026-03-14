package com.example.leanangletracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (state.isCalibrated) "Lean Angle" else "Calibration required",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${"%.1f".format(state.leanAngleDeg)}°",
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        state.leanAngleDeg > 0 -> "Right"
                        state.leanAngleDeg < 0 -> "Left"
                        else -> "Upright"
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(text = state.instructions)
        if (state.qualityHint.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = state.qualityHint, color = MaterialTheme.colorScheme.primary)
        }

        Spacer(modifier = Modifier.height(20.dp))

        when (state.calibrationStep) {
            CalibrationStep.IDLE,
            CalibrationStep.READY -> Button(onClick = onStartCalibration) {
                Text("Start Calibration")
            }

            CalibrationStep.UPRIGHT -> Button(onClick = onCaptureUpright) {
                Text("Capture Upright")
            }

            CalibrationStep.WIGGLE -> Button(onClick = onFinishCalibration) {
                Text("Finish Calibration")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onReset) {
            Text("Reset")
        }
    }
}
