package com.acuspic.app.markdown

import android.content.Context
import android.text.Spanned
import android.text.Spannable
import android.text.method.LinkMovementMethod
import android.text.method.MovementMethod
import android.text.method.ScrollingMovementMethod
import android.text.style.ClickableSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.MarkwonSpansFactory
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.CorePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import io.noties.markwon.html.HtmlPlugin
import io.noties.markwon.image.ImagesPlugin
import io.noties.markwon.image.glide.GlideImagesPlugin
import io.noties.markwon.linkify.LinkifyPlugin
import org.commonmark.node.Link

/**
 * Markdown渲染管理器
 * 支持多种Markdown语法标准，每种方言配置对应的渲染插件和预处理
 *
 * 六种方言的完整渲染配置及特有语法支持：
 *
 * 1. GFM (GitHub Flavored Markdown)
 *    - 特有语法：任务列表(- [x])、删除线(~~text~~)、表格(|)、自动链接、表情符号(:emoji:)
 *    - 渲染：完整支持（Markwon原生插件）
 *
 * 2. CommonMark
 *    - 严格标准语法，无任何扩展
 *    - 渲染：基础语法严格模式（无扩展插件）
 *
 * 3. Original (Gruber Markdown)
 *    - 2004年原始规范，无扩展
 *    - 渲染：原始语法兼容模式（无扩展插件）
 *
 * 4. MultiMarkdown (MMD)
 *    - 特有语法：脚注[^1]、目录[TOC]、元数据(Title:)、定义列表、LaTeX数学公式(\(...\) \[...\])、CriticMarkup、文件包含
 *    - 渲染：表格+删除线，特有语法通过预处理转换
 *
 * 5. Markdown Extra
 *    - 特有语法：脚注[^1]、围栏代码块(```/~~~)、标题ID(## {#id})、定义列表、缩写、表格对齐
 *    - 渲染：表格+删除线+围栏代码块，特有语法通过预处理转换
 *
 * 6. Pandoc's Markdown
 *    - 特有语法：YAML元数据(---)、脚注[^1]、引用[@cite]、LaTeX数学公式($...$ $$...$$)、定义列表、上标^text^、下标~text~、Fenced divs、Wiki链接、行块、示例列表
 *    - 渲染：表格+删除线+自动链接，特有语法通过预处理转换
 */
class MarkdownRenderer(private val context: Context) {

    private val markwonInstances = mutableMapOf<MarkdownFlavor, Markwon>()
    private var currentFlavor: MarkdownFlavor = MarkdownFlavor.default()

    init {
        // 初始化所有语法的渲染器
        MarkdownFlavor.values().forEach { flavor ->
            markwonInstances[flavor] = createMarkwon(flavor)
        }
    }

