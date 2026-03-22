package com.acuspic.app

import android.app.Activity
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.acuspic.app.imagehost.ImageHostPreferences
import com.acuspic.app.markdown.MarkdownFlavor
import com.acuspic.app.markdown.MarkdownPreferences
import com.acuspic.app.update.DownloadStatus
import com.acuspic.app.update.RepositoryType
import com.acuspic.app.update.UpdateCheckResult
import com.acuspic.app.update.UpdateManager
import com.acuspic.app.update.VersionInfo
import kotlinx.coroutines.launch
import java.io.File

class SettingsActivity : AppCompatActivity() {

    private lateinit var updateManager: UpdateManager
    private lateinit var markdownPreferences: MarkdownPreferences
    private lateinit var imageHostPreferences: ImageHostPreferences

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvCurrentRepository: TextView
    private lateinit var tvCurrentMarkdownFlavor: TextView
    private lateinit var tvCurrentImageHost: TextView
    private lateinit var btnCheckUpdate: LinearLayout
    private lateinit var tvUpdateStatus: TextView
    private lateinit var progressUpdate: LinearProgressIndicator
    private lateinit var btnVersionHistory: LinearLayout
    private lateinit var btnRepositorySettings: LinearLayout
    private lateinit var btnMarkdownFlavor: LinearLayout
    private lateinit var btnImageHost: LinearLayout
    private lateinit var btnAbout: LinearLayout
    private lateinit var btnLicense: LinearLayout

    private var isCheckingUpdate = false
    private var currentVersionInfo: VersionInfo? = null
    private var downloadProgressDialog: DownloadProgressDialog? = null

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        handleInstallPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        
        updateManager = UpdateManager(this)
        markdownPreferences = MarkdownPreferences(this)
        imageHostPreferences = ImageHostPreferences(this)

