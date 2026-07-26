package com.example.leanangletracker.ui.tracking

import com.example.leanangletracker.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class RideStatsTest {

    @Test
    fun `selects larger right lean when smaller left lean occurs first`() {
        val result = findMaxLeanPoint(
            listOf(
                trackPoint(leanAngleDeg = -60f),
                trackPoint(leanAngleDeg = 75f),
            )
        )

        assertEquals(1, result?.index)
        assertEquals(75f, result?.absoluteAngleDeg)
    }

    @Test
    fun `selects larger left lean when smaller right lean occurs first`() {
        val result = findMaxLeanPoint(
            listOf(
                trackPoint(leanAngleDeg = 54f),
                trackPoint(leanAngleDeg = -68f),
            )
        )

        assertEquals(1, result?.index)
        assertEquals(68f, result?.absoluteAngleDeg)
    }

    @Test
    fun `selects first point when absolute lean angles are equal`() {
        val result = findMaxLeanPoint(
            listOf(
                trackPoint(leanAngleDeg = -70f),
                trackPoint(leanAngleDeg = 70f),
            )
        )

        assertEquals(0, result?.index)
        assertEquals(70f, result?.absoluteAngleDeg)
    }

    private fun trackPoint(leanAngleDeg: Float) = TrackPoint(
        timestampMs = 0L,
        latitude = 0.0,
        longitude = 0.0,
        speedKmh = 0f,
        leanAngleDeg = leanAngleDeg,
        leanFreshnessMs = 0L,
        gpsFreshnessMs = 0L,
        hasFreshGps = true,
    )
}
