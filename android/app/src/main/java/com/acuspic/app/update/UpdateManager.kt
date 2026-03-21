package com.acuspic.app.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新管理器
 * 统一管理版本检查、下载、安装等功能
 */
class UpdateManager(private val context: Context) {

    private val versionChecker = VersionChecker(context)
    private val downloadManager = DownloadManager(context)
    private val historyManager = VersionHistoryManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    // 下载状态
    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus

    // 当前下载任务
    private var currentDownloadJob: kotlinx.coroutines.Job? = null

    companion object {
        // 当前版本配置
        const val CURRENT_VERSION_CODE = 1
        const val CURRENT_VERSION_NAME = "1.0.0"
        
        // APK文件名（使用大写Apk避免微信/QQ添加.1后缀）
        fun getApkFileName(versionName: String = CURRENT_VERSION_NAME): String {
            return "Acuspic-v$versionName.Apk"
        }
    }

    /**
     * 检查更新
     * @param repositoryType 仓库类型
     * @param showNoUpdateToast 是否显示"已是最新版本"提示
     * @param callback 检查结果回调
     */
    fun checkUpdate(
        repositoryType: RepositoryType = RepositoryType.AUTO,
        showNoUpdateToast: Boolean = false,
        callback: (UpdateCheckResult) -> Unit
    ) {
        scope.launch {
            val result = versionChecker.checkUpdate(CURRENT_VERSION_CODE, repositoryType)
            
            when (result) {
                is UpdateCheckResult.HasUpdate -> {
                    // 检查是否已跳过此版本
                    val skippedVersion = versionChecker.getSkippedVersion()
                    if (skippedVersion == result.versionInfo.versionCode && !result.isForceUpdate) {
                        if (showNoUpdateToast) {
                            Toast.makeText(context, "已跳过当前版本", Toast.LENGTH_SHORT).show()
                        }
                        callback(UpdateCheckResult.NoUpdate)
                    } else {
                        callback(result)
                    }
                }
                is UpdateCheckResult.NoUpdate -> {
                    if (showNoUpdateToast) {
                        Toast.makeText(context, "已是最新版本", Toast.LENGTH_SHORT).show()
                    }
                    callback(result)
                }
                is UpdateCheckResult.Error -> {
                    Toast.makeText(context, result.message, Toast.LENGTH_LONG).show()
                    callback(result)
                }
            }
        }
    }

    /**
     * 下载并安装更新
     * @param versionInfo 版本信息
     */
    fun downloadAndInstall(versionInfo: VersionInfo) {
        if (versionInfo.downloadUrl.isEmpty()) {
            Toast.makeText(context, "下载地址无效", Toast.LENGTH_SHORT).show()
            return
        }

        // 生成文件名
        val fileName = "Acuspic-v${versionInfo.versionName}.Apk"
        
        // 添加到版本历史
        historyManager.addVersionHistory(
            VersionHistory(
                versionCode = versionInfo.versionCode,
                versionName = versionInfo.versionName,
                publishDate = versionInfo.publishDate,
                releaseNotes = versionInfo.releaseNotes,
                downloadUrl = versionInfo.downloadUrl
            )
        )

        // 开始下载
        currentDownloadJob = scope.launch {
            downloadManager.download(versionInfo.downloadUrl, fileName, true)
                .collect { status ->
                    _downloadStatus.value = status
                    
                    when (status) {
                        is DownloadStatus.Success -> {
                            // 更新下载状态
                            historyManager.updateDownloadStatus(
                                versionInfo.versionCode,
                                true,
                                downloadManager.getDownloadedFile(fileName)?.absolutePath
                            )
                            
                            // 安装APK
                            installApk(fileName)
                        }
                        is DownloadStatus.Error -> {
                            Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
        }
    }

    /**
     * 安装APK
     */
    private fun installApk(fileName: String) {
        val file = downloadManager.getDownloadedFile(fileName) ?: return
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 使用FileProvider
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(
                    Uri.fromFile(file),
                    "application/vnd.android.package-archive"
                )
            }
        }
        
        context.startActivity(intent)
    }

    /**
     * 取消下载
     */
    fun cancelDownload() {
        currentDownloadJob?.cancel()
        downloadManager.cancel()
        _downloadStatus.value = DownloadStatus.Cancelled
    }

    /**
     * 跳过当前版本
     */
    fun skipVersion(versionCode: Int) {
        versionChecker.setSkippedVersion(versionCode)
        Toast.makeText(context, "已跳过此版本", Toast.LENGTH_SHORT).show()
    }

    /**
     * 回退到指定版本
     * @param versionCode 目标版本号
     */
    fun rollbackToVersion(versionCode: Int): Boolean {
        val versionHistory = historyManager.getVersionHistory(versionCode)
        
        if (versionHistory == null || !versionHistory.isDownloaded || versionHistory.localPath == null) {
            Toast.makeText(context, "未找到该版本的下载文件", Toast.LENGTH_SHORT).show()
            return false
        }
        
        val file = File(versionHistory.localPath)
        if (!file.exists()) {
            Toast.makeText(context, "安装文件已删除", Toast.LENGTH_SHORT).show()
            return false
        }
        
        // 安装旧版本
        val intent = Intent(Intent.ACTION_VIEW).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive")
            }
        }
        
