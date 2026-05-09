package com.example.leanangletracker

import android.hardware.SensorEvent
import com.example.leanangletracker.data.Vec3
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.atan2

    internal fun MainViewModel.collectGyroBiasSample(event: SensorEvent) {
        val calibrationStep = _uiState.value.calibration.calibrationStep
        val isManualUpright = calibrationStep == BikeLean.UPRIGHT
        val isDynamicBiasPossible = calibrationStep == BikeLean.DONE && speedKmh < 1.0f && abs(gyroLeanDeg) < 3.0f

        if (!isManualUpright && !isDynamicBiasPossible) return

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val angularSpeed = omega.norm()
        val isStable =
            latestLinearAccelerationMagnitude <= GYRO_BIAS_LINEAR_ACCEL_MAX &&
                angularSpeed <= GYRO_BIAS_ANGULAR_SPEED_MAX

        if (!isStable) {
            gyroBiasCollectionStartNs = null
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            gyroBiasSampleCount = 0
            if (isManualUpright) {
                updateCalibrationState { state ->
                    state.copy(instructionsResId = R.string.instructions_hold_still_for_bias)
                }
            }
            return
        }

        if (isManualUpright && _uiState.value.calibration.instructionsResId == R.string.instructions_hold_still_for_bias) {
            updateCalibrationState { state ->
                state.copy(instructionsResId = R.string.instructions_upright)
            }
        }

        val start = gyroBiasCollectionStartNs ?: event.timestamp.also {
            gyroBiasCollectionStartNs = it
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            gyroBiasSampleCount = 0
        }

        gyroBiasAccumulated += omega
        gyroBiasSampleCount += 1

        if (event.timestamp - start < GYRO_BIAS_COLLECTION_DURATION_NS || gyroBiasSampleCount < 10) return

        val invCount = 1f / gyroBiasSampleCount
        val newBias = gyroBiasAccumulated * invCount
        
        gyroBiasVectorRadPerSec = newBias
        bikeForwardAxis?.let { forward ->
            gyroBiasRadPerSec = newBias.dot(forward)
        }
        
        gyroBiasCollectionStartNs = null
        gyroBiasAccumulated = Vec3(0f, 0f, 0f)
        gyroBiasSampleCount = 0
        
        if (isDynamicBiasPossible) {
            persistCalibration() 
        }
    }

    internal fun MainViewModel.handleManualCalibrationSensorUpdate() {
        val up = uprightUp ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val angleDeg = angleBetweenDeg(up, currentUp)
        
        val step = _uiState.value.calibration.calibrationStep

        if (calibrationSideAxis == null && step == BikeLean.LEFT && angleDeg > 3f) {
            calibrationSideAxis = (currentUp - up * currentUp.dot(up)).normalized()
        }

        val sideComponent = if (calibrationSideAxis != null) currentUp.dot(calibrationSideAxis!!) else 0f
        val isLeftSide = sideComponent > 0.1f
        val isRightSide = sideComponent < -0.1f

        val progress = (angleDeg / CALIBRATION_TILT_MAX_RANGE).coerceIn(0f, 1f)
        
        val isWrongWay = when (step) {
            BikeLean.LEFT -> isRightSide && angleDeg > 5f
            BikeLean.RIGHT -> isLeftSide && angleDeg > 5f
            else -> false
        }

        updateCalibrationState { state ->
            val newLeftMax = if (step == BikeLean.LEFT && isLeftSide) maxOf(state.leftMax, progress) else state.leftMax
            val newRightMax = if (step == BikeLean.RIGHT && isRightSide) maxOf(state.rightMax, progress) else state.rightMax
            
            if (step == BikeLean.LEFT && isLeftSide && progress >= newLeftMax) leftUpPeak = currentUp
            if (step == BikeLean.RIGHT && isRightSide && progress >= newRightMax) rightUpPeak = currentUp

            val directionProgress = when (step) {
                BikeLean.LEFT -> if (isLeftSide) progress else 0f
                BikeLean.RIGHT -> if (isRightSide) progress else 0f
                else -> progress
            }

            state.copy(
                leftMax = newLeftMax,
                rightMax = newRightMax,
                currentProgress = directionProgress,
                currentAngleDeg = angleDeg,
                isWrongDirection = isWrongWay
            )
        }
    }

    internal fun MainViewModel.finalizeManualCalibration() {
        val up = uprightUp ?: return
        val left = leftUpPeak ?: up
        val right = rightUpPeak ?: up
        
        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f) {
            val side = calibrationSideAxis ?: Vec3(1f, 0f, 0f)
            forward = side.cross(up).normalized()
        }
        
        bikeForwardAxis = forward
        gyroLeanDeg = 0f
        gyroBiasRadPerSec = gyroBiasVectorRadPerSec?.dot(forward) ?: 0f
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
                currentProgress = 0f,
                currentAngleDeg = 0f
            )
        }
        persistCalibration()
    }

    internal fun MainViewModel.angleBetweenDeg(a: Vec3, b: Vec3): Float {
        val dot = a.dot(b).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot).toDouble()).toFloat()
    }

    internal fun MainViewModel.registerRecentLeanSample(timestampNs: Long, leanDeg: Float) {
        recentLeanSamples += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)
        while (recentLeanSamples.size > SENSOR_TIMING_POLICY.recentLeanBufferSize) {
            recentLeanSamples.removeFirst()
        }
    }

    internal fun MainViewModel.fallbackLeanFromRecentSamples(timestampNs: Long): Float? {
        if (recentLeanSamples.isEmpty()) return null
        val averaged = recentLeanSamples.map { it.valueDeg }.average().toFloat()
        val clamped = averaged.coerceAtLeast(-MAX_LEAN_DEG).coerceAtMost(MAX_LEAN_DEG)
        latestLeanDeg = clamped
        latestLeanTimestampNs = timestampNs
        return clamped
    }

    internal fun MainViewModel.updateLeanAngle(timestampNs: Long) {
        val upRef = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val sideAxis = upRef.cross(forward).normalized()

        val previousTimestamp = lastLeanComputationTimestampNs
        if (previousTimestamp != null) {
            val rawDeltaNs = timestampNs - previousTimestamp
            if (rawDeltaNs <= 0L) return 
            if (rawDeltaNs > SENSOR_TIMING_POLICY.leanDropDtNs) {
                lastLeanComputationTimestampNs = timestampNs
                fallbackLeanFromRecentSamples(timestampNs)
                return
            }
        }
        lastLeanComputationTimestampNs = timestampNs
        val clampedDeltaNs = if (previousTimestamp == null) {
            SENSOR_TIMING_POLICY.leanClampDtNs
        } else {
            (timestampNs - previousTimestamp).coerceIn(SENSOR_TIMING_POLICY.minDtNs, SENSOR_TIMING_POLICY.leanClampDtNs)
        }
        val leanDtSec = clampedDeltaNs / 1_000_000_000f

        val numerator = forward.dot(upRef.cross(currentUp))
        val denominator = upRef.dot(currentUp)
        val leanRad = atan2(numerator, denominator)
        val accelLeanDeg = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
        latestRotationLeanDeg = accelLeanDeg
        latestRotationTimestampNs = timestampNs

        val useFusion = gyroscopeSensor != null
        val fusedLeanDeg = if (useFusion) {
            val gravityNorm = filteredGravity.norm()
            val gravityTrust = (1f - (abs(gravityNorm - 9.81f) / 4f)).coerceIn(0f, 1f)
            val linearAccelNorm = filteredLinearAcceleration.norm()
            val sideLinearAccel = abs(filteredLinearAcceleration.dot(sideAxis))
            val lowLinearPhaseTrust = (1f - (linearAccelNorm / OBSERVABILITY_LINEAR_ACCEL_MAX)).coerceIn(0f, 1f)
            val innovationAbs = abs(accelLeanDeg - gyroLeanDeg)
            val innovationStabilityTrust =
                (1f - (innovationAbs / OBSERVABILITY_INNOVATION_MAX_DEG)).coerceIn(0f, 1f)
            val rollStabilityTrust =
                (1f - (abs(lastRollRateRadPerSec) / OBSERVABILITY_ROLL_RATE_MAX_RAD_PER_SEC)).coerceIn(0f, 1f)
            val stablePhaseTrust = (0.55f * innovationStabilityTrust + 0.45f * rollStabilityTrust).coerceIn(0f, 1f)
            val observabilityTrust = maxOf(lowLinearPhaseTrust, stablePhaseTrust)

            val lateralInnovationGate =
                (1f - (sideLinearAccel / LATERAL_ACCEL_GATING_FULL_MS2)).coerceIn(0.15f, 1f)
            val rawConfidence = (gravityTrust * observabilityTrust * lateralInnovationGate).coerceIn(0f, 1f)
            fusionConfidence = (fusionConfidence * 0.88f + rawConfidence * 0.12f).coerceIn(0f, 1f)

            val speedMs = speedKmh / 3.6f
            val centripetalLeanRad = atan2(speedMs * lastYawRateRadPerSec, 9.81f)
            val centripetalLeanDeg = Math.toDegrees(centripetalLeanRad.toDouble()).toFloat()
            
            val referenceLeanDeg = if (speedKmh > 5f && lateralInnovationGate < 0.7f) {
                centripetalLeanDeg
            } else {
                accelLeanDeg
            }

            val accelCorrectionLimitDeg = (1f + 10f * fusionConfidence) * lateralInnovationGate
            val accelInnovation = (referenceLeanDeg - gyroLeanDeg).coerceIn(-accelCorrectionLimitDeg, accelCorrectionLimitDeg)
            val accelGain = 0.003f + 0.14f * fusionConfidence * fusionConfidence

            val rotationFresh = latestRotationTimestampNs?.let { timestampNs - it <= SENSOR_TIMING_POLICY.fusionRotationFreshNs } == true
            val rotationFreshGain = if (rotationFresh) 0.03f else 0f
            val rotationInnovation = ((latestRotationLeanDeg ?: referenceLeanDeg) - gyroLeanDeg).coerceIn(-4f, 4f)

            val fused = gyroLeanDeg + (accelInnovation * accelGain) + (rotationInnovation * rotationFreshGain)
            val maxFusedStepDeg = SENSOR_TIMING_POLICY.maxLeanRateDegPerSec * leanDtSec
            val boundedFused = (fused - gyroLeanDeg).coerceIn(-maxFusedStepDeg, maxFusedStepDeg) + gyroLeanDeg
            gyroLeanDeg = boundedFused.coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
            gyroLeanDeg
        } else {
            gyroLeanDeg = accelLeanDeg
            fusionConfidence = 1f
            accelLeanDeg
        }

        val previousTimestampNs = latestLeanTimestampNs
        val dtSeconds = previousTimestampNs?.let { ((timestampNs - it) / 1_000_000_000f).coerceAtLeast(0f) } ?: 0f
        val maxStep = if (dtSeconds > 0f) MAX_OUTPUT_SLEW_RATE_DEG_PER_SEC * dtSeconds else MAX_LEAN_DEG
        val smoothedLeanDeg = (fusedLeanDeg - latestLeanDeg).coerceIn(-maxStep, maxStep) + latestLeanDeg
        val leanDeg = if (_uiState.value.settings.invertLeanAngle) -smoothedLeanDeg else smoothedLeanDeg
        latestLeanDeg = leanDeg
        latestLeanTimestampNs = timestampNs

        if (abs(leanDeg) > abs(peakLeanSinceLastTick)) {
            peakLeanSinceLastTick = leanDeg
        }

        // Auto pause if lean angle is too high (likely crash or extreme event)
        if (abs(leanDeg) >= AUTO_PAUSE_LEAN_THRESHOLD && _uiState.value.settings.autoPauseEnabled) {
            val currentState = _uiState.value.tracking
            if (currentState.trackingStarted && !currentState.isPaused) {
                togglePauseTracking()
            }
        }

        leanHistory += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)
        registerRecentLeanSample(timestampNs, leanDeg)

        val previous = _uiState.value
        pruneHistory(timestampNs, previous.settings.historyWindowSeconds)

        val visibleHistory = leanHistory.map { it.valueDeg }

        // Detect upside down: if gravity points in same direction as uprightUp (captured as -gravity)
        val upsideDown = filteredGravity.dot(upRef) > 5.0f

        // High rotation detection (not tracking, angle > threshold, not obviously flipped, persists for 10s)
        val isHighRotation = !previous.tracking.trackingStarted && abs(leanDeg) > AUTO_PAUSE_LEAN_THRESHOLD && !upsideDown
        if (isHighRotation) {
            if (highRotationStartNs == null) {
                highRotationStartNs = timestampNs
            }
        } else {
            highRotationStartNs = null
        }
        val showHighRotationWarning = highRotationStartNs?.let { (timestampNs - it) > 10_000_000_000L } ?: false

        updateTrackingState {
            it.copy(
                leanAngleDeg = leanDeg,
                maxLeftDeg = minOf(it.maxLeftDeg, if (leanDeg < 0f) leanDeg else 0f),
                maxRightDeg = maxOf(it.maxRightDeg, if (leanDeg > 0f) leanDeg else 0f),
                leanHistoryDeg = visibleHistory,
                speedKmh = speedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = ridePointCount > 0,
                currentLatitude = latestGpsLocation?.latitude,
                currentLongitude = latestGpsLocation?.longitude,
                isUpsideDown = upsideDown,
                showHighRotationWarning = showHighRotationWarning,
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
        if (_uiState.value.calibration.calibrationStep != BikeLean.DONE) {
            lastGyroTimestampNs = null
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        if (gyroscopeSensor == null) {
            lastGyroTimestampNs = event.timestamp
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        val forward = bikeForwardAxis ?: return
        val up = uprightUp ?: return
        val previousTimestamp = lastGyroTimestampNs
        if (previousTimestamp == null) {
            lastGyroTimestampNs = event.timestamp
            return
        }

        val rawDeltaNs = event.timestamp - previousTimestamp
        if (rawDeltaNs <= 0L) return 
        if (rawDeltaNs > SENSOR_TIMING_POLICY.gyroDropDtNs) {
            lastGyroTimestampNs = event.timestamp
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        lastGyroTimestampNs = event.timestamp
        val clampedDeltaNs = rawDeltaNs.coerceIn(SENSOR_TIMING_POLICY.minDtNs, SENSOR_TIMING_POLICY.gyroClampDtNs)
        val dt = clampedDeltaNs / 1_000_000_000f

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val biasVector = gyroBiasVectorRadPerSec ?: Vec3(0f, 0f, 0f)
        val unbiasedOmega = omega - biasVector
        
        val rollRateRadPerSec = unbiasedOmega.dot(forward)
            .coerceIn(-SENSOR_TIMING_POLICY.maxRollRateRadPerSec, SENSOR_TIMING_POLICY.maxRollRateRadPerSec)
        
        val yawRateRadPerSec = unbiasedOmega.dot(up)
        
        lastRollRateRadPerSec = rollRateRadPerSec
        lastYawRateRadPerSec = yawRateRadPerSec
        
        val deltaDeg = Math.toDegrees((rollRateRadPerSec * dt).toDouble()).toFloat()
        val maxStepDeg = SENSOR_TIMING_POLICY.maxLeanRateDegPerSec * dt
        val boundedDeltaDeg = deltaDeg.coerceIn(-maxStepDeg, maxStepDeg)
        gyroLeanDeg = (gyroLeanDeg + boundedDeltaDeg).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    }
