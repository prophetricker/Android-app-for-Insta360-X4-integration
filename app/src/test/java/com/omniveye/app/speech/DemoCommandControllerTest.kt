package com.omniveye.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class DemoCommandControllerTest {
    @Test
    fun firstPressShowsRecognizingCommand() {
        val controller = DemoCommandController()

        assertEquals(DemoCommandEvent.Recognizing, controller.onVolumeDownPressed())
    }

    @Test
    fun secondPressAnalyzesProductThenCyclesToTrafficLight() {
        val controller = DemoCommandController()

        controller.onVolumeDownPressed()
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.ProductRecognition),
            controller.onVolumeDownPressed()
        )

        assertEquals(DemoCommandEvent.Recognizing, controller.onVolumeDownPressed())
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.TrafficLightRecognition),
            controller.onVolumeDownPressed()
        )
    }

    @Test
    fun actionsCycleThroughAllThreeFunctions() {
        val controller = DemoCommandController()

        controller.onVolumeDownPressed()
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.ProductRecognition),
            controller.onVolumeDownPressed()
        )
        controller.onVolumeDownPressed()
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.TrafficLightRecognition),
            controller.onVolumeDownPressed()
        )
        controller.onVolumeDownPressed()
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.ObstacleAvoidance),
            controller.onVolumeDownPressed()
        )
        controller.onVolumeDownPressed()
        assertEquals(
            DemoCommandEvent.Analyze(DemoCommandAction.ProductRecognition),
            controller.onVolumeDownPressed()
        )
    }
}
