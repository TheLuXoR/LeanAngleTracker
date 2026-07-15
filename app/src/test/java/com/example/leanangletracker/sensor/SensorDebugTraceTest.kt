package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import org.junit.Assert.assertEquals
import org.junit.Test

class SensorDebugTraceTest {
    @Test
    fun `trace keeps only newest samples`() {
        val trace = SensorDebugTrace(capacity = 2)
        repeat(3) { index ->
            trace.add(
                SensorTraceSample(
                    timestampNs = index.toLong(),
                    accelerationMs2 = Vec3(0f, 0f, 9.8f),
                    gyroRadPerSec = Vec3(0f, 0f, 0f),
                    orientation = Quaternion.IDENTITY,
                    accelerationWeight = 1f,
                    leanAngleDeg = index.toFloat()
                )
            )
        }

        assertEquals(listOf(1L, 2L), trace.snapshot().map { it.timestampNs })
        val csv = trace.toCsv()
        assertEquals(3, csv.lineSequence().filter { it.isNotBlank() }.count())
        assertEquals(
            "timestamp_ns,ax,ay,az,gx,gy,gz,qw,qx,qy,qz,accel_weight,lean_deg",
            csv.lineSequence().first()
        )
    }
}
