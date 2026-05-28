package com.omniveye.app.speech

class HardwareVoiceControl {
    private var waitingForSecondPress = false

    fun onVolumeDownPressed(): HardwareVoiceAction {
        return if (waitingForSecondPress) {
            waitingForSecondPress = false
            HardwareVoiceAction.PlayRoadshowSurroundingsResult
        } else {
            waitingForSecondPress = true
            HardwareVoiceAction.ShowRecognizingCommand
        }
    }

    fun onVolumeDownReleased(): HardwareVoiceAction {
        return HardwareVoiceAction.Ignore
    }

    fun onRecognitionInactive() {
        waitingForSecondPress = false
    }
}

enum class HardwareVoiceAction {
    ShowRecognizingCommand,
    PlayRoadshowSurroundingsResult,
    Ignore
}
