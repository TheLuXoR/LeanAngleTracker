package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
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
    }
}

data class BikeFrameCalibration(
    val bikeUpWorld: Vec3,
    val bikeForwardWorld: Vec3,
    val bikeRightWorld: Vec3,
    val ready: Boolean
)
