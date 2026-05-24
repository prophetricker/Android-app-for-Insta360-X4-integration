package com.omniveye.app.cloud

import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.net.InetAddress
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

private const val TIMEOUT_SECONDS = 60L

interface CloudNetworkBinding {
    val socketFactory: SocketFactory
    fun lookup(hostname: String): List<InetAddress>
}

data class CloudHttpClientRoute(
    val client: OkHttpClient,
    val usesBoundNetwork: Boolean
)

fun createCloudOkHttpClient(binding: CloudNetworkBinding?): CloudHttpClientRoute {
    val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    val builder = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)

    if (binding != null) {
        builder.socketFactory(binding.socketFactory)
        builder.dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                return binding.lookup(hostname)
            }
        })
    }

    return CloudHttpClientRoute(
        client = builder.build(),
        usesBoundNetwork = binding != null
    )
}
