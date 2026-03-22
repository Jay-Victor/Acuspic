package com.acuspic.app.update

import android.content.Context
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * 版本检查器
 * 负责从GitHub/Gitee检查最新版本
 */
class VersionChecker(private val context: Context) {

    companion object {
        private const val TAG = "VersionChecker"
        private const val GITHUB_API_URL = "https://api.github.com/repos/Jay-Victor/Acuspic/releases/latest"
        private const val GITEE_API_URL = "https://gitee.com/api/v5/repos/Jay-Victor/Acuspic/releases/latest"
        
        // 版本缓存时间（1小时）
        private const val CACHE_DURATION = 60 * 60 * 1000L
        
        // 重试配置
        private const val MAX_RETRY_COUNT = 3
        private const val RETRY_DELAY = 1000L
    }

    private val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
    private var lastCheckTime = 0L
    private var cachedVersionInfo: VersionInfo? = null

    /**
     * 检查更新
     * @param currentVersionCode 当前版本号
     * @param repositoryType 仓库类型
     * @param skipCache 是否跳过缓存
     * @return 更新检查结果
     */
    suspend fun checkUpdate(
        currentVersionCode: Int,
        repositoryType: RepositoryType = RepositoryType.AUTO,
        skipCache: Boolean = false
    ): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            // 检查缓存
            if (!skipCache && cachedVersionInfo != null && 
                System.currentTimeMillis() - lastCheckTime < CACHE_DURATION) {
                return@withContext compareVersion(cachedVersionInfo!!, currentVersionCode)
            }

            // 选择仓库
            val actualRepository = when (repositoryType) {
                RepositoryType.AUTO -> selectRepositoryByLocation()
                else -> repositoryType
            }

            // 获取版本信息（带重试）
            val versionInfo = fetchVersionInfoWithRetry(actualRepository)
            cachedVersionInfo = versionInfo
            lastCheckTime = System.currentTimeMillis()

