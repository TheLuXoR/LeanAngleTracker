package de.hasselmeyer.leanangle.sensor

import kotlin.math.abs

object FusionGating {
    private const val FULL_CORRECTION_YAW_RATE_RAD_SEC = 0.04f
    private const val NO_CORRECTION_YAW_RATE_RAD_SEC = 0.12f

    fun accelerationWeight(baseWeight: Float, yawRateRadPerSec: Float): Float {
        val yawRate = abs(yawRateRadPerSec)
        val turnWeight = when {
            yawRate <= FULL_CORRECTION_YAW_RATE_RAD_SEC -> 1f
            yawRate >= NO_CORRECTION_YAW_RATE_RAD_SEC -> 0f
            else -> 1f -
                (yawRate - FULL_CORRECTION_YAW_RATE_RAD_SEC) /
                (NO_CORRECTION_YAW_RATE_RAD_SEC - FULL_CORRECTION_YAW_RATE_RAD_SEC)
        }
        return baseWeight.coerceIn(0f, 1f) * turnWeight
    }
}
