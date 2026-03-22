package com.acuspic.app.update

import android.content.Context
import android.telephony.TelephonyManager
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
        private const val GITHUB_API_URL = "https://api.github.com/repos/Jay-Victor/Acuspic/releases/latest"
        private const val GITEE_API_URL = "https://gitee.com/api/v5/repos/Jay-Victor/Acuspic/releases/latest"
        
        // 版本缓存时间（1小时）
        private const val CACHE_DURATION = 60 * 60 * 1000L
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

            // 获取版本信息
            val versionInfo = fetchVersionInfo(actualRepository)
            cachedVersionInfo = versionInfo
            lastCheckTime = System.currentTimeMillis()

            return@withContext compareVersion(versionInfo, currentVersionCode)
        } catch (e: Exception) {
            return@withContext UpdateCheckResult.Error("检查更新失败: ${e.message}")
        }
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

        val connection = URL(apiUrl).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP错误: $responseCode")
            }

            val response = connection.inputStream.bufferedReader().use { it.readText() }
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
        val versionCode = versionName.replace(".", "").toIntOrNull() ?: 1
        
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
                    return asset.getString("browser_download_url")
                }
            }
        }
        
        // 如果没有找到APK，返回空字符串
        return ""
    }

    /**
     * 比较版本
     */
    private fun compareVersion(versionInfo: VersionInfo, currentVersionCode: Int): UpdateCheckResult {
        return when {
            versionInfo.versionCode > currentVersionCode -> {
                UpdateCheckResult.HasUpdate(versionInfo, versionInfo.isForceUpdate)
            }
            else -> UpdateCheckResult.NoUpdate
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
