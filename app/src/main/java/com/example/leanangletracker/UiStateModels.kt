package com.example.leanangletracker

import androidx.annotation.StringRes
import com.example.leanangletracker.ui.animation.BikeLean

data class CalibrationUiState(
    val calibrationStep: BikeLean = BikeLean.UPRIGHT,
    @StringRes val instructionsResId: Int = R.string.instructions_upright,
    val isCalibrated: Boolean = false,
    val leftMax: Float = 0f,
    val rightMax: Float = 0f,
    val currentProgress: Float = 0f,
    val currentAngleDeg: Float = 0f,
    val isWrongDirection: Boolean = false
)

data class TrackPoint(
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val leanAngleDeg: Float,
    val leanFreshnessMs: Long,
    val gpsFreshnessMs: Long,
    val hasFreshGps: Boolean,
    val lapIndex: Int = 0
)

data class RideSession(
    val rideId: Long = 0L,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val points: List<TrackPoint>,
    val name: String? = null,
    val routeDescription: String? = null,
    val isFinished: Boolean = true
)

data class RideSummary(
    val rideId: Long = 0L,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val name: String? = null,
    val routeDescription: String? = null,
    val pointCount: Int = 0,
    val isSkeleton: Boolean = false,
    val isFinished: Boolean = true
)

fun RideSession.toSummary() = RideSummary(
    rideId = rideId,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    name = name,
    routeDescription = routeDescription,
    pointCount = points.size,
    isFinished = isFinished
)

data class TrackingUiState(
    val leanAngleDeg: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val leanHistoryDeg: List<Float> = emptyList(),
    val speedKmh: Float = 0f,
    val gpsActive: Boolean = false,
    val hasTrackData: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val trackingStarted: Boolean = false,
    val isPaused: Boolean = false,
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val elapsedTimeMs: Long = 0L,
    val averageSpeedKmh: Float = 0f,
    val trackLengthKm: Float = 0f,
    val averageLeanAngleDeg: Float = 0f,
    val isUpsideDown: Boolean = false,
    val showHighRotationWarning: Boolean = false,
    val recentPoints: List<TrackPoint> = emptyList(),
    val autoPauseEnabled: Boolean = true
)

data class SettingsUiState(
    val invertLeanAngle: Boolean = false,
    val historyWindowSeconds: Int = 20,
    val recorderIntervalMs: Int = 200,
    val gyroscopeAvailable: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val autoResumeEnabled: Boolean = false,
    val isAutoResumePurchased: Boolean = false,
    val autoPauseEnabled: Boolean = true
)

data class UiState(
    val calibration: CalibrationUiState = CalibrationUiState(),
    val tracking: TrackingUiState = TrackingUiState(),
    val settings: SettingsUiState = SettingsUiState(),
    val rideHistory: List<RideSummary> = emptyList(),
    val expandedRides: Map<Long, RideSession> = emptyMap(),
    val lastSavedRideId: Long? = null,
    val offerExtendSession: RideSession? = null,
    val pendingRecovery: RideSession? = null
)
