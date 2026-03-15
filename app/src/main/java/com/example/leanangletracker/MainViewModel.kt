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
    UPRIGHT_CAPTURE,
    LEFT_READY,
    LEFT_MEASURING,
    RIGHT_READY,
    RIGHT_MEASURING,
    READY
}

data class UiState(
    val leanAngleDeg: Float = 0f,
    val calibrationStep: CalibrationStep = CalibrationStep.UPRIGHT_CAPTURE,
    val instructions: String = "Bike aufrecht stellen",
    val isCalibrated: Boolean = false,
    val qualityHint: String = "",
    val maxLeftDeg: Float = 0f,
    val maxRightDeg: Float = 0f,
    val leanHistoryDeg: List<Float> = emptyList(),
    val invertLeanAngle: Boolean = true,
    val historyWindowSeconds: Int = 20,
    val leftCalibrationAmplitudeDeg: Float = 0f,
    val rightCalibrationAmplitudeDeg: Float = 0f,
    val currentCalibrationAmplitudeDeg: Float = 0f,
    val isMeasuringCalibrationStep: Boolean = false,
    val uprightNow: Boolean = true
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

    private var currentStepPeakDeg = 0f
    private var currentStepPeakUp: Vec3? = null
    private var uprightStableCounter = 0

    private data class TimedLean(val timestampNs: Long, val valueDeg: Float)
    private val leanHistory = ArrayDeque<TimedLean>()

    init {
        gravitySensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }

        if (gravitySensor == null) {
            _uiState.value = _uiState.value.copy(
                instructions = "Beschleunigungssensor fehlt"
            )
        }
    }

    fun startCalibration() {
        uprightUp = null
        leftUp = null
        rightUp = null
        bikeForwardAxis = null
        currentStepPeakDeg = 0f
        currentStepPeakUp = null
        uprightStableCounter = 0
        leanHistory.clear()

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.UPRIGHT_CAPTURE,
            isCalibrated = false,
            instructions = "1/3 Aufrecht + tippen",
            qualityHint = "Bedienung nur in Nulllage",
            leanAngleDeg = 0f,
            leanHistoryDeg = emptyList(),
            maxLeftDeg = 0f,
            maxRightDeg = 0f,
            leftCalibrationAmplitudeDeg = 0f,
            rightCalibrationAmplitudeDeg = 0f,
            currentCalibrationAmplitudeDeg = 0f,
            isMeasuringCalibrationStep = false
        )
    }

    fun captureUpright() {
        if (_uiState.value.calibrationStep != CalibrationStep.UPRIGHT_CAPTURE) return
        if (!_uiState.value.uprightNow) {
            _uiState.value = _uiState.value.copy(qualityHint = "Bitte erst ganz aufrecht stellen")
            return
        }

        uprightUp = (filteredGravity * -1f).normalized()
        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.LEFT_READY,
            instructions = "2/3 Links messen (nur aufrecht starten)",
            qualityHint = "Neigen → zurück aufrecht, dann stoppt Messung automatisch"
        )
    }

    fun startLeftMeasurement() {
        if (_uiState.value.calibrationStep != CalibrationStep.LEFT_READY) return
        if (!_uiState.value.uprightNow) {
            _uiState.value = _uiState.value.copy(qualityHint = "Start nur in Nulllage")
            return
        }

        currentStepPeakDeg = 0f
        currentStepPeakUp = null
        uprightStableCounter = 0

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.LEFT_MEASURING,
            instructions = "Jetzt kontrolliert nach LINKS neigen",
            qualityHint = "Dann zurück aufrecht",
            isMeasuringCalibrationStep = true,
            currentCalibrationAmplitudeDeg = 0f
        )
    }

    fun startRightMeasurement() {
        if (_uiState.value.calibrationStep != CalibrationStep.RIGHT_READY) return
        if (!_uiState.value.uprightNow) {
            _uiState.value = _uiState.value.copy(qualityHint = "Start nur in Nulllage")
            return
        }

        currentStepPeakDeg = 0f
        currentStepPeakUp = null
        uprightStableCounter = 0

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.RIGHT_MEASURING,
            instructions = "Jetzt kontrolliert nach RECHTS neigen",
            qualityHint = "Dann zurück aufrecht",
            isMeasuringCalibrationStep = true,
            currentCalibrationAmplitudeDeg = 0f
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

        val step = _uiState.value.calibrationStep
        val upRef = uprightUp
        val currentUp = (filteredGravity * -1f).normalized()

        val uprightNow = if (upRef == null) true else angleBetweenDeg(upRef, currentUp) < 4f
        _uiState.value = _uiState.value.copy(uprightNow = uprightNow)

        when (step) {
            CalibrationStep.LEFT_MEASURING,
            CalibrationStep.RIGHT_MEASURING -> updateCalibrationMeasurement(currentUp, upRef)

            CalibrationStep.READY -> updateLeanAngle(event.timestamp)
            else -> Unit
        }
    }

    private fun updateCalibrationMeasurement(currentUp: Vec3, upRef: Vec3?) {
        val reference = upRef ?: return
        val tiltDeg = angleBetweenDeg(reference, currentUp)

        if (tiltDeg > currentStepPeakDeg) {
            currentStepPeakDeg = tiltDeg
            currentStepPeakUp = currentUp
        }

        val current = _uiState.value
        _uiState.value = current.copy(currentCalibrationAmplitudeDeg = currentStepPeakDeg)

        val reachedAmplitude = currentStepPeakDeg > 8f
        val backUpright = tiltDeg < 4f
        uprightStableCounter = if (backUpright) uprightStableCounter + 1 else 0

        if (reachedAmplitude && uprightStableCounter > 8) {
            when (current.calibrationStep) {
                CalibrationStep.LEFT_MEASURING -> {
                    leftUp = currentStepPeakUp
                    _uiState.value = _uiState.value.copy(
                        calibrationStep = CalibrationStep.RIGHT_READY,
                        instructions = "3/3 Rechts messen (nur aufrecht starten)",
                        qualityHint = "Sicher stehen, dann starten",
                        leftCalibrationAmplitudeDeg = currentStepPeakDeg,
                        currentCalibrationAmplitudeDeg = 0f,
                        isMeasuringCalibrationStep = false
                    )
                }

                CalibrationStep.RIGHT_MEASURING -> {
                    rightUp = currentStepPeakUp
                    _uiState.value = _uiState.value.copy(
                        rightCalibrationAmplitudeDeg = currentStepPeakDeg,
                        currentCalibrationAmplitudeDeg = 0f,
                        isMeasuringCalibrationStep = false
                    )
                    finishCalibrationIfPossible()
                }

                else -> Unit
            }

            currentStepPeakDeg = 0f
            currentStepPeakUp = null
            uprightStableCounter = 0
        }
    }

    private fun finishCalibrationIfPossible() {
        val up = uprightUp ?: return
        val left = leftUp ?: return
        val right = rightUp ?: return

        var forward = left.cross(right).normalized()
        if (forward.norm() < 0.2f || kotlin.math.abs(forward.dot(up)) > 0.85f) {
            _uiState.value = _uiState.value.copy(
                calibrationStep = CalibrationStep.UPRIGHT_CAPTURE,
                instructions = "Kalibrierung wiederholen",
                qualityHint = "Links/Rechts war nicht eindeutig"
            )
            return
        }

        forward = (forward - up * forward.dot(up)).normalized()
        bikeForwardAxis = forward

        _uiState.value = _uiState.value.copy(
            calibrationStep = CalibrationStep.READY,
            isCalibrated = true,
            instructions = "Live",
            qualityHint = "Kalibrierung abgeschlossen"
        )
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

        _uiState.value = previous.copy(
            leanAngleDeg = leanDeg,
            maxLeftDeg = minOf(previous.maxLeftDeg, visibleHistory.minOrNull()?.coerceAtMost(0f) ?: 0f),
            maxRightDeg = maxOf(previous.maxRightDeg, visibleHistory.maxOrNull()?.coerceAtLeast(0f) ?: 0f),
            leanHistoryDeg = visibleHistory
        )
    }

    private fun pruneHistory(currentTimestampNs: Long, historyWindowSeconds: Int) {
        val cutoff = currentTimestampNs - historyWindowSeconds * 1_000_000_000L
        while (leanHistory.isNotEmpty() && leanHistory.first().timestampNs < cutoff) {
            leanHistory.removeFirst()
        }
    }

    private fun angleBetweenDeg(a: Vec3, b: Vec3): Float {
        val c = (a.dot(b) / (a.norm() * b.norm())).coerceIn(-1f, 1f)
        return Math.toDegrees(kotlin.math.acos(c).toDouble()).toFloat()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onCleared() {
        sensorManager.unregisterListener(this)
        super.onCleared()
    }
}
