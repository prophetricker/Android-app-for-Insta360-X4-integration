package com.omniveye.app.camera

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.wifi.WifiManager
import android.os.Environment
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class CameraConnectionState {
    data object Disconnected : CameraConnectionState()
    data object Connecting : CameraConnectionState()
    data object Connected : CameraConnectionState()
    data class Error(val message: String) : CameraConnectionState()
}

sealed class CameraOperationResult {
    data class Success(val data: Any, val savedPath: String? = null) : CameraOperationResult()
    data class Error(val message: String) : CameraOperationResult()
}

fun x4OscInfoUrl(): String = "http://192.168.42.1:80/osc/info"

fun x4OscCommandStatusUrl(): String = "http://192.168.42.1:80/osc/commands/status"

fun x4OscCommandStatusBody(commandId: String): String =
    """{"id":"${commandId.toJsonStringContent()}"}"""

private fun String.toJsonStringContent(): String =
    buildString {
        for (char in this@toJsonStringContent) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
    }

fun shouldAttemptX4OscConnection(isWifiEnabled: Boolean): Boolean = isWifiEnabled

fun shouldTreatWifiAsEnabledForCamera(isWifiEnabled: Boolean, networkId: Int): Boolean {
    return isWifiEnabled
}

class CameraManager(private val context: Context) {

    companion object {
        private const val TAG = "CameraManager"

        private const val INSTA360_WIFI_PREFIX = "X4"
        private const val DEFAULT_CAMERA_IP = "192.168.42.1"
        private const val CAMERA_PORT = 80  // Insta360 X4 uses port 80, not 8080!

        private const val OSC_INFO_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/info"
        private const val OSC_COMMAND_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/commands/execute"
        private const val OSC_STATE_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/state"
        private const val OSC_THUMBNAILS_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/thumbnails"
        private const val OSC_FILES_URL = "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/osc/files"

        private const val DEFAULT_CAPTURE_INTERVAL = 500L
        private const val CONNECT_TIMEOUT = 10000
        private const val READ_TIMEOUT = 60000  // Photos may take time to save

        private const val POLL_INTERVAL_MS = 500L
        private const val MAX_POLL_ATTEMPTS = 60  // 30 seconds max wait
    }

    private val _connectionState = MutableStateFlow<CameraConnectionState>(CameraConnectionState.Disconnected)
    val connectionState: StateFlow<CameraConnectionState> = _connectionState.asStateFlow()

    private val _lastPhotoBitmap = MutableStateFlow<Bitmap?>(null)
    val lastPhotoBitmap: StateFlow<Bitmap?> = _lastPhotoBitmap.asStateFlow()

    private val _isTakingPhoto = MutableStateFlow(false)
    val isTakingPhoto: StateFlow<Boolean> = _isTakingPhoto.asStateFlow()

    private val _photoList = MutableStateFlow<List<String>>(emptyList())
    val photoList: StateFlow<List<String>> = _photoList.asStateFlow()

    private val _isContinuousCapture = MutableStateFlow(false)
    val isContinuousCapture: StateFlow<Boolean> = _isContinuousCapture.asStateFlow()

    private val _capturedPhotoCount = MutableStateFlow(0)
    val capturedPhotoCount: StateFlow<Int> = _capturedPhotoCount.asStateFlow()

    private val _lastSavedPhotoPath = MutableStateFlow<String?>(null)
    val lastSavedPhotoPath: StateFlow<String?> = _lastSavedPhotoPath.asStateFlow()

    private var isWiFiConnected = false
    private var continuousCaptureJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var cameraSessionId: String? = null

