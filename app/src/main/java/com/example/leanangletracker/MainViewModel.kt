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

data class UiState(
    val leanAngleDeg: Float = 0f,
    val calibrationStep: CalibrationStep = CalibrationStep.UPRIGHT,
    @StringRes val instructionsResId: Int = R.string.instructions_upright,
    val isCalibrated: Boolean = false,
    @StringRes val qualityHintResId: Int? = R.string.hint_operate_only_zero_position,
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val leanHistoryDeg: List<Float> = emptyList(),
    val invertLeanAngle: Boolean = true,
    val historyWindowSeconds: Int = 20,
    val leftCalibrationAmplitudeDeg: Float = 0f,
    val rightCalibrationAmplitudeDeg: Float = 0f,
    val currentStepAmplitudeDeg: Float = 0f
)

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

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

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        if (gravitySensor == null) {
            _uiState.value = _uiState.value.copy(
                instructionsResId = R.string.instructions_sensor_missing
            )
        }
    }

    fun startCalibration() {
        uprightUp = null
        leftUp = null
        rightUp = null
        bikeForwardAxis = null
        peakTiltDegInStep = 0f
        peakTiltVectorInStep = null
        uprightStableCounter = 0
        leanHistory.clear()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.UPRIGHT,
            isCalibrated = false,
            leanAngleDeg = 0f,
            maxLeftDeg = 0f,
            maxRightDeg = 0f,
            leanHistoryDeg = emptyList(),
            qualityHintResId = R.string.hint_operate_only_zero_position,
            instructionsResId = R.string.instructions_upright,
            leftCalibrationAmplitudeDeg = 0f,
            rightCalibrationAmplitudeDeg = 0f,
            currentStepAmplitudeDeg = 0f
        )
    }

    fun captureUpright() {
        if (_uiState.value.calibrationStep != CalibrationStep.UPRIGHT) return
        uprightUp = (filteredGravity * -1f).normalized()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.LEFT_READY,
            instructionsResId = R.string.instructions_start_left_measurement,
            qualityHintResId = R.string.hint_after_start_tilt_left
        )
    }

    fun startLeftMeasurement() {
        if (_uiState.value.calibrationStep != CalibrationStep.LEFT_READY) return
        prepareMeasurementStep(
            nextStep = CalibrationStep.LEFT_MEASURING,
            instructionResId = R.string.instructions_tilt_left_then_return
        )
    }

    fun startRightMeasurement() {
        if (_uiState.value.calibrationStep != CalibrationStep.RIGHT_READY) return
        prepareMeasurementStep(
            nextStep = CalibrationStep.RIGHT_MEASURING,
            instructionResId = R.string.instructions_tilt_right_then_return
        )
    }

    private fun prepareMeasurementStep(nextStep: CalibrationStep, @StringRes instructionResId: Int) {
        peakTiltDegInStep = 0f
        peakTiltVectorInStep = null
        uprightStableCounter = 0
        _uiState.value = _uiState.value.copy(
            calibrationStep = nextStep,
            instructionsResId = instructionResId,
            currentStepAmplitudeDeg = 0f,
            qualityHintResId = null
        )
    }

    fun setInvertLeanAngle(invert: Boolean) {
        val previous = _uiState.value
        if (previous.invertLeanAngle == invert) return

        val transformedHistory = previous.leanHistoryDeg.map { -it }
        val transformedTimedHistory = leanHistory.map { it.copy(valueDeg = -it.valueDeg) }

        leanHistory.clear()
        leanHistory.addAll(transformedTimedHistory)

        _uiState.value = previous.copy(
            invertLeanAngle = invert,
            leanAngleDeg = -previous.leanAngleDeg,
            maxLeftDeg = -previous.maxRightDeg,
            maxRightDeg = -previous.maxLeftDeg,
            leanHistoryDeg = transformedHistory
        )
    }

    fun setHistoryWindowSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        val previous = _uiState.value
        if (previous.historyWindowSeconds == clamped) return

        leanHistory.lastOrNull()?.let { pruneHistory(it.timestampNs, clamped) }
        _uiState.value = previous.copy(
            historyWindowSeconds = clamped,
            leanHistoryDeg = leanHistory.map { it.valueDeg }
        )
    }

    fun resetExtrema() {
        val historyValues = _uiState.value.leanHistoryDeg
        _uiState.value = _uiState.value.copy(
            maxLeftDeg = historyValues.minOrNull()?.coerceAtMost(0f) ?: 0f,
            maxRightDeg = historyValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val alpha = 0.92f
        val raw = Vec3(event.values[0], event.values[1], event.values[2])
        filteredGravity = filteredGravity * alpha + raw * (1f - alpha)

        when (_uiState.value.calibrationStep) {
            CalibrationStep.LEFT_MEASURING,
            CalibrationStep.RIGHT_MEASURING -> handleMeasurementStep()

            CalibrationStep.READY -> updateLeanAngle(event.timestamp)
            else -> Unit
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

        _uiState.value = _uiState.value.copy(currentStepAmplitudeDeg = peakTiltDegInStep)

        if (angleDeg < 4f) {
            uprightStableCounter++
        } else {
            uprightStableCounter = 0
        }

        val enoughMotion = peakTiltDegInStep > 8f
        val returnedUpright = uprightStableCounter > 14
        if (!enoughMotion || !returnedUpright) return

        when (_uiState.value.calibrationStep) {
            CalibrationStep.LEFT_MEASURING -> {
                leftUp = peakTiltVectorInStep ?: return
                _uiState.value = _uiState.value.copy(
                    calibrationStep = CalibrationStep.RIGHT_READY,
                    instructionsResId = R.string.instructions_start_right_measurement,
                    qualityHintResId = R.string.hint_after_start_tilt_right,
                    leftCalibrationAmplitudeDeg = peakTiltDegInStep,
                    currentStepAmplitudeDeg = 0f
                )
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
            _uiState.value = _uiState.value.copy(
                qualityHintResId = R.string.hint_calibration_uncertain,
                calibrationStep = CalibrationStep.LEFT_READY,
                instructionsResId = R.string.instructions_start_left_measurement,
                currentStepAmplitudeDeg = 0f
            )
            return
        }

        forward = (forward - up * forward.dot(up)).normalized()
        bikeForwardAxis = forward

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.READY,
            isCalibrated = true,
            instructionsResId = R.string.instructions_calibrated,
            qualityHintResId = null,
            rightCalibrationAmplitudeDeg = rightAmplitudeDeg,
            currentStepAmplitudeDeg = 0f
        )
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
        val rawLeanDeg = Math.toDegrees(leanRad.toDouble()).toFloat().coerceIn(-75f, 75f)
        val leanDeg = if (_uiState.value.invertLeanAngle) -rawLeanDeg else rawLeanDeg

        leanHistory += TimedLean(timestampNs = timestampNs, valueDeg = leanDeg)

        val previous = _uiState.value
        pruneHistory(timestampNs, previous.historyWindowSeconds)

        val visibleHistory = leanHistory.map { it.valueDeg }
        val visibleLeft = visibleHistory.minOrNull()?.coerceAtMost(0f) ?: 0f
        val visibleRight = visibleHistory.maxOrNull()?.coerceAtLeast(0f) ?: 0f

        _uiState.value = previous.copy(
            leanAngleDeg = leanDeg,
            maxLeftDeg = minOf(previous.maxLeftDeg, visibleLeft),
            maxRightDeg = maxOf(previous.maxRightDeg, visibleRight),
            leanHistoryDeg = visibleHistory
        )
    }

    private fun pruneHistory(currentTimestampNs: Long, historyWindowSeconds: Int) {
        val cutoff = currentTimestampNs - historyWindowSeconds * 1_000_000_000L
        while (leanHistory.isNotEmpty() && leanHistory.first().timestampNs < cutoff) {
            leanHistory.removeFirst()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
