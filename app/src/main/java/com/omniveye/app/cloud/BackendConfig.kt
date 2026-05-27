package com.omniveye.app.cloud

import android.content.Context
import com.omniveye.app.BuildConfig

private const val PREFS_NAME = "omnieye_backend"
private const val KEY_BASE_URL = "base_url"

fun normalizeCloudBaseUrl(input: String): String {
    var value = input.trim()
    if (value.isBlank()) {
        value = BuildConfig.CLOUD_BASE_URL
    }
    if (!value.startsWith("http://") && !value.startsWith("https://")) {
        value = "https://$value"
    }

    val healthIndex = value.indexOf("/health")
    if (healthIndex >= 0) {
        value = value.substring(0, healthIndex)
    }
    val analyzeIndex = value.indexOf("/analyze")
    if (analyzeIndex >= 0) {
        value = value.substring(0, analyzeIndex)
    }

    return value.trimEnd('/') + "/"
}

class BackendConfig(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getBaseUrl(): String {
        return normalizeCloudBaseUrl(
            prefs.getString(KEY_BASE_URL, BuildConfig.CLOUD_BASE_URL) ?: BuildConfig.CLOUD_BASE_URL
        )
    }

    fun setBaseUrl(baseUrl: String): String {
        val normalized = normalizeCloudBaseUrl(baseUrl)
        prefs.edit().putString(KEY_BASE_URL, normalized).apply()
        return normalized
    }
}
