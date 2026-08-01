package de.hasselmeyer.leanangle.sensor

import de.hasselmeyer.leanangle.data.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StaticCalibrationCollectorTest {
    private val gravity = Vec3(0f, 0f, 9.80665f)

    @Test
    fun `stable window averages world up and gyro bias`() {
        val collector = StaticCalibrationCollector()
        val gyro = Vec3(0.01f, -0.02f, 0.005f)

        collector.update(0L, gravity, gyro, 1_500_000_000L, true)
        collector.update(750_000_000L, gravity, gyro, 1_500_000_000L, true)
        val completed = collector.update(1_500_000_000L, gravity, gyro, 1_500_000_000L, true)

        assertEquals(1f, completed.progress, 0f)
        val sample = completed.readySample
        assertNotNull(sample)
        assertEquals(1f, requireNotNull(sample).worldUpDevice.z, 0.0001f)
        assertEquals(gyro.x, sample.gyroBiasRadPerSec.x, 0.0001f)
        assertEquals(gyro.y, sample.gyroBiasRadPerSec.y, 0.0001f)
        assertEquals(3, sample.sampleCount)
    }

    @Test
    fun `motion resets stable window`() {
        val collector = StaticCalibrationCollector()
        collector.update(0L, gravity, Vec3(0f, 0f, 0f), 1_000_000_000L, true)
        val moving = collector.update(
            800_000_000L,
            gravity,
            Vec3(0.2f, 0f, 0f),
            1_000_000_000L,
            true
        )
        val restarted = collector.update(
            1_000_000_000L,
            gravity,
            Vec3(0f, 0f, 0f),
            1_000_000_000L,
            true
        )

        assertEquals(0f, moving.progress, 0f)
        assertEquals(0f, restarted.progress, 0f)
        assertNull(restarted.readySample)
    }

    @Test
    fun `invalid pose cannot complete sample`() {
        val collector = StaticCalibrationCollector()
        val result = collector.update(
            2_000_000_000L,
            gravity,
            Vec3(0f, 0f, 0f),
            500_000_000L,
            false
        )

        assertEquals(0f, result.progress, 0f)
        assertNull(result.readySample)
    }
}
