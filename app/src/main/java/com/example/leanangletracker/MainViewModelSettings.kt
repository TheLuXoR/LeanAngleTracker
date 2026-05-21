package com.example.leanangletracker

import android.annotation.SuppressLint
import androidx.lifecycle.viewModelScope
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MAX_MS
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_MIN_MS
import com.example.leanangletracker.MainViewModelConfig.RECORDER_INTERVAL_STEP_MS
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.sensor.Quaternion
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

    uprightUp = persistedCalibration.uprightUp
    bikeForwardAxis = persistedCalibration.bikeForwardAxis
    bikeFrameCalibration = buildBikeFrameFromCalibration(persistedCalibration.bikeForwardAxis ?: Vec3(0f, 1f, 0f))
    gyroLeanDeg = 0f
    gyroBiasVectorRadPerSec = persistedCalibration.gyroBiasVectorRadPerSec
    gyroBiasRadPerSec = persistedCalibration.bikeForwardAxis?.let { gyroBiasVectorRadPerSec?.dot(it) }
        ?: 0f
    lastGyroTimestampNs = null
    lastRollRateRadPerSec = 0f
    lastYawRateRadPerSec = 0f
    latestRotationLeanDeg = null
    latestRotationTimestampNs = null
    lastLeanComputationTimestampNs = null
    recentLeanSamples.clear()

    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.DONE,
            isCalibrated = true,
            instructionsResId = R.string.instructions_calibrated,
            currentStepIndex = 4,
            totalSteps = 4,
            isMeasuring = false
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

@SuppressLint("MissingPermission")
internal fun MainViewModel.startCalibration() {
    uprightUp = null
    leftUpPeak = null
    rightUpPeak = null
    bikeForwardAxis = null
    calibrationSideAxis = null
    bikeFrameCalibration = null
    fusionQuaternion = Quaternion.IDENTITY
    lastAccelTimestampNs = null
    headingSinAccum = 0f
    headingCosAccum = 0f
    headingSampleCount = 0
    gyroLeanDeg = 0f
    gyroBiasRadPerSec = 0f
    gyroBiasVectorRadPerSec = null
    gyroBiasCollectionStartNs = null
    gyroBiasAccumulated = Vec3(0f, 0f, 0f)
    gyroBiasSampleCount = 0
    lastGyroTimestampNs = null
    lastRollRateRadPerSec = 0f
    lastYawRateRadPerSec = 0f
    latestRotationLeanDeg = null
    latestRotationTimestampNs = null
    lastLeanComputationTimestampNs = null
    recentLeanSamples.clear()
    clearPersistedCalibration()
    
    if (hasLocationPermission()) {
        startLocationUpdates()
    }

    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.UPRIGHT,
            isCalibrated = false,
            instructionsResId = R.string.instructions_upright,
            leftMax = 0f,
            rightMax = 0f,
            dynamicMax = 0f,
            currentProgress = 0f,
            currentAngleDeg = 0f,
            isWrongDirection = false,
            currentStepIndex = 1,
            totalSteps = 4,
            isMeasuring = false,
            currentSpeedKmh = 0f
        )
    }
}

internal fun MainViewModel.captureUpright() {
    val calState = _uiState.value.calibration
    val currentStep = calState.calibrationStep
    when (currentStep) {
        BikeLean.UPRIGHT -> {
            uprightUp = (filteredGravity * -1f).normalized()
            gyroBiasVectorRadPerSec = if (gyroBiasSampleCount > 0) gyroBiasAccumulated * (1f / gyroBiasSampleCount) else Vec3(0f, 0f, 0f)
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            gyroBiasSampleCount = 0
            
            updateCalibrationState {
                it.copy(
                    calibrationStep = BikeLean.LEFT,
                    instructionsResId = R.string.instructions_tilt_left,
                    currentProgress = 0f,
                    currentStepIndex = 2
                )
            }
        }
        BikeLean.LEFT -> {
            if (calState.leftMax < 0.2f) return 
            updateCalibrationState {
                it.copy(
                    calibrationStep = BikeLean.RIGHT,
                    instructionsResId = R.string.instructions_tilt_right,
                    currentProgress = 0f,
                    currentStepIndex = 3
                )
            }
        }
        BikeLean.RIGHT -> {
            if (calState.rightMax < 0.2f) return
            
            updateCalibrationState {
                it.copy(
                    calibrationStep = BikeLean.DYNAMIC,
                    instructionsResId = R.string.instructions_dynamic,
                    currentProgress = 0f,
                    currentStepIndex = 4,
                    isMeasuring = false
                )
            }
        }
        BikeLean.DYNAMIC -> {
            headingSinAccum = 0f
            headingCosAccum = 0f
            headingSampleCount = 0
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            
            updateCalibrationState {
                it.copy(
                    isMeasuring = true,
                    currentProgress = 0.01f
                )
            }
        }
        else -> Unit
    }
}

internal fun MainViewModel.continueCalibrationFallback() = Unit

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
