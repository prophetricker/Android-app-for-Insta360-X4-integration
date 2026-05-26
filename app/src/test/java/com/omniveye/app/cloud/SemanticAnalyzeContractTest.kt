package com.omniveye.app.cloud

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SemanticAnalyzeContractTest {

    private val gson = Gson()

    @Test
    fun parsesSemanticAnalyzeResponseFromCloudBackendJson() {
        val json = """
            {
              "mode": "traffic_light",
              "summary": "前方是绿灯，但请确认周围安全后通行。",
              "objects": ["traffic light", "crosswalk"],
              "traffic_light": "green",
              "target_found": true,
              "product_name": null,
              "confidence": 0.82,
              "latency_ms": 1500
            }
        """.trimIndent()

        val response = gson.fromJson(json, SemanticAnalyzeResponse::class.java)

        assertEquals(SemanticAnalyzeMode.TRAFFIC_LIGHT.value, response.mode)
        assertEquals("green", response.trafficLight)
        assertEquals(true, response.targetFound)
        assertEquals(0.82, response.confidence, 0.001)
        assertTrue(response.summary.contains("绿灯"))
        assertEquals(listOf("traffic light", "crosswalk"), response.objects)
    }

    @Test
    fun createsSemanticAnalyzeMultipartParts() {
        val tempFile = File.createTempFile("omnieye-semantic-frame", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3))
            deleteOnExit()
        }

        val framePart = createAnalyzeFramePart(tempFile)
        val modePart = createSemanticTextPart(SemanticAnalyzeMode.PRODUCT.value)
        val queryPart = createSemanticTextPart("牛奶")

        assertTrue(framePart.headers.toString().contains("name=\"frame\""))
        assertEquals("product", readRequestBody(modePart))
        assertEquals("牛奶", readRequestBody(queryPart))
    }
}
