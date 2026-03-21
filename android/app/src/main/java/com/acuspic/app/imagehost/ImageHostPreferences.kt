package com.acuspic.app.imagehost

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * 图床配置管理器
 */
class ImageHostPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "ImageHostPrefs"
        private const val KEY_HOST_TYPE = "host_type"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_API_SECRET = "api_secret"
        private const val KEY_CUSTOM_DOMAIN = "custom_domain"
        private const val KEY_CUSTOM_UPLOAD_URL = "custom_upload_url"
        // 通用仓库配置（GitHub/Gitee共用）
        private const val KEY_REPO = "repo"
        private const val KEY_BRANCH = "branch"
        // 七牛云配置
        private const val KEY_BUCKET = "bucket"
        // 开关配置
        private const val KEY_IS_ENABLED = "is_enabled"
        private const val KEY_AUTO_UPLOAD = "auto_upload"
        private const val KEY_UPLOAD_HISTORY = "upload_history"
        private const val MAX_HISTORY_SIZE = 50
    }

    /**
     * 保存图床配置
     */
    fun saveConfig(config: ImageHostConfig) {
        prefs.edit().apply {
            putString(KEY_HOST_TYPE, config.type.name)
            putString(KEY_API_KEY, config.apiKey)
            putString(KEY_API_SECRET, config.apiSecret)
            putString(KEY_CUSTOM_DOMAIN, config.customDomain)
            putString(KEY_CUSTOM_UPLOAD_URL, config.customUploadUrl)
            putString(KEY_REPO, config.repo)
            putString(KEY_BRANCH, config.branch)
            putString(KEY_BUCKET, config.bucket)
            putBoolean(KEY_IS_ENABLED, config.isEnabled)
            putBoolean(KEY_AUTO_UPLOAD, config.autoUpload)
            apply()
        }
    }

    /**
     * 获取图床配置
     */
    fun getConfig(): ImageHostConfig {
        val typeName = prefs.getString(KEY_HOST_TYPE, ImageHostType.NONE.name)
        return ImageHostConfig(
            type = ImageHostType.fromName(typeName ?: ImageHostType.NONE.name) ?: ImageHostType.NONE,
            apiKey = prefs.getString(KEY_API_KEY, "") ?: "",
            apiSecret = prefs.getString(KEY_API_SECRET, "") ?: "",
            customDomain = prefs.getString(KEY_CUSTOM_DOMAIN, "") ?: "",
            customUploadUrl = prefs.getString(KEY_CUSTOM_UPLOAD_URL, "") ?: "",
            repo = prefs.getString(KEY_REPO, "") ?: "",
            branch = prefs.getString(KEY_BRANCH, "main") ?: "main",
            bucket = prefs.getString(KEY_BUCKET, "") ?: "",
            isEnabled = prefs.getBoolean(KEY_IS_ENABLED, false),
            autoUpload = prefs.getBoolean(KEY_AUTO_UPLOAD, true)
        )
    }

    /**
     * 是否启用了图床
     */
    fun isImageHostEnabled(): Boolean {
        return prefs.getBoolean(KEY_IS_ENABLED, false) && getConfig().type != ImageHostType.NONE
    }

    /**
     * 是否自动上传
     */
    fun isAutoUpload(): Boolean {
        return prefs.getBoolean(KEY_AUTO_UPLOAD, true)
    }

    /**
     * 添加上传历史
     */
    fun addUploadHistory(history: UploadHistory) {
        val historyList = getUploadHistory().toMutableList()
        historyList.add(0, history)

        // 限制历史记录数量
        while (historyList.size > MAX_HISTORY_SIZE) {
            historyList.removeAt(historyList.size - 1)
        }

        saveUploadHistory(historyList)
    }

    /**
     * 获取上传历史
     */
    fun getUploadHistory(): List<UploadHistory> {
        val jsonString = prefs.getString(KEY_UPLOAD_HISTORY, "[]") ?: "[]"
        return parseUploadHistory(jsonString)
    }

    /**
     * 删除上传历史
     */
    fun removeUploadHistory(id: Long) {
        val historyList = getUploadHistory().toMutableList()
        historyList.removeAll { it.id == id }
        saveUploadHistory(historyList)
    }

    /**
     * 清空上传历史
     */
    fun clearUploadHistory() {
        prefs.edit().remove(KEY_UPLOAD_HISTORY).apply()
    }

    /**
     * 保存上传历史
     */
    private fun saveUploadHistory(historyList: List<UploadHistory>) {
        val jsonArray = JSONArray()
        historyList.forEach { item ->
            jsonArray.put(serializeUploadHistory(item))
        }
        prefs.edit().putString(KEY_UPLOAD_HISTORY, jsonArray.toString()).apply()
    }

    /**
     * 解析上传历史 JSON
     */
    private fun parseUploadHistory(jsonString: String): List<UploadHistory> {
        val list = mutableListOf<UploadHistory>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(deserializeUploadHistory(jsonObject))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /**
     * 序列化上传历史
     */
    private fun serializeUploadHistory(history: UploadHistory): JSONObject {
        return JSONObject().apply {
            put("id", history.id)
            put("originalName", history.originalName)
            put("url", history.url)
            put("deleteUrl", history.deleteUrl ?: "")
            put("hostType", history.hostType.name)
            put("uploadTime", history.uploadTime)
            put("fileSize", history.fileSize)
        }
    }

    /**
     * 反序列化上传历史
     */
    private fun deserializeUploadHistory(jsonObject: JSONObject): UploadHistory {
        return UploadHistory(
            id = jsonObject.getLong("id"),
            originalName = jsonObject.getString("originalName"),
            url = jsonObject.getString("url"),
            deleteUrl = jsonObject.optString("deleteUrl", "").takeIf { it.isNotEmpty() },
            hostType = ImageHostType.fromName(jsonObject.getString("hostType")) ?: ImageHostType.NONE,
            uploadTime = jsonObject.getLong("uploadTime"),
            fileSize = jsonObject.getLong("fileSize")
        )
    }

    /**
     * 重置所有配置
     */
    fun reset() {
        prefs.edit().clear().apply()
    }
}
