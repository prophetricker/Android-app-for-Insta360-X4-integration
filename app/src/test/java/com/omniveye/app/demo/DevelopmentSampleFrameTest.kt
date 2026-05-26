package com.omniveye.app.demo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DevelopmentSampleFrameTest {

    @Test
    fun sampleMetadataDescribesOfflinePanoramaFrame() {
        val sample = DevelopmentSampleFrame.default

        assertEquals("开发样张", sample.label)
        assertEquals(2.0f, sample.aspectRatio, 0.001f)
        assertFalse(sample.overlayText.contains("Roadshow", ignoreCase = true))
    }
}
