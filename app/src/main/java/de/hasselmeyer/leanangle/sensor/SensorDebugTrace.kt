package de.hasselmeyer.leanangle.sensor

import de.hasselmeyer.leanangle.data.Vec3

data class SensorTraceSample(
    val timestampNs: Long,
    val accelerationMs2: Vec3,
    val gyroRadPerSec: Vec3,
    val orientation: Quaternion,
    val accelerationWeight: Float,
    val leanAngleDeg: Float
)

class SensorDebugTrace(private val capacity: Int = 2_048) {
    private val samples = ArrayDeque<SensorTraceSample>()

    fun add(sample: SensorTraceSample) {
        if (capacity <= 0) return
        samples += sample
        while (samples.size > capacity) samples.removeFirst()
    }

    fun snapshot(): List<SensorTraceSample> = samples.toList()

    fun toCsv(): String = buildString {
        appendLine("timestamp_ns,ax,ay,az,gx,gy,gz,qw,qx,qy,qz,accel_weight,lean_deg")
        samples.forEach { sample ->
            append(sample.timestampNs).append(',')
            append(sample.accelerationMs2.x).append(',')
            append(sample.accelerationMs2.y).append(',')
            append(sample.accelerationMs2.z).append(',')
            append(sample.gyroRadPerSec.x).append(',')
            append(sample.gyroRadPerSec.y).append(',')
            append(sample.gyroRadPerSec.z).append(',')
            append(sample.orientation.w).append(',')
            append(sample.orientation.x).append(',')
            append(sample.orientation.y).append(',')
            append(sample.orientation.z).append(',')
            append(sample.accelerationWeight).append(',')
            appendLine(sample.leanAngleDeg)
        }
    }

    fun clear() = samples.clear()
}
