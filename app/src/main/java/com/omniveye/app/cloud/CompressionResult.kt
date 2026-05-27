package com.omniveye.app.cloud

data class CompressionResult(
    val originalWidth: Int,
    val originalHeight: Int,
    val compressedWidth: Int,
    val compressedHeight: Int,
    val jpegSizeBytes: Int,
    val compressionMs: Long,
    val quality: UploadQuality
)
