package com.acuspic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.acuspic.app.imagehost.ImageHostConfig
import com.acuspic.app.imagehost.ImageHostPreferences
import com.acuspic.app.imagehost.ImageHostType
import com.acuspic.app.imagehost.ImageHostUploader
import com.acuspic.app.imagehost.UploadResult
import kotlinx.coroutines.launch

/**
 * 图床设置页面
 */
class ImageHostSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: ImageHostPreferences
    private lateinit var uploader: ImageHostUploader

    // 视图组件
    private lateinit var switchEnable: Switch
    private lateinit var switchAutoUpload: Switch
    private lateinit var tvCurrentHost: TextView
    private lateinit var btnSelectHost: LinearLayout
    private lateinit var layoutApiKey: LinearLayout
    private lateinit var etApiKey: EditText
    private lateinit var layoutApiSecret: LinearLayout
    private lateinit var etApiSecret: EditText
    private lateinit var layoutCustomDomain: LinearLayout
    private lateinit var etCustomDomain: EditText
    private lateinit var layoutCustomUrl: LinearLayout
    private lateinit var etCustomUrl: EditText
    private lateinit var layoutRepo: LinearLayout
    private lateinit var etRepo: EditText
    private lateinit var layoutBranch: LinearLayout
    private lateinit var etBranch: EditText
    private lateinit var layoutBucket: LinearLayout
    private lateinit var etBucket: EditText
    private lateinit var btnTestUpload: MaterialButton
    private lateinit var btnViewHistory: MaterialButton
    private lateinit var btnSave: MaterialButton
    private lateinit var tvTestResult: TextView

    private var currentConfig = ImageHostConfig()

    // 图片选择器
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { testUpload(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_host_settings)

        prefs = ImageHostPreferences(this)
        uploader = ImageHostUploader(this)

        initViews()
        loadConfig()
        setupListeners()
    }

    private fun initViews() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        switchEnable = findViewById(R.id.switchEnable)
        switchAutoUpload = findViewById(R.id.switchAutoUpload)
        tvCurrentHost = findViewById(R.id.tvCurrentHost)
        btnSelectHost = findViewById(R.id.btnSelectHost)

        layoutApiKey = findViewById(R.id.layoutApiKey)
        etApiKey = findViewById(R.id.etApiKey)
        layoutApiSecret = findViewById(R.id.layoutApiSecret)
        etApiSecret = findViewById(R.id.etApiSecret)
        layoutCustomDomain = findViewById(R.id.layoutCustomDomain)
        etCustomDomain = findViewById(R.id.etCustomDomain)
        layoutCustomUrl = findViewById(R.id.layoutCustomUrl)
        etCustomUrl = findViewById(R.id.etCustomUrl)
        layoutRepo = findViewById(R.id.layoutGitHubRepo)
        etRepo = findViewById(R.id.etGitHubRepo)
        layoutBranch = findViewById(R.id.layoutGitHubBranch)
        etBranch = findViewById(R.id.etGitHubBranch)

        btnTestUpload = findViewById(R.id.btnTestUpload)
        btnViewHistory = findViewById(R.id.btnViewHistory)
        btnSave = findViewById(R.id.btnSave)
        tvTestResult = findViewById(R.id.tvTestResult)
    }

    private fun loadConfig() {
        currentConfig = prefs.getConfig()

        switchEnable.isChecked = currentConfig.isEnabled
        switchAutoUpload.isChecked = currentConfig.autoUpload
        tvCurrentHost.text = currentConfig.type.displayName

        etApiKey.setText(currentConfig.apiKey)
        etApiSecret.setText(currentConfig.apiSecret)
        etCustomDomain.setText(currentConfig.customDomain)
        etCustomUrl.setText(currentConfig.customUploadUrl)
        // 七牛云使用 bucket 字段，其他使用 repo 字段
        etRepo.setText(if (currentConfig.type == ImageHostType.QINIU) currentConfig.bucket else currentConfig.repo)
        etBranch.setText(currentConfig.branch)

        updateUIVisibility()
    }

    private fun setupListeners() {
        // 选择图床类型
        btnSelectHost.setOnClickListener {
            showHostSelector()
        }

        // 测试上传
        btnTestUpload.setOnClickListener {
            if (!validateConfig()) {
                Toast.makeText(this, "请先完成配置", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            imagePickerLauncher.launch("image/*")
        }

        // 查看上传历史
        btnViewHistory.setOnClickListener {
            startActivity(Intent(this, ImageHostHistoryActivity::class.java))
        }

        // 保存配置
        btnSave.setOnClickListener {
            saveConfig()
        }

        // 开关监听
        switchEnable.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(isEnabled = isChecked)
        }

        switchAutoUpload.setOnCheckedChangeListener { _, isChecked ->
            currentConfig = currentConfig.copy(autoUpload = isChecked)
        }
    }

    private fun showHostSelector() {
        val hosts = ImageHostType.values().filter { it != ImageHostType.NONE }
        val options = hosts.map { it.displayName }.toTypedArray()
        val currentIndex = hosts.indexOf(currentConfig.type)

        MaterialAlertDialogBuilder(this)
            .setTitle("选择图床")
            .setSingleChoiceItems(options, currentIndex) { dialog, which ->
                currentConfig = currentConfig.copy(type = hosts[which])
                tvCurrentHost.text = hosts[which].displayName
                updateUIVisibility()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun updateUIVisibility() {
        val type = currentConfig.type

        // API Token 字段
        layoutApiKey.visibility = if (type.needApiKey) View.VISIBLE else View.GONE
        // 根据图床类型更新标签和提示文字
        when (type) {
            ImageHostType.GITEE -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "Gitee 私人令牌 (Personal Access Token)"
                etApiKey.hint = "gitee.com → 设置 → 私人令牌 → 生成新令牌"
            }
            ImageHostType.QINIU -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "七牛云 AccessKey"
                etApiKey.hint = "portal.qiniu.com → 密钥管理 → 创建密钥"
            }
            ImageHostType.SMMS -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "SM.MS API Token"
                etApiKey.hint = "sm.ms → User → Dashboard → API Token"
            }
            ImageHostType.IMGUR -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "Imgur Client ID"
                etApiKey.hint = "api.imgur.com/oauth2/addclient → 注册应用获取"
            }
            ImageHostType.GITHUB -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "GitHub Personal Access Token"
                etApiKey.hint = "github.com → Settings → Developer settings → Tokens"
            }
            ImageHostType.CUSTOM -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "API Key / Token"
                etApiKey.hint = "请输入自定义图床的认证密钥"
            }
            else -> {
                (layoutApiKey.getChildAt(0) as TextView).text = "API Token"
                etApiKey.hint = "请输入 API Token"
            }
        }

        // API Secret 字段
        layoutApiSecret.visibility = if (type.needSecret) View.VISIBLE else View.GONE
        if (type == ImageHostType.QINIU) {
            (layoutApiSecret.getChildAt(0) as TextView).text = "七牛云 SecretKey"
            etApiSecret.hint = "与 AccessKey 在同一页面获取，注意保密"
        }

        // 存储空间/仓库字段
        layoutRepo.visibility = if (type.needRepo || type.needBucket) View.VISIBLE else View.GONE
        when {
            type == ImageHostType.GITEE -> {
                (layoutRepo.getChildAt(0) as TextView).text = "Gitee 仓库地址"
                etRepo.hint = "格式：用户名/仓库名（需预先创建公开仓库）"
            }
            type == ImageHostType.GITHUB -> {
                (layoutRepo.getChildAt(0) as TextView).text = "GitHub 仓库地址"
                etRepo.hint = "格式：用户名/仓库名（需预先创建公开仓库）"
            }
            type == ImageHostType.QINIU -> {
                (layoutRepo.getChildAt(0) as TextView).text = "七牛云存储空间名称"
                etRepo.hint = "portal.qiniu.com → 空间管理 → 创建空间"
            }
            else -> {
                (layoutRepo.getChildAt(0) as TextView).text = "仓库/空间"
                etRepo.hint = "请输入仓库或空间名称"
            }
        }

        // 分支字段（仅 Gitee/GitHub）
        layoutBranch.visibility = if (type.needRepo) View.VISIBLE else View.GONE
        if (type.needRepo) {
            (layoutBranch.getChildAt(0) as TextView).text = "分支名称"
            etBranch.hint = "默认：main（请确保分支存在）"
        }

        // 自定义域名
        layoutCustomDomain.visibility = if (type.needCustomDomain) View.VISIBLE else View.GONE
        if (type == ImageHostType.QINIU) {
            (layoutCustomDomain.getChildAt(0) as TextView).text = "七牛云自定义域名（可选）"
            etCustomDomain.hint = "配置 CDN 加速域名，如：https://img.yourdomain.com"
        }

        // 自定义上传地址
        layoutCustomUrl.visibility = if (type == ImageHostType.CUSTOM) View.VISIBLE else View.GONE
        if (type == ImageHostType.CUSTOM) {
            (layoutCustomUrl.getChildAt(0) as TextView).text = "自定义上传接口地址"
            etCustomUrl.hint = "例如：https://your-api.com/upload（需支持 POST + multipart/form-data）"
        }

        // 测试按钮
        btnTestUpload.isEnabled = type != ImageHostType.NONE
    }

    private fun validateConfig(): Boolean {
        if (currentConfig.type == ImageHostType.NONE) return false
        if (!currentConfig.type.needApiKey) return true

        val apiKey = etApiKey.text.toString().trim()
        if (apiKey.isEmpty()) return false

        if (currentConfig.type.needSecret) {
            val apiSecret = etApiSecret.text.toString().trim()
            if (apiSecret.isEmpty()) return false
        }

        if (currentConfig.type.needRepo || currentConfig.type.needBucket) {
            val repo = etRepo.text.toString().trim()
            if (repo.isEmpty()) return false
        }

        if (currentConfig.type == ImageHostType.CUSTOM) {
            val url = etCustomUrl.text.toString().trim()
            if (url.isEmpty()) return false
        }

        return true
    }

    private fun testUpload(uri: Uri) {
        tvTestResult.visibility = View.VISIBLE
        tvTestResult.text = "正在测试上传..."
        btnTestUpload.isEnabled = false

        // 更新配置
        val repoValue = etRepo.text.toString().trim()
        val testConfig = currentConfig.copy(
            apiKey = etApiKey.text.toString().trim(),
            apiSecret = etApiSecret.text.toString().trim(),
            customDomain = etCustomDomain.text.toString().trim(),
            customUploadUrl = etCustomUrl.text.toString().trim(),
            repo = if (currentConfig.type == ImageHostType.QINIU) "" else repoValue,
            bucket = if (currentConfig.type == ImageHostType.QINIU) repoValue else "",
            branch = etBranch.text.toString().trim()
        )

        lifecycleScope.launch {
            uploader.uploadImage(uri, testConfig).collect { result ->
                when (result) {
                    is UploadResult.Success -> {
                        tvTestResult.text = "✅ 测试成功!\n${result.url}"
                        tvTestResult.setTextColor(getColor(R.color.success))
                    }
                    is UploadResult.Error -> {
                        tvTestResult.text = "❌ 测试失败\n${result.message}"
                        tvTestResult.setTextColor(getColor(R.color.error))
                    }
                    else -> {}
                }
                btnTestUpload.isEnabled = true
            }
        }
    }

    private fun saveConfig() {
        val repoValue = etRepo.text.toString().trim()
        currentConfig = currentConfig.copy(
            apiKey = etApiKey.text.toString().trim(),
            apiSecret = etApiSecret.text.toString().trim(),
            customDomain = etCustomDomain.text.toString().trim(),
            customUploadUrl = etCustomUrl.text.toString().trim(),
            repo = if (currentConfig.type == ImageHostType.QINIU) "" else repoValue,
            bucket = if (currentConfig.type == ImageHostType.QINIU) repoValue else "",
            branch = etBranch.text.toString().trim().ifEmpty { "main" }
        )

        prefs.saveConfig(currentConfig)
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }
}
