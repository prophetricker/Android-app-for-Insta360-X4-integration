package com.omniveye.app.speech

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class SpeechRecognitionState {
    data object Idle : SpeechRecognitionState()
    data object Listening : SpeechRecognitionState()
    data object Processing : SpeechRecognitionState()
    data class Result(val text: String) : SpeechRecognitionState()
    data class Error(val message: String, val userAction: String? = null) : SpeechRecognitionState()
}

class SpeechToTextManager(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextManager"

        // User guidance for different errors
        private const val ACTION_MIC_PERMISSION = "设置 → 应用 → OmniEye → 权限 → 允许麦克风"
        private const val ACTION_REBOOT = "请重启设备后再试"
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
        val available = SpeechRecognizer.isRecognitionAvailable(context)
        Log.d(TAG, "Speech recognition available: $available")
        return available
    }

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun initialize() {
        if (!isAvailable()) {
            Log.e(TAG, "Speech recognition not available on this device")
            _recognitionState.value = SpeechRecognitionState.Error(
                "此设备不支持语音识别",
                ACTION_MIC_PERMISSION
            )
            return
        }

        if (!hasPermission()) {
            Log.e(TAG, "Microphone permission not granted")
            _recognitionState.value = SpeechRecognitionState.Error(
                "请授予麦克风权限",
                ACTION_MIC_PERMISSION
            )
            return
        }

        try {
            // Use default speech recognizer (will use system default or Google if available)
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(createRecognitionListener())
            Log.d(TAG, "Speech recognizer initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize speech recognizer", e)
            _recognitionState.value = SpeechRecognitionState.Error(
                "初始化失败: ${e.message}",
                ACTION_REBOOT
            )
        }
    }

    fun startListening() {
        if (!isAvailable()) {
            _recognitionState.value = SpeechRecognitionState.Error(
                "语音识别不可用",
                ACTION_MIC_PERMISSION
            )
            Log.e(TAG, "Cannot start listening: not available")
            return
        }

        if (!hasPermission()) {
            _recognitionState.value = SpeechRecognitionState.Error(
                "需要麦克风权限",
                ACTION_MIC_PERMISSION
            )
            Log.e(TAG, "Cannot start listening: no permission")
            return
        }

        if (isListening) {
            Log.w(TAG, "Already listening...")
            return
        }

        if (speechRecognizer == null) {
            initialize()
            if (speechRecognizer == null) {
                _recognitionState.value = SpeechRecognitionState.Error(
                    "语音识别初始化失败",
                    ACTION_REBOOT
                )
                return
            }
        }

        _recognitionState.value = SpeechRecognitionState.Listening
        _partialText.value = ""
        _recognizedText.value = ""
        isListening = true

        val intent = createRecognizerIntent()
        try {
            speechRecognizer?.startListening(intent)
            Log.d(TAG, "Started listening...")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start listening", e)
            isListening = false
            _recognitionState.value = SpeechRecognitionState.Error(
                "启动失败: ${e.message}",
                ACTION_REBOOT
            )
        }
    }

    fun stopListening() {
        if (!isListening) return

        isListening = false
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
        _recognitionState.value = SpeechRecognitionState.Processing
        Log.d(TAG, "Stopped listening")
    }

    fun destroy() {
        isListening = false
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying recognizer", e)
        }
        speechRecognizer = null
        Log.d(TAG, "Speech recognizer destroyed")
    }

    private fun createRecognizerIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
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
                Log.d(TAG, "Audio level: $rmsdB dB")
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
                val userAction = when (error) {
                    SpeechRecognizer.ERROR_AUDIO,
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> ACTION_MIC_PERMISSION
                    SpeechRecognizer.ERROR_CLIENT -> ACTION_REBOOT
                    else -> null
                }
                Log.e(TAG, "Recognition error: $errorMessage (code: $error)")
                _recognitionState.value = SpeechRecognitionState.Error(errorMessage, userAction)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull() ?: ""

                Log.d(TAG, "Recognition results: $recognizedText")
                Log.d(TAG, "All matches: $matches")
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
