package com.omniveye.app.cloud

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

sealed class CloudState {
    data object Idle : CloudState()
    data object Connecting : CloudState()
    data object Connected : CloudState()
    data class Uploading(val progress: Int) : CloudState()
    data class Processing(val message: String) : CloudState()
    data class Success<T>(val data: T) : CloudState()
    data class Error(val message: String) : CloudState()
}

sealed class CloudResult<out T> {
    data class Success<T>(val data: T) : CloudResult<T>()
    data class Error(val message: String) : CloudResult<Nothing>()
}

class CloudRepository(private val context: Context) {

    companion object {
        private const val TAG = "CloudRepository"
        private const val TIMEOUT_SECONDS = 60L
        private const val USE_MOCK = false
    }

    private val _state = MutableStateFlow<CloudState>(CloudState.Idle)
    val state: StateFlow<CloudState> = _state.asStateFlow()

    private val apiService: CloudApiService
    private val imageUploadManager = ImageUploadManager(context)

    init {
        apiService = createApiService()
    }

    private fun createApiService(): CloudApiService {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .addInterceptor(loggingInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(CloudEndpoints.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(CloudApiService::class.java)
    }

    suspend fun checkHealth(): CloudResult<HealthCheckResponse> {
        return try {
            _state.value = CloudState.Connecting
            if (USE_MOCK) {
                kotlinx.coroutines.delay(500)
                _state.value = CloudState.Connected
                CloudResult.Success(
                    HealthCheckResponse(
                        status = "healthy",
                        timestamp = System.currentTimeMillis().toString(),
                        services = mapOf("api" to "ok", "ml" to "ok")
                    )
                )
            } else {
                val response = apiService.healthCheck()
                if (response.isSuccessful && response.body()?.ok == true) {
                    _state.value = CloudState.Connected
                    CloudResult.Success(
                        HealthCheckResponse(
                            status = "healthy",
                            timestamp = System.currentTimeMillis().toString(),
                            services = mapOf("cloud-backend" to "ok")
                        )
                    )
                } else {
                    throw Exception("Health check failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health check failed", e)
            _state.value = CloudState.Error("Connection failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun uploadImage(bitmap: Bitmap, description: String? = null): CloudResult<ImageUploadResponse> {
        return try {
            _state.value = CloudState.Uploading(0)

            if (USE_MOCK) {
                kotlinx.coroutines.delay(1000)
                _state.value = CloudState.Uploading(100)
                kotlinx.coroutines.delay(500)
                _state.value = CloudState.Processing("Image uploaded")
                CloudResult.Success(
                    ImageUploadResponse(
                        imageId = "mock_${System.currentTimeMillis()}",
                        thumbnailUrl = null,
                        processingStatus = "uploaded"
                    )
                )
            } else {
                val file = imageUploadManager.prepareForUpload(bitmap)
                val imagePart = imageUploadManager.createMultipartBody(file)
                val descPart = imageUploadManager.createDescriptionBody(description)

                _state.value = CloudState.Uploading(50)
                val response = apiService.uploadImage(imagePart, descPart)

                if (response.isSuccessful && response.body()?.code == 0) {
                    _state.value = CloudState.Processing("Upload complete")
                    CloudResult.Success(response.body()!!.data!!)
                } else {
                    throw Exception(response.body()?.message ?: "Upload failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed", e)
            _state.value = CloudState.Error("Upload failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun analyzeFrame(bitmap: Bitmap): CloudResult<AnalyzeResponse> {
        return try {
            _state.value = CloudState.Uploading(0)

            val file = imageUploadManager.prepareForUpload(bitmap)
            val framePart = createAnalyzeFramePart(file)

            _state.value = CloudState.Processing("云端 DAP 正在分析...")
            val response = apiService.analyzeFrame(framePart)

            if (response.isSuccessful && response.body() != null) {
                val analyzeResponse = response.body()!!
                _state.value = CloudState.Success(analyzeResponse)
                CloudResult.Success(analyzeResponse)
            } else {
                throw Exception(response.errorBody()?.string() ?: "Analyze failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Analyze failed", e)
            _state.value = CloudState.Error("Analyze failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun processImage(imageId: String): CloudResult<ImageProcessResult> {
        return try {
            _state.value = CloudState.Processing("Processing image...")

            if (USE_MOCK) {
                kotlinx.coroutines.delay(2000)
                _state.value = CloudState.Success(MockData.imageProcessResult)
                CloudResult.Success(MockData.imageProcessResult)
            } else {
                val response = apiService.processImage(imageId)
                if (response.isSuccessful && response.body()?.code == 0) {
                    _state.value = CloudState.Success(response.body()!!.data!!)
                    CloudResult.Success(response.body()!!.data!!)
                } else {
                    throw Exception(response.body()?.message ?: "Processing failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Processing failed", e)
            _state.value = CloudState.Error("Processing failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun processVoice(text: String): CloudResult<VoiceProcessResponse> {
        return try {
            _state.value = CloudState.Processing("Processing voice...")

            if (USE_MOCK) {
                kotlinx.coroutines.delay(1000)
                _state.value = CloudState.Success(MockData.voiceProcessResponse)
                CloudResult.Success(MockData.voiceProcessResponse)
            } else {
                val request = VoiceProcessRequest(text = text)
                val response = apiService.processVoice(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    _state.value = CloudState.Success(response.body()!!.data!!)
                    CloudResult.Success(response.body()!!.data!!)
                } else {
                    throw Exception(response.body()?.message ?: "Voice processing failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Voice processing failed", e)
            _state.value = CloudState.Error("Voice processing failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun synthesizeSpeech(text: String): CloudResult<TtsResponse> {
        return try {
            _state.value = CloudState.Processing("Synthesizing speech...")

            if (USE_MOCK) {
                kotlinx.coroutines.delay(800)
                _state.value = CloudState.Success(MockData.ttsResponse)
                CloudResult.Success(MockData.ttsResponse)
            } else {
                val request = TtsRequest(text = text)
                val response = apiService.synthesizeSpeech(request)
                if (response.isSuccessful && response.body()?.code == 0) {
                    _state.value = CloudState.Success(response.body()!!.data!!)
                    CloudResult.Success(response.body()!!.data!!)
                } else {
                    throw Exception(response.body()?.message ?: "TTS synthesis failed")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "TTS synthesis failed", e)
            _state.value = CloudState.Error("TTS synthesis failed: ${e.message}")
            CloudResult.Error(e.message ?: "Unknown error")
        }
    }

    fun resetState() {
        _state.value = CloudState.Idle
    }
}

object MockData {
    val imageProcessResult = ImageProcessResult(
        requestId = "req_${System.currentTimeMillis()}",
        imageId = "img_mock_001",
        recognizedText = "这是一张Insta360相机拍摄的全景照片",
        sceneDescription = "室内场景，包含家具和装饰物",
        objectsDetected = listOf("沙发", "茶几", "台灯", "书架"),
        ocrText = "Welcome to OmniEye",
        confidence = 0.92f,
        processedAt = java.time.Instant.now().toString()
    )

    val voiceProcessResponse = VoiceProcessResponse(
        requestId = "req_voice_${System.currentTimeMillis()}",
        inputText = "测试语音输入",
        processedText = "已收到您的语音输入，正在处理...",
        intent = "greeting",
        entities = emptyMap(),
        audioUrl = null
    )

    val ttsResponse = TtsResponse(
        requestId = "req_tts_${System.currentTimeMillis()}",
        audioUrl = "https://api.example.com/audio/mock_tts.mp3",
        durationMs = 3000
    )
}
