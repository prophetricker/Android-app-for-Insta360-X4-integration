package com.omniveye.app.cloud

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import com.google.gson.Gson

/**
 * GitHub API Service
 *
 * 提供 GitHub API 的基本操作，包括：
 * - 获取文件信息（用于获取 SHA 以便更新文件）
 * - 创建或更新文件
 * - 删除文件
 * - 列出目录内容
 *
 * @param config GitHub 配置（包含 token、owner、repo、branch 等）
 * @param client OkHttpClient 实例，用于执行 HTTP 请求
 */
class GitHubApiService(
    private val config: GitHubConfig,
    private val client: okhttp3.OkHttpClient
) {
    private val gson = Gson()

    companion object {
        private const val MEDIA_TYPE_JSON = "application/json; charset=utf-8"
    }

    /**
     * 获取文件信息（可能用于获取 SHA 以便更新）
     *
     * @param path 文件在仓库中的路径
     * @return GitHubResponse 包含文件信息，成功时包含 sha 等字段
     */
    fun getFile(path: String): GitHubResponse? {
        val url = "${config.apiBaseUrl}/repos/${config.owner}/${config.repo}/contents/$path?ref=${config.branch}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return executeRequest(request)
    }

    /**
     * 创建或更新文件
     *
     * @param path 文件在仓库中的路径
     * @param content 文件内容（Base64 编码）
     * @param message 提交信息
     * @param sha 文件的 SHA 哈希值（更新文件时必需，创建新文件时为 null）
     * @return GitHubResponse 包含操作结果
     */
    fun createOrUpdateFile(path: String, content: String, message: String, sha: String? = null): GitHubResponse? {
        val url = "${config.apiBaseUrl}/repos/${config.owner}/${config.repo}/contents/$path"

        // 构建请求体
        val requestBody = buildString {
            append("""{"message":"$message","content":"$content"""")
            if (sha != null) {
                append(""","sha":"$sha"""")
            }
            append(""","branch":"${config.branch}"}""")
        }

        val requestBodyJson = requestBody.toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .put(requestBodyJson)
            .build()

        return executeRequest(request)
    }

    /**
     * 删除文件
     *
     * @param path 文件在仓库中的路径
     * @param sha 文件的 SHA 哈希值（必需，用于确认删除）
     * @param message 提交信息
     * @return Boolean 是否删除成功
     */
    fun deleteFile(path: String, sha: String, message: String): Boolean {
        val url = "${config.apiBaseUrl}/repos/${config.owner}/${config.repo}/contents/$path"

        val requestBody = """
            {
                "message": "$message",
                "sha": "$sha",
                "branch": "${config.branch}"
            }
        """.trimIndent().toRequestBody(MEDIA_TYPE_JSON.toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .delete(requestBody)
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 列出目录内容
     *
     * @param path 目录在仓库中的路径（空字符串表示根目录）
     * @return List<GitHubContent> 目录内容列表
     */
    fun listDirectory(path: String = ""): List<GitHubContent>? {
        val url = "${config.apiBaseUrl}/repos/${config.owner}/${config.repo}/contents/$path?ref=${config.branch}"

        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${config.token}")
            .addHeader("Accept", "application/vnd.github.v3+json")
            .get()
            .build()

        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null && body.startsWith("[")) {
                        gson.fromJson(body, Array<GitHubContent>::class.java).toList()
                    } else {
                        // 单个文件返回的是对象，不是数组
                        listOf(gson.fromJson(body, GitHubContent::class.java))
                    }
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun executeRequest(request: Request): GitHubResponse? {
        return try {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) {
                        gson.fromJson(body, GitHubResponse::class.java)
                    } else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
