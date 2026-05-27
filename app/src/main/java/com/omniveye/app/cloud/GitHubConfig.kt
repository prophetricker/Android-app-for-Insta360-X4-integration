package com.omniveye.app.cloud

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.io.Serializable

/**
 * GitHub配置类，用于存储GitHub仓库连接信息
 */
data class GitHubConfig(
    val token: String,
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val folderPath: String = "OmniEye",
    val apiBaseUrl: String = "https://api.github.com"
) : Serializable {
    
    companion object {
        private const val PREFS_NAME = "github_config"
        private const val KEY_TOKEN = "github_token"
        private const val KEY_OWNER = "github_owner"
        private const val KEY_REPO = "github_repo"
        private const val KEY_BRANCH = "github_branch"
        private const val KEY_FOLDER = "github_folder"
        
        @Volatile
        private var instance: GitHubConfig? = null
        
        fun getInstance(context: Context): GitHubConfig {
            return instance ?: synchronized(this) {
                instance ?: loadFromPrefs(context).also { instance = it }
            }
        }
        
        fun loadFromPrefs(context: Context): GitHubConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return GitHubConfig(
                token = prefs.getString(KEY_TOKEN, "") ?: "",
                owner = prefs.getString(KEY_OWNER, "") ?: "",
                repo = prefs.getString(KEY_REPO, "") ?: "",
                branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
                folderPath = prefs.getString(KEY_FOLDER, "OmniEye") ?: "OmniEye"
            )
        }
        
        fun saveToPrefs(context: Context, config: GitHubConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_TOKEN, config.token)
                putString(KEY_OWNER, config.owner)
                putString(KEY_REPO, config.repo)
                putString(KEY_BRANCH, config.branch)
                putString(KEY_FOLDER, config.folderPath)
                apply()
            }
            instance = config
        }
        
        fun isConfigured(config: GitHubConfig): Boolean {
            return config.token.isNotBlank() && 
                   config.owner.isNotBlank() && 
                   config.repo.isNotBlank()
        }
    }
}

/**
 * GitHub上传结果
 */
data class GitHubUploadResult(
    val success: Boolean,
    val fileName: String,
    val filePath: String,
    val downloadUrl: String?,
    val sha: String?,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * GitHub文件内容信息
 */
data class GitHubContent(
    val name: String,
    val path: String,
    val sha: String,
    val size: Int,
    val downloadUrl: String?,
    val type: String,
    val content: String? = null  // Base64编码的内容
)

/**
 * GitHub API响应
 */
data class GitHubResponse(
    val content: GitHubContent?,
    val commit: GitHubCommit?
)

/**
 * GitHub提交信息
 */
data class GitHubCommit(
    val sha: String,
    val message: String
)

/**
 * 上传进度状态
 */
sealed class UploadProgress {
    data object Idle : UploadProgress()
    data class Preparing(val message: String) : UploadProgress()
    data class Uploading(val progress: Int, val fileName: String) : UploadProgress()
    data class Success(val result: GitHubUploadResult) : UploadProgress()
    data class Error(val message: String) : UploadProgress()
}