        initViews()
        setupListeners()
        updateUI()
    }

    override fun onResume() {
        super.onResume()
        checkPendingInstall()
    }

    private fun initViews() {
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvCurrentRepository = findViewById(R.id.tvCurrentRepository)
        tvCurrentMarkdownFlavor = findViewById(R.id.tvCurrentMarkdownFlavor)
        tvCurrentImageHost = findViewById(R.id.tvCurrentImageHost)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        tvUpdateStatus = findViewById(R.id.tvUpdateStatus)
        progressUpdate = findViewById(R.id.progressUpdate)
        btnVersionHistory = findViewById(R.id.btnVersionHistory)
        btnRepositorySettings = findViewById(R.id.btnRepositorySettings)
        btnMarkdownFlavor = findViewById(R.id.btnMarkdownFlavor)
        btnImageHost = findViewById(R.id.btnImageHost)
        btnAbout = findViewById(R.id.btnAbout)
        btnLicense = findViewById(R.id.btnLicense)
    }
    
    private fun setupListeners() {
        btnCheckUpdate.setOnClickListener {
            if (!isCheckingUpdate) {
                checkForUpdate()
            }
        }
        
        btnVersionHistory.setOnClickListener {
            startActivity(Intent(this, VersionHistoryActivity::class.java))
        }
        
        btnRepositorySettings.setOnClickListener {
            showRepositorySelector()
        }
        
        btnMarkdownFlavor.setOnClickListener {
            showMarkdownFlavorSelector()
        }

        btnImageHost.setOnClickListener {
            startActivity(Intent(this, ImageHostSettingsActivity::class.java))
        }

        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        
        btnLicense.setOnClickListener {
            showLicenseDialog()
        }
    }
    
    private fun updateUI() {
        tvCurrentVersion.text = "当前版本: ${updateManager.getCurrentVersionInfo()}"
        tvCurrentRepository.text = when (updateManager.getRepositoryType()) {
            RepositoryType.GITHUB -> "GitHub (国外)"
            RepositoryType.GITEE -> "Gitee (国内)"
            RepositoryType.AUTO -> "自动选择"
        }
        tvCurrentMarkdownFlavor.text = markdownPreferences.getMarkdownFlavor().displayName

        val imageHostConfig = imageHostPreferences.getConfig()
        tvCurrentImageHost.text = if (imageHostConfig.isEnabled) {
            imageHostConfig.type.displayName
        } else {
            "未启用"
        }
    }
    
    private fun checkForUpdate() {
        if (!isNetworkAvailable()) {
            showNoNetworkDialog()
            return
        }
        
        isCheckingUpdate = true
        tvUpdateStatus.visibility = View.VISIBLE
        tvUpdateStatus.text = "正在检查更新..."
        progressUpdate.visibility = View.VISIBLE
        progressUpdate.isIndeterminate = true
        
        updateManager.checkUpdate(
            repositoryType = updateManager.getRepositoryType(),
            showNoUpdateToast = false
        ) { result ->
            isCheckingUpdate = false
            progressUpdate.visibility = View.GONE
            
            when (result) {
                is UpdateCheckResult.HasUpdate -> {
                    tvUpdateStatus.visibility = View.GONE
                    showUpdateDialog(result)
                }
                is UpdateCheckResult.NoUpdate -> {
                    tvUpdateStatus.text = "已是最新版本"
                    tvUpdateStatus.setTextColor(getColor(com.acuspic.app.R.color.success))
                    Toast.makeText(this, "已是最新版本", Toast.LENGTH_SHORT).show()
                    
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(2000)
                        tvUpdateStatus.visibility = View.GONE
                    }
                }
                is UpdateCheckResult.Error -> {
                    tvUpdateStatus.text = "检查失败: ${result.message}"
                    tvUpdateStatus.setTextColor(getColor(com.acuspic.app.R.color.error))
                    
                    lifecycleScope.launch {
                        kotlinx.coroutines.delay(3000)
                        tvUpdateStatus.visibility = View.GONE
                    }
                }
            }
        }
    }
    
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    
    private fun showNoNetworkDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("网络不可用")
            .setMessage("请检查您的网络连接后重试")
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showUpdateDialog(result: UpdateCheckResult.HasUpdate) {
        val versionInfo = result.versionInfo
        currentVersionInfo = versionInfo
        
        val dialogView = layoutInflater.inflate(R.layout.dialog_update_info, null)
        val tvVersion = dialogView.findViewById<TextView>(R.id.tvVersion)
        val tvDate = dialogView.findViewById<TextView>(R.id.tvDate)
        val tvSize = dialogView.findViewById<TextView>(R.id.tvSize)
        val tvNotes = dialogView.findViewById<TextView>(R.id.tvNotes)
        val tvBadge = dialogView.findViewById<TextView>(R.id.tvBadge)
        
        tvVersion.text = "v${versionInfo.versionName}"
        tvDate.text = versionInfo.publishDate.ifEmpty { "未知" }
        tvSize.text = formatFileSize(versionInfo.fileSize)
        tvNotes.text = formatReleaseNotes(versionInfo.releaseNotes)
        
        if (result.isForceUpdate) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = "强制更新"
            tvBadge.setBackgroundColor(getColor(com.acuspic.app.R.color.error))
        } else if (versionInfo.isImportant) {
            tvBadge.visibility = View.VISIBLE
            tvBadge.text = "重要更新"
            tvBadge.setBackgroundColor(getColor(com.acuspic.app.R.color.warning))
        } else {
            tvBadge.visibility = View.GONE
        }
        
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(!result.isForceUpdate)
            .setPositiveButton("立即下载") { _, _ ->
                startDownload(versionInfo)
            }
        
        if (!result.isForceUpdate) {
            dialog.setNegativeButton("稍后提醒", null)
                .setNeutralButton("跳过此版本") { _, _ ->
                    updateManager.skipVersion(versionInfo.versionCode)
                    Toast.makeText(this, "已跳过此版本", Toast.LENGTH_SHORT).show()
                }
        }
        
        dialog.show()
    }
    
    private fun formatReleaseNotes(notes: String): String {
        if (notes.isEmpty()) return "暂无更新说明"
        return notes
            .replace(Regex("#+\\s*"), "")
            .replace(Regex("\\*\\*([^*]+)\\*\\*"), "$1")
            .replace(Regex("-\\s*"), "• ")
            .trim()
    }
    
    private fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "未知"
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        return when {
            mb >= 1 -> String.format("%.1f MB", mb)
            kb >= 1 -> String.format("%.1f KB", kb)
            else -> "$bytes B"
        }
    }
    
    private fun startDownload(versionInfo: VersionInfo) {
        downloadProgressDialog = DownloadProgressDialog(this)
        downloadProgressDialog?.setVersionInfo(versionInfo.versionName)
        downloadProgressDialog?.show()
        
        updateManager.downloadAndInstall(versionInfo)
        
        lifecycleScope.launch {
            updateManager.downloadStatus.collect { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        downloadProgressDialog?.updateProgress(status.progress, status.speed)
                    }
                    is DownloadStatus.Success -> {
                        downloadProgressDialog?.setComplete()
                    }
                    is DownloadStatus.Error -> {
                        downloadProgressDialog?.setError(status.message)
                    }
                    is DownloadStatus.Cancelled -> {
                        downloadProgressDialog?.dismiss()
                    }
                    else -> {}
                }
            }
        }
        
        downloadProgressDialog?.setOnCancelListener {
            updateManager.cancelDownload()
        }
        
        downloadProgressDialog?.setOnBackgroundListener {
            Toast.makeText(this, "正在后台下载，完成后将自动提示安装", Toast.LENGTH_LONG).show()
        }

        lifecycleScope.launch {
            updateManager.installStatus.collect { status ->
                when (status) {
                    is UpdateManager.InstallStatus.ReadyToInstall -> {
                        tryInstallApk(status.file)
                    }
                    is UpdateManager.InstallStatus.NeedPermission -> {
                        requestInstallPermission(status.file)
                    }
                    is UpdateManager.InstallStatus.Error -> {
                        downloadProgressDialog?.setError(status.message)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun tryInstallApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                requestInstallPermission(file)
                return
            }
        }
        
        downloadProgressDialog?.dismiss()
        
        MaterialAlertDialogBuilder(this)
            .setTitle("安装更新")
            .setMessage("新版本已下载完成，是否立即安装？")
            .setPositiveButton("立即安装") { _, _ ->
                updateManager.installApk(file)
            }
            .setNegativeButton("稍后安装", null)
            .show()
    }

    private fun requestInstallPermission(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                downloadProgressDialog?.dismiss()
                
                MaterialAlertDialogBuilder(this)
                    .setTitle("需要权限")
                    .setMessage("安装应用需要授权\"安装未知来源应用\"权限，是否前往设置？")
                    .setPositiveButton("去设置") { _, _ ->
                        val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                        intent.data = android.net.Uri.parse("package:$packageName")
                        installPermissionLauncher.launch(intent)
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
        }
    }

    private fun handleInstallPermissionResult() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val hasPermission = packageManager.canRequestPackageInstalls()
            
            if (hasPermission) {
                Toast.makeText(this, "授权成功", Toast.LENGTH_SHORT).show()
                checkPendingInstall()
            } else {
                Toast.makeText(this, "需要授权才能安装应用", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun checkPendingInstall() {
        val file = updateManager.getPendingInstallFile()
        if (file != null && file.exists()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (packageManager.canRequestPackageInstalls()) {
                    tryInstallApk(file)
                }
            } else {
                tryInstallApk(file)
            }
        }
    }
    
    private fun showRepositorySelector() {
        val options = arrayOf("自动选择 (推荐)", "Gitee (国内加速)", "GitHub (国外)")
        val currentType = updateManager.getRepositoryType()
        val selectedIndex = when (currentType) {
            RepositoryType.AUTO -> 0
            RepositoryType.GITEE -> 1
            RepositoryType.GITHUB -> 2
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("选择下载源")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newType = when (which) {
                    0 -> RepositoryType.AUTO
                    1 -> RepositoryType.GITEE
                    2 -> RepositoryType.GITHUB
                    else -> RepositoryType.AUTO
                }
                updateManager.setRepositoryType(newType)
                updateUI()
                val tip = when (newType) {
                    RepositoryType.AUTO -> "将根据网络环境自动选择最优源"
                    RepositoryType.GITEE -> "适合国内用户，下载速度更快"
                    RepositoryType.GITHUB -> "适合国外用户或需要最新版本"
                }
                Toast.makeText(this, tip, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
    
    private fun showLicenseDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("许可协议")
            .setMessage("Acuspic 软件许可协议\n\n版权所有 © 2026 Jay-Victor\n\n个人使用免费，商业使用需购买授权。\n\n作者：Jay-Victor\n邮箱：18261738221@163.com\nGitHub：https://github.com/Jay-Victor/Acuspic\nGitee：https://gitee.com/Jay-Victor/Acuspic\n\n完整协议请查看项目仓库 LICENSE 文件。")
            .setPositiveButton("确定", null)
            .show()
    }
    
    private fun showMarkdownFlavorSelector() {
        val flavors = MarkdownFlavor.values()
        val options = flavors.map { it.displayName }.toTypedArray()
        val currentFlavor = markdownPreferences.getMarkdownFlavor()
        val selectedIndex = flavors.indexOf(currentFlavor)
        
        MaterialAlertDialogBuilder(this)
            .setTitle("选择Markdown语法标准")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                val newFlavor = flavors[which]
                markdownPreferences.setMarkdownFlavor(newFlavor)
                updateUI()
                Toast.makeText(this, "已切换到: ${newFlavor.displayName}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
