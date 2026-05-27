package com.omniveye.app.cloud

import org.junit.Assert.*
import org.junit.Test

class UploadQualityTest {

    @Test
    fun `FAST_ANALYZE has correct max dimension`() {
        assertEquals(1280, UploadQuality.FAST_ANALYZE.maxDimension)
    }

    @Test
    fun `FAST_ANALYZE has correct JPEG quality`() {
        assertEquals(75, UploadQuality.FAST_ANALYZE.jpegQuality)
    }

    @Test
    fun `HIGH_QUALITY has correct max dimension`() {
        assertEquals(2048, UploadQuality.HIGH_QUALITY.maxDimension)
    }

    @Test
    fun `HIGH_QUALITY has correct JPEG quality`() {
        assertEquals(85, UploadQuality.HIGH_QUALITY.jpegQuality)
    }

    @Test
    fun `enum has exactly two values`() {
        assertEquals(2, UploadQuality.entries.size)
    }

    @Test
    fun `enum values are in expected order`() {
        val values = UploadQuality.entries
        assertEquals(UploadQuality.FAST_ANALYZE, values[0])
        assertEquals(UploadQuality.HIGH_QUALITY, values[1])
    }

    @Test
    fun `valueOf returns correct enum for FAST_ANALYZE`() {
        assertEquals(UploadQuality.FAST_ANALYZE, UploadQuality.valueOf("FAST_ANALYZE"))
    }

    @Test
    fun `valueOf returns correct enum for HIGH_QUALITY`() {
        assertEquals(UploadQuality.HIGH_QUALITY, UploadQuality.valueOf("HIGH_QUALITY"))
    }

    @Test
    fun `FAST_ANALYZE produces smaller max dimension than HIGH_QUALITY`() {
        assertTrue(UploadQuality.FAST_ANALYZE.maxDimension < UploadQuality.HIGH_QUALITY.maxDimension)
    }

    @Test
    fun `FAST_ANALYZE produces lower JPEG quality than HIGH_QUALITY`() {
        assertTrue(UploadQuality.FAST_ANALYZE.jpegQuality < UploadQuality.HIGH_QUALITY.jpegQuality)
    }

    @Test
    fun `each enum has positive max dimension`() {
        for (quality in UploadQuality.entries) {
            assertTrue("maxDimension should be positive for ${quality.name}",
                quality.maxDimension > 0)
        }
    }

    @Test
    fun `each enum has valid JPEG quality range`() {
        for (quality in UploadQuality.entries) {
            assertTrue("jpegQuality should be between 0 and 100 for ${quality.name}",
                quality.jpegQuality in 1..100)
        }
    }

    @Test
    fun `enum names match expected values`() {
        assertEquals("FAST_ANALYZE", UploadQuality.FAST_ANALYZE.name)
        assertEquals("HIGH_QUALITY", UploadQuality.HIGH_QUALITY.name)
    }

    @Test
    fun `name property returns correct values`() {
        UploadQuality.entries.forEach { quality ->
            assertNotNull(quality.name)
            assertTrue(quality.name.isNotEmpty())
        }
    }

    @Test
    fun `ordinal property returns correct values`() {
        assertEquals(0, UploadQuality.FAST_ANALYZE.ordinal)
        assertEquals(1, UploadQuality.HIGH_QUALITY.ordinal)
    }

    @Test
    fun `toString returns name by default`() {
        assertEquals("FAST_ANALYZE", UploadQuality.FAST_ANALYZE.toString())
        assertEquals("HIGH_QUALITY", UploadQuality.HIGH_QUALITY.toString())
    }

    @Test
    fun `compareTo works correctly between qualities`() {
        assertTrue(UploadQuality.FAST_ANALYZE < UploadQuality.HIGH_QUALITY)
        assertFalse(UploadQuality.HIGH_QUALITY < UploadQuality.FAST_ANALYZE)
    }
}
