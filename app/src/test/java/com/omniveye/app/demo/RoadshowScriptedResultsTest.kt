package com.omniveye.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoadshowScriptedResultsTest {
    @Test
    fun productResultDescribesMengniuMilkOnShelfAndInHand() {
        val result = roadshowProductResult()

        assertEquals("product", result.mode)
        assertEquals("蒙牛纯牛奶", result.productName)
        assertTrue(result.summary.contains("放有饮品的货架"))
        assertTrue(result.summary.contains("手上拿着的一盒蒙牛的纯牛奶"))
    }

    @Test
    fun trafficLightResultDescribesRedTurningGreen() {
        val result = roadshowTrafficLightResult()

        assertEquals("traffic_light", result.mode)
        assertEquals("green", result.trafficLight)
        assertTrue(result.summary.contains("红灯已经转为绿灯"))
        assertTrue(result.summary.contains("可以通行"))
    }

    @Test
    fun obstacleResultDescribesTreeObstacle() {
        val result = roadshowTreeObstacleResult()

        assertEquals(2, result.level)
        assertTrue(result.sceneText.contains("前方道路右侧有树木"))
        assertTrue(result.sceneText.contains("向左侧绕行"))
    }
}
