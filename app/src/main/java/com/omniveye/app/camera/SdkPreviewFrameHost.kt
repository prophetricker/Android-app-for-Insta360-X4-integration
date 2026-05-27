package com.omniveye.app.camera

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.omniveye.app.BuildConfig
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

private const val SDK_PREVIEW_TAG = "SdkPreviewFrameHost"
private const val SDK_FRAME_WIDTH = 960
private const val SDK_FRAME_HEIGHT = 480
private const val SDK_FRAME_FPS = 2
private const val SDK_FRAME_FORMAT_RGBA = 32
private const val SDK_PLAYER_SIZE = 64

@Composable
fun SdkPreviewFrameHost(
    enabled: Boolean,
    onFrame: (Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!enabled || !BuildConfig.INSTA360_SDK_ENABLED) return

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            SdkPreviewFrameView(viewContext).apply {
                layoutParams = FrameLayout.LayoutParams(SDK_PLAYER_SIZE, SDK_PLAYER_SIZE)
                alpha = 0.01f
                start(onFrame)
            }
        },
        update = { it.start(onFrame) }
    )

}

class SdkPreviewFrameView(context: Context) : FrameLayout(context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val started = AtomicBoolean(false)
    private var playerView: Any? = null
    private var cameraManager: Any? = null
    private var onFrame: ((Bitmap) -> Unit)? = null
    private val frameLogCounter = AtomicInteger(0)

    fun start(onFrame: (Bitmap) -> Unit) {
        this.onFrame = onFrame
        if (!started.compareAndSet(false, true)) return
        mainHandler.post { startInternal() }
    }

    fun stop() {
        if (!started.compareAndSet(true, false)) return
        runCatching { playerView?.javaClass?.getMethod("stopExtractMediaFrame")?.invoke(playerView) }
        runCatching { playerView?.javaClass?.getMethod("destroy")?.invoke(playerView) }
        runCatching {
            cameraManager?.let { manager ->
                manager.javaClass.getMethod("setPipeline", Any::class.java).invoke(manager, null)
            }
        }
        runCatching { cameraManager?.javaClass?.getMethod("closePreviewStream")?.invoke(cameraManager) }
        playerView = null
        cameraManager = null
        removeAllViews()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun startInternal() {
        try {
            Log.d(SDK_PREVIEW_TAG, "Starting SDK preview frame host")
            initializeSdk()
            val manager = loadCameraManager()
            cameraManager = manager

            val player = createPlayerView()
            playerView = player
            addView(player as View, LayoutParams(SDK_PLAYER_SIZE, SDK_PLAYER_SIZE))

            openCameraWifi(manager)
            startPreviewStream(manager, player)
        } catch (e: Throwable) {
            Log.w(SDK_PREVIEW_TAG, "SDK preview frame host disabled: ${e.javaClass.simpleName}: ${e.message}")
            stop()
        }
    }

    private fun initializeSdk() {
        val application = context.applicationContext as? Application ?: return
        Class.forName("com.arashivision.sdkcamera.InstaCameraSDK")
            .getMethod("init", Application::class.java)
            .invoke(null, application)
        Class.forName("com.arashivision.sdkmedia.InstaMediaSDK")
            .getMethod("init", Application::class.java)
            .invoke(null, application)
        Log.d(SDK_PREVIEW_TAG, "Insta360 SDK initialized")
    }

    private fun loadCameraManager(): Any {
        val managerClass = Class.forName("com.arashivision.sdkcamera.camera.InstaCameraManager")
        return managerClass.getMethod("getInstance").invoke(null)
    }

    private fun createPlayerView(): Any {
        val playerClass = Class.forName("com.arashivision.sdkmedia.player.capture.InstaCapturePlayerView")
        return playerClass.getConstructor(Context::class.java).newInstance(context)
    }

    private fun openCameraWifi(manager: Any) {
        val managerClass = manager.javaClass
        val connectType = managerClass.getField("CONNECT_TYPE_WIFI").getInt(null)
        findWifiNetwork()?.let { network ->
            runCatching {
                managerClass.getMethod("setNetIdToCamera", Long::class.javaPrimitiveType)
                    .invoke(manager, network.networkHandle)
                Log.d(SDK_PREVIEW_TAG, "Bound SDK camera manager to WiFi network ${network.networkHandle}")
            }
        }
        Log.d(SDK_PREVIEW_TAG, "Opening SDK camera over WiFi")
        managerClass.getMethod("openCamera", Int::class.javaPrimitiveType).invoke(manager, connectType)
    }

    private fun startPreviewStream(manager: Any, player: Any) {
        val managerClass = manager.javaClass
        val previewType = managerClass.getField("PREVIEW_TYPE_NORMAL").getInt(null)
        setPreviewStatusListener(manager, player)
        Log.d(SDK_PREVIEW_TAG, "Starting SDK preview stream")
        managerClass.getMethod("startPreviewStream", Int::class.javaPrimitiveType).invoke(manager, previewType)
    }

    private fun preparePlayer(manager: Any, player: Any) {
        setPlayerViewListener(manager, player)
        val paramsBuilder = Class.forName("com.arashivision.sdkmedia.player.capture.CaptureParamsBuilderV2")
            .getConstructor()
            .newInstance()
        paramsBuilder.javaClass.getMethod("setScreenRatio", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
            .invoke(paramsBuilder, 2, 1)
        paramsBuilder.javaClass.getMethod("setWidth", Int::class.javaPrimitiveType)
            .invoke(paramsBuilder, SDK_FRAME_WIDTH)
        paramsBuilder.javaClass.getMethod("setHeight", Int::class.javaPrimitiveType)
            .invoke(paramsBuilder, SDK_FRAME_HEIGHT)
        paramsBuilder.javaClass.getMethod("setFps", Int::class.javaPrimitiveType)
            .invoke(paramsBuilder, SDK_FRAME_FPS)

        val playerClass = player.javaClass
        Log.d(SDK_PREVIEW_TAG, "Preparing SDK capture player")
        playerClass.methods.first { it.name == "prepare" && it.parameterTypes.size == 1 }
            .invoke(player, paramsBuilder)
        Log.d(SDK_PREVIEW_TAG, "Playing SDK capture player")
        playerClass.getMethod("play").invoke(player)
    }

    private fun setPreviewStatusListener(manager: Any, player: Any) {
        val listenerClass = Class.forName("com.arashivision.sdkcamera.camera.callback.IPreviewStatusListener")
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, args ->
            when (method.name) {
                "onOpened" -> {
                    Log.d(SDK_PREVIEW_TAG, "SDK preview stream opened")
                    runCatching { manager.javaClass.getMethod("setStreamEncode").invoke(manager) }
                    preparePlayer(manager, player)
                }
                "onError" -> {
                    Log.w(SDK_PREVIEW_TAG, "SDK preview stream open failed args=${args?.toList().orEmpty()}")
                }
            }
            null
        }
        manager.javaClass.getMethod("setPreviewStatusChangedListener", listenerClass).invoke(manager, listener)
    }

    private fun setPlayerViewListener(manager: Any, player: Any) {
        val listenerClass = Class.forName("com.arashivision.sdkmedia.player.listener.PlayerViewListener")
        val listener = Proxy.newProxyInstance(
            listenerClass.classLoader,
            arrayOf(listenerClass)
        ) { _, method, _ ->
            when (method.name) {
                "onFirstFrameRender" -> {
                    Log.d(SDK_PREVIEW_TAG, "SDK capture player rendered first frame")
                }
                "onLoadingFinish" -> {
                    Log.d(SDK_PREVIEW_TAG, "SDK capture player loading finished")
                    runCatching {
                        val pipeline = player.javaClass.getMethod("getPipeline").invoke(player)
                        manager.javaClass.getMethod("setPipeline", Any::class.java).invoke(manager, pipeline)
                        Log.d(SDK_PREVIEW_TAG, "SDK camera pipeline attached")
                    }.onFailure {
                        Log.w(SDK_PREVIEW_TAG, "Failed to attach SDK camera pipeline: ${it.javaClass.simpleName}: ${it.message}")
                    }
                    startExtractingFrames(player)
                }
                "onReleaseCameraPipeline" -> {
                    Log.d(SDK_PREVIEW_TAG, "SDK capture player released camera pipeline")
                    runCatching {
                        manager.javaClass.getMethod("setPipeline", Any::class.java).invoke(manager, null)
                    }
                }
            }
            null
        }
        player.javaClass.getMethod("setPlayerViewListener", listenerClass).invoke(player, listener)
    }

    private fun startExtractingFrames(player: Any) {
        Log.d(SDK_PREVIEW_TAG, "Starting SDK media frame extraction ${SDK_FRAME_WIDTH}x$SDK_FRAME_HEIGHT@$SDK_FRAME_FPS")
        val callbackClass = Class.forName("com.arashivision.insta360.basemedia.ui.player.capture.IMediaFrameCallback")
        val callback = Proxy.newProxyInstance(
            callbackClass.classLoader,
            arrayOf(callbackClass)
        ) { _, method, args ->
            if (method.name == "onMediaFrame") {
                handleMediaFrame(args?.firstOrNull())
            }
            null
        }
        player.javaClass.getMethod(
            "startExtractMediaFrame",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            callbackClass
        ).invoke(player, SDK_FRAME_WIDTH, SDK_FRAME_HEIGHT, SDK_FRAME_FPS, SDK_FRAME_FORMAT_RGBA, callback)
    }

    private fun handleMediaFrame(mediaFrame: Any?) {
        if (mediaFrame == null) return
        try {
            val width = mediaFrame.javaClass.getMethod("getWidth").invoke(mediaFrame) as Int
            val height = mediaFrame.javaClass.getMethod("getHeight").invoke(mediaFrame) as Int
            val planes = mediaFrame.javaClass.getMethod("getPlanes").invoke(mediaFrame) as Array<*>
            val plane = planes.firstOrNull() as? ByteBuffer ?: return
            val bitmap = bitmapFromRgbaBuffer(plane, width, height)
            val frameIndex = frameLogCounter.incrementAndGet()
            if (frameIndex <= 3 || frameIndex % 20 == 0) {
                Log.d(SDK_PREVIEW_TAG, "SDK preview frame received count=$frameIndex size=${width}x$height")
            }
            onFrame?.invoke(bitmap)
        } catch (e: Throwable) {
            Log.w(SDK_PREVIEW_TAG, "Failed to convert SDK preview frame: ${e.javaClass.simpleName}: ${e.message}")
        }
    }

    private fun findWifiNetwork(): Network? {
        val connectivityManager =
            context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return connectivityManager.allNetworks.firstOrNull { network ->
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            val linkProperties = connectivityManager.getLinkProperties(network)
            val addresses = linkProperties?.linkAddresses.orEmpty().mapNotNull { it.address.hostAddress }
            val isWifi = capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val isX4Wifi = isX4WifiRoute(isWifi, addresses)
            Log.d(SDK_PREVIEW_TAG, "SDK WiFi candidate network=$network wifi=$isWifi addresses=$addresses isX4=$isX4Wifi")
            isX4Wifi
        }
    }
}

fun bitmapFromRgbaBuffer(buffer: ByteBuffer, width: Int, height: Int): Bitmap {
    val copy = copyRgbaBytes(buffer, width, height)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    bitmap.copyPixelsFromBuffer(ByteBuffer.wrap(copy))
    return bitmap
}

fun copyRgbaBytes(buffer: ByteBuffer, width: Int, height: Int): ByteArray {
    val copy = buffer.duplicate()
    copy.rewind()
    val expectedSize = width * height * 4
    require(copy.remaining() >= expectedSize) {
        "RGBA buffer too small: ${copy.remaining()} < $expectedSize"
    }
    val bytes = ByteArray(expectedSize)
    copy.get(bytes)
    return bytes
}
