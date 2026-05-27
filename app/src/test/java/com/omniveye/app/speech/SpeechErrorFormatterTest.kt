package com.omniveye.app.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechErrorFormatterTest {
    @Test
    fun audioErrorIncludesCodeAndChineseDiagnostic() {
        val message = formatSpeechError(SpeechRecognizer.ERROR_AUDIO)

        assertTrue(message.contains("错误码 3"))
        assertTrue(message.contains("录音设备错误"))
        assertTrue(message.contains("麦克风权限"))
    }

    @Test
    fun clientErrorSuggestsSystemSpeechFallback() {
        val message = formatSpeechError(SpeechRecognizer.ERROR_CLIENT)

        assertTrue(message.contains("错误码 5"))
        assertTrue(message.contains("系统语音识别"))
    }

    @Test
    fun unknownErrorStillShowsCode() {
        val message = formatSpeechError(99)

        assertTrue(message.contains("错误码 99"))
    }
}
