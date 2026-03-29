package com.example.leanangletracker

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.leanangletracker.data.RideRepository
import com.example.leanangletracker.ui.animation.BikeLean
import com.example.leanangletracker.ui.tracking.calculateRouteDescription
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import kotlin.math.acos
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
    operator fun times(scale: Float) = Vec3(x * scale, y * scale, z * scale)

    fun dot(other: Vec3): Float = x * other.x + y * other.y + z * other.z

    fun cross(other: Vec3): Vec3 = Vec3(
        y * other.z - z * other.y,
        z * other.x - x * other.z,
        x * other.y - y * other.x
    )

    fun norm(): Float = sqrt(dot(this))

    fun normalized(): Vec3 {
        val n = norm()
        if (n < 1e-6f) return this
        return this * (1f / n)
    }
}

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
    val startedAtMs: Long,
    val endedAtMs: Long,
    val points: List<TrackPoint>,
    val name: String? = null,
    val routeDescription: String? = null
)

data class RideSummary(
    val startedAtMs: Long,
    val endedAtMs: Long,
    val name: String? = null,
    val routeDescription: String? = null,
    val pointCount: Int = 0
)

fun RideSession.toSummary() = RideSummary(
    startedAtMs = startedAtMs,
    endedAtMs = endedAtMs,
    name = name,
    routeDescription = routeDescription,
    pointCount = points.size
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
    val averageLeanAngleDeg: Float = 0f
)

data class SettingsUiState(
    val invertLeanAngle: Boolean = false,
    val historyWindowSeconds: Int = 20,
    val recorderIntervalMs: Int = 200,
    val gyroscopeAvailable: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false
)

