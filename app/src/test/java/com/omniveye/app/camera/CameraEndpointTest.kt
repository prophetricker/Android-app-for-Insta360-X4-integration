package com.omniveye.app.camera

import org.junit.Assert.assertEquals
import org.junit.Test

class CameraEndpointTest {

    @Test
    fun x4OscInfoUsesPort80() {
        assertEquals("http://192.168.42.1:80/osc/info", x4OscInfoUrl())
    }
}
