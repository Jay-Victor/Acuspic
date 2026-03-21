package com.acuspic.app

import android.Manifest
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.acuspic.app.imagehost.ImageHostPreferences
import com.acuspic.app.imagehost.ImageHostUploader
import com.acuspic.app.imagehost.UploadHistory
import com.acuspic.app.imagehost.UploadResult
import com.acuspic.app.markdown.MarkdownFlavor
import kotlinx.coroutines.launch
import com.acuspic.app.markdown.MarkdownPreferences
import com.acuspic.app.markdown.MarkdownRenderer
import java.io.File

/**
 * MainActivity - Acuspic 主活动
 * 
 * 视觉状态规范：
 * - 编辑模式（深色主题）：
 *   - 编辑按钮：背景 accent_primary，文字白色（选中状态）
 *   - 预览按钮：背景透明，文字 dark_text_secondary（非激活状态）
 * - 预览模式（浅色主题）：
 *   - 预览按钮：背景 accent_primary，文字白色（选中状态）
 *   - 编辑按钮：背景透明，文字 light_text_secondary（非激活状态）
 * 
 * 状态持久化：使用 SharedPreferences 保存当前模式
 */
class MainActivity : AppCompatActivity() {

    private lateinit var rootContainer: View
    private lateinit var toolbar: View
    private lateinit var toolbarTitle: TextView
    private lateinit var wordCountText: TextView
    private lateinit var modeToggleContainer: FrameLayout
    private lateinit var modeToggleIndicator: View
    private lateinit var editModeBtn: TextView
    private lateinit var previewModeBtn: TextView
    private lateinit var editScrollView: NestedScrollView
    private lateinit var editArea: EditText
    private lateinit var previewScrollView: NestedScrollView
    private lateinit var previewArea: TextView
    private lateinit var statusBar: View
    private lateinit var statusText: TextView
    private lateinit var bottomToolbar: View
    private lateinit var formatToolsScroll: View
    private lateinit var formatTools: View
    private lateinit var btnClear: MaterialButton
    private lateinit var btnCopy: MaterialButton
    private lateinit var btnShare: MaterialButton
    private lateinit var btnImport: MaterialButton
    private lateinit var btnExport: MaterialButton
    private lateinit var btnSettings: ImageButton

    private lateinit var markdownRenderer: MarkdownRenderer
    private lateinit var markdownPreferences: MarkdownPreferences
    private lateinit var imageHostPreferences: ImageHostPreferences
    private lateinit var imageHostUploader: ImageHostUploader
    private lateinit var sharedPreferences: SharedPreferences
    private var currentMode = Mode.EDIT
    private val handler = Handler(Looper.getMainLooper())
    private var saveRunnable: Runnable? = null
    private var isDirty = false

    // 滚动位置记忆
    private var editScrollY = 0
    private var previewScrollY = 0

    // 撤销/重做历史 - 使用索引方式实现，同时记录光标/选区位置
    private data class HistoryItem(
        val text: String,
        val selectionStart: Int,
        val selectionEnd: Int
    ) {
        val cursorPosition: Int get() = if (selectionStart == selectionEnd) selectionStart else selectionEnd
        val hasSelection: Boolean get() = selectionStart != selectionEnd
    }
    private val historyList = mutableListOf<HistoryItem>()
    private var historyIndex = -1
    private var isUndoRedo = false
    private val maxHistorySize = 100

    private enum class Mode {
        EDIT, PREVIEW
    }

    companion object {
        private const val PREFS_NAME = "AcuspicPrefs"
        private const val KEY_CURRENT_MODE = "current_mode"
        private const val KEY_DRAFT_CONTENT = "draft_content"
        private const val AUTO_SAVE_DELAY = 2000L // 2秒自动保存
    }