    /**
     * 创建Markwon实例
     * 根据不同的Markdown方言配置对应的渲染插件
     */
    private fun createMarkwon(flavor: MarkdownFlavor): Markwon {
        return Markwon.builder(context)
            .apply {
                // 核心插件（所有语法都支持基础Markdown语法）
                usePlugin(CorePlugin.create())

                // HTML标签支持（所有语法都支持）
                usePlugin(HtmlPlugin.create())

                // 图片加载支持（所有语法都支持）
                usePlugin(ImagesPlugin.create())
                usePlugin(GlideImagesPlugin.create(context))

                // 根据语法标准添加扩展插件
                when (flavor) {
                    MarkdownFlavor.GFM -> {
                        // GFM: GitHub Flavored Markdown
                        // 支持：表格、任务列表、删除线、自动链接
                        usePlugin(TablePlugin.create(context))
                        usePlugin(TaskListPlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                        usePlugin(LinkifyPlugin.create())
                    }

                    MarkdownFlavor.COMMONMARK -> {
                        // CommonMark: 标准化规范
                        // 仅支持基础语法，无扩展
                    }

                    MarkdownFlavor.ORIGINAL -> {
                        // Original Markdown (Gruber): 原始规范
                        // 仅支持基础语法，无扩展
                    }

                    MarkdownFlavor.MULTIMARKDOWN -> {
                        // MultiMarkdown (MMD): 学术写作扩展
                        // 支持：表格、删除线
                        usePlugin(TablePlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                    }

                    MarkdownFlavor.MARKDOWN_EXTRA -> {
                        // Markdown Extra: PHP Markdown扩展
                        // 支持：表格、删除线
                        usePlugin(TablePlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                    }

                    MarkdownFlavor.PANDOC -> {
                        // Pandoc's Markdown: 文档转换器方言
                        // 支持：表格、删除线、自动链接
                        usePlugin(TablePlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                        usePlugin(LinkifyPlugin.create())
                    }
                }
            }
            .build()
    }

    /**
     * 设置当前语法标准
     */
    fun setFlavor(flavor: MarkdownFlavor) {
        currentFlavor = flavor
    }

    /**
     * 获取当前语法标准
     */
    fun getCurrentFlavor(): MarkdownFlavor = currentFlavor

    /**
     * 渲染Markdown文本
     * 先根据方言预处理，再渲染
     */
    fun render(markdown: String): Spanned {
        val processedMarkdown = preprocessMarkdown(markdown, currentFlavor)
        return markwonInstances[currentFlavor]?.toMarkdown(processedMarkdown)
            ?: throw IllegalStateException("Markwon instance not initialized for $currentFlavor")
    }

    /**
     * 渲染并设置到TextView
     * 先根据方言预处理，再渲染
     */
    fun setMarkdown(textView: TextView, markdown: String, onLinkClicked: ((String) -> Boolean)? = null) {
        val processedMarkdown = preprocessMarkdown(markdown, currentFlavor)

        // 先提取并存储标题位置（用于锚点跳转）
        extractHeaderPositions(processedMarkdown)

        // 创建带链接处理的Markwon实例
        val markwon = Markwon.builder(context)
            .apply {
                // 核心插件（所有语法都支持基础Markdown语法）
                usePlugin(CorePlugin.create())

                // HTML标签支持（所有语法都支持）
                usePlugin(HtmlPlugin.create())

                // 图片加载支持（所有语法都支持）
                usePlugin(ImagesPlugin.create())
                usePlugin(GlideImagesPlugin.create(context))

                // 根据语法标准添加扩展插件
                when (currentFlavor) {
                    MarkdownFlavor.GFM -> {
                        usePlugin(TablePlugin.create(context))
                        usePlugin(TaskListPlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                        // LinkifyPlugin用于自动链接识别
                        usePlugin(LinkifyPlugin.create())
                    }
                    MarkdownFlavor.COMMONMARK, MarkdownFlavor.ORIGINAL -> {
                        // 无扩展，但添加自动链接支持
                        usePlugin(LinkifyPlugin.create())
                    }
                    MarkdownFlavor.MULTIMARKDOWN, MarkdownFlavor.MARKDOWN_EXTRA -> {
                        usePlugin(TablePlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                    }
                    MarkdownFlavor.PANDOC -> {
                        usePlugin(TablePlugin.create(context))
                        usePlugin(StrikethroughPlugin.create())
                        usePlugin(LinkifyPlugin.create())
                    }
                }

                // 添加链接点击处理插件
                usePlugin(object : AbstractMarkwonPlugin() {
                    override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                        builder.linkResolver { view, link ->
                            val handled = onLinkClicked?.invoke(link) ?: false
                            if (!handled) {
                                // 如果未处理，使用默认行为（打开浏览器）
                                try {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))
                                    view.context.startActivity(intent)
                                } catch (e: Exception) {
                                    // 忽略无法处理的链接
                                }
                            }
                        }
                    }
                })
            }
            .build()

        markwon.setMarkdown(textView, processedMarkdown)
        
        // 启用链接点击 - 使用LinkMovementMethod
        // 注意：LinkMovementMethod会与textIsSelectable冲突，需要权衡
        // 如果启用了textIsSelectable，链接点击可能不灵敏
        textView.movementMethod = LinkMovementMethod.getInstance()
    }

    /**
     * 提取标题位置用于锚点跳转
     * 分析Markdown源码中的标题，记录其位置
     */
    private fun extractHeaderPositions(markdown: String) {
        headerPositions.clear()
        
        val headerRegex = Regex("^(#{1,6})\\s+(.+?)(?:\\s+\\[#([^\\]]+)\\])?$", RegexOption.MULTILINE)
        val lines = markdown.lines()
        var currentPos = 0
        
        lines.forEach { line ->
            val match = headerRegex.find(line)
            if (match != null) {
                val title = match.groupValues[2].trim()
                val manualAnchor = match.groupValues[3].takeIf { it.isNotEmpty() }
                
                // 自动生成锚点ID
                val autoAnchorId = generateAnchorId(title)
                if (autoAnchorId.isNotEmpty()) {
                    headerPositions[autoAnchorId] = currentPos
                }
                
                // 如果有手动定义的锚点，也记录
                if (manualAnchor != null) {
                    headerPositions[manualAnchor.lowercase()] = currentPos
                }
            }
            currentPos += line.length + 1 // +1 for newline
        }
    }

    // 存储标题位置映射（标题文本 -> 字符偏移量）
    private val headerPositions = mutableMapOf<String, Int>()

    // 存储锚点位置映射（已废弃，使用headerPositions）
    private val anchorPositions = mutableMapOf<String, Int>()

    /**
     * 获取锚点位置映射
     * 返回标题位置映射，用于锚点跳转
     */
    fun getAnchorPositions(): Map<String, Int> = headerPositions.toMap()

    /**
     * 获取标题位置映射
     */
    fun getHeaderPositions(): Map<String, Int> = headerPositions.toMap()

    /**
     * 去除HTML标签，获取纯文本
     */
    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), "")  // 去除HTML标签
            .replace("&#8203;", "")          // 去除零宽字符
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
    }

