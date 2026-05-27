package com.omniveye.app.viewmodel

import com.omniveye.app.camera.CameraConnectionState
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameAcquisitionPolicyTest {

    @Test
    fun obstacleUsesLatestCameraFrameWhenConnectedAndAvailable() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestPreviewFrame = false,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseLatestCameraFrame, plan)
    }

    @Test
    fun obstacleUsesSdkPreviewFrameBeforeFullCameraCapture() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestPreviewFrame = true,
            hasLatestCameraFrame = false
        )

        assertEquals(FrameAcquisitionPlan.UseSdkPreviewFrame, plan)
    }

    @Test
    fun obstacleUsesSdkPreviewFrameBeforeCachedPhoto() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestPreviewFrame = true,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseSdkPreviewFrame, plan)
    }

    @Test
    fun obstacleCapturesCameraFrameWhenConnectedWithoutLatestFrame() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Connected,
            hasLatestPreviewFrame = false,
            hasLatestCameraFrame = false
        )

        assertEquals(FrameAcquisitionPlan.CaptureCameraFrame, plan)
    }

    @Test
    fun obstacleUsesLatestCameraFrameWhenCameraDisconnectsAfterCapture() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Disconnected,
            hasLatestPreviewFrame = false,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseLatestCameraFrame, plan)
    }

    @Test
    fun obstacleUsesDevelopmentSampleWhenCameraIsNotConnectedAndNoCachedFrame() {
        val plan = selectObstacleFramePlan(
            cameraState = CameraConnectionState.Disconnected,
            hasLatestPreviewFrame = false,
            hasLatestCameraFrame = false
        )

        assertEquals(FrameAcquisitionPlan.UseDevelopmentSample, plan)
    }

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
    fun surroundingsUsesLatestCameraFrameWhenCameraDisconnectsAfterCapture() {
        val plan = selectSurroundingsFramePlan(
            cameraState = CameraConnectionState.Disconnected,
            hasLatestCameraFrame = true
        )

        assertEquals(FrameAcquisitionPlan.UseLatestCameraFrame, plan)
    }

    @Test
    fun surroundingsUsesDevelopmentSampleWhenCameraIsNotConnectedAndNoCachedFrame() {
        val plan = selectSurroundingsFramePlan(
            cameraState = CameraConnectionState.Disconnected,
            hasLatestCameraFrame = false
        )

        assertEquals(FrameAcquisitionPlan.UseDevelopmentSample, plan)
    }
}
