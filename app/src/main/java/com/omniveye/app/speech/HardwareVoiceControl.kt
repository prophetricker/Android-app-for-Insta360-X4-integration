package com.omniveye.app.speech

class HardwareVoiceControl {
    private var listening = false

    fun onVolumeDownPressed(): HardwareVoiceAction {
        listening = !listening
        return if (listening) HardwareVoiceAction.StartListening else HardwareVoiceAction.StopListening
    }

    fun onVolumeDownReleased(): HardwareVoiceAction {
        return HardwareVoiceAction.Ignore
    }

    fun onRecognitionInactive() {
        listening = false
    }
}

enum class HardwareVoiceAction {
    StartListening,
    StopListening,
    Ignore
}
