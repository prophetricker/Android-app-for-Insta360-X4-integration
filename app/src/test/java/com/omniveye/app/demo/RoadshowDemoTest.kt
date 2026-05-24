package com.omniveye.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadshowDemoTest {
    @Test
    fun demoScenesCoverDifferentRiskLevelsAndDistances() {
        val levels = RoadshowDemo.scenes.map { it.response.level }.toSet()
        val distances = RoadshowDemo.scenes.map { it.response.distanceM }.toSet()

        assertTrue(levels.containsAll(listOf(0, 2, 3, 4)))
        assertEquals(RoadshowDemo.scenes.size, distances.size)
    }

    @Test
    fun sceneAtCyclesThroughDemoScenes() {
        assertEquals(RoadshowDemo.scenes.first(), RoadshowDemo.sceneAt(0))
        assertEquals(RoadshowDemo.scenes.first(), RoadshowDemo.sceneAt(RoadshowDemo.scenes.size))
    }
}
