package app.omnieye.mobile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeModelsTest {
    @Test
    fun clampsHapticLevel() {
        assertEquals(0, clampHapticLevel(-2))
        assertEquals(3, clampHapticLevel(3))
        assertEquals(4, clampHapticLevel(7))
    }

    @Test
    fun vibratesForLevelTwoAndAbove() {
        assertFalse(shouldVibrate(1))
        assertTrue(shouldVibrate(2))
        assertTrue(shouldVibrate(4))
    }

    @Test
    fun mapsLevelToDuration() {
        assertEquals(0L, vibrationDurationMs(0))
        assertEquals(120L, vibrationDurationMs(2))
        assertEquals(240L, vibrationDurationMs(3))
        assertEquals(420L, vibrationDurationMs(4))
    }
}
