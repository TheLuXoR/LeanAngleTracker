package com.example.leanangletracker

import android.hardware.SensorEvent
import com.example.leanangletracker.MainViewModelConfig.AUTO_PAUSE_LEAN_THRESHOLD
import com.example.leanangletracker.MainViewModelConfig.MAX_LEAN_DEG
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.sensor.BikeFrameMath
import com.example.leanangletracker.sensor.CalibrationTiltProgress
import com.example.leanangletracker.sensor.FusionGating
import com.example.leanangletracker.sensor.SensorTraceSample
import com.example.leanangletracker.sensor.fusionDeltaSeconds
import com.example.leanangletracker.ui.animation.BikeLean
import kotlin.math.abs

private const val UPRIGHT_STABLE_DURATION_NS = 1_500_000_000L
private const val GYRO_SAMPLE_FRESH_NS = 100_000_000L
private const val HIGH_ROTATION_WARNING_DELAY_NS = 10_000_000_000L

internal fun MainViewModel.registerRecentLeanSample(timestampNs: Long, leanDeg: Float) {
    recentLeanSamples += TimedLean(timestampNs, leanDeg)
    while (recentLeanSamples.size > 64) recentLeanSamples.removeFirst()
}

internal fun MainViewModel.fallbackLeanFromRecentSamples(timestampNs: Long): Float? {
    if (recentLeanSamples.isEmpty()) return null
    val value = recentLeanSamples.map { it.valueDeg }.average().toFloat()
    latestLeanDeg = value
    latestLeanTimestampNs = timestampNs
    return value
}

internal fun MainViewModel.updateLeanAngle(timestampNs: Long) {
    val orientation = fusionQuaternion ?: return
    val worldUpDevice = orientation.conjugate().rotate(Vec3(0f, 0f, 1f))
    publishLeanAngle(timestampNs, worldUpDevice)
}

private fun MainViewModel.publishLeanAngle(timestampNs: Long, worldUpDevice: Vec3) {
    val frame = bikeFrameCalibration ?: return
    val pose = BikeFrameMath.evaluatePose(worldUpDevice, frame)
    val signedFullLean = pose.signedLeanAngleDeg
    val leanDeg = if (pose.isUpsideDown) {
        signedFullLean
    } else {
        signedFullLean.coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    }

    val trackingStarted = _uiState.value.tracking.trackingStarted
    val isHighRotation = !pose.isUpsideDown && abs(signedFullLean) >= AUTO_PAUSE_LEAN_THRESHOLD
    if (isHighRotation && !trackingStarted) {
        if (highRotationStartNs == null) highRotationStartNs = timestampNs
    } else {
        highRotationStartNs = null
    }
    val showHighRotationWarning = highRotationStartNs?.let {
        timestampNs - it >= HIGH_ROTATION_WARNING_DELAY_NS
    } == true

    latestLeanDeg = leanDeg
    latestLeanTimestampNs = timestampNs
    if (!pose.isUpsideDown && abs(leanDeg) > abs(peakLeanSinceLastTick)) {
        peakLeanSinceLastTick = leanDeg
    }

    leanHistory += TimedLean(timestampNs, leanDeg)
    if (!pose.isUpsideDown) registerRecentLeanSample(timestampNs, leanDeg)
    pruneHistory(timestampNs, _uiState.value.settings.historyWindowSeconds)

    if ((pose.isUpsideDown || abs(leanDeg) >= AUTO_PAUSE_LEAN_THRESHOLD) &&
        _uiState.value.settings.autoPauseEnabled
    ) {
        val state = _uiState.value.tracking
        if (state.trackingStarted && !state.isPaused) togglePauseTracking()
    }

    var updatedGaugeExtrema: LeanExtrema? = null
    updateTrackingState {
        val nextGaugeExtrema = if (pose.isUpsideDown) {
            it.gaugeExtrema
        } else {
            it.gaugeExtrema.include(leanDeg)
        }
        if (nextGaugeExtrema != it.gaugeExtrema) {
            updatedGaugeExtrema = nextGaugeExtrema
        }
        it.copy(
            leanAngleDeg = leanDeg,
            gaugeExtrema = nextGaugeExtrema,
            leanHistoryDeg = leanHistory.map { sample -> sample.valueDeg },
            speedKmh = speedKmh,
            gpsActive = locationUpdatesRunning,
            hasTrackData = ridePointCount > 0,
            currentLatitude = latestGpsLocation?.latitude,
            currentLongitude = latestGpsLocation?.longitude,
            isUpsideDown = pose.isUpsideDown,
            showHighRotationWarning = showHighRotationWarning,
            recentPoints = recentRidePoints.toList(),
            autoPauseEnabled = _uiState.value.settings.autoPauseEnabled
        )
    }
    updatedGaugeExtrema?.let(settingsStore::saveGaugeExtrema)
}

