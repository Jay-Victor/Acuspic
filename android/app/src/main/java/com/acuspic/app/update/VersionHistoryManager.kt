package com.acuspic.app.update

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 版本历史管理器
 * 管理已下载的版本历史记录
 */
class VersionHistoryManager(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "VersionHistoryPrefs"
        private const val KEY_VERSION_HISTORY = "version_history"
        private const val MAX_HISTORY_SIZE = 10
        private const val TAG = "VersionHistoryManager"
    }

    /**
     * 添加版本历史记录
     */
    fun addVersionHistory(versionHistory: VersionHistory) {
        val history = getVersionHistory().toMutableList()
        
        // 如果已存在相同版本，先移除
        history.removeAll { it.versionCode == versionHistory.versionCode }
        
        // 添加到开头
        history.add(0, versionHistory)
        
        // 限制历史记录数量
        while (history.size > MAX_HISTORY_SIZE) {
            // 删除最旧的未下载版本
            val oldestNotDownloaded = history.findLast { !it.isDownloaded }
            if (oldestNotDownloaded != null) {
                history.remove(oldestNotDownloaded)
            } else {
                // 如果都下载了，删除最旧的
                history.removeAt(history.size - 1)
            }
        }
        
        saveVersionHistory(history)
    }

    /**
     * 获取版本历史列表
     */
    fun getVersionHistory(): List<VersionHistory> {
        val jsonString = prefs.getString(KEY_VERSION_HISTORY, "[]") ?: "[]"
        return parseVersionHistory(jsonString)
    }

    /**
     * 获取已下载的版本列表
     */
    fun getDownloadedVersions(): List<VersionHistory> {
        return getVersionHistory().filter { it.isDownloaded && it.localPath != null }
    }

    /**
     * 获取特定版本的历史记录
     */
    fun getVersionHistory(versionCode: Int): VersionHistory? {
        return getVersionHistory().find { it.versionCode == versionCode }
    }

    /**
     * 更新版本下载状态
     */
    fun updateDownloadStatus(versionCode: Int, isDownloaded: Boolean, localPath: String?) {
        val history = getVersionHistory().toMutableList()
        val index = history.indexOfFirst { it.versionCode == versionCode }
        
        if (index != -1) {
            val old = history[index]
            history[index] = old.copy(isDownloaded = isDownloaded, localPath = localPath)
            saveVersionHistory(history)
        }
    }

    /**
     * 删除版本历史记录
     */
    fun removeVersionHistory(versionCode: Int) {
        val history = getVersionHistory().toMutableList()
        history.removeAll { it.versionCode == versionCode }
        saveVersionHistory(history)
    }

    /**
     * 清空版本历史
     */
    fun clearVersionHistory() {
        prefs.edit().remove(KEY_VERSION_HISTORY).apply()
    }

    /**
     * 保存版本历史到SharedPreferences
     */
    private fun saveVersionHistory(history: List<VersionHistory>) {
        val jsonArray = JSONArray()
        history.forEach { item ->
            jsonArray.put(serializeVersionHistory(item))
        }
        prefs.edit().putString(KEY_VERSION_HISTORY, jsonArray.toString()).apply()
    }

    /**
     * 解析版本历史JSON
     */
    private fun parseVersionHistory(jsonString: String): List<VersionHistory> {
        val list = mutableListOf<VersionHistory>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                list.add(deserializeVersionHistory(jsonObject))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse version history: ${e.message}", e)
        }
        return list
    }

    /**
     * 序列化版本历史
     */
    private fun serializeVersionHistory(history: VersionHistory): JSONObject {
        return JSONObject().apply {
            put("versionCode", history.versionCode)
            put("versionName", history.versionName)
            put("publishDate", history.publishDate)
            put("releaseNotes", history.releaseNotes)
            put("downloadUrl", history.downloadUrl)
            put("isDownloaded", history.isDownloaded)
            put("localPath", history.localPath ?: "")
        }
    }

    /**
     * 反序列化版本历史
     */
    private fun deserializeVersionHistory(jsonObject: JSONObject): VersionHistory {
        return VersionHistory(
            versionCode = jsonObject.getInt("versionCode"),
            versionName = jsonObject.getString("versionName"),
            publishDate = jsonObject.optString("publishDate", ""),
            releaseNotes = jsonObject.optString("releaseNotes", ""),
            downloadUrl = jsonObject.optString("downloadUrl", ""),
            isDownloaded = jsonObject.optBoolean("isDownloaded", false),
            localPath = jsonObject.optString("localPath", "").takeIf { it.isNotEmpty() }
        )
    }
}
