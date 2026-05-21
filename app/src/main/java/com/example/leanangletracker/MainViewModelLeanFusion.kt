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
import kotlin.math.cos
import kotlin.math.sin

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

    val worldUpD = q.conjugate().rotate(Vec3(0f, 0f, 1f))
    val bUpD = frame.bikeUpWorld
    val bRightD = frame.bikeRightWorld
    
    val upProj = worldUpD.dot(bUpD)
    val rightProj = worldUpD.dot(bRightD)
    
    val leanRad = atan2(-rightProj, upProj)
    val leanDegRaw = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
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
    when (step) {
        BikeLean.UPRIGHT -> {
            val stillness = (1f - (latestLinearAccelerationMagnitude / 1.2f)).coerceIn(0f, 1f)
            updateCalibrationState {
                it.copy(currentProgress = stillness, currentAngleDeg = 0f, isWrongDirection = false)
            }
        }
        BikeLean.LEFT -> {
            val up = uprightUp ?: return
            val currentUp = (filteredGravity * -1f).normalized()
            val angle = Math.toDegrees(Math.acos(up.dot(currentUp).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            updateCalibrationState {
                it.copy(
                    currentProgress = (angle / 15f).coerceIn(0f, 1f),
                    leftMax = maxOf(it.leftMax, (angle / 15f).coerceIn(0f, 1f)),
                    currentAngleDeg = -angle
                )
            }
            if (angle > 4f) {
                if (leftUpPeak == null || angle > Math.toDegrees(Math.acos(up.dot(leftUpPeak!!).toDouble().coerceIn(-1.0, 1.0)))) {
                    leftUpPeak = currentUp
                }
            }
        }
        BikeLean.RIGHT -> {
            val up = uprightUp ?: return
            val currentUp = (filteredGravity * -1f).normalized()
            val angle = Math.toDegrees(Math.acos(up.dot(currentUp).toDouble().coerceIn(-1.0, 1.0))).toFloat()
            updateCalibrationState {
                it.copy(
                    currentProgress = (angle / 15f).coerceIn(0f, 1f),
                    rightMax = maxOf(it.rightMax, (angle / 15f).coerceIn(0f, 1f)),
                    currentAngleDeg = angle
                )
            }
            if (angle > 4f) {
                if (rightUpPeak == null || angle > Math.toDegrees(Math.acos(up.dot(rightUpPeak!!).toDouble().coerceIn(-1.0, 1.0)))) {
                    rightUpPeak = currentUp
                }
            }
        }
        BikeLean.DYNAMIC -> {
            val calibState = _uiState.value.calibration
            if (calibState.isMeasuring) {
                val q = fusionQuaternion
                var forwardSample: Vec3? = null
                
                // Strategy A: GPS velocity (Very reliable if moving)
                val loc = latestGpsLocation
                if (q != null && loc != null && speedKmh > 5f && loc.hasBearing()) {
                    val bearingRad = Math.toRadians(loc.bearing.toDouble())
                    val velocityWorld = Vec3(sin(bearingRad).toFloat(), cos(bearingRad).toFloat(), 0f)
                    forwardSample = q.conjugate().rotate(velocityWorld)
                } 
                // Strategy B: Linear acceleration (Works when speeding up/braking)
                else {
                    val upD = uprightUp ?: return
                    val gravityD = upD * -SensorManager.GRAVITY_EARTH
                    val linAccelD = raw - gravityD
                    val lateralAccelD = linAccelD - upD * linAccelD.dot(upD)
                    if (lateralAccelD.norm() > 0.15f) { // Lowered threshold
                        forwardSample = lateralAccelD.normalized()
                    }
                }

                if (forwardSample != null) {
                    gyroBiasAccumulated += forwardSample
                    headingSampleCount += 1
                    
                    val calculatedProgress = (headingSampleCount / 40f).coerceIn(0f, 1f)
                    updateCalibrationState {
                        it.copy(
                            currentProgress = calculatedProgress,
                            dynamicMax = maxOf(it.dynamicMax, calculatedProgress)
                        )
                    }
                    
                    if (calculatedProgress >= 1f) {
                        finalizeDynamicCalibration()
                    }
                }
            }
        }
        else -> Unit
    }
}

internal fun MainViewModel.finalizeDynamicCalibration() {
    val forwardD = gyroBiasAccumulated.normalized()
    if (forwardD.norm() < 0.1f) return
    
    bikeFrameCalibration = buildBikeFrameFromCalibration(forwardD)
    bikeForwardAxis = bikeFrameCalibration?.bikeForwardWorld
    
    updateCalibrationState {
        it.copy(
            calibrationStep = BikeLean.DONE,
            isCalibrated = true,
            instructionsResId = R.string.instructions_calibrated,
            currentProgress = 1f,
            isMeasuring = false
        )
    }
    persistCalibration()
}

internal fun MainViewModel.buildBikeFrameFromCalibration(forwardDevice: Vec3): BikeFrameCalibration {
    val up = uprightUp ?: Vec3(0f, 0f, 1f)
    val forward = (forwardDevice - up * forwardDevice.dot(up)).normalized()
    var right = up.cross(forward).normalized()
    
    val rPeak = rightUpPeak
    val lPeak = leftUpPeak
    
    if (rPeak != null && lPeak != null) {
        val rDir = (rPeak - up * rPeak.dot(up)).normalized()
        val lDir = (lPeak - up * lPeak.dot(up)).normalized()
        val score = rDir.dot(right) - lDir.dot(right)
        if (score < 0) right = right * -1f
    } else if (rPeak != null) {
        val rDir = (rPeak - up * rPeak.dot(up)).normalized()
        if (rDir.dot(right) < 0) right = right * -1f
    } else if (lPeak != null) {
        val lDir = (lPeak - up * lPeak.dot(up)).normalized()
        if (lDir.dot(right) > 0) right = right * -1f
    }
    
    val finalForward = right.cross(up).normalized()
    return BikeFrameCalibration(up, finalForward, right, true)
}
