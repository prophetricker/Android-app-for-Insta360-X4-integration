package com.omniveye.app.cloud

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.InetAddress
import javax.net.SocketFactory

sealed class CellularNetworkState {
    data object Unavailable : CellularNetworkState()
    data object Requesting : CellularNetworkState()
    data object Available : CellularNetworkState()
    data object Lost : CellularNetworkState()
}

class AndroidCloudNetworkBinding(private val network: Network) : CloudNetworkBinding {
    override val socketFactory: SocketFactory
        get() = network.socketFactory

    override fun lookup(hostname: String): List<InetAddress> {
        return network.getAllByName(hostname).toList()
    }
}

class CellularNetworkProvider(context: Context) {

    companion object {
        private const val TAG = "CellularNetworkProvider"
    }

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val _state = MutableStateFlow<CellularNetworkState>(CellularNetworkState.Unavailable)
    val state: StateFlow<CellularNetworkState> = _state.asStateFlow()

    @Volatile
    private var cellularNetwork: Network? = null
    private var callbackRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            cellularNetwork = network
            _state.value = CellularNetworkState.Available
            Log.d(TAG, "Cellular network available for cloud route")
        }

        override fun onLost(network: Network) {
            if (cellularNetwork == network) {
                cellularNetwork = null
                _state.value = CellularNetworkState.Lost
                Log.w(TAG, "Cellular network lost for cloud route")
            }
        }

        override fun onUnavailable() {
            cellularNetwork = null
            _state.value = CellularNetworkState.Unavailable
            Log.w(TAG, "Cellular network unavailable for cloud route")
        }
    }

    fun start() {
        if (callbackRegistered) return

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        _state.value = CellularNetworkState.Requesting
        try {
            connectivityManager.requestNetwork(request, networkCallback)
            callbackRegistered = true
        } catch (e: RuntimeException) {
            cellularNetwork = null
            callbackRegistered = false
            _state.value = CellularNetworkState.Unavailable
            Log.e(TAG, "Failed to request cellular network", e)
        }
    }

    fun currentBinding(): CloudNetworkBinding? {
        return cellularNetwork?.let(::AndroidCloudNetworkBinding)
    }

    fun stop() {
        if (!callbackRegistered) return
        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Cellular network callback was already unregistered", e)
        } finally {
            callbackRegistered = false
            cellularNetwork = null
            _state.value = CellularNetworkState.Unavailable
        }
    }
}
