package com.acuspic.app.imagehost

/**
 * 图床类型枚举
 */
enum class ImageHostType(
    val displayName: String,
    val description: String,
    val needApiKey: Boolean,
    val needSecret: Boolean = false,
    val needCustomDomain: Boolean = false,
    val needRepo: Boolean = false,
    val needBucket: Boolean = false
) {
    GITEE(
        displayName = "Gitee 码云",
        description = "国内访问快，免费1GB空间",
        needApiKey = true,
        needRepo = true
    ),
    QINIU(
        displayName = "七牛云",
        description = "国内CDN加速，免费10GB",
        needApiKey = true,
        needSecret = true,
        needBucket = true,
        needCustomDomain = true
    ),
    SMMS(
        displayName = "SM.MS 图床",
        description = "免费稳定，5MB/张，5G空间",
        needApiKey = true
    ),
    IMGUR(
        displayName = "Imgur 图床",
        description = "国际知名图床，全球访问",
        needApiKey = true
    ),
    GITHUB(
        displayName = "GitHub 图床",
        description = "开发者首选，需配置仓库",
        needApiKey = true,
        needRepo = true
    ),
    CUSTOM(
        displayName = "自定义图床",
        description = "支持自定义上传接口",
        needApiKey = true,
        needCustomDomain = true
    ),
    NONE(
        displayName = "不使用图床",
        description = "图片使用本地路径",
        needApiKey = false
    );

    companion object {
        fun default() = GITEE

        fun fromName(name: String): ImageHostType? {
            return values().find { it.name == name }
        }
    }
}