    /**
     * 预处理Markdown文本，根据方言转换特有语法
     * 这是关键方法，将各方言的特有语法转换为可渲染的格式
     */
    private fun preprocessMarkdown(markdown: String, flavor: MarkdownFlavor): String {
        // 首先处理裸URL，确保所有URL都能被点击
        var result = convertBareUrlsToLinks(markdown)
        
        return when (flavor) {
            MarkdownFlavor.MULTIMARKDOWN -> preprocessMultiMarkdown(result)
            MarkdownFlavor.MARKDOWN_EXTRA -> preprocessMarkdownExtra(result)
            MarkdownFlavor.PANDOC -> preprocessPandoc(result)
            else -> result
        }
    }
    
    /**
     * 将裸URL转换为Markdown链接格式
     * 确保所有URL都能被点击
     */
    private fun convertBareUrlsToLinks(markdown: String): String {
        // 更精确的URL匹配模式
        val urlRegex = Regex(
            "(?<![\\[\\(])(?<![\\w./])" +  // 确保前面没有 [ 或 ( 或 字母数字
            "((?:https?|ftp)://" +  // 协议
            "(?:[\\w-]+\\.)+[\\w-]+" +  // 域名
            "(?::\\d+)?" +  // 可选端口
            "(?:/[\\w./?=#&%+-]*)?)",  // 路径和查询参数
            RegexOption.IGNORE_CASE
        )
        
        // 检查是否在代码块内
        val lines = markdown.lines()
        var inCodeBlock = false
        val resultBuilder = StringBuilder()
        
        for (line in lines) {
            if (line.trim().startsWith("```")) {
                inCodeBlock = !inCodeBlock
            }
            
            if (!inCodeBlock) {
                // 不在代码块内，替换URL
                val processedLine = line.replace(urlRegex) { match ->
                    val url = match.groupValues[1]
                    "[$url]($url)"
                }
                resultBuilder.append(processedLine).append("\n")
            } else {
                resultBuilder.append(line).append("\n")
            }
        }
        
        return resultBuilder.toString().trimEnd()
    }

