package com.omniveye.app.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
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
import com.omniveye.app.feedback.roadshowVibrationDurationMs
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
import kotlin.system.measureTimeMillis

data class MainUiState(
    val cameraState: CameraConnectionState = CameraConnectionState.Disconnected,
    val displayCameraState: CameraConnectionState = cameraState,
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
    val lastAnalysisTiming: FrameAnalysisTiming? = null,
    val autoCaptureEnabled: Boolean = false,
    val autoCaptureIntervalMs: Long = 500,
    val totalPhotosCaptured: Int = 0
)

data class FrameAnalysisTiming(
    val totalMs: Long,
    val captureMs: Long?,
    val uploadRoundTripMs: Long,
    val backendLatencyMs: Int,
    val bitmapWidth: Int,
    val bitmapHeight: Int
) {
    fun toSummaryText(): String {
        val parts = mutableListOf("总耗时 $totalMs ms")
        captureMs?.let { parts += "X4 拍照 $it ms" }
        parts += "云端往返 $uploadRoundTripMs ms"
        parts += "后端 $backendLatencyMs ms"
        parts += "图片 ${bitmapWidth}x${bitmapHeight}"
        return parts.joinToString("，")
    }
}

enum class FrameAcquisitionPlan {
    UseSdkPreviewFrame,
    UseLatestCameraFrame,
    CaptureCameraFrame,
    UseDevelopmentSample
}

fun selectSurroundingsFramePlan(
    cameraState: CameraConnectionState,
    hasLatestCameraFrame: Boolean
): FrameAcquisitionPlan {
    return when {
        hasLatestCameraFrame ->
            FrameAcquisitionPlan.UseLatestCameraFrame
        cameraState is CameraConnectionState.Connected ->
            FrameAcquisitionPlan.CaptureCameraFrame
        else ->
            FrameAcquisitionPlan.UseDevelopmentSample
    }
}

