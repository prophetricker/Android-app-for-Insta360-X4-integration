package com.omniveye.app.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemSpeechIntentFactoryTest {
    @Test
    fun createsFreeFormChineseRecognitionConfig() {
        val config = createSystemSpeechRecognitionConfig()

        assertEquals("zh-CN", config.language)
        assertEquals(1, config.maxResults)
        assertTrue(config.partialResults)
        assertTrue(config.prompt.contains("识别商品"))
    }
}
