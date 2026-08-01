package de.hasselmeyer.leanangle

internal const val CALIBRATION_SCHEMA_VERSION = 2

internal fun isSupportedCalibrationVersion(storedVersion: Int): Boolean =
    storedVersion == CALIBRATION_SCHEMA_VERSION
