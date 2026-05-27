package com.omniveye.app.cloud

data class UploadMetrics(
    val originalWidth: Int,
    val originalHeight: Int,
    val compressedWidth: Int,
    val compressedHeight: Int,
    val jpegSizeBytes: Int,
    val compressionMs: Long,
    val requestMs: Long,
    val backendLatencyMs: Long,
    val totalMs: Long,
    val quality: UploadQuality
)
