package com.acuspic.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.acuspic.app.update.UpdateManager

/**
 * 关于页面
 */
class AboutActivity : AppCompatActivity() {

    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        updateManager = UpdateManager(this)

        initViews()
    }

    private fun initViews() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        // 版本信息
        val tvVersion = findViewById<TextView>(R.id.tvVersion)
        tvVersion.text = updateManager.getCurrentVersionInfo()

        // GitHub链接
        findViewById<LinearLayout>(R.id.btnGitHub).setOnClickListener {
            openUrl("https://github.com/Jay-Victor/Acuspic")
        }

        // Gitee链接
        findViewById<LinearLayout>(R.id.btnGitee).setOnClickListener {
            openUrl("https://gitee.com/Jay-Victor/Acuspic")
        }

        // 反馈建议
        findViewById<LinearLayout>(R.id.btnFeedback).setOnClickListener {
            sendFeedback()
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        startActivity(intent)
    }

    private fun sendFeedback() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("18261738221@163.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Acuspic 反馈建议")
            putExtra(Intent.EXTRA_TEXT, "\n\n版本: ${updateManager.getCurrentVersionInfo()}\nAndroid版本: ${android.os.Build.VERSION.RELEASE}")
        }
        startActivity(Intent.createChooser(intent, "发送反馈"))
    }
}
