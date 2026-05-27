package com.omniveye.app.viewmodel

import com.omniveye.app.camera.CameraConnectionState
import org.junit.Assert.assertTrue
import org.junit.Test

class MainUiStateTest {

    @Test
    fun defaultDisplayCameraStateMatchesRealDisconnectedState() {
        val state = MainUiState()

        assertTrue(state.cameraState is CameraConnectionState.Disconnected)
        assertTrue(state.displayCameraState is CameraConnectionState.Disconnected)
    }
}
