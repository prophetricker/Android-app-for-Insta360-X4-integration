package com.omniveye.app.demo

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

data class DevelopmentSampleFrameSpec(
    val label: String,
    val width: Int,
    val height: Int,
    val overlayText: String
) {
    val aspectRatio: Float = width.toFloat() / height.toFloat()
}

object DevelopmentSampleFrame {
    val default = DevelopmentSampleFrameSpec(
        label = "开发样张",
        width = 1024,
        height = 512,
        overlayText = "OmniEye Dev Panorama Sample"
    )

    fun createBitmap(spec: DevelopmentSampleFrameSpec = default): Bitmap {
        val bitmap = Bitmap.createBitmap(spec.width, spec.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val width = spec.width.toFloat()
        val height = spec.height.toFloat()

        paint.color = Color.rgb(32, 43, 52)
        canvas.drawRect(0f, 0f, width, height * 0.55f, paint)
        paint.color = Color.rgb(80, 92, 78)
        canvas.drawRect(0f, height * 0.55f, width, height, paint)

        paint.color = Color.rgb(145, 78, 68)
        canvas.drawRect(width * 0.42f, height * 0.46f, width * 0.58f, height * 0.97f, paint)
        paint.color = Color.rgb(230, 185, 82)
        canvas.drawCircle(width * 0.25f, height * 0.45f, height * 0.11f, paint)
        paint.color = Color.rgb(68, 132, 160)
        canvas.drawRect(width * 0.70f, height * 0.34f, width * 0.88f, height * 0.82f, paint)

        paint.color = Color.WHITE
        paint.textSize = 36f
        canvas.drawText(spec.overlayText, width * 0.28f, height * 0.16f, paint)
        paint.textSize = 25f
        canvas.drawText("2:1 panorama placeholder for /analyze", width * 0.30f, height * 0.24f, paint)

        return bitmap
    }
}
