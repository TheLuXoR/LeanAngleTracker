package com.example.leanangletracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PausedRideMovementDetectionTest {
    @Test
    fun unpurchasedAutoResumeShowsPremiumShortcutAfterMovementThreshold() {
        val started = evaluatePausedRideMovement(
            trackingStarted = true,
            isPaused = true,
            autoResumeEnabled = false,
            isAutoResumePurchased = false,
            speedKmh = 25f,
            nowMs = 1_000L,
            timerStartMs = null,
            premiumShortcutAlreadyVisible = false
        )
        val detected = evaluatePausedRideMovement(
            trackingStarted = true,
            isPaused = true,
            autoResumeEnabled = false,
            isAutoResumePurchased = false,
            speedKmh = 25f,
            nowMs = 11_000L,
            timerStartMs = started.timerStartMs,
            premiumShortcutAlreadyVisible = false
        )

        assertTrue(detected.showPremiumShortcut)
        assertFalse(detected.shouldAutoResume)
        assertNull(detected.timerStartMs)
    }

    @Test
    fun premiumShortcutStaysVisibleAfterRiderSlowsDown() {
        val decision = evaluatePausedRideMovement(
            trackingStarted = true,
            isPaused = true,
            autoResumeEnabled = false,
            isAutoResumePurchased = false,
            speedKmh = 0f,
            nowMs = 20_000L,
            timerStartMs = null,
            premiumShortcutAlreadyVisible = true
        )

        assertTrue(decision.showPremiumShortcut)
        assertFalse(decision.shouldAutoResume)
    }

    @Test
    fun purchasedAndEnabledAutoResumeUsesSameMovementThreshold() {
        val decision = evaluatePausedRideMovement(
            trackingStarted = true,
            isPaused = true,
            autoResumeEnabled = true,
            isAutoResumePurchased = true,
            speedKmh = 20f,
            nowMs = 15_000L,
            timerStartMs = 5_000L,
            premiumShortcutAlreadyVisible = false
        )

        assertTrue(decision.shouldAutoResume)
        assertFalse(decision.showPremiumShortcut)
        assertNull(decision.timerStartMs)
    }
}
