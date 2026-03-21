package com.acuspic.app.markdown

import android.content.Context
import android.content.SharedPreferences

/**
 * Markdown偏好设置管理器
 */
class MarkdownPreferences(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "MarkdownPrefs"
        private const val KEY_MARKDOWN_FLAVOR = "markdown_flavor"
        private const val KEY_LIVE_PREVIEW = "live_preview"
        private const val KEY_CODE_HIGHLIGHT = "code_highlight"
        private const val KEY_SHOW_LINE_NUMBERS = "show_line_numbers"
        private const val KEY_FONT_SIZE = "font_size"
        private const val KEY_WORD_WRAP = "word_wrap"
    }

    /**
     * 获取当前Markdown语法标准
     */
    fun getMarkdownFlavor(): MarkdownFlavor {
        val flavorName = prefs.getString(KEY_MARKDOWN_FLAVOR, MarkdownFlavor.GFM.name)
        return try {
            MarkdownFlavor.valueOf(flavorName ?: MarkdownFlavor.GFM.name)
        } catch (e: Exception) {
            MarkdownFlavor.GFM
        }
    }

    /**
     * 设置Markdown语法标准
     */
    fun setMarkdownFlavor(flavor: MarkdownFlavor) {
        prefs.edit().putString(KEY_MARKDOWN_FLAVOR, flavor.name).apply()
    }

    /**
     * 是否启用实时预览
     */
    fun isLivePreviewEnabled(): Boolean {
        return prefs.getBoolean(KEY_LIVE_PREVIEW, true)
    }

    /**
     * 设置实时预览
     */
    fun setLivePreview(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_LIVE_PREVIEW, enabled).apply()
    }

    /**
     * 是否启用代码高亮
     */
    fun isCodeHighlightEnabled(): Boolean {
        return prefs.getBoolean(KEY_CODE_HIGHLIGHT, true)
    }

    /**
     * 设置代码高亮
     */
    fun setCodeHighlight(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CODE_HIGHLIGHT, enabled).apply()
    }

    /**
     * 是否显示行号
     */
    fun isShowLineNumbers(): Boolean {
        return prefs.getBoolean(KEY_SHOW_LINE_NUMBERS, false)
    }

    /**
     * 设置显示行号
     */
    fun setShowLineNumbers(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_LINE_NUMBERS, enabled).apply()
    }

    /**
     * 获取字体大小
     */
    fun getFontSize(): Float {
        return prefs.getFloat(KEY_FONT_SIZE, 16f)
    }

    /**
     * 设置字体大小
     */
    fun setFontSize(size: Float) {
        prefs.edit().putFloat(KEY_FONT_SIZE, size).apply()
    }

    /**
     * 是否自动换行
     */
    fun isWordWrapEnabled(): Boolean {
        return prefs.getBoolean(KEY_WORD_WRAP, true)
    }

    /**
     * 设置自动换行
     */
    fun setWordWrap(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_WORD_WRAP, enabled).apply()
    }

    /**
     * 重置所有设置为默认值
     */
    fun resetToDefaults() {
        prefs.edit().clear().apply()
    }
}
