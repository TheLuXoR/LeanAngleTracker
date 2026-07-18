package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt

data class Quaternion(val w: Float, val x: Float, val y: Float, val z: Float) {
    fun normalized(): Quaternion {
        val n = sqrt(w * w + x * x + y * y + z * z)
        if (n < 1e-9f) return IDENTITY
        val inv = 1f / n
        return Quaternion(w * inv, x * inv, y * inv, z * inv)
    }

    fun conjugate(): Quaternion = Quaternion(w, -x, -y, -z)

    operator fun times(other: Quaternion): Quaternion = Quaternion(
        w * other.w - x * other.x - y * other.y - z * other.z,
        w * other.x + x * other.w + y * other.z - z * other.y,
        w * other.y - x * other.z + y * other.w + z * other.x,
        w * other.z + x * other.y - y * other.x + z * other.w
    )

    fun rotate(v: Vec3): Vec3 {
        val qv = Quaternion(0f, v.x, v.y, v.z)
        val r = this * qv * conjugate()
        return Vec3(r.x, r.y, r.z)
    }

    companion object {
        val IDENTITY = Quaternion(1f, 0f, 0f, 0f)

        fun fromTo(from: Vec3, to: Vec3): Quaternion {
            val source = from.normalized()
            val target = to.normalized()
            if (source.norm() < 0.5f || target.norm() < 0.5f) return IDENTITY

            val dot = source.dot(target).coerceIn(-1f, 1f)
            if (dot > 0.999_999f) return IDENTITY
            if (dot < -0.999_999f) {
                val helper = if (kotlin.math.abs(source.x) < 0.8f) {
                    Vec3(1f, 0f, 0f)
                } else {
                    Vec3(0f, 1f, 0f)
                }
                val axis = source.cross(helper).normalized()
                return Quaternion(0f, axis.x, axis.y, axis.z)
            }

            val axis = source.cross(target)
            return Quaternion(1f + dot, axis.x, axis.y, axis.z).normalized()
        }
    }
}

data class BikeFrameCalibration(
    val bikeUpDevice: Vec3,
    val bikeForwardDevice: Vec3,
    val bikeRightDevice: Vec3,
    val ready: Boolean
)

enum class BikeFrameFailure {
    INSUFFICIENT_LEFT_TILT,
    INSUFFICIENT_RIGHT_TILT,
    SAME_DIRECTION,
    DEGENERATE_FRAME
}

data class BikeFrameBuildResult(
    val calibration: BikeFrameCalibration? = null,
    val failure: BikeFrameFailure? = null
)

data class BikePoseEvaluation(
    val signedLeanAngleDeg: Float,
    val uprightAlignment: Float,
    val isUpsideDown: Boolean
)

object BikeFrameMath {
    const val MIN_TILT_DEG = 3f
    private const val UPSIDE_DOWN_ALIGNMENT_THRESHOLD = -0.5f

    fun fromStaticSamples(
        upright: Vec3,
        left: Vec3,
        right: Vec3,
        minTiltDeg: Float = MIN_TILT_DEG
    ): BikeFrameBuildResult {
        val up = upright.normalized()
        val leftUp = left.normalized()
        val rightUp = right.normalized()
        if (!up.isUsableUnitVector() || !leftUp.isUsableUnitVector() || !rightUp.isUsableUnitVector()) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.DEGENERATE_FRAME)
        }

        val leftTilt = angleBetweenDeg(up, leftUp)
        if (leftTilt < minTiltDeg) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.INSUFFICIENT_LEFT_TILT)
        }
        val rightTilt = angleBetweenDeg(up, rightUp)
        if (rightTilt < minTiltDeg) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.INSUFFICIENT_RIGHT_TILT)
        }

        val leftOffset = rejectFrom(leftUp, up)
        val rightOffset = rejectFrom(rightUp, up)
        if (leftOffset.norm() < 1e-4f || rightOffset.norm() < 1e-4f) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.DEGENERATE_FRAME)
        }
        if (leftOffset.normalized().dot(rightOffset.normalized()) > -0.25f) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.SAME_DIRECTION)
        }

        // World-up moves toward the motorcycle's right side during a left lean.
        val bikeRight = rejectFrom(leftOffset - rightOffset, up).normalized()
        val bikeForward = up.cross(bikeRight).normalized()
        if (!bikeRight.isUsableUnitVector() || !bikeForward.isUsableUnitVector()) {
            return BikeFrameBuildResult(failure = BikeFrameFailure.DEGENERATE_FRAME)
        }

        return BikeFrameBuildResult(
            calibration = BikeFrameCalibration(
                bikeUpDevice = up,
                bikeForwardDevice = bikeForward,
                bikeRightDevice = bikeRight,
                ready = true
            )
        )
    }

    fun fromUpAndForward(upInput: Vec3, forwardInput: Vec3): BikeFrameCalibration? {
        val up = upInput.normalized()
        val forward = rejectFrom(forwardInput, up).normalized()
        val right = forward.cross(up).normalized()
        if (!up.isUsableUnitVector() || !forward.isUsableUnitVector() || !right.isUsableUnitVector()) {
            return null
        }
        return BikeFrameCalibration(up, forward, right, true)
    }

    fun leanAngleDeg(worldUpDevice: Vec3, frame: BikeFrameCalibration): Float {
        return evaluatePose(worldUpDevice, frame).signedLeanAngleDeg
    }

    fun evaluatePose(worldUpDevice: Vec3, frame: BikeFrameCalibration): BikePoseEvaluation {
        val currentUp = worldUpDevice.normalized()
        val upProjection = currentUp.dot(frame.bikeUpDevice).coerceIn(-1f, 1f)
        val rightProjection = currentUp.dot(frame.bikeRightDevice)
        val signedLeanAngleDeg = Math.toDegrees(
            atan2(-rightProjection, upProjection).toDouble()
        ).toFloat()
        return BikePoseEvaluation(
            signedLeanAngleDeg = signedLeanAngleDeg,
            uprightAlignment = upProjection,
            isUpsideDown = upProjection <= UPSIDE_DOWN_ALIGNMENT_THRESHOLD
        )
    }

    fun angleBetweenDeg(a: Vec3, b: Vec3): Float {
        val normalizedA = a.normalized()
        val normalizedB = b.normalized()
        return Math.toDegrees(
            acos(normalizedA.dot(normalizedB).toDouble().coerceIn(-1.0, 1.0))
        ).toFloat()
    }

    fun projectedOffset(sample: Vec3, up: Vec3): Vec3 = rejectFrom(sample.normalized(), up.normalized())

    private fun rejectFrom(vector: Vec3, axis: Vec3): Vec3 = vector - axis * vector.dot(axis)

    private fun Vec3.isUsableUnitVector(): Boolean =
        x.isFinite() && y.isFinite() && z.isFinite() && norm() > 0.9f
}
