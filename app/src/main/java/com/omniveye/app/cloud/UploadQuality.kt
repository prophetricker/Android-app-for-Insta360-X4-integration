package com.omniveye.app.cloud

enum class UploadQuality(val maxDimension: Int, val jpegQuality: Int) {
    FAST_ANALYZE(1280, 75),  // Fast upload with good quality
    HIGH_QUALITY(2048, 85)   // Retain existing strategy
}
