package com.example.leanangletracker

import androidx.lifecycle.viewModelScope
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MAX_MS
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MIN_MS
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_STEP_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.sensor.BikeFrameFailure
import com.example.leanangletracker.sensor.BikeFrameMath
import com.example.leanangletracker.ui.animation.BikeLean

private const val APP_TOUR_VERSION = 1
private const val APP_TOUR_PAGE_COUNT = 6

internal fun MainViewModel.checkForUnfinishedRides() {
    viewModelScope.launch(Dispatchers.IO) {
        val pending = rideSessionUseCases.findUnfinishedRideForRecovery() ?: return@launch
        launch(Dispatchers.Main) { _uiState.update { it.copy(pendingRecovery = pending) } }
    }
}

internal fun MainViewModel.resolveRecovery(continueRide: Boolean) {
    val session = _uiState.value.pendingRecovery ?: return
    _uiState.update { it.copy(pendingRecovery = null) }
    
    if (continueRide) {
        viewModelScope.launch(Dispatchers.Main) {
            resumeRideSession(session)
        }
    } else {
        viewModelScope.launch(Dispatchers.IO) {
            if (session.points.isNotEmpty()) {
                val recoveredRide = rideSessionUseCases.saveRecoveredRide(session)
                launch(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            rideHistory = (listOf(recoveredRide.toSummary()) + state.rideHistory.filter { it.rideId != recoveredRide.rideId })
                                .sortedByDescending { it.startedAtMs },
                            lastSavedRideId = recoveredRide.rideId
                        )
                    }
                }
            } else {
                rideRepository.deleteRide(session.rideId)
                launch(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(rideHistory = state.rideHistory.filter { it.rideId != session.rideId })
                    }
                }
            }
        }
    }
}

internal fun MainViewModel.loadPersistedState() {
    val persistedState = settingsStore.load(
        recorderIntervalMinMs = RECORDER_INTERVAL_MIN_MS,
        recorderIntervalMaxMs = RECORDER_INTERVAL_MAX_MS
    )
    val persistedSettings = persistedState.settings

    updateSettingsState {
        it.copy(
            historyWindowSeconds = persistedSettings.historyWindowSeconds,
            recorderIntervalMs = persistedSettings.recorderIntervalMs,
            autoResumeEnabled = persistedSettings.autoResumeEnabled,
            isAutoResumePurchased = persistedSettings.isAutoResumePurchased,
            isPremiumSubscribed = persistedSettings.isPremiumSubscribed,
            autoPauseEnabled = persistedSettings.autoPauseEnabled
        )
    }
    updateTrackingState {
        it.copy(
            autoPauseEnabled = persistedSettings.autoPauseEnabled,
            gaugeExtrema = persistedState.gaugeExtrema
        )
    }
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                offerPending = persistedState.completedAppTourVersion < APP_TOUR_VERSION
            )
        )
    }

    viewModelScope.launch(Dispatchers.IO) {
        val history = rideSessionUseCases.loadRideHistory()
        launch(Dispatchers.Main) {
            _uiState.update { it.copy(rideHistory = history) }
        }
    }

    val persistedCalibration = persistedState.calibration ?: return

    val up = persistedCalibration.uprightUp ?: return
    val forward = persistedCalibration.bikeForwardAxis ?: return
    val frame = BikeFrameMath.fromUpAndForward(up, forward)
    if (frame == null) {
        clearPersistedCalibration()
        return
    }

    uprightUp = frame.bikeUpDevice
    bikeForwardAxis = frame.bikeForwardDevice
    bikeFrameCalibration = frame
    gyroBiasVectorRadPerSec = persistedCalibration.gyroBiasVectorRadPerSec
    lastGyroTimestampNs = null
    recentLeanSamples.clear()

    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.DONE,
            isCalibrated = true,
            currentStepIndex = 3,
            totalSteps = 3,
            currentProgress = 1f,
            maximumTiltProgress = 0f,
            errorResId = null
        )
    }
}

internal fun MainViewModel.backfillRouteDescriptions() {
    viewModelScope.launch(Dispatchers.IO) {
        val updated = rideSessionUseCases.backfillRouteDescriptions()
        if (updated.isEmpty()) return@launch
        launch(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(rideHistory = state.rideHistory.map { summary ->
                    updated.find { it.rideId == summary.rideId }?.toSummary() ?: summary
                })
            }
        }
    }
}

internal fun MainViewModel.persistSettings() {
    settingsStore.save(_uiState.value.settings)
}