            return@withContext compareVersion(versionInfo, currentVersionCode)
        } catch (e: Exception) {
            Log.e(TAG, "检查更新失败: ${e.message}", e)
            return@withContext UpdateCheckResult.Error("检查更新失败: ${e.message}")
        }
    }

    /**
     * 带重试的版本信息获取
     */
    private fun fetchVersionInfoWithRetry(repositoryType: RepositoryType): VersionInfo {
        var lastException: Exception? = null
        
        for (attempt in 1..MAX_RETRY_COUNT) {
            try {
                return fetchVersionInfo(repositoryType)
            } catch (e: Exception) {
                lastException = e
                Log.w(TAG, "获取版本信息失败 (尝试 $attempt/$MAX_RETRY_COUNT): ${e.message}")
                
                if (attempt < MAX_RETRY_COUNT) {
                    // 如果是GitHub失败，尝试切换到Gitee
                    if (repositoryType == RepositoryType.GITHUB) {
                        Log.i(TAG, "GitHub请求失败，尝试切换到Gitee...")
                        try {
                            return fetchVersionInfo(RepositoryType.GITEE)
                        } catch (e2: Exception) {
                            Log.w(TAG, "Gitee备用请求也失败: ${e2.message}")
                        }
                    }
                    Thread.sleep(RETRY_DELAY)
                }
            }
        }
        
        throw lastException ?: Exception("未知错误")
    }

    /**
     * 根据地理位置选择仓库
     */
    private fun selectRepositoryByLocation(): RepositoryType {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val countryCode = telephonyManager.networkCountryIso.uppercase()
            
            // 中国用户使用Gitee，其他使用GitHub
            if (countryCode == "CN") RepositoryType.GITEE else RepositoryType.GITHUB
        } catch (e: Exception) {
            Log.w(TAG, "获取国家代码失败，使用默认仓库: ${e.message}")
            // 默认使用Gitee
            RepositoryType.GITEE
        }
    }

    /**
     * 从API获取版本信息
     */
    private fun fetchVersionInfo(repositoryType: RepositoryType): VersionInfo {
        val apiUrl = when (repositoryType) {
            RepositoryType.GITHUB -> GITHUB_API_URL
            RepositoryType.GITEE -> GITEE_API_URL
            else -> GITEE_API_URL
        }

        Log.d(TAG, "正在从 ${repositoryType.name} 获取版本信息: $apiUrl")

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 15000
            // GitHub API 需要设置 User-Agent
            setRequestProperty("User-Agent", "Acuspic-Android-App")
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }

        try {
            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP响应码: $responseCode")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "无错误信息"
                Log.e(TAG, "HTTP错误: $responseCode, 响应: $errorStream")
                throw Exception("HTTP错误: $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
            Log.d(TAG, "成功获取版本信息，响应长度: ${response.length}")
            return parseVersionInfo(response, repositoryType)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 解析版本信息
     */
    private fun parseVersionInfo(json: String, repositoryType: RepositoryType): VersionInfo {
        val jsonObject = JSONObject(json)
        
        val tagName = jsonObject.getString("tag_name")
        val versionName = tagName.removePrefix("v")
        
        // 正确的语义化版本号解析
        val versionCode = parseSemanticVersionCode(versionName)
        
        val releaseNotes = jsonObject.optString("body", "")
        val publishDate = jsonObject.optString("published_at", "")
        
        // 解析下载地址
        val downloadUrl = parseDownloadUrl(jsonObject, repositoryType)
        
        // 检查是否是重要版本（通过标签或标题判断）
        val isImportant = jsonObject.optString("name", "").contains("[重要]") ||
                         releaseNotes.contains("[重要更新]")
        
        // 检查是否强制更新
        val isForceUpdate = jsonObject.optString("name", "").contains("[强制]") ||
                           releaseNotes.contains("[强制更新]")

        Log.i(TAG, "解析版本: $versionName (code: $versionCode), 下载地址: $downloadUrl")

        return VersionInfo(
            versionCode = versionCode,
            versionName = versionName,
            downloadUrl = downloadUrl,
            releaseNotes = releaseNotes,
            isForceUpdate = isForceUpdate,
            isImportant = isImportant,
            publishDate = publishDate
        )
    }

    /**
     * 解析语义化版本号为整数
     * 格式: major.minor.patch (如 1.2.3)
     * 转换公式: major * 10000 + minor * 100 + patch
     * 这样可以正确比较: 1.2.3 < 1.10.0 < 2.0.0
     */
    private fun parseSemanticVersionCode(versionName: String): Int {
        try {
            // 移除可能的预发布标识 (如 1.0.0-beta.1 -> 1.0.0)
            val cleanVersion = versionName.split("-")[0]
            
            val parts = cleanVersion.split(".")
            val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
            val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
            
            // 确保每部分不超过两位数
            if (major > 99 || minor > 99 || patch > 99) {
                Log.w(TAG, "版本号某部分超过99: $versionName")
            }
            
            return major * 10000 + minor * 100 + patch
        } catch (e: Exception) {
            Log.e(TAG, "解析版本号失败: $versionName", e)
            return 1
        }
    }

    /**
     * 解析下载地址
     */
    @Suppress("UNUSED_PARAMETER")
    private fun parseDownloadUrl(jsonObject: JSONObject, repositoryType: RepositoryType): String {
        val assets = jsonObject.optJSONArray("assets")
        
        if (assets != null && assets.length() > 0) {
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                
                // 查找APK文件（优先大写Apk后缀）
                if (name.endsWith(".Apk", ignoreCase = true)) {
                    val url = asset.getString("browser_download_url")
                    Log.d(TAG, "找到APK资源: $name, URL: $url")
                    return url
                }
            }
        }
        
        Log.w(TAG, "未找到APK下载资源")
        // 如果没有找到APK，返回空字符串
        return ""
    }

    /**
     * 比较版本
     */
    private fun compareVersion(versionInfo: VersionInfo, currentVersionCode: Int): UpdateCheckResult {
        Log.d(TAG, "版本比较: 远程=${versionInfo.versionCode}, 当前=$currentVersionCode")
        
        return when {
            versionInfo.versionCode > currentVersionCode -> {
                Log.i(TAG, "发现新版本: ${versionInfo.versionName}")
                UpdateCheckResult.HasUpdate(versionInfo, versionInfo.isForceUpdate)
            }
            versionInfo.versionCode < currentVersionCode -> {
                Log.w(TAG, "远程版本低于当前版本，可能是测试版本")
                UpdateCheckResult.NoUpdate
            }
            else -> {
                Log.i(TAG, "已是最新版本")
                UpdateCheckResult.NoUpdate
            }
        }
    }

    /**
     * 获取跳过的版本号
     */
    fun getSkippedVersion(): Int {
        return prefs.getInt("skipped_version", 0)
    }

    /**
     * 设置跳过的版本号
     */
    fun setSkippedVersion(versionCode: Int) {
        prefs.edit().putInt("skipped_version", versionCode).apply()
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        cachedVersionInfo = null
        lastCheckTime = 0
    }
}
