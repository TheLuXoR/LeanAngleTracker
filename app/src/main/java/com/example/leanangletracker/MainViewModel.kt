package com.example.leanangletracker

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

private data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
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
    IDLE,
    UPRIGHT,
    WIGGLE,
    READY
}

data class UiState(
    val leanAngleDeg: Float = 0f,
    val calibrationStep: CalibrationStep = CalibrationStep.IDLE,
    val instructions: String = "Mount phone on bike, then start calibration.",
    val isCalibrated: Boolean = false,
    val qualityHint: String = "",
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val leanHistoryDeg: List<Float> = emptyList(),
    val invertLeanAngle: Boolean = true,
    val historyWindowSeconds: Int = 20
)

class MainViewModel(application: Application) : AndroidViewModel(application), SensorEventListener {

    private val sensorManager = application.getSystemService(SensorManager::class.java)
    private val gravitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var filteredGravity = Vec3(0f, 0f, 9.81f)

    private var uprightUp: Vec3? = null
    private val gyroSamples = mutableListOf<Vec3>()
    private var bikeForwardAxis: Vec3? = null

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)

    private val leanHistory = ArrayDeque<TimedLean>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        if (gravitySensor == null || gyroSensor == null) {
            _uiState.value = _uiState.value.copy(
                instructions = "Required sensors not available on this phone."
            )
        }
    }

    fun startCalibration() {
        uprightUp = null
        bikeForwardAxis = null
        gyroSamples.clear()
        leanHistory.clear()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.UPRIGHT,
            isCalibrated = false,
            leanAngleDeg = 0f,
            maxLeftDeg = 0f,
            maxRightDeg = 0f,
            leanHistoryDeg = emptyList(),
            qualityHint = "",
            instructions = "Step 1/2: Hold bike upright and still. Tap 'Capture Upright'."
        )
    }

    fun captureUpright() {
        if (_uiState.value.calibrationStep != CalibrationStep.UPRIGHT) return
        uprightUp = (filteredGravity * -1f).normalized()
        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.WIGGLE,
            instructions = "Step 2/2: Lean bike left-right a few times. Tap 'Finish Calibration'."
        )
    }

    fun finishCalibration() {
        if (_uiState.value.calibrationStep != CalibrationStep.WIGGLE) return
        val up = uprightUp ?: return

        if (gyroSamples.size < 20) {
            _uiState.value = _uiState.value.copy(
                qualityHint = "Not enough motion captured. Wiggle more and try again."
            )
            return
        }

        val principal = principalAxisFromGyro(gyroSamples)
        val forward = if (abs(principal.dot(up)) > 0.9f) null else principal.normalized()

        if (forward == null) {
            _uiState.value = _uiState.value.copy(
                qualityHint = "Wiggle motion ambiguous. Try larger side-to-side lean."
            )
            return
        }

        bikeForwardAxis = forward
        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.READY,
            isCalibrated = true,
            instructions = "Calibrated. Ride safe! Lean angle is now live.",
            qualityHint = "Calibration complete."
        )
    }

    fun setInvertLeanAngle(invert: Boolean) {
        val previous = _uiState.value
        if (previous.invertLeanAngle == invert) return

        val transformedHistory = previous.leanHistoryDeg.map { -it }
        val transformedCurrent = -previous.leanAngleDeg

        _uiState.value = previous.copy(
            invertLeanAngle = invert,
            leanAngleDeg = transformedCurrent,
            maxLeftDeg = -previous.maxRightDeg,
            maxRightDeg = -previous.maxLeftDeg,
            leanHistoryDeg = transformedHistory
        )

        val transformedTimedHistory = leanHistory.map { it.copy(valueDeg = -it.valueDeg) }
        leanHistory.clear()
        leanHistory.addAll(transformedTimedHistory)
    }

    fun setHistoryWindowSeconds(seconds: Int) {
        val clamped = seconds.coerceIn(5, 120)
        val previous = _uiState.value
        if (previous.historyWindowSeconds == clamped) return

        val currentTimestamp = leanHistory.lastOrNull()?.timestampNs
        if (currentTimestamp != null) {
            pruneHistory(currentTimestamp, clamped)
        }

        val historyValues = leanHistory.map { it.valueDeg }
        val recalculatedLeft = historyValues.minOrNull()?.coerceAtMost(0f) ?: 0f
        val recalculatedRight = historyValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f

        _uiState.value = previous.copy(
            historyWindowSeconds = clamped,
            leanHistoryDeg = historyValues,
            maxLeftDeg = minOf(previous.maxLeftDeg, recalculatedLeft),
            maxRightDeg = maxOf(previous.maxRightDeg, recalculatedRight)
        )
    }

    fun resetCalibration() {
        uprightUp = null
        bikeForwardAxis = null
        gyroSamples.clear()
        leanHistory.clear()
        _uiState.value = UiState(
            invertLeanAngle = _uiState.value.invertLeanAngle,
            historyWindowSeconds = _uiState.value.historyWindowSeconds
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                val alpha = 0.92f
                val raw = Vec3(event.values[0], event.values[1], event.values[2])
                filteredGravity = filteredGravity * alpha + raw * (1f - alpha)
                updateLeanAngle(event.timestamp)
            }

            Sensor.TYPE_GYROSCOPE -> {
                if (_uiState.value.calibrationStep == CalibrationStep.WIGGLE) {
                    gyroSamples += Vec3(event.values[0], event.values[1], event.values[2])
                    if (gyroSamples.size > 2000) gyroSamples.removeFirst()
                }
            }
        }
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

    private fun principalAxisFromGyro(samples: List<Vec3>): Vec3 {
        var axis = Vec3(1f, 0f, 0f)
        repeat(16) {
            var next = Vec3(0f, 0f, 0f)
            for (s in samples) {
                val p = axis.dot(s)
                next += s * p
            }
            axis = next.normalized()
        }
        return axis
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
