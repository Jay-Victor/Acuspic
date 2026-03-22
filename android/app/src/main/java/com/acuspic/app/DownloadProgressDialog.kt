package com.acuspic.app

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * 下载进度对话框
 * 现代化UI设计，支持后台下载和取消操作
 * 符合Material Design 3设计规范
 */
class DownloadProgressDialog(context: Context) : Dialog(context) {

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvProgress: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvVersionInfo: TextView
    private lateinit var tvSize: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnBackground: MaterialButton
    private lateinit var statusIndicatorContainer: MaterialCardView
    private lateinit var iconUpdate: ImageView

    private var isDownloading = true
    private var onCancelListener: (() -> Unit)? = null
    private var onBackgroundListener: (() -> Unit)? = null
    private var pendingVersionName: String? = null
    private var totalBytes: Long = 0
    private var statusAnimator: ObjectAnimator? = null
    private var iconAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_download_progress)
        
        setCancelable(true)
        setCanceledOnTouchOutside(false)
        
        initViews()
        
        // 如果有待设置的版本名称，现在设置
        pendingVersionName?.let {
            tvVersionInfo.text = "Acuspic v$it"
            pendingVersionName = null
        }
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvStatus = findViewById(R.id.tvStatus)
        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        tvSize = findViewById(R.id.tvSize)
        btnCancel = findViewById(R.id.btnCancel)
        btnBackground = findViewById(R.id.btnBackground)
        statusIndicatorContainer = findViewById(R.id.statusIndicatorContainer)
        iconUpdate = findViewById(R.id.iconUpdate)
        
        btnCancel.setOnClickListener {
            onCancelListener?.invoke()
            dismiss()
        }
        
        btnBackground.setOnClickListener {
            onBackgroundListener?.invoke()
            dismiss()
        }
    }
    
    /**
     * 设置版本信息
     */
    fun setVersionInfo(versionName: String) {
        if (::tvVersionInfo.isInitialized) {
            tvVersionInfo.text = "Acuspic v$versionName"
        } else {
            pendingVersionName = versionName
        }
    }
    
    /**
     * 设置总文件大小
     */
    fun setTotalSize(bytes: Long) {
        totalBytes = bytes
        updateSizeText(0)
    }
    
    /**
     * 更新进度
     * @param progress 进度百分比 (0-100)
     * @param speed 下载速度
     * @param downloadedBytes 已下载字节数
     */
    fun updateProgress(progress: Int, speed: String, downloadedBytes: Long = 0) {
        if (!::progressBar.isInitialized) return
        
        progressBar.isIndeterminate = false
        progressBar.progress = progress
        tvProgress.text = "$progress%"
        tvSpeed.text = speed
        tvStatus.text = context.getString(R.string.status_downloading)
        
        // 更新状态指示器颜色为蓝色（下载中）
        statusIndicatorContainer.setCardBackgroundColor(context.getColor(R.color.accent_primary))
        
        if (downloadedBytes > 0 || totalBytes > 0) {
            updateSizeText(downloadedBytes)
        }
        
        isDownloading = true
        updateButtonState()
        startStatusAnimation()
        startIconRotation()
    }
    
    /**
     * 设置连接状态
     */
    fun setConnecting() {
        if (!::progressBar.isInitialized) return
        
        progressBar.isIndeterminate = true
        tvProgress.text = "..."
        tvStatus.text = context.getString(R.string.status_connecting)
        tvSpeed.text = ""
        tvSize.text = ""
        
        // 状态指示器变为黄色（连接中）
        statusIndicatorContainer.setCardBackgroundColor(context.getColor(R.color.warning))
        
        isDownloading = true
        updateButtonState()
        startStatusAnimation()
        startIconRotation()
    }
    
    /**
     * 设置下载完成状态
     */
    fun setComplete() {
        if (!::progressBar.isInitialized) return
        
        progressBar.isIndeterminate = false
        progressBar.progress = 100
        tvProgress.text = "100%"
        tvProgress.setTextColor(context.getColor(R.color.success))
        tvStatus.text = context.getString(R.string.status_complete)
        tvSpeed.text = ""
        
        // 状态指示器变为绿色（成功）
        statusIndicatorContainer.setCardBackgroundColor(context.getColor(R.color.success))
        
        if (totalBytes > 0) {
            tvSize.text = "${formatFileSize(totalBytes)} / ${formatFileSize(totalBytes)}"
        }
        
        isDownloading = false
        updateButtonState()
        stopStatusAnimation()
        stopIconRotation()
    }
    
    /**
     * 设置下载失败状态
     */
    fun setError(message: String) {
        if (!::progressBar.isInitialized) return
        
        progressBar.isIndeterminate = false
        tvStatus.text = "${context.getString(R.string.status_error)}: $message"
        tvStatus.setTextColor(context.getColor(R.color.error))
        tvSpeed.text = ""
        
        // 状态指示器变为红色（错误）
        statusIndicatorContainer.setCardBackgroundColor(context.getColor(R.color.error))
        
        isDownloading = false
        updateButtonState()
        stopStatusAnimation()
        stopIconRotation()
    }
    
    /**
     * 设置取消监听器
     */
    fun setOnCancelListener(listener: () -> Unit) {
        onCancelListener = listener
    }
    
    /**
     * 设置后台下载监听器
     */
    fun setOnBackgroundListener(listener: () -> Unit) {
        onBackgroundListener = listener
    }
    
    private fun updateButtonState() {
        if (!::btnCancel.isInitialized) return
        
        if (isDownloading) {
            btnCancel.text = context.getString(R.string.cancel)
            btnBackground.visibility = View.VISIBLE
        } else {
            btnCancel.text = context.getString(R.string.close)
            btnBackground.visibility = View.GONE
        }
    }
    
    private fun updateSizeText(downloadedBytes: Long) {
        if (totalBytes > 0) {
            tvSize.text = "${formatFileSize(downloadedBytes)} / ${formatFileSize(totalBytes)}"
        } else if (downloadedBytes > 0) {
            tvSize.text = "已下载 ${formatFileSize(downloadedBytes)}"
        }
    }
    
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
            else -> String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024))
        }
    }
    
    /**
     * 开始状态指示器脉冲动画
     */
    private fun startStatusAnimation() {
        if (statusAnimator?.isRunning == true) return
        
        statusAnimator = ObjectAnimator.ofFloat(statusIndicatorContainer, "alpha", 1f, 0.4f, 1f).apply {
            duration = 1200
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    
    /**
     * 停止状态指示器动画
     */
    private fun stopStatusAnimation() {
        statusAnimator?.cancel()
        statusIndicatorContainer.alpha = 1f
    }
    
    /**
     * 开始图标旋转动画
     */
    private fun startIconRotation() {
        if (iconAnimator?.isRunning == true) return
        
        iconAnimator = ObjectAnimator.ofFloat(iconUpdate, "rotation", 0f, 360f).apply {
            duration = 2000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            start()
        }
    }
    
    /**
     * 停止图标旋转动画
     */
    private fun stopIconRotation() {
        iconAnimator?.cancel()
        iconUpdate.rotation = 0f
    }
    
    override fun dismiss() {
        stopStatusAnimation()
        stopIconRotation()
        super.dismiss()
    }
}
