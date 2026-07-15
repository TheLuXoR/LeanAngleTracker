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
            invertLeanAngle = persistedSettings.invertLeanAngle,
            historyWindowSeconds = persistedSettings.historyWindowSeconds,
            recorderIntervalMs = persistedSettings.recorderIntervalMs,
            gpsTrackingEnabled = persistedSettings.gpsTrackingEnabled && it.locationPermissionGranted,
            autoResumeEnabled = persistedSettings.autoResumeEnabled,
            isAutoResumePurchased = persistedSettings.isAutoResumePurchased,
            autoPauseEnabled = persistedSettings.autoPauseEnabled
        )
    }
    updateTrackingState {
        it.copy(
            gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled,
            autoPauseEnabled = persistedSettings.autoPauseEnabled
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
    updateSettingsState { it.copy(locationPermissionGranted = granted) }
    if (!granted) {
        stopLocationUpdates()
        updateTrackingState { it.copy(trackingStarted = false) }
    }
    updateTrackingState {
        it.copy(
            gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled && granted,
            gpsActive = locationUpdatesRunning && granted
        )
    }
    persistSettings()
}

internal fun MainViewModel.setGpsTrackingEnabled(enabled: Boolean) {
    updateSettingsState { it.copy(gpsTrackingEnabled = enabled) }
    if (!enabled) {
        stopLocationUpdates()
        recentRidePoints.clear()
        pausedPointsBuffer.clear()
        activeRideId = null
        activeRideStartedMs = null
        accumulatedTimeMs = 0L
        latestGpsLocation = null
        latestGpsTimestampNs = null
        speedKmh = 0f
        trackLengthMeters = 0f
        ridePointCount = 0
        rideSumSpeedKmh = 0f
        rideSumAbsLeanDeg = 0f
        isCheckingForExtension = false
        stopRecorder()
        updateTrackingState {
            it.copy(
                speedKmh = 0f,
                gpsActive = false,
                hasTrackData = false,
                trackingStarted = false,
                isPaused = false,
                currentLatitude = null,
                currentLongitude = null,
                elapsedTimeMs = 0L,
                averageSpeedKmh = 0f,
                trackLengthKm = 0f,
                averageLeanAngleDeg = 0f,
                isUpsideDown = false,
                showHighRotationWarning = false,
                recentPoints = emptyList()
            )
        }
    }
    updateTrackingState {
        it.copy(gpsTrackingEnabled = enabled && _uiState.value.settings.locationPermissionGranted)
    }
    persistSettings()
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

internal fun MainViewModel.setInvertLeanAngle(invert: Boolean) {
    val previous = _uiState.value
    if (previous.settings.invertLeanAngle == invert) return

    val transformedHistory = previous.tracking.leanHistoryDeg.map { -it }
    val transformedTimedHistory = leanHistory.map { it.copy(valueDeg = -it.valueDeg) }
    val transformedRecentSamples = recentLeanSamples.map { it.copy(valueDeg = -it.valueDeg) }

    leanHistory.clear()
    leanHistory.addAll(transformedTimedHistory)
    recentLeanSamples.clear()
    recentLeanSamples.addAll(transformedRecentSamples)

    _uiState.update { state ->
        state.copy(
            settings = state.settings.copy(invertLeanAngle = invert),
            tracking = state.tracking.copy(
                leanAngleDeg = -state.tracking.leanAngleDeg,
                maxLeftDeg = -state.tracking.maxRightDeg,
                maxRightDeg = -state.tracking.maxLeftDeg,
                leanHistoryDeg = transformedHistory
            )
        )
    }
    persistSettings()
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
    if (!enabled) {
        pausedPointsBuffer.clear()
        autoResumeTimerStartMs = null
    }
    persistSettings()
}

internal fun MainViewModel.setAutoPauseEnabled(enabled: Boolean) {
    updateSettingsState { it.copy(autoPauseEnabled = enabled) }
    updateTrackingState { it.copy(autoPauseEnabled = enabled) }
    persistSettings()
}

internal fun MainViewModel.purchaseAutoResume() {
    updateSettingsState { it.copy(isAutoResumePurchased = true, autoResumeEnabled = true) }
    persistSettings()
}

internal fun MainViewModel.resetExtrema() {
    updateTrackingState {
        it.copy(
            maxLeftDeg = 0f,
            maxRightDeg = 0f
        )
    }
}
