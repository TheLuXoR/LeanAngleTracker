package com.example.leanangletracker

import androidx.annotation.StringRes
import com.example.leanangletracker.ui.animation.BikeLean

data class CalibrationUiState(
    val calibrationStep: BikeLean = BikeLean.UPRIGHT,
    val isCalibrated: Boolean = false,
    val currentProgress: Float = 0f,
    val maximumTiltProgress: Float = 0f,
    val leanDetected: Boolean = false,
    val currentTiltDeg: Float = 0f,
    @param:StringRes val errorResId: Int? = null,
    val currentStepIndex: Int = 1,
    val totalSteps: Int = 3
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
    val isFinished: Boolean = true,
    val accumulatedTimeMs: Long = 0L,
    val trackLengthMeters: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val sumSpeedKmh: Float = 0f,
    val sumAbsLeanDeg: Float = 0f
)

data class RideSummary(
    val rideId: Long = 0L,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val name: String? = null,
    val routeDescription: String? = null,
    val pointCount: Int = 0,
    val isSkeleton: Boolean = false,
    val isFinished: Boolean = true,
    val accumulatedTimeMs: Long = 0L,
    val trackLengthMeters: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val averageSpeedKmh: Float = 0f,
    val averageLeanAngleDeg: Float = 0f
)

fun RideSession.toSummary() = RideSummary(
    rideId = rideId,
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    name = name,
    routeDescription = routeDescription,
    pointCount = points.size,
    isFinished = isFinished,
    accumulatedTimeMs = accumulatedTimeMs,
    trackLengthMeters = trackLengthMeters,
    maxLeftDeg = maxLeftDeg,
    maxRightDeg = maxRightDeg,
    averageSpeedKmh = if (points.isNotEmpty()) sumSpeedKmh / points.size else 0f,
    averageLeanAngleDeg = if (points.isNotEmpty()) sumAbsLeanDeg / points.size else 0f
)

data class TrackingUiState(
    val leanAngleDeg: Float = 0f,
    val gaugeExtrema: LeanExtrema = LeanExtrema.ZERO,
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

data class AppTourUiState(
    val offerPending: Boolean = false,
    val isActive: Boolean = false,
    val currentPage: Int = 0
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
    val pendingRecovery: RideSession? = null,
    val appTour: AppTourUiState = AppTourUiState()
)
