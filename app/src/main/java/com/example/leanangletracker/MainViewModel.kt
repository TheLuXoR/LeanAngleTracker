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
import kotlin.math.abs
import kotlin.math.acos
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
    @StringRes val qualityHintResId: Int? = R.string.hint_operate_only_zero_position,
    val leftProgress: Float = 0f,
    val leftMax: Float = 0f,
    val rightProgress: Float = 0f,
    val rightMax: Float = 0f,
    val tiltRecognitionProgress: Float = 0f,
    val uprightRecognitionProgress: Float = 0f
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
    val points: List<TrackPoint>
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
    val currentLatitude: Double? = null,
    val currentLongitude: Double? = null,
    val elapsedTimeMs: Long = 0L,
    val averageSpeedKmh: Float = 0f,
    val trackLengthKm: Float = 0f,
    val averageLeanAngleDeg: Float = 0f
)

data class SettingsUiState(
    val invertLeanAngle: Boolean = true,
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
    val rideHistory: List<RideSession> = emptyList()
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
    }

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val locationManager = application.getSystemService(LocationManager::class.java)
    private val rideRepository = RideRepository(application)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private var filteredGravity = Vec3(0f, 0f, 9.81f)

    private var uprightUp: Vec3? = null
    private var leftUp: Vec3? = null
    private var rightUp: Vec3? = null
    private var bikeForwardAxis: Vec3? = null

    private var peakTiltDegInStep = 0f
    private var peakTiltVectorInStep: Vec3? = null
    private var uprightStableCounter = 0
    private var gyroLeanDeg = 0f
    private var lastGyroTimestampNs: Long? = null
    private var speedKmh = 0f
    private var activeRideStartedMs: Long? = null
    private var locationUpdatesRunning = false
    private var latestLeanDeg = 0f
    private var latestLeanTimestampNs: Long? = null
    private var latestGpsLocation: Location? = null
    private var latestGpsTimestampNs: Long? = null
    private var recorderJob: Job? = null
    private var trackLengthMeters = 0f

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()
    private val ridePoints = mutableListOf<TrackPoint>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        updateSettingsState {
            it.copy(
                gyroscopeAvailable = gyroscopeSensor != null,
                locationPermissionGranted = hasLocationPermission()
            )
        }

        loadPersistedState()

        if (gravitySensor == null) {
            updateCalibrationState { it.copy(instructionsResId = R.string.instructions_sensor_missing) }
        }
    }

    private fun loadPersistedState() {
        val savedInvert = prefs.getBoolean(KEY_INVERT, true)
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

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated,
                qualityHintResId = null
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
            latestGpsLocation = null
            latestGpsTimestampNs = null
            speedKmh = 0f
            trackLengthMeters = 0f
            stopRecorder()
            updateTrackingState {
                it.copy(
                    speedKmh = 0f,
                    gpsActive = false,
                    hasTrackData = false,
                    trackingStarted = false,
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
        activeRideStartedMs = System.currentTimeMillis()
        startRecorder()
        updateTrackingState { it.copy(trackingStarted = true, gpsTrackingEnabled = true) }
    }

    fun finishRide() {
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
                rideHistory = listOf(newSession) + _uiState.value.rideHistory
            )
        }
        stopLocationUpdates()
        stopRecorder()
        ridePoints.clear()
        activeRideStartedMs = null
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

    fun startCalibration() {
        uprightUp = null
        leftUp = null
        rightUp = null
        bikeForwardAxis = null
        peakTiltDegInStep = 0f
        peakTiltVectorInStep = null
        uprightStableCounter = 0
        gyroLeanDeg = 0f
        lastGyroTimestampNs = null
        leanHistory.clear()
        ridePoints.clear()
        activeRideStartedMs = null
        latestGpsLocation = null
        latestGpsTimestampNs = null
        trackLengthMeters = 0f
        stopRecorder()
        clearPersistedCalibration()

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.UPRIGHT,
                isCalibrated = false,
                qualityHintResId = R.string.hint_operate_only_zero_position,
                instructionsResId = R.string.instructions_upright,
                leftProgress = 0f,
                leftMax = 0f,
                rightProgress = 0f,
                rightMax = 0f,
                tiltRecognitionProgress = 0f,
                uprightRecognitionProgress = 0f
            )
        }
    }

    fun captureUpright() {
        if (_uiState.value.calibration.calibrationStep != BikeLean.UPRIGHT) return
        uprightUp = (filteredGravity * -1f).normalized()
        prepareMeasurementStep(
            nextStep = BikeLean.LEFT,
            instructionResId = R.string.instructions_tilt_left_then_return
        )
    }

    private fun prepareMeasurementStep(nextStep: BikeLean, @StringRes instructionResId: Int) {
        peakTiltDegInStep = 0f
        peakTiltVectorInStep = null
        uprightStableCounter = 0
        updateCalibrationState {
            it.copy(
                calibrationStep = nextStep,
                instructionsResId = instructionResId,
                tiltRecognitionProgress = 0f,
                uprightRecognitionProgress = 0f,
                qualityHintResId = null
            )
        }
    }

    fun continueCalibrationFallback() {
        when (_uiState.value.calibration.calibrationStep) {
            BikeLean.LEFT -> {
                val currentUp = (filteredGravity * -1f).normalized()
                leftUp = peakTiltVectorInStep ?: currentUp
                val leftAmplitude = peakTiltDegInStep
                updateCalibrationState {
                    it.copy(
                        leftMax = (leftAmplitude / 8f).coerceIn(0f, 1f),
                        leftProgress = 0f
                    )
                }
                prepareMeasurementStep(
                    nextStep = BikeLean.RIGHT,
                    instructionResId = R.string.instructions_tilt_right_then_return
                )
            }

            BikeLean.RIGHT -> {
                val currentUp = (filteredGravity * -1f).normalized()
                rightUp = peakTiltVectorInStep ?: currentUp
                finalizeCalibration(peakTiltDegInStep)
            }

            else -> Unit
        }
    }

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

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val alpha = 0.92f
                val raw = Vec3(event.values[0], event.values[1], event.values[2])
                filteredGravity = filteredGravity * alpha + raw * (1f - alpha)

                when (_uiState.value.calibration.calibrationStep) {
                    BikeLean.LEFT,
                    BikeLean.RIGHT -> handleMeasurementStep()
                    BikeLean.UPRIGHT,
                    BikeLean.DONE -> updateLeanAngle(event.timestamp)
                }
            }

            Sensor.TYPE_GYROSCOPE -> updateGyroLean(event)
        }
    }

    private fun handleMeasurementStep() {
        val up = uprightUp ?: return
        val currentUp = (filteredGravity * -1f).normalized()
        val angleDeg = angleBetweenDeg(up, currentUp)

        if (angleDeg > peakTiltDegInStep) {
            peakTiltDegInStep = angleDeg
            peakTiltVectorInStep = currentUp
        }

        val currentProgress = (angleDeg / 8f).coerceIn(0f, 1f)
        val peakProgress = (peakTiltDegInStep / 8f).coerceIn(0f, 1f)

        if (angleDeg < 4f) {
            uprightStableCounter++
        } else {
            uprightStableCounter = 0
        }

        updateCalibrationState {
            val step = it.calibrationStep
            it.copy(
                leftProgress = if (step == BikeLean.LEFT) currentProgress else it.leftProgress,
                leftMax = if (step == BikeLean.LEFT) peakProgress else it.leftMax,
                rightProgress = if (step == BikeLean.RIGHT) currentProgress else it.rightProgress,
                rightMax = if (step == BikeLean.RIGHT) peakProgress else it.rightMax,
                tiltRecognitionProgress = peakProgress,
                uprightRecognitionProgress = (uprightStableCounter / 15f).coerceIn(0f, 1f)
            )
        }

        val enoughMotion = peakTiltDegInStep > 8f
        val returnedUpright = uprightStableCounter > 14
        if (!enoughMotion || !returnedUpright) return

        when (_uiState.value.calibration.calibrationStep) {
            BikeLean.LEFT -> {
                leftUp = peakTiltVectorInStep ?: return
                prepareMeasurementStep(
                    nextStep = BikeLean.RIGHT,
                    instructionResId = R.string.instructions_tilt_right_then_return
                )
            }

            BikeLean.RIGHT -> {
                rightUp = peakTiltVectorInStep ?: return
                finalizeCalibration(peakTiltDegInStep)
            }

            else -> Unit
        }
    }

    private fun finalizeCalibration(rightAmplitudeDeg: Float) {
        val up = uprightUp ?: return
        val left = leftUp ?: return
        val right = rightUp ?: return

        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f || abs(forward.dot(up)) > 0.85f) {
            peakTiltDegInStep = 0f
            peakTiltVectorInStep = null
            uprightStableCounter = 0
            updateCalibrationState {
                it.copy(
                    qualityHintResId = R.string.hint_calibration_uncertain,
                    calibrationStep = BikeLean.LEFT,
                    instructionsResId = R.string.instructions_tilt_left_then_return,
                    tiltRecognitionProgress = 0f,
                    uprightRecognitionProgress = 0f,
                    leftProgress = 0f,
                    leftMax = 0f,
                    rightProgress = 0f,
                    rightMax = 0f
                )
            }
            return
        }

        forward = (forward - up * forward.dot(up)).normalized()
        bikeForwardAxis = forward
        gyroLeanDeg = 0f
        lastGyroTimestampNs = null

        updateCalibrationState {
            it.copy(
                calibrationStep = BikeLean.DONE,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated,
                qualityHintResId = null,
                tiltRecognitionProgress = 0f,
                uprightRecognitionProgress = 0f
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
        val accelLeanDeg = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-75f, 75f)

        val useFusion = _uiState.value.settings.useGyroFusion && gyroscopeSensor != null
        val fusedLeanDeg = if (useFusion) {
            val fused = (0.96f * gyroLeanDeg) + (0.04f * accelLeanDeg)
            gyroLeanDeg = fused.coerceIn(-75f, 75f)
            gyroLeanDeg
        } else {
            gyroLeanDeg = accelLeanDeg
            accelLeanDeg
        }

        val leanDeg = if (_uiState.value.settings.invertLeanAngle) -fusedLeanDeg else fusedLeanDeg
        latestLeanDeg = leanDeg
        latestLeanTimestampNs = timestampNs

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
            return
        }

        if (!_uiState.value.settings.useGyroFusion) {
            lastGyroTimestampNs = event.timestamp
            return
        }

        val forward = bikeForwardAxis ?: return
        val previousTimestamp = lastGyroTimestampNs
        lastGyroTimestampNs = event.timestamp
        if (previousTimestamp == null) return

        val dt = (event.timestamp - previousTimestamp) / 1_000_000_000f
        if (dt <= 0f || dt > 0.2f) return

        val omega = Vec3(event.values[0], event.values[1], event.values[2])
        val rollRateRadPerSec = omega.dot(forward)
        val deltaDeg = Math.toDegrees((rollRateRadPerSec * dt).toDouble()).toFloat()
        gyroLeanDeg = (gyroLeanDeg + deltaDeg).coerceIn(-75f, 75f)
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

        if (_uiState.value.settings.gpsTrackingEnabled) {
            if (_uiState.value.tracking.trackingStarted && activeRideStartedMs == null) {
                activeRideStartedMs = System.currentTimeMillis()
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

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
        if (!state.settings.gpsTrackingEnabled || !state.tracking.trackingStarted) return

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

        ridePoints += TrackPoint(
            timestampMs = nowMs,
            latitude = gps.latitude,
            longitude = gps.longitude,
            speedKmh = fusedSpeedKmh,
            leanAngleDeg = latestLeanDeg,
            leanFreshnessMs = leanAgeMs,
            gpsFreshnessMs = gpsAgeMs,
            hasFreshGps = gpsAgeMs <= GPS_FRESHNESS_THRESHOLD_MS,
            lapIndex = 0
        )

        val elapsedMs = activeRideStartedMs?.let { (nowMs - it).coerceAtLeast(0L) } ?: 0L
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

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        stopLocationUpdates()
        stopRecorder()
        super.onCleared()
    }
}
