package com.omniveye.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

class VoskSpeechManager(private val context: Context) : RecognitionListener {

    companion object {
        private const val TAG = "VoskSpeechManager"
        private const val MODEL_NAME = "vosk-model-small-cn-0.3"
        private const val MODEL_ZIP = "vosk-model-small-cn-0.3.zip"
    }

    private var model: Model? = null
    private var speechService: SpeechService? = null
    private var recognizer: Recognizer? = null
    private var initJob: Job? = null

    private val _recognitionState = MutableStateFlow<SpeechRecognitionState>(SpeechRecognitionState.Idle)
    val recognitionState: StateFlow<SpeechRecognitionState> = _recognitionState.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    private var isListening = false

    fun isAvailable(): Boolean = true // Vosk is always available offline

    fun hasPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun initialize(onComplete: (() -> Unit)? = null) {
        if (!hasPermission()) {
            Log.e(TAG, "Microphone permission not granted")
            _recognitionState.value = SpeechRecognitionState.Error(
                "请授予麦克风权限",
                "设置 → 应用 → OmniEye → 权限 → 允许麦克风"
            )
            return
        }

        if (model != null) {
            Log.d(TAG, "Model already loaded")
            _isModelReady.value = true
            onComplete?.invoke()
            return
        }

        _recognitionState.value = SpeechRecognitionState.Processing
        Log.d(TAG, "Loading Vosk model...")

        initJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                StorageService.unpack(
                    context,
                    MODEL_ZIP,
                    MODEL_NAME,
                    { loadedModel ->
                        model = loadedModel
                        _isModelReady.value = true
                        _recognitionState.value = SpeechRecognitionState.Idle
                        Log.d(TAG, "Vosk model loaded successfully")
                        withContext(Dispatchers.Main) {
                            onComplete?.invoke()
                        }
                    },
                    { exception ->
                        Log.e(TAG, "Failed to load Vosk model", exception)
                        withContext(Dispatchers.Main) {
                            _recognitionState.value = SpeechRecognitionState.Error(
                                "语音模型加载失败: ${exception.message}",
                                "请确保网络连接后重试"
                            )
                            _isModelReady.value = false
                        }
                    }
                )
            } catch (e: IOException) {
                Log.e(TAG, "IO error loading model", e)
                withContext(Dispatchers.Main) {
                    _recognitionState.value = SpeechRecognitionState.Error(
                        "模型加载失败: ${e.message}",
                        "请确保网络连接后重试"
                    )
                    _isModelReady.value = false
                }
            }
        }
    }

    fun startListening() {
        if (isListening) {
            Log.w(TAG, "Already listening...")
            return
        }

        if (!hasPermission()) {
            _recognitionState.value = SpeechRecognitionState.Error(
                "请授予麦克风权限",
                "设置 → 应用 → OmniEye → 权限 → 允许麦克风"
            )
            return
        }

        if (model == null) {
            _recognitionState.value = SpeechRecognitionState.Error(
                "语音模型未加载",
                "请等待模型下载完成"
            )
            return
        }

        isListening = true
        _partialText.value = ""
        _recognizedText.value = ""
        _recognitionState.value = SpeechRecognitionState.Listening
        Log.d(TAG, "Starting Vosk listening...")

        try {
            speechService = SpeechService(model, 16000.0f).apply {
                startListening(this@VoskSpeechManager, true)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start speech service", e)
            isListening = false
            _recognitionState.value = SpeechRecognitionState.Error(
                "启动失败: ${e.message}",
                "请重启应用"
            )
        }
    }

    fun stopListening() {
        if (!isListening) return

        Log.d(TAG, "Stopping Vosk listening...")
        isListening = false
        try {
            speechService?.stopListening()
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping listening", e)
        }
        speechService = null
        _recognitionState.value = SpeechRecognitionState.Processing
    }

    fun destroy() {
        isListening = false
        initJob?.cancel()
        try {
            speechService?.shutdown()
        } catch (e: Exception) {
            Log.e(TAG, "Error destroying speech service", e)
        }
        speechService = null
        recognizer?.close()
        recognizer = null
        model?.close()
        model = null
        _isModelReady.value = false
        Log.d(TAG, "VoskSpeechManager destroyed")
    }

    // RecognitionListener implementation
    override fun onPartialResult(hypothesis: String?) {
        hypothesis?.let {
            _partialText.value = it
            Log.d(TAG, "Partial result: $it")
        }
    }

    override fun onResult(hypothesis: String?) {
        hypothesis?.let {
            val text = it.trim()
            if (text.isNotEmpty()) {
                _recognizedText.value = text
                _recognitionState.value = SpeechRecognitionState.Result(text)
                Log.d(TAG, "Final result: $text")
            }
        }
        // Continue listening after a result
        if (isListening) {
            // Keep listening for more input
        }
    }

    override fun onFinalResult(hypothesis: String?) {
        onResult(hypothesis)
    }

    override fun onError(exception: Exception?) {
        Log.e(TAG, "Vosk error", exception)
        isListening = false
        _recognitionState.value = SpeechRecognitionState.Error(
            "识别错误: ${exception?.message ?: "未知错误"}",
            "请重启应用"
        )
    }

    override fun onTimeout() {
        Log.d(TAG, "Vosk timeout")
        isListening = false
        if (_recognitionState.value is SpeechRecognitionState.Listening) {
            _recognitionState.value = SpeechRecognitionState.Idle
        }
    }

    override fun onDeviceError(deviceError: Int) {
        Log.e(TAG, "Device error: $deviceError")
        isListening = false
        _recognitionState.value = SpeechRecognitionState.Error(
            "设备错误 ($deviceError)",
            "请重启应用"
        )
    }

    override fun onAudioLevel(audioLevel: Float) {
        // Could emit this to UI if needed
    }
}
