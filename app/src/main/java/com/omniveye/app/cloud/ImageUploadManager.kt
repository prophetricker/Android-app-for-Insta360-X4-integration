package com.omniveye.app.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.system.measureTimeMillis

fun createAnalyzeFramePart(file: File): MultipartBody.Part {
    val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
    return MultipartBody.Part.createFormData("frame", file.name, requestFile)
}

fun createSemanticTextPart(value: String): okhttp3.RequestBody {
    return value.toRequestBody("text/plain".toMediaTypeOrNull())
}

fun readRequestBody(body: okhttp3.RequestBody): String {
    val buffer = Buffer()
    body.writeTo(buffer)
    return buffer.readUtf8()
}

class ImageUploadManager(private val context: Context) {

    companion object {
        private const val TAG = "ImageUploadManager"
    }

    fun compressImage(
        bitmap: Bitmap,
        spec: UploadImageSpec = selectUploadImageSpec(UploadImagePurpose.General)
    ): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val ratio = width.toFloat() / height.toFloat()

        val newWidth: Int
        val newHeight: Int

        if (width > height) {
            newWidth = minOf(width, spec.maxLongEdge)
            newHeight = (newWidth / ratio).toInt()
        } else {
            newHeight = minOf(height, spec.maxLongEdge)
            newWidth = (newHeight * ratio).toInt()
        }

        return if (width > spec.maxLongEdge || height > spec.maxLongEdge) {
            Log.d(TAG, "Resizing image from ${width}x${height} to ${newWidth}x${newHeight}")
            Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        } else {
            bitmap
        }
    }

    fun bitmapToByteArray(
        bitmap: Bitmap,
        format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG,
        quality: Int = selectUploadImageSpec(UploadImagePurpose.General).jpegQuality
    ): ByteArray {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(format, quality, outputStream)
        return outputStream.toByteArray()
    }

    fun bitmapToFile(
        bitmap: Bitmap,
        fileName: String = "image_${System.currentTimeMillis()}.jpg",
        quality: Int = selectUploadImageSpec(UploadImagePurpose.General).jpegQuality
    ): File {
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, fos)
        }
        Log.d(TAG, "Saved bitmap to: ${file.absolutePath}, size: ${file.length()} bytes")
        return file
    }

    fun fileToBitmap(file: File): Bitmap? {
        return try {
            BitmapFactory.decodeFile(file.absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode file to bitmap", e)
            null
        }
    }

    suspend fun prepareForUpload(
        bitmap: Bitmap,
        purpose: UploadImagePurpose = UploadImagePurpose.General
    ): File = withContext(Dispatchers.IO) {
        val spec = selectUploadImageSpec(purpose)
        var compressed: Bitmap = bitmap
        val resizeMs = measureTimeMillis {
            compressed = compressImage(bitmap, spec)
        }
        var file: File
        val encodeMs = measureTimeMillis {
            file = bitmapToFile(
                bitmap = compressed,
                quality = spec.jpegQuality,
            )
        }
        Log.d(
            TAG,
            "UploadPrepare purpose=$purpose original=${bitmap.width}x${bitmap.height} " +
                "encoded=${compressed.width}x${compressed.height} maxLongEdge=${spec.maxLongEdge} " +
                "quality=${spec.jpegQuality} bytes=${file.length()} resizeMs=$resizeMs encodeMs=$encodeMs"
        )
        file
    }

    fun createMultipartBody(file: File, fieldName: String = "image"): MultipartBody.Part {
        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        return MultipartBody.Part.createFormData(fieldName, file.name, requestFile)
    }

    fun createDescriptionBody(description: String?): okhttp3.RequestBody? {
        return description?.toRequestBody("text/plain".toMediaTypeOrNull())
    }

    suspend fun downloadImage(url: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val connection = java.net.URL(url).openConnection()
            connection.connect()
            val input = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image from: $url", e)
            null
        }
    }

    fun getImageInfo(file: File): Map<String, Any> {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        return mapOf(
            "width" to options.outWidth,
            "height" to options.outHeight,
            "mimeType" to (options.outMimeType ?: "unknown"),
            "fileSize" to file.length(),
            "fileName" to file.name
        )
    }

    fun cleanupCache() {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("image_") && file.name.endsWith(".jpg")) {
                file.delete()
            }
        }
        Log.d(TAG, "Cache cleaned up")
    }
}
