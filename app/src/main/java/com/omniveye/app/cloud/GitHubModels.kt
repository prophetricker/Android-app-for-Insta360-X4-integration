package com.omniveye.app.cloud

import com.google.gson.annotations.SerializedName

data class GitHubConfig(
    val token: String,
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val apiBaseUrl: String = "https://api.github.com"
)

data class GitHubResponse(
    @SerializedName("content")
    val content: GitHubContent?,
    @SerializedName("commit")
    val commit: GitHubCommit?
)

data class GitHubContent(
    @SerializedName("name")
    val name: String?,
    @SerializedName("path")
    val path: String?,
    @SerializedName("sha")
    val sha: String?,
    @SerializedName("download_url")
    val downloadUrl: String?
)

data class GitHubCommit(
    @SerializedName("sha")
    val sha: String?
)

data class GitHubUploadResult(
    val success: Boolean,
    val fileName: String,
    val filePath: String,
    val downloadUrl: String?,
    val sha: String?,
    val errorMessage: String? = null
)