    /**
     * MultiMarkdown预处理
     * 转换MMD特有语法为可渲染格式
     */
    private fun preprocessMultiMarkdown(markdown: String): String {
        var result = markdown

        // 1. 处理元数据 (Title: / Author: / Date: / Keywords: / Affiliation: / Copyright: / Address: / Abstract: / Base Header Level: / Bibliography: / Citation Style: / CSS: / HTML Header: / HTML Footer: / Language: / LaTeX Header: / LaTeX Footer: / LaTeX Input: / LaTeX Mode: / LaTeX Title: / MMD Footer: / MMD Header: / Quote Attributes: / Transclude Base:)
        result = result.replace(Regex("^(Title|Author|Date|Keywords|Affiliation|Copyright|Address|Abstract|Base Header Level|Bibliography|Citation Style|CSS|HTML Header|HTML Footer|Language|LaTeX Header|LaTeX Footer|LaTeX Input|LaTeX Mode|LaTeX Title|MMD Footer|MMD Header|Quote Attributes|Transclude Base|XHTML Header|XHTML Footer|ODF Header|ODF Footer):\\s*(.+)$", RegexOption.MULTILINE)) {
            val key = it.groupValues[1]
            val value = it.groupValues[2]
            "**$key:** $value  "
        }

        // 2. 处理LaTeX数学公式 \\(...\\) 内联公式
        result = result.replace(Regex("\\\\\\((.+?)\\\\\\)")) {
            val formula = it.groupValues[1]
            "`$formula`"
        }

        // 3. 处理LaTeX数学公式 \\[...\\] 块级公式
        result = result.replace(Regex("\\\\\\[(.+?)\\\\\\]", RegexOption.DOT_MATCHES_ALL)) {
            val formula = it.groupValues[1].trim()
            "```\n$formula\n```"
        }

        // 4. 处理脚注 [^1] 和 [^label]
        result = result.replace(Regex("\\[\\^(\\w+)\\](?!:)")) {
            val label = it.groupValues[1]
            "<sup>[$label]</sup>"
        }

        // 5. 处理脚注定义 [^1]: 脚注内容 (支持缩进续行)
        result = result.replace(Regex("^\\[\\^(\\w+)\\]:\\s*(.+?)(?:\\n\\s+(.+?))*$", RegexOption.MULTILINE)) {
            val label = it.groupValues[1]
            val content = it.groupValues[2]
            val continuation = if (it.groupValues[3].isNotEmpty()) " " + it.groupValues[3].replace("\n", " ") else ""
            "<sup>[$label]</sup>: $content$continuation  "
        }

        // 6. 处理目录 [TOC] / {{TOC}} / {toc}
        result = result.replace(Regex("\\[TOC\\]|\\{\\{TOC\\}\\}|\\{toc\\}", RegexOption.IGNORE_CASE)) {
            "*Table of Contents*  "
        }

        // 7. 处理交叉引用 [#label] -> 转换为标准Markdown链接格式
        // 锚点跳转由 extractHeaderPositions 方法处理
        result = result.replace(Regex("\\[#([^\\]]+)\\](?!\\()")) { match ->
            val label = match.groupValues[1].trim()
            val anchorId = label.lowercase()
            "[#$label](#$anchorId)"
        }

        // 8. 处理定义列表 (支持多行定义)
        result = result.replace(Regex("^(.+?)\\n:(?:\\s*(.+?)(?:\\n\\n|$))", RegexOption.MULTILINE)) {
            val term = it.groupValues[1]
            val definition = it.groupValues[2]
            "**$term**  \n$definition  \n"
        }

        // 9. 处理跨引用 [](#label) 或 [text](#label)
        result = result.replace(Regex("\\[(.*?)\\]\\(\\s*#(.+?)\\s*\\)")) {
            val text = it.groupValues[1]
            val label = it.groupValues[2]
            if (text.isEmpty()) "[$label]" else "[$text](#$label)"
        }

        // 10. 处理CriticMarkup批注 {>>...<<}
        result = result.replace(Regex("\\{>>(.+?)<<\\}")) {
            val comment = it.groupValues[1]
            "*(Comment: $comment)*"
        }

        // 11. 处理CriticMarkup高亮 {==...==}
        result = result.replace(Regex("\\{==(.+?)==\\}")) {
            val text = it.groupValues[1]
            "**$text**"
        }

        // 12. 处理CriticMarkup插入 {++...++}
        result = result.replace(Regex("\\{\\+\\+(.+?)\\+\\+\\}")) {
            val text = it.groupValues[1]
            "<ins>$text</ins>"
        }

        // 13. 处理CriticMarkup删除 {--...--}
        result = result.replace(Regex("\\{--(.+?)--\\}")) {
            val text = it.groupValues[1]
            "<del>$text</del>"
        }

        // 14. 处理CriticMarkup替换 {~~old~>new~~} 或 {~~old~new~~}
        result = result.replace(Regex("\\{~~(.+?)~>(.+?)~~\\}")) {
            val old = it.groupValues[1]
            val new = it.groupValues[2]
            "<del>$old</del> <ins>$new</ins>"
        }
        result = result.replace(Regex("\\{~~(.+?)~(.+?)~~\\}")) {
            val old = it.groupValues[1]
            val new = it.groupValues[2]
            "<del>$old</del> <ins>$new</ins>"
        }

        // 15. 处理表格标题 | Table Caption |
        result = result.replace(Regex("^\\|\\s*(Table\\s*[:：]?\\s*.+?)\\s*\\|$", RegexOption.MULTILINE)) {
            val caption = it.groupValues[1]
            "*$caption*  "
        }

        // 16. 处理缩写 (Abbreviations) *[HTML]: Hyper Text Markup Language
        result = result.replace(Regex("\\*\\[(.+?)\\]:\\s*(.+)$", RegexOption.MULTILINE)) {
            val abbr = it.groupValues[1]
            val full = it.groupValues[2]
            "**$abbr**: $full  "
        }

        // 17. 处理文件包含 (File Transclusion) {{file.md}}
        result = result.replace(Regex("\\{\\{(.+?\\.(?:md|markdown|txt))\\}\\}")) {
            val file = it.groupValues[1]
            "*(Include: $file)*"
        }

        // 18. 处理词汇表术语 {{term}}
        result = result.replace(Regex("\\{\\{(.+?)\\}\\}")) {
            val term = it.groupValues[1]
            "**$term**"
        }

        return result
    }

