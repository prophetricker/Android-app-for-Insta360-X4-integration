package com.omniveye.app.speech

import android.content.Intent
import android.speech.RecognizerIntent

data class SystemSpeechRecognitionConfig(
    val language: String,
    val maxResults: Int,
    val partialResults: Boolean,
    val prompt: String
)

fun createSystemSpeechRecognitionConfig(): SystemSpeechRecognitionConfig {
    return SystemSpeechRecognitionConfig(
        language = "zh-CN",
        maxResults = 1,
        partialResults = true,
        prompt = "请说：识别商品、看红绿灯或前方避障"
    )
}

fun createSystemSpeechRecognitionIntent(): Intent {
    val config = createSystemSpeechRecognitionConfig()
    return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, config.language)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, config.language)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, config.partialResults)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, config.maxResults)
        putExtra(RecognizerIntent.EXTRA_PROMPT, config.prompt)
    }
}
