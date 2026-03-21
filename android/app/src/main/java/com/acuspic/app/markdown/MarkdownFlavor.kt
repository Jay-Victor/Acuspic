package com.acuspic.app.markdown

/**
 * Markdown语法标准枚举
 * 按流行度和实用性排序
 * features列表与实际渲染能力保持一致
 *
 * 六种方言的完整渲染能力及特有语法符号：
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
 *    - 特有语法：脚注[^1]、目录[TOC]、元数据(Title:)、定义列表、LaTeX数学公式(\(...\) \[...\])、CriticMarkup
 *    - 渲染：表格+删除线，特有语法通过预处理转换
 *
 * 5. Markdown Extra
 *    - 特有语法：脚注[^1]、围栏代码块(```/~~~)、标题ID(## {#id})、定义列表、缩写、闭合标题
 *    - 渲染：表格+删除线+围栏代码块，特有语法通过预处理转换
 *
 * 6. Pandoc's Markdown
 *    - 特有语法：YAML元数据(---)、脚注[^1]、引用[@cite]、LaTeX数学公式($...$ $$...$$)、定义列表、上标^text^、下标~text~、Fenced divs、Wiki链接
 *    - 渲染：表格+删除线+自动链接，特有语法通过预处理转换
 */
enum class MarkdownFlavor(
    val displayName: String,
    val description: String,
    val features: List<String>
) {
    GFM(
        displayName = "GitHub Flavored Markdown",
        description = "GitHub官方标准，最流行的Markdown方言，支持最完整的扩展",
        features = listOf(
            "✓ 标准Markdown语法",
            "✓ 表格支持 (| 表头 |)",
            "✓ 任务列表 (- [x] 任务)",
            "✓ 删除线 (~~文字~~)",
            "✓ 自动链接 (URL自动识别)",
            "✓ 围栏代码块 (```)",
            "✓ 表情符号 (:emoji:)",
            "✓ 图片渲染",
            "✓ HTML标签"
        )
    ),

    COMMONMARK(
        displayName = "CommonMark",
        description = "Markdown标准化规范，最严格的语法，无扩展",
        features = listOf(
            "✓ 标准化Markdown语法",
            "✓ 严格的解析规则",
            "✓ 跨平台兼容",
            "✓ 缩进代码块",
            "✓ 图片渲染",
            "✓ HTML标签",
            "✗ 表格",
            "✗ 任务列表",
            "✗ 删除线",
            "✗ 围栏代码块"
        )
    ),

    ORIGINAL(
        displayName = "Original Markdown (Gruber)",
        description = "John Gruber创建的原始Markdown规范(2004)，无任何扩展",
        features = listOf(
            "✓ 原始Markdown语法",
            "✓ 标题、列表、链接",
            "✓ 强调、代码",
            "✓ 引用、图片",
            "✓ 水平分隔线",
            "✓ HTML标签",
            "✗ 表格",
            "✗ 删除线",
            "✗ 围栏代码块"
        )
    ),

    MULTIMARKDOWN(
        displayName = "MultiMarkdown (MMD)",
        description = "学术写作扩展，支持LaTeX数学公式、CriticMarkup、脚注等特有语法",
        features = listOf(
            "✓ 标准Markdown语法",
            "✓ 表格支持 (| 表头 |)",
            "✓ 删除线 (~~文字~~)",
            "✓ LaTeX数学公式 (\\(...\\) \\[...\\])",
            "✓ 脚注 [^1] (预处理转换)",
            "✓ 元数据 (Title: / Author: / Date:)",
            "✓ 目录 [TOC] / {{TOC}}",
            "✓ 定义列表 (术语\\n: 定义)",
            "✓ CriticMarkup ({>>批注<<} {==高亮==} {++插入++} {--删除--})",
            "✓ 跨引用 [](#label)",
            "✓ 图片渲染",
            "✓ HTML标签",
            "✗ 任务列表",
            "✗ 自动链接"
        )
    ),

    MARKDOWN_EXTRA(
        displayName = "Markdown Extra",
        description = "PHP Markdown扩展，支持围栏代码块、标题ID、缩写等特有语法",
        features = listOf(
            "✓ 标准Markdown语法",
            "✓ 表格支持 (| 表头 |)",
            "✓ 删除线 (~~文字~~)",
            "✓ 围栏代码块 (``` 或 ~~~)",
            "✓ 脚注 [^1] (预处理转换)",
            "✓ 标题ID (## {#id})",
            "✓ 闭合标题 (### Header ###)",
            "✓ 缩写定义 *[HTML]: ...",
            "✓ 定义列表 (术语\\n: 定义)",
            "✓ 特殊属性 {.class #id}",
            "✓ 图片渲染",
            "✓ HTML标签",
            "✗ 任务列表",
            "✗ 自动链接"
        )
    ),

    PANDOC(
        displayName = "Pandoc's Markdown",
        description = "文档转换器方言，支持LaTeX数学公式、YAML元数据、引用等特有语法",
        features = listOf(
            "✓ 标准Markdown语法",
            "✓ 表格支持 (| 表头 |)",
            "✓ 删除线 (~~文字~~)",
            "✓ 自动链接 (URL自动识别)",
            "✓ 围栏代码块 (```)",
            "✓ LaTeX数学公式 ($...$ $$...$$)",
            "✓ YAML元数据 (---)",
            "✓ 内联元数据 (% 标题)",
            "✓ 脚注 [^1] (预处理转换)",
            "✓ 引用 [@cite] / @cite",
            "✓ 上标 ^text^",
            "✓ 下标 ~text~",
            "✓ Fenced divs (:::",
            "✓ Wiki链接 [[Page Title]]",
            "✓ +/- 列表标记",
            "✓ 围栏属性 ``` {.java}",
            "✓ 图片渲染",
            "✓ HTML标签",
            "✗ 任务列表"
        )
    );

    companion object {
        /**
         * 获取默认语法
         */
        fun default(): MarkdownFlavor = GFM

        /**
         * 通过名称获取
         */
        fun fromName(name: String): MarkdownFlavor? {
            return values().find { it.name == name }
        }
    }
}
