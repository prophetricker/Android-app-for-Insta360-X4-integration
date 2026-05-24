package com.omniveye.app.cloud

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AnalyzeContractTest {

    private val gson = Gson()

    @Test
    fun parsesAnalyzeResponseFromCloudBackendJson() {
        val json = """
            {
              "distance_m": 0.86,
              "level": 2,
              "confidence": 0.8,
              "scene_text": "前方约一米有障碍物，请减速。",
              "latency_ms": 1234
            }
        """.trimIndent()

        val response = gson.fromJson(json, AnalyzeResponse::class.java)

        assertEquals(0.86, response.distanceM, 0.001)
        assertEquals(2, response.level)
        assertEquals(0.8, response.confidence, 0.001)
        assertEquals("前方约一米有障碍物，请减速。", response.sceneText)
        assertEquals(1234, response.latencyMs)
    }

    @Test
    fun createsAnalyzeMultipartPartWithFrameFieldName() {
        val tempFile = File.createTempFile("omnieye-test-frame", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val part = createAnalyzeFramePart(tempFile)

        assertTrue(part.headers.toString().contains("name=\"frame\""))
        assertTrue(part.headers.toString().contains("filename=\"${tempFile.name}\""))
    }
}
