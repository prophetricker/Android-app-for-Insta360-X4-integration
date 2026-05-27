package com.omniveye.app.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceCommandRouterTest {
    @Test
    fun routesProductCommands() {
        assertEquals(VoiceCommandAction.ProductRecognition, routeVoiceCommand("帮我识别商品"))
        assertEquals(VoiceCommandAction.ProductRecognition, routeVoiceCommand("找一下牛奶"))
        assertEquals(VoiceCommandAction.ProductRecognition, routeVoiceCommand("看看货架"))
    }

    @Test
    fun routesTrafficLightCommands() {
        assertEquals(VoiceCommandAction.TrafficLightRecognition, routeVoiceCommand("前面红绿灯是什么"))
        assertEquals(VoiceCommandAction.TrafficLightRecognition, routeVoiceCommand("现在能不能过马路"))
        assertEquals(VoiceCommandAction.TrafficLightRecognition, routeVoiceCommand("路口是绿灯吗"))
    }

    @Test
    fun routesObstacleCommands() {
        assertEquals(VoiceCommandAction.ObstacleAvoidance, routeVoiceCommand("帮我看前方危险"))
        assertEquals(VoiceCommandAction.ObstacleAvoidance, routeVoiceCommand("避障"))
        assertEquals(VoiceCommandAction.ObstacleAvoidance, routeVoiceCommand("看路况是否安全"))
    }

    @Test
    fun unknownCommandFallsBack() {
        assertEquals(VoiceCommandAction.Unknown, routeVoiceCommand("今天天气怎么样"))
        assertEquals(VoiceCommandAction.Unknown, routeVoiceCommand(""))
    }

    @Test
    fun routesPanoramaEnvironmentCommandsToSurroundings() {
        assertEquals(VoiceCommandAction.Surroundings, routeVoiceCommand("看看周围环境"))
        assertEquals(VoiceCommandAction.Surroundings, routeVoiceCommand("帮我观察一下周围"))
    }
}
