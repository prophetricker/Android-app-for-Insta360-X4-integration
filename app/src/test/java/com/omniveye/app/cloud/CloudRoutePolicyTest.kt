package com.omniveye.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudRoutePolicyTest {

    @Test
    fun demoFrameDoesNotRequireCellularRoute() {
        assertFalse(shouldRequireCellularRoute(isCameraFrame = false))
    }

    @Test
    fun cameraFrameRequiresCellularRoute() {
        assertTrue(shouldRequireCellularRoute(isCameraFrame = true))
    }
}
