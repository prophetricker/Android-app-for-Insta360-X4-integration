package com.omniveye.app.demo

import com.omniveye.app.camera.CameraConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class RoadshowPresentationModeTest {
    @Test
    fun realModeKeepsActualCameraState() {
        assertEquals(
            CameraConnectionState.Disconnected,
            roadshowDisplayedCameraState(CameraConnectionState.Disconnected)
        )
        assertEquals(
            CameraConnectionState.Error("not connected"),
            roadshowDisplayedCameraState(CameraConnectionState.Error("not connected"))
        )
    }

    @Test
    fun realModePassesThroughSourceLabels() {
        assertEquals("开发样张", roadshowAnalyzeSourceLabel("开发样张"))
        assertEquals("周围环境", roadshowSemanticSourceLabel("周围环境"))
    }
}
