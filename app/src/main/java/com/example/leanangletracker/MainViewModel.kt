package com.example.leanangletracker

import android.Manifest
import android.app.Application
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
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.domain.RideSessionUseCases
import com.example.leanangletracker.ui.animation.BikeLean
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
    val pointCount: Int = 0,
    val isSkeleton: Boolean = false
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
    val averageLeanAngleDeg: Float = 0f,
    val isUpsideDown: Boolean = false,
    val recentPoints: List<TrackPoint> = emptyList()
)

data class SettingsUiState(
    val invertLeanAngle: Boolean = false,
    val historyWindowSeconds: Int = 20,
    val recorderIntervalMs: Int = 200,
    val gyroscopeAvailable: Boolean = false,
    val gpsTrackingEnabled: Boolean = false,
    val locationPermissionGranted: Boolean = false,
    val autoResumeEnabled: Boolean = false,
    val isAutoResumePurchased: Boolean = false
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

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    private companion object {
        const val TAG = "MainViewModel"
        const val RECORDER_INTERVAL_MIN_MS = 50
        const val RECORDER_INTERVAL_MAX_MS = 1_000
        const val RECORDER_INTERVAL_STEP_MS = 50
        const val GPS_FRESHNESS_THRESHOLD_MS = 2_500L
        const val CALIBRATION_TILT_MAX_RANGE = 35f
        const val EXTEND_PROXIMITY_METERS = 500f
        const val MAX_LEAN_DEG = 75f
        const val AUTO_PAUSE_LEAN_THRESHOLD = 70f
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
    private val rideSessionUseCases = RideSessionUseCases(application, rideRepository)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val settingsStore = SettingsStore(application.applicationContext)

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
    private var activeRideId: Long? = null
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
    private var autoResumeTimerStartMs: Long? = null
    private val pausedPointsBuffer = ArrayDeque<TrackPoint>()

    // Rolling ride statistics to avoid keeping all points in memory
    private var ridePointCount = 0
    private var rideSumSpeedKmh = 0f
    private var rideSumAbsLeanDeg = 0f
    private val recentRidePoints = ArrayDeque<TrackPoint>()

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()
    private val recentLeanSamples = ArrayDeque<TimedLean>()

    init {
        val delay = SensorManager.SENSOR_DELAY_FASTEST
        accelerometerSensor?.let { 
            sensorManager.registerListener(this, it, delay)
        }
        gravitySensor?.let { 
            sensorManager.registerListener(this, it, delay)
        }
        linearAccelerationSensor?.let { 
            sensorManager.registerListener(this, it, delay)
        }
        gyroscopeSensor?.let { 
            sensorManager.registerListener(this, it, delay)
        }

        updateSettingsState {
            it.copy(
                gyroscopeAvailable = gyroscopeSensor != null,
                locationPermissionGranted = hasLocationPermission()
            )
        }

        loadPersistedState()
        backfillRouteDescriptions()
        checkForUnfinishedRides()

        if (accelerometerSensor == null && gravitySensor == null) {
            updateCalibrationState { it.copy(instructionsResId = R.string.instructions_sensor_missing) }
        }
    }

    private fun checkForUnfinishedRides() {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = rideSessionUseCases.findUnfinishedRideForRecovery() ?: return@launch
            launch(Dispatchers.Main) { _uiState.update { it.copy(pendingRecovery = pending) } }
        }
    }

    fun resolveRecovery(continueRide: Boolean) {
        val session = _uiState.value.pendingRecovery ?: return
        _uiState.update { it.copy(pendingRecovery = null) }
        
        if (continueRide) {
            viewModelScope.launch(Dispatchers.Main) {
                if (hasLocationPermission()) {
                    startLocationUpdates()
                }
                activeRideStartedMs = session.startedAtMs
                activeRideId = rideRepository.startNewRide(session.startedAtMs)
                
                recentRidePoints.clear()
                recentRidePoints.addAll(session.points.takeLast(LIVE_POINTS_UI_LIMIT))
                
                trackLengthMeters = 0f
                ridePointCount = session.points.size
                rideSumSpeedKmh = 0f
                rideSumAbsLeanDeg = 0f
                
                for (i in 0 until session.points.size) {
                    val p = session.points[i]
                    rideSumSpeedKmh += p.speedKmh
                    rideSumAbsLeanDeg += abs(p.leanAngleDeg)
                    if (i > 0) {
                        trackLengthMeters += distanceMeters(
                            session.points[i-1].latitude, session.points[i-1].longitude,
                            p.latitude, p.longitude
                        )
                    }
                    rideRepository.recordPoint(p, activeRideId!!)
                }
                
                accumulatedTimeMs = session.endedAtMs - session.startedAtMs
                lastResumeMs = System.currentTimeMillis()
                peakLeanSinceLastTick = 0f
                startRecorder()
                updateTrackingState { it.copy(trackingStarted = true, isPaused = false, hasTrackData = true, recentPoints = recentRidePoints.toList()) }
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val recoveredRide = rideSessionUseCases.saveRecoveredRide(session)
                launch(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            rideHistory = (listOf(recoveredRide.toSummary()) + state.rideHistory).sortedByDescending { it.startedAtMs },
                            lastSavedRideId = recoveredRide.startedAtMs
                        )
                    }
                }
            }
        }
    }

    private fun loadPersistedState() {
        val persistedState = settingsStore.load(
            recorderIntervalMinMs = RECORDER_INTERVAL_MIN_MS,
            recorderIntervalMaxMs = RECORDER_INTERVAL_MAX_MS
        )
        val persistedSettings = persistedState.settings

        updateSettingsState {
            it.copy(
                invertLeanAngle = persistedSettings.invertLeanAngle,
                historyWindowSeconds = persistedSettings.historyWindowSeconds,
                recorderIntervalMs = persistedSettings.recorderIntervalMs,
                gpsTrackingEnabled = persistedSettings.gpsTrackingEnabled && it.locationPermissionGranted,
                autoResumeEnabled = persistedSettings.autoResumeEnabled,
                isAutoResumePurchased = persistedSettings.isAutoResumePurchased
            )
        }
        updateTrackingState {
            it.copy(gpsTrackingEnabled = _uiState.value.settings.gpsTrackingEnabled)
        }

        viewModelScope.launch(Dispatchers.IO) {
            val history = rideSessionUseCases.loadRideHistory()
            launch(Dispatchers.Main) {
                _uiState.update { it.copy(rideHistory = history) }
            }
        }

        val persistedCalibration = persistedState.calibration ?: return

        uprightUp = persistedCalibration.uprightUp
        bikeForwardAxis = persistedCalibration.bikeForwardAxis
        gyroLeanDeg = 0f
        gyroBiasVectorRadPerSec = persistedCalibration.gyroBiasVectorRadPerSec
        gyroBiasRadPerSec = persistedCalibration.bikeForwardAxis?.let { gyroBiasVectorRadPerSec?.dot(it) }
            ?: 0f
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
            val updated = rideSessionUseCases.backfillRouteDescriptions()
            if (updated.isEmpty()) return@launch
            launch(Dispatchers.Main) {
                _uiState.update { state ->
                    state.copy(rideHistory = state.rideHistory.map { summary ->
                        updated.find { it.startedAtMs == summary.startedAtMs }?.toSummary() ?: summary
                    })
                }
            }
        }
    }

    private fun persistSettings() {
        settingsStore.save(_uiState.value.settings)
    }

    private fun persistCalibration() {
        val up = uprightUp ?: return
        val forward = bikeForwardAxis ?: return
        settingsStore.saveCalibration(up, forward, gyroBiasVectorRadPerSec)
    }

    private fun clearPersistedCalibration() {
        settingsStore.clearCalibration()
    }

    private inline fun updateCalibrationState(transform: (CalibrationUiState) -> CalibrationUiState) {
        _uiState.update { it.copy(calibration = transform(it.calibration)) }
    }

    private inline fun updateTrackingState(transform: (TrackingUiState) -> TrackingUiState) {
        _uiState.update { it.copy(tracking = transform(it.tracking)) }
    }

    private inline fun updateSettingsState(transform: (SettingsUiState) -> SettingsUiState) {
        _uiState.update { it.copy(settings = transform(it.settings)) }
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
            recentRidePoints.clear()
            pausedPointsBuffer.clear()
            activeRideId = null
            activeRideStartedMs = null
            accumulatedTimeMs = 0L
            latestGpsLocation = null
            latestGpsTimestampNs = null
            speedKmh = 0f
            trackLengthMeters = 0f
            ridePointCount = 0
            rideSumSpeedKmh = 0f
            rideSumAbsLeanDeg = 0f
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
                    averageLeanAngleDeg = 0f,
                    isUpsideDown = false,
                    recentPoints = emptyList()
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
                    val fullRide = rideSessionUseCases.loadSession(lastRideSummary!!.startedAtMs)
                    if (fullRide != null) {
                        val lastPoint = fullRide.points.lastOrNull()
                        if (lastPoint != null) {
                            val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, currentLoc.latitude, currentLoc.longitude)
                            if (dist < EXTEND_PROXIMITY_METERS) {
                                launch(Dispatchers.Main) {
                                    _uiState.update { it.copy(offerExtendSession = fullRide) }
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
            viewModelScope.launch {
                performStartNewRide()
            }
        }
    }

    fun confirmExtendRide(extend: Boolean) {
        val offer = _uiState.value.offerExtendSession
        _uiState.update { it.copy(offerExtendSession = null) }
        if (extend && offer != null) {
            performExtendRide(offer)
        } else {
            viewModelScope.launch { performStartNewRide() }
        }
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    private suspend fun performStartNewRide() {
        if (!locationUpdatesRunning) {
            startLocationUpdates()
        }
        val startTime = System.currentTimeMillis()
        activeRideStartedMs = startTime
        activeRideId = rideRepository.startNewRide(startTime)
        accumulatedTimeMs = 0L
        lastResumeMs = startTime
        trackLengthMeters = 0f
        ridePointCount = 0
        rideSumSpeedKmh = 0f
        rideSumAbsLeanDeg = 0f
        recentRidePoints.clear()
        pausedPointsBuffer.clear()
        peakLeanSinceLastTick = 0f
        startRecorder()
        updateTrackingState { it.copy(trackingStarted = true, isPaused = false, gpsTrackingEnabled = true, hasTrackData = false, recentPoints = emptyList()) }
    }

    private fun performExtendRide(session: RideSession) {
        viewModelScope.launch {
            if (!locationUpdatesRunning) {
                startLocationUpdates()
            }
            activeRideStartedMs = session.startedAtMs
            activeRideId = rideRepository.startNewRide(session.startedAtMs)
            
            trackLengthMeters = 0f
            ridePointCount = session.points.size
            rideSumSpeedKmh = 0f
            rideSumAbsLeanDeg = 0f
            
            for (i in 0 until session.points.size) {
                val p = session.points[i]
                rideSumSpeedKmh += p.speedKmh
                rideSumAbsLeanDeg += abs(p.leanAngleDeg)
                if (i > 0) {
                    trackLengthMeters += distanceMeters(
                        session.points[i-1].latitude, session.points[i-1].longitude,
                        p.latitude, p.longitude
                    )
                }
                rideRepository.recordPoint(p, activeRideId!!)
            }
            
            accumulatedTimeMs = session.endedAtMs - session.startedAtMs
            lastResumeMs = System.currentTimeMillis()
            
            recentRidePoints.clear()
            recentRidePoints.addAll(session.points.takeLast(LIVE_POINTS_UI_LIMIT))
            pausedPointsBuffer.clear()
            
            rideSessionUseCases.deleteRide(session.startedAtMs)
            _uiState.update { state -> state.copy(rideHistory = state.rideHistory.filter { it.startedAtMs != session.startedAtMs }) }

            peakLeanSinceLastTick = 0f
            startRecorder()
            updateTrackingState { it.copy(trackingStarted = true, isPaused = false, gpsTrackingEnabled = true, hasTrackData = true, recentPoints = recentRidePoints.toList()) }
        }
    }

    fun togglePauseTracking() {
        val currentState = _uiState.value.tracking
        if (!currentState.trackingStarted) return
        
        val now = System.currentTimeMillis()
        if (currentState.isPaused) {
            lastResumeMs = now
            if (pausedPointsBuffer.isNotEmpty()) {
                pausedPointsBuffer.forEach { point ->
                    addTrackPoint(point)
                    activeRideId?.let { id -> viewModelScope.launch(Dispatchers.IO) { rideRepository.recordPoint(point, id) } }
                }
                pausedPointsBuffer.clear()
            }
            updateTrackingState { it.copy(isPaused = false) }
        } else {
            accumulatedTimeMs += (now - lastResumeMs)
            pausedPointsBuffer.clear()
            autoResumeTimerStartMs = null
            updateTrackingState { it.copy(isPaused = true) }
        }
    }

    private fun addTrackPoint(point: TrackPoint) {
        val prev = recentRidePoints.lastOrNull()
        if (prev != null) {
            trackLengthMeters += distanceMeters(prev.latitude, prev.longitude, point.latitude, point.longitude)
        }
        ridePointCount++
        rideSumSpeedKmh += point.speedKmh
        rideSumAbsLeanDeg += abs(point.leanAngleDeg)
        
        recentRidePoints.addLast(point)
        if (recentRidePoints.size > LIVE_POINTS_UI_LIMIT) {
            recentRidePoints.removeFirst()
        }
    }

    fun finishRide() {
        isCheckingForExtension = false
        val currentRideId = activeRideId
        val started = activeRideStartedMs ?: System.currentTimeMillis()
        val ended = System.currentTimeMillis()
        
        if (currentRideId != null) {
            if (ridePointCount > 0) {
                val skeleton = RideSummary(
                    startedAtMs = started,
                    endedAtMs = ended,
                    isSkeleton = true
                )
                _uiState.update { it.copy(
                    rideHistory = (listOf(skeleton) + it.rideHistory).sortedByDescending { it.startedAtMs },
                    lastSavedRideId = started
                ) }

                viewModelScope.launch(Dispatchers.IO) {
                    delay(2000) 
                    // Load points once from DB to finalize the session (route description etc)
                    val allPoints = rideRepository.loadFullSession(currentRideId)?.points ?: emptyList()
                    val newSession = rideSessionUseCases.saveFinishedRide(currentRideId, started, ended, allPoints)
                    
                    launch(Dispatchers.Main) {
                        _uiState.update { state ->
                            state.copy(
                                rideHistory = state.rideHistory.map {
                                    if (it.startedAtMs == started) newSession.toSummary() else it
                                },
                                expandedRides = state.expandedRides + (started to newSession)
                            )
                        }
                    }
                }
            } else {
                // No points stored, delete the empty ride record
                viewModelScope.launch(Dispatchers.IO) {
                    rideRepository.deleteRide(currentRideId)
                }
            }
        }
        
        stopLocationUpdates()
        stopRecorder()
        recentRidePoints.clear()
        pausedPointsBuffer.clear()
        activeRideId = null
        activeRideStartedMs = null
        accumulatedTimeMs = 0L
        latestGpsLocation = null
        latestGpsTimestampNs = null
        speedKmh = 0f
        trackLengthMeters = 0f
        ridePointCount = 0
        rideSumSpeedKmh = 0f
        rideSumAbsLeanDeg = 0f
        autoResumeTimerStartMs = null
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
                averageLeanAngleDeg = 0f,
                isUpsideDown = false,
                recentPoints = emptyList()
            )
        }
    }

    fun deleteRide(summary: RideSummary) {
        viewModelScope.launch(Dispatchers.IO) {
            rideSessionUseCases.deleteRide(summary.startedAtMs)
            launch(Dispatchers.Main) {
                _uiState.update { it.copy(
                    rideHistory = it.rideHistory.filter { it.startedAtMs != summary.startedAtMs },
                    expandedRides = it.expandedRides - summary.startedAtMs
                ) }
            }
        }
    }

    fun updateRideName(summary: RideSummary, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = rideSessionUseCases.updateRideName(summary.startedAtMs, newName) ?: return@launch
                launch(Dispatchers.Main) {
                    _uiState.update { state ->
                        state.copy(
                            rideHistory = state.rideHistory.map {
                                if (it.startedAtMs == summary.startedAtMs) updated.toSummary() else it
                            },
                            expandedRides = if (state.expandedRides.containsKey(summary.startedAtMs)) {
                                state.expandedRides + (summary.startedAtMs to updated)
                            } else state.expandedRides
                        )
                    }
                }
        }
    }

    fun loadFullSession(startedAtMs: Long) {
        if (_uiState.value.expandedRides.containsKey(startedAtMs)) return
        viewModelScope.launch(Dispatchers.IO) {
            val session = rideSessionUseCases.loadSession(startedAtMs) ?: return@launch
            launch(Dispatchers.Main) {
                _uiState.update { it.copy(expandedRides = it.expandedRides + (startedAtMs to session)) }
            }
        }
    }

    fun combineRides(summaries: List<RideSummary>) {
        if (summaries.size < 2) return
        viewModelScope.launch(Dispatchers.IO) {
            val newSession = rideSessionUseCases.combineRides(summaries.map { it.startedAtMs }) ?: return@launch
            
            val idsToRemove = summaries.map { it.startedAtMs }.toSet()
            launch(Dispatchers.Main) {
                _uiState.update { it.copy(
                    rideHistory = (listOf(newSession.toSummary()) + it.rideHistory.filter { it.startedAtMs !in idsToRemove })
                        .sortedByDescending { it.startedAtMs },
                    expandedRides = it.expandedRides.filterKeys { it !in idsToRemove }
                ) }
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

        _uiState.update { it.copy(
            settings = it.settings.copy(invertLeanAngle = invert),
            tracking = it.tracking.copy(
                leanAngleDeg = -it.tracking.leanAngleDeg,
                maxLeftDeg = -it.tracking.maxRightDeg,
                maxRightDeg = -it.tracking.maxLeftDeg,
                leanHistoryDeg = transformedHistory
            )
        ) }
        persistSettings()
    }

    fun setHistoryWindowSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        val previous = _uiState.value
        if (previous.settings.historyWindowSeconds == clamped) return

        leanHistory.lastOrNull()?.let { pruneHistory(it.timestampNs, clamped) }
        _uiState.update { it.copy(
            settings = it.settings.copy(historyWindowSeconds = clamped),
            tracking = it.tracking.copy(leanHistoryDeg = leanHistory.map { it.valueDeg })
        ) }
        persistSettings()
    }

    fun setRecorderIntervalMs(intervalMs: Int) {
        val stepped = (intervalMs / RECORDER_INTERVAL_STEP_MS) * RECORDER_INTERVAL_STEP_MS
        val clamped = stepped.coerceIn(RECORDER_INTERVAL_MIN_MS, RECORDER_INTERVAL_MAX_MS)
        val previous = _uiState.value
        if (previous.settings.recorderIntervalMs == clamped) return

        _uiState.update { it.copy(settings = it.settings.copy(recorderIntervalMs = clamped)) }
        persistSettings()
    }

    fun setAutoResumeEnabled(enabled: Boolean) {
        updateSettingsState { it.copy(autoResumeEnabled = enabled) }
        if (!enabled) {
            pausedPointsBuffer.clear()
            autoResumeTimerStartMs = null
        }
        persistSettings()
    }

    fun purchaseAutoResume() {
        updateSettingsState { it.copy(isAutoResumePurchased = true, autoResumeEnabled = true) }
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

        if (abs(leanDeg) > abs(peakLeanSinceLastTick)) {
            peakLeanSinceLastTick = leanDeg
        }

        // Auto pause if lean angle is too high (likely crash or extreme event)
        if (abs(leanDeg) >= AUTO_PAUSE_LEAN_THRESHOLD) {
            val currentState = _uiState.value.tracking
            if (currentState.trackingStarted && !currentState.isPaused) {
                togglePauseTracking()
            }
        }

        leanHistory += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)
        registerRecentLeanSample(timestampNs, leanDeg)

        val previous = _uiState.value
        pruneHistory(timestampNs, previous.settings.historyWindowSeconds)

        val visibleHistory = leanHistory.map { it.valueDeg }

        // Detect upside down: if gravity points in same direction as uprightUp (captured as -gravity)
        val upsideDown = filteredGravity.dot(upRef) > 5.0f

        updateTrackingState {
            it.copy(
                leanAngleDeg = leanDeg,
                maxLeftDeg = minOf(it.maxLeftDeg, if (leanDeg < 0f) leanDeg else 0f),
                maxRightDeg = maxOf(it.maxRightDeg, if (leanDeg > 0f) leanDeg else 0f),
                leanHistoryDeg = visibleHistory,
                speedKmh = speedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = ridePointCount > 0,
                currentLatitude = latestGpsLocation?.latitude,
                currentLongitude = latestGpsLocation?.longitude,
                isUpsideDown = upsideDown,
                recentPoints = recentRidePoints.toList()
            )
        }
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
        if (location.hasAccuracy() && location.accuracy > 60f) {
            return
        }

        latestGpsLocation = location
        latestGpsTimestampNs = location.elapsedRealtimeNanos
        speedKmh = (location.speed * 3.6f).coerceAtLeast(0f)

        if (isCheckingForExtension) {
            isCheckingForExtension = false
            val lastSummary = _uiState.value.rideHistory.firstOrNull()
            if (lastSummary != null) {
                viewModelScope.launch(Dispatchers.IO) {
                    val lastRide = rideSessionUseCases.loadSession(lastSummary.startedAtMs)
                    if (lastRide != null) {
                        val lastPoint = lastRide.points.lastOrNull()
                        if (lastPoint != null) {
                            val dist = distanceMeters(lastPoint.latitude, lastPoint.longitude, location.latitude, location.longitude)
                            if (dist < EXTEND_PROXIMITY_METERS) {
                                launch(Dispatchers.Main) {
                                    _uiState.update { it.copy(offerExtendSession = lastRide) }
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
                viewModelScope.launch { performStartNewRide() }
            }
        }

        val state = _uiState.value
        if (state.settings.gpsTrackingEnabled) {
            if (state.tracking.trackingStarted && activeRideStartedMs == null && !isCheckingForExtension) {
                viewModelScope.launch {
                    val startTime = System.currentTimeMillis()
                    activeRideStartedMs = startTime
                    activeRideId = rideRepository.startNewRide(startTime)
                    lastResumeMs = startTime
                }
            }

            // Auto Resume Logic
            if (state.tracking.trackingStarted && state.tracking.isPaused && state.settings.autoResumeEnabled) {
                if (speedKmh >= AUTO_REWIND_SPEED_THRESHOLD_KMH) {
                    val now = System.currentTimeMillis()
                    val start = autoResumeTimerStartMs ?: now.also { autoResumeTimerStartMs = it }
                    if (now - start >= AUTO_REWIND_DURATION_MS) {
                        autoResumeTimerStartMs = null
                        togglePauseTracking()
                    }
                } else {
                    autoResumeTimerStartMs = null
                }
            }

            if (!state.tracking.trackingStarted) {
                updateTrackingState { it.copy(speedKmh = speedKmh, gpsActive = locationUpdatesRunning, currentLatitude = location.latitude, currentLongitude = location.longitude) }
                return
            }
        }

        updateTrackingState {
            it.copy(
                speedKmh = speedKmh,
                gpsActive = locationUpdatesRunning,
                hasTrackData = ridePointCount > 0,
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
        if (!state.settings.gpsTrackingEnabled || !state.tracking.trackingStarted || isCheckingForExtension) return

        val gps = latestGpsLocation ?: return
        val nowNs = SystemClock.elapsedRealtimeNanos()
        val leanAgeMs = latestLeanTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
            ?: Long.MAX_VALUE
        val gpsAgeMs = latestGpsTimestampNs?.let { ((nowNs - it).coerceAtLeast(0L)) / 1_000_000L }
            ?: Long.MAX_VALUE
        val fusedSpeedKmh = (gps.speed * 3.6f).coerceAtLeast(0f)
        speedKmh = fusedSpeedKmh

        val nowMs = System.currentTimeMillis()
        
        val recordedLean = if (peakLeanSinceLastTick != 0f) peakLeanSinceLastTick else latestLeanDeg
        
        val point = TrackPoint(
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

        if (state.tracking.isPaused) {
            if (state.settings.autoResumeEnabled) {
                pausedPointsBuffer.addLast(point)
                val cutoff = nowMs - 30_000L
                while (pausedPointsBuffer.isNotEmpty() && pausedPointsBuffer.first().timestampMs < cutoff) {
                    pausedPointsBuffer.removeFirst()
                }
            }
            return
        }

        // Live stats update & Local buffer update
        addTrackPoint(point)
        
        // Immediate persistence
        activeRideId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                rideRepository.recordPoint(point, id)
            }
        }
        
        peakLeanSinceLastTick = latestLeanDeg

        val elapsedMs = accumulatedTimeMs + (nowMs - lastResumeMs)
        val avgSpeed = if (ridePointCount > 0) rideSumSpeedKmh / ridePointCount else 0f
        val avgLean = if (ridePointCount > 0) rideSumAbsLeanDeg / ridePointCount else 0f

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
                averageLeanAngleDeg = avgLean,
                recentPoints = recentRidePoints.toList()
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
