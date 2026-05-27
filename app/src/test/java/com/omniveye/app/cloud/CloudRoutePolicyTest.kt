package com.omniveye.app.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import javax.net.SocketFactory

class CloudRoutePolicyTest {

    @Test
    fun demoFrameDoesNotRequireCellularRoute() {
        assertFalse(shouldRequireCellularRoute(isCameraFrame = false))
    }

    @Test
    fun cameraFrameRequiresCellularRoute() {
        assertTrue(shouldRequireCellularRoute(isCameraFrame = true))
    }

    @Test
    fun developmentSampleUsesDefaultRoute() {
        assertFalse(shouldRequireCellularRoute(AnalyzeFrameSource.DevelopmentSample))
    }

    @Test
    fun cameraCaptureSourceUsesCellularRoute() {
        assertTrue(shouldRequireCellularRoute(AnalyzeFrameSource.CameraCapture))
    }

    @Test
    fun cloudHealthCheckRequiresCellularRoute() {
        assertTrue(shouldRequireCellularRoute(CloudRequestKind.HealthCheck))
    }

    @Test
    fun onlyCameraCaptureSelectsAvailableCellularBinding() {
        val binding = object : CloudNetworkBinding {
            override val socketFactory: SocketFactory = SocketFactory.getDefault()

            override fun lookup(hostname: String): List<InetAddress> {
                return emptyList()
            }
        }

        assertSame(binding, selectCloudNetworkBinding(AnalyzeFrameSource.CameraCapture, binding))
        assertNull(selectCloudNetworkBinding(AnalyzeFrameSource.DevelopmentSample, binding))
    }
}
