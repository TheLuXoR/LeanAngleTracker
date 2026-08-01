package de.hasselmeyer.leanangle.sensor

import de.hasselmeyer.leanangle.data.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuaternionMadgwickTest {
    @Test
    fun `gravity initialization aligns arbitrary device up without changing its inverse`() {
        val deviceUp = Vec3(0.4f, -0.3f, 0.8660254f).normalized()
        val filter = MadgwickFilter()

        val orientation = filter.initializeFromGravity(deviceUp)

        assertVectorEquals(Vec3(0f, 0f, 1f), orientation.rotate(deviceUp), 0.0001f)
        assertVectorEquals(deviceUp, orientation.conjugate().rotate(Vec3(0f, 0f, 1f)), 0.0001f)
    }

    @Test
    fun `reset restores identity after gyro integration`() {
        val filter = MadgwickFilter()
        repeat(100) {
            filter.update(Vec3(1f, 0f, 0f), Vec3(0f, 0f, 9.80665f), 0.01f, 0f)
        }

        filter.reset()

        assertEquals(Quaternion.IDENTITY, filter.orientation)
    }

    @Test
    fun `gyro integration produces signed lean in calibrated frame`() {
        val frame = BikeFrameCalibration(
            bikeUpDevice = Vec3(0f, 0f, 1f),
            bikeForwardDevice = Vec3(0f, 1f, 0f),
            bikeRightDevice = Vec3(1f, 0f, 0f),
            ready = true
        )
        val filter = MadgwickFilter()
        filter.initializeFromGravity(Vec3(0f, 0f, 9.80665f))
        repeat(100) {
            filter.update(Vec3(0f, 1f, 0f), Vec3(0f, 0f, 9.80665f), 0.01f, 0f)
        }

        val worldUpDevice = filter.orientation.conjugate().rotate(Vec3(0f, 0f, 1f))

        assertEquals(57.3f, BikeFrameMath.leanAngleDeg(worldUpDevice, frame), 0.1f)
    }

    @Test
    fun `large acceleration error disables correction`() {
        val filter = MadgwickFilter()
        assertEquals(1f, filter.adaptiveAccelWeight(Vec3(0f, 0f, 9.80665f)), 0f)
        assertEquals(0f, filter.adaptiveAccelWeight(Vec3(0f, 0f, 12f)), 0f)
    }

    @Test
    fun `turn gating disables accelerometer correction during yaw`() {
        assertEquals(1f, FusionGating.accelerationWeight(1f, 0.02f), 0f)
        assertEquals(0.5f, FusionGating.accelerationWeight(1f, 0.08f), 0.0001f)
        assertEquals(0f, FusionGating.accelerationWeight(1f, 0.12f), 0f)
        assertEquals(0f, FusionGating.accelerationWeight(1f, -0.5f), 0f)
    }

    @Test
    fun `invalid and long sensor deltas are rejected`() {
        assertNull(fusionDeltaSeconds(10L, 10L))
        assertNull(fusionDeltaSeconds(10L, 100_000_011L))
        assertEquals(0.01f, fusionDeltaSeconds(0L, 10_000_000L)!!, 0.00001f)
    }

    private fun assertVectorEquals(expected: Vec3, actual: Vec3, tolerance: Float) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.z, actual.z, tolerance)
    }
}
