package com.example.leanangletracker

import android.hardware.SensorEvent
import android.hardware.SensorManager
import com.example.leanangletracker.MainViewModelConfig.AUTO_PAUSE_LEAN_THRESHOLD
import com.example.leanangletracker.MainViewModelConfig.MAX_LEAN_DEG
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.sensor.BikeFrameCalibration
import com.example.leanangletracker.sensor.LowPassFilter
import com.example.leanangletracker.sensor.MadgwickFilter
import com.example.leanangletracker.sensor.Quaternion
import com.example.leanangletracker.ui.animation.BikeLean
import kotlin.math.abs
import kotlin.math.atan2

private val gravityLowPass = LowPassFilter(cutoffHz = 4f)
private val madgwick = MadgwickFilter(betaBase = 0.055f)

internal fun MainViewModel.collectGyroBiasSample(event: SensorEvent) = Unit
internal fun MainViewModel.handleManualCalibrationSensorUpdate() = Unit
internal fun MainViewModel.finalizeManualCalibration() = Unit
internal fun MainViewModel.angleBetweenDeg(a: Vec3, b: Vec3): Float = 0f

internal fun MainViewModel.registerRecentLeanSample(timestampNs: Long, leanDeg: Float) {
    recentLeanSamples += TimedLean(timestampNs, leanDeg)
    while (recentLeanSamples.size > 64) recentLeanSamples.removeFirst()
}

internal fun MainViewModel.fallbackLeanFromRecentSamples(timestampNs: Long): Float? {
    if (recentLeanSamples.isEmpty()) return null
    val v = recentLeanSamples.map { it.valueDeg }.average().toFloat()
    latestLeanDeg = v
    latestLeanTimestampNs = timestampNs
    return v
}

internal fun MainViewModel.updateLeanAngle(timestampNs: Long) {
    val q = fusionQuaternion ?: return
    val frame = bikeFrameCalibration ?: return

    // World gravity from quaternion (device-frame +Z projected to world then flipped).
    val worldGravity = q.rotate(Vec3(0f, 0f, SensorManager.GRAVITY_EARTH)).normalized() * -1f
    val bikeUp = frame.bikeUpWorld
    val bikeRight = frame.bikeRightWorld

    // Signed lean from projected gravity around bike right axis, no Euler extraction.
    val leanRad = atan2(worldGravity.dot(bikeRight), worldGravity.dot(bikeUp))
    val leanDegRaw = (-Math.toDegrees(leanRad.toDouble()).toFloat()).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    val leanDeg = if (_uiState.value.settings.invertLeanAngle) -leanDegRaw else leanDegRaw

    latestLeanDeg = leanDeg
    latestLeanTimestampNs = timestampNs
    if (abs(leanDeg) > abs(peakLeanSinceLastTick)) peakLeanSinceLastTick = leanDeg

    leanHistory += TimedLean(timestampNs, leanDeg)
    registerRecentLeanSample(timestampNs, leanDeg)
    pruneHistory(timestampNs, _uiState.value.settings.historyWindowSeconds)

    if (abs(leanDeg) >= AUTO_PAUSE_LEAN_THRESHOLD && _uiState.value.settings.autoPauseEnabled) {
        val s = _uiState.value.tracking
        if (s.trackingStarted && !s.isPaused) togglePauseTracking()
    }

    updateTrackingState {
        it.copy(
            leanAngleDeg = leanDeg,
            maxLeftDeg = minOf(it.maxLeftDeg, if (leanDeg < 0f) leanDeg else 0f),
            maxRightDeg = maxOf(it.maxRightDeg, if (leanDeg > 0f) leanDeg else 0f),
            leanHistoryDeg = leanHistory.map { h -> h.valueDeg },
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
    while (leanHistory.isNotEmpty() && leanHistory.first().timestampNs < cutoff) leanHistory.removeFirst()
}

internal fun MainViewModel.updateGyroLean(event: SensorEvent) {
    val step = _uiState.value.calibration.calibrationStep
    val rawGyro = Vec3(event.values[0], event.values[1], event.values[2])
    if (step == BikeLean.UPRIGHT) {
        gyroBiasAccumulated += rawGyro
        gyroBiasSampleCount += 1
        return
    }

    val lastTs = lastGyroTimestampNs
    lastGyroTimestampNs = event.timestamp
    if (lastTs == null) return

    val dt = ((event.timestamp - lastTs).coerceAtLeast(0L) / 1_000_000_000f)
    if (dt <= 0f || dt > 0.05f) return

    val gyro = rawGyro - (gyroBiasVectorRadPerSec ?: Vec3(0f, 0f, 0f))
    val accelFiltered = filteredGravity
    val accelWeight = madgwick.adaptiveAccelWeight(accelFiltered)
    fusionQuaternion = madgwick.update(gyro, accelFiltered, dt, accelWeight)
    updateLeanAngle(event.timestamp)
}

internal fun MainViewModel.processAccelerometer(event: SensorEvent) {
    val raw = Vec3(event.values[0], event.values[1], event.values[2])
    val lastTs = lastAccelTimestampNs
    lastAccelTimestampNs = event.timestamp
    val dt = if (lastTs == null) 0.01f else ((event.timestamp - lastTs).coerceAtLeast(0L) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
    filteredGravity = gravityLowPass.update(raw, dt)
    latestLinearAccelerationMagnitude = abs(filteredGravity.norm() - SensorManager.GRAVITY_EARTH)

    val step = _uiState.value.calibration.calibrationStep
    if (step == BikeLean.UPRIGHT) {
        val stillness = (1f - (latestLinearAccelerationMagnitude / 1.2f)).coerceIn(0f, 1f)
        updateCalibrationState {
            it.copy(
                currentProgress = stillness,
                currentAngleDeg = 0f,
                isWrongDirection = false
            )
        }
    } else if (step == BikeLean.LEFT) {
        val progress = (headingSampleCount / 24f).coerceIn(0f, 1f)
        val up = uprightUp
        val currentUp = (filteredGravity * -1f).normalized()
        val angleDeg = if (up != null) {
            val dot = up.dot(currentUp).coerceIn(-1f, 1f)
            Math.toDegrees(kotlin.math.acos(dot).toDouble()).toFloat()
        } else 0f
        updateCalibrationState {
            it.copy(
                currentProgress = progress,
                leftMax = maxOf(it.leftMax, progress),
                currentAngleDeg = angleDeg,
                isWrongDirection = false
            )
        }
    }
}


internal fun MainViewModel.buildBikeFrameFromCalibration(forwardWorld: Vec3): BikeFrameCalibration {
    val up = uprightUp ?: Vec3(0f, 0f, 1f)
    val forward = (forwardWorld - up * forwardWorld.dot(up)).normalized()
    val right = up.cross(forward).normalized()
    return BikeFrameCalibration(up, forward, right, true)
}
