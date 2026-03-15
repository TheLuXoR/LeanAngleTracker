package com.example.leanangletracker

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

enum class CalibrationStep {
    UPRIGHT,
    LEFT_READY,
    LEFT_MEASURING,
    RIGHT_READY,
    RIGHT_MEASURING,
    READY
}

data class CalibrationUiState(
    val calibrationStep: CalibrationStep = CalibrationStep.UPRIGHT,
    @StringRes val instructionsResId: Int = R.string.instructions_upright,
    val isCalibrated: Boolean = false,
    @StringRes val qualityHintResId: Int? = R.string.hint_operate_only_zero_position,
    val leftCalibrationAmplitudeDeg: Float = 0f,
    val rightCalibrationAmplitudeDeg: Float = 0f,
    val currentStepAmplitudeDeg: Float = 0f
)

data class TrackingUiState(
    val leanAngleDeg: Float = 0f,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val leanHistoryDeg: List<Float> = emptyList()
)

data class SettingsUiState(
    val invertLeanAngle: Boolean = true,
    val historyWindowSeconds: Int = 20,
    val useGyroFusion: Boolean = false,
    val gyroscopeAvailable: Boolean = false
)

data class UiState(
    val calibration: CalibrationUiState = CalibrationUiState(),
    val tracking: TrackingUiState = TrackingUiState(),
    val settings: SettingsUiState = SettingsUiState()
)

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscopeSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

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

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroscopeSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        updateSettingsState {
            it.copy(gyroscopeAvailable = gyroscopeSensor != null)
        }

        if (gravitySensor == null) {
            updateCalibrationState { it.copy(instructionsResId = R.string.instructions_sensor_missing) }
        }
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

        updateCalibrationState {
            it.copy(
                calibrationStep = CalibrationStep.UPRIGHT,
                isCalibrated = false,
                qualityHintResId = R.string.hint_operate_only_zero_position,
                instructionsResId = R.string.instructions_upright,
                leftCalibrationAmplitudeDeg = 0f,
                rightCalibrationAmplitudeDeg = 0f,
                currentStepAmplitudeDeg = 0f
            )
        }
        updateTrackingState {
            it.copy(
                leanAngleDeg = 0f,
                maxLeftDeg = 0f,
                maxRightDeg = 0f,
                leanHistoryDeg = emptyList()
            )
        }
    }

    fun captureUpright() {
        if (_uiState.value.calibration.calibrationStep != CalibrationStep.UPRIGHT) return
        uprightUp = (filteredGravity * -1f).normalized()

        updateCalibrationState {
            it.copy(
                calibrationStep = CalibrationStep.LEFT_READY,
                instructionsResId = R.string.instructions_start_left_measurement,
                qualityHintResId = R.string.hint_after_start_tilt_left
            )
        }
    }

    fun startLeftMeasurement() {
        if (_uiState.value.calibration.calibrationStep != CalibrationStep.LEFT_READY) return
        prepareMeasurementStep(
            nextStep = CalibrationStep.LEFT_MEASURING,
            instructionResId = R.string.instructions_tilt_left_then_return
        )
    }

    fun startRightMeasurement() {
        if (_uiState.value.calibration.calibrationStep != CalibrationStep.RIGHT_READY) return
        prepareMeasurementStep(
            nextStep = CalibrationStep.RIGHT_MEASURING,
            instructionResId = R.string.instructions_tilt_right_then_return
        )
    }

    private fun prepareMeasurementStep(nextStep: CalibrationStep, @StringRes instructionResId: Int) {
        peakTiltDegInStep = 0f
        peakTiltVectorInStep = null
        uprightStableCounter = 0
        updateCalibrationState {
            it.copy(
                calibrationStep = nextStep,
                instructionsResId = instructionResId,
                currentStepAmplitudeDeg = 0f,
                qualityHintResId = null
            )
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
    }

    fun setUseGyroFusion(enabled: Boolean) {
        val shouldUse = enabled && gyroscopeSensor != null
        val current = _uiState.value
        _uiState.value = current.copy(settings = current.settings.copy(useGyroFusion = shouldUse))
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
                    CalibrationStep.LEFT_MEASURING,
                    CalibrationStep.RIGHT_MEASURING -> handleMeasurementStep()

                    CalibrationStep.READY -> updateLeanAngle(event.timestamp)
                    else -> Unit
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

        updateCalibrationState { it.copy(currentStepAmplitudeDeg = peakTiltDegInStep) }

        if (angleDeg < 4f) {
            uprightStableCounter++
        } else {
            uprightStableCounter = 0
        }

        val enoughMotion = peakTiltDegInStep > 8f
        val returnedUpright = uprightStableCounter > 14
        if (!enoughMotion || !returnedUpright) return

        when (_uiState.value.calibration.calibrationStep) {
            CalibrationStep.LEFT_MEASURING -> {
                leftUp = peakTiltVectorInStep ?: return
                updateCalibrationState {
                    it.copy(
                        calibrationStep = CalibrationStep.RIGHT_READY,
                        instructionsResId = R.string.instructions_start_right_measurement,
                        qualityHintResId = R.string.hint_after_start_tilt_right,
                        leftCalibrationAmplitudeDeg = peakTiltDegInStep,
                        currentStepAmplitudeDeg = 0f
                    )
                }
            }

            CalibrationStep.RIGHT_MEASURING -> {
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
            updateCalibrationState {
                it.copy(
                    qualityHintResId = R.string.hint_calibration_uncertain,
                    calibrationStep = CalibrationStep.LEFT_READY,
                    instructionsResId = R.string.instructions_start_left_measurement,
                    currentStepAmplitudeDeg = 0f
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
                calibrationStep = CalibrationStep.READY,
                isCalibrated = true,
                instructionsResId = R.string.instructions_calibrated,
                qualityHintResId = null,
                rightCalibrationAmplitudeDeg = rightAmplitudeDeg,
                currentStepAmplitudeDeg = 0f
            )
        }
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
                leanHistoryDeg = visibleHistory
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
        if (_uiState.value.calibration.calibrationStep != CalibrationStep.READY) {
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
