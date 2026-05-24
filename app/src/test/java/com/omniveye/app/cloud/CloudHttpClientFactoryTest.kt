package com.omniveye.app.cloud

import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class CloudHttpClientFactoryTest {

    @Test
    fun usesCellularSocketFactoryAndDnsWhenBindingIsAvailable() {
        val socketFactory = FakeSocketFactory()
        val binding = FakeCloudNetworkBinding(socketFactory)

        val route = createCloudOkHttpClient(binding)

        assertTrue(route.usesBoundNetwork)
        assertSame(socketFactory, route.client.socketFactory)
        assertSame(binding.resolvedAddress, route.client.dns.lookup("example.com").single())
    }

    @Test
    fun fallsBackToDefaultClientWhenBindingIsMissing() {
        val route = createCloudOkHttpClient(null)

        assertTrue(!route.usesBoundNetwork)
    }
}

private class FakeCloudNetworkBinding(
    override val socketFactory: SocketFactory
) : CloudNetworkBinding {
    val resolvedAddress: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

    override fun lookup(hostname: String): List<InetAddress> {
        return listOf(resolvedAddress)
    }
}

private class FakeSocketFactory : SocketFactory() {
    override fun createSocket(): Socket = Socket()
    override fun createSocket(host: String, port: Int): Socket = Socket(host, port)
    override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): Socket =
        Socket(host, port, localHost, localPort)

    override fun createSocket(host: java.net.InetAddress, port: Int): Socket = Socket(host, port)
    override fun createSocket(
        address: java.net.InetAddress,
        port: Int,
        localAddress: java.net.InetAddress,
        localPort: Int
    ): Socket = Socket(address, port, localAddress, localPort)
}
