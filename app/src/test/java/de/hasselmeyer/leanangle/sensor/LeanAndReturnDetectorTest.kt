package de.hasselmeyer.leanangle.sensor

import de.hasselmeyer.leanangle.data.Vec3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class LeanAndReturnDetectorTest {
    private val up = Vec3(0f, 0f, 1f)
    private val right = Vec3(1f, 0f, 0f)
    private val noRotation = Vec3(0f, 0f, 0f)

    @Test
    fun `comfortable lean is captured automatically after stable upright return`() {
        val detector = LeanAndReturnDetector()

        val beforeDetection = update(detector, 0L, 3f)
        assertFalse(beforeDetection.leanDetected)
        assertEquals(3f, beforeDetection.currentAngleDeg, 0.01f)
        assertEquals(3f, beforeDetection.maxAngleDeg, 0.01f)
        assertTrue(update(detector, 100_000_000L, 5f).leanDetected)
        update(detector, 200_000_000L, 11f)
        update(detector, 300_000_000L, 10.7f)
        val returned = update(detector, 500_000_000L, 2f)
        val completed = update(detector, 1_100_000_000L, 1f)

        assertEquals(0.5f, returned.progress, 0f)
        assertEquals(1f, completed.progress, 0f)
        assertEquals(1f, completed.currentAngleDeg, 0.01f)
        assertEquals(11f, completed.maxAngleDeg, 0.01f)
        val captured = completed.completedWorldUpDevice
        assertNotNull(captured)
        assertEquals(11f, BikeFrameMath.angleBetweenDeg(up, requireNotNull(captured)), 0.5f)
    }

    @Test
    fun `right step rejects another lean in the left direction`() {
        val detector = LeanAndReturnDetector()

        val wrong = update(detector, 0L, 8f, oppositeOfDirection = right)
        val resetAtUpright = update(detector, 100_000_000L, 0f, oppositeOfDirection = right)
        val correct = update(detector, 200_000_000L, -8f, oppositeOfDirection = right)

        assertTrue(wrong.wrongDirection)
        assertFalse(wrong.leanDetected)
        assertFalse(resetAtUpright.wrongDirection)
        assertTrue(correct.leanDetected)
    }

    @Test
    fun `movement after return restarts upright stability timer`() {
        val detector = LeanAndReturnDetector()
        update(detector, 0L, 7f)
        update(detector, 100_000_000L, 1f)
        val moving = detector.update(
            timestampNs = 500_000_000L,
            accelerationMs2 = gravityAt(1f),
            gyroRadPerSec = Vec3(0.2f, 0f, 0f),
            uprightWorldUpDevice = up
        )
        val restarted = update(detector, 700_000_000L, 1f)

        assertNull(moving.completedWorldUpDevice)
        assertEquals(0.5f, restarted.progress, 0f)
    }

    @Test
    fun `return deadzone accepts a stable pose within three point five degrees`() {
        val detector = LeanAndReturnDetector()

        update(detector, 0L, 7f)
        val outsideDeadzone = update(detector, 100_000_000L, 3.8f)
        val returnStarted = update(detector, 700_000_000L, 3.4f)
        val completed = update(detector, 1_300_000_000L, 3.4f)

        assertNull(outsideDeadzone.completedWorldUpDevice)
        assertNull(returnStarted.completedWorldUpDevice)
        assertNotNull(completed.completedWorldUpDevice)
    }

    @Test
    fun `tilt progress maps eight degrees to half of the bar`() {
        assertEquals(0f, CalibrationTiltProgress.fromAngle(0f), 0f)
        assertEquals(0.25f, CalibrationTiltProgress.fromAngle(4f), 0f)
        assertEquals(0.5f, CalibrationTiltProgress.fromAngle(8f), 0f)
        assertEquals(1f, CalibrationTiltProgress.fromAngle(16f), 0f)
        assertEquals(1f, CalibrationTiltProgress.fromAngle(30f), 0f)
    }

    private fun update(
        detector: LeanAndReturnDetector,
        timestampNs: Long,
        angleDeg: Float,
        oppositeOfDirection: Vec3? = null
    ): LeanAndReturnProgress = detector.update(
        timestampNs = timestampNs,
        accelerationMs2 = gravityAt(angleDeg),
        gyroRadPerSec = noRotation,
        uprightWorldUpDevice = up,
        oppositeOfDirection = oppositeOfDirection
    )

    private fun gravityAt(angleDeg: Float): Vec3 {
        val radians = Math.toRadians(angleDeg.toDouble()).toFloat()
        return (up * cos(radians) + right * sin(radians)) * 9.80665f
    }
}
