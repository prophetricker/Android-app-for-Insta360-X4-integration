package com.omniveye.app.speech

class DemoCommandController {
    private var waitingForAnalyzePress = false
    private var actionIndex = 0

    fun onVolumeDownPressed(): DemoCommandEvent {
        if (!waitingForAnalyzePress) {
            waitingForAnalyzePress = true
            return DemoCommandEvent.Recognizing
        }

        waitingForAnalyzePress = false
        val action = DemoCommandAction.entries[actionIndex]
        actionIndex = (actionIndex + 1) % DemoCommandAction.entries.size
        return DemoCommandEvent.Analyze(action)
    }
}

enum class DemoCommandAction {
    ProductRecognition,
    TrafficLightRecognition,
    ObstacleAvoidance
}

sealed class DemoCommandEvent {
    data object Recognizing : DemoCommandEvent()
    data class Analyze(val action: DemoCommandAction) : DemoCommandEvent()
}