internal fun MainViewModel.pruneHistory(currentTimestampNs: Long, historyWindowSeconds: Int) {
    val cutoff = currentTimestampNs - historyWindowSeconds * 1_000_000_000L
    while (leanHistory.isNotEmpty() && leanHistory.first().timestampNs < cutoff) {
        leanHistory.removeFirst()
    }
}

internal fun MainViewModel.updateGyroLean(event: SensorEvent) {
    val rawGyro = Vec3(event.values[0], event.values[1], event.values[2])
    latestRawGyro = rawGyro
    latestRawGyroTimestampNs = event.timestamp

    val previousTimestamp = lastGyroTimestampNs
    lastGyroTimestampNs = event.timestamp
    if (previousTimestamp == null || !fusionInitialized) return

    val dt = fusionDeltaSeconds(previousTimestamp, event.timestamp) ?: return

    val gyro = rawGyro - (gyroBiasVectorRadPerSec ?: Vec3(0f, 0f, 0f))
    val orientation = fusionQuaternion ?: madgwickFilter.orientation
    val estimatedUpDevice = orientation.conjugate().rotate(Vec3(0f, 0f, 1f)).normalized()
    val yawRate = gyro.dot(estimatedUpDevice)
    val accelerationWeight = FusionGating.accelerationWeight(
        baseWeight = madgwickFilter.adaptiveAccelWeight(filteredGravity),
        yawRateRadPerSec = yawRate
    )
    latestAccelerationWeight = accelerationWeight

    fusionQuaternion = madgwickFilter.update(gyro, filteredGravity, dt, accelerationWeight)
    updateLeanAngle(event.timestamp)
    recordSensorTrace(event.timestamp)
}

internal fun MainViewModel.processAccelerometer(event: SensorEvent) {
    val raw = Vec3(event.values[0], event.values[1], event.values[2])
    latestRawAcceleration = raw
    val previousTimestamp = lastAccelTimestampNs
    lastAccelTimestampNs = event.timestamp
    val dt = if (previousTimestamp == null) {
        0.01f
    } else {
        ((event.timestamp - previousTimestamp).coerceAtLeast(0L) / 1_000_000_000f)
            .coerceIn(0.001f, 0.05f)
    }
    filteredGravity = gravityLowPass.update(raw, dt)
    if (!fusionInitialized && filteredGravity.norm() > 1f) {
        fusionQuaternion = madgwickFilter.initializeFromGravity(filteredGravity)
        fusionInitialized = true
        lastGyroTimestampNs = null
    }

    updateStaticCalibrationProgress(event.timestamp)

    if (gyroscopeSensor == null && bikeFrameCalibration != null) {
        publishLeanAngle(event.timestamp, filteredGravity.normalized())
        recordSensorTrace(event.timestamp)
    }
}

