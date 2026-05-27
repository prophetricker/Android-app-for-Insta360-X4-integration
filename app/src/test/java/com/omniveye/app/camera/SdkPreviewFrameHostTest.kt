package com.omniveye.app.camera

import java.nio.ByteBuffer
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class SdkPreviewFrameHostTest {

    @Test
    fun copiesRgbaBytesForExpectedFrameSize() {
        val expected = byteArrayOf(
            255.toByte(), 0, 0, 255.toByte(),
            0, 255.toByte(), 0, 255.toByte()
        )
        val buffer = ByteBuffer.allocate(expected.size)
        buffer.put(expected)
        buffer.flip()

        val actual = copyRgbaBytes(buffer, width = 2, height = 1)

        assertArrayEquals(expected, actual)
    }
}