    /**
     * Markdown Extra预处理
     * 转换Markdown Extra特有语法为可渲染格式
     */
    private fun preprocessMarkdownExtra(markdown: String): String {
        var result = markdown

        // 1. 处理标题ID ## Header {#id} 或 ## Header #id
        result = result.replace(Regex("^(#{1,6}\\s+.+?)\\s*\\{\\s*#(.+?)\\s*\\}$", RegexOption.MULTILINE)) {
            val header = it.groupValues[1]
            val id = it.groupValues[2]
            "$header <a name=\"$id\"></a>"
        }

        // 2. 处理闭合标题 ### Header ###
        result = result.replace(Regex("^(#{1,6}\\s+.+?)\\s*#+\\s*$", RegexOption.MULTILINE)) {
            it.groupValues[1]
        }

        // 3. 处理脚注 [^1]
        result = result.replace(Regex("\\[\\^(\\w+)\\](?!:)")) {
            val label = it.groupValues[1]
            "<sup>[$label]</sup>"
        }

        // 4. 处理脚注定义 [^1]: 脚注内容
        result = result.replace(Regex("^\\[\\^(\\w+)\\]:\\s*(.+)$", RegexOption.MULTILINE)) {
            val label = it.groupValues[1]
            val content = it.groupValues[2]
            "<sup>[$label]</sup>: $content  "
        }

        // 5. 处理特殊属性 {.class #id} 在链接和图片上
        result = result.replace(Regex("\\[(.*?)\\]\\((.+?)\\)\\s*\\{\\s*(.+?)\\s*\\}")) {
            val text = it.groupValues[1]
            val url = it.groupValues[2]
            "[$text]($url)"
        }

        result = result.replace(Regex("!\\[(.*?)\\]\\((.+?)\\)\\s*\\{\\s*(.+?)\\s*\\}")) {
            val alt = it.groupValues[1]
            val url = it.groupValues[2]
            "![$alt]($url)"
        }

        // 6. 处理缩写定义 *[HTML]: Hyper Text Markup Language
        result = result.replace(Regex("\\*\\[(.+?)\\]:\\s*(.+)$", RegexOption.MULTILINE)) {
            val abbr = it.groupValues[1]
            val full = it.groupValues[2]
            "**$abbr**: $full  "
        }

        // 7. 处理定义列表
        result = result.replace(Regex("^(.+?)\\n:\\s*(.+)$", RegexOption.MULTILINE)) {
            val term = it.groupValues[1]
            val definition = it.groupValues[2]
            "**$term**  \n$definition  "
        }

        // 8. 处理围栏代码块 ~~~ (替代```)
        result = result.replace(Regex("^~~~\\s*(\\w*)\\s*$", RegexOption.MULTILINE)) {
            val lang = it.groupValues[1]
            if (lang.isEmpty()) "```" else "```$lang"
        }

        // 9. 处理表格对齐语法 (保留原样，Markwon支持)
        // | :--- | :---: | ---: |

        return result
    }

