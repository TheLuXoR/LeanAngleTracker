package com.example.leanangletracker

import org.junit.Assert.assertEquals
import org.junit.Test

class LeanExtremaTest {
    @Test
    fun `include keeps the strongest measured lean for each side`() {
        val extrema = listOf(-12f, 8f, -27.5f, 21f, -18f)
            .fold(LeanExtrema.ZERO) { current, angle -> current.include(angle) }

        assertEquals(-27.5f, extrema.maxLeftDeg, 0.001f)
        assertEquals(21f, extrema.maxRightDeg, 0.001f)
    }

    @Test
    fun `inverted swaps directions while keeping magnitudes`() {
        val inverted = LeanExtrema(maxLeftDeg = -31f, maxRightDeg = 24f).inverted()

        assertEquals(-24f, inverted.maxLeftDeg, 0.001f)
        assertEquals(31f, inverted.maxRightDeg, 0.001f)
    }

    @Test
    fun `resetting gauge extrema does not change ride extrema`() {
        val gaugeExtrema = LeanExtrema.fromAngles(listOf(-40f, 35f))
        val rideExtrema = LeanExtrema.fromAngles(listOf(-22f, 19f))

        val resetGaugeExtrema = LeanExtrema.ZERO

        assertEquals(0f, resetGaugeExtrema.maxLeftDeg, 0.001f)
        assertEquals(0f, resetGaugeExtrema.maxRightDeg, 0.001f)
        assertEquals(-22f, rideExtrema.maxLeftDeg, 0.001f)
        assertEquals(19f, rideExtrema.maxRightDeg, 0.001f)
        assertEquals(-40f, gaugeExtrema.maxLeftDeg, 0.001f)
    }

    @Test
    fun `non finite sensor values are ignored`() {
        val original = LeanExtrema(maxLeftDeg = -10f, maxRightDeg = 12f)

        assertEquals(original, original.include(Float.NaN))
        assertEquals(original, original.include(Float.POSITIVE_INFINITY))
    }
}
