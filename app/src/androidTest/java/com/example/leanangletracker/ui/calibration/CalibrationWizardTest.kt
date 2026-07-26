package com.example.leanangletracker.ui.calibration

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.example.leanangletracker.CalibrationUiState
import com.example.leanangletracker.R
import com.example.leanangletracker.ui.animation.BikeLean
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CalibrationWizardTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun uprightButtonStartsMeasurementBeforeAutomaticCapture() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var state by mutableStateOf(CalibrationUiState())

        composeRule.setContent {
            MaterialTheme {
                CalibrationWizardPortrait(
                    state = state,
                    offerAppTour = true,
                    onStartUprightMeasurement = {
                        state = state.copy(uprightMeasurementStarted = true)
                    },
                    onFinishCalibration = {}
                )
            }
        }

        composeRule.onNodeWithText(
            context.getString(R.string.calibration_action_start_measurement)
        ).performClick()
        composeRule.onNodeWithText(
            context.getString(R.string.calibration_action_measurement_running)
        ).assertExists()
    }

    @Test
    fun detectedLeanShowsProminentReturnUprightFeedback() {
        composeRule.setContent {
            MaterialTheme {
                CalibrationWizardPortrait(
                    state = CalibrationUiState(
                        calibrationStep = BikeLean.LEFT,
                        currentProgress = 0.5f,
                        maximumTiltProgress = 0.75f,
                        leanDetected = true,
                        currentTiltDeg = -10f,
                        currentStepIndex = 2
                    ),
                    offerAppTour = true,
                    onStartUprightMeasurement = {},
                    onFinishCalibration = {}
                )
            }
        }

        composeRule.onNodeWithTag("calibrationReturnUprightFeedback").assertExists()
    }

    @Test
    fun completionStepLetsUserStartOrSkipTheTour() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var startTourSelection: Boolean? = null

        composeRule.setContent {
            MaterialTheme {
                CalibrationWizardPortrait(
                    state = CalibrationUiState(
                        calibrationStep = BikeLean.DONE,
                        isCalibrated = true,
                        completionPending = true,
                        currentProgress = 1f,
                        currentStepIndex = 4
                    ),
                    offerAppTour = true,
                    onStartUprightMeasurement = {},
                    onFinishCalibration = { startTourSelection = it }
                )
            }
        }

        composeRule.onNodeWithTag("calibrationCompletionStep").assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.calibration_action_skip_tour)
        ).assertExists()
        composeRule.onNodeWithText(
            context.getString(R.string.calibration_action_start_tour)
        ).performClick()

        composeRule.runOnIdle {
            assertEquals(true, startTourSelection)
        }
    }
}
