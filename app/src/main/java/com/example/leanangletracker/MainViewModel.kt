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
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.leanangletracker.data.RideRepository
import com.example.leanangletracker.data.Vec3
import com.example.leanangletracker.domain.RideSessionUseCases
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

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener, LocationListener {

    companion object {
        const val TAG = MainViewModelConfig.TAG
        const val RECORDER_INTERVAL_MIN_MS = MainViewModelConfig.RECORDER_INTERVAL_MIN_MS
        const val RECORDER_INTERVAL_MAX_MS = MainViewModelConfig.RECORDER_INTERVAL_MAX_MS
        const val RECORDER_INTERVAL_STEP_MS = MainViewModelConfig.RECORDER_INTERVAL_STEP_MS
        const val GPS_FRESHNESS_THRESHOLD_MS = MainViewModelConfig.GPS_FRESHNESS_THRESHOLD_MS
        const val CALIBRATION_TILT_MAX_RANGE = MainViewModelConfig.CALIBRATION_TILT_MAX_RANGE
        const val EXTEND_PROXIMITY_METERS = MainViewModelConfig.EXTEND_PROXIMITY_METERS
        const val MAX_LEAN_DEG = MainViewModelConfig.MAX_LEAN_DEG
        const val AUTO_PAUSE_LEAN_THRESHOLD = MainViewModelConfig.AUTO_PAUSE_LEAN_THRESHOLD
        const val MAX_ROLL_RATE_RAD_PER_SEC = MainViewModelConfig.MAX_ROLL_RATE_RAD_PER_SEC
        const val GYRO_REVERSAL_DAMPING = MainViewModelConfig.GYRO_REVERSAL_DAMPING
        const val GYRO_BIAS_COLLECTION_DURATION_NS = MainViewModelConfig.GYRO_BIAS_COLLECTION_DURATION_NS
        const val GYRO_BIAS_LINEAR_ACCEL_MAX = MainViewModelConfig.GYRO_BIAS_LINEAR_ACCEL_MAX
        const val GYRO_BIAS_ANGULAR_SPEED_MAX = MainViewModelConfig.GYRO_BIAS_ANGULAR_SPEED_MAX
        const val OBSERVABILITY_LINEAR_ACCEL_MAX = MainViewModelConfig.OBSERVABILITY_LINEAR_ACCEL_MAX
        const val OBSERVABILITY_INNOVATION_MAX_DEG = MainViewModelConfig.OBSERVABILITY_INNOVATION_MAX_DEG
        const val OBSERVABILITY_ROLL_RATE_MAX_RAD_PER_SEC = MainViewModelConfig.OBSERVABILITY_ROLL_RATE_MAX_RAD_PER_SEC
        const val LATERAL_ACCEL_GATING_FULL_MS2 = MainViewModelConfig.LATERAL_ACCEL_GATING_FULL_MS2
        const val MAX_OUTPUT_SLEW_RATE_DEG_PER_SEC = MainViewModelConfig.MAX_OUTPUT_SLEW_RATE_DEG_PER_SEC
        const val RECENT_LEAN_BUFFER_SIZE = MainViewModelConfig.RECENT_LEAN_BUFFER_SIZE
        const val AUTO_REWIND_SPEED_THRESHOLD_KMH = MainViewModelConfig.AUTO_REWIND_SPEED_THRESHOLD_KMH
        const val AUTO_REWIND_DURATION_MS = MainViewModelConfig.AUTO_REWIND_DURATION_MS
        const val LIVE_POINTS_UI_LIMIT = MainViewModelConfig.LIVE_POINTS_UI_LIMIT

        val SENSOR_TIMING_POLICY = MainViewModelConfig.SENSOR_TIMING_POLICY
    }


    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    private val linearAccelerationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    internal val locationManager = application.getSystemService(LocationManager::class.java)
    internal val rideRepository = RideRepository(application)
    internal val rideSessionUseCases = RideSessionUseCases(application, rideRepository)

    internal val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    internal val settingsStore = SettingsStore(application.applicationContext)

    internal var filteredGravity = Vec3(0f, 0f, 9.81f)
    internal var filteredLinearAcceleration = Vec3(0f, 0f, 0f)

    internal var uprightUp: Vec3? = null
    internal var leftUpPeak: Vec3? = null
    internal var rightUpPeak: Vec3? = null
    internal var bikeForwardAxis: Vec3? = null
    internal var calibrationSideAxis: Vec3? = null

    internal var gyroLeanDeg = 0f
    internal var gyroBiasRadPerSec = 0f
    internal var gyroBiasVectorRadPerSec: Vec3? = null
    internal var gyroBiasCollectionStartNs: Long? = null
    internal var gyroBiasAccumulated = Vec3(0f, 0f, 0f)
    internal var gyroBiasSampleCount = 0
    internal var lastGyroTimestampNs: Long? = null
    internal var lastRollRateRadPerSec = 0f
    internal var lastYawRateRadPerSec = 0f
    internal var latestRotationLeanDeg: Float? = null
    internal var latestRotationTimestampNs: Long? = null
    internal var lastLeanComputationTimestampNs: Long? = null
    internal var speedKmh = 0f
    internal var activeRideId: Long? = null
    internal var activeRideStartedMs: Long? = null
    internal var accumulatedTimeMs: Long = 0L
    internal var lastResumeMs: Long = 0L
    internal var locationUpdatesRunning = false
    internal var latestLeanDeg = 0f
    internal var peakLeanSinceLastTick = 0f
    internal var latestLeanTimestampNs: Long? = null
    internal var latestGpsLocation: Location? = null
    internal var latestGpsTimestampNs: Long? = null
    internal var recorderJob: Job? = null
    internal var trackLengthMeters = 0f
    internal var isCheckingForExtension = false
    internal var latestLinearAccelerationMagnitude = 0f
    internal var fusionConfidence = 1f
    internal var autoResumeTimerStartMs: Long? = null
    internal val pausedPointsBuffer = ArrayDeque<TrackPoint>()
    internal var highRotationStartNs: Long? = null

    // Rolling ride statistics to avoid keeping all points in memory
    internal var ridePointCount = 0
    internal var rideSumSpeedKmh = 0f
    internal var rideSumAbsLeanDeg = 0f
    internal val recentRidePoints = ArrayDeque<TrackPoint>()

    internal val leanHistory = ArrayDeque<TimedLean>()
    internal val recentLeanSamples = ArrayDeque<TimedLean>()

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










    internal fun hasLocationPermission(): Boolean {
        val context = getApplication<Application>()
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    internal fun startLocationUpdates() {
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

    internal fun stopLocationUpdates() {
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
                    val lastRide = rideSessionUseCases.loadSession(lastSummary.rideId)
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
                    settingsStore.savePendingRideId(activeRideId)
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






    override fun onCleared() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        stopRecorder()
        activeRideId?.let { id ->
            viewModelScope.launch(Dispatchers.IO) {
                rideRepository.flushRidePoints(id)
            }
        }
        super.onCleared()
    }
}
