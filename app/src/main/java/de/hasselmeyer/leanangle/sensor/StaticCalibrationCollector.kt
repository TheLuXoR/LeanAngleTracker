package de.hasselmeyer.leanangle.sensor

import de.hasselmeyer.leanangle.data.Vec3
import kotlin.math.abs

data class StableCalibrationSample(
    val worldUpDevice: Vec3,
    val gyroBiasRadPerSec: Vec3,
    val sampleCount: Int
)

data class CalibrationSampleProgress(
    val progress: Float,
    val readySample: StableCalibrationSample?
)

class StaticCalibrationCollector(
    private val maxAccelerationErrorMs2: Float = 0.45f,
    private val maxAngularSpeedRadPerSec: Float = 0.10f
) {
    private var requiredDurationNs = 0L
    private var stableStartNs: Long? = null
    private var upSum = Vec3(0f, 0f, 0f)
    private var gyroSum = Vec3(0f, 0f, 0f)
    private var sampleCount = 0

    fun reset() {
        stableStartNs = null
        upSum = Vec3(0f, 0f, 0f)
        gyroSum = Vec3(0f, 0f, 0f)
        sampleCount = 0
    }

    fun update(
        timestampNs: Long,
        accelerationMs2: Vec3,
        gyroRadPerSec: Vec3,
        requiredDurationNs: Long,
        poseIsValid: Boolean
    ): CalibrationSampleProgress {
        if (this.requiredDurationNs != requiredDurationNs) {
            this.requiredDurationNs = requiredDurationNs
            reset()
        }

        val stable = poseIsValid &&
            abs(accelerationMs2.norm() - GRAVITY_EARTH) <= maxAccelerationErrorMs2 &&
            gyroRadPerSec.norm() <= maxAngularSpeedRadPerSec
        if (!stable) {
            reset()
            return CalibrationSampleProgress(0f, null)
        }

        val start = stableStartNs ?: timestampNs.also { stableStartNs = it }
        upSum += accelerationMs2.normalized()
        gyroSum += gyroRadPerSec
        sampleCount += 1

        val progress = if (requiredDurationNs <= 0L) {
            1f
        } else {
            ((timestampNs - start).coerceAtLeast(0L).toFloat() / requiredDurationNs).coerceIn(0f, 1f)
        }
        val sample = if (progress >= 1f && sampleCount > 0) {
            StableCalibrationSample(
                worldUpDevice = upSum.normalized(),
                gyroBiasRadPerSec = gyroSum * (1f / sampleCount),
                sampleCount = sampleCount
            )
        } else {
            null
        }
        return CalibrationSampleProgress(progress, sample)
    }

    private companion object {
        const val GRAVITY_EARTH = 9.80665f
    }
}
