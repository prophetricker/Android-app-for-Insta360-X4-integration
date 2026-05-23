package com.omniveye.app.cloud

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

interface CloudApiService {

    @GET("health")
    suspend fun healthCheck(): Response<ApiResponse<HealthCheckResponse>>

    @Multipart
    @POST("v1/image/upload")
    suspend fun uploadImage(
        @Part image: MultipartBody.Part,
        @Part("description") description: RequestBody? = null
    ): Response<ApiResponse<ImageUploadResponse>>

    @GET("v1/image/{imageId}")
    suspend fun getImageStatus(
        @Path("imageId") imageId: String
    ): Response<ApiResponse<ImageProcessResult>>

    @GET("v1/image/{imageId}/result")
    suspend fun getImageResult(
        @Path("imageId") imageId: String
    ): Response<ApiResponse<ImageProcessResult>>

    @POST("v1/image/{imageId}/process")
    suspend fun processImage(
        @Path("imageId") imageId: String,
        @Body options: Map<String, Any>? = null
    ): Response<ApiResponse<ImageProcessResult>>

    @POST("v1/voice/process")
    suspend fun processVoice(
        @Body request: VoiceProcessRequest
    ): Response<ApiResponse<VoiceProcessResponse>>

    @POST("v1/tts/synthesize")
    suspend fun synthesizeSpeech(
        @Body request: TtsRequest
    ): Response<ApiResponse<TtsResponse>>

    @GET("v1/tts/{requestId}")
    suspend fun getTtsStatus(
        @Path("requestId") requestId: String
    ): Response<ApiResponse<TtsResponse>>
}

object CloudEndpoints {
    const val BASE_URL = "https://api.omniveye.example.com/"

    const val ENDPOINT_HEALTH = "health"
    const val ENDPOINT_IMAGE_UPLOAD = "v1/image/upload"
    const val ENDPOINT_IMAGE_STATUS = "v1/image/{imageId}"
    const val ENDPOINT_IMAGE_RESULT = "v1/image/{imageId}/result"
    const val ENDPOINT_IMAGE_PROCESS = "v1/image/{imageId}/process"
    const val ENDPOINT_VOICE_PROCESS = "v1/voice/process"
    const val ENDPOINT_TTS_SYNTHESIZE = "v1/tts/synthesize"
    const val ENDPOINT_TTS_STATUS = "v1/tts/{requestId}"
}
