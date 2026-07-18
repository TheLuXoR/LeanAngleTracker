package com.example.leanangletracker.sensor

import com.example.leanangletracker.data.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class BikeFrameMathTest {
    @Test
    fun `static samples build canonical device frame and signed lean`() {
        val up = Vec3(0f, 0f, 1f)
        val left = tiltedUp(up, Vec3(1f, 0f, 0f), 30f)
        val right = tiltedUp(up, Vec3(1f, 0f, 0f), -30f)

        val result = BikeFrameMath.fromStaticSamples(up, left, right)
        val frame = requireNotNull(result.calibration)

        assertVectorEquals(Vec3(0f, 0f, 1f), frame.bikeUpDevice)
        assertVectorEquals(Vec3(1f, 0f, 0f), frame.bikeRightDevice)
        assertVectorEquals(Vec3(0f, 1f, 0f), frame.bikeForwardDevice)
        assertEquals(0f, BikeFrameMath.leanAngleDeg(up, frame), 0.001f)
        assertEquals(-30f, BikeFrameMath.leanAngleDeg(left, frame), 0.01f)
        assertEquals(30f, BikeFrameMath.leanAngleDeg(right, frame), 0.01f)
    }

    @Test
    fun `lean calculation works for arbitrary phone mounting and requested angles`() {
        val up = Vec3(0f, 1f, 0f)
        val bikeRight = Vec3(1f, 0f, 0f)
        val calibration = requireNotNull(
            BikeFrameMath.fromStaticSamples(
                up,
                tiltedUp(up, bikeRight, 25f),
                tiltedUp(up, bikeRight, -25f)
            ).calibration
        )

        listOf(10f, 30f, 60f).forEach { angle ->
            assertEquals(-angle, BikeFrameMath.leanAngleDeg(tiltedUp(up, bikeRight, angle), calibration), 0.02f)
            assertEquals(angle, BikeFrameMath.leanAngleDeg(tiltedUp(up, bikeRight, -angle), calibration), 0.02f)
        }
    }

    @Test
    fun `upside down pose reports full rotation instead of false center`() {
        val up = Vec3(0f, 0f, 1f)
        val frame = requireNotNull(
            BikeFrameMath.fromUpAndForward(up, Vec3(0f, 1f, 0f))
        )

        val pose = BikeFrameMath.evaluatePose(up * -1f, frame)

        assertTrue(pose.isUpsideDown)
        assertEquals(-1f, pose.uprightAlignment, 0.001f)
        assertEquals(180f, abs(pose.signedLeanAngleDeg), 0.001f)
    }

    @Test
    fun `large valid lean and upside down mounting are distinguished`() {
        val up = Vec3(0f, 0f, 1f)
        val right = Vec3(1f, 0f, 0f)
        val frame = requireNotNull(
            BikeFrameMath.fromUpAndForward(up, Vec3(0f, 1f, 0f))
        )

        val validLean = BikeFrameMath.evaluatePose(tiltedUp(up, right, 60f), frame)
        val upsideDown = BikeFrameMath.evaluatePose(tiltedUp(up, right, 140f), frame)

        assertTrue(!validLean.isUpsideDown)
        assertTrue(upsideDown.isUpsideDown)
        assertEquals(-60f, validLean.signedLeanAngleDeg, 0.02f)
        assertEquals(-140f, upsideDown.signedLeanAngleDeg, 0.02f)
    }

    @Test
    fun `insufficient tilt is rejected`() {
        val up = Vec3(0f, 0f, 1f)
        val result = BikeFrameMath.fromStaticSamples(
            up,
            tiltedUp(up, Vec3(1f, 0f, 0f), 2f),
            tiltedUp(up, Vec3(1f, 0f, 0f), -20f)
        )

        assertNull(result.calibration)
        assertEquals(BikeFrameFailure.INSUFFICIENT_LEFT_TILT, result.failure)
    }

    @Test
    fun `same side samples are rejected`() {
        val up = Vec3(0f, 0f, 1f)
        val result = BikeFrameMath.fromStaticSamples(
            up,
            tiltedUp(up, Vec3(1f, 0f, 0f), 20f),
            tiltedUp(up, Vec3(1f, 0f, 0f), 25f)
        )

        assertNull(result.calibration)
        assertEquals(BikeFrameFailure.SAME_DIRECTION, result.failure)
    }

    @Test
    fun `degenerate persisted axes are rejected and valid axes restore`() {
        assertNull(BikeFrameMath.fromUpAndForward(Vec3(0f, 0f, 1f), Vec3(0f, 0f, 2f)))

        val restored = BikeFrameMath.fromUpAndForward(Vec3(0f, 0f, 2f), Vec3(0f, 4f, 0f))
        assertNotNull(restored)
        assertTrue(requireNotNull(restored).ready)
        assertVectorEquals(Vec3(1f, 0f, 0f), restored.bikeRightDevice)
    }

    private fun tiltedUp(up: Vec3, right: Vec3, angleDeg: Float): Vec3 {
        val radians = Math.toRadians(angleDeg.toDouble()).toFloat()
        return up * cos(radians) + right * sin(radians)
    }

    private fun assertVectorEquals(expected: Vec3, actual: Vec3, tolerance: Float = 0.001f) {
        assertEquals(expected.x, actual.x, tolerance)
        assertEquals(expected.y, actual.y, tolerance)
        assertEquals(expected.z, actual.z, tolerance)
    }
}