    /**
     * Pandoc预处理
     * 转换Pandoc特有语法为可渲染格式
     */
    private fun preprocessPandoc(markdown: String): String {
        var result = markdown

        // 1. 处理YAML元数据块 ---
        // 支持多行值（使用|或>折叠），和非冒号开头的行
        result = result.replace(Regex("^---\\s*\n(.*?)\n---\\s*\n", RegexOption.DOT_MATCHES_ALL)) {
            val yamlContent = it.groupValues[1]
            val yamlLines = yamlContent.lines()
            val formattedYaml = yamlLines.map { line ->
                when {
                    line.trim().isEmpty() -> ""
                    line.contains(":") -> {
                        val parts = line.split(":", limit = 2)
                        if (parts.size == 2) {
                            val key = parts[0].trim()
                            val value = parts[1].trim()
                            "**$key:** $value  "
                        } else {
                            line
                        }
                    }
                    line.trim().startsWith("-") -> "  $line"
                    else -> line
                }
            }.joinToString("\n")
            "$formattedYaml\n\n"
        }

        // 2. 处理内联YAML元数据 % 开头
        result = result.replace(Regex("^%\\s*(.+)$", RegexOption.MULTILINE)) {
            val content = it.groupValues[1]
            "*$content*  "
        }

        // 3. 处理LaTeX数学公式 $$...$$ 块级公式
        result = result.replace(Regex("\\$\\$\\s*\\n?(.+?)\\n?\\$\\$", RegexOption.DOT_MATCHES_ALL)) {
            val formula = it.groupValues[1].trim()
            "```\n$formula\n```"
        }

        // 4. 处理LaTeX数学公式 $...$ 内联公式
        result = result.replace(Regex("\\$([^\\$\\n]+?)\\$")) {
            val formula = it.groupValues[1]
            "`$formula`"
        }

        // 5. 处理脚注 [^1]
        result = result.replace(Regex("\\[\\^(\\w+)\\](?!:)")) {
            val label = it.groupValues[1]
            "<sup>[$label]</sup>"
        }

        // 6. 处理脚注定义 [^1]: 脚注内容
        result = result.replace(Regex("^\\[\\^(\\w+)\\]:\\s*(.+)$", RegexOption.MULTILINE)) {
            val label = it.groupValues[1]
            val content = it.groupValues[2]
            "<sup>[$label]</sup>: $content  "
        }

        // 7. 处理引用 @cite
        result = result.replace(Regex("@([A-Za-z][A-Za-z0-9_-]*)")) {
            val citeKey = it.groupValues[1]
            "[@$citeKey]"
        }

        // 8. 处理引用链接 [@cite] 或 [@cite, p. 123]
        result = result.replace(Regex("\\[@([A-Za-z][A-Za-z0-9_-]*)(?:\\s*,\\s*[^\\]]+)?\\]")) {
            val citeKey = it.groupValues[1]
            "<sup>[$citeKey]</sup>"
        }

        // 9. 处理上标 ^text^ (注意：不是脚注)
        result = result.replace(Regex("(?<!\\[)\\^([^\\^\\n]+?)\\^(?!\\])")) {
            val text = it.groupValues[1]
            "<sup>$text</sup>"
        }

        // 10. 处理下标 ~text~ (注意：不是删除线)
        // Pandoc规范：单个~包裹为下标，双~~包裹为删除线(但删除线由StrikethroughPlugin处理)
        result = result.replace(Regex("(?<!~)~([^~\\n]+?)~(?!~)")) {
            "<sub>${it.groupValues[1]}</sub>"
        }

        // 11. 处理围栏属性 ``` {.java #code-example}
        result = result.replace(Regex("```\\s*\\{([^}]+)\\}")) {
            val attrs = it.groupValues[1]
            val parts = attrs.split(" ")
            val lang = parts.firstOrNull()?.replace(".", "") ?: ""
            val remainingAttrs = if (parts.size > 1) " <!-- ${parts.drop(1).joinToString(" ")} -->" else ""
            "```$lang$remainingAttrs"
        }

        // 12. 处理Fenced divs ::: {.class}
        result = result.replace(Regex("^:::\\s*\\{?(.+?)\\}?\\s*$", RegexOption.MULTILINE)) {
            val attrs = it.groupValues[1]
            if (attrs == ":" || attrs.isEmpty()) {
                "---"
            } else {
                "<!-- div: $attrs -->"
            }
        }

        // 13. 处理Wiki链接 [[Page Title]]
        result = result.replace(Regex("\\[\\[([^\\]]+)\\]\\]")) {
            val title = it.groupValues[1]
            "[$title](#$title)"
        }

        // 14. 处理+/-列表标记
        result = result.replace(Regex("^(\\s*)\\+(\\s+)", RegexOption.MULTILINE)) {
            val indent = it.groupValues[1]
            val space = it.groupValues[2]
            "${indent}-$space"
        }

        // 15. 处理行块 (Line blocks) | 用于诗歌/地址
        // 行块需要空行前置，且不是表格（表格前面是|---+之类的分隔线）
        result = result.replace(Regex("(?<=\n\n)\\|\\s*(.+)$", RegexOption.MULTILINE)) {
            val line = it.groupValues[1]
            "$line  "
        }

        // 16. 处理示例列表 (@) 或 (@good)
        result = result.replace(Regex("^\\(@\\w*\\)")) {
            "-"
        }

        // 17. 处理原始HTML块
        result = result.replace(Regex("^\\{\\{html\\}\\}(.+?)\\{\\{/html\\}\\}$", RegexOption.DOT_MATCHES_ALL)) {
            val html = it.groupValues[1]
            html
        }

        // 18. 处理原始LaTeX块
        result = result.replace(Regex("^\\{\\{latex\\}\\}(.+?)\\{\\{/latex\\}\\}$", RegexOption.DOT_MATCHES_ALL)) {
            val latex = it.groupValues[1]
            "```\n$latex\n```"
        }

        // 19. 处理Grid tables (简化处理)
        // 保留原样，Markwon可能部分支持

        return result
    }

