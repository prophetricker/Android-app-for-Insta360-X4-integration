package com.omniveye.app.feedback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HapticFeedbackTest {

    @Test
    fun vibratesOnlyForObstacleLevelsAtLeastTwo() {
        assertFalse(shouldVibrateForLevel(0))
        assertFalse(shouldVibrateForLevel(1))
        assertTrue(shouldVibrateForLevel(2))
        assertTrue(shouldVibrateForLevel(3))
        assertTrue(shouldVibrateForLevel(4))
    }

    @Test
    fun mapsObstacleLevelToRoadshowVibrationDuration() {
        assertEquals(0L, vibrationDurationMs(0))
        assertEquals(0L, vibrationDurationMs(1))
        assertEquals(120L, vibrationDurationMs(2))
        assertEquals(240L, vibrationDurationMs(3))
        assertEquals(420L, vibrationDurationMs(4))
        assertEquals(420L, vibrationDurationMs(99))
    }

    @Test
    fun givesLightRoadshowPulseForValidLowRiskCloudResult() {
        assertEquals(0L, roadshowVibrationDurationMs(level = 0, confidence = 0.0))
        assertEquals(70L, roadshowVibrationDurationMs(level = 1, confidence = 0.8))
        assertEquals(120L, roadshowVibrationDurationMs(level = 2, confidence = 0.8))
    }
}
