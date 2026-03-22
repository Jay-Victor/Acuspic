package com.acuspic.app

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.acuspic.app.imagehost.ImageHostPreferences
import com.acuspic.app.markdown.MarkdownFlavor
import com.acuspic.app.markdown.MarkdownPreferences
import com.acuspic.app.update.RepositoryType
import com.acuspic.app.update.UpdateCheckResult
import com.acuspic.app.update.UpdateManager
import kotlinx.coroutines.launch

/**
 * 设置页面
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var updateManager: UpdateManager
    private lateinit var markdownPreferences: MarkdownPreferences
    private lateinit var imageHostPreferences: ImageHostPreferences

    private lateinit var tvCurrentVersion: TextView
    private lateinit var tvCurrentRepository: TextView
    private lateinit var tvCurrentMarkdownFlavor: TextView
    private lateinit var tvCurrentImageHost: TextView
    private lateinit var btnCheckUpdate: LinearLayout
    private lateinit var btnVersionHistory: LinearLayout
    private lateinit var btnRepositorySettings: LinearLayout
    private lateinit var btnMarkdownFlavor: LinearLayout
    private lateinit var btnImageHost: LinearLayout
    private lateinit var btnAbout: LinearLayout
    private lateinit var btnLicense: LinearLayout

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

    private fun initViews() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        tvCurrentVersion = findViewById(R.id.tvCurrentVersion)
        tvCurrentRepository = findViewById(R.id.tvCurrentRepository)
        tvCurrentMarkdownFlavor = findViewById(R.id.tvCurrentMarkdownFlavor)
        tvCurrentImageHost = findViewById(R.id.tvCurrentImageHost)
        btnCheckUpdate = findViewById(R.id.btnCheckUpdate)
        btnVersionHistory = findViewById(R.id.btnVersionHistory)
        btnRepositorySettings = findViewById(R.id.btnRepositorySettings)
        btnMarkdownFlavor = findViewById(R.id.btnMarkdownFlavor)
        btnImageHost = findViewById(R.id.btnImageHost)
        btnAbout = findViewById(R.id.btnAbout)
        btnLicense = findViewById(R.id.btnLicense)
    }
    
    private fun setupListeners() {
        // 检查更新
        btnCheckUpdate.setOnClickListener {
            checkForUpdate()
        }
        
        // 版本历史
        btnVersionHistory.setOnClickListener {
            startActivity(Intent(this, VersionHistoryActivity::class.java))
        }
        
        // 下载源设置
        btnRepositorySettings.setOnClickListener {
            showRepositorySelector()
        }
        
        // Markdown语法选择
        btnMarkdownFlavor.setOnClickListener {
            showMarkdownFlavorSelector()
        }

        // 图床设置
        btnImageHost.setOnClickListener {
            startActivity(Intent(this, ImageHostSettingsActivity::class.java))
        }

        // 关于
        btnAbout.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }
        
        // 开源协议
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
        Toast.makeText(this, "正在检查更新...", Toast.LENGTH_SHORT).show()
        
        updateManager.checkUpdate(
            repositoryType = updateManager.getRepositoryType(),
            showNoUpdateToast = true
        ) { result ->
            when (result) {
                is UpdateCheckResult.HasUpdate -> {
                    showUpdateDialog(result)
                }
                else -> {
                    // 其他情况已在UpdateManager中处理
                }
            }
        }
    }
    
    private fun showUpdateDialog(result: UpdateCheckResult.HasUpdate) {
        val versionInfo = result.versionInfo
        val title = if (result.isForceUpdate) "重要更新" else "发现新版本"
        
        val message = buildString {
            appendLine("版本: v${versionInfo.versionName}")
            appendLine("发布时间: ${versionInfo.publishDate}")
            appendLine()
            appendLine("更新内容:")
            append(versionInfo.releaseNotes)
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(!result.isForceUpdate)
            .setPositiveButton("立即更新") { _, _ ->
                showDownloadDialog(versionInfo)
            }
            .apply {
                if (!result.isForceUpdate) {
                    setNegativeButton("稍后") { _, _ -> }
                    setNeutralButton("跳过此版本") { _, _ ->
                        updateManager.skipVersion(versionInfo.versionCode)
                    }
                }
            }
            .show()
    }
    
    private fun showDownloadDialog(versionInfo: com.acuspic.app.update.VersionInfo) {
        val dialog = DownloadProgressDialog(this)
        dialog.setVersionInfo(versionInfo.versionName)
        dialog.show()
        
        // 开始下载
        updateManager.downloadAndInstall(versionInfo)
        
        // 监听下载状态
        lifecycleScope.launch {
            updateManager.downloadStatus.collect { status ->
                when (status) {
                    is com.acuspic.app.update.DownloadStatus.Progress -> {
                        dialog.updateProgress(status.progress, status.speed)
                    }
                    is com.acuspic.app.update.DownloadStatus.Success -> {
                        dialog.setComplete()
                        Toast.makeText(this@SettingsActivity, "下载完成，正在安装...", Toast.LENGTH_SHORT).show()
                    }
                    is com.acuspic.app.update.DownloadStatus.Error -> {
                        dialog.setError(status.message)
                    }
                    is com.acuspic.app.update.DownloadStatus.Cancelled -> {
                        dialog.dismiss()
                    }
                    else -> {}
                }
            }
        }
        
        dialog.setOnCancelListener {
            updateManager.cancelDownload()
        }
        
        dialog.setOnBackgroundListener {
            Toast.makeText(this@SettingsActivity, "正在后台下载，下载完成后将自动安装", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun showRepositorySelector() {
        val options = arrayOf("自动选择", "Gitee (国内)", "GitHub (国外)")
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