    /**
     * 获取支持的语法列表
     */
    fun getSupportedFlavors(): List<MarkdownFlavor> {
        return MarkdownFlavor.values().toList()
    }

    /**
     * 检查特定语法是否支持某功能
     */
    fun supportsFeature(feature: MarkdownFeature): Boolean {
        return when (currentFlavor) {
            MarkdownFlavor.GFM -> {
                when (feature) {
                    MarkdownFeature.BASIC,
                    MarkdownFeature.TABLE,
                    MarkdownFeature.TASK_LIST,
                    MarkdownFeature.STRIKETHROUGH,
                    MarkdownFeature.AUTO_LINK,
                    MarkdownFeature.IMAGE,
                    MarkdownFeature.HTML,
                    MarkdownFeature.FENCED_CODE,
                    MarkdownFeature.EMOJI -> true
                    else -> false
                }
            }

            MarkdownFlavor.COMMONMARK, MarkdownFlavor.ORIGINAL -> {
                when (feature) {
                    MarkdownFeature.BASIC,
                    MarkdownFeature.IMAGE,
                    MarkdownFeature.HTML -> true
                    else -> false
                }
            }

            MarkdownFlavor.MULTIMARKDOWN -> {
                when (feature) {
                    MarkdownFeature.BASIC,
                    MarkdownFeature.TABLE,
                    MarkdownFeature.STRIKETHROUGH,
                    MarkdownFeature.IMAGE,
                    MarkdownFeature.HTML,
                    MarkdownFeature.FOOTNOTE,
                    MarkdownFeature.TOC,
                    MarkdownFeature.METADATA,
                    MarkdownFeature.DEFINITION_LIST,
                    MarkdownFeature.CRITIC_MARKUP,
                    MarkdownFeature.MATH_LATEX,
                    MarkdownFeature.FILE_TRANSCLUSION,
                    MarkdownFeature.GLOSSARY -> true
                    else -> false
                }
            }

            MarkdownFlavor.MARKDOWN_EXTRA -> {
                when (feature) {
                    MarkdownFeature.BASIC,
                    MarkdownFeature.TABLE,
                    MarkdownFeature.STRIKETHROUGH,
                    MarkdownFeature.IMAGE,
                    MarkdownFeature.HTML,
                    MarkdownFeature.FOOTNOTE,
                    MarkdownFeature.HEADER_ID,
                    MarkdownFeature.FENCED_CODE,
                    MarkdownFeature.ABBREVIATION,
                    MarkdownFeature.DEFINITION_LIST,
                    MarkdownFeature.CLOSED_HEADER,
                    MarkdownFeature.TABLE_ALIGNMENT -> true
                    else -> false
                }
            }

            MarkdownFlavor.PANDOC -> {
                when (feature) {
                    MarkdownFeature.BASIC,
                    MarkdownFeature.TABLE,
                    MarkdownFeature.STRIKETHROUGH,
                    MarkdownFeature.AUTO_LINK,
                    MarkdownFeature.IMAGE,
                    MarkdownFeature.HTML,
                    MarkdownFeature.FENCED_CODE,
                    MarkdownFeature.FOOTNOTE,
                    MarkdownFeature.YAML_METADATA,
                    MarkdownFeature.CITATION,
                    MarkdownFeature.SUPERSCRIPT,
                    MarkdownFeature.SUBSCRIPT,
                    MarkdownFeature.MATH_LATEX,
                    MarkdownFeature.FENCED_DIVS,
                    MarkdownFeature.WIKI_LINK,
                    MarkdownFeature.LINE_BLOCK,
                    MarkdownFeature.EXAMPLE_LIST,
                    MarkdownFeature.RAW_HTML,
                    MarkdownFeature.RAW_LATEX -> true
                    else -> false
                }
            }
        }
    }