    @SuppressLint("MissingPermission")
    fun checkCameraWiFiConnection(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo

        Log.d(TAG, "WiFi check - networkId: ${wifiInfo?.networkId}, wifiEnabled: ${wifiManager.isWifiEnabled}")

        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val ssid = wifiInfo.ssid?.removeSurrounding("\"") ?: ""
            val ssidUnknown = ssid == "<unknown ssid>" || ssid.isBlank()

            if (ssidUnknown) {
                Log.w(TAG, "WiFi SSID is unknown - possible causes:")
                Log.w(TAG, "  1. Location permission not granted")
                Log.w(TAG, "  2. Android 10+ requires location permission + GPS enabled")
                Log.w(TAG, "  3. App must have 'Coarse location' or 'Fine location' enabled in settings")
                isWiFiConnected = false
                return false
            }

            isWiFiConnected = ssid.contains(INSTA360_WIFI_PREFIX) || ssid.contains("Insta360")
            Log.d(TAG, "WiFi check - SSID: $ssid, Connected to camera: $isWiFiConnected")
            return isWiFiConnected
        }
        isWiFiConnected = false
        Log.d(TAG, "WiFi check - Not connected to any network")
        return false
    }

    fun getConnectionDiagnostic(): String {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        val ssid = wifiInfo?.ssid?.removeSurrounding("\"") ?: "N/A"
        return buildString {
            appendLine("=== Wi-Fi Diagnostic ===")
            appendLine("Wi-Fi Enabled: ${wifiManager.isWifiEnabled}")
            appendLine("Network ID: ${wifiInfo?.networkId ?: "N/A"}")
            appendLine("SSID: $ssid")
            appendLine("IP: ${wifiInfo?.ipAddress?.let { "${it and 0xFF}.${it shr 8 and 0xFF}.${it shr 16 and 0xFF}.${it shr 24 and 0xFF}" } ?: "N/A"}")
            appendLine("Location Permission: ${hasLocationPermission()}")
            appendLine("========================")
        }
    }

    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun connectToCamera(): Boolean {
        if (_connectionState.value == CameraConnectionState.Connecting) {
            Log.w(TAG, "Already connecting...")
            return false
        }

        _connectionState.value = CameraConnectionState.Connecting
        Log.d(TAG, "Attempting to connect to camera...")

        val wifiCheck = checkWiFiEnabled()
        Log.d(TAG, "WiFi status: enabled=$wifiCheck")

        if (!wifiCheck) {
            _connectionState.value = CameraConnectionState.Error(
                "Wi-Fi is disabled. Please enable Wi-Fi."
            )
            return false
        }

        return try {
            scope.launch {
                if (checkCameraConnection()) {
                    _connectionState.value = CameraConnectionState.Connected
                    Log.d(TAG, "Camera connected successfully")
                    fetchCameraState()
                } else {
                    _connectionState.value = CameraConnectionState.Error(
                        "Cannot connect to camera.\n\n" +
                        "Please check:\n" +
                        "1. Connected to Insta360 X4 Wi-Fi?\n" +
                        "2. Camera is powered on?\n" +
                        "3. Wi-Fi signal is strong?"
                    )
                }
            }
            true
        } catch (e: Exception) {
            _connectionState.value = CameraConnectionState.Error(
                "Connection failed: ${e.message}"
            )
            Log.e(TAG, "Connection failed", e)
            false
        }
    }

    private fun checkWiFiEnabled(): Boolean {
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val isEnabled = wifiManager.isWifiEnabled
        val networkId = wifiManager.connectionInfo?.networkId ?: -1
        Log.d(TAG, "WiFi enabled: $isEnabled, networkId: $networkId")
        return shouldTreatWifiAsEnabledForCamera(isEnabled, networkId)
    }

    private suspend fun checkCameraConnection(): Boolean {
        return try {
            Log.d(TAG, "=== Camera Connection Test ===")
            Log.d(TAG, "Testing connection to: $OSC_INFO_URL")

            val url = URL(OSC_INFO_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT
            connection.setRequestProperty("User-Agent", "OmniEye/1.0")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            Log.d(TAG, "Response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection.inputStream)
                Log.d(TAG, "Camera info response: $response")

                // Parse camera info
                val json = JSONObject(response)
                val model = json.optString("model", "Unknown")
                val firmware = json.optString("firmwareVersion", "Unknown")
                Log.d(TAG, "Camera model: $model, firmware: $firmware")
                Log.d(TAG, "=== Connection Successful ===")
                true
            } else {
                Log.w(TAG, "=== Connection Failed: HTTP $responseCode ===")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "=== Connection Failed: ${e.javaClass.simpleName} ===")
            Log.e(TAG, "Message: ${e.message}")
            e.printStackTrace()
            false
        }
    }

    private suspend fun fetchCameraState() {
        try {
            val url = URL(OSC_STATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection.inputStream)
                val json = JSONObject(response)
                cameraSessionId = json.optString("sessionId", null)
                Log.d(TAG, "Camera session ID: $cameraSessionId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch camera state", e)
        }
    }

    fun disconnect() {
        stopContinuousCapture()
        _connectionState.value = CameraConnectionState.Disconnected
        _lastPhotoBitmap.value = null
        _photoList.value = emptyList()
        cameraSessionId = null
        Log.d(TAG, "Camera disconnected")
    }

    suspend fun takePhoto(): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        if (_isTakingPhoto.value) {
            return CameraOperationResult.Error("Already taking photo")
        }

        _isTakingPhoto.value = true
        return try {
            Log.d(TAG, "Taking photo...")
            val result = capturePhoto()
            when (result) {
                is CameraOperationResult.Success -> {
                    _capturedPhotoCount.value++
                }
                else -> {}
            }
            result
        } catch (e: Exception) {
            CameraOperationResult.Error("Failed to take photo: ${e.message}")
        } finally {
            _isTakingPhoto.value = false
        }
    }

    private suspend fun capturePhoto(): CameraOperationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Sending takePicture command...")

            // Check camera state first
            val stateJson = checkCameraState()
            val currentState = stateJson.optString("state", "Unknown")
            Log.d(TAG, "Camera state before capture: $currentState")

            val jsonBody = JSONObject().apply {
                put("name", "camera.takePicture")
                put("parameters", JSONObject().apply {
                    cameraSessionId?.let { put("sessionId", it) }
                })
            }

            val response = sendOscCommand(jsonBody.toString())
            Log.d(TAG, "Take picture initial response: $response")

            val jsonResponse = JSONObject(response)

            // Check for immediate error
            if (jsonResponse.has("error")) {
                val error = jsonResponse.getJSONObject("error")
                val code = error.optString("code", "")
                val message = error.optString("message", "Unknown error")
                Log.e(TAG, "Camera error: $code - $message")
                return@withContext CameraOperationResult.Error("Camera error: $message")
            }

            // Handle async response (state: "inProgress")
            val id = jsonResponse.optString("id", null)
            if (id != null && jsonResponse.optString("state", "") == "inProgress") {
                Log.d(TAG, "Photo capture in progress, polling for result...")
                return@withContext pollForPhotoResult(id)
            }

            // Handle sync response (state: "done")
            if (jsonResponse.optString("state", "") == "done" && jsonResponse.has("results")) {
                return@withContext processPhotoResults(jsonResponse.getJSONObject("results"))
            }

            // Fallback: try to get results directly
            if (jsonResponse.has("results")) {
                return@withContext processPhotoResults(jsonResponse.getJSONObject("results"))
            }

            Log.w(TAG, "Unexpected response format: $response")
            CameraOperationResult.Error("Unexpected response: $response")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to capture photo", e)
            CameraOperationResult.Error("Failed to capture: ${e.message}")
        }
    }

    private suspend fun checkCameraState(): JSONObject {
        return try {
            val url = URL(OSC_STATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection.inputStream)
                JSONObject(response)
            } else {
                JSONObject()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check camera state", e)
            JSONObject()
        }
    }

    private suspend fun pollForPhotoResult(commandId: String): CameraOperationResult = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Polling for command result, id: $commandId")

            for (attempt in 1..MAX_POLL_ATTEMPTS) {
                delay(POLL_INTERVAL_MS)

                val statusBody = JSONObject().apply {
                    put("name", "camera.takePicture")
                    put("id", commandId)
                }

                val response = sendOscCommand(statusBody.toString())
                val jsonResponse = JSONObject(response)

                val state = jsonResponse.optString("state", "")
                Log.d(TAG, "Poll attempt $attempt: state=$state")

                when (state) {
                    "done" -> {
                        if (jsonResponse.has("results")) {
                            return@withContext processPhotoResults(jsonResponse.getJSONObject("results"))
                        }
                    }
                    "error" -> {
                        val error = jsonResponse.optJSONObject("error")
                        val message = error?.optString("message", "Unknown error") ?: "Unknown error"
                        val code = error?.optString("code", "") ?: ""
                        Log.e(TAG, "Photo capture error: $code - $message")
                        return@withContext CameraOperationResult.Error("Camera error: $message")
                    }
                    "inProgress" -> {
                        val progress = jsonResponse.optJSONObject("progress")
                        Log.d(TAG, "Progress: $progress")
                        continue
                    }
                }
            }

            Log.e(TAG, "Photo capture timed out after $MAX_POLL_ATTEMPTS attempts")
            CameraOperationResult.Error("Photo capture timed out")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll for photo result", e)
            CameraOperationResult.Error("Failed to capture: ${e.message}")
        }
    }

    private fun processPhotoResults(results: JSONObject): CameraOperationResult {
        // Try different possible field names for file URL
        val fileUrl = results.optString("fileUrl",
            results.optString("fileUri",
                results.optString("_localFileGroup", "")))

        if (fileUrl.isEmpty()) {
            // Try fileGroup array
            val fileGroup = results.optJSONArray("_fileGroup")
            if (fileGroup != null && fileGroup.length() > 0) {
                val actualUrl = fileGroup.getString(0)
                Log.d(TAG, "Photo captured, fileUrl from group: $actualUrl")
                val bitmap = downloadPhotoBitmap(actualUrl)
                if (bitmap != null) {
                    _lastPhotoBitmap.value = bitmap
                    _photoList.value = _photoList.value + actualUrl
                    // Save to gallery
                    val savedPath = saveBitmapToGallery(bitmap)
                    if (savedPath != null) {
                        return CameraOperationResult.Success(bitmap, savedPath)
                    } else {
                        Log.e(TAG, "Failed to save photo to gallery")
                        return CameraOperationResult.Error("Failed to save photo to gallery")
                    }
                }
            }

            Log.e(TAG, "No file URL in results: $results")
            return CameraOperationResult.Error("No file URL in response")
        }

        Log.d(TAG, "Photo captured, fileUrl: $fileUrl")
        val bitmap = downloadPhotoBitmap(fileUrl)
        if (bitmap != null) {
            _lastPhotoBitmap.value = bitmap
            _photoList.value = _photoList.value + fileUrl
            // Save to gallery
            val savedPath = saveBitmapToGallery(bitmap)
            if (savedPath != null) {
                return CameraOperationResult.Success(bitmap, savedPath)
            } else {
                Log.e(TAG, "Failed to save photo to gallery")
                return CameraOperationResult.Error("Failed to save photo to gallery")
            }
        } else {
            return CameraOperationResult.Error("Failed to download photo")
        }
    }

    fun startContinuousCapture(intervalMs: Long = DEFAULT_CAPTURE_INTERVAL) {
        if (_isContinuousCapture.value) {
            Log.w(TAG, "Continuous capture already running")
            return
        }

        if (_connectionState.value != CameraConnectionState.Connected) {
            Log.e(TAG, "Cannot start continuous capture: camera not connected")
            return
        }

        _isContinuousCapture.value = true
        _capturedPhotoCount.value = 0

        continuousCaptureJob = scope.launch {
            Log.d(TAG, "Starting continuous capture every ${intervalMs}ms")
            while (isActive && _isContinuousCapture.value) {
                if (_connectionState.value == CameraConnectionState.Connected) {
                    takePhoto()
                }
                delay(intervalMs)
            }
        }
    }

    fun stopContinuousCapture() {
        continuousCaptureJob?.cancel()
        continuousCaptureJob = null
        _isContinuousCapture.value = false
        Log.d(TAG, "Stopped continuous capture. Total photos: ${_capturedPhotoCount.value}")
    }

    private fun sendOscCommand(body: String): String {
        val url = URL(OSC_COMMAND_URL)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.doInput = true
        connection.setRequestProperty("Content-Type", "application/json;charset=utf-8")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("X-XSRF-Protected", "1")
        connection.connectTimeout = CONNECT_TIMEOUT
        connection.readTimeout = READ_TIMEOUT

        Log.d(TAG, "Sending OSC command: $body")

        connection.outputStream.use { os ->
            os.write(body.toByteArray())
        }

        val responseCode = connection.responseCode
        val response = if (responseCode == HttpURLConnection.HTTP_OK) {
            readResponse(connection.inputStream)
        } else {
            val errorResponse = try {
                readResponse(connection.errorStream)
            } catch (e: Exception) {
                "No error body"
            }
            Log.e(TAG, "OSC command failed: HTTP $responseCode, body: $errorResponse")
            throw Exception("HTTP error: $responseCode")
        }

        Log.d(TAG, "OSC command response: $response")
        return response
    }

    private fun downloadPhotoBitmap(fileUri: String): Bitmap? {
        return try {
            // Handle various URL formats
            val fullUrl = when {
                fileUri.startsWith("http://") || fileUri.startsWith("https://") -> fileUri
                fileUri.startsWith("/") -> "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT$fileUri"
                else -> "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT/$fileUri"
            }

            Log.d(TAG, "Downloading photo from: $fullUrl")

            val url = URL(fullUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "image/*")
            connection.connectTimeout = READ_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val responseCode = connection.responseCode
            Log.d(TAG, "Download response code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val bitmap = BitmapFactory.decodeStream(connection.inputStream)
                Log.d(TAG, "Photo downloaded successfully, size: ${bitmap?.width}x${bitmap?.height}")
                bitmap
            } else {
                Log.e(TAG, "Failed to download photo, HTTP: $responseCode")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download photo", e)
            null
        }
    }

    suspend fun fetchPhotoList(): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        return try {
            Log.d(TAG, "Fetching photo list...")

            val url = URL(OSC_STATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = readResponse(connection.inputStream)
                val json = JSONObject(response)

                val fileUrls = mutableListOf<String>()
                if (json.has("entries")) {
                    val entries = json.getJSONArray("entries")
                    for (i in 0 until entries.length()) {
                        val entry = entries.getJSONObject(i)
                        val name = entry.optString("name", "")
                        if (name.isNotEmpty()) {
                            fileUrls.add("/osc/files/$name")
                        }
                    }
                }

                _photoList.value = fileUrls
                Log.d(TAG, "Found ${fileUrls.size} photos")
                CameraOperationResult.Success(fileUrls)
            } else {
                CameraOperationResult.Error("Failed to fetch photos: HTTP ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch photos", e)
            CameraOperationResult.Error("Failed to fetch photos: ${e.message}")
        }
    }

    suspend fun downloadPhoto(photoId: String): CameraOperationResult {
        if (_connectionState.value != CameraConnectionState.Connected) {
            return CameraOperationResult.Error("Camera not connected")
        }

        return try {
            Log.d(TAG, "Downloading photo: $photoId")

            val fullUrl = if (photoId.startsWith("http")) {
                photoId
            } else {
                "http://$DEFAULT_CAMERA_IP:$CAMERA_PORT$photoId"
            }

            val bitmap = downloadPhotoBitmap(fullUrl)
            if (bitmap != null) {
                _lastPhotoBitmap.value = bitmap
                CameraOperationResult.Success(bitmap)
            } else {
                CameraOperationResult.Error("Failed to download photo")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download photo", e)
            CameraOperationResult.Error("Failed to download: ${e.message}")
        }
    }

    private fun readResponse(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }

    fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File? {
        return try {
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos)
            }
            Log.d(TAG, "Bitmap saved to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save bitmap", e)
            null
        }
    }

    fun saveBitmapToGallery(bitmap: Bitmap): String? {
        return try {
            val fileName = generateFileName()

            // 尝试使用 MediaStore API 保存到公共目录（Android 10+ 推荐方式）
            val savedPath = saveViaMediaStore(bitmap, fileName)
            if (savedPath != null) {
                Log.d(TAG, "Photo saved via MediaStore: $savedPath")
                _lastSavedPhotoPath.value = savedPath
                return savedPath
            }

            // Fallback: 使用传统方式保存到应用私有目录
            Log.w(TAG, "MediaStore save failed, using app directory fallback")
            return saveBitmapToAppDirectory(bitmap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save photo to gallery", e)
            return saveBitmapToAppDirectory(bitmap)
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, fileName: String): String? {
        return try {
            val contentValues = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/OmniEye")
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.flush()
                }

                // 通知媒体扫描
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(uri.toString()),
                    arrayOf("image/jpeg")
                ) { path, _ ->
                    Log.d(TAG, "MediaScanner: $path")
                }

                return uri.toString()
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save via MediaStore", e)
            null
        }
    }

    private fun saveBitmapToAppDirectory(bitmap: Bitmap): String? {
        return try {
            val fileName = generateFileName()
            val picturesDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val omniEyeDir = File(picturesDir, "OmniEye")
            if (!omniEyeDir.exists()) {
                omniEyeDir.mkdirs()
            }
            val imageFile = File(omniEyeDir, fileName)
            FileOutputStream(imageFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, fos)
            }
            val savedPath = imageFile.absolutePath
            Log.d(TAG, "Photo saved to app directory: $savedPath")
            MediaScannerConnection.scanFile(
                context,
                arrayOf(savedPath),
                arrayOf("image/jpeg")
            ) { _, _ -> }
            _lastSavedPhotoPath.value = savedPath
            savedPath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save to app directory", e)
            null
        }
    }

    private fun generateFileName(): String {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
        return "IMG_${dateFormat.format(Date())}.jpg"
    }

    fun getCameraInfo(): Map<String, String> {
        return mapOf(
            "model" to "Insta360 X4",
            "firmware" to "Unknown",
            "ip" to DEFAULT_CAMERA_IP,
            "port" to CAMERA_PORT.toString(),
            "wifi_connected" to isWiFiConnected.toString(),
            "session_id" to (cameraSessionId ?: "None"),
            "continuous_capture" to _isContinuousCapture.value.toString(),
            "photos_captured" to _capturedPhotoCount.value.toString()
        )
    }
}
