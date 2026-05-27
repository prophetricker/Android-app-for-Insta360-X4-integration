package com.omniveye.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareVoiceControlTest {
    @Test
    fun volumeDownPressTogglesListening() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.StartListening, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.StopListening, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.StartListening, control.onVolumeDownPressed())
    }

    @Test
    fun volumeDownReleaseDoesNotStopListening() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.StartListening, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.Ignore, control.onVolumeDownReleased())
        assertEquals(HardwareVoiceAction.StopListening, control.onVolumeDownPressed())
    }

    @Test
    fun recognitionInactiveResetsNextPressToStartListening() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.StartListening, control.onVolumeDownPressed())

        control.onRecognitionInactive()

        assertEquals(HardwareVoiceAction.StartListening, control.onVolumeDownPressed())
    }
}
