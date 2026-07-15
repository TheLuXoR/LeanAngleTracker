package com.example.leanangletracker

import android.hardware.SensorEvent
import com.example.leanangletracker.MainViewModelConfig.AUTO_PAUSE_LEAN_THRESHOLD
import com.example.leanangletracker.MainViewModelConfig.CALIBRATION_TILT_MAX_RANGE
import com.example.leanangletracker.MainViewModelConfig.MAX_LEAN_DEG
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.sensor.BikeFrameMath
import com.example.leanangletracker.sensor.FusionGating
import com.example.leanangletracker.sensor.SensorTraceSample
import com.example.leanangletracker.sensor.fusionDeltaSeconds
import com.example.leanangletracker.ui.animation.BikeLean
import kotlin.math.abs

private const val UPRIGHT_STABLE_DURATION_NS = 1_500_000_000L
private const val TILTED_STABLE_DURATION_NS = 750_000_000L
private const val GYRO_SAMPLE_FRESH_NS = 100_000_000L

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
    val rawLean = BikeFrameMath.leanAngleDeg(worldUpDevice, frame)
        .coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    val leanDeg = if (_uiState.value.settings.invertLeanAngle) -rawLean else rawLean

    latestLeanDeg = leanDeg
    latestLeanTimestampNs = timestampNs
    if (abs(leanDeg) > abs(peakLeanSinceLastTick)) peakLeanSinceLastTick = leanDeg

    leanHistory += TimedLean(timestampNs, leanDeg)
    registerRecentLeanSample(timestampNs, leanDeg)
    pruneHistory(timestampNs, _uiState.value.settings.historyWindowSeconds)

    if (abs(leanDeg) >= AUTO_PAUSE_LEAN_THRESHOLD && _uiState.value.settings.autoPauseEnabled) {
        val state = _uiState.value.tracking
        if (state.trackingStarted && !state.isPaused) togglePauseTracking()
    }

    updateTrackingState {
        it.copy(
            leanAngleDeg = leanDeg,
            maxLeftDeg = minOf(it.maxLeftDeg, if (leanDeg < 0f) leanDeg else 0f),
            maxRightDeg = maxOf(it.maxRightDeg, if (leanDeg > 0f) leanDeg else 0f),
            leanHistoryDeg = leanHistory.map { sample -> sample.valueDeg },
            speedKmh = speedKmh,
            gpsActive = locationUpdatesRunning,
            hasTrackData = ridePointCount > 0,
            currentLatitude = latestGpsLocation?.latitude,
            currentLongitude = latestGpsLocation?.longitude,
            recentPoints = recentRidePoints.toList(),
            autoPauseEnabled = _uiState.value.settings.autoPauseEnabled
        )
    }
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
    val currentUp = filteredGravity.normalized()
    val upright = uprightUp

    val requiredDurationNs: Long
    val poseIsValid: Boolean
    val angleDeg: Float
    val wrongDirection: Boolean
    when (step) {
        BikeLean.UPRIGHT -> {
            requiredDurationNs = UPRIGHT_STABLE_DURATION_NS
            poseIsValid = gyroFresh
            angleDeg = 0f
            wrongDirection = false
        }

        BikeLean.LEFT -> {
            val up = upright ?: return
            angleDeg = BikeFrameMath.angleBetweenDeg(up, currentUp)
            requiredDurationNs = TILTED_STABLE_DURATION_NS
            poseIsValid = gyroFresh && angleDeg >= BikeFrameMath.MIN_TILT_DEG
            wrongDirection = false
        }

        BikeLean.RIGHT -> {
            val up = upright ?: return
            angleDeg = BikeFrameMath.angleBetweenDeg(up, currentUp)
            val currentOffset = BikeFrameMath.projectedOffset(currentUp, up)
            val leftDirection = calibrationSideAxis
            wrongDirection = angleDeg > 3f && leftDirection != null &&
                currentOffset.norm() > 1e-4f &&
                currentOffset.normalized().dot(leftDirection) > 0f
            requiredDurationNs = TILTED_STABLE_DURATION_NS
            poseIsValid = gyroFresh && angleDeg >= BikeFrameMath.MIN_TILT_DEG && !wrongDirection
        }

        BikeLean.DONE -> return
    }

    val sampleProgress = staticCalibrationCollector.update(
        timestampNs = timestampNs,
        accelerationMs2 = filteredGravity,
        gyroRadPerSec = if (gyroscopeSensor == null) Vec3(0f, 0f, 0f) else latestRawGyro,
        requiredDurationNs = requiredDurationNs,
        poseIsValid = poseIsValid
    )
    pendingStableCalibrationSample = sampleProgress.readySample

    updateCalibrationState { state ->
        state.copy(
            currentProgress = sampleProgress.progress,
            leftMax = if (step == BikeLean.LEFT) {
                maxOf(state.leftMax, (angleDeg / CALIBRATION_TILT_MAX_RANGE).coerceIn(0f, 1f))
            } else {
                state.leftMax
            },
            rightMax = if (step == BikeLean.RIGHT && !wrongDirection) {
                maxOf(state.rightMax, (angleDeg / CALIBRATION_TILT_MAX_RANGE).coerceIn(0f, 1f))
            } else {
                state.rightMax
            },
            currentAngleDeg = when (step) {
                BikeLean.LEFT -> -angleDeg
                BikeLean.RIGHT -> angleDeg
                else -> 0f
            },
            errorResId = if (wrongDirection) R.string.calibration_error_wrong_direction else null
        )
    }
}

internal fun MainViewModel.prepareNextCalibrationStep() {
    staticCalibrationCollector.reset()
    pendingStableCalibrationSample = null
}

internal fun MainViewModel.resetSensorFusion() {
    gravityLowPass.reset()
    madgwickFilter.reset()
    staticCalibrationCollector.reset()
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
