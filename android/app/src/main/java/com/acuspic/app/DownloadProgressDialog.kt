package com.acuspic.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * 下载进度对话框
 * 现代化UI设计，支持后台下载和取消操作
 */
class DownloadProgressDialog(context: Context) : Dialog(context) {

    private lateinit var progressBar: LinearProgressIndicator
    private lateinit var tvProgress: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var tvVersionInfo: TextView
    private lateinit var btnCancel: MaterialButton
    private lateinit var btnBackground: MaterialButton

    private var isDownloading = true
    private var onBackgroundListener: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_download_progress)
        
        setCancelable(true)
        setCanceledOnTouchOutside(false)
        
        initViews()
    }
    
    private fun initViews() {
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        tvSpeed = findViewById(R.id.tvSpeed)
        tvStatus = findViewById(R.id.tvStatus)
        tvVersionInfo = findViewById(R.id.tvVersionInfo)
        btnCancel = findViewById(R.id.btnCancel)
        btnBackground = findViewById(R.id.btnBackground)
        
        btnCancel.setOnClickListener {
            cancel()
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
        tvVersionInfo.text = "Acuspic v$versionName"
    }
    
    /**
     * 更新进度
     * @param progress 进度百分比 (0-100)
     * @param speed 下载速度
     */
    fun updateProgress(progress: Int, speed: String) {
        progressBar.progress = progress
        tvProgress.text = "$progress%"
        tvSpeed.text = speed
        tvStatus.text = "正在下载..."
        isDownloading = true
        updateButtonState()
    }
    
    /**
     * 设置下载完成状态
     */
    fun setComplete() {
        progressBar.progress = 100
        tvProgress.text = "100%"
        tvStatus.text = "下载完成，正在安装..."
        tvSpeed.text = ""
        isDownloading = false
        updateButtonState()
    }
    
    /**
     * 设置下载失败状态
     */
    fun setError(message: String) {
        tvStatus.text = "下载失败: $message"
        tvSpeed.text = ""
        isDownloading = false
        updateButtonState()
    }
    
    /**
     * 设置后台下载监听器
     */
    fun setOnBackgroundListener(listener: () -> Unit) {
        onBackgroundListener = listener
    }
    
    private fun updateButtonState() {
        if (isDownloading) {
            btnCancel.text = "取消下载"
            btnBackground.visibility = View.VISIBLE
        } else {
            btnCancel.text = "关闭"
            btnBackground.visibility = View.GONE
        }
    }
}
