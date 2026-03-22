package com.acuspic.app.update

/**
 * 版本信息数据类
 */
data class VersionInfo(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val patchUrl: String? = null,
    val releaseNotes: String = "",
    val isForceUpdate: Boolean = false,
    val isImportant: Boolean = false,
    val publishDate: String = "",
    val fileSize: Long = 0L
) {
    /**
     * 格式化版本显示
     */
    fun getDisplayVersion(): String {
        return "v$versionName"
    }
    
    /**
     * 获取版本描述
     */
    fun getVersionDescription(): String {
        val sb = StringBuilder()
        if (isForceUpdate) sb.append("[强制更新] ")
        if (isImportant) sb.append("[重要] ")
        sb.append("v$versionName")
        return sb.toString()
    }
}

/**
 * 版本历史记录
 */
data class VersionHistory(
    val versionCode: Int,
    val versionName: String,
    val publishDate: String,
    val releaseNotes: String,
    val downloadUrl: String,
    val isDownloaded: Boolean = false,
    val localPath: String? = null
)

/**
 * 下载状态
 */
sealed class DownloadStatus {
    object Idle : DownloadStatus()
    object Connecting : DownloadStatus()
    data class Progress(val progress: Int, val speed: String) : DownloadStatus()
    data class Success(val filePath: String) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
    object Cancelled : DownloadStatus()
}

/**
 * 更新检查结果
 */
sealed class UpdateCheckResult {
    object NoUpdate : UpdateCheckResult()
    data class HasUpdate(val versionInfo: VersionInfo, val isForceUpdate: Boolean) : UpdateCheckResult()
    data class Error(val message: String) : UpdateCheckResult()
}

/**
 * 仓库类型
 */
enum class RepositoryType {
    GITHUB,     // GitHub仓库（国外）
    GITEE,      // Gitee仓库（国内）
    AUTO        // 自动选择
}

/**
 * 下载配置
 */
data class DownloadConfig(
    val repositoryType: RepositoryType = RepositoryType.AUTO,
    val enableBreakpointResume: Boolean = true,
    val maxRetryCount: Int = 3,
    val connectTimeout: Int = 30000,
    val readTimeout: Int = 30000
)
