package com.omniveye.app.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class FrameAnalysisTimingTest {

    @Test
    fun formatsFullTimingSummary() {
        val timing = FrameAnalysisTiming(
            totalMs = 2100,
            captureMs = 900,
            uploadRoundTripMs = 800,
            backendLatencyMs = 420,
            bitmapWidth = 3840,
            bitmapHeight = 1920
        )

        assertEquals(
            "总耗时 2100 ms，X4 拍照 900 ms，云端往返 800 ms，后端 420 ms，图片 3840x1920",
            timing.toSummaryText()
        )
    }

    @Test
    fun omitsUnknownOptionalTimingParts() {
        val timing = FrameAnalysisTiming(
            totalMs = 500,
            captureMs = null,
            uploadRoundTripMs = 320,
            backendLatencyMs = 120,
            bitmapWidth = 960,
            bitmapHeight = 480
        )

        assertEquals(
            "总耗时 500 ms，云端往返 320 ms，后端 120 ms，图片 960x480",
            timing.toSummaryText()
        )
    }
}
