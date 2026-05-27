package com.omniveye.app.viewmodel

import com.omniveye.app.camera.CameraConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameAcquisitionPolicyTest {

    @Test
    fun surroundingsUsesLatestCameraFrameWhenConnectedAndAvailable() {
        val plan = selectSurroundingsFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseLatestCameraFrame, plan)
    }

    @Test
    fun surroundingsCapturesCameraFrameWhenConnectedWithoutLatestFrame() {
        val plan = selectSurroundingsFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestCameraFrame = false
        )

        assertEquals(FrameAcquisitionPlan.CaptureCameraFrame, plan)
    }

    @Test
    fun surroundingsUsesDevelopmentSampleWhenCameraIsNotConnected() {
        val plan = selectSurroundingsFramePlan(
            cameraState = CameraConnectionState.Disconnected,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseDevelopmentSample, plan)
    }
}
