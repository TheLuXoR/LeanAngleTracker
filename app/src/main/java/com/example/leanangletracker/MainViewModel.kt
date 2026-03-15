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
    LEFT,
    RIGHT,
    READY
}

data class UiState(
    val leanAngleDeg: Float = 0f,
    val calibrationStep: CalibrationStep = CalibrationStep.UPRIGHT,
    val instructions: String = "1/3: Stelle das Motorrad aufrecht hin und tippe auf \"Aufrecht erfassen\".",
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

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var filteredGravity = Vec3(0f, 0f, 9.81f)

    private var uprightUp: Vec3? = null
    private var leftUp: Vec3? = null
    private var rightUp: Vec3? = null
    private var bikeForwardAxis: Vec3? = null

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        if (gravitySensor == null) {
            _uiState.value = _uiState.value.copy(
                instructions = "Benötigter Sensor (Beschleunigung) ist auf diesem Gerät nicht verfügbar."
            )
        }
    }

    fun startCalibration() {
        uprightUp = null
        leftUp = null
        rightUp = null
        bikeForwardAxis = null
        leanHistory.clear()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.UPRIGHT,
            isCalibrated = false,
            leanAngleDeg = 0f,
            maxLeftDeg = 0f,
            maxRightDeg = 0f,
            leanHistoryDeg = emptyList(),
            qualityHint = "",
            instructions = "1/3: Stelle das Motorrad aufrecht hin und tippe auf \"Aufrecht erfassen\"."
        )
    }

    fun captureUpright() {
        if (_uiState.value.calibrationStep != CalibrationStep.UPRIGHT) return
        uprightUp = (filteredGravity * -1f).normalized()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.LEFT,
            instructions = "2/3: Neige das Motorrad nach LINKS und tippe auf \"Links erfassen\".",
            qualityHint = ""
        )
    }

    fun captureLeftLean() {
        if (_uiState.value.calibrationStep != CalibrationStep.LEFT) return
        leftUp = (filteredGravity * -1f).normalized()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.RIGHT,
            instructions = "3/3: Neige das Motorrad nach RECHTS und tippe auf \"Rechts erfassen\".",
            qualityHint = ""
        )
    }

    fun captureRightLeanAndFinish() {
        if (_uiState.value.calibrationStep != CalibrationStep.RIGHT) return
        rightUp = (filteredGravity * -1f).normalized()

        val up = uprightUp ?: return
        val left = leftUp ?: return
        val right = rightUp ?: return

        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f) {
            _uiState.value = _uiState.value.copy(
                qualityHint = "Kalibrierung unklar. Bitte links/rechts deutlicher neigen."
            )
            return
        }

        if (kotlin.math.abs(forward.dot(up)) > 0.85f) {
            _uiState.value = _uiState.value.copy(
                qualityHint = "Kalibrierung unklar. Bitte links/rechts sauber trennen und erneut starten."
            )
            return
        }

        // Make axis orthogonal to upright to reduce drift from noisy captures.
        forward = (forward - up * forward.dot(up)).normalized()
        bikeForwardAxis = forward

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.READY,
            isCalibrated = true,
            instructions = "Kalibrierung abgeschlossen.",
            qualityHint = "Fertig: Gauge und Verlauf sind jetzt live."
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
        val historyValues = leanHistory.map { it.valueDeg }

        _uiState.value = previous.copy(
            historyWindowSeconds = clamped,
            leanHistoryDeg = historyValues
        )
    }

    fun resetExtrema() {
        val historyValues = _uiState.value.leanHistoryDeg
        val left = historyValues.minOrNull()?.coerceAtMost(0f) ?: 0f
        val right = historyValues.maxOrNull()?.coerceAtLeast(0f) ?: 0f

        _uiState.value = _uiState.value.copy(
            maxLeftDeg = left,
            maxRightDeg = right
        )
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val alpha = 0.92f
        val raw = Vec3(event.values[0], event.values[1], event.values[2])
        filteredGravity = filteredGravity * alpha + raw * (1f - alpha)

        if (_uiState.value.isCalibrated) {
            updateLeanAngle(event.timestamp)
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

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