fun selectObstacleFramePlan(
    cameraState: CameraConnectionState,
    hasLatestPreviewFrame: Boolean,
    hasLatestCameraFrame: Boolean
): FrameAcquisitionPlan {
    return when {
        hasLatestPreviewFrame ->
            FrameAcquisitionPlan.UseSdkPreviewFrame
        else ->
            selectSurroundingsFramePlan(cameraState, hasLatestCameraFrame)
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val ROADSHOW_RECOGNIZING_TEXT = "正在识别指令"
        private const val ROADSHOW_ANALYZING_TEXT = "分析指令中"
        private const val ROADSHOW_SURROUNDINGS_TEXT =
            "前方是巨大的蓝色博览会主展板，右侧是白色座椅休息区和开阔展厅通道，左侧也是展览区域。建议先停留确认方向，再向右侧开阔通道绕行继续参观。"
    }

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
                _uiState.update {
                    it.copy(
                        cameraState = state,
                        displayCameraState = state
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

    fun updateSdkPreviewFrame(bitmap: Bitmap) {
        cameraManager.updatePreviewFrame(bitmap)
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
        val clickStart = System.currentTimeMillis()
        Log.d(TAG, "ObstacleClickTiming click received cameraState=${cameraManager.connectionState.value}")
        viewModelScope.launch {
            Log.d(TAG, "ObstacleClickTiming coroutineStartedMs=${System.currentTimeMillis() - clickStart}")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    analyzeResult = null,
                    semanticResult = null,
                    errorMessage = null,
                    resultSourceLabel = null,
                    lastAnalysisTiming = null
                )
            }

            val latestCameraBitmap = cameraManager.lastPhotoBitmap.value
            val latestPreviewBitmap = cameraManager.lastPreviewBitmap.value
            when (
                selectObstacleFramePlan(
                    cameraState = cameraManager.connectionState.value,
                    hasLatestPreviewFrame = latestPreviewBitmap != null,
                    hasLatestCameraFrame = latestCameraBitmap != null
                )
            ) {
                FrameAcquisitionPlan.UseSdkPreviewFrame -> {
                    Log.d(TAG, "ObstacleClickTiming usingSdkPreviewFrameMs=${System.currentTimeMillis() - clickStart}")
                    uploadImage(
                        bitmap = latestPreviewBitmap ?: return@launch,
                        source = AnalyzeFrameSource.CameraCapture,
                        sourceLabel = "X4 SDK 预览帧",
                        captureMs = null
                    )
                }
                FrameAcquisitionPlan.UseLatestCameraFrame -> {
                    Log.d(TAG, "ObstacleClickTiming usingLatestCameraFrameMs=${System.currentTimeMillis() - clickStart}")
                    uploadImage(
                        bitmap = latestCameraBitmap ?: return@launch,
                        source = AnalyzeFrameSource.CameraCapture,
                        sourceLabel = "X4 瀹炴媿",
                        captureMs = null
                    )
                }
                FrameAcquisitionPlan.CaptureCameraFrame -> {
                    Log.d(TAG, "ObstacleClickTiming enteringTakePhotoMs=${System.currentTimeMillis() - clickStart}")
                    var capture: com.omniveye.app.camera.CameraOperationResult
                    val captureMs = measureTimeMillis {
                        capture = cameraManager.takePhoto()
                    }
                    Log.d(TAG, "ObstacleClickTiming takePhotoReturnedMs=${System.currentTimeMillis() - clickStart} captureMs=$captureMs")
                    when (val result = capture) {
                        is com.omniveye.app.camera.CameraOperationResult.Success -> {
                            val bitmap = result.data as? Bitmap
                            if (bitmap != null) {
                                uploadImage(
                                    bitmap = bitmap,
                                    source = AnalyzeFrameSource.CameraCapture,
                                    sourceLabel = "X4 实拍",
                                    captureMs = captureMs
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
                FrameAcquisitionPlan.UseDevelopmentSample -> {
                    Log.d(TAG, "ObstacleClickTiming usingDevelopmentSampleMs=${System.currentTimeMillis() - clickStart}")
                    val bitmap = DevelopmentSampleFrame.createBitmap()
                    uploadImage(
                        bitmap = bitmap,
                        source = AnalyzeFrameSource.DevelopmentSample,
                        sourceLabel = "开发样张",
                        captureMs = null
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
                    sourceLabel = "X4 实拍"
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
        sourceLabel: String? = null,
        captureMs: Long? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, lastCapturedBitmap = bitmap, lastAnalysisTiming = null) }

            var analyzeResult: CloudResult<AnalyzeResponse>
            val uploadMs = measureTimeMillis {
                analyzeResult = cloudRepository.analyzeFrame(bitmap, source)
            }
            when (val result = analyzeResult) {
                is CloudResult.Success -> {
                    val totalMs = (captureMs ?: 0L) + uploadMs
                    handleAnalyzeResult(
                        result = result.data,
                        sourceLabel = sourceLabel,
                        timing = FrameAnalysisTiming(
                            totalMs = totalMs,
                            captureMs = captureMs,
                            uploadRoundTripMs = uploadMs,
                            backendLatencyMs = result.data.latencyMs,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    )
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

    private fun handleAnalyzeResult(
        result: AnalyzeResponse,
        sourceLabel: String?,
        timing: FrameAnalysisTiming? = null
    ) {
        _uiState.update {
            it.copy(
                isLoading = false,
                analyzeResult = result,
                processedResult = result.sceneText,
                resultSourceLabel = sourceLabel,
                lastAnalysisTiming = timing
            )
        }
        vibrateForAnalyzeResult(result)
        speakResult(result.sceneText)
    }

    fun analyzeProduct(query: String = "牛奶") {
        analyzeSemanticFrame(
            mode = SemanticAnalyzeMode.PRODUCT,
            query = query,
            sourceLabel = "商品识别"
        )
    }

    fun analyzeTrafficLight() {
        analyzeSemanticFrame(
            mode = SemanticAnalyzeMode.TRAFFIC_LIGHT,
            query = null,
            sourceLabel = "红绿灯识别"
        )
    }

    fun analyzeSurroundings() {
        val latestCameraBitmap = cameraManager.lastPhotoBitmap.value
        when (
            selectSurroundingsFramePlan(
                cameraState = cameraManager.connectionState.value,
                hasLatestCameraFrame = latestCameraBitmap != null
            )
        ) {
            FrameAcquisitionPlan.UseSdkPreviewFrame -> {
                capturePhotoForSurroundings()
            }
            FrameAcquisitionPlan.UseLatestCameraFrame -> {
                analyzeSurroundingsBitmap(
                    bitmap = latestCameraBitmap ?: return,
                    source = AnalyzeFrameSource.CameraCapture,
                    sourceLabel = "X4 实拍 · 周围环境"
                )
            }
            FrameAcquisitionPlan.CaptureCameraFrame -> {
                capturePhotoForSurroundings()
            }
            FrameAcquisitionPlan.UseDevelopmentSample -> {
                analyzeSurroundingsBitmap(
                    bitmap = DevelopmentSampleFrame.createBitmap(),
                    source = AnalyzeFrameSource.DevelopmentSample,
                    sourceLabel = "开发样张 · 周围环境"
                )
            }
        }
    }

    private fun capturePhotoForSurroundings() {
        val clickStart = System.currentTimeMillis()
        Log.d(TAG, "SurroundingsClickTiming capture requested cameraState=${cameraManager.connectionState.value}")
        viewModelScope.launch {
            Log.d(TAG, "SurroundingsClickTiming coroutineStartedMs=${System.currentTimeMillis() - clickStart}")
            _uiState.update {
                it.copy(
                    isLoading = true,
                    resultSourceLabel = "X4 实拍 · 周围环境",
                    analyzeResult = null,
                    semanticResult = null,
                    errorMessage = null,
                    lastAnalysisTiming = null
                )
            }

            var capture: com.omniveye.app.camera.CameraOperationResult
            Log.d(TAG, "SurroundingsClickTiming enteringTakePhotoMs=${System.currentTimeMillis() - clickStart}")
            val captureMs = measureTimeMillis {
                capture = cameraManager.takePhoto()
            }
            Log.d(TAG, "SurroundingsClickTiming takePhotoReturnedMs=${System.currentTimeMillis() - clickStart} captureMs=$captureMs")
            when (val result = capture) {
                is com.omniveye.app.camera.CameraOperationResult.Success -> {
                    val bitmap = result.data as? Bitmap
                    if (bitmap != null) {
                        analyzeSurroundingsBitmap(
                            bitmap = bitmap,
                            source = AnalyzeFrameSource.CameraCapture,
                            sourceLabel = "X4 实拍 · 周围环境",
                            captureMs = captureMs
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
    }

    private fun analyzeSurroundingsBitmap(
        bitmap: Bitmap,
        source: AnalyzeFrameSource,
        sourceLabel: String,
        captureMs: Long? = null
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastCapturedBitmap = bitmap,
                    resultSourceLabel = sourceLabel,
                    analyzeResult = null,
                    semanticResult = null,
                    errorMessage = null,
                    lastAnalysisTiming = null
                )
            }

            var semanticResult: CloudResult<SemanticAnalyzeResponse>
            val uploadMs = measureTimeMillis {
                semanticResult = cloudRepository.semanticAnalyzeFrame(
                    bitmap = bitmap,
                    mode = SemanticAnalyzeMode.SURROUNDINGS,
                    query = null,
                    source = source
                )
            }

            when (val result = semanticResult) {
                is CloudResult.Success -> {
                    val totalMs = (captureMs ?: 0L) + uploadMs
                    handleSemanticResult(
                        result = result.data,
                        sourceLabel = sourceLabel,
                        timing = FrameAnalysisTiming(
                            totalMs = totalMs,
                            captureMs = captureMs,
                            uploadRoundTripMs = uploadMs,
                            backendLatencyMs = result.data.latencyMs,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    )
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

    private fun analyzeSemanticFrame(
        mode: SemanticAnalyzeMode,
        query: String?,
        sourceLabel: String
    ) {
        viewModelScope.launch {
            val latestCameraBitmap = cameraManager.lastPhotoBitmap.value
            val source = if (latestCameraBitmap != null) {
                AnalyzeFrameSource.CameraCapture
            } else {
                AnalyzeFrameSource.DevelopmentSample
            }
            val bitmap = latestCameraBitmap ?: DevelopmentSampleFrame.createBitmap()
            _uiState.update {
                it.copy(
                    isLoading = true,
                    lastCapturedBitmap = bitmap,
                    resultSourceLabel = if (latestCameraBitmap != null) "X4 实拍 · $sourceLabel" else "开发样张 · $sourceLabel",
                    semanticResult = null,
                    errorMessage = null,
                    lastAnalysisTiming = null
                )
            }

            var semanticResult: CloudResult<SemanticAnalyzeResponse>
            val uploadMs = measureTimeMillis {
                semanticResult = cloudRepository.semanticAnalyzeFrame(
                    bitmap = bitmap,
                    mode = mode,
                    query = query,
                    source = source
                )
            }

            when (
                val result = semanticResult
            ) {
                is CloudResult.Success -> {
                    handleSemanticResult(
                        result = result.data,
                        sourceLabel = if (latestCameraBitmap != null) "X4 实拍 · $sourceLabel" else "开发样张 · $sourceLabel",
                        timing = FrameAnalysisTiming(
                            totalMs = uploadMs,
                            captureMs = null,
                            uploadRoundTripMs = uploadMs,
                            backendLatencyMs = result.data.latencyMs,
                            bitmapWidth = bitmap.width,
                            bitmapHeight = bitmap.height
                        )
                    )
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

    private fun handleSemanticResult(
        result: SemanticAnalyzeResponse,
        sourceLabel: String,
        timing: FrameAnalysisTiming? = null
    ) {
        _uiState.update {
            it.copy(
                isLoading = false,
                semanticResult = result,
                processedResult = result.summary,
                resultSourceLabel = sourceLabel,
                lastAnalysisTiming = timing
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

    fun showRoadshowCommandRecognition() {
        _uiState.update {
            it.copy(
                isLoading = false,
                recognizedText = ROADSHOW_RECOGNIZING_TEXT,
                processedResult = ROADSHOW_RECOGNIZING_TEXT,
                analyzeResult = null,
                semanticResult = null,
                errorMessage = null,
                resultSourceLabel = "路演语音按键"
            )
        }
        speakResult(ROADSHOW_RECOGNIZING_TEXT)
    }

    fun playRoadshowSurroundingsResult() {
        val result = SemanticAnalyzeResponse(
            mode = SemanticAnalyzeMode.SURROUNDINGS.value,
            summary = ROADSHOW_SURROUNDINGS_TEXT,
            objects = listOf("蓝色主展板", "白色座椅", "休息区", "展厅通道", "人群", "展位"),
            trafficLight = null,
            targetFound = false,
            productName = null,
            confidence = 0.95,
            latencyMs = 0,
            fallbackReason = null
        )
        _uiState.update {
            it.copy(
                isLoading = false,
                recognizedText = ROADSHOW_ANALYZING_TEXT,
                processedResult = result.summary,
                analyzeResult = null,
                semanticResult = result,
                errorMessage = null,
                resultSourceLabel = "路演预置全景图",
                lastAnalysisTiming = FrameAnalysisTiming(
                    totalMs = 0,
                    captureMs = null,
                    uploadRoundTripMs = 0,
                    backendLatencyMs = 0,
                    bitmapWidth = 0,
                    bitmapHeight = 0
                )
            )
        }
        speakResult(ROADSHOW_ANALYZING_TEXT)
        viewModelScope.launch {
            delay(800)
            speakResult(result.summary)
        }
    }

    fun processVoiceInput(text: String) {
        when (routeVoiceCommand(text)) {
            VoiceCommandAction.ProductRecognition -> {
                speakResult("开始商品识别")
                analyzeProduct()
            }
            VoiceCommandAction.TrafficLightRecognition -> {
                speakResult("开始红绿灯识别")
                analyzeTrafficLight()
            }
            VoiceCommandAction.ObstacleAvoidance -> {
                speakResult("开始前方避障分析")
                capturePhoto()
            }
            VoiceCommandAction.Surroundings -> {
                speakResult("开始查看周围环境")
                analyzeSurroundings()
            }
            VoiceCommandAction.Unknown -> {
                speakResult("请说前方避障或查看周围环境")
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
