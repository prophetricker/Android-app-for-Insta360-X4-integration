package com.omniveye.app.cloud

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("code")
    val code: Int,
    @SerializedName("message")
    val message: String,
    @SerializedName("data")
    val data: T?
)

data class ImageUploadResponse(
    @SerializedName("image_id")
    val imageId: String,
    @SerializedName("thumbnail_url")
    val thumbnailUrl: String?,
    @SerializedName("processing_status")
    val processingStatus: String
)

data class ImageProcessResult(
    @SerializedName("request_id")
    val requestId: String,
    @SerializedName("image_id")
    val imageId: String,
    @SerializedName("recognized_text")
    val recognizedText: String?,
    @SerializedName("scene_description")
    val sceneDescription: String?,
    @SerializedName("objects_detected")
    val objectsDetected: List<String>?,
    @SerializedName("ocr_text")
    val ocrText: String?,
    @SerializedName("confidence")
    val confidence: Float?,
    @SerializedName("processed_at")
    val processedAt: String?
)

data class AnalyzeResponse(
    @SerializedName("distance_m")
    val distanceM: Double,
    @SerializedName("level")
    val level: Int,
    @SerializedName("confidence")
    val confidence: Double,
    @SerializedName("scene_text")
    val sceneText: String,
    @SerializedName("latency_ms")
    val latencyMs: Int
)

data class VoiceProcessRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("language")
    val language: String = "zh-CN",
    @SerializedName("user_id")
    val userId: String? = null
)

data class VoiceProcessResponse(
    @SerializedName("request_id")
    val requestId: String,
    @SerializedName("input_text")
    val inputText: String,
    @SerializedName("processed_text")
    val processedText: String?,
    @SerializedName("intent")
    val intent: String?,
    @SerializedName("entities")
    val entities: Map<String, Any>?,
    @SerializedName("audio_url")
    val audioUrl: String?
)

data class TtsRequest(
    @SerializedName("text")
    val text: String,
    @SerializedName("voice")
    val voice: String = "zh-CN-female",
    @SerializedName("speed")
    val speed: Float = 1.0f,
    @SerializedName("pitch")
    val pitch: Float = 1.0f,
    @SerializedName("volume")
    val volume: Float = 1.0f
)

data class TtsResponse(
    @SerializedName("request_id")
    val requestId: String,
    @SerializedName("audio_url")
    val audioUrl: String,
    @SerializedName("duration_ms")
    val durationMs: Int
)

data class HealthCheckResponse(
    @SerializedName("status")
    val status: String,
    @SerializedName("timestamp")
    val timestamp: String,
    @SerializedName("services")
    val services: Map<String, String>?
)

data class BackendHealthResponse(
    @SerializedName("ok")
    val ok: Boolean
)
