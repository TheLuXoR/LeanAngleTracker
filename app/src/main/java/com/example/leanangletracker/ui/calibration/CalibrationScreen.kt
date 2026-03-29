package com.example.leanangletracker.ui.calibration

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.example.leanangletracker.CalibrationUiState

@Composable
internal fun CalibrationScreen(
    calibrationState: CalibrationUiState,
    onCaptureUpright: () -> Unit,
    onContinueFallback: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        if (isLandscape) {
            CalibrationWizardLandscape(
                state = calibrationState,
                onCaptureUpright = onCaptureUpright,
                onContinueFallback = onContinueFallback
            )
        } else {
            CalibrationWizardPortrait(
                state = calibrationState,
                onCaptureUpright = onCaptureUpright,
                onContinueFallback = onContinueFallback
            )
        }
    }
}
