package com.acuspic.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * 更新管理器 - 单例模式
 * 负责版本检查、下载、安装等更新相关功能
 */
class UpdateManager private constructor(private val context: Context) {

    private val versionChecker = VersionChecker(context)
    private val downloadManager = DownloadManager(context)
    private val historyManager = VersionHistoryManager(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val _downloadStatus = MutableStateFlow<DownloadStatus>(DownloadStatus.Idle)
    val downloadStatus: StateFlow<DownloadStatus> = _downloadStatus

    private val _installStatus = MutableStateFlow<InstallStatus>(InstallStatus.Idle)
    val installStatus: StateFlow<InstallStatus> = _installStatus

    private var currentDownloadJob: kotlinx.coroutines.Job? = null
    private var pendingInstallFile: File? = null

    companion object {
        private const val TAG = "UpdateManager"
        
        const val CURRENT_VERSION_CODE = 7
        const val CURRENT_VERSION_NAME = "1.0.6"
        
        @Volatile
        private var instance: UpdateManager? = null
        
        /**
         * 获取 UpdateManager 单例实例
         */
        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
        
        fun getApkFileName(versionName: String = CURRENT_VERSION_NAME): String {
            return "Acuspic-v$versionName.Apk"
        }
    }

    fun checkUpdate(
        repositoryType: RepositoryType = RepositoryType.AUTO,
        showNoUpdateToast: Boolean = false,
        callback: (UpdateCheckResult) -> Unit
    ) {
        scope.launch {
            val result = versionChecker.checkUpdate(CURRENT_VERSION_CODE, repositoryType)
            
            when (result) {
                is UpdateCheckResult.HasUpdate -> {
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

    fun downloadAndInstall(versionInfo: VersionInfo) {
        if (versionInfo.downloadUrl.isEmpty()) {
            Toast.makeText(context, "下载地址无效", Toast.LENGTH_SHORT).show()
            return
        }

        // 重置状态
        _downloadStatus.value = DownloadStatus.Idle
        _installStatus.value = InstallStatus.Idle
        pendingInstallFile = null

        val fileName = "Acuspic-v${versionInfo.versionName}.Apk"
        
        historyManager.addVersionHistory(
            VersionHistory(
                versionCode = versionInfo.versionCode,
                versionName = versionInfo.versionName,
                publishDate = versionInfo.publishDate,
                releaseNotes = versionInfo.releaseNotes,
                downloadUrl = versionInfo.downloadUrl
            )
        )

        currentDownloadJob = scope.launch {
            downloadManager.download(versionInfo.downloadUrl, fileName, true)
                .collect { status ->
                    _downloadStatus.value = status
                    
                    when (status) {
                        is DownloadStatus.Success -> {
                            historyManager.updateDownloadStatus(
                                versionInfo.versionCode,
                                true,
                                downloadManager.getDownloadedFile(fileName)?.absolutePath
                            )
                            
                            val file = downloadManager.getDownloadedFile(fileName)
                            if (file != null && file.exists()) {
                                pendingInstallFile = file
                                _installStatus.value = InstallStatus.ReadyToInstall(file)
                            }
                        }
                        is DownloadStatus.Error -> {
                            Toast.makeText(context, status.message, Toast.LENGTH_LONG).show()
                        }
                        else -> {}
                    }
                }
        }
    }

    sealed class InstallStatus {
        object Idle : InstallStatus()
        data class NeedPermission(val file: File) : InstallStatus()
        data class ReadyToInstall(val file: File) : InstallStatus()
        object Installing : InstallStatus()
        data class Error(val message: String) : InstallStatus()
    }

    fun getPendingInstallFile(): File? = pendingInstallFile

    fun clearPendingInstall() {
        pendingInstallFile = null
        _installStatus.value = InstallStatus.Idle
    }

    fun checkAndPrepareInstall(): File? {
        val file = pendingInstallFile
        if (file == null || !file.exists()) {
            Log.e(TAG, "没有待安装的文件")
            return null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "需要请求安装权限")
                _installStatus.value = InstallStatus.NeedPermission(file)
                return null
            }
        }

        return file
    }

    fun installApk(file: File): Boolean {
        return try {
            if (!file.exists()) {
                Log.e(TAG, "APK文件不存在: ${file.absolutePath}")
                Toast.makeText(context, "安装文件不存在", Toast.LENGTH_LONG).show()
                _installStatus.value = InstallStatus.Error("安装文件不存在")
                return false
            }
            
            Log.i(TAG, "开始安装APK: ${file.absolutePath}, 大小: ${file.length()} bytes")
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    Log.w(TAG, "没有安装未知来源应用的权限")
                    _installStatus.value = InstallStatus.NeedPermission(file)
                    return false
                }
            }
            
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
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    Log.d(TAG, "使用FileProvider URI: $uri")
                } else {
                    setDataAndType(
                        Uri.fromFile(file),
                        "application/vnd.android.package-archive"
                    )
                    Log.d(TAG, "使用file:// URI")
                }
            }
            
            val packageManager = context.packageManager
            val activities = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            if (activities.isNullOrEmpty()) {
                Log.e(TAG, "无法找到处理APK安装的应用")
                Toast.makeText(context, "无法打开安装界面", Toast.LENGTH_LONG).show()
                _installStatus.value = InstallStatus.Error("无法打开安装界面")
                return false
            }
            
            Log.i(TAG, "启动安装界面...")
            _installStatus.value = InstallStatus.Installing
            context.startActivity(intent)
            true
            
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败: ${e.message}", e)
            Toast.makeText(context, "安装失败: ${e.message}", Toast.LENGTH_LONG).show()
            _installStatus.value = InstallStatus.Error(e.message ?: "未知错误")
            false
        }
    }

    fun cancelDownload() {
        currentDownloadJob?.cancel()
        downloadManager.cancel()
        _downloadStatus.value = DownloadStatus.Cancelled
    }

    fun skipVersion(versionCode: Int) {
        versionChecker.setSkippedVersion(versionCode)
        Toast.makeText(context, "已跳过此版本", Toast.LENGTH_SHORT).show()
    }

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
        
        pendingInstallFile = file
        _installStatus.value = InstallStatus.ReadyToInstall(file)
        return true
    }

    fun getVersionHistory(): List<VersionHistory> {
        return historyManager.getVersionHistory()
    }

    fun getDownloadedVersions(): List<VersionHistory> {
        return historyManager.getDownloadedVersions()
    }

    fun deleteVersionFile(versionCode: Int) {
        val versionHistory = historyManager.getVersionHistory(versionCode)
        versionHistory?.let {
            val fileName = "Acuspic-v${it.versionName}.Apk"
            downloadManager.deleteDownloadedFile(fileName)
            historyManager.updateDownloadStatus(versionCode, false, null)
        }
    }

    fun deleteVersion(version: VersionHistory) {
        val fileName = "Acuspic-v${version.versionName}.Apk"
        downloadManager.deleteDownloadedFile(fileName)
        historyManager.updateDownloadStatus(version.versionCode, false, null)
    }

    fun rollbackToVersion(versionName: String): Boolean {
        val versionHistory = historyManager.getVersionHistory().find { it.versionName == versionName }
            ?: run {
                Toast.makeText(context, "未找到该版本", Toast.LENGTH_SHORT).show()
                return false
            }
        return rollbackToVersion(versionHistory.versionCode)
    }

    fun clearAllDownloads() {
        val downloadedVersions = historyManager.getDownloadedVersions()
        downloadedVersions.forEach { version ->
            val fileName = "Acuspic-v${version.versionName}.Apk"
            downloadManager.deleteDownloadedFile(fileName)
        }
        historyManager.clearVersionHistory()
    }

    fun getCurrentVersionInfo(): String {
        return "v$CURRENT_VERSION_NAME"
    }

    fun setRepositoryType(type: RepositoryType) {
        val prefs = context.getSharedPreferences("UpdatePrefs", Context.MODE_PRIVATE)
        prefs.edit().putString("repository_type", type.name).apply()
    }

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
