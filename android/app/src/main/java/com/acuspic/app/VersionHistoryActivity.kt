package com.acuspic.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.acuspic.app.update.UpdateManager
import com.acuspic.app.update.VersionHistory
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.io.File

class VersionHistoryActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyView: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var btnBack: ImageButton
    private lateinit var btnClearAll: ImageButton
    private lateinit var storageSummary: LinearLayout
    private lateinit var tvDownloadedCount: TextView
    private lateinit var tvTotalSize: TextView
    private lateinit var batchActionBar: LinearLayout
    private lateinit var checkboxSelectAll: CheckBox
    private lateinit var tvSelectedCount: TextView
    private lateinit var btnDeleteSelected: MaterialButton

    private lateinit var updateManager: UpdateManager
    private lateinit var adapter: VersionHistoryAdapter
    private val selectedVersions = mutableSetOf<VersionHistory>()

    private val installPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        handleInstallPermissionResult()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_version_history)

        updateManager = UpdateManager(this)

        initViews()
        setupListeners()
        loadVersionHistory()
    }

    override fun onResume() {
        super.onResume()
        checkPendingInstall()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyView = findViewById(R.id.emptyView)
        toolbar = findViewById(R.id.toolbar)
        btnBack = findViewById(R.id.btnBack)
        btnClearAll = findViewById(R.id.btnClearAll)
        storageSummary = findViewById(R.id.storageSummary)
        tvDownloadedCount = findViewById(R.id.tvDownloadedCount)
        tvTotalSize = findViewById(R.id.tvTotalSize)
        batchActionBar = findViewById(R.id.batchActionBar)
        checkboxSelectAll = findViewById(R.id.checkboxSelectAll)
        tvSelectedCount = findViewById(R.id.tvSelectedCount)
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected)

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }

        btnClearAll.setOnClickListener {
            showClearAllConfirm()
        }

        checkboxSelectAll.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                selectAll()
            } else {
                deselectAll()
            }
        }

        btnDeleteSelected.setOnClickListener {
            deleteSelected()
        }
    }

    private fun loadVersionHistory() {
        val versions = updateManager.getVersionHistory()

        if (versions.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            storageSummary.visibility = View.GONE
            btnClearAll.visibility = View.GONE
            batchActionBar.visibility = View.GONE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE
            storageSummary.visibility = View.VISIBLE
            btnClearAll.visibility = View.VISIBLE

            adapter = VersionHistoryAdapter(versions.toMutableList()) { version, isSelected ->
                if (isSelected) {
                    selectedVersions.add(version)
                } else {
                    selectedVersions.remove(version)
                }
                updateBatchActionBar()
            }

            recyclerView.adapter = adapter
            updateStorageSummary()
        }
    }

    private fun updateStorageSummary() {
        val downloadedVersions = updateManager.getDownloadedVersions()
        val totalSize = downloadedVersions.sumOf { getFileSize(it) }

        tvDownloadedCount.text = "已下载 ${downloadedVersions.size} 个版本"
        tvTotalSize.text = "共 ${formatFileSize(totalSize)}"
    }

    private fun getFileSize(version: VersionHistory): Long {
        val localPath = version.localPath
        return if (localPath != null && localPath.isNotEmpty()) {
            val file = File(localPath)
            if (file.exists()) file.length() else 0
        } else {
            0
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

    private fun updateBatchActionBar() {
        val count = selectedVersions.size
        if (count > 0) {
            batchActionBar.visibility = View.VISIBLE
            tvSelectedCount.text = "已选择 $count 项"
        } else {
            batchActionBar.visibility = View.GONE
        }

        val totalCount = adapter.itemCount
        checkboxSelectAll.isChecked = count == totalCount && count > 0
    }

    private fun selectAll() {
        selectedVersions.clear()
        selectedVersions.addAll(adapter.getAllVersions())
        adapter.setAllSelected(true)
        updateBatchActionBar()
    }

    private fun deselectAll() {
        selectedVersions.clear()
        adapter.setAllSelected(false)
        updateBatchActionBar()
    }

    private fun deleteSelected() {
        if (selectedVersions.isEmpty()) {
            Toast.makeText(this, "请先选择要删除的版本", Toast.LENGTH_SHORT).show()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("删除选中版本")
            .setMessage("确定要删除选中的 ${selectedVersions.size} 个版本吗？")
            .setPositiveButton("删除") { _, _ ->
                val count = selectedVersions.size
                selectedVersions.forEach { version ->
                    updateManager.deleteVersion(version)
                }
                selectedVersions.clear()
                loadVersionHistory()
                Toast.makeText(this, "已删除 $count 个版本", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showClearAllConfirm() {
        val downloadedVersions = updateManager.getDownloadedVersions()

        if (downloadedVersions.isEmpty()) {
            Toast.makeText(this, "没有下载的文件", Toast.LENGTH_SHORT).show()
            return
        }

        val totalSize = downloadedVersions.sumOf { getFileSize(it) }

        MaterialAlertDialogBuilder(this)
            .setTitle("清空全部下载")
            .setMessage("确定要清除所有下载的更新文件吗？\n\n共 ${downloadedVersions.size} 个版本\n占用 ${formatFileSize(totalSize)}")
            .setPositiveButton("清空") { _, _ ->
                updateManager.clearAllDownloads()
                selectedVersions.clear()
                loadVersionHistory()
                Toast.makeText(this, "已清除所有下载文件", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun tryInstallApk(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                requestInstallPermission(file)
                return
            }
        }
        
        MaterialAlertDialogBuilder(this)
            .setTitle("安装版本")
            .setMessage("是否安装此版本？")
            .setPositiveButton("安装") { _, _ ->
                updateManager.installApk(file)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun requestInstallPermission(file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
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

    inner class VersionHistoryAdapter(
        private val versions: MutableList<VersionHistory>,
        private val onSelectionChanged: (VersionHistory, Boolean) -> Unit
    ) : RecyclerView.Adapter<VersionHistoryAdapter.ViewHolder>() {

        private val selectedItems = mutableSetOf<Int>()

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val checkbox: CheckBox = itemView.findViewById(R.id.checkbox)
            val tvVersionName: TextView = itemView.findViewById(R.id.tvVersionName)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            val tvPublishDate: TextView = itemView.findViewById(R.id.tvPublishDate)
            val tvFileSize: TextView = itemView.findViewById(R.id.tvFileSize)
            val btnDelete: MaterialButton = itemView.findViewById(R.id.btnDelete)
            val btnRollback: MaterialButton = itemView.findViewById(R.id.btnRollback)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
            val view = android.view.LayoutInflater.from(parent.context)
                .inflate(R.layout.item_version_history, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val version = versions[position]

            holder.tvVersionName.text = version.versionName
            holder.tvPublishDate.text = version.publishDate

            if (version.isDownloaded) {
                holder.tvStatus.text = "已下载"
                holder.tvStatus.setTextColor(getColor(R.color.success))
                holder.tvFileSize.visibility = View.VISIBLE
                holder.tvFileSize.text = formatFileSize(getFileSize(version))
                holder.btnRollback.visibility = View.VISIBLE
            } else {
                holder.tvStatus.text = "未下载"
                holder.tvStatus.setTextColor(getColor(R.color.dark_text_secondary))
                holder.tvFileSize.visibility = View.GONE
                holder.btnRollback.visibility = View.GONE
            }

            holder.checkbox.isChecked = selectedItems.contains(position)
            holder.checkbox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(position)
                } else {
                    selectedItems.remove(position)
                }
                onSelectionChanged(version, isChecked)
            }

            holder.btnDelete.setOnClickListener {
                showDeleteConfirm(version)
            }

            holder.btnRollback.setOnClickListener {
                val success = updateManager.rollbackToVersion(version.versionName)
                if (success) {
                    val file = updateManager.getPendingInstallFile()
                    if (file != null) {
                        tryInstallApk(file)
                    }
                }
            }
        }

        override fun getItemCount() = versions.size

        fun getAllVersions(): List<VersionHistory> = versions.toList()

        fun setAllSelected(selected: Boolean) {
            selectedItems.clear()
            if (selected) {
                for (i in 0 until versions.size) {
                    selectedItems.add(i)
                }
            }
            notifyDataSetChanged()
        }

        private fun showDeleteConfirm(version: VersionHistory) {
            val position = versions.indexOf(version)
            if (position == -1) return
            
            MaterialAlertDialogBuilder(this@VersionHistoryActivity)
                .setTitle("删除版本")
                .setMessage("确定要删除 v${version.versionName} 吗？")
                .setPositiveButton("删除") { _, _ ->
                    updateManager.deleteVersion(version)
                    
                    versions.removeAt(position)
                    selectedItems.remove(position)
                    selectedVersions.remove(version)
                    
                    val newSelectedItems = mutableSetOf<Int>()
                    selectedItems.forEach { idx ->
                        if (idx > position) {
                            newSelectedItems.add(idx - 1)
                        } else if (idx < position) {
                            newSelectedItems.add(idx)
                        }
                    }
                    selectedItems.clear()
                    selectedItems.addAll(newSelectedItems)
                    
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, versions.size)
                    
                    this@VersionHistoryActivity.updateStorageSummary()
                    this@VersionHistoryActivity.updateBatchActionBar()
                    
                    if (versions.isEmpty()) {
                        this@VersionHistoryActivity.loadVersionHistory()
                    }
                    
                    Toast.makeText(this@VersionHistoryActivity, "已删除 v${version.versionName}", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }
}
