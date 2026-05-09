package com.example.leanangletracker

internal data class SensorTimingPolicy(
    val minDtNs: Long,
    val leanClampDtNs: Long,
    val leanDropDtNs: Long,
    val gyroClampDtNs: Long,
    val gyroDropDtNs: Long,
    val fusionRotationFreshNs: Long,
    val maxLeanRateDegPerSec: Float,
    val maxRollRateRadPerSec: Float,
    val recentLeanBufferSize: Int
)

internal object MainViewModelConfig {
    const val TAG = "MainViewModel"
    const val RECORDER_INTERVAL_MIN_MS = 50
    const val RECORDER_INTERVAL_MAX_MS = 1_000
    const val RECORDER_INTERVAL_STEP_MS = 50
    const val GPS_FRESHNESS_THRESHOLD_MS = 2_500L
    const val CALIBRATION_TILT_MAX_RANGE = 35f
    const val EXTEND_PROXIMITY_METERS = 500f
    const val MAX_LEAN_DEG = 75f
    const val AUTO_PAUSE_LEAN_THRESHOLD = 60f
    const val MAX_ROLL_RATE_RAD_PER_SEC = 8.5f
    const val GYRO_REVERSAL_DAMPING = 0.55f
    const val GYRO_BIAS_COLLECTION_DURATION_NS = 1_500_000_000L
    const val GYRO_BIAS_LINEAR_ACCEL_MAX = 0.45f
    const val GYRO_BIAS_ANGULAR_SPEED_MAX = 0.12f
    const val OBSERVABILITY_LINEAR_ACCEL_MAX = 3.2f
    const val OBSERVABILITY_INNOVATION_MAX_DEG = 14f
    const val OBSERVABILITY_ROLL_RATE_MAX_RAD_PER_SEC = 1.4f
    const val LATERAL_ACCEL_GATING_FULL_MS2 = 4.2f
    const val MAX_OUTPUT_SLEW_RATE_DEG_PER_SEC = 240f
    const val RECENT_LEAN_BUFFER_SIZE = 8
    const val AUTO_REWIND_SPEED_THRESHOLD_KMH = 20f
    const val AUTO_REWIND_DURATION_MS = 10_000L
    const val LIVE_POINTS_UI_LIMIT = 50

    val SENSOR_TIMING_POLICY = SensorTimingPolicy(
        minDtNs = 1_000_000L,
        leanClampDtNs = 50_000_000L,
        leanDropDtNs = 250_000_000L,
        gyroClampDtNs = 50_000_000L,
        gyroDropDtNs = 200_000_000L,
        fusionRotationFreshNs = 120_000_000L,
        maxLeanRateDegPerSec = 180f,
        maxRollRateRadPerSec = MAX_ROLL_RATE_RAD_PER_SEC,
        recentLeanBufferSize = RECENT_LEAN_BUFFER_SIZE
    )
}

internal data class TimedLean(val timestampNs: Long, val valueDeg: Float)
