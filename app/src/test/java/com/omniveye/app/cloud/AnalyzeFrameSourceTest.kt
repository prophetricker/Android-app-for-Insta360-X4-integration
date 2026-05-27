package com.omniveye.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalyzeFrameSourceTest {

    @Test
    fun onlyRealCameraCaptureRequiresCellularRoute() {
        assertTrue(shouldRequireCellularRoute(AnalyzeFrameSource.CameraCapture))
        assertFalse(shouldRequireCellularRoute(AnalyzeFrameSource.DevelopmentSample))
    }
}
