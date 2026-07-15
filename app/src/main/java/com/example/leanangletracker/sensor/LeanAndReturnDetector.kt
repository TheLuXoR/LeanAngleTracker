package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import kotlin.math.abs

data class LeanAndReturnProgress(
    val leanDetected: Boolean,
    val progress: Float,
    val wrongDirection: Boolean,
    val currentAngleDeg: Float,
    val maxAngleDeg: Float,
    val completedWorldUpDevice: Vec3? = null
)

internal object CalibrationTiltProgress {
    const val MINIMUM_TILT_ANGLE_DEG = 4f
    const val MINIMUM_TILT_PROGRESS = 0.25f
    private const val FULL_SCALE_ANGLE_DEG = MINIMUM_TILT_ANGLE_DEG / MINIMUM_TILT_PROGRESS

    fun fromAngle(angleDeg: Float): Float =
        (angleDeg / FULL_SCALE_ANGLE_DEG).coerceIn(0f, 1f)
}

class LeanAndReturnDetector(
    private val detectionAngleDeg: Float = CalibrationTiltProgress.MINIMUM_TILT_ANGLE_DEG,
    private val returnedUprightAngleDeg: Float = 3.5f,
    private val returnedStableDurationNs: Long = 600_000_000L,
    private val maxAccelerationErrorMs2: Float = 0.45f,
    private val maxReturnAngularSpeedRadPerSec: Float = 0.10f
) {
    private var leanDetected = false
    private var maxAngleDeg = 0f
    private var peakUpSum = Vec3(0f, 0f, 0f)
    private var peakSampleCount = 0
    private var returnedStableStartNs: Long? = null

    fun reset() {
        leanDetected = false
        maxAngleDeg = 0f
        peakUpSum = Vec3(0f, 0f, 0f)
        peakSampleCount = 0
        returnedStableStartNs = null
    }

    fun update(
        timestampNs: Long,
        accelerationMs2: Vec3,
        gyroRadPerSec: Vec3,
        uprightWorldUpDevice: Vec3,
        oppositeOfDirection: Vec3? = null
    ): LeanAndReturnProgress {
        val currentUp = accelerationMs2.normalized()
        val upright = uprightWorldUpDevice.normalized()
        val angleDeg = BikeFrameMath.angleBetweenDeg(upright, currentUp)
        val offset = BikeFrameMath.projectedOffset(currentUp, upright)
        val directionValid = oppositeOfDirection == null ||
            (offset.norm() > 1e-4f && offset.normalized().dot(oppositeOfDirection) <= -0.25f)

        if (!leanDetected && angleDeg >= detectionAngleDeg && !directionValid) {
            return LeanAndReturnProgress(
                leanDetected = false,
                progress = 0f,
                wrongDirection = true,
                currentAngleDeg = angleDeg,
                maxAngleDeg = maxAngleDeg
            )
        }

        if (!leanDetected && directionValid) {
            maxAngleDeg = maxOf(maxAngleDeg, angleDeg)
        }

        if (!leanDetected && angleDeg >= detectionAngleDeg && directionValid) {
            leanDetected = true
            maxAngleDeg = angleDeg
            peakUpSum = currentUp
            peakSampleCount = 1
        } else if (leanDetected && angleDeg > returnedUprightAngleDeg) {
            if (angleDeg > maxAngleDeg + PEAK_RESET_MARGIN_DEG) {
                maxAngleDeg = angleDeg
                peakUpSum = currentUp
                peakSampleCount = 1
            } else if (angleDeg >= maxAngleDeg - PEAK_AVERAGE_BAND_DEG) {
                maxAngleDeg = maxOf(maxAngleDeg, angleDeg)
                peakUpSum += currentUp
                peakSampleCount += 1
            }
        }

        if (!leanDetected) {
            return LeanAndReturnProgress(
                leanDetected = false,
                progress = 0f,
                wrongDirection = false,
                currentAngleDeg = angleDeg,
                maxAngleDeg = maxAngleDeg
            )
        }

        val returnedStable = angleDeg <= returnedUprightAngleDeg &&
            abs(accelerationMs2.norm() - GRAVITY_EARTH) <= maxAccelerationErrorMs2 &&
            gyroRadPerSec.norm() <= maxReturnAngularSpeedRadPerSec
        if (!returnedStable) {
            returnedStableStartNs = null
            return LeanAndReturnProgress(
                leanDetected = true,
                progress = LEAN_DETECTED_PROGRESS,
                wrongDirection = false,
                currentAngleDeg = angleDeg,
                maxAngleDeg = maxAngleDeg
            )
        }

        val start = returnedStableStartNs ?: timestampNs.also { returnedStableStartNs = it }
        val returnProgress = if (returnedStableDurationNs <= 0L) {
            1f
        } else {
            ((timestampNs - start).coerceAtLeast(0L).toFloat() / returnedStableDurationNs)
                .coerceIn(0f, 1f)
        }
        val progress = LEAN_DETECTED_PROGRESS + returnProgress * (1f - LEAN_DETECTED_PROGRESS)
        val completed = if (returnProgress >= 1f && peakSampleCount > 0) {
            peakUpSum.normalized()
        } else {
            null
        }
        return LeanAndReturnProgress(
            leanDetected = true,
            progress = progress,
            wrongDirection = false,
            currentAngleDeg = angleDeg,
            maxAngleDeg = maxAngleDeg,
            completedWorldUpDevice = completed
        )
    }

    private companion object {
        const val GRAVITY_EARTH = 9.80665f
        const val LEAN_DETECTED_PROGRESS = 0.5f
        const val PEAK_RESET_MARGIN_DEG = 0.5f
        const val PEAK_AVERAGE_BAND_DEG = 0.75f
    }
}