        context.startActivity(intent)
        return true
    }

    /**
     * 获取版本历史列表
     */
    fun getVersionHistory(): List<VersionHistory> {
        return historyManager.getVersionHistory()
    }

    /**
     * 获取已下载的版本列表
     */
    fun getDownloadedVersions(): List<VersionHistory> {
        return historyManager.getDownloadedVersions()
    }

    /**
     * 删除版本文件
     */
    fun deleteVersionFile(versionCode: Int) {
        val versionHistory = historyManager.getVersionHistory(versionCode)
        versionHistory?.let {
            val fileName = "Acuspic-v${it.versionName}.Apk"
            downloadManager.deleteDownloadedFile(fileName)
            historyManager.updateDownloadStatus(versionCode, false, null)
        }
    }

    /**
     * 删除版本（接受VersionHistory对象）
     */
    fun deleteVersion(version: VersionHistory) {
        val fileName = "Acuspic-v${version.versionName}.Apk"
        downloadManager.deleteDownloadedFile(fileName)
        historyManager.updateDownloadStatus(version.versionCode, false, null)
    }

    /**
     * 回退到指定版本（接受版本名）
     */
    fun rollbackToVersion(versionName: String): Boolean {
        val versionHistory = historyManager.getVersionHistory().find { it.versionName == versionName }
            ?: run {
                Toast.makeText(context, "未找到该版本", Toast.LENGTH_SHORT).show()
                return false
            }
        return rollbackToVersion(versionHistory.versionCode)
    }

    /**
     * 清除所有下载文件
     */
    fun clearAllDownloads() {
        val downloadedVersions = historyManager.getDownloadedVersions()
        downloadedVersions.forEach { version ->
            val fileName = "Acuspic-v${version.versionName}.Apk"
            downloadManager.deleteDownloadedFile(fileName)
        }
        historyManager.clearVersionHistory()
    }

    /**
     * 获取当前版本信息
     */
    fun getCurrentVersionInfo(): String {
        return "v$CURRENT_VERSION_NAME"
    }

    /**
     * 设置仓库类型
     */
    fun setRepositoryType(type: RepositoryType) {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("repository_type", type.name).apply()
    }

    /**
     * 获取仓库类型
     */
    fun getRepositoryType(): RepositoryType {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        val typeName = prefs.getString("repository_type", RepositoryType.AUTO.name)
        return try {
            RepositoryType.valueOf(typeName ?: RepositoryType.AUTO.name)
        } catch (e: Exception) {
            RepositoryType.AUTO
        }
    }
}
