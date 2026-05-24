package com.omniveye.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omniveye.app.camera.CameraConnectionState
import com.omniveye.app.camera.CameraManager
import com.omniveye.app.cloud.AnalyzeResponse
import com.omniveye.app.cloud.CellularNetworkState
import com.omniveye.app.cloud.CloudRepository
import com.omniveye.app.cloud.CloudResult
import com.omniveye.app.cloud.CloudState
import com.omniveye.app.cloud.VoiceProcessResponse
import com.omniveye.app.feedback.roadshowVibrationDurationMs
import com.omniveye.app.speech.SpeechRecognitionState
import com.omniveye.app.speech.SpeechToTextManager
import com.omniveye.app.speech.TtsState
import com.omniveye.app.speech.TextToSpeechManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MainUiState(
    val cameraState: CameraConnectionState = CameraConnectionState.Disconnected,
    val cloudState: CloudState = CloudState.Idle,
    val speechRecognitionState: SpeechRecognitionState = SpeechRecognitionState.Idle,
    val ttsState: TtsState = TtsState.Idle,
    val recognizedText: String = "",
    val processedResult: String = "",
    val currentTtsText: String = "",
    val analyzeResult: AnalyzeResponse? = null,
    val lastCapturedBitmap: Bitmap? = null,
    val backendBaseUrl: String = "",
    val cellularNetworkState: CellularNetworkState = CellularNetworkState.Unavailable,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val autoCaptureEnabled: Boolean = false
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraManager = CameraManager(application)
    private val speechToTextManager = SpeechToTextManager(application)
    private val textToSpeechManager = TextToSpeechManager(application)
    private val cloudRepository = CloudRepository(application)
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        application.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Vibrator::class.java)
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var autoCaptureJob: Job? = null

    init {
        initializeManagers()
        observeStates()
    }

    private fun initializeManagers() {
        speechToTextManager.initialize()
        textToSpeechManager.initialize()
        _uiState.update { it.copy(backendBaseUrl = cloudRepository.baseUrl) }
        cloudRepository.startCellularRoute()

        viewModelScope.launch {
            cloudRepository.checkHealth()
        }
    }

    private fun observeStates() {
        viewModelScope.launch {
            cameraManager.connectionState.collect { state ->
                _uiState.update { it.copy(cameraState = state) }
                when (state) {
                    is com.omniveye.app.camera.CameraConnectionState.Connected -> {
                        stopAutoCapture()
                    }
                    is com.omniveye.app.camera.CameraConnectionState.Disconnected -> {
                        stopAutoCapture()
                    }
                    else -> {}
                }
            }
        }

        viewModelScope.launch {
            cloudRepository.state.collect { state ->
                _uiState.update { it.copy(cloudState = state) }
            }
        }

        viewModelScope.launch {
            cloudRepository.cellularNetworkState.collect { state ->
                _uiState.update { it.copy(cellularNetworkState = state) }
            }
        }

        viewModelScope.launch {
            speechToTextManager.recognitionState.collect { state ->
                _uiState.update { it.copy(speechRecognitionState = state) }
            }
        }

        viewModelScope.launch {
            combine(
                speechToTextManager.recognizedText,
                textToSpeechManager.ttsState,
                textToSpeechManager.currentText
            ) { recognized, tts, currentText ->
                Triple(recognized, tts, currentText)
            }.collect { (recognized, tts, currentText) ->
                _uiState.update {
                    it.copy(
                        recognizedText = recognized,
                        ttsState = tts,
                        currentTtsText = currentText
                    )
                }
            }
        }
    }

    fun connectToCamera() {
        viewModelScope.launch {
            cameraManager.connectToCamera()
        }
    }

    fun disconnectCamera() {
        cameraManager.disconnect()
    }

    fun checkCameraConnection(): Boolean {
        return cameraManager.checkCameraWiFiConnection()
    }

    fun updateBackendBaseUrl(baseUrl: String) {
        val normalized = cloudRepository.updateBaseUrl(baseUrl)
        _uiState.update { it.copy(backendBaseUrl = normalized) }
        checkCloudHealth()
    }

    fun checkCloudHealth() {
        viewModelScope.launch {
            cloudRepository.checkHealth()
        }
    }

    private fun startAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = viewModelScope.launch {
            _uiState.update { it.copy(autoCaptureEnabled = true) }
            while (isActive) {
                capturePhoto()
                delay(500)
            }
        }
    }

    private fun stopAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        _uiState.update { it.copy(autoCaptureEnabled = false) }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            if (cameraManager.connectionState.value != CameraConnectionState.Connected) {
                uploadImage(createRoadshowDemoFrame())
                return@launch
            }

            val result = cameraManager.takePhoto()
            when (result) {
                is com.omniveye.app.camera.CameraOperationResult.Success -> {
                    fetchAndUploadLatestPhoto()
                }
                is com.omniveye.app.camera.CameraOperationResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun fetchAndUploadLatestPhoto() {
        viewModelScope.launch {
            val bitmap = cameraManager.lastPhotoBitmap.value
            if (bitmap != null) {
                uploadImage(bitmap)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "No photo captured"
                    )
                }
            }
        }
    }

    fun uploadImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastCapturedBitmap = bitmap) }

            when (val result = cloudRepository.analyzeFrame(bitmap)) {
                is CloudResult.Success -> {
                    handleAnalyzeResult(result.data)
                }
                is CloudResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun handleAnalyzeResult(result: AnalyzeResponse) {
        _uiState.update {
            it.copy(
                isLoading = false,
                analyzeResult = result,
                processedResult = result.sceneText
            )
        }
        vibrateForAnalyzeResult(result)
        speakResult(result.sceneText)
    }

    fun startListening() {
        if (!speechToTextManager.isAvailable()) {
            _uiState.update { it.copy(errorMessage = "语音识别不可用") }
            return
        }
        speechToTextManager.startListening()
    }

    fun stopListening() {
        speechToTextManager.stopListening()
    }

    fun processVoiceInput(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            when (val result = cloudRepository.processVoice(text)) {
                is CloudResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    speakResult(result.data.processedText ?: result.data.inputText)
                }
                is CloudResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = result.message
                        )
                    }
                }
            }
        }
    }

    private fun speakResult(result: String) {
        if (result.isNotBlank()) {
            textToSpeechManager.speak(result)
        }
    }

    private fun vibrateForAnalyzeResult(result: AnalyzeResponse) {
        val durationMs = roadshowVibrationDurationMs(result.level, result.confidence)
        vibrate(durationMs)
    }

    private fun vibrate(durationMs: Long) {
        if (durationMs <= 0L) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(
                VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
            )
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(durationMs)
        }
    }

    private fun createRoadshowDemoFrame(): Bitmap {
        val bitmap = Bitmap.createBitmap(1024, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = Color.rgb(35, 45, 55)
        canvas.drawRect(0f, 0f, 1024f, 280f, paint)
        paint.color = Color.rgb(95, 105, 85)
        canvas.drawRect(0f, 280f, 1024f, 512f, paint)
        paint.color = Color.rgb(160, 80, 70)
        canvas.drawRect(430f, 230f, 590f, 500f, paint)
        paint.color = Color.WHITE
        paint.textSize = 36f
        canvas.drawText("OmniEye Roadshow Frame", 320f, 80f, paint)

        return bitmap
    }

    fun speakText(text: String) {
        textToSpeechManager.speak(text)
    }

    fun stopSpeaking() {
        textToSpeechManager.stop()
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        autoCaptureJob?.cancel()
        cloudRepository.stopCellularRoute()
        speechToTextManager.destroy()
        textToSpeechManager.shutdown()
    }
}
