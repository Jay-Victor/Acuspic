package com.acuspic.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.TextView
import com.acuspic.app.update.VersionInfo
import com.google.android.material.button.MaterialButton

/**
 * 更新信息对话框
 * 使用自定义布局展示新版本信息
 */
class UpdateInfoDialog(context: Context) : Dialog(context) {

    private lateinit var tvVersion: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvSize: TextView
    private lateinit var tvNotes: TextView
    private lateinit var tvBadge: TextView
    private lateinit var badgeContainer: com.google.android.material.card.MaterialCardView
    private lateinit var btnSkip: MaterialButton
    private lateinit var btnLater: MaterialButton
    private lateinit var btnDownload: MaterialButton

    private var versionInfo: VersionInfo? = null
    private var isForceUpdate = false
    private var onSkipListener: (() -> Unit)? = null
    private var onLaterListener: (() -> Unit)? = null
    private var onDownloadListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_update_info)

        initViews()
        setupListeners()
        bindData()
    }

    private fun initViews() {
        tvVersion = findViewById(R.id.tvVersion)
        tvDate = findViewById(R.id.tvDate)
        tvSize = findViewById(R.id.tvSize)
        tvNotes = findViewById(R.id.tvNotes)
        tvBadge = findViewById(R.id.tvBadge)
        badgeContainer = findViewById(R.id.badgeContainer)
        btnSkip = findViewById(R.id.btnSkip)
        btnLater = findViewById(R.id.btnLater)
        btnDownload = findViewById(R.id.btnDownload)
    }

    private fun setupListeners() {
        btnSkip.setOnClickListener {
            onSkipListener?.invoke()
            dismiss()
        }

        btnLater.setOnClickListener {
            onLaterListener?.invoke()
            dismiss()
        }

        btnDownload.setOnClickListener {
            onDownloadListener?.invoke()
            dismiss()
        }
    }

    private fun bindData() {
        versionInfo?.let { info ->
            tvVersion.text = "v${info.versionName}"
            tvDate.text = info.publishDate.ifEmpty { "未知" }
            tvSize.text = if (info.fileSize > 0) {
                formatFileSize(info.fileSize)
            } else {
                "未知"
            }
            tvNotes.text = info.releaseNotes.ifEmpty { "暂无更新说明" }

            // 显示重要更新标签
            if (isForceUpdate || info.isImportant) {
                badgeContainer.visibility = android.view.View.VISIBLE
                tvBadge.text = if (isForceUpdate) "强制更新" else "重要更新"
            } else {
                badgeContainer.visibility = android.view.View.GONE
            }

            // 强制更新时隐藏跳过和稍后按钮
            if (isForceUpdate) {
                btnSkip.visibility = android.view.View.GONE
                btnLater.visibility = android.view.View.GONE
                setCancelable(false)
            } else {
                btnSkip.visibility = android.view.View.VISIBLE
                btnLater.visibility = android.view.View.VISIBLE
                setCancelable(true)
            }
        }
    }

    fun setVersionInfo(info: VersionInfo, force: Boolean = false) {
        versionInfo = info
        isForceUpdate = force
        if (::tvVersion.isInitialized) {
            bindData()
        }
    }

    fun setOnSkipListener(listener: () -> Unit) {
        onSkipListener = listener
    }

    fun setOnLaterListener(listener: () -> Unit) {
        onLaterListener = listener
    }

    fun setOnDownloadListener(listener: () -> Unit) {
        onDownloadListener = listener
    }

    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
}
