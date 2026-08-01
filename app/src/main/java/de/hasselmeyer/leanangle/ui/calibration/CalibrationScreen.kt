package de.hasselmeyer.leanangle.ui.calibration

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import de.hasselmeyer.leanangle.CalibrationUiState

@Composable
internal fun CalibrationScreen(
    calibrationState: CalibrationUiState,
    offerAppTour: Boolean,
    onStartUprightMeasurement: () -> Unit,
    onFinishCalibration: (startAppTour: Boolean) -> Unit,
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
                offerAppTour = offerAppTour,
                onStartUprightMeasurement = onStartUprightMeasurement,
                onFinishCalibration = onFinishCalibration
            )
        } else {
            CalibrationWizardPortrait(
                state = calibrationState,
                offerAppTour = offerAppTour,
                onStartUprightMeasurement = onStartUprightMeasurement,
                onFinishCalibration = onFinishCalibration
            )
        }
    }
}