data class UiState(
    val calibration: CalibrationUiState = CalibrationUiState(),
    val tracking: TrackingUiState = TrackingUiState(),
    val settings: SettingsUiState = SettingsUiState(),
    val rideHistory: List<RideSummary> = emptyList(),
    val expandedRides: Map<Long, RideSession> = emptyMap(),
    val lastSavedRideId: Long? = null,
    val offerExtendSession: RideSession? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    private companion object {
        const val RECORDER_INTERVAL_MIN_MS = 50
        const val RECORDER_INTERVAL_MAX_MS = 1_000
        const val RECORDER_INTERVAL_STEP_MS = 50
        const val GPS_FRESHNESS_THRESHOLD_MS = 2_500L
        const val PREFS_NAME = "lean_angle_tracker_prefs"
        const val KEY_INVERT = "invert_lean"
        const val KEY_HISTORY_WINDOW = "history_window_s"
        const val KEY_RECORDER_INTERVAL = "recorder_interval_ms"
        const val KEY_GPS_ENABLED = "gps_enabled"
        const val KEY_CALIBRATED = "is_calibrated"
        const val KEY_UPRIGHT_X = "upright_x"
        const val KEY_UPRIGHT_Y = "upright_y"
        const val KEY_UPRIGHT_Z = "upright_z"
        const val KEY_FORWARD_X = "forward_x"
        const val KEY_FORWARD_Y = "forward_y"
        const val KEY_FORWARD_Z = "forward_z"
        const val KEY_GYRO_BIAS_X = "gyro_bias_x"
        const val KEY_GYRO_BIAS_Y = "gyro_bias_y"
        const val KEY_GYRO_BIAS_Z = "gyro_bias_z"
        const val CALIBRATION_TILT_MAX_RANGE = 35f
        const val EXTEND_PROXIMITY_METERS = 500f
        const val MAX_LEAN_DEG = 75f
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

        private data class SensorTimingPolicy(
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

        private val SENSOR_TIMING_POLICY = SensorTimingPolicy(
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

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val locationManager = application.getSystemService(LocationManager::class.java)
    private val rideRepository = RideRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var filteredGravity = Vec3(0f, 0f, 9.81f)
    private var filteredLinearAcceleration = Vec3(0f, 0f, 0f)

    private var uprightUp: Vec3? = null
    private var leftUpPeak: Vec3? = null
    private var rightUpPeak: Vec3? = null
    private var bikeForwardAxis: Vec3? = null
    private var calibrationSideAxis: Vec3? = null

    private var gyroLeanDeg = 0f
    private var gyroBiasRadPerSec = 0f
    private var gyroBiasVectorRadPerSec: Vec3? = null
    private var gyroBiasCollectionStartNs: Long? = null
    private var gyroBiasAccumulated = Vec3(0f, 0f, 0f)
    private var gyroBiasSampleCount = 0
    private var lastGyroTimestampNs: Long? = null
    private var lastRollRateRadPerSec = 0f
    private var lastYawRateRadPerSec = 0f
    private var latestRotationLeanDeg: Float? = null
    private var latestRotationTimestampNs: Long? = null
    private var lastLeanComputationTimestampNs: Long? = null
    private var speedKmh = 0f
    private var activeRideStartedMs: Long? = null
    private var accumulatedTimeMs: Long = 0L
    private var lastResumeMs: Long = 0L
    private var locationUpdatesRunning = false
    private var latestLeanDeg = 0f
    private var peakLeanSinceLastTick = 0f
    private var latestLeanTimestampNs: Long? = null
    private var latestGpsLocation: Location? = null
    private var latestGpsTimestampNs: Long? = null
    private var recorderJob: Job? = null
    private var trackLengthMeters = 0f
    private var isCheckingForExtension = false
    private var latestLinearAccelerationMagnitude = 0f
    private var fusionConfidence = 1f

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()
    private val recentLeanSamples = ArrayDeque<TimedLean>()
    private val ridePoints = mutableListOf<TrackPoint>()

    init {
        val delay = SensorManager.SENSOR_DELAY_FASTEST
        accelerometerSensor?.let { sensorManager.registerListener(this, it, delay) }
        gravitySensor?.let { sensorManager.registerListener(this, it, delay) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, delay) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, delay) }

        updateSettingsState {
            it.copy(
                gyroscopeAvailable = gyroscopeSensor != null,
                locationPermissionGranted = hasLocationPermission()
            )
        }

        loadPersistedState()
        backfillRouteDescriptions()

        if (accelerometerSensor == null && gravitySensor == null) {
            updateCalibrationState { it.copy(instructionsResId = R.string.instructions_sensor_missing) }
        }
    }

    private fun loadPersistedState() {
        val savedInvert = prefs.getBoolean(KEY_INVERT, false)
        val savedHistory = prefs.getInt(KEY_HISTORY_WINDOW, 20).coerceIn(5, 120)
        val savedRecorder = prefs.getInt(KEY_RECORDER_INTERVAL, 200).coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
        val savedGps = prefs.getBoolean(KEY_GPS_ENABLED, false)

        updateSettingsState {
            it.copy(
                invertLeanAngle = savedInvert,
                historyWindowSeconds = savedHistory,
                recorderIntervalMs = savedRecorder,
                gpsTrackingEnabled = savedGps && it.locationPermissionGranted
            )
        }
        updateTrackingState {
            it.copy(gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled)
        }

        _uiState.value = _uiState.value.copy(rideHistory = rideRepository.loadRides().map { it.toSummary() })

        if (!prefs.getBoolean(KEY_CALIBRATED, false)) return

        val savedUpright = readVec3(KEY_UPRIGHT_X, KEY_UPRIGHT_Y, KEY_UPRIGHT_Z)?.normalized() ?: return
        val savedForward = readVec3(KEY_FORWARD_X, KEY_FORWARD_Y, KEY_FORWARD_Z)?.normalized() ?: return

        uprightUp = savedUpright
        bikeForwardAxis = savedForward
        gyroLeanDeg = 0f
        gyroBiasVectorRadPerSec = readVec3(KEY_GYRO_BIAS_X, KEY_GYRO_BIAS_Y, KEY_GYRO_BIAS_Z)
        gyroBiasRadPerSec = gyroBiasVectorRadPerSec?.dot(savedForward) ?: 0f
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        lastYawRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null
        lastLeanComputationTimestampNs = null
        recentLeanSamples.clear()

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated
            )
        }
    }

    private fun backfillRouteDescriptions() {
        viewModelScope.launch(Dispatchers.IO) {
            val ridesToUpdate = _uiState.value.rideHistory.filter { it.routeDescription.isNullOrBlank() }
            // val ridesToUpdate = _uiState.value.rideHistory
            if (ridesToUpdate.isEmpty()) return@launch
            
            val allFullRides = rideRepository.loadRides()
            
            ridesToUpdate.forEach { summary ->
                val fullRide = allFullRides.find { it.startedAtMs == summary.startedAtMs }
                if (fullRide != null) {
                    val description = calculateRouteDescription(getApplication(), fullRide)
                    if (description != null) {
                        val updatedRide = fullRide.copy(routeDescription = description)
                        rideRepository.saveRide(updatedRide)
                        
                        launch(Dispatchers.Main) {
                            _uiState.update { state ->
                                state.copy(
                                    rideHistory = state.rideHistory.map {
                                        if (it.startedAtMs == summary.startedAtMs) updatedRide.toSummary() else it
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun readVec3(xKey: String, yKey: String, zKey: String): Vec3? {
        if (!prefs.contains(xKey) || !prefs.contains(yKey) || !prefs.contains(zKey)) return null
        return Vec3(
            prefs.getFloat(xKey, 0f),
            prefs.getFloat(yKey, 0f),
            prefs.getFloat(zKey, 0f)
        )
    }

    private fun persistSettings() {
        val settings = _uiState.value.settings
        prefs.edit()
            .putBoolean(KEY_INVERT, settings.invertLeanAngle)
            .putInt(KEY_HISTORY_WINDOW, settings.historyWindowSeconds)
            .putInt(KEY_RECORDER_INTERVAL, settings.recorderIntervalMs)
            .putBoolean(KEY_GPS_ENABLED, settings.gpsTrackingEnabled)
            .apply()
    }

    private fun persistCalibration() {
        val up = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        val editor = prefs.edit()
            .putBoolean(KEY_CALIBRATED, true)
            .putFloat(KEY_UPRIGHT_X, up.x)
            .putFloat(KEY_UPRIGHT_Y, up.y)
            .putFloat(KEY_UPRIGHT_Z, up.z)
            .putFloat(KEY_FORWARD_X, forward.x)
            .putFloat(KEY_FORWARD_Y, forward.y)
            .putFloat(KEY_FORWARD_Z, forward.z)
        val biasVec = gyroBiasVectorRadPerSec
        if (biasVec != null) {
            editor
                .putFloat(KEY_GYRO_BIAS_X, biasVec.x)
                .putFloat(KEY_GYRO_BIAS_Y, biasVec.y)
                .putFloat(KEY_GYRO_BIAS_Z, biasVec.z)
        } else {
            editor
                .remove(KEY_GYRO_BIAS_X)
                .remove(KEY_GYRO_BIAS_Y)
                .remove(KEY_GYRO_BIAS_Z)
        }
        editor.apply()
    }

    private fun clearPersistedCalibration() {
        prefs.edit()
            .putBoolean(KEY_CALIBRATED, false)
            .remove(KEY_UPRIGHT_X)
            .remove(KEY_UPRIGHT_Y)
            .remove(KEY_UPRIGHT_Z)
            .remove(KEY_FORWARD_X)
            .remove(KEY_FORWARD_Y)
            .remove(KEY_FORWARD_Z)
            .remove(KEY_GYRO_BIAS_X)
            .remove(KEY_GYRO_BIAS_Y)
            .remove(KEY_GYRO_BIAS_Z)
            .apply()
    }

    private inline fun updateCalibrationState(transform: (CalibrationUiState) -> CalibrationUiState) {
        _uiState.value = _uiState.value.copy(calibration = transform(_uiState.value.calibration))
    }

    private inline fun updateTrackingState(transform: (TrackingUiState) -> TrackingUiState) {
        _uiState.value = _uiState.value.copy(tracking = transform(_uiState.value.tracking))
    }

    private inline fun updateSettingsState(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.value = _uiState.value.copy(settings = transform(_uiState.value.settings))
    }

    fun onLocationPermissionResult(granted: Boolean) {
        updateSettingsState { it.copy(locationPermissionGranted = granted) }
        if (!granted) {
            stopLocationUpdates()
            updateTrackingState { it.copy(trackingStarted = false) }
        }
        updateTrackingState {
            it.copy(
                gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled && granted,
                gpsActive = locationUpdatesRunning && granted
            )
        }
        persistSettings()
    }

    fun setGpsTrackingEnabled(enabled: Boolean) {
        updateSettingsState { it.copy(gpsTrackingEnabled = enabled) }
        if (!enabled) {
            stopLocationUpdates()
            ridePoints.clear()
            activeRideStartedMs = null
            accumulatedTimeMs = 0L
            latestGpsLocation = null
            latestGpsTimestampNs = null
            speedKmh = 0f
            trackLengthMeters = 0f
            isCheckingForExtension = false
            stopRecorder()
            updateTrackingState {
                it.copy(
                    speedKmh = 0f,
                    gpsActive = false,
                    hasTrackData = false,
                    trackingStarted = false,
                    isPaused = false,
                    currentLatitude = null,
                    currentLongitude = null,
                    elapsedTimeMs = 0L,
                    averageSpeedKmh = 0f,
                    trackLengthKm = 0f,
                    averageLeanAngleDeg = 0f
                )
            }
        }
        updateTrackingState {
            it.copy(gpsTrackingEnabled = enabled && _uiState.value.settings.locationPermissionGranted)
        }
        persistSettings()
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    fun startTracking() {
        val state = _uiState.value
        if (!state.settings.gpsTrackingEnabled || !state.settings.locationPermissionGranted) return

        if (!locationUpdatesRunning) {
            startLocationUpdates()
        }

        val lastRideSummary = state.rideHistory.firstOrNull()
        val isSameDayRideAvailable = lastRideSummary != null && isSameDay(lastRideSummary.endedAtMs, System.currentTimeMillis())

        if (isSameDayRideAvailable) {
            val currentLoc = latestGpsLocation
            if (currentLoc != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val fullRide = rideRepository.loadRides().find { it.startedAtMs == lastRideSummary!!.startedAtMs }
                    if (fullRide != null) {
                        val lastPoint = fullRide.points.lastOrNull()
                        if (lastPoint != null) {
                            val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, currentLoc.latitude, currentLoc.longitude)
                            if (dist < EXTEND_PROXIMITY_METERS) {
                                launch(Dispatchers.Main) {
                                    _uiState.value = state.copy(offerExtendSession = fullRide)
                                }
                                return@launch
                            }
                        }
                    }
                    launch(Dispatchers.Main) {
                        performStartNewRide()
                    }
                }
            } else {
                isCheckingForExtension = true
                updateTrackingState { it.copy(trackingStarted = true, gpsTrackingEnabled = true) }
            }
        } else {
            performStartNewRide()
        }
    }

    fun confirmExtendRide(extend: Boolean) {
        val offer = _uiState.value.offerExtendSession
        _uiState.value = _uiState.value.copy(offerExtendSession = null)
        if (extend && offer != null) {
            performExtendRide(offer)
        } else {
            performStartNewRide()
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun performStartNewRide() {
        if (!locationUpdatesRunning) {
            startLocationUpdates()
        }
        activeRideStartedMs = System.currentTimeMillis()
        accumulatedTimeMs = 0L
        lastResumeMs = activeRideStartedMs!!
        trackLengthMeters = 0f
        ridePoints.clear()
        peakLeanSinceLastTick = 0f
        startRecorder()
        updateTrackingState { it.copy(trackingStarted = true, isPaused = false, gpsTrackingEnabled = true, hasTrackData = false) }
    }

    private fun performExtendRide(session: RideSession) {
        if (!locationUpdatesRunning) {
            startLocationUpdates()
        }
        activeRideStartedMs = session.startedAtMs
        trackLengthMeters = 0f
        for (i in 0 until session.points.size - 1) {
            trackLengthMeters += distanceMeters(
                session.points[i].latitude, session.points[i].longitude,
                session.points[i+1].latitude, session.points[i+1].longitude
            )
        }
        accumulatedTimeMs = session.endedAtMs - session.startedAtMs
        lastResumeMs = System.currentTimeMillis()
        
        ridePoints.clear()
        ridePoints.addAll(session.points)
        
        rideRepository.deleteRide(session)
        _uiState.value = _uiState.value.copy(rideHistory = _uiState.value.rideHistory.filter { it.startedAtMs != session.startedAtMs })

        peakLeanSinceLastTick = 0f
        startRecorder()
        updateTrackingState { it.copy(trackingStarted = true, isPaused = false, gpsTrackingEnabled = true, hasTrackData = true) }
    }

    fun togglePauseTracking() {
        val currentState = _uiState.value.tracking
        if (!currentState.trackingStarted) return
        
        val now = System.currentTimeMillis()
        if (currentState.isPaused) {
            lastResumeMs = now
            updateTrackingState { it.copy(isPaused = false) }
        } else {
            accumulatedTimeMs += (now - lastResumeMs)
            updateTrackingState { it.copy(isPaused = true) }
        }
    }

    fun finishRide() {
        isCheckingForExtension = false
        val pointsSnapshot = ridePoints.toList()
        val started = activeRideStartedMs ?: System.currentTimeMillis()
        val ended = System.currentTimeMillis()
        
        viewModelScope.launch(Dispatchers.IO) {
            if (pointsSnapshot.isNotEmpty()) {
                val tempSession = RideSession(
                    startedAtMs = started,
                    endedAtMs = ended,
                    points = pointsSnapshot
                )
                val description = calculateRouteDescription(getApplication(), tempSession)
                val newSession = tempSession.copy(routeDescription = description)
                
                rideRepository.saveRide(newSession)
                
                launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        rideHistory = (listOf(newSession.toSummary()) + _uiState.value.rideHistory).sortedByDescending { it.startedAtMs },
                        lastSavedRideId = newSession.startedAtMs
                    )
                }
            }
        }
        
        stopLocationUpdates()
        stopRecorder()
        ridePoints.clear()
        activeRideStartedMs = null
        accumulatedTimeMs = 0L
        latestGpsLocation = null
        latestGpsTimestampNs = null
        speedKmh = 0f
        trackLengthMeters = 0f
        updateTrackingState {
            it.copy(
                hasTrackData = false,
                speedKmh = 0f,
                gpsActive = false,
                trackingStarted = false,
                isPaused = false,
                currentLatitude = null,
                currentLongitude = null,
                elapsedTimeMs = 0L,
                averageSpeedKmh = 0f,
                trackLengthKm = 0f,
                averageLeanAngleDeg = 0f
            )
        }
    }

    fun deleteRide(summary: RideSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = rideRepository.loadRides().find { it.startedAtMs == summary.startedAtMs }
            if (session != null) {
                rideRepository.deleteRide(session)
                launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        rideHistory = _uiState.value.rideHistory.filter { it.startedAtMs != summary.startedAtMs },
                        expandedRides = _uiState.value.expandedRides - summary.startedAtMs
                    )
                }
            }
        }
    }

    fun updateRideName(summary: RideSummary, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val session = rideRepository.loadRides().find { it.startedAtMs == summary.startedAtMs }
            if (session != null) {
                val updated = session.copy(name = newName)
                rideRepository.saveRide(updated)
                launch(Dispatchers.Main) {
                    _uiState.value = _uiState.value.copy(
                        rideHistory = _uiState.value.rideHistory.map {
                            if (it.startedAtMs == summary.startedAtMs) updated.toSummary() else it
                        },
                        expandedRides = if (_uiState.value.expandedRides.containsKey(summary.startedAtMs)) {
                            _uiState.value.expandedRides + (summary.startedAtMs to updated)
                        } else _uiState.value.expandedRides
                    )
                }
            }
        }
    }

    fun loadFullSession(startedAtMs: Long) {
        if (_uiState.value.expandedRides.containsKey(startedAtMs)) return
        viewModelScope.launch(Dispatchers.IO) {
            val session = rideRepository.loadRides().find { it.startedAtMs == startedAtMs }
            if (session != null) {
                launch(Dispatchers.Main) {
                    _uiState.update { it.copy(expandedRides = it.expandedRides + (startedAtMs to session)) }
                }
            }
        }
    }

    fun combineRides(summaries: List<RideSummary>) {
        if (summaries.size < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            val allRides = rideRepository.loadRides()
            val sessions = summaries.mapNotNull { summary -> allRides.find { it.startedAtMs == summary.startedAtMs } }
            if (sessions.size < 2) return@launch
            
            val sorted = sessions.sortedBy { it.startedAtMs }
            val mergedPoints = sorted.flatMap { it.points }.sortedBy { it.timestampMs }
            
            val tempSession = RideSession(
                startedAtMs = sorted.first().startedAtMs,
                endedAtMs = sorted.last().endedAtMs,
                points = mergedPoints,
                name = "Combined Ride"
            )
            val description = calculateRouteDescription(getApplication(), tempSession)
            val newSession = tempSession.copy(routeDescription = description)
            
            rideRepository.saveRide(newSession)
            sessions.forEach { rideRepository.deleteRide(it) }
            
            val idsToRemove = summaries.map { it.startedAtMs }.toSet()
            launch(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(
                    rideHistory = (listOf(newSession.toSummary()) + _uiState.value.rideHistory.filter { it.startedAtMs !in idsToRemove })
                        .sortedByDescending { it.startedAtMs },
                    expandedRides = _uiState.value.expandedRides.filterKeys { it !in idsToRemove }
                )
            }
        }
    }

    fun startCalibration() {
        uprightUp = null
        leftUpPeak = null
        rightUpPeak = null
        bikeForwardAxis = null
        calibrationSideAxis = null
        gyroLeanDeg = 0f
        gyroBiasRadPerSec = 0f
        gyroBiasVectorRadPerSec = null
        gyroBiasCollectionStartNs = null
        gyroBiasAccumulated = Vec3(0f, 0f, 0f)
        gyroBiasSampleCount = 0
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        lastYawRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null
        lastLeanComputationTimestampNs = null
        recentLeanSamples.clear()
        clearPersistedCalibration()

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.UPRIGHT,
                isCalibrated = false,
                instructionsResId = R.string.instructions_upright,
                leftMax = 0f,
                rightMax = 0f,
                currentProgress = 0f,
                currentAngleDeg = 0f,
                isWrongDirection = false
            )
        }
    }

    fun captureUpright() {
        val state = _uiState.value.calibration
        when (state.calibrationStep) {
            BikeLean.UPRIGHT -> {
                uprightUp = (filteredGravity * -1f).normalized()
                updateCalibrationState {
                    it.copy(
                        calibrationStep = BikeLean.LEFT,
                        instructionsResId = R.string.instructions_tilt_left_then_return
                    )
                }
            }
            BikeLean.LEFT -> {
                updateCalibrationState {
                    it.copy(
                        calibrationStep = BikeLean.RIGHT,
                        instructionsResId = R.string.instructions_tilt_right_then_return
                    )
                }
            }
            BikeLean.RIGHT -> {
                finalizeManualCalibration()
            }
            else -> Unit
        }
    }

    fun continueCalibrationFallback() = Unit

    fun setInvertLeanAngle(invert: Boolean) {
        val previous = _uiState.value
        if (previous.settings.invertLeanAngle == invert) return

        val transformedHistory = previous.tracking.leanHistoryDeg.map { -it }
        val transformedTimedHistory = leanHistory.map { it.copy(valueDeg = -it.valueDeg) }
        val transformedRecentSamples = recentLeanSamples.map { it.copy(valueDeg = -it.valueDeg) }

        leanHistory.clear()
        leanHistory.addAll(transformedTimedHistory)
        recentLeanSamples.clear()
        recentLeanSamples.addAll(transformedRecentSamples)

        _uiState.value = previous.copy(
            settings = previous.settings.copy(invertLeanAngle = invert),
            tracking = previous.tracking.copy(
                leanAngleDeg = -previous.tracking.leanAngleDeg,
                maxLeftDeg = -previous.tracking.maxRightDeg,
                maxRightDeg = -previous.tracking.maxLeftDeg,
                leanHistoryDeg = transformedHistory
            )
        )
        persistSettings()
    }

    fun setHistoryWindowSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        val previous = _uiState.value
        if (previous.settings.historyWindowSeconds == clamped) return

        leanHistory.lastOrNull()?.let { pruneHistory(it.timestampNs, clamped) }
        _uiState.value = previous.copy(
            settings = previous.settings.copy(historyWindowSeconds = clamped),
            tracking = previous.tracking.copy(leanHistoryDeg = leanHistory.map { it.valueDeg })
        )
        persistSettings()
    }

    fun setRecorderIntervalMs(intervalMs: Int) {
        val stepped = (intervalMs / RECORDER_INTERVAL_STEP_MS) * RECORDER_INTERVAL_STEP_MS
        val clamped = stepped.coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
        val previous = _uiState.value
        if (previous.settings.recorderIntervalMs == clamped) return

        _uiState.value = previous.copy(settings = previous.settings.copy(recorderIntervalMs = clamped))
        persistSettings()
    }

    fun resetExtrema() {
        updateTrackingState {
            it.copy(
                maxLeftDeg = 0f,
                maxRightDeg = 0f
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val alpha = if (gravitySensor == null) 0.92f else 0.985f
                val raw = Vec3(event.values[0], event.values[1], event.values[2])
                filteredGravity = filteredGravity * alpha + raw * (1f - alpha)
                latestLinearAccelerationMagnitude = (raw - filteredGravity).norm()

                val step = _uiState.value.calibration.calibrationStep
                if (step == BikeLean.LEFT || step == BikeLean.RIGHT) {
                    handleManualCalibrationSensorUpdate()
                } else if (step == BikeLean.UPRIGHT || step == BikeLean.DONE) {
                    updateLeanAngle(event.timestamp)
                }
            }
            Sensor.TYPE_GRAVITY -> {
                val raw = Vec3(event.values[0], event.values[1], event.values[2])
                filteredGravity = filteredGravity * 0.25f + raw * 0.75f
                val step = _uiState.value.calibration.calibrationStep
                if (step == BikeLean.LEFT || step == BikeLean.RIGHT) {
                    handleManualCalibrationSensorUpdate()
                } else if (step == BikeLean.UPRIGHT || step == BikeLean.DONE) {
                    updateLeanAngle(event.timestamp)
                }
            }
            Sensor.TYPE_LINEAR_ACCELERATION -> {
                val raw = Vec3(event.values[0], event.values[1], event.values[2])
                filteredLinearAcceleration = filteredLinearAcceleration * 0.6f + raw * 0.4f
            }
            Sensor.TYPE_GYROSCOPE -> {
                collectGyroBiasSample(event)
                updateGyroLean(event)
            }
        }
    }

    private fun collectGyroBiasSample(event: SensorEvent) {
        val calibrationStep = _uiState.value.calibration.calibrationStep
        val isManualUpright = calibrationStep == BikeLean.UPRIGHT
        val isDynamicBiasPossible = calibrationStep == BikeLean.DONE && speedKmh < 1.0f && abs(gyroLeanDeg) < 3.0f

        if (!isManualUpright && !isDynamicBiasPossible) return

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val angularSpeed = omega.norm()
        val isStable =
            latestLinearAccelerationMagnitude <= GYRO_BIAS_LINEAR_ACCEL_MAX &&
                angularSpeed <= GYRO_BIAS_ANGULAR_SPEED_MAX

        if (!isStable) {
            gyroBiasCollectionStartNs = null
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            gyroBiasSampleCount = 0
            if (isManualUpright) {
                updateCalibrationState { state ->
                    state.copy(instructionsResId = R.string.instructions_hold_still_for_bias)
                }
            }
            return
        }

        if (isManualUpright && _uiState.value.calibration.instructionsResId == R.string.instructions_hold_still_for_bias) {
            updateCalibrationState { state ->
                state.copy(instructionsResId = R.string.instructions_upright)
            }
        }

        val start = gyroBiasCollectionStartNs ?: event.timestamp.also {
            gyroBiasCollectionStartNs = it
            gyroBiasAccumulated = Vec3(0f, 0f, 0f)
            gyroBiasSampleCount = 0
        }

        gyroBiasAccumulated += omega
        gyroBiasSampleCount += 1

        if (event.timestamp - start < GYRO_BIAS_COLLECTION_DURATION_NS || gyroBiasSampleCount < 10) return

        val invCount = 1f / gyroBiasSampleCount
        val newBias = gyroBiasAccumulated * invCount
        
        gyroBiasVectorRadPerSec = newBias
        bikeForwardAxis?.let { forward ->
            gyroBiasRadPerSec = newBias.dot(forward)
        }
        
        gyroBiasCollectionStartNs = null
        gyroBiasAccumulated = Vec3(0f, 0f, 0f)
        gyroBiasSampleCount = 0
        
        if (isDynamicBiasPossible) {
            persistCalibration() 
        }
    }

    private fun handleManualCalibrationSensorUpdate() {
        val up = uprightUp ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val angleDeg = angleBetweenDeg(up, currentUp)
        
        val step = _uiState.value.calibration.calibrationStep

        if (calibrationSideAxis == null && step == BikeLean.LEFT && angleDeg > 3f) {
            calibrationSideAxis = (currentUp - up * currentUp.dot(up)).normalized()
        }

        val sideComponent = if (calibrationSideAxis != null) currentUp.dot(calibrationSideAxis!!) else 0f
        val isLeftSide = sideComponent > 0.1f
        val isRightSide = sideComponent < -0.1f

        val progress = (angleDeg / CALIBRATION_TILT_MAX_RANGE).coerceIn(0f, 1f)
        
        val isWrongWay = when (step) {
            BikeLean.LEFT -> isRightSide && angleDeg > 5f
            BikeLean.RIGHT -> isLeftSide && angleDeg > 5f
            else -> false
        }

        updateCalibrationState { state ->
            val newLeftMax = if (step == BikeLean.LEFT && isLeftSide) maxOf(state.leftMax, progress) else state.leftMax
            val newRightMax = if (step == BikeLean.RIGHT && isRightSide) maxOf(state.rightMax, progress) else state.rightMax
            
            if (step == BikeLean.LEFT && isLeftSide && progress >= newLeftMax) leftUpPeak = currentUp
            if (step == BikeLean.RIGHT && isRightSide && progress >= newRightMax) rightUpPeak = currentUp

            val directionProgress = when (step) {
                BikeLean.LEFT -> if (isLeftSide) progress else 0f
                BikeLean.RIGHT -> if (isRightSide) progress else 0f
                else -> progress
            }

            state.copy(
                leftMax = newLeftMax,
                rightMax = newRightMax,
                currentProgress = directionProgress,
                currentAngleDeg = angleDeg,
                isWrongDirection = isWrongWay
            )
        }
    }

    private fun finalizeManualCalibration() {
        val up = uprightUp ?: return
        val left = leftUpPeak ?: up
        val right = rightUpPeak ?: up
        
        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f) {
            val side = calibrationSideAxis ?: Vec3(1f, 0f, 0f)
            forward = side.cross(up).normalized()
        }
        
        bikeForwardAxis = forward
        gyroLeanDeg = 0f
        gyroBiasRadPerSec = gyroBiasVectorRadPerSec?.dot(forward) ?: 0f
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        lastYawRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null
        lastLeanComputationTimestampNs = null
        recentLeanSamples.clear()

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated,
                currentProgress = 0f,
                currentAngleDeg = 0f
            )
        }
        persistCalibration()
    }

    private fun angleBetweenDeg(a: Vec3, b: Vec3): Float {
        val dot = a.dot(b).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot).toDouble()).toFloat()
    }

    private fun registerRecentLeanSample(timestampNs: Long, leanDeg: Float) {
        recentLeanSamples += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)
        while (recentLeanSamples.size > SENSOR_TIMING_POLICY.recentLeanBufferSize) {
            recentLeanSamples.removeFirst()
        }
    }

    private fun fallbackLeanFromRecentSamples(timestampNs: Long): Float? {
        if (recentLeanSamples.isEmpty()) return null
        val averaged = recentLeanSamples.map { it.valueDeg }.average().toFloat()
        val clamped = averaged.coerceAtLeast(-MAX_LEAN_DEG).coerceAtMost(MAX_LEAN_DEG)
        latestLeanDeg = clamped
        latestLeanTimestampNs = timestampNs
        return clamped
    }

    private fun updateLeanAngle(timestampNs: Long) {
        val upRef = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val sideAxis = upRef.cross(forward).normalized()

        val previousTimestamp = lastLeanComputationTimestampNs
        if (previousTimestamp != null) {
            val rawDeltaNs = timestampNs - previousTimestamp
            if (rawDeltaNs <= 0L) return 
            if (rawDeltaNs > SENSOR_TIMING_POLICY.leanDropDtNs) {
                lastLeanComputationTimestampNs = timestampNs
                fallbackLeanFromRecentSamples(timestampNs)
                return
            }
        }
        lastLeanComputationTimestampNs = timestampNs
        val clampedDeltaNs = if (previousTimestamp == null) {
            SENSOR_TIMING_POLICY.leanClampDtNs
        } else {
            (timestampNs - previousTimestamp).coerceIn(SENSOR_TIMING_POLICY.minDtNs, SENSOR_TIMING_POLICY.leanClampDtNs)
        }
        val leanDtSec = clampedDeltaNs / 1_000_000_000f

        val numerator = forward.dot(upRef.cross(currentUp))
        val denominator = upRef.dot(currentUp)
        val leanRad = atan2(numerator, denominator)
        val accelLeanDeg = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
        latestRotationLeanDeg = accelLeanDeg
        latestRotationTimestampNs = timestampNs

        val useFusion = gyroscopeSensor != null
        val fusedLeanDeg = if (useFusion) {
            val gravityNorm = filteredGravity.norm()
            val gravityTrust = (1f - (abs(gravityNorm - 9.81f) / 4f)).coerceIn(0f, 1f)
            val linearAccelNorm = filteredLinearAcceleration.norm()
            val sideLinearAccel = abs(filteredLinearAcceleration.dot(sideAxis))
            val lowLinearPhaseTrust = (1f - (linearAccelNorm / OBSERVABILITY_LINEAR_ACCEL_MAX)).coerceIn(0f, 1f)
            val innovationAbs = abs(accelLeanDeg - gyroLeanDeg)
            val innovationStabilityTrust =
                (1f - (innovationAbs / OBSERVABILITY_INNOVATION_MAX_DEG)).coerceIn(0f, 1f)
            val rollStabilityTrust =
                (1f - (abs(lastRollRateRadPerSec) / OBSERVABILITY_ROLL_RATE_MAX_RAD_PER_SEC)).coerceIn(0f, 1f)
            val stablePhaseTrust = (0.55f * innovationStabilityTrust + 0.45f * rollStabilityTrust).coerceIn(0f, 1f)
            val observabilityTrust = maxOf(lowLinearPhaseTrust, stablePhaseTrust)

            val lateralInnovationGate =
                (1f - (sideLinearAccel / LATERAL_ACCEL_GATING_FULL_MS2)).coerceIn(0.15f, 1f)
            val rawConfidence = (gravityTrust * observabilityTrust * lateralInnovationGate).coerceIn(0f, 1f)
            fusionConfidence = (fusionConfidence * 0.88f + rawConfidence * 0.12f).coerceIn(0f, 1f)

            val speedMs = speedKmh / 3.6f
            val centripetalLeanRad = atan2(speedMs * lastYawRateRadPerSec, 9.81f)
            val centripetalLeanDeg = Math.toDegrees(centripetalLeanRad.toDouble()).toFloat()
            
            val referenceLeanDeg = if (speedKmh > 5f && lateralInnovationGate < 0.7f) {
                centripetalLeanDeg
            } else {
                accelLeanDeg
            }

            val accelCorrectionLimitDeg = (1f + 10f * fusionConfidence) * lateralInnovationGate
            val accelInnovation = (referenceLeanDeg - gyroLeanDeg).coerceIn(-accelCorrectionLimitDeg, accelCorrectionLimitDeg)
            val accelGain = 0.003f + 0.14f * fusionConfidence * fusionConfidence

            val rotationFresh = latestRotationTimestampNs?.let { timestampNs - it <= SENSOR_TIMING_POLICY.fusionRotationFreshNs } == true
            val rotationGain = if (rotationFresh) 0.03f else 0f
            val rotationInnovation = ((latestRotationLeanDeg ?: referenceLeanDeg) - gyroLeanDeg).coerceIn(-4f, 4f)

            val fused = gyroLeanDeg + (accelInnovation * accelGain) + (rotationInnovation * rotationGain)
            val maxFusedStepDeg = SENSOR_TIMING_POLICY.maxLeanRateDegPerSec * leanDtSec
            val boundedFused = (fused - gyroLeanDeg).coerceIn(-maxFusedStepDeg, maxFusedStepDeg) + gyroLeanDeg
            gyroLeanDeg = boundedFused.coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
            gyroLeanDeg
        } else {
            gyroLeanDeg = accelLeanDeg
            fusionConfidence = 1f
            accelLeanDeg
        }

        val previousTimestampNs = latestLeanTimestampNs
        val dtSeconds = previousTimestampNs?.let { ((timestampNs - it) / 1_000_000_000f).coerceAtLeast(0f) } ?: 0f
        val maxStep = if (dtSeconds > 0f) MAX_OUTPUT_SLEW_RATE_DEG_PER_SEC * dtSeconds else MAX_LEAN_DEG
        val smoothedLeanDeg = (fusedLeanDeg - latestLeanDeg).coerceIn(-maxStep, maxStep) + latestLeanDeg
        val leanDeg = if (_uiState.value.settings.invertLeanAngle) -smoothedLeanDeg else smoothedLeanDeg
        latestLeanDeg = leanDeg
        latestLeanTimestampNs = timestampNs

        if (Math.abs(leanDeg) > Math.abs(peakLeanSinceLastTick)) {
            peakLeanSinceLastTick = leanDeg
        }

        leanHistory += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)
        registerRecentLeanSample(timestampNs, leanDeg)

        val previous = _uiState.value
        pruneHistory(timestampNs, previous.settings.historyWindowSeconds)

        val visibleHistory = leanHistory.map { it.valueDeg }

        _uiState.value = previous.copy(
            tracking = previous.tracking.copy(
                leanAngleDeg = leanDeg,
                maxLeftDeg = minOf(previous.tracking.maxLeftDeg, if (leanDeg < 0f) leanDeg else 0f),
                maxRightDeg = maxOf(previous.tracking.maxRightDeg, if (leanDeg > 0f) leanDeg else 0f),
                leanHistoryDeg = visibleHistory,
                speedKmh = speedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = ridePoints.isNotEmpty(),
                currentLatitude = latestGpsLocation?.latitude,
                currentLongitude = latestGpsLocation?.longitude
            )
        )
    }

    private fun pruneHistory(currentTimestampNs: Long, historyWindowSeconds: Int) {
        val cutoff = currentTimestampNs - historyWindowSeconds * 1_000_000_000L
        while (leanHistory.isNotEmpty() && leanHistory.first().timestampNs < cutoff) {
            leanHistory.removeFirst()
        }
    }

    private fun updateGyroLean(event: SensorEvent) {
        if (_uiState.value.calibration.calibrationStep != BikeLean.DONE) {
            lastGyroTimestampNs = null
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        if (gyroscopeSensor == null) {
            lastGyroTimestampNs = event.timestamp
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        val forward = bikeForwardAxis ?: return
        val up = uprightUp ?: return
        val previousTimestamp = lastGyroTimestampNs
        if (previousTimestamp == null) {
            lastGyroTimestampNs = event.timestamp
            return
        }

        val rawDeltaNs = event.timestamp - previousTimestamp
        if (rawDeltaNs <= 0L) return 
        if (rawDeltaNs > SENSOR_TIMING_POLICY.gyroDropDtNs) {
            lastGyroTimestampNs = event.timestamp
            lastRollRateRadPerSec = 0f
            lastYawRateRadPerSec = 0f
            return
        }

        lastGyroTimestampNs = event.timestamp
        val clampedDeltaNs = rawDeltaNs.coerceIn(SENSOR_TIMING_POLICY.minDtNs, SENSOR_TIMING_POLICY.gyroClampDtNs)
        val dt = clampedDeltaNs / 1_000_000_000f

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val biasVector = gyroBiasVectorRadPerSec ?: Vec3(0f, 0f, 0f)
        val unbiasedOmega = omega - biasVector
        
        val rollRateRadPerSec = unbiasedOmega.dot(forward)
            .coerceIn(-SENSOR_TIMING_POLICY.maxRollRateRadPerSec, SENSOR_TIMING_POLICY.maxRollRateRadPerSec)
        
        val yawRateRadPerSec = unbiasedOmega.dot(up)
        
        lastRollRateRadPerSec = rollRateRadPerSec
        lastYawRateRadPerSec = yawRateRadPerSec
        
        val deltaDeg = Math.toDegrees((rollRateRadPerSec * dt).toDouble()).toFloat()
        val maxStepDeg = SENSOR_TIMING_POLICY.maxLeanRateDegPerSec * dt
        val boundedDeltaDeg = deltaDeg.coerceIn(-maxStepDeg, maxStepDeg)
        gyroLeanDeg = (gyroLeanDeg + boundedDeltaDeg).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
    }

    private fun hasLocationPermission(): Boolean {
        val context = getApplication<Application>()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private fun startLocationUpdates() {
        if (!hasLocationPermission() || locationUpdatesRunning) return

        val provider = when {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> null
        }

        if (provider == null) {
            updateTrackingState { it.copy(gpsActive = false) }
            return
        }

        locationManager.requestLocationUpdates(provider, 1000L, 2f, this)
        locationUpdatesRunning = true
        updateTrackingState { it.copy(gpsActive = true) }
    }

    private fun stopLocationUpdates() {
        if (!locationUpdatesRunning) {
            updateTrackingState { it.copy(gpsActive = false) }
            return
        }
        locationManager.removeUpdates(this)
        locationUpdatesRunning = false
        updateTrackingState { it.copy(gpsActive = false) }
    }

    override fun onLocationChanged(location: Location) {
        if (location.hasAccuracy() && location.accuracy > 60f) return

        latestGpsLocation = location
        latestGpsTimestampNs = location.elapsedRealtimeNanos
        speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)

        if (isCheckingForExtension) {
            isCheckingForExtension = false
            val lastSummary = _uiState.value.rideHistory.firstOrNull()
            if (lastSummary != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val lastRide = rideRepository.loadRides().find { it.startedAtMs == lastSummary.startedAtMs }
                    if (lastRide != null) {
                        val lastPoint = lastRide.points.lastOrNull()
                        if (lastPoint != null) {
                            val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, location.latitude, location.longitude)
                            if (dist < EXTEND_PROXIMITY_METERS) {
                                launch(Dispatchers.Main) {
                                    _uiState.value = _uiState.value.copy(offerExtendSession = lastRide)
                                }
                                return@launch
                            }
                        }
                    }
                    launch(Dispatchers.Main) {
                        performStartNewRide()
                    }
                }
            } else {
                performStartNewRide()
            }
        }

        if (_uiState.value.settings.gpsTrackingEnabled) {
            if (_uiState.value.tracking.trackingStarted && activeRideStartedMs == null && !isCheckingForExtension) {
                activeRideStartedMs = System.currentTimeMillis()
                lastResumeMs = activeRideStartedMs!!
            }
            if (!_uiState.value.tracking.trackingStarted) {
                updateTrackingState { it.copy(speedKmh = speedKmh, gpsActive = locationUpdatesRunning, currentLatitude = location.latitude, currentLongitude = location.longitude) }
                return
            }
        }

        updateTrackingState {
            it.copy(
                speedKmh = speedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = ridePoints.isNotEmpty(),
                currentLatitude = location.latitude,
                currentLongitude = location.longitude
            )
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

    private fun startRecorder() {
        if (recorderJob?.isActive == true) return
        recorderJob = viewModelScope.launch {
            while (isActive) {
                val intervalMs = _uiState.value.settings.recorderIntervalMs
                    .coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
                delay(intervalMs.toLong())
                recordFusedSample()
            }
        }
    }

    private fun stopRecorder() {
        recorderJob?.cancel()
        recorderJob = null
    }

    private fun recordFusedSample() {
        val state = _uiState.value
        if (!state.settings.gpsTrackingEnabled || !state.tracking.trackingStarted || state.tracking.isPaused || isCheckingForExtension) return

        val gps = latestGpsLocation ?: return
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val leanAgeMs = latestLeanTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
            ?: Long.MAX_VALUE
        val gpsAgeMs = latestGpsTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
            ?: Long.MAX_VALUE
        val fusedSpeedKmh = (gps.speed * 3.6f).coerceAtLeast(0f)
        speedKmh = fusedSpeedKmh

        val nowMs = System.currentTimeMillis()
        val previousPoint = ridePoints.lastOrNull()
        if (previousPoint != null) {
            trackLengthMeters += distanceMeters(
                previousPoint.latitude,
                previousPoint.longitude,
                gps.latitude,
                gps.longitude
            )
        }

        val recordedLean = if (peakLeanSinceLastTick != 0f) peakLeanSinceLastTick else latestLeanDeg
        
        ridePoints += TrackPoint(
            timestampMs = nowMs,
            latitude = gps.latitude,
            longitude = gps.longitude,
            speedKmh = fusedSpeedKmh,
            leanAngleDeg = recordedLean,
            leanFreshnessMs = leanAgeMs,
            gpsFreshnessMs = gpsAgeMs,
            hasFreshGps = gpsAgeMs <= GPS_FRESHNESS_THRESHOLD_MS,
            lapIndex = 0
        )
        
        peakLeanSinceLastTick = latestLeanDeg

        val elapsedMs = accumulatedTimeMs + if (!state.tracking.isPaused) (nowMs - lastResumeMs) else 0L
        val avgSpeed = if (ridePoints.isNotEmpty()) ridePoints.map { it.speedKmh }.average().toFloat() else 0f
        val avgLean = if (ridePoints.isNotEmpty()) ridePoints.map { kotlin.math.abs(it.leanAngleDeg.toDouble()) }.average().toFloat() else 0f

        updateTrackingState {
            it.copy(
                speedKmh = fusedSpeedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = true,
                currentLatitude = gps.latitude,
                currentLongitude = gps.longitude,
                elapsedTimeMs = elapsedMs,
                averageSpeedKmh = avgSpeed,
                trackLengthKm = trackLengthMeters / 1000f,
                averageLeanAngleDeg = avgLean
            )
        }
    }


    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0].coerceAtLeast(0f)
    }

    private fun isSameDay(ms1: Long, ms2: Long): Boolean {
        val cal1 = Calendar.getInstance().apply { timeInMillis = ms1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = ms2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
               cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        stopRecorder()
        super.onCleared()
    }
}