internal fun MainViewModel.persistCalibration() {
    val up = uprightUp ?: return
    val forward = bikeForwardAxis ?: return
    settingsStore.saveCalibration(up, forward, gyroBiasVectorRadPerSec)
}

internal fun MainViewModel.clearPersistedCalibration() {
    settingsStore.clearCalibration()
}

internal inline fun MainViewModel.updateCalibrationState(transform: (CalibrationUiState) -> CalibrationUiState) {
    _uiState.update { it.copy(calibration = transform(it.calibration)) }
}

internal inline fun MainViewModel.updateTrackingState(transform: (TrackingUiState) -> TrackingUiState) {
    _uiState.update { it.copy(tracking = transform(it.tracking)) }
}

internal inline fun MainViewModel.updateSettingsState(transform: (SettingsUiState) -> SettingsUiState) {
    _uiState.update { it.copy(settings = transform(it.settings)) }
}

internal fun MainViewModel.onLocationPermissionResult(granted: Boolean) {
    _uiState.update { it.copy(locationPermissionGranted = granted) }
    if (!granted) {
        stopLocationUpdates()
        autoResumeTimerStartMs = null
        updateTrackingState {
            it.copy(trackingStarted = false, showAutoResumePremiumShortcut = false)
        }
    }
    updateTrackingState {
        it.copy(
            gpsActive = locationUpdatesRunning && granted
        )
    }
}

internal fun MainViewModel.startCalibration() {
    uprightUp = null
    leftUpPeak = null
    bikeForwardAxis = null
    calibrationSideAxis = null
    bikeFrameCalibration = null
    resetSensorFusion()
    gyroBiasVectorRadPerSec = null
    recentLeanSamples.clear()
    clearPersistedCalibration()

    updateTrackingState {
        it.copy(isUpsideDown = false, showHighRotationWarning = false)
    }
    
    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.UPRIGHT,
            isCalibrated = false,
            currentProgress = 0f,
            maximumTiltProgress = 0f,
            leanDetected = false,
            currentTiltDeg = 0f,
            errorResId = null,
            currentStepIndex = 1,
            totalSteps = 3
        )
    }
}

internal fun MainViewModel.captureUpright() {
    val calState = _uiState.value.calibration
    if (calState.calibrationStep != BikeLean.UPRIGHT) return
    val stableSample = pendingStableCalibrationSample
    if (stableSample == null || calState.currentProgress < 1f) {
        updateCalibrationState {
            it.copy(errorResId = R.string.calibration_error_unstable)
        }
        return
    }

    uprightUp = stableSample.worldUpDevice
    gyroBiasVectorRadPerSec = stableSample.gyroBiasRadPerSec
    initializeFusionFromUp(stableSample.worldUpDevice)
    prepareNextCalibrationStep()

    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.LEFT,
            currentProgress = 0f,
            maximumTiltProgress = 0f,
            leanDetected = false,
            currentTiltDeg = 0f,
            currentStepIndex = 2,
            errorResId = null
        )
    }
}

internal fun MainViewModel.completeAutomaticTiltCalibration(step: BikeLean, capturedUp: Vec3) {
    when (step) {
        BikeLean.LEFT -> {
            val up = uprightUp ?: return
            val leftDirection = BikeFrameMath.projectedOffset(capturedUp, up).normalized()
            if (leftDirection.norm() < 0.9f) {
                updateCalibrationState { it.copy(errorResId = R.string.calibration_error_degenerate) }
                return
            }
            leftUpPeak = capturedUp
            calibrationSideAxis = leftDirection
            prepareNextCalibrationStep()
            updateCalibrationState {
                it.copy(
                    calibrationStep = BikeLean.RIGHT,
                    currentProgress = 0f,
                    maximumTiltProgress = 0f,
                    leanDetected = false,
                    currentTiltDeg = 0f,
                    currentStepIndex = 3,
                    errorResId = null
                )
            }
        }

        BikeLean.RIGHT -> {
            val up = uprightUp ?: return
            val left = leftUpPeak ?: return
            val result = BikeFrameMath.fromStaticSamples(up, left, capturedUp)
            val frame = result.calibration
            if (frame == null) {
                val error = when (result.failure) {
                    BikeFrameFailure.INSUFFICIENT_LEFT_TILT,
                    BikeFrameFailure.INSUFFICIENT_RIGHT_TILT -> R.string.calibration_error_too_little
                    BikeFrameFailure.SAME_DIRECTION -> R.string.calibration_error_same_direction
                    BikeFrameFailure.DEGENERATE_FRAME,
                    null -> R.string.calibration_error_degenerate
                }
                prepareNextCalibrationStep()
                updateCalibrationState {
                    it.copy(
                        currentProgress = 0f,
                        maximumTiltProgress = 0f,
                        leanDetected = false,
                        currentTiltDeg = 0f,
                        errorResId = error
                    )
                }
                return
            }

            bikeFrameCalibration = frame
            bikeForwardAxis = frame.bikeForwardDevice
            prepareNextCalibrationStep()
            updateCalibrationState {
                it.copy(
                    calibrationStep = BikeLean.DONE,
                    isCalibrated = true,
                    currentProgress = 1f,
                    maximumTiltProgress = 0f,
                    leanDetected = false,
                    currentTiltDeg = 0f,
                    errorResId = null
                )
            }
            persistCalibration()
        }

        else -> Unit
    }
}

