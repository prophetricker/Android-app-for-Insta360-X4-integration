package com.omniveye.app.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraEndpointTest {

    @Test
    fun x4OscInfoUsesPort80() {
        assertEquals("http://192.168.42.1:80/osc/info", x4OscInfoUrl())
    }

    @Test
    fun x4OscCommandStatusUsesDedicatedStatusEndpoint() {
        assertEquals("http://192.168.42.1:80/osc/commands/status", x4OscCommandStatusUrl())
    }

    @Test
    fun x4OscCommandStatusBodyOnlyIncludesCommandId() {
        assertEquals("""{"id":"command-123"}""", x4OscCommandStatusBody("command-123"))
    }

    @Test
    fun attemptsX4OscConnectionWhenWifiIsEnabledEvenIfNetworkIdIsHidden() {
        assertTrue(shouldAttemptX4OscConnection(isWifiEnabled = true))
    }

    @Test
    fun treatsEnabledWifiAsEnoughToTryCameraConnectionOnHarmonyOs() {
        assertTrue(shouldTreatWifiAsEnabledForCamera(isWifiEnabled = true, networkId = -1))
    }
}
