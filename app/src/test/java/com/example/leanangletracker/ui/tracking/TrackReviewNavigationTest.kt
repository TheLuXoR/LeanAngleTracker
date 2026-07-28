package com.example.leanangletracker.ui.tracking

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackReviewNavigationTest {

    @Test
    fun `index progress maps start middle and end`() {
        assertEquals(0f, trackIndexToProgress(selectedIndex = 0, pointCount = 5))
        assertEquals(0.5f, trackIndexToProgress(selectedIndex = 2, pointCount = 5))
        assertEquals(1f, trackIndexToProgress(selectedIndex = 4, pointCount = 5))
    }

    @Test
    fun `index progress clamps values outside the track`() {
        assertEquals(0f, trackIndexToProgress(selectedIndex = -10, pointCount = 5))
        assertEquals(1f, trackIndexToProgress(selectedIndex = 20, pointCount = 5))
    }

    @Test
    fun `progress maps to nearest track index`() {
        assertEquals(0, progressToTrackIndex(progress = 0f, pointCount = 5))
        assertEquals(2, progressToTrackIndex(progress = 0.5f, pointCount = 5))
        assertEquals(4, progressToTrackIndex(progress = 1f, pointCount = 5))
        assertEquals(2, progressToTrackIndex(progress = 0.38f, pointCount = 5))
    }

    @Test
    fun `progress clamps invalid and out of range input`() {
        assertEquals(0, progressToTrackIndex(progress = Float.NaN, pointCount = 5))
        assertEquals(0, progressToTrackIndex(progress = -1f, pointCount = 5))
        assertEquals(4, progressToTrackIndex(progress = 2f, pointCount = 5))
    }

    @Test
    fun `single point track always maps to its only point`() {
        assertEquals(0f, trackIndexToProgress(selectedIndex = 10, pointCount = 1))
        assertEquals(0, progressToTrackIndex(progress = 0.8f, pointCount = 1))
    }

    @Test
    fun `detail zoom starts at level seventeen`() {
        assertEquals(17.0, DEFAULT_TRACK_DETAIL_ZOOM, 0.0)
    }
}
