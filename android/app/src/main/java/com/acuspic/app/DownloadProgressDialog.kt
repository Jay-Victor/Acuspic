package com.acuspic.app

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.widget.ProgressBar
import android.widget.TextView
import com.google.android.material.button.MaterialButton

/**
 * 下载进度对话框
 */
class DownloadProgressDialog(context: Context) : Dialog(context) {

    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var tvSpeed: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnCancel: MaterialButton

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
        btnCancel = findViewById(R.id.btnCancel)
        
        btnCancel.setOnClickListener {
            cancel()
        }
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
    }
    
    /**
     * 设置下载完成状态
     */
    fun setComplete() {
        progressBar.progress = 100
        tvProgress.text = "100%"
        tvStatus.text = "下载完成"
        tvSpeed.text = ""
        btnCancel.text = "关闭"
    }
    
    /**
     * 设置下载失败状态
     */
    fun setError(message: String) {
        tvStatus.text = "下载失败: $message"
        tvSpeed.text = ""
        btnCancel.text = "关闭"
    }
}
