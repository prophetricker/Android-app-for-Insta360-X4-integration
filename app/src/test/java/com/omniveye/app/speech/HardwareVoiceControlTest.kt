package com.omniveye.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class HardwareVoiceControlTest {
    @Test
    fun volumeDownPressesAdvanceRoadshowPromptFlow() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.ShowRecognizingCommand, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.PlayRoadshowSurroundingsResult, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.ShowRecognizingCommand, control.onVolumeDownPressed())
    }

    @Test
    fun volumeDownReleaseDoesNotAdvanceRoadshowFlow() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.ShowRecognizingCommand, control.onVolumeDownPressed())
        assertEquals(HardwareVoiceAction.Ignore, control.onVolumeDownReleased())
        assertEquals(HardwareVoiceAction.PlayRoadshowSurroundingsResult, control.onVolumeDownPressed())
    }

    @Test
    fun recognitionInactiveResetsNextPressToRecognizingCommand() {
        val control = HardwareVoiceControl()

        assertEquals(HardwareVoiceAction.ShowRecognizingCommand, control.onVolumeDownPressed())

        control.onRecognitionInactive()

        assertEquals(HardwareVoiceAction.ShowRecognizingCommand, control.onVolumeDownPressed())
    }
}
