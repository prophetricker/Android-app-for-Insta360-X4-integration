package com.omniveye.app.cloud

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地同步管理器
 * 负责管理本地OmniEye文件夹，与GitHub保持同步
 */
class LocalSyncManager(private val context: Context) {

    companion object {
        private const val TAG = "LocalSyncManager"
        private const val FOLDER_NAME = "OmniEye-GitHub"
        private const val SYNC_FILE_NAME = ".sync_metadata"
        private const val COMPRESSION_QUALITY = 90
    }

    private val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _localFiles = MutableStateFlow<List<LocalFileInfo>>(emptyList())
    val localFiles: StateFlow<List<LocalFileInfo>> = _localFiles.asStateFlow()

    /**
     * 获取本地同步文件夹路径
     */
    fun getSyncFolder(): File {
        val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        val syncDir = File(picturesDir, FOLDER_NAME)
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }
        return syncDir
    }

    /**
     * 获取应用私有同步文件夹
     */
    fun getPrivateSyncFolder(): File {
        val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val syncDir = File(picturesDir, FOLDER_NAME)
        if (!syncDir.exists()) {
            syncDir.mkdirs()
        }
        return syncDir
    }

    /**
     * 保存图片到本地同步文件夹
     */
    suspend fun saveImageLocally(bitmap: Bitmap, fileName: String? = null): LocalFileInfo? = withContext(Dispatchers.IO) {
        try {
            val name = fileName ?: generateFileName()
            val file = File(getSyncFolder(), name)
            
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos)
                fos.flush()
            }
            
            val fileInfo = LocalFileInfo(
                fileName = name,
                filePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                syncedToCloud = false
            )
            
            updateLocalFilesList()
            Log.d(TAG, "Image saved locally: ${file.absolutePath}")
            fileInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save image locally", e)
            null
        }
    }

    /**
     * 保存图片到应用私有目录（不需要存储权限）
     */
    suspend fun saveImageToPrivateStorage(bitmap: Bitmap, fileName: String? = null): LocalFileInfo? = withContext(Dispatchers.IO) {
        try {
            val name = fileName ?: generateFileName()
            val file = File(getPrivateSyncFolder(), name)
            
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, COMPRESSION_QUALITY, fos)
                fos.flush()
            }
            
            val fileInfo = LocalFileInfo(
                fileName = name,
                filePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                syncedToCloud = false
            )
            
            updateLocalFilesList()
            Log.d(TAG, "Image saved to private storage: ${file.absolutePath}")
            fileInfo
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to private storage", e)
            null
        }
    }

    /**
     * 从GitHub下载同步图片到本地
     */
    suspend fun downloadFromCloud(downloadUrl: String, fileName: String): LocalFileInfo? = withContext(Dispatchers.IO) {
        try {
            _syncState.value = SyncState.Syncing("下载中: $fileName")
            
            val url = java.net.URL(downloadUrl)
            val connection = url.openConnection()
            connection.connect()
            
            val inputStream = connection.getInputStream()
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()
            
            if (bitmap != null) {
                val localFile = saveImageLocally(bitmap, fileName)
                if (localFile != null) {
                    val updatedFile = localFile.copy(syncedToCloud = true)
                    updateSyncMetadata(updatedFile)
                    _syncState.value = SyncState.Synced
                    return@withContext updatedFile
                }
            }
            
            _syncState.value = SyncState.Error("下载失败")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download from cloud", e)
            _syncState.value = SyncState.Error(e.message ?: "下载失败")
            null
        }
    }

    /**
     * 标记文件为已同步到云端
     */
    fun markAsSynced(fileName: String, cloudUrl: String) {
        val file = File(getSyncFolder(), fileName)
        if (file.exists()) {
            val fileInfo = LocalFileInfo(
                fileName = fileName,
                filePath = file.absolutePath,
                size = file.length(),
                lastModified = file.lastModified(),
                syncedToCloud = true,
                cloudUrl = cloudUrl
            )
            updateSyncMetadata(fileInfo)
            updateLocalFilesList()
        }
    }

    /**
     * 更新本地文件列表
     */
    fun updateLocalFilesList() {
        val folder = getSyncFolder()
        if (folder.exists()) {
            val files = folder.listFiles { file -> 
                file.isFile && file.extension.lowercase() in listOf("jpg", "jpeg", "png")
            }?.map { file ->
                val metadata = getSyncMetadata(file.name)
                LocalFileInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    size = file.length(),
                    lastModified = file.lastModified(),
                    syncedToCloud = metadata?.syncedToCloud ?: false,
                    cloudUrl = metadata?.cloudUrl
                )
            } ?: emptyList()
            
            _localFiles.value = files.sortedByDescending { it.lastModified }
        }
    }

    /**
     * 获取未同步的文件
     */
    fun getUnsyncedFiles(): List<LocalFileInfo> {
        return _localFiles.value.filter { !it.syncedToCloud }
    }

    /**
     * 删除本地文件
     */
    suspend fun deleteLocalFile(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(getSyncFolder(), fileName)
            val result = file.delete()
            if (result) {
                removeSyncMetadata(fileName)
                updateLocalFilesList()
            }
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete file", e)
            false
        }
    }

    /**
     * 获取同步元数据
     */
    private fun getSyncMetadata(fileName: String): LocalFileInfo? {
        val metadataFile = File(getSyncFolder(), "$SYNC_FILE_NAME.json")
        if (!metadataFile.exists()) return null
        
        return try {
            val json = metadataFile.readText()
            val gson = com.google.gson.Gson()
            val metadata = gson.fromJson(json, SyncMetadata::class.java)
            metadata.files[fileName]
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 更新同步元数据
     */
    private fun updateSyncMetadata(fileInfo: LocalFileInfo) {
        val metadataFile = File(getSyncFolder(), "$SYNC_FILE_NAME.json")
        val gson = com.google.gson.Gson()
        
        val metadata = if (metadataFile.exists()) {
            try {
                gson.fromJson(metadataFile.readText(), SyncMetadata::class.java)
            } catch (e: Exception) {
                SyncMetadata()
            }
        } else {
            SyncMetadata()
        }
        
        metadata.files[fileInfo.fileName] = fileInfo
        metadataFile.writeText(gson.toJson(metadata))
    }

    /**
     * 移除同步元数据
     */
    private fun removeSyncMetadata(fileName: String) {
        val metadataFile = File(getSyncFolder(), "$SYNC_FILE_NAME.json")
        if (!metadataFile.exists()) return
        
        try {
            val gson = com.google.gson.Gson()
            val metadata = gson.fromJson(metadataFile.readText(), SyncMetadata::class.java)
            metadata.files.remove(fileName)
            metadataFile.writeText(gson.toJson(metadata))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove metadata", e)
        }
    }

    /**
     * 生成文件名
     */
    private fun generateFileName(): String {
        return "IMG_${dateFormat.format(Date())}.jpg"
    }

    /**
     * 清理旧文件（保留最近N个）
     */
    suspend fun cleanupOldFiles(keepCount: Int = 100) = withContext(Dispatchers.IO) {
        val files = getUnsyncedFiles()
        if (files.size > keepCount) {
            files.drop(keepCount).forEach { file ->
                deleteLocalFile(file.fileName)
            }
        }
    }

    /**
     * 获取同步状态统计
     */
    fun getSyncStats(): SyncStats {
        val allFiles = _localFiles.value
        return SyncStats(
            totalFiles = allFiles.size,
            syncedFiles = allFiles.count { it.syncedToCloud },
            unsyncedFiles = allFiles.count { !it.syncedToCloud },
            totalSize = allFiles.sumOf { it.size }
        )
    }
}

/**
 * 本地文件信息
 */
data class LocalFileInfo(
    val fileName: String,
    val filePath: String,
    val size: Long,
    val lastModified: Long,
    val syncedToCloud: Boolean = false,
    val cloudUrl: String? = null
)

/**
 * 同步状态
 */
sealed class SyncState {
    data object Idle : SyncState()
    data class Syncing(val message: String) : SyncState()
    data object Synced : SyncState()
    data class Error(val message: String) : SyncState()
}

/**
 * 同步统计
 */
data class SyncStats(
    val totalFiles: Int,
    val syncedFiles: Int,
    val unsyncedFiles: Int,
    val totalSize: Long
)

/**
 * 同步元数据
 */
data class SyncMetadata(
    val lastSyncTime: Long = System.currentTimeMillis(),
    val files: MutableMap<String, LocalFileInfo> = mutableMapOf()
)
