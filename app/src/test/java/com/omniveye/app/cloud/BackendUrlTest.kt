package com.omniveye.app.cloud

import org.junit.Assert.assertEquals
import org.junit.Test

class BackendUrlTest {

    @Test
    fun keepsHttpsTunnelRootAsRetrofitBaseUrl() {
        assertEquals(
            "https://4e21ffc1d92509.lhr.life/",
            normalizeCloudBaseUrl("https://4e21ffc1d92509.lhr.life/")
        )
    }

    @Test
    fun stripsHealthPathFromPastedTunnelUrl() {
        assertEquals(
            "https://4e21ffc1d92509.lhr.life/",
            normalizeCloudBaseUrl("https://4e21ffc1d92509.lhr.life/health")
        )
    }

    @Test
    fun addsHttpsSchemeWhenUserPastesOnlyHost() {
        assertEquals(
            "https://4e21ffc1d92509.lhr.life/",
            normalizeCloudBaseUrl("4e21ffc1d92509.lhr.life")
        )
    }
}
