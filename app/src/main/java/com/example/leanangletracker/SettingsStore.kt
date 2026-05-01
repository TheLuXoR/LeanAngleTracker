package com.example.leanangletracker

import android.content.Context
import android.content.SharedPreferences
import com.example.leanangletracker.data.Vec3

data class PersistedSettings(
    val invertLeanAngle: Boolean,
    val historyWindowSeconds: Int,
    val recorderIntervalMs: Int,
    val gpsTrackingEnabled: Boolean,
    val autoResumeEnabled: Boolean,
    val isAutoResumePurchased: Boolean,
    val fastSensorSpeedEnabled: Boolean
)

data class PersistedCalibration(
    val uprightUp: Vec3?,
    val bikeForwardAxis: Vec3?,
    val gyroBiasVectorRadPerSec: Vec3?
)

data class PersistedState(
    val settings: PersistedSettings,
    val calibration: PersistedCalibration?
)

class SettingsStore(applicationContext: Context) {
    private companion object {
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
        const val KEY_AUTO_REWIND = "auto_resume_enabled"
        const val KEY_AUTO_REWIND_PURCHASED = "auto_resume_purchased"
        const val KEY_FAST_SENSOR_SPEED = "fast_sensor_speed"
    }

    private val prefs: SharedPreferences =
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(recorderIntervalMinMs: Int, recorderIntervalMaxMs: Int): PersistedState {
        val settings = PersistedSettings(
            invertLeanAngle = prefs.getBoolean(KEY_INVERT, false),
            historyWindowSeconds = prefs.getInt(KEY_HISTORY_WINDOW, 20).coerceIn(5, 120),
            recorderIntervalMs = prefs.getInt(KEY_RECORDER_INTERVAL, 200)
                .coerceIn(recorderIntervalMinMs, recorderIntervalMaxMs),
            gpsTrackingEnabled = prefs.getBoolean(KEY_GPS_ENABLED, false),
            autoResumeEnabled = prefs.getBoolean(KEY_AUTO_REWIND, false),
            isAutoResumePurchased = prefs.getBoolean(KEY_AUTO_REWIND_PURCHASED, false),
            fastSensorSpeedEnabled = prefs.getBoolean(KEY_FAST_SENSOR_SPEED, true)
        )

        if (!prefs.getBoolean(KEY_CALIBRATED, false)) {
            return PersistedState(settings = settings, calibration = null)
        }

        val uprightUp = readVec3(KEY_UPRIGHT_X, KEY_UPRIGHT_Y, KEY_UPRIGHT_Z)?.normalized()
        val bikeForwardAxis = readVec3(KEY_FORWARD_X, KEY_FORWARD_Y, KEY_FORWARD_Z)?.normalized()
        if (uprightUp == null || bikeForwardAxis == null) {
            return PersistedState(settings = settings, calibration = null)
        }
        return PersistedState(
            settings = settings,
            calibration = PersistedCalibration(
                uprightUp = uprightUp,
                bikeForwardAxis = bikeForwardAxis,
                gyroBiasVectorRadPerSec = readVec3(KEY_GYRO_BIAS_X, KEY_GYRO_BIAS_Y, KEY_GYRO_BIAS_Z)
            )
        )
    }

    fun save(settings: SettingsUiState) {
        prefs.edit()
            .putBoolean(KEY_INVERT, settings.invertLeanAngle)
            .putInt(KEY_HISTORY_WINDOW, settings.historyWindowSeconds)
            .putInt(KEY_RECORDER_INTERVAL, settings.recorderIntervalMs)
            .putBoolean(KEY_GPS_ENABLED, settings.gpsTrackingEnabled)
            .putBoolean(KEY_AUTO_REWIND, settings.autoResumeEnabled)
            .putBoolean(KEY_AUTO_REWIND_PURCHASED, settings.isAutoResumePurchased)
            .putBoolean(KEY_FAST_SENSOR_SPEED, settings.fastSensorSpeedEnabled)
            .apply()
    }

    fun saveCalibration(up: Vec3, forward: Vec3, biasVec: Vec3?) {
        val editor = prefs.edit()
            .putBoolean(KEY_CALIBRATED, true)
            .putFloat(KEY_UPRIGHT_X, up.x)
            .putFloat(KEY_UPRIGHT_Y, up.y)
            .putFloat(KEY_UPRIGHT_Z, up.z)
            .putFloat(KEY_FORWARD_X, forward.x)
            .putFloat(KEY_FORWARD_Y, forward.y)
            .putFloat(KEY_FORWARD_Z, forward.z)
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

    fun clearCalibration() {
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

    private fun readVec3(xKey: String, yKey: String, zKey: String): Vec3? {
        if (!prefs.contains(xKey) || !prefs.contains(yKey) || !prefs.contains(zKey)) return null
        return Vec3(
            prefs.getFloat(xKey, 0f),
            prefs.getFloat(yKey, 0f),
            prefs.getFloat(zKey, 0f)
        )
    }
}
