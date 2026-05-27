package com.omniveye.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUploadSpecTest {

    @Test
    fun obstacleAnalysisUsesSmallFastUploadSpec() {
        val spec = selectUploadImageSpec(UploadImagePurpose.ObstacleAnalyze)

        assertEquals(960, spec.maxLongEdge)
        assertEquals(72, spec.jpegQuality)
    }

    @Test
    fun semanticAnalysisUsesMediumUploadSpec() {
        val spec = selectUploadImageSpec(UploadImagePurpose.SemanticAnalyze)

        assertEquals(1280, spec.maxLongEdge)
        assertEquals(78, spec.jpegQuality)
    }

    @Test
    fun generalUploadKeepsHighQualitySpec() {
        val spec = selectUploadImageSpec(UploadImagePurpose.General)

        assertEquals(1920, spec.maxLongEdge)
        assertEquals(85, spec.jpegQuality)
    }
}
