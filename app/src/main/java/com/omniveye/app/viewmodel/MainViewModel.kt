package com.omniveye.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.omniveye.app.camera.CameraConnectionState
import com.omniveye.app.camera.CameraManager
import com.omniveye.app.cloud.AnalyzeFrameSource
import com.omniveye.app.cloud.AnalyzeResponse
import com.omniveye.app.cloud.CellularNetworkState
import com.omniveye.app.cloud.CloudRepository
import com.omniveye.app.cloud.CloudResult
import com.omniveye.app.cloud.CloudState
import com.omniveye.app.cloud.SemanticAnalyzeMode
import com.omniveye.app.cloud.SemanticAnalyzeResponse
import com.omniveye.app.cloud.VoiceProcessResponse
import com.omniveye.app.demo.DevelopmentSampleFrame
import com.omniveye.app.demo.RoadshowDemo
import com.omniveye.app.demo.roadshowProductResult
import com.omniveye.app.demo.roadshowAnalyzeSourceLabel
import com.omniveye.app.demo.roadshowDisplayedCameraState
import com.omniveye.app.demo.roadshowSemanticSourceLabel
import com.omniveye.app.demo.roadshowTrafficLightResult
import com.omniveye.app.demo.roadshowTreeObstacleResult
import com.omniveye.app.feedback.roadshowVibrationDurationMs
import com.omniveye.app.speech.DemoCommandAction
import com.omniveye.app.speech.DemoCommandController
import com.omniveye.app.speech.DemoCommandEvent
import com.omniveye.app.speech.SpeechRecognitionState
import com.omniveye.app.speech.SpeechToTextManager
import com.omniveye.app.speech.TtsState
import com.omniveye.app.speech.TextToSpeechManager
import com.omniveye.app.speech.VoiceCommandAction
import com.omniveye.app.speech.routeVoiceCommand
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
    val displayCameraState: CameraConnectionState = roadshowDisplayedCameraState(cameraState),
    val cloudState: CloudState = CloudState.Idle,
    val speechRecognitionState: SpeechRecognitionState = SpeechRecognitionState.Idle,
    val ttsState: TtsState = TtsState.Idle,
    val recognizedText: String = "",
    val processedResult: String = "",
    val currentTtsText: String = "",
    val analyzeResult: AnalyzeResponse? = null,
    val semanticResult: SemanticAnalyzeResponse? = null,
    val lastCapturedBitmap: Bitmap? = null,
    val lastSavedPhotoPath: String? = null,
    val backendBaseUrl: String = "",
    val cellularNetworkState: CellularNetworkState = CellularNetworkState.Unavailable,
    val resultSourceLabel: String? = null,
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
    private val demoCommandController = DemoCommandController()
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        application.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        application.getSystemService(Vibrator::class.java)
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var autoCaptureJob: Job? = null
    private var demoSceneIndex = 0

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
                _uiState.update {
                    it.copy(
                        cameraState = state,
                        displayCameraState = roadshowDisplayedCameraState(state)
                    )
                }
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

    fun toggleAutoCapture() {
        if (_uiState.value.autoCaptureEnabled) {
            stopAutoCapture()
        } else {
            startAutoCapture()
        }
    }

    fun setAutoCaptureInterval(intervalMs: Long) {
        _uiState.update { it.copy(autoCaptureIntervalMs = intervalMs.coerceAtLeast(500L)) }
    }

    fun startAutoCapture() {
        if (_uiState.value.autoCaptureEnabled) return
        autoCaptureJob?.cancel()
        autoCaptureJob = viewModelScope.launch {
            _uiState.update { it.copy(autoCaptureEnabled = true, totalPhotosCaptured = 0) }
            while (isActive) {
                capturePhoto()
                delay(_uiState.value.autoCaptureIntervalMs)
            }
        }
    }

    fun stopAutoCapture() {
        autoCaptureJob?.cancel()
        autoCaptureJob = null
        _uiState.update { it.copy(autoCaptureEnabled = false) }
    }

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    analyzeResult = null,
                    semanticResult = null,
                    errorMessage = null,
                    resultSourceLabel = null
                )
            }

            when (cameraManager.connectionState.value) {
                is CameraConnectionState.Connected -> {
                    when (val result = cameraManager.takePhoto()) {
                        is com.omniveye.app.camera.CameraOperationResult.Success -> {
                            val bitmap = result.data as? Bitmap
                            if (bitmap != null) {
                                uploadImage(
                                    bitmap = bitmap,
                                    source = AnalyzeFrameSource.CameraCapture,
                                    sourceLabel = roadshowAnalyzeSourceLabel("X4 实拍")
                                )
                            } else {
                                _uiState.update {
                                    it.copy(isLoading = false, errorMessage = "X4 did not return a bitmap")
                                }
                            }
                        }
                        is com.omniveye.app.camera.CameraOperationResult.Error -> {
                            _uiState.update {
                                it.copy(isLoading = false, errorMessage = result.message)
                            }
                        }
                    }
                }
                else -> {
                    val bitmap = DevelopmentSampleFrame.createBitmap()
                    uploadImage(
                        bitmap = bitmap,
                        source = AnalyzeFrameSource.DevelopmentSample,
                        sourceLabel = "开发样张"
                    )
                }
            }
        }
    }

    private fun fetchAndUploadLatestPhoto() {
        viewModelScope.launch {
            val bitmap = cameraManager.lastPhotoBitmap.value
            if (bitmap != null) {
                uploadImage(
                    bitmap = bitmap,
                    source = AnalyzeFrameSource.CameraCapture,
                    sourceLabel = roadshowAnalyzeSourceLabel("X4 实拍")
                )
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

    fun uploadImage(
        bitmap: Bitmap,
        source: AnalyzeFrameSource = AnalyzeFrameSource.DevelopmentSample,
        sourceLabel: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastCapturedBitmap = bitmap) }

            when (val result = cloudRepository.analyzeFrame(bitmap, source)) {
                is CloudResult.Success -> {
                    handleAnalyzeResult(result.data, sourceLabel)
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

    private fun handleAnalyzeResult(result: AnalyzeResponse, sourceLabel: String?) {
        _uiState.update {
            it.copy(
                isLoading = false,
                analyzeResult = result,
                processedResult = result.sceneText,
                resultSourceLabel = sourceLabel
            )
        }
        vibrateForAnalyzeResult(result)
        speakResult(result.sceneText)
    }

    fun playRoadshowDemo() {
        val scene = RoadshowDemo.sceneAt(demoSceneIndex)
        demoSceneIndex++
        _uiState.update {
            it.copy(
                isLoading = false,
                analyzeResult = scene.response,
                processedResult = scene.response.sceneText,
                resultSourceLabel = "路演演示：${scene.name}",
                errorMessage = null
            )
        }
        vibrateForAnalyzeResult(scene.response)
        speakResult(scene.response.sceneText)
    }

    fun analyzeProductDemo(query: String = "牛奶") {
        handleSemanticResult(
            result = roadshowProductResult(),
            sourceLabel = roadshowSemanticSourceLabel("商品识别")
        )
    }

    fun analyzeTrafficLightDemo() {
        handleSemanticResult(
            result = roadshowTrafficLightResult(),
            sourceLabel = roadshowSemanticSourceLabel("红绿灯识别")
        )
    }

    fun analyzeSurroundings() {
        val bitmap = cameraManager.lastPhotoBitmap.value ?: DevelopmentSampleFrame.createBitmap()
        val source = if (cameraManager.connectionState.value is CameraConnectionState.Connected &&
            cameraManager.lastPhotoBitmap.value != null
        ) {
            AnalyzeFrameSource.CameraCapture
        } else {
            AnalyzeFrameSource.DevelopmentSample
        }
        val sourceLabel = if (source == AnalyzeFrameSource.CameraCapture) {
            "X4 实拍 · 周围环境"
        } else {
            "开发样张 · 周围环境"
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastCapturedBitmap = bitmap,
                    resultSourceLabel = sourceLabel,
                    analyzeResult = null,
                    semanticResult = null,
                    errorMessage = null
                )
            }

            when (
                val result = cloudRepository.semanticAnalyzeFrame(
                    bitmap = bitmap,
                    mode = SemanticAnalyzeMode.SURROUNDINGS,
                    query = null,
                    source = source
                )
            ) {
                is CloudResult.Success -> handleSemanticResult(result.data, sourceLabel)
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

    private fun analyzeSemanticDemo(
        mode: SemanticAnalyzeMode,
        query: String?,
        sourceLabel: String
    ) {
        viewModelScope.launch {
            val bitmap = DevelopmentSampleFrame.createBitmap()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastCapturedBitmap = bitmap,
                    resultSourceLabel = sourceLabel,
                    semanticResult = null,
                    errorMessage = null
                )
            }

            when (
                val result = cloudRepository.semanticAnalyzeFrame(
                    bitmap = bitmap,
                    mode = mode,
                    query = query,
                    source = AnalyzeFrameSource.DevelopmentSample
                )
            ) {
                is CloudResult.Success -> handleSemanticResult(result.data, sourceLabel)
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

    private fun handleSemanticResult(result: SemanticAnalyzeResponse, sourceLabel: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                semanticResult = result,
                processedResult = result.summary,
                resultSourceLabel = sourceLabel
            )
        }
        speakResult(result.summary)
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

    fun reportSpeechError(message: String) {
        _uiState.update { it.copy(speechRecognitionState = SpeechRecognitionState.Error(message)) }
    }

    fun handleDemoCommandVolumePress() {
        when (val event = demoCommandController.onVolumeDownPressed()) {
            DemoCommandEvent.Recognizing -> showDemoCommandRecognizing()
            is DemoCommandEvent.Analyze -> runDemoCommand(event.action)
        }
    }

    private fun showDemoCommandRecognizing() {
        _uiState.update {
            it.copy(
                speechRecognitionState = SpeechRecognitionState.Listening,
                recognizedText = "正在识别指令",
                processedResult = "正在识别指令",
                errorMessage = null
            )
        }
    }

    private fun runDemoCommand(action: DemoCommandAction) {
        _uiState.update {
            it.copy(
                speechRecognitionState = SpeechRecognitionState.Processing,
                recognizedText = "分析指令中",
                processedResult = "分析指令中",
                errorMessage = null
            )
        }
        when (action) {
            DemoCommandAction.ProductRecognition -> {
                speakResult("分析指令中，开始商品识别")
                analyzeProductDemo()
            }
            DemoCommandAction.TrafficLightRecognition -> {
                speakResult("分析指令中，开始红绿灯识别")
                analyzeTrafficLightDemo()
            }
            DemoCommandAction.ObstacleAvoidance -> {
                speakResult("分析指令中，开始前方避障分析")
                capturePhoto()
            }
        }
    }

    fun processVoiceInput(text: String) {
        when (routeVoiceCommand(text)) {
            VoiceCommandAction.ProductRecognition -> {
                speakResult("开始商品识别")
                analyzeProductDemo()
            }
            VoiceCommandAction.TrafficLightRecognition -> {
                speakResult("开始红绿灯识别")
                analyzeTrafficLightDemo()
            }
            VoiceCommandAction.ObstacleAvoidance -> {
                speakResult("开始前方避障分析")
                capturePhoto()
            }
            VoiceCommandAction.Unknown -> {
                speakResult("请说商品识别、红绿灯识别或前方避障")
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
