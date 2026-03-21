package com.acuspic.app.imagehost

/**
 * 图床配置数据类
 */
data class ImageHostConfig(
    val type: ImageHostType = ImageHostType.NONE,
    val apiKey: String = "",
    val apiSecret: String = "",
    val customDomain: String = "",
    val customUploadUrl: String = "",
    // GitHub/Gitee 仓库配置
    val repo: String = "",
    val branch: String = "main",
    // 七牛云配置
    val bucket: String = "",
    // 通用配置
    val isEnabled: Boolean = false,
    val autoUpload: Boolean = true
)

/**
 * 上传结果
 */
sealed class UploadResult {
    data class Success(val url: String, val deleteUrl: String? = null) : UploadResult()
    data class Error(val message: String) : UploadResult()
    object Cancelled : UploadResult()
}

/**
 * 上传进度
 */
data class UploadProgress(
    val current: Int,
    val total: Int,
    val fileName: String,
    val percentage: Int = (current * 100 / total.coerceAtLeast(1))
)

/**
 * 上传历史记录
 */
data class UploadHistory(
    val id: Long = System.currentTimeMillis(),
    val originalName: String,
    val url: String,
    val deleteUrl: String? = null,
    val hostType: ImageHostType,
    val uploadTime: Long = System.currentTimeMillis(),
    val fileSize: Long = 0
)