    /**
     * 生成锚点ID
     * 将标题转换为适合作为HTML锚点的格式
     */
    private fun generateAnchorId(title: String): String {
        return title.lowercase()
            .replace(Regex("\\s+"), "-")           // 空格替换为连字符
            .replace(Regex("[^a-z0-9\\-]"), "")    // 移除非字母数字和连字符
            .replace(Regex("^-+|-+$"), "")         // 移除开头和结尾的连字符
            .replace(Regex("-+$"), "")             // 移除结尾的连字符
    }

    /**
     * 重新创建渲染器（用于配置更改后）
     */
    fun recreate() {
        markwonInstances.clear()
        MarkdownFlavor.values().forEach { flavor ->
            markwonInstances[flavor] = createMarkwon(flavor)
        }
    }
}

/**
 * Markdown功能特性枚举
 */
enum class MarkdownFeature {
    BASIC,              // 基础语法
    TABLE,              // 表格
    TASK_LIST,          // 任务列表
    STRIKETHROUGH,      // 删除线
    AUTO_LINK,          // 自动链接
    IMAGE,              // 图片
    HTML,               // HTML标签
    FENCED_CODE,        // 围栏代码块
    EMOJI,              // 表情符号
    FOOTNOTE,           // 脚注[^1]
    TOC,                // 目录[TOC]
    METADATA,           // 元数据(Title:)
    YAML_METADATA,      // YAML元数据(---)
    HEADER_ID,          // 标题ID(## {#id})
    CLOSED_HEADER,      // 闭合标题(### Header ###)
    ABBREVIATION,       // 缩写*[HTML]: ...
    CITATION,           // 引用[@cite]
    SUPERSCRIPT,        // 上标^text^
    SUBSCRIPT,          // 下标~text~
    MATH_LATEX,         // LaTeX数学公式($...$ $$...$$ \(...\) \[...\])
    FENCED_DIVS,        // Fenced divs(:::
    WIKI_LINK,          // Wiki链接[[...]]
    CRITIC_MARKUP,      // CriticMarkup({>>...<<} {==...==} {++...++} {--...--})
    DEFINITION_LIST,    // 定义列表
    TABLE_ALIGNMENT,    // 表格对齐
    FILE_TRANSCLUSION,  // 文件包含{{file.md}}
    GLOSSARY,           // 词汇表{{term}}
    LINE_BLOCK,         // 行块| 用于诗歌
    EXAMPLE_LIST,       // 示例列表(@)
    RAW_HTML,           // 原始HTML{{html}}...{{/html}}
    RAW_LATEX           // 原始LaTeX{{latex}}...{{/latex}}
}

/**
 * 自定义MovementMethod，用于在NestedScrollView中正确处理链接点击
 * 继承自LinkMovementMethod，确保链接可点击的同时不影响滚动
 */
class NestedScrollViewLinkMovementMethod : LinkMovementMethod() {
    
    companion object {
        private var instance: NestedScrollViewLinkMovementMethod? = null
        
        fun getInstance(): NestedScrollViewLinkMovementMethod {
            if (instance == null) {
                instance = NestedScrollViewLinkMovementMethod()
            }
            return instance!!
        }
    }
}
