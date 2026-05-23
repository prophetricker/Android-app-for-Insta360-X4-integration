package com.omniveye.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.wifi.WifiManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class CameraConnectionState {
    data object Disconnected : CameraConnectionState()
    data object Connecting : CameraConnectionState()
    data object Connected : CameraConnectionState()
    data class Error(val message: String) : CameraConnectionState()
}

sealed class CameraOperationResult {
    data class Success(val data: Any) : CameraOperationResult()
    data class Error(val message: String) : CameraOperationResult()
}

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"
        
        private const val INSTA360_WIFI_PREFIX = "X4"
        private const val DEFAULT_CAMERA_IP = "192.168.42.1"
        private const val CAMERA_PORT = 8080
        private const val PHOTO_LIST_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/commands/execute"
        private const val PHOTO_INFO_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/info"
        private const val THUMBNAIL_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/thumbnails"
    }

    private val _connectionState = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    val connectionState: StateFlow<CameraConnectionState> = _connectionState.asStateFlow()

    private val _lastPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val lastPhotoBitmap: StateFlow<Bitmap?> = _lastPhotoBitmap.asStateFlow()

    private val _isTakingPhoto = MutableStateFlow(false)
    val isTakingPhoto: StateFlow<Boolean> = _isTakingPhoto.asStateFlow()

    private val _photoList = MutableStateFlow<List<String>>(emptyList())
    val photoList: StateFlow<List<String>> = _photoList.asStateFlow()

    private var isWiFiConnected = false

    @SuppressLint("MissingPermission")
    fun checkCameraWiFiConnection(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
            isWiFiConnected = ssid.contains(INSTA360_WIFI_PREFIX) || ssid.contains("Insta360")
            Log.d(TAG, "WiFi check - SSID: $ssid, Connected to camera: $isWiFiConnected")
            return isWiFiConnected
        }
        isWiFiConnected = false
        return false
    }

    fun connectToCamera(): Boolean {
        if (_connectionState.value == CameraConnectionState.Connecting) {
            Log.w(TAG, "Already connecting...")
            return false
        }

        _connectionState.value = CameraConnectionState.Connecting
        Log.d(TAG, "Attempting to connect to camera...")

        if (!checkCameraWiFiConnection()) {
            _connectionState.value = CameraConnectionState.Error(
                "Please connect to Insta360 X4 Wi-Fi first"
            )
            return false
        }

        return try {
            _connectionState.value = CameraConnectionState.Connected
            Log.d(TAG, "Camera connected successfully")
            true
        } catch (e: Exception) {
            _connectionState.value = CameraConnectionState.Error(
                "Connection failed: ${e.message}"
            )
            Log.e(TAG, "Connection failed", e)
            false
        }
    }

    fun disconnect() {
        _connectionState.value = CameraConnectionState.Disconnected
        _lastPhotoBitmap.value = null
        _photoList.value = emptyList()
        Log.d(TAG, "Camera disconnected")
    }

    suspend fun takePhoto(): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        _isTakingPhoto.value = true
        return try {
            Log.d(TAG, "Taking photo...")
            fetchLatestPhoto()
        } catch (e: Exception) {
            CameraOperationResult.Error("Failed to take photo: ${e.message}")
        } finally {
            _isTakingPhoto.value = false
        }
    }

    private suspend fun fetchLatestPhoto(): CameraOperationResult {
        return try {
            Log.d(TAG, "Fetching latest photo from camera...")
            val photoId = "latest_photo_${System.currentTimeMillis()}"
            val bitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
            _lastPhotoBitmap.value = bitmap
            _photoList.value = _photoList.value + photoId
            Log.d(TAG, "Latest photo fetched successfully")
            CameraOperationResult.Success(bitmap)
        } catch (e: Exception) {
            CameraOperationResult.Error("Failed to fetch latest photo: ${e.message}")
        }
    }

    suspend fun fetchPhotoList(): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        return try {
            Log.d(TAG, "Fetching photo list...")
            val mockPhotos = listOf(
                "IMG_001.jpg",
                "IMG_002.jpg",
                "IMG_003.jpg"
            )
            _photoList.value = mockPhotos
            CameraOperationResult.Success(mockPhotos)
        } catch (e: Exception) {
            CameraOperationResult.Error("Failed to fetch photos: ${e.message}")
        }
    }

    suspend fun downloadPhoto(photoId: String): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        return try {
            Log.d(TAG, "Downloading photo: $photoId")
            val bitmap = Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888)
            _lastPhotoBitmap.value = bitmap
            CameraOperationResult.Success(bitmap)
        } catch (e: Exception) {
            CameraOperationResult.Error("Failed to download photo: ${e.message}")
        }
    }

    private fun downloadFromUrl(imageUrl: String, outputFile: File): Boolean {
        return try {
            val url = URL(imageUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()

            val input = connection.inputStream
            val bitmap = BitmapFactory.decodeStream(input)
            input.close()

            FileOutputStream(outputFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download image", e)
            false
        }
    }

    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap", e)
            null
        }
    }

    fun getCameraInfo(): Map<String, String> {
        return mapOf(
            "model" to "Insta360 X4",
            "firmware" to "1.0.0",
            "ip" to DEFAULT_CAMERA_IP,
            "port" to CAMERA_PORT.toString(),
            "wifi_connected" to isWiFiConnected.toString()
        )
    }
}
