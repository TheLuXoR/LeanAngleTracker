package com.example.leanangletracker

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationPersistencePolicyTest {
    @Test
    fun `only current calibration coordinate schema is accepted`() {
        assertFalse(isSupportedCalibrationVersion(0))
        assertFalse(isSupportedCalibrationVersion(CALIBRATION_SCHEMA_VERSION - 1))
        assertTrue(isSupportedCalibrationVersion(CALIBRATION_SCHEMA_VERSION))
    }
}
