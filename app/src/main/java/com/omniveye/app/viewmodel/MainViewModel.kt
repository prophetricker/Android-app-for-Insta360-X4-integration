package com.omniveye.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omniveye.app.camera.CameraConnectionState
import com.omniveye.app.camera.CameraManager
import com.omniveye.app.cloud.CloudRepository
import com.omniveye.app.cloud.CloudResult
import com.omniveye.app.cloud.CloudState
import com.omniveye.app.cloud.VoiceProcessResponse
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
    val lastCapturedBitmap: Bitmap? = null,
    val lastSavedPhotoPath: String? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val autoCaptureEnabled: Boolean = false,
    val autoCaptureIntervalMs: Long = 500,
    val totalPhotosCaptured: Int = 0
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val cameraManager = CameraManager(application)
    private val speechToTextManager = SpeechToTextManager(application)
    private val textToSpeechManager = TextToSpeechManager(application)
    private val cloudRepository = CloudRepository(application)

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
                    // 相机已连接，但不自动开始拍摄，等待用户手动操作
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

    fun startAutoCapture() {
        if (_uiState.value.autoCaptureEnabled) return
        autoCaptureJob?.cancel()
        autoCaptureJob = viewModelScope.launch {
            _uiState.update { it.copy(autoCaptureEnabled = true, totalPhotosCaptured = 0) }
            while (isActive) {
                capturePhotoAndUpload()
                delay(_uiState.value.autoCaptureIntervalMs)
            }
        }
    }

    fun toggleAutoCapture() {
        if (_uiState.value.autoCaptureEnabled) {
            stopAutoCapture()
        } else {
            startAutoCapture()
        }
    }

    fun stopAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        _uiState.update { it.copy(autoCaptureEnabled = false) }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            val result = cameraManager.takePhoto()
            when (result) {
                is com.omniveye.app.camera.CameraOperationResult.Success -> {
                    val bitmap = cameraManager.lastPhotoBitmap.value
                    val savedPath = cameraManager.lastSavedPhotoPath.value
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            lastCapturedBitmap = bitmap,
                            lastSavedPhotoPath = savedPath,
                            totalPhotosCaptured = it.totalPhotosCaptured + 1
                        )
                    }
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

    private fun capturePhotoAndUpload() {
        viewModelScope.launch {
            val result = cameraManager.takePhoto()
            when (result) {
                is com.omniveye.app.camera.CameraOperationResult.Success -> {
                    val bitmap = cameraManager.lastPhotoBitmap.value
                    val savedPath = cameraManager.lastSavedPhotoPath.value
                    _uiState.update {
                        it.copy(
                            lastCapturedBitmap = bitmap,
                            lastSavedPhotoPath = savedPath,
                            totalPhotosCaptured = it.totalPhotosCaptured + 1
                        )
                    }
                }
                is com.omniveye.app.camera.CameraOperationResult.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                }
            }
        }
    }

    fun uploadImage(bitmap: Bitmap) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastCapturedBitmap = bitmap) }

            when (val uploadResult = cloudRepository.uploadImage(bitmap)) {
                is CloudResult.Success -> {
                    _uiState.update { it.copy(isLoading = false) }
                    // Image uploaded successfully, algorithm processing placeholder
                    _uiState.update {
                        it.copy(processedResult = "图片上传成功，等待云端处理...")
                    }
                }
                is CloudResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = uploadResult.message
                        )
                    }
                }
            }
        }
    }

    fun setAutoCaptureInterval(intervalMs: Long) {
        _uiState.update { it.copy(autoCaptureIntervalMs = intervalMs) }
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
        speechToTextManager.destroy()
        textToSpeechManager.shutdown()
    }
}