private fun MainViewModel.updateStaticCalibrationProgress(timestampNs: Long) {
    val step = _uiState.value.calibration.calibrationStep
    if (step == BikeLean.DONE) return

    val gyroFresh = gyroscopeSensor == null || latestRawGyroTimestampNs?.let {
        abs(timestampNs - it) <= GYRO_SAMPLE_FRESH_NS
    } == true
    val gyroForCalibration = when {
        gyroscopeSensor == null -> Vec3(0f, 0f, 0f)
        gyroFresh -> latestRawGyro
        else -> Vec3(1f, 1f, 1f)
    }

    if (step == BikeLean.UPRIGHT) {
        val sampleProgress = staticCalibrationCollector.update(
            timestampNs = timestampNs,
            accelerationMs2 = filteredGravity,
            gyroRadPerSec = gyroForCalibration,
            requiredDurationNs = UPRIGHT_STABLE_DURATION_NS,
            poseIsValid = gyroFresh
        )
        pendingStableCalibrationSample = sampleProgress.readySample
        updateCalibrationState { state ->
            state.copy(
                currentProgress = sampleProgress.progress,
                maximumTiltProgress = 0f,
                leanDetected = false,
                currentTiltDeg = 0f,
                errorResId = null
            )
        }
        return
    }

    val upright = uprightUp ?: return
    val result = leanAndReturnDetector.update(
        timestampNs = timestampNs,
        accelerationMs2 = filteredGravity,
        gyroRadPerSec = gyroForCalibration,
        uprightWorldUpDevice = upright,
        oppositeOfDirection = if (step == BikeLean.RIGHT) calibrationSideAxis else null
    )
    val currentUp = filteredGravity.normalized()
    val signedTiltDeg = when (step) {
        BikeLean.LEFT -> -result.currentAngleDeg
        BikeLean.RIGHT -> {
            val sideAxis = calibrationSideAxis
            val pointsTowardsLeft = sideAxis != null &&
                BikeFrameMath.projectedOffset(currentUp, upright).dot(sideAxis) > 0f
            if (pointsTowardsLeft) -result.currentAngleDeg else result.currentAngleDeg
        }
    }
    updateCalibrationState { state ->
        state.copy(
            currentProgress = CalibrationTiltProgress.fromAngle(result.currentAngleDeg),
            maximumTiltProgress = CalibrationTiltProgress.fromAngle(result.maxAngleDeg),
            leanDetected = result.leanDetected,
            currentTiltDeg = signedTiltDeg,
            errorResId = if (result.wrongDirection) {
                R.string.calibration_error_wrong_direction
            } else {
                null
            }
        )
    }
    result.completedWorldUpDevice?.let { capturedUp ->
        completeAutomaticTiltCalibration(step, capturedUp)
    }
}

internal fun MainViewModel.prepareNextCalibrationStep() {
    staticCalibrationCollector.reset()
    leanAndReturnDetector.reset()
    pendingStableCalibrationSample = null
}

internal fun MainViewModel.resetSensorFusion() {
    gravityLowPass.reset()
    madgwickFilter.reset()
    staticCalibrationCollector.reset()
    leanAndReturnDetector.reset()
    sensorDebugTrace.clear()
    fusionQuaternion = com.example.leanangletracker.sensor.Quaternion.IDENTITY
    fusionInitialized = false
    latestRawAcceleration = Vec3(0f, 0f, 0f)
    latestRawGyro = Vec3(0f, 0f, 0f)
    latestRawGyroTimestampNs = null
    latestAccelerationWeight = 0f
    pendingStableCalibrationSample = null
    lastAccelTimestampNs = null
    lastGyroTimestampNs = null
    highRotationStartNs = null
}

internal fun MainViewModel.initializeFusionFromUp(worldUpDevice: Vec3) {
    fusionQuaternion = madgwickFilter.initializeFromGravity(worldUpDevice)
    fusionInitialized = true
    lastGyroTimestampNs = null
}

internal fun MainViewModel.sensorDebugTraceCsv(): String = sensorDebugTrace.toCsv()

private fun MainViewModel.recordSensorTrace(timestampNs: Long) {
    if (!BuildConfig.DEBUG) return
    sensorDebugTrace.add(
        SensorTraceSample(
            timestampNs = timestampNs,
            accelerationMs2 = latestRawAcceleration,
            gyroRadPerSec = latestRawGyro,
            orientation = fusionQuaternion ?: madgwickFilter.orientation,
            accelerationWeight = latestAccelerationWeight,
            leanAngleDeg = latestLeanDeg
        )
    )
}
