package com.omniveye.app.cloud

enum class UploadImagePurpose {
    General,
    ObstacleAnalyze,
    SemanticAnalyze
}

data class UploadImageSpec(
    val maxLongEdge: Int,
    val jpegQuality: Int
)

fun selectUploadImageSpec(purpose: UploadImagePurpose): UploadImageSpec {
    return when (purpose) {
        UploadImagePurpose.ObstacleAnalyze -> UploadImageSpec(maxLongEdge = 960, jpegQuality = 72)
        UploadImagePurpose.SemanticAnalyze -> UploadImageSpec(maxLongEdge = 1280, jpegQuality = 78)
        UploadImagePurpose.General -> UploadImageSpec(maxLongEdge = 1920, jpegQuality = 85)
    }
}
