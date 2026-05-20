package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

class MadgwickFilter(
    private val betaBase: Float = 0.055f
) {
    var orientation: Quaternion = Quaternion.IDENTITY
        private set

    fun reset() {
        orientation = Quaternion.IDENTITY
    }

    fun update(gyroRadSec: Vec3, accelMs2: Vec3, dtSec: Float, accelWeight: Float): Quaternion {
        if (dtSec <= 0f) return orientation
        var q1 = orientation.w
        var q2 = orientation.x
        var q3 = orientation.y
        var q4 = orientation.z

        val gx = gyroRadSec.x
        val gy = gyroRadSec.y
        val gz = gyroRadSec.z

        var qDot1 = 0.5f * (-q2 * gx - q3 * gy - q4 * gz)
        var qDot2 = 0.5f * (q1 * gx + q3 * gz - q4 * gy)
        var qDot3 = 0.5f * (q1 * gy - q2 * gz + q4 * gx)
        var qDot4 = 0.5f * (q1 * gz + q2 * gy - q3 * gx)

        val aNorm = accelMs2.norm()
        if (aNorm > 1e-6f && accelWeight > 0f) {
            val invA = 1f / aNorm
            val ax = accelMs2.x * invA
            val ay = accelMs2.y * invA
            val az = accelMs2.z * invA

            val f1 = 2f * (q2 * q4 - q1 * q3) - ax
            val f2 = 2f * (q1 * q2 + q3 * q4) - ay
            val f3 = 1f - 2f * (q2 * q2 + q3 * q3) - az

            var s1 = -2f * q3 * f1 + 2f * q2 * f2
            var s2 = 2f * q4 * f1 + 2f * q1 * f2 - 4f * q2 * f3
            var s3 = -2f * q1 * f1 + 2f * q4 * f2 - 4f * q3 * f3
            var s4 = 2f * q2 * f1 + 2f * q3 * f2

            val sNorm = sqrt(max(1e-12f, s1 * s1 + s2 * s2 + s3 * s3 + s4 * s4))
            val invS = 1f / sNorm
            s1 *= invS
            s2 *= invS
            s3 *= invS
            s4 *= invS

            val beta = betaBase * accelWeight
            qDot1 -= beta * s1
            qDot2 -= beta * s2
            qDot3 -= beta * s3
            qDot4 -= beta * s4
        }

        q1 += qDot1 * dtSec
        q2 += qDot2 * dtSec
        q3 += qDot3 * dtSec
        q4 += qDot4 * dtSec

        val qNorm = sqrt(max(1e-12f, q1 * q1 + q2 * q2 + q3 * q3 + q4 * q4))
        val invQ = 1f / qNorm
        orientation = Quaternion(q1 * invQ, q2 * invQ, q3 * invQ, q4 * invQ)
        return orientation
    }

    fun adaptiveAccelWeight(accelMs2: Vec3): Float {
        val g = 9.80665f
        val errorG = abs(accelMs2.norm() - g) / g
        return when {
            errorG < 0.05f -> 1f
            errorG < 0.15f -> 1f - (errorG - 0.05f) / 0.10f * 0.85f
            else -> 0.05f
        }
    }
}
