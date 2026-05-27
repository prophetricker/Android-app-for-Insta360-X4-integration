package com.omniveye.app.cloud

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitHubStorageManager(
    private val config: GitHubConfig,
    private val apiService: GitHubApiService
) {
    companion object {
        private const val TAG = "GitHubStorageManager"
        private const val FOLDER_PATH = "OmniEye"  // GitHub上的文件夹名
        private const val IMAGE_PREFIX = "IMG_"
        private const val IMAGE_EXTENSION = ".jpg"
        private const val MAX_FILE_SIZE = 1024 * 1024 * 10  // 10MB
        private const val COMPRESSION_QUALITY = 85
    }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    /**
     * 上传图片到GitHub
     * @param bitmap 要上传的图片
     * @param customFileName 可选的自定义文件名
     * @return 上传结果
     */
    suspend fun uploadImage(bitmap: Bitmap, customFileName: String? = null): GitHubUploadResult = withContext(Dispatchers.IO) {
        try {
            val fileName = customFileName ?: generateFileName()
            val filePath = "$FOLDER_PATH/$fileName"

            Log.d(TAG, "Uploading image: $filePath")

            // 压缩并转换为Base64
            val base64Content = bitmapToBase64(bitmap)

            // 检查是否文件已存在（获取SHA）
            val existingFile = apiService.getFile(filePath)
            val sha = existingFile?.content?.sha

            // 上传文件
            val message = if (sha != null) {
                "Update image: $fileName"
            } else {
                "Upload image: $fileName"
            }

            val response = apiService.createOrUpdateFile(
                path = filePath,
                content = base64Content,
                message = message,
                sha = sha
            )

            if (response != null && response.content != null) {
                Log.d(TAG, "Upload successful: ${response.content.downloadUrl}")
                GitHubUploadResult(
                    success = true,
                    fileName = fileName,
                    filePath = filePath,
                    downloadUrl = response.content.downloadUrl,
                    sha = response.content.sha
                )
            } else {
                Log.e(TAG, "Upload failed: response is null")
                GitHubUploadResult(
                    success = false,
                    fileName = fileName,
                    filePath = filePath,
                    downloadUrl = null,
                    sha = null,
                    errorMessage = "Upload failed: empty response"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload error", e)
            GitHubUploadResult(
                success = false,
                fileName = customFileName ?: "unknown",
                filePath = "$FOLDER_PATH/${customFileName ?: "unknown"}",
                downloadUrl = null,
                sha = null,
                errorMessage = e.message ?: "Unknown error"
            )
        }
    }

    /**
     * 批量上传图片
     */
    suspend fun uploadImages(bitmaps: List<Bitmap>): List<GitHubUploadResult> = withContext(Dispatchers.IO) {
        bitmaps.mapIndexed { index, bitmap ->
            uploadImage(bitmap, "${IMAGE_PREFIX}batch_${System.currentTimeMillis()}_$index$IMAGE_EXTENSION")
        }
    }

    /**
     * 获取已上传的图片列表
     */
    suspend fun listImages(): List<GitHubContent>? = withContext(Dispatchers.IO) {
        try {
            apiService.listDirectory(FOLDER_PATH)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list images", e)
            null
        }
    }

    /**
     * 删除图片
     */
    suspend fun deleteImage(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val filePath = "$FOLDER_PATH/$fileName"
            val existingFile = apiService.getFile(filePath)
            val sha = existingFile?.content?.sha

            if (sha != null) {
                apiService.deleteFile(filePath, sha, "Delete image: $fileName")
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete image", e)
            false
        }
    }

    /**
     * 生成文件名
     */
    private fun generateFileName(): String {
        val timestamp = dateFormat.format(Date())
        return "$IMAGE_PREFIX$timestamp$IMAGE_EXTENSION"
    }

    /**
     * 将Bitmap转换为Base64字符串
     */
    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()

        // 如果图片太大，先压缩
        val maxDimension = 2048
        val scaledBitmap = if (bitmap.width > maxDimension || bitmap.height > maxDimension) {
            val ratio = minOf(
                maxDimension.toFloat() / bitmap.width,
                maxDimension.toFloat() / bitmap.height
            )
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * ratio).toInt(),
                (bitmap.height * ratio).toInt(),
                true
            )
        } else {
            bitmap
        }

        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, outputStream)

        // 如果原图被缩放过，需要回收
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }

        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    /**
     * 获取图片的下载URL（不上传，只获取预签名URL）
     */
    fun getImageUrl(fileName: String): String {
        return "https://raw.githubusercontent.com/${config.owner}/${config.repo}/${config.branch}/$FOLDER_PATH/$fileName"
    }
}
