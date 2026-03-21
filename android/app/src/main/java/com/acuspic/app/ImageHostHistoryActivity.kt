package com.acuspic.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.acuspic.app.imagehost.ImageHostPreferences
import com.acuspic.app.imagehost.UploadHistory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 图床上传历史页面
 */
class ImageHostHistoryActivity : AppCompatActivity() {

    private lateinit var prefs: ImageHostPreferences
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var btnClear: MaterialButton
    private lateinit var adapter: UploadHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_host_history)

        prefs = ImageHostPreferences(this)

        initViews()
        loadHistory()
    }

    private fun initViews() {
        // 返回按钮
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        recyclerView = findViewById(R.id.recyclerView)
        tvEmpty = findViewById(R.id.tvEmpty)
        btnClear = findViewById(R.id.btnClear)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UploadHistoryAdapter(
            onCopyClick = { copyUrl(it.url) },
            onDeleteClick = { showDeleteConfirm(it) }
        )
        recyclerView.adapter = adapter

        btnClear.setOnClickListener {
            showClearConfirm()
        }
    }

    private fun loadHistory() {
        val history = prefs.getUploadHistory()
        adapter.submitList(history)

        if (history.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
            btnClear.visibility = View.GONE
        } else {
            tvEmpty.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            btnClear.visibility = View.VISIBLE
        }
    }

    private fun copyUrl(url: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("图片链接", url)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "链接已复制", Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteConfirm(history: UploadHistory) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除记录")
            .setMessage("确定要删除这条上传记录吗？")
            .setPositiveButton("删除") { _, _ ->
                prefs.removeUploadHistory(history.id)
                loadHistory()
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearConfirm() {
        MaterialAlertDialogBuilder(this)
            .setTitle("清空历史")
            .setMessage("确定要清空所有上传历史吗？此操作不可恢复。")
            .setPositiveButton("清空") { _, _ ->
                prefs.clearUploadHistory()
                loadHistory()
                Toast.makeText(this, "历史已清空", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 上传历史适配器
     */
    inner class UploadHistoryAdapter(
        private val onCopyClick: (UploadHistory) -> Unit,
        private val onDeleteClick: (UploadHistory) -> Unit
    ) : RecyclerView.Adapter<UploadHistoryAdapter.ViewHolder>() {

        private var items: List<UploadHistory> = emptyList()

        fun submitList(newItems: List<UploadHistory>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_upload_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(items[position])
        }

        override fun getItemCount(): Int = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val ivPreview: ImageView = itemView.findViewById(R.id.ivPreview)
            private val tvFileName: TextView = itemView.findViewById(R.id.tvFileName)
            private val tvHostType: TextView = itemView.findViewById(R.id.tvHostType)
            private val tvUploadTime: TextView = itemView.findViewById(R.id.tvUploadTime)
            private val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
            private val btnCopy: ImageButton = itemView.findViewById(R.id.btnCopy)
            private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

            private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

            fun bind(history: UploadHistory) {
                tvFileName.text = history.originalName
                tvHostType.text = history.hostType.displayName
                tvUploadTime.text = dateFormat.format(Date(history.uploadTime))
                tvFileSize.text = formatFileSize(history.fileSize)

                // 加载图片预览
                Glide.with(itemView.context)
                    .load(history.url)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .centerCrop()
                    .into(ivPreview)

                btnCopy.setOnClickListener { onCopyClick(history) }
                btnDelete.setOnClickListener { onDeleteClick(history) }

                itemView.setOnClickListener {
                    // 显示图片详情对话框
                    showImageDetail(history)
                }
            }

            private fun formatFileSize(size: Long): String {
                return when {
                    size < 1024 -> "$size B"
                    size < 1024 * 1024 -> "${size / 1024} KB"
                    else -> "${size / (1024 * 1024)} MB"
                }
            }

            private fun showImageDetail(history: UploadHistory) {
                val dialogView = LayoutInflater.from(itemView.context)
                    .inflate(R.layout.dialog_image_detail, null)

                val ivDetail: ImageView = dialogView.findViewById(R.id.ivDetail)
                val tvDetailUrl: TextView = dialogView.findViewById(R.id.tvDetailUrl)

                Glide.with(itemView.context)
                    .load(history.url)
                    .into(ivDetail)

                tvDetailUrl.text = history.url

                MaterialAlertDialogBuilder(itemView.context)
                    .setView(dialogView)
                    .setPositiveButton("复制链接") { _, _ ->
                        onCopyClick(history)
                    }
                    .setNegativeButton("关闭", null)
                    .show()
            }
        }
    }
}