internal fun MainViewModel.setHistoryWindowSeconds(seconds: Int) {
    val clamped = seconds.coerceIn(5, 120)
    val previous = _uiState.value
    if (previous.settings.historyWindowSeconds == clamped) return

    leanHistory.lastOrNull()?.let { pruneHistory(it.timestampNs, clamped) }
    _uiState.update { state ->
        state.copy(
            settings = state.settings.copy(historyWindowSeconds = clamped),
            tracking = state.tracking.copy(leanHistoryDeg = leanHistory.map { it.valueDeg })
        )
    }
    persistSettings()
}

internal fun MainViewModel.setRecorderIntervalMs(intervalMs: Int) {
    val stepped = (intervalMs / RECORDER_INTERVAL_STEP_MS) * RECORDER_INTERVAL_STEP_MS
    val clamped = stepped.coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
    
    _uiState.update { it.copy(settings = it.settings.copy(recorderIntervalMs = clamped)) }
    persistSettings()
}

internal fun MainViewModel.setAutoResumeEnabled(enabled: Boolean) {
    updateSettingsState { it.copy(autoResumeEnabled = enabled) }
    autoResumeTimerStartMs = null
    updateTrackingState { it.copy(showAutoResumePremiumShortcut = false) }
    if (!enabled) {
        pausedPointsBuffer.clear()
    }
    persistSettings()
}

internal fun MainViewModel.setAutoPauseEnabled(enabled: Boolean) {
    updateSettingsState { it.copy(autoPauseEnabled = enabled) }
    updateTrackingState { it.copy(autoPauseEnabled = enabled) }
    persistSettings()
}

internal fun MainViewModel.setPremiumEntitlements(
    isAutoResumePurchased: Boolean,
    isPremiumSubscribed: Boolean
) {
    val hadAutoResumeAccess = _uiState.value.settings.run {
        this.isAutoResumePurchased || this.isPremiumSubscribed
    }
    val hasAutoResumeAccess = isAutoResumePurchased || isPremiumSubscribed
    updateSettingsState {
        it.copy(
            isAutoResumePurchased = isAutoResumePurchased,
            isPremiumSubscribed = isPremiumSubscribed,
            autoResumeEnabled = when {
                !hasAutoResumeAccess -> false
                !hadAutoResumeAccess -> true
                else -> it.autoResumeEnabled
            }
        )
    }
    autoResumeTimerStartMs = null
    updateTrackingState { it.copy(showAutoResumePremiumShortcut = false) }
    if (!hasAutoResumeAccess) pausedPointsBuffer.clear()
    persistSettings()
}

internal fun MainViewModel.resetGaugeExtrema() {
    updateTrackingState {
        it.copy(gaugeExtrema = LeanExtrema.ZERO)
    }
    settingsStore.saveGaugeExtrema(LeanExtrema.ZERO)
}

internal fun MainViewModel.acceptAppTourOffer() {
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                offerPending = false,
                isActive = true,
                currentPage = 0
            )
        )
    }
}

internal fun MainViewModel.startAppTour() {
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                offerPending = false,
                isActive = true,
                currentPage = 0
            )
        )
    }
}

internal fun MainViewModel.showPreviousAppTourPage() {
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                currentPage = (state.appTour.currentPage - 1).coerceAtLeast(0)
            )
        )
    }
}

internal fun MainViewModel.showNextAppTourPage() {
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                currentPage = (state.appTour.currentPage + 1).coerceAtMost(APP_TOUR_PAGE_COUNT - 1)
            )
        )
    }
}

internal fun MainViewModel.completeAppTour() {
    settingsStore.saveCompletedAppTourVersion(APP_TOUR_VERSION)
    _uiState.update { state ->
        state.copy(
            appTour = state.appTour.copy(
                offerPending = false,
                isActive = false,
                currentPage = 0
            )
        )
    }
}
