package de.hasselmeyer.leanangle

import org.junit.Assert.assertEquals
import org.junit.Test

class SensorSamplingRateTest {
    @Test
    fun mediumRequests50HertzInEveryBuild() {
        assertEquals(20_000, SensorSamplingRate.MEDIUM.samplingPeriodUs)
    }

    @Test
    fun highestRequests200Hertz() {
        assertEquals(5_000, SensorSamplingRate.HIGHEST.samplingPeriodUs)
    }
}
