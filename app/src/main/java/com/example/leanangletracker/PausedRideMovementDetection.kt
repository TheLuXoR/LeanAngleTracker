package com.example.leanangletracker

internal data class PausedRideMovementDecision(
    val timerStartMs: Long?,
    val showPremiumShortcut: Boolean,
    val shouldAutoResume: Boolean
)

internal fun evaluatePausedRideMovement(
    trackingStarted: Boolean,
    isPaused: Boolean,
    autoResumeEnabled: Boolean,
    hasPremiumAccess: Boolean,
    speedKmh: Float,
    nowMs: Long,
    timerStartMs: Long?,
    premiumShortcutAlreadyVisible: Boolean
): PausedRideMovementDecision {
    if (!trackingStarted || !isPaused) return PausedRideMovementDecision(null, false, false)

    if (hasPremiumAccess && !autoResumeEnabled) {
        return PausedRideMovementDecision(null, false, false)
    }

    if (!hasPremiumAccess && premiumShortcutAlreadyVisible) {
        return PausedRideMovementDecision(null, true, false)
    }

    if (speedKmh < MainViewModelConfig.AUTO_REWIND_SPEED_THRESHOLD_KMH) {
        return PausedRideMovementDecision(null, false, false)
    }

    val detectionStartedMs = timerStartMs ?: nowMs
    val movementDetected = nowMs - detectionStartedMs >= MainViewModelConfig.AUTO_REWIND_DURATION_MS
    if (!movementDetected) {
        return PausedRideMovementDecision(detectionStartedMs, false, false)
    }

    return if (hasPremiumAccess && autoResumeEnabled) {
        PausedRideMovementDecision(null, false, true)
    } else {
        PausedRideMovementDecision(null, true, false)
    }
}
