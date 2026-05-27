package com.omniveye.app.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SpeechRecognitionState {
    data object Idle : SpeechRecognitionState()
    data object Listening : SpeechRecognitionState()
    data object Processing : SpeechRecognitionState()
    data class Result(val text: String) : SpeechRecognitionState()
    data class Error(val message: String) : SpeechRecognitionState()
}

class SpeechToTextManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextManager"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    private val _recognitionState = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    val recognitionState: StateFlow<SpeechRecognitionState> = _recognitionState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private var isListening = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }

    fun initialize() {
        if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available on this device")
            _recognitionState.value = SpeechRecognitionState.Error(
                "Speech recognition not available"
            )
            return
        }

        try {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG, "Speech recognizer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer", e)
            _recognitionState.value = SpeechRecognitionState.Error(
                "Failed to initialize: ${e.message}"
            )
        }
    }

    fun startListening() {
        if (!isAvailable()) {
            _recognitionState.value = SpeechRecognitionState.Error(
                "Speech recognition not available"
            )
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening...")
            return
        }

        _recognitionState.value = SpeechRecognitionState.Listening
        _partialText.value = ""
        _recognizedText.value = ""
        isListening = true

        val intent = createRecognizerIntent()
        speechRecognizer?.startListening(intent)
        Log.d(TAG, "Started listening...")
    }

    fun stopListening() {
        if (!isListening) return

        isListening = false
        speechRecognizer?.stopListening()
        _recognitionState.value = SpeechRecognitionState.Processing
        Log.d(TAG, "Stopped listening")
    }

    fun destroy() {
        isListening = false
        speechRecognizer?.destroy()
        speechRecognizer = null
        Log.d(TAG, "Speech recognizer destroyed")
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
    }

    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "Beginning of speech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Audio level changed - can be used for visual feedback
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d(TAG, "Buffer received")
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "End of speech")
                isListening = false
                _recognitionState.value = SpeechRecognitionState.Processing
            }

            override fun onError(error: Int) {
                isListening = false
                val errorMessage = formatSpeechError(error)
                Log.e(TAG, "Recognition error: $errorMessage (code: $error)")
                _recognitionState.value = SpeechRecognitionState.Error(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""

                Log.d(TAG, "Recognition results: $recognizedText")
                _recognizedText.value = recognizedText
                _recognitionState.value = SpeechRecognitionState.Result(recognizedText)
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )
                val partial = matches?.firstOrNull() ?: ""

                _partialText.value = partial
                Log.d(TAG, "Partial results: $partial")
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d(TAG, "Event: $eventType")
            }
        }
    }
}
