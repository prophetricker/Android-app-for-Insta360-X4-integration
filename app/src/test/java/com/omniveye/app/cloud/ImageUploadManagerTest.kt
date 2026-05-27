package com.omniveye.app.cloud

import android.content.Context
import android.graphics.Bitmap
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File

class ImageUploadManagerTest {

    @Mock
    private lateinit var mockContext: Context

    @Mock
    private lateinit var mockCacheDir: File

    private lateinit var imageUploadManager: ImageUploadManager

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(mockContext.cacheDir).thenReturn(mockCacheDir)
        imageUploadManager = ImageUploadManager(mockContext)
    }

    @Test
    fun `compressWithQuality returns correct result for FAST_ANALYZE quality`() {
        val bitmap = createMockBitmap(2560, 1440)

        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)

        assertEquals(2560, result.originalWidth)
        assertEquals(1440, result.originalHeight)
        assertEquals(1280, result.compressedWidth)
        assertTrue(result.compressionMs >= 0)
        assertTrue(result.jpegSizeBytes > 0)
        assertEquals(UploadQuality.FAST_ANALYZE, result.quality)
    }

    @Test
    fun `compressWithQuality returns correct result for HIGH_QUALITY quality`() {
        val bitmap = createMockBitmap(2560, 1440)

        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.HIGH_QUALITY)

        assertEquals(2560, result.originalWidth)
        assertEquals(1440, result.originalHeight)
        assertEquals(2048, result.compressedWidth)
        assertTrue(result.compressionMs >= 0)
        assertTrue(result.jpegSizeBytes > 0)
        assertEquals(UploadQuality.HIGH_QUALITY, result.quality)
    }

    @Test
    fun `compressWithQuality does not resize when dimensions are within limit`() {
        val bitmap = createMockBitmap(800, 600)

        val resultFast = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)
        val resultHigh = imageUploadManager.compressWithQuality(bitmap, UploadQuality.HIGH_QUALITY)

        assertEquals(800, resultFast.compressedWidth)
        assertEquals(600, resultFast.compressedHeight)
        assertEquals(800, resultHigh.compressedWidth)
        assertEquals(600, resultHigh.compressedHeight)
    }

    @Test
    fun `compressWithQuality respects portrait orientation`() {
        val bitmap = createMockBitmap(1440, 2560)

        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)

        assertEquals(1280, result.compressedHeight)
        assertEquals(720, result.compressedWidth)
    }

    @Test
    fun `compressWithQuality measures compression time`() {
        val bitmap = createMockBitmap(1920, 1080)

        val startTime = System.currentTimeMillis()
        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.HIGH_QUALITY)
        val endTime = System.currentTimeMillis()

        assertTrue(result.compressionMs >= 0)
        assertTrue(result.compressionMs <= (endTime - startTime) + 100)
    }

    @Test
    fun `compressWithQuality produces smaller JPEG for FAST_ANALYZE than HIGH_QUALITY`() {
        val bitmap = createMockBitmap(2048, 1536)

        val resultFast = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)
        val resultHigh = imageUploadManager.compressWithQuality(bitmap, UploadQuality.HIGH_QUALITY)

        assertTrue("FAST_ANALYZE should produce smaller or equal file size",
            resultFast.jpegSizeBytes <= resultHigh.jpegSizeBytes)
    }

    @Test
    fun `compressImage resizes large images correctly`() {
        val bitmap = createMockBitmap(3840, 2160)

        val result = imageUploadManager.compressImage(bitmap)

        assertEquals(1920, result.width)
        assertEquals(1080, result.height)
    }

    @Test
    fun `compressImage preserves small images`() {
        val bitmap = createMockBitmap(800, 600)

        val result = imageUploadManager.compressImage(bitmap)

        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }

    @Test
    fun `bitmapToByteArray produces valid JPEG bytes`() {
        val bitmap = createMockBitmap(640, 480)

        val bytes = imageUploadManager.bitmapToByteArray(bitmap)

        assertNotNull(bytes)
        assertTrue(bytes.isNotEmpty())
        assertEquals(0xFF.toByte(), bytes[0])
        assertEquals(0xD8.toByte(), bytes[1])
    }

    @Test
    fun `compressWithQuality includes correct quality in result`() {
        for (quality in UploadQuality.entries) {
            val bitmap = createMockBitmap(1024, 768)
            val result = imageUploadManager.compressWithQuality(bitmap, quality)
            assertEquals("Quality should match for ${quality.name}", quality, result.quality)
        }
    }

    @Test
    fun `compressWithQuality handles square images correctly`() {
        val bitmap = createMockBitmap(2048, 2048)

        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)

        assertEquals(1280, result.compressedWidth)
        assertEquals(1280, result.compressedHeight)
    }

    @Test
    fun `compressWithQuality handles exact boundary dimensions`() {
        val bitmap = createMockBitmap(1280, 720)

        val result = imageUploadManager.compressWithQuality(bitmap, UploadQuality.FAST_ANALYZE)

        assertEquals(1280, result.compressedWidth)
        assertEquals(720, result.compressedHeight)
    }

    private fun createMockBitmap(width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    }
}