    // Android 16+ 权限请求
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (!allGranted) {
            Toast.makeText(this, "部分功能需要存储权限才能正常使用", Toast.LENGTH_LONG).show()
        }
    }

    private val openDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { importMarkdown(it) }
    }

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        uri?.let { exportMarkdown(it) }
    }

    // 图片选择器（用于图床上传）
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { handleImageSelection(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化 SharedPreferences
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // 恢复上次保存的模式
        val savedMode = sharedPreferences.getString(KEY_CURRENT_MODE, Mode.EDIT.name)
        currentMode = Mode.valueOf(savedMode ?: Mode.EDIT.name)

        enableEdgeToEdge()
        initViews()
        initMarkwon()
        initImageHost()
        setupListeners()
        checkAndRequestPermissions()
        loadSampleContent()
        setupWindowInsets()
        setupPreviewGesture()
        setupBackPressHandler()

        // 应用保存的模式
        switchMode(currentMode, animate = false)
    }
    
    private fun setupBackPressHandler() {
        val backToast: Toast? = null
        var backPressedTime = 0L
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    backToast?.cancel()
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    Toast.makeText(this@MainActivity, "再按一次退出应用", Toast.LENGTH_SHORT).show()
                }
                backPressedTime = System.currentTimeMillis()
            }
        })
    }

    private fun initImageHost() {
        imageHostPreferences = ImageHostPreferences(this)
        imageHostUploader = ImageHostUploader(this)
    }

    override fun onPause() {
        super.onPause()
        // 保存当前模式
        sharedPreferences.edit().putString(KEY_CURRENT_MODE, currentMode.name).apply()
    }

    private fun enableEdgeToEdge() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val imeInsets = windowInsets.getInsets(WindowInsetsCompat.Type.ime())

            toolbar.updatePadding(top = insets.top)

            val bottomPadding = if (currentMode == Mode.EDIT) {
                insets.bottom + if (imeInsets.bottom > 0) imeInsets.bottom else 0
            } else {
                insets.bottom
            }
            bottomToolbar.updatePadding(bottom = bottomPadding)

            WindowInsetsCompat.CONSUMED
        }

        setupKeyboardAnimationCallback()
    }

    private fun setupKeyboardAnimationCallback() {
        val callback = object : WindowInsetsAnimationCompat.Callback(
            WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE
        ) {
            private var startScrollY: Int = 0
            private var endScrollY: Int = 0

            override fun onPrepare(animation: WindowInsetsAnimationCompat) {
                if (currentMode == Mode.EDIT) {
                    startScrollY = editScrollView.scrollY
                }
            }

            override fun onStart(
                animation: WindowInsetsAnimationCompat,
                bounds: WindowInsetsAnimationCompat.BoundsCompat
            ): WindowInsetsAnimationCompat.BoundsCompat {
                if (currentMode == Mode.EDIT) {
                    endScrollY = calculateTargetScrollY()
                }
                return bounds
            }

            override fun onProgress(
                insets: WindowInsetsCompat,
                runningAnimations: MutableList<WindowInsetsAnimationCompat>
            ): WindowInsetsCompat {
                if (currentMode != Mode.EDIT) return insets

                val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())

                if (imeInsets.bottom > 0) {
                    bottomToolbar.updatePadding(bottom = imeInsets.bottom)

                    val fraction = calculateAnimationFraction(runningAnimations)
                    val currentScrollY = startScrollY + ((endScrollY - startScrollY) * fraction).toInt()
                    editScrollView.scrollTo(0, currentScrollY)
                }

                return insets
            }

            override fun onEnd(animation: WindowInsetsAnimationCompat) {
                if (currentMode == Mode.EDIT) {
                    val imeInsets = ViewCompat.getRootWindowInsets(rootContainer)
                        ?.getInsets(WindowInsetsCompat.Type.ime())
                    if (imeInsets != null && imeInsets.bottom > 0) {
                        val finalScrollY = calculateTargetScrollY()
                        editScrollView.scrollTo(0, finalScrollY)
                    }
                }
            }
        }

        ViewCompat.setWindowInsetsAnimationCallback(rootContainer, callback)
    }

    private fun calculateAnimationFraction(animations: MutableList<WindowInsetsAnimationCompat>): Float {
        if (animations.isEmpty()) return 1f
        val animation = animations.first()
        return animation.interpolatedFraction.coerceIn(0f, 1f)
    }

    private fun calculateTargetScrollY(): Int {
        if (editArea.layout == null) return editScrollView.scrollY

        val selectionStart = editArea.selectionStart
        if (selectionStart < 0) return editScrollView.scrollY

        val layout = editArea.layout ?: return editScrollView.scrollY

        val line = layout.getLineForOffset(selectionStart)
        val lineBottom = layout.getLineBottom(line)
        val lineDescent = layout.getLineDescent(line)

        val toolbarLocation = IntArray(2)
        bottomToolbar.getLocationOnScreen(toolbarLocation)
        val toolbarTop = toolbarLocation[1]

        val scrollViewLocation = IntArray(2)
        editScrollView.getLocationOnScreen(scrollViewLocation)

        val editLocation = IntArray(2)
        editArea.getLocationOnScreen(editLocation)

        val cursorBottom = lineBottom + lineDescent
        val cursorLineBottomInScrollView = cursorBottom + editLocation[1] - scrollViewLocation[1]
        val toolbarTopInScrollView = toolbarTop - scrollViewLocation[1]

        val margin = (8 * resources.displayMetrics.density).toInt()
        val scrollDelta = cursorLineBottomInScrollView - toolbarTopInScrollView + margin
        var newScrollY = editScrollView.scrollY + scrollDelta

        val contentHeight = editArea.height + editArea.paddingTop + editArea.paddingBottom
        val scrollViewHeight = editScrollView.height - editScrollView.paddingTop - editScrollView.paddingBottom
        val maxScrollY = maxOf(0, contentHeight - scrollViewHeight)

        return newScrollY.coerceIn(0, maxScrollY)
    }

    private fun scrollToCursorPosition() {
        val targetY = calculateTargetScrollY()
        editScrollView.smoothScrollTo(0, targetY)
    }

    private fun getKeyboardHeight(): Int {
        val rect = android.graphics.Rect()
        rootContainer.getWindowVisibleDisplayFrame(rect)
        val screenHeight = rootContainer.rootView.height
        return screenHeight - rect.bottom
    }

    private fun initViews() {
        rootContainer = findViewById(R.id.rootContainer)
        toolbar = findViewById(R.id.toolbar)
        toolbarTitle = findViewById(R.id.toolbarTitle)
        wordCountText = findViewById(R.id.wordCountText)
        modeToggleContainer = findViewById(R.id.modeToggleContainer)
        modeToggleIndicator = findViewById(R.id.modeToggleIndicator)
        editModeBtn = findViewById(R.id.editModeBtn)
        previewModeBtn = findViewById(R.id.previewModeBtn)
        editScrollView = findViewById(R.id.editScrollView)
        editArea = findViewById(R.id.editArea)
        previewScrollView = findViewById(R.id.previewScrollView)
        previewArea = findViewById(R.id.previewArea)
        statusBar = findViewById(R.id.statusBar)
        statusText = findViewById(R.id.statusText)
        bottomToolbar = findViewById(R.id.bottomToolbar)
        formatToolsScroll = findViewById(R.id.formatToolsScroll)
        formatTools = findViewById(R.id.formatTools)
        btnClear = findViewById(R.id.btnClear)
        btnCopy = findViewById(R.id.btnCopy)
        btnShare = findViewById(R.id.btnShare)
        btnImport = findViewById(R.id.btnImport)
        btnExport = findViewById(R.id.btnExport)
        
        // 设置按钮
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        
        // 初始化字数统计
        updateWordCount()
    }

    private fun initMarkwon() {
        markdownPreferences = MarkdownPreferences(this)
        markdownRenderer = MarkdownRenderer(this)
        // 设置当前选择的Markdown语法
        markdownRenderer.setFlavor(markdownPreferences.getMarkdownFlavor())
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        when {
            Build.VERSION.SDK_INT >= 33 -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_IMAGES)
                }
            }
            Build.VERSION.SDK_INT >= 23 -> {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    private fun setupListeners() {
        editModeBtn.setOnClickListener { switchMode(Mode.EDIT) }
        previewModeBtn.setOnClickListener { switchMode(Mode.PREVIEW) }

        // 点击EditText时滚动到光标位置（仅在键盘弹出时）
        editArea.setOnClickListener {
            if (getKeyboardHeight() > 0) {
                editArea.postDelayed({
                    scrollToCursorPosition()
                }, 200)
            }
        }
        
        // 设置编辑区滚动监听器 - 保存滚动位置
        editScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (currentMode == Mode.EDIT) {
                editScrollY = scrollY
            }
        }

        editArea.addTextChangedListener(object : android.text.TextWatcher {
            private var beforeSelectionStart = 0
            private var beforeSelectionEnd = 0
            
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                // 记录变化前的选区位置
                beforeSelectionStart = editArea.selectionStart
                beforeSelectionEnd = editArea.selectionEnd
            }
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            
            override fun afterTextChanged(s: android.text.Editable?) {
                if (!isUndoRedo) {
                    // 删除当前位置之后的所有历史记录（当有新编辑时，清除重做历史）
                    while (historyList.size > historyIndex + 1) {
                        historyList.removeAt(historyList.size - 1)
                    }
                    // 添加新状态（使用变化前的选区位置）
                    historyList.add(HistoryItem(s.toString(), beforeSelectionStart, beforeSelectionEnd))
                    historyIndex = historyList.size - 1
                    // 限制历史记录大小
                    if (historyList.size > maxHistorySize) {
                        historyList.removeAt(0)
                        historyIndex--
                    }
                }
                isDirty = true
                showSaving()
                updateWordCount()
            }
        })

        setupFormatButtons()
        setupQuickActionButtons()
    }

    private fun setupFormatButtons() {
        // 撤销/重做按钮
        findViewById<View>(R.id.btnUndo).apply {
            setOnClickListener { undo() }
            setOnLongClickListener { showTooltip("撤销上一步操作"); true }
        }
        findViewById<View>(R.id.btnRedo).apply {
            setOnClickListener { redo() }
            setOnLongClickListener { showTooltip("重做已撤销的操作"); true }
        }
        
        // 格式按钮
        findViewById<View>(R.id.btnBold).apply {
            setOnClickListener { insertMarkdown("**", "**") }
            setOnLongClickListener { showTooltip("粗体 **文本**"); true }
        }
        findViewById<View>(R.id.btnItalic).apply {
            setOnClickListener { insertMarkdown("*", "*") }
            setOnLongClickListener { showTooltip("斜体 *文本*"); true }
        }
        findViewById<View>(R.id.btnStrikethrough).apply {
            setOnClickListener { insertMarkdown("~~", "~~") }
            setOnLongClickListener { showTooltip("删除线 ~~文本~~"); true }
        }
        findViewById<View>(R.id.btnHeading).apply {
            setOnClickListener { insertMarkdown("# ") }
            setOnLongClickListener { showTooltip("标题 # 标题"); true }
        }
        findViewById<View>(R.id.btnList).apply {
            setOnClickListener { insertMarkdown("- ") }
            setOnLongClickListener { showTooltip("无序列表 - 项目"); true }
        }
        findViewById<View>(R.id.btnOrderedList).apply {
            setOnClickListener { insertMarkdown("1. ") }
            setOnLongClickListener { showTooltip("有序列表 1. 项目"); true }
        }
        findViewById<View>(R.id.btnQuote).apply {
            setOnClickListener { insertMarkdown("> ") }
            setOnLongClickListener { showTooltip("引用 > 引用文本"); true }
        }
        findViewById<View>(R.id.btnCode).apply {
            setOnClickListener { insertMarkdown("```\n", "\n```") }
            setOnLongClickListener { showTooltip("代码块"); true }
        }
        findViewById<View>(R.id.btnInlineCode).apply {
            setOnClickListener { insertMarkdown("`", "`") }
            setOnLongClickListener { showTooltip("行内代码 `代码`"); true }
        }
        findViewById<View>(R.id.btnLink).apply {
            setOnClickListener { insertMarkdown("[", "](url)") }
            setOnLongClickListener { showTooltip("链接 [文本](url)"); true }
        }
        findViewById<View>(R.id.btnImage).apply {
            setOnClickListener { showImageOptions() }
            setOnLongClickListener { showTooltip("图片 ![alt](url)"); true }
        }
        findViewById<View>(R.id.btnDivider).apply {
            setOnClickListener { insertMarkdown("---\n") }
            setOnLongClickListener { showTooltip("分隔线 ---"); true }
        }
        findViewById<View>(R.id.btnTable).apply {
            setOnClickListener {
                insertMarkdown("| 列1 | 列2 |\n| --- | --- |\n| 内容 | 内容 |")
            }
            setOnLongClickListener { showTooltip("表格"); true }
        }
    }

    private fun showTooltip(message: String): Boolean {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
        return true
    }

    private fun setupQuickActionButtons() {
        btnClear.apply {
            setOnClickListener { clearEditor() }
            setOnLongClickListener { showTooltip("清空所有内容"); true }
        }
        btnCopy.apply {
            setOnClickListener { copyContent() }
            setOnLongClickListener { showTooltip("复制到剪贴板"); true }
        }
        btnShare.apply {
            setOnClickListener { shareContent() }
            setOnLongClickListener { showTooltip("分享Markdown文件"); true }
        }
        btnImport.apply {
            setOnClickListener { triggerImport() }
            setOnLongClickListener { showTooltip("导入Markdown文件"); true }
        }
        btnExport.apply {
            setOnClickListener { triggerExport() }
            setOnLongClickListener { showTooltip("导出Markdown文件"); true }
        }
    }

    /**
     * 设置预览模式下的交互
     * 包括链接点击、滚动同步等
     */
    private fun setupPreviewGesture() {
        // 预览区的触摸事件处理：
        // 1. 链接点击由 LinkMovementMethod 处理
        // 2. 滚动由 NestedScrollView 处理
        // 3. 长按选择文本由 TextView 的 textIsSelectable 处理
        
        // 设置滚动监听器 - 保存滚动位置用于模式切换时同步
        previewScrollView.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            if (currentMode == Mode.PREVIEW) {
                previewScrollY = scrollY
            }
        }
    }

    /**
     * 切换工具栏显示/隐藏
     * 已废除：工具栏始终可见
     */
    private fun toggleToolbarVisibility() {
        // 工具栏始终可见，不再隐藏
    }

    /**
     * 显示工具栏
     */
    private fun showToolbar() {
        bottomToolbar.animate()
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
        
        // 不再自动隐藏
    }

    /**
     * 隐藏工具栏
     * 已废除：工具栏始终可见
     */
    private fun hideToolbar() {
        // 工具栏始终可见，不再隐藏
    }

    private fun loadSampleContent() {
        // 优先加载草稿内容
        val savedDraft = sharedPreferences.getString(KEY_DRAFT_CONTENT, null)
        val initialContent = if (savedDraft != null && savedDraft.isNotEmpty()) {
            savedDraft
        } else {
            """# 欢迎使用 Acuspic

这是一个**实时渲染**的 Markdown 编辑器。

## 特性

- **所见即所得** - 编辑时实时预览
- **优雅界面** - 深色编辑模式，白色预览模式
- **快捷操作** - 底部工具栏快速插入格式
- **移动优先** - 专为移动端优化
- **自动保存** - 草稿自动保存，不怕丢失

## 代码示例

```kotlin
fun hello() {
    println("Hello, Markdown!")
}
```

## 表格示例

| 功能 | 状态 | 描述 |
|------|------|------|
| 实时预览 | ✅ | 编辑时自动渲染 |
| 文件导入 | ✅ | 支持导入本地 Markdown 文件 |
| 文件导出 | ✅ | 支持导出 Markdown 源文件 |
| 自动保存 | ✅ | 草稿自动保存 |

> **提示**: 点击右上角的"预览"按钮查看渲染效果，点击"编辑"返回源码模式。

---

开始编辑吧！"""
        }

        // 先设置初始内容
        editArea.setText(initialContent)

        // 初始化历史记录（包含光标位置和选区信息）
        historyList.clear()
        historyList.add(HistoryItem(initialContent, initialContent.length, initialContent.length))
        historyIndex = 0
    }

    /**
     * 切换编辑/预览模式
     * 实现滑块动画和状态同步
     * 
     * @param mode 目标模式
     * @param animate 是否执行动画
     */
    private fun switchMode(mode: Mode, animate: Boolean = true) {
        currentMode = mode
        val duration = if (animate) 250L else 0L

        when (mode) {
            Mode.EDIT -> {
                // 保存预览区滚动位置
                previewScrollY = previewScrollView.scrollY
                
                // 移动滑块到编辑按钮位置 (左侧，X = 0)
                animateIndicator(0f, duration)
                
                // 设置按钮选中状态
                editModeBtn.isSelected = true
                previewModeBtn.isSelected = false

                applyDarkTheme()

                editArea.visibility = View.VISIBLE
                previewScrollView.visibility = View.INVISIBLE
                previewScrollView.alpha = 0f

                // 启用所有格式工具按钮
                setFormatToolsEnabled(true)
                
                // 启用所有底部按钮
                setBottomButtonsEnabled(true)
                
                // 根据预览区滚动位置计算编辑区对应位置
                syncPreviewToEditPosition()
                
                // 重新应用WindowInsets以更新底部工具栏位置
                rootContainer.requestApplyInsets()
            }

            Mode.PREVIEW -> {
                // 保存编辑区滚动位置
                editScrollY = editScrollView.scrollY
                
                // 移动滑块到预览按钮位置 (右侧)
                // 计算移动距离：容器内部宽度(138dp) - 滑块宽度(69dp) = 69dp
                animateIndicator(69f, duration)
                
                // 设置按钮选中状态
                editModeBtn.isSelected = false
                previewModeBtn.isSelected = true

                applyLightTheme()
                updatePreview()

                previewScrollView.visibility = View.VISIBLE
                if (animate) {
                    previewScrollView.animate()
                        .alpha(1f)
                        .setDuration(duration)
                        .start()
                } else {
                    previewScrollView.alpha = 1f
                }

                editArea.visibility = View.INVISIBLE

                // 预览模式下禁用格式工具按钮
                setFormatToolsEnabled(false)
                
                // 预览模式下禁用非导出按钮
                setBottomButtonsForPreview()
                
                // 根据编辑区滚动位置计算预览区对应位置
                syncEditToPreviewPosition()
                
                // 重新应用WindowInsets以更新底部工具栏位置
                rootContainer.requestApplyInsets()
            }
        }
    }

    /**
     * 根据编辑区滚动位置同步预览区位置
     * 基于文本行数比例计算对应位置
     */
    private fun syncEditToPreviewPosition() {
        val text = editArea.text.toString()
        if (text.isEmpty()) return
        
        val layout = editArea.layout ?: return
        
        // 获取编辑区可见的第一行
        val firstVisibleLine = layout.getLineForVertical(editScrollY)
        
        // 计算总行数
        val totalLines = layout.lineCount
        
        // 计算滚动比例
        val scrollRatio = if (totalLines > 0) firstVisibleLine.toFloat() / totalLines else 0f
        
        // 计算预览区对应位置
        val previewContentHeight = previewArea.height
        val scrollViewHeight = previewScrollView.height
        val maxScrollY = (previewContentHeight - scrollViewHeight).coerceAtLeast(0)
        val targetScrollY = (scrollRatio * maxScrollY).toInt().coerceIn(0, maxScrollY)
        
        previewScrollView.post {
            previewScrollView.smoothScrollTo(0, targetScrollY)
        }
    }

    /**
     * 根据预览区滚动位置同步编辑区位置
     * 基于内容比例计算对应位置
     */
    private fun syncPreviewToEditPosition() {
        val text = editArea.text.toString()
        if (text.isEmpty()) return
        
        val layout = editArea.layout ?: return
        
        // 计算预览区滚动比例
        val previewContentHeight = previewArea.height
        val previewScrollViewHeight = previewScrollView.height
        val maxPreviewScroll = (previewContentHeight - previewScrollViewHeight).coerceAtLeast(1)
        val previewScrollRatio = (previewScrollY.toFloat() / maxPreviewScroll).coerceIn(0f, 1f)
        
        // 计算编辑区对应行
        val totalLines = layout.lineCount
        val targetLine = (previewScrollRatio * totalLines).toInt().coerceIn(0, totalLines - 1)
        
        // 获取目标行的Y坐标
        val targetScrollY = layout.getLineTop(targetLine)
        val editContentHeight = editArea.height
        val editScrollViewHeight = editScrollView.height
        val maxEditScroll = (editContentHeight - editScrollViewHeight).coerceAtLeast(0)
        
        editScrollView.post {
            editScrollView.smoothScrollTo(0, targetScrollY.coerceIn(0, maxEditScroll))
        }
    }

    /**
     * 滑块位移动画
     */
    private fun animateIndicator(targetX: Float, duration: Long) {
        modeToggleIndicator.animate()
            .translationX(targetX.dpToPx())
            .setDuration(duration)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /**
     * dp转px
     */
    private fun Float.dpToPx(): Float {
        return this * resources.displayMetrics.density
    }

    /**
     * 设置底部按钮启用/禁用状态
     */
    private fun setBottomButtonsEnabled(enabled: Boolean) {
        val buttons = listOf(btnClear, btnCopy, btnShare, btnImport, btnExport)
        buttons.forEach { it.isEnabled = enabled }
        
        if (enabled) {
            // 启用状态样式 - 编辑模式
            setButtonStyle(btnClear, R.color.dark_button_bg, R.color.dark_text_primary)
            setButtonStyle(btnCopy, R.color.dark_button_bg, R.color.dark_text_primary)
            setButtonStyle(btnShare, R.color.dark_button_bg, R.color.dark_text_primary)
            setButtonStyle(btnImport, R.color.dark_button_bg, R.color.dark_text_primary)
            setButtonStyle(btnExport, R.color.accent_primary, android.R.color.white)
        } else {
            // 禁用状态样式
            buttons.forEach { setButtonStyle(it, R.color.dark_button_disabled, R.color.dark_text_disabled) }
        }
    }
    
    private fun setButtonStyle(button: MaterialButton, bgColorRes: Int, textColorRes: Int) {
        button.backgroundTintList = ContextCompat.getColorStateList(this, bgColorRes)
        button.setTextColor(ContextCompat.getColor(this, textColorRes))
        button.iconTint = ContextCompat.getColorStateList(this, textColorRes)
    }

    
    /**
     * 设置格式工具按钮启用/禁用状态
     */
    private fun setFormatToolsEnabled(enabled: Boolean) {
        val formatButtons = listOf<ImageButton>(
            findViewById(R.id.btnUndo),
            findViewById(R.id.btnRedo),
            findViewById(R.id.btnBold),
            findViewById(R.id.btnItalic),
            findViewById(R.id.btnStrikethrough),
            findViewById(R.id.btnHeading),
            findViewById(R.id.btnList),
            findViewById(R.id.btnOrderedList),
            findViewById(R.id.btnQuote),
            findViewById(R.id.btnCode),
            findViewById(R.id.btnInlineCode),
            findViewById(R.id.btnLink),
            findViewById(R.id.btnImage),
            findViewById(R.id.btnDivider),
            findViewById(R.id.btnTable)
        )
        
        for (button in formatButtons) {
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else 0.4f
        }
    }

    /**
     * 预览模式下的按钮状态：仅导出可用
     */
    private fun setBottomButtonsForPreview() {
        // 清空、复制、导入按钮禁用
        btnClear.isEnabled = false
        btnCopy.isEnabled = false
        btnImport.isEnabled = false
        
        // 分享、导出按钮启用
        btnShare.isEnabled = true
        btnExport.isEnabled = true
        
        // 应用禁用样式
        btnClear.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_button_disabled)
        btnClear.setTextColor(ContextCompat.getColor(this, R.color.dark_text_disabled))
        btnClear.iconTint = ContextCompat.getColorStateList(this, R.color.dark_text_disabled)
        
        btnCopy.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_button_disabled)
        btnCopy.setTextColor(ContextCompat.getColor(this, R.color.dark_text_disabled))
        btnCopy.iconTint = ContextCompat.getColorStateList(this, R.color.dark_text_disabled)
        
        btnImport.backgroundTintList = ContextCompat.getColorStateList(this, R.color.dark_button_disabled)
        btnImport.setTextColor(ContextCompat.getColor(this, R.color.dark_text_disabled))
        btnImport.iconTint = ContextCompat.getColorStateList(this, R.color.dark_text_disabled)
        
        // 分享按钮保持主色样式
        btnShare.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
        btnShare.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        btnShare.iconTint = ContextCompat.getColorStateList(this, android.R.color.white)
        
        // 导出按钮保持主色样式
        btnExport.backgroundTintList = ContextCompat.getColorStateList(this, R.color.accent_primary)
        btnExport.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        btnExport.iconTint = ContextCompat.getColorStateList(this, android.R.color.white)
    }

    private fun applyDarkTheme() {
        rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_background))
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_toolbar_bg))
        toolbarTitle.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        bottomToolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_surface))
        formatToolsScroll.setBackgroundColor(ContextCompat.getColor(this, R.color.dark_surface))

        // 设置按钮 - 深色模式使用白色
        btnSettings.imageTintList = ContextCompat.getColorStateList(this, android.R.color.white)

        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.dark_surface)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = 0
        }
    }

    private fun applyLightTheme() {
        rootContainer.setBackgroundColor(ContextCompat.getColor(this, R.color.light_background))
        toolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.light_toolbar_bg))
        toolbarTitle.setTextColor(ContextCompat.getColor(this, R.color.light_text_primary))
        bottomToolbar.setBackgroundColor(ContextCompat.getColor(this, R.color.light_surface))
        formatToolsScroll.setBackgroundColor(ContextCompat.getColor(this, R.color.light_surface))

        // 设置按钮 - 浅色模式使用深色
        btnSettings.imageTintList = ContextCompat.getColorStateList(this, R.color.light_text_primary)

        @Suppress("DEPRECATION")
        window.statusBarColor = ContextCompat.getColor(this, R.color.light_background)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS,
                android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun updateWordCount() {
        val text = editArea.text.toString()
        val charCount = text.length
        val lineCount = if (text.isEmpty()) 0 else text.split("\n").size
        wordCountText.text = getString(R.string.word_count_format, charCount, lineCount)
    }

    private fun updatePreview() {
        val markdown = editArea.text.toString()
        markdownRenderer.setMarkdown(previewArea, markdown, ::onLinkClicked)
    }
    
    /**
     * 处理链接点击事件
     * @param url 链接URL
     * @return true 表示已处理，false 表示未处理
     */
    private fun onLinkClicked(url: String): Boolean {
        return if (url.startsWith("#")) {
            // 锚点链接，在页面内跳转
            scrollToAnchor(url.substring(1))
            true
        } else {
            // 外部链接，让系统处理
            false
        }
    }
    
    /**
     * 滚动到指定锚点位置
     * 支持标题锚点跳转（如 #heading-name）
     */
    private fun scrollToAnchor(anchorId: String) {
        // 从渲染器获取标题位置映射
        val headerPositions = markdownRenderer.getHeaderPositions()
        val headerOffset = headerPositions[anchorId.lowercase()]

        if (headerOffset != null) {
            // 找到标题位置，计算对应的滚动位置
            val markdown = editArea.text.toString()
            
            // 计算标题在源码中的行号
            val lines = markdown.lines()
            var charCount = 0
            var targetLine = 0
            
            for ((index, line) in lines.withIndex()) {
                if (charCount >= headerOffset) {
                    targetLine = index
                    break
                }
                charCount += line.length + 1
            }
            
            // 计算预览区对应位置
            // 基于行数比例计算滚动位置
            val totalLines = lines.size
            if (totalLines > 0 && previewArea.layout != null) {
                val previewLayout = previewArea.layout
                val previewLineCount = previewLayout.lineCount
                
                // 计算目标行在预览中的大致位置
                val targetPreviewLine = (targetLine.toFloat() / totalLines * previewLineCount).toInt()
                val y = previewLayout.getLineTop(targetPreviewLine.coerceIn(0, previewLineCount - 1))
                
                // 添加顶部边距
                val topMargin = 20f.dpToPx().toInt()
                previewScrollView.smoothScrollTo(0, (y - topMargin).coerceAtLeast(0))
            }
        } else {
            // 未找到锚点，尝试在渲染后的文本中查找
            val anchorPositions = markdownRenderer.getAnchorPositions()
            val offset = anchorPositions[anchorId.lowercase()]
            
            if (offset != null) {
                val layout = previewArea.layout
                if (layout != null) {
                    val safeOffset = offset.coerceIn(0, previewArea.text.length)
                    val line = layout.getLineForOffset(safeOffset)
                    val y = layout.getLineTop(line)
                    val topMargin = 20f.dpToPx().toInt()
                    previewScrollView.smoothScrollTo(0, (y - topMargin).coerceAtLeast(0))
                }
            }
        }
    }

    private fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            val historyItem = historyList[historyIndex]
            isUndoRedo = true
            editArea.setText(historyItem.text)
            // 恢复保存的选区位置，确保不超出文本长度
            val textLength = editArea.text.length
            val selStart = historyItem.selectionStart.coerceIn(0, textLength)
            val selEnd = historyItem.selectionEnd.coerceIn(0, textLength)
            if (historyItem.hasSelection) {
                editArea.setSelection(selStart, selEnd)
            } else {
                editArea.setSelection(selStart)
            }
            isUndoRedo = false
            updateWordCount()
        }
    }

    private fun redo() {
        if (historyIndex < historyList.size - 1) {
            historyIndex++
            val historyItem = historyList[historyIndex]
            isUndoRedo = true
            editArea.setText(historyItem.text)
            // 恢复保存的选区位置，确保不超出文本长度
            val textLength = editArea.text.length
            val selStart = historyItem.selectionStart.coerceIn(0, textLength)
            val selEnd = historyItem.selectionEnd.coerceIn(0, textLength)
            if (historyItem.hasSelection) {
                editArea.setSelection(selStart, selEnd)
            } else {
                editArea.setSelection(selStart)
            }
            isUndoRedo = false
            updateWordCount()
        }
    }

    private fun insertMarkdown(before: String, after: String = "") {
        val start = editArea.selectionStart
        val end = editArea.selectionEnd
        val text = editArea.text

        val selectedText = if (start != end) text.substring(start, end) else ""
        val newText = text.substring(0, start) + before + selectedText + after + text.substring(end)

        editArea.setText(newText)
        editArea.setSelection(start + before.length + selectedText.length)
        editArea.requestFocus()

        showSaving()
    }

    private fun showSaving() {
        statusText.text = getString(R.string.status_saving)
        statusBar.animate()
            .translationY(0f)
            .setDuration(100)
            .start()

        saveRunnable?.let { handler.removeCallbacks(it) }
        saveRunnable = Runnable {
            // 自动保存草稿
            saveDraft()
            statusText.text = getString(R.string.status_saved)
            isDirty = false
            handler.postDelayed({
                if (!isDirty) {
                    statusBar.animate()
                        .translationY(-statusBar.height.toFloat())
                        .setDuration(100)
                        .start()
                }
            }, 1000)
        }.also { runnable ->
            handler.postDelayed(runnable, AUTO_SAVE_DELAY)
        }
    }

    /**
     * 保存草稿到SharedPreferences
     */
    private fun saveDraft() {
        val content = editArea.text.toString()
        sharedPreferences.edit()
            .putString(KEY_DRAFT_CONTENT, content)
            .apply()
    }

    private fun clearEditor() {
        val dialog = AlertDialog.Builder(this)
            .setTitle("确认清空")
            .setMessage(R.string.confirm_clear)
            .setPositiveButton(R.string.confirm_yes) { _, _ ->
                editArea.setText("")
                showSaving()
                editArea.requestFocus()
            }
            .setNegativeButton(R.string.confirm_cancel, null)
            .create()
        
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(
                ContextCompat.getColor(this, R.color.accent_primary)
            )
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(
                ContextCompat.getColor(this, R.color.dark_text_secondary)
            )
        }
        
        dialog.show()
    }

    private fun copyContent() {
        val content = when (currentMode) {
            Mode.EDIT -> editArea.text.toString()
            Mode.PREVIEW -> previewArea.text.toString()
        }

        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Markdown", content)
        clipboard.setPrimaryClip(clip)

        showStatus(getString(R.string.status_copied))
    }

    private fun shareContent() {
        try {
            val content = editArea.text.toString()
            val timestamp = System.currentTimeMillis()
            val fileName = "markdown_$timestamp.md"
            
            val shareDir = File(cacheDir, "share")
            if (!shareDir.exists()) {
                shareDir.mkdirs()
            }
            
            val shareFile = File(shareDir, fileName)
            shareFile.writeText(content)
            
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                shareFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                // 添加多种MIME类型以支持更多应用
                type = "*/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, fileName)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            }
            
            val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.action_share))
            startActivity(chooserIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "分享失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerImport() {
        openDocumentLauncher.launch(arrayOf("text/markdown", "text/plain", "text/x-markdown"))
    }

    private fun importMarkdown(uri: Uri) {
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val content = inputStream.bufferedReader().use { it.readText() }
                editArea.setText(content)
                showStatus(getString(R.string.status_imported))
                showSaving()
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_import, Toast.LENGTH_SHORT).show()
        }
    }

    private fun triggerExport() {
        val timestamp = System.currentTimeMillis()
        createDocumentLauncher.launch("markdown-export-$timestamp.md")
    }

    private fun exportMarkdown(uri: Uri) {
        try {
            val content = editArea.text.toString()
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(content.toByteArray())
                showStatus(getString(R.string.status_exported))
            }
        } catch (e: Exception) {
            Toast.makeText(this, R.string.error_export, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStatus(message: String) {
        statusText.text = message
        statusBar.animate()
            .translationY(0f)
            .setDuration(100)
            .start()

        handler.postDelayed({
            statusBar.animate()
                .translationY(-statusBar.height.toFloat())
                .setDuration(100)
                .start()
        }, 2000)
    }

    private var backPressedTime = 0L
    private var backToast: Toast? = null

    override fun onDestroy() {
        super.onDestroy()
        saveRunnable?.let { handler.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        // 检查Markdown语法是否改变
        val currentFlavor = markdownPreferences.getMarkdownFlavor()
        if (markdownRenderer.getCurrentFlavor() != currentFlavor) {
            // 语法已改变，更新渲染器
            markdownRenderer.setFlavor(currentFlavor)
            // 如果当前在预览模式，立即重新渲染
            if (currentMode == Mode.PREVIEW) {
                updatePreview()
            }
        }
    }

    // ==================== 图床功能 ====================

    /**
     * 显示图片选项对话框
     */
    private fun showImageOptions() {
        val options = arrayOf("插入图片链接", "从相册选择并上传")

        AlertDialog.Builder(this)
            .setTitle("插入图片")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> insertMarkdown("![alt](", ")")
                    1 -> startImageUpload()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 开始图片上传流程
     */
    private fun startImageUpload() {
        if (!imageHostPreferences.isImageHostEnabled()) {
            // 未启用图床，提示用户配置
            AlertDialog.Builder(this)
                .setTitle("图床未配置")
                .setMessage("您尚未配置图床。是否现在前往设置？")
                .setPositiveButton("去设置") { _, _ ->
                    startActivity(Intent(this, ImageHostSettingsActivity::class.java))
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }

        imagePickerLauncher.launch("image/*")
    }

    /**
     * 处理图片选择结果
     */
    private fun handleImageSelection(uri: Uri) {
        if (!imageHostUploader.isImageFile(uri)) {
            Toast.makeText(this, "请选择图片文件", Toast.LENGTH_SHORT).show()
            return
        }

        val config = imageHostPreferences.getConfig()

        if (config.autoUpload) {
            // 自动上传
            uploadImageToHost(uri)
        } else {
            // 显示确认对话框
            AlertDialog.Builder(this)
                .setTitle("上传图片")
                .setMessage("是否上传此图片到 ${config.type.displayName}？")
                .setPositiveButton("上传") { _, _ ->
                    uploadImageToHost(uri)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    /**
     * 上传图片到图床
     */
    private fun uploadImageToHost(uri: Uri) {
        showStatus("正在上传图片...")

        val config = imageHostPreferences.getConfig()
        val fileSize = imageHostUploader.getFileSize(uri)
        val fileName = uri.lastPathSegment ?: "image.png"

        lifecycleScope.launch {
            imageHostUploader.uploadImage(uri, config, fileName).collect { result ->
                when (result) {
                    is UploadResult.Success -> {
                        // 保存上传历史
                        val history = UploadHistory(
                            originalName = fileName,
                            url = result.url,
                            deleteUrl = result.deleteUrl,
                            hostType = config.type,
                            fileSize = fileSize
                        )
                        imageHostPreferences.addUploadHistory(history)

                        // 插入图片链接到编辑器
                        insertMarkdown("![${fileName}](${result.url})")
                        showStatus("图片上传成功！")
                    }
                    is UploadResult.Error -> {
                        showStatus("上传失败: ${result.message}")
                        Toast.makeText(this@MainActivity, "上传失败: ${result.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {}
                }
            }
        }
    }
}
