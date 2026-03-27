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
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val name: String? = null
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
    val useGyroFusion: Boolean = false,
    val gyroscopeAvailable: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false
)

data class UiState(
    val calibration: CalibrationUiState = CalibrationUiState(),
    val tracking: TrackingUiState = TrackingUiState(),
    val settings: SettingsUiState = SettingsUiState(),
    val rideHistory: List<RideSession> = emptyList(),
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
        const val KEY_USE_GYRO = "use_gyro_fusion"
        const val KEY_GPS_ENABLED = "gps_enabled"
        const val KEY_CALIBRATED = "is_calibrated"
        const val KEY_UPRIGHT_X = "upright_x"
        const val KEY_UPRIGHT_Y = "upright_y"
        const val KEY_UPRIGHT_Z = "upright_z"
        const val KEY_FORWARD_X = "forward_x"
        const val KEY_FORWARD_Y = "forward_y"
        const val KEY_FORWARD_Z = "forward_z"
        const val CALIBRATION_TILT_MAX_RANGE = 35f
        const val EXTEND_PROXIMITY_METERS = 500f
        const val MAX_LEAN_DEG = 75f
        const val MAX_GYRO_DT_SEC = 0.2f
        const val MAX_ROLL_RATE_RAD_PER_SEC = 8.5f
        const val GYRO_REVERSAL_DAMPING = 0.55f
        const val FUSION_ROTATION_FRESH_NS = 120_000_000L
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
    private var lastGyroTimestampNs: Long? = null
    private var lastRollRateRadPerSec = 0f
    private var latestRotationLeanDeg: Float? = null
    private var latestRotationTimestampNs: Long? = null
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

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()
    private val ridePoints = mutableListOf<TrackPoint>()

    init {
        accelerometerSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        linearAccelerationSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        updateSettingsState {
            it.copy(
                gyroscopeAvailable = gyroscopeSensor != null,
                locationPermissionGranted = hasLocationPermission()
            )
        }

        loadPersistedState()

        if (accelerometerSensor == null && gravitySensor == null) {
            updateCalibrationState { it.copy(instructionsResId = R.string.instructions_sensor_missing) }
        }
    }

    private fun loadPersistedState() {
        val savedInvert = prefs.getBoolean(KEY_INVERT, false)
        val savedHistory = prefs.getInt(KEY_HISTORY_WINDOW, 20).coerceIn(5, 120)
        val savedRecorder = prefs.getInt(KEY_RECORDER_INTERVAL, 200).coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
        val savedUseGyro = prefs.getBoolean(KEY_USE_GYRO, false)
        val savedGps = prefs.getBoolean(KEY_GPS_ENABLED, false)

        updateSettingsState {
            it.copy(
                invertLeanAngle = savedInvert,
                historyWindowSeconds = savedHistory,
                recorderIntervalMs = savedRecorder,
                useGyroFusion = savedUseGyro && gyroscopeSensor != null,
                gpsTrackingEnabled = savedGps && it.locationPermissionGranted
            )
        }
        updateTrackingState {
            it.copy(gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled)
        }

        _uiState.value = _uiState.value.copy(rideHistory = rideRepository.loadRides())

        if (!prefs.getBoolean(KEY_CALIBRATED, false)) return

        val savedUpright = readVec3(KEY_UPRIGHT_X, KEY_UPRIGHT_Y, KEY_UPRIGHT_Z)?.normalized() ?: return
        val savedForward = readVec3(KEY_FORWARD_X, KEY_FORWARD_Y, KEY_FORWARD_Z)?.normalized() ?: return

        uprightUp = savedUpright
        bikeForwardAxis = savedForward
        gyroLeanDeg = 0f
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated
            )
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
            .putBoolean(KEY_USE_GYRO, settings.useGyroFusion)
            .putBoolean(KEY_GPS_ENABLED, settings.gpsTrackingEnabled)
            .apply()
    }

    private fun persistCalibration() {
        val up = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        prefs.edit()
            .putBoolean(KEY_CALIBRATED, true)
            .putFloat(KEY_UPRIGHT_X, up.x)
            .putFloat(KEY_UPRIGHT_Y, up.y)
            .putFloat(KEY_UPRIGHT_Z, up.z)
            .putFloat(KEY_FORWARD_X, forward.x)
            .putFloat(KEY_FORWARD_Y, forward.y)
            .putFloat(KEY_FORWARD_Z, forward.z)
            .apply()
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

    fun startTracking() {
        val state = _uiState.value
        if (!state.settings.gpsTrackingEnabled || !state.settings.locationPermissionGranted) return

        if (!locationUpdatesRunning) {
            startLocationUpdates()
        }

        // Check if there's a ride from the same day
        val lastRide = state.rideHistory.firstOrNull()
        val isSameDayRideAvailable = lastRide != null && isSameDay(lastRide.endedAtMs, System.currentTimeMillis())

        if (isSameDayRideAvailable) {
            val currentLoc = latestGpsLocation
            if (currentLoc != null) {
                // We have a location, check proximity now
                val lastPoint = lastRide!!.points.lastOrNull()
                if (lastPoint != null) {
                    val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, currentLoc.latitude, currentLoc.longitude)
                    if (dist < EXTEND_PROXIMITY_METERS) {
                        _uiState.value = state.copy(offerExtendSession = lastRide)
                        return
                    }
                }
                performStartNewRide()
            } else {
                // No location yet, start tracking UI but wait for first GPS update before deciding extend/new
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
        // Calculate previous track length
        trackLengthMeters = 0f
        for (i in 0 until session.points.size - 1) {
            trackLengthMeters += distanceMeters(
                session.points[i].latitude, session.points[i].longitude,
                session.points[i+1].latitude, session.points[i+1].longitude
            )
        }
        // Accumulated time from previous session
        accumulatedTimeMs = session.endedAtMs - session.startedAtMs
        lastResumeMs = System.currentTimeMillis()
        
        ridePoints.clear()
        ridePoints.addAll(session.points)
        
        // Remove the ride from history as it will be replaced
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
            // Resuming
            lastResumeMs = now
            updateTrackingState { it.copy(isPaused = false) }
        } else {
            // Pausing
            accumulatedTimeMs += (now - lastResumeMs)
            updateTrackingState { it.copy(isPaused = true) }
        }
    }

    fun finishRide() {
        isCheckingForExtension = false
        val pointsSnapshot = ridePoints.toList()
        val started = activeRideStartedMs ?: System.currentTimeMillis()
        if (pointsSnapshot.isNotEmpty()) {
            val newSession = RideSession(
                startedAtMs = started,
                endedAtMs = System.currentTimeMillis(),
                points = pointsSnapshot
            )
            rideRepository.saveRide(newSession)
            
            _uiState.value = _uiState.value.copy(
                rideHistory = (listOf(newSession) + _uiState.value.rideHistory).sortedByDescending { it.startedAtMs },
                lastSavedRideId = newSession.startedAtMs
            )
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

    fun deleteRide(session: RideSession) {
        rideRepository.deleteRide(session)
        _uiState.value = _uiState.value.copy(
            rideHistory = _uiState.value.rideHistory.filter { it != session }
        )
    }

    fun updateRideName(session: RideSession, newName: String) {
        val updatedSession = session.copy(name = newName)
        rideRepository.saveRide(updatedSession)
        _uiState.value = _uiState.value.copy(
            rideHistory = _uiState.value.rideHistory.map {
                if (it.startedAtMs == session.startedAtMs) updatedSession else it
            }
        )
    }

    fun combineRides(sessions: List<RideSession>) {
        if (sessions.size < 2) return
        val sorted = sessions.sortedBy { it.startedAtMs }
        val mergedPoints = sorted.flatMap { it.points }.sortedBy { it.timestampMs }
        val newSession = RideSession(
            startedAtMs = sorted.first().startedAtMs,
            endedAtMs = sorted.last().endedAtMs,
            points = mergedPoints,
            name = "Combined Ride"
        )
        
        // Save new
        rideRepository.saveRide(newSession)
        
        // Delete old
        sessions.forEach { rideRepository.deleteRide(it) }
        
        val idsToRemove = sessions.map { it.startedAtMs }.toSet()
        _uiState.value = _uiState.value.copy(
            rideHistory = (listOf(newSession) + _uiState.value.rideHistory.filter { it.startedAtMs !in idsToRemove })
                .sortedByDescending { it.startedAtMs }
        )
    }

    fun startCalibration() {
        uprightUp = null
        leftUpPeak = null
        rightUpPeak = null
        bikeForwardAxis = null
        calibrationSideAxis = null
        gyroLeanDeg = 0f
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null
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

    fun continueCalibrationFallback() = Unit // Removed logic for automated fallback

    fun setInvertLeanAngle(invert: Boolean) {
        val previous = _uiState.value
        if (previous.settings.invertLeanAngle == invert) return

        val transformedHistory = previous.tracking.leanHistoryDeg.map { -it }
        val transformedTimedHistory = leanHistory.map { it.copy(valueDeg = -it.valueDeg) }

        leanHistory.clear()
        leanHistory.addAll(transformedTimedHistory)

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

    fun setUseGyroFusion(enabled: Boolean) {
        val shouldUse = enabled && gyroscopeSensor != null
        val current = _uiState.value
        _uiState.value = current.copy(settings = current.settings.copy(useGyroFusion = shouldUse))
        persistSettings()
    }

    fun resetExtrema() {
        val historyValues = _uiState.value.tracking.leanHistoryDeg
        updateTrackingState {
            it.copy(
                maxLeftDeg = historyValues.minOrNull()?.coerceAtMost(0f) ?: 0f,
                maxRightDeg = historyValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f
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
            Sensor.TYPE_GYROSCOPE -> updateGyroLean(event)
        }
    }

    private fun handleManualCalibrationSensorUpdate() {
        val up = uprightUp ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val angleDeg = angleBetweenDeg(up, currentUp)
        
        val step = _uiState.value.calibration.calibrationStep

        // Side detection: Only initialize once when a significant tilt (>3 deg) is detected during the LEFT step
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
            
            // Track the actual vectors for the peak to calculate forward axis later
            if (step == BikeLean.LEFT && isLeftSide && progress >= newLeftMax) leftUpPeak = currentUp
            if (step == BikeLean.RIGHT && isRightSide && progress >= newRightMax) rightUpPeak = currentUp

            // Only show progress if moving in the correct direction for the current step
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
        
        // Improve forward axis calculation: use cross product of left and right peaks if they differ
        // If they are too similar, fall back to side axis cross upright
        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f) {
            val side = calibrationSideAxis ?: Vec3(1f, 0f, 0f)
            forward = side.cross(up).normalized()
        }
        
        bikeForwardAxis = forward
        gyroLeanDeg = 0f
        lastGyroTimestampNs = null
        lastRollRateRadPerSec = 0f
        latestRotationLeanDeg = null
        latestRotationTimestampNs = null

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

    private fun updateLeanAngle(timestampNs: Long) {
        val upRef = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        val currentUp = (filteredGravity * -1f).normalized()

        val numerator = forward.dot(upRef.cross(currentUp))
        val denominator = upRef.dot(currentUp)
        val leanRad = atan2(numerator, denominator)
        val accelLeanDeg = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
        latestRotationLeanDeg = accelLeanDeg
        latestRotationTimestampNs = timestampNs

        val useFusion = _uiState.value.settings.useGyroFusion && gyroscopeSensor != null
        val fusedLeanDeg = if (useFusion) {
            val gravityNorm = filteredGravity.norm()
            val gravityTrust = (1f - (abs(gravityNorm - 9.81f) / 4f)).coerceIn(0f, 1f)
            val linearAccelNorm = filteredLinearAcceleration.norm()
            val dynamicsTrust = (1f - (linearAccelNorm / 7f)).coerceIn(0.1f, 1f)
            val accelTrust = (gravityTrust * dynamicsTrust).coerceIn(0.05f, 1f)

            val accelCorrectionLimitDeg = 2f + 8f * accelTrust
            val accelInnovation = (accelLeanDeg - gyroLeanDeg).coerceIn(-accelCorrectionLimitDeg, accelCorrectionLimitDeg)
            val accelGain = 0.015f + 0.12f * accelTrust

            val rotationFresh = latestRotationTimestampNs?.let { timestampNs - it <= FUSION_ROTATION_FRESH_NS } == true
            val rotationGain = if (rotationFresh) 0.03f else 0f
            val rotationInnovation = ((latestRotationLeanDeg ?: accelLeanDeg) - gyroLeanDeg).coerceIn(-4f, 4f)

            val fused = gyroLeanDeg + (accelInnovation * accelGain) + (rotationInnovation * rotationGain)
            gyroLeanDeg = fused.coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
            gyroLeanDeg
        } else {
            gyroLeanDeg = accelLeanDeg
            accelLeanDeg
        }

        val leanDeg = if (_uiState.value.settings.invertLeanAngle) -fusedLeanDeg else fusedLeanDeg
        latestLeanDeg = leanDeg
        latestLeanTimestampNs = timestampNs

        // Update peak since last recorder tick
        if (Math.abs(leanDeg) > Math.abs(peakLeanSinceLastTick)) {
            peakLeanSinceLastTick = leanDeg
        }

        leanHistory += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)

        val previous = _uiState.value
        pruneHistory(timestampNs, previous.settings.historyWindowSeconds)

        val visibleHistory = leanHistory.map { it.valueDeg }
        val visibleLeft = visibleHistory.minOrNull()?.coerceAtMost(0f) ?: 0f
        val visibleRight = visibleHistory.maxOrNull()?.coerceAtLeast(0f) ?: 0f

        _uiState.value = previous.copy(
            tracking = previous.tracking.copy(
                leanAngleDeg = leanDeg,
                maxLeftDeg = minOf(previous.tracking.maxLeftDeg, visibleLeft),
                maxRightDeg = maxOf(previous.tracking.maxRightDeg, visibleRight),
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
            return
        }

        if (!_uiState.value.settings.useGyroFusion) {
            lastGyroTimestampNs = event.timestamp
            lastRollRateRadPerSec = 0f
            return
        }

        val forward = bikeForwardAxis ?: return
        val previousTimestamp = lastGyroTimestampNs
        lastGyroTimestampNs = event.timestamp
        if (previousTimestamp == null) return

        val dt = (event.timestamp - previousTimestamp) / 1_000_000_000f
        if (dt <= 0f || dt > MAX_GYRO_DT_SEC) return

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val rawRollRateRadPerSec = omega.dot(forward).coerceIn(-MAX_ROLL_RATE_RAD_PER_SEC, MAX_ROLL_RATE_RAD_PER_SEC)
        val filteredRollRateRadPerSec = (0.8f * lastRollRateRadPerSec) + (0.2f * rawRollRateRadPerSec)
        val directionReversalDetected =
            (lastRollRateRadPerSec * filteredRollRateRadPerSec < 0f) &&
                (abs(filteredRollRateRadPerSec - lastRollRateRadPerSec) > 1.1f)
        val effectiveRollRate = if (directionReversalDetected) {
            filteredRollRateRadPerSec * GYRO_REVERSAL_DAMPING
        } else {
            filteredRollRateRadPerSec
        }
        lastRollRateRadPerSec = filteredRollRateRadPerSec

        val deltaDeg = Math.toDegrees((effectiveRollRate * dt).toDouble()).toFloat()
        gyroLeanDeg = (gyroLeanDeg + deltaDeg).coerceIn(-MAX_LEAN_DEG, MAX_LEAN_DEG)
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
            val lastRide = _uiState.value.rideHistory.firstOrNull()
            if (lastRide != null) {
                val lastPoint = lastRide.points.lastOrNull()
                if (lastPoint != null) {
                    val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, location.latitude, location.longitude)
                    if (dist < EXTEND_PROXIMITY_METERS) {
                        _uiState.value = _uiState.value.copy(offerExtendSession = lastRide)
                        return
                    }
                }
            }
            performStartNewRide()
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

        // Use the peak lean angle captured since the last recording tick
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
        
        // Reset peak for the next interval
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
