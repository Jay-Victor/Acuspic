package com.acuspic.app.imagehost

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 图床上传管理器
 */
class ImageHostUploader(private val context: Context) {

    private val boundary = "----WebKitFormBoundary${System.currentTimeMillis()}"

    /**
     * 上传图片到图床
     */
    fun uploadImage(
        uri: Uri,
        config: ImageHostConfig,
        fileName: String? = null
    ): Flow<UploadResult> = flow {
        try {
            val result = when (config.type) {
                ImageHostType.GITEE -> uploadToGitee(uri, config, fileName)
                ImageHostType.QINIU -> uploadToQiniu(uri, config, fileName)
                ImageHostType.SMMS -> uploadToSMMS(uri, config)
                ImageHostType.IMGUR -> uploadToImgur(uri, config)
                ImageHostType.GITHUB -> uploadToGitHub(uri, config, fileName)
                ImageHostType.CUSTOM -> uploadToCustom(uri, config)
                ImageHostType.NONE -> UploadResult.Error("未配置图床")
            }
            emit(result)
        } catch (e: Exception) {
            emit(UploadResult.Error("上传失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 上传多张图片
     */
    fun uploadImages(
        uris: List<Uri>,
        config: ImageHostConfig
    ): Flow<Pair<UploadProgress, UploadResult?>> = flow {
        uris.forEachIndexed { index, uri ->
            val progress = UploadProgress(
                current = index + 1,
                total = uris.size,
                fileName = uri.lastPathSegment ?: "image_$index"
            )

            var result: UploadResult? = null
            uploadImage(uri, config, progress.fileName).collect { uploadResult ->
                result = uploadResult
            }

            emit(progress to result)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 上传到 Gitee 码云
     */
    private suspend fun uploadToGitee(
        uri: Uri,
        config: ImageHostConfig,
        fileName: String?
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val actualFileName = fileName ?: "image_$timestamp.png"
                val path = "images/${actualFileName}"

                val url = URL("https://gitee.com/api/v5/repos/${config.repo}/contents/$path")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Content-Type", "application/json")
                }

                // 读取图片并转为 Base64
                val base64Content = uriToBase64(uri)

                val jsonBody = JSONObject().apply {
                    put("access_token", config.apiKey)
                    put("content", base64Content)
                    put("message", "Upload image via Acuspic")
                    put("branch", config.branch)
                }

                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonObject = JSONObject(response)
                    val rawUrl = jsonObject.getString("raw_url")
                    // Gitee 原始链接直接可用
                    UploadResult.Success(url = rawUrl)
                } else {
                    UploadResult.Error("Gitee 上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("Gitee 上传失败: ${e.message}")
            }
        }
    }

    /**
     * 上传到七牛云
     */
    private suspend fun uploadToQiniu(
        uri: Uri,
        config: ImageHostConfig,
        fileName: String?
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                // 获取上传凭证
                val uploadToken = generateQiniuUploadToken(config)

                val url = URL("https://upload.qiniup.com")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                val actualFileName = fileName ?: "image_${System.currentTimeMillis()}.png"
                val key = "images/${actualFileName}"

                connection.outputStream.use { outputStream ->
                    // 写入 token
                    writeFormField(outputStream, "token", uploadToken, boundary)
                    // 写入 key
                    writeFormField(outputStream, "key", key, boundary)
                    // 写入文件
                    writeFileData(outputStream, uri, "file", boundary)
                }

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(response)
                    val fileKey = jsonObject.getString("key")
                    val domain = config.customDomain.takeIf { it.isNotEmpty() } 
                        ?: "https://${config.bucket}.qiniudn.com"
                    val imageUrl = "$domain/$fileKey"
                    UploadResult.Success(url = imageUrl)
                } else {
                    UploadResult.Error("七牛云上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("七牛云上传失败: ${e.message}")
            }
        }
    }

    /**
     * 生成七牛云上传凭证
     */
    private fun generateQiniuUploadToken(config: ImageHostConfig): String {
        val accessKey = config.apiKey
        val secretKey = config.apiSecret
        val bucket = config.bucket

        // 构建上传策略
        val putPolicy = JSONObject().apply {
            put("scope", bucket)
            put("deadline", System.currentTimeMillis() / 1000 + 3600) // 1小时有效期
        }

        val encodedPutPolicy = Base64.getEncoder().encodeToString(
            putPolicy.toString().toByteArray()
        ).replace("+", "-").replace("/", "_")

        // 生成签名
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretKey.toByteArray(), "HmacSHA1"))
        val sign = Base64.getEncoder().encodeToString(
            mac.doFinal(encodedPutPolicy.toByteArray())
        ).replace("+", "-").replace("/", "_")

        return "$accessKey:$sign:$encodedPutPolicy"
    }

    /**
     * 上传到 SM.MS 图床
     */
    private suspend fun uploadToSMMS(uri: Uri, config: ImageHostConfig): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://sm.ms/api/v2/upload")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    useCaches = false
                    setRequestProperty("Authorization", config.apiKey)
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                connection.outputStream.use { outputStream ->
                    writeFileData(outputStream, uri, "smfile", boundary)
                }

                val responseCode = connection.responseCode
                val response = if (responseCode == HttpURLConnection.HTTP_OK) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }

                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getBoolean("success")) {
                        val data = jsonObject.getJSONObject("data")
                        UploadResult.Success(
                            url = data.getString("url"),
                            deleteUrl = data.optString("delete", "")
                        )
                    } else {
                        UploadResult.Error(jsonObject.getString("message"))
                    }
                } else {
                    UploadResult.Error("上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("SM.MS 上传失败: ${e.message}")
            }
        }
    }

    /**
     * 上传到 Imgur 图床
     */
    private suspend fun uploadToImgur(uri: Uri, config: ImageHostConfig): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL("https://api.imgur.com/3/image")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Authorization", "Client-ID ${config.apiKey}")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                }

                val base64Image = uriToBase64(uri)

                connection.outputStream.use { outputStream ->
                    val postData = "image=${URLEncoder.encode(base64Image, "UTF-8")}"
                    outputStream.write(postData.toByteArray())
                }

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(response)
                    if (jsonObject.getBoolean("success")) {
                        val data = jsonObject.getJSONObject("data")
                        UploadResult.Success(
                            url = data.getString("link"),
                            deleteUrl = data.optString("deletehash", "")
                        )
                    } else {
                        UploadResult.Error("Imgur 上传失败")
                    }
                } else {
                    UploadResult.Error("上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("Imgur 上传失败: ${e.message}")
            }
        }
    }

    /**
     * 上传到 GitHub 图床
     */
    private suspend fun uploadToGitHub(
        uri: Uri,
        config: ImageHostConfig,
        fileName: String?
    ): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val timestamp = System.currentTimeMillis()
                val actualFileName = fileName ?: "image_$timestamp.png"
                val path = "images/${actualFileName}"

                val url = URL("https://api.github.com/repos/${config.repo}/contents/$path")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "PUT"
                    doOutput = true
                    doInput = true
                    setRequestProperty("Authorization", "token ${config.apiKey}")
                    setRequestProperty("Content-Type", "application/json")
                }

                val base64Content = uriToBase64(uri)

                val jsonBody = JSONObject().apply {
                    put("message", "Upload image via Acuspic")
                    put("content", base64Content)
                    put("branch", config.branch)
                }

                connection.outputStream.use { outputStream ->
                    outputStream.write(jsonBody.toString().toByteArray())
                }

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    val jsonObject = JSONObject(response)
                    val content = jsonObject.getJSONObject("content")
                    val rawUrl = content.getString("download_url")
                    val cdnUrl = rawUrl.replace(
                        "https://raw.githubusercontent.com",
                        "https://cdn.jsdelivr.net/gh"
                    ).replace("/main/", "@main/").replace("/master/", "@master/")

                    UploadResult.Success(url = cdnUrl)
                } else {
                    UploadResult.Error("GitHub 上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("GitHub 上传失败: ${e.message}")
            }
        }
    }

    /**
     * 上传到自定义图床
     */
    private suspend fun uploadToCustom(uri: Uri, config: ImageHostConfig): UploadResult {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(config.customUploadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    doOutput = true
                    doInput = true
                    useCaches = false
                    setRequestProperty("Authorization", "Bearer ${config.apiKey}")
                    setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                }

                connection.outputStream.use { outputStream ->
                    writeFileData(outputStream, uri, "file", boundary)
                }

                val responseCode = connection.responseCode
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                connection.disconnect()

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(response)
                    val imageUrl = jsonObject.optString("url")
                        .takeIf { it.isNotEmpty() }
                        ?: jsonObject.optJSONObject("data")?.optString("url")
                        ?: jsonObject.optString("link")

                    if (imageUrl.isNotEmpty()) {
                        UploadResult.Success(url = imageUrl)
                    } else {
                        UploadResult.Error("无法解析返回的URL")
                    }
                } else {
                    UploadResult.Error("上传失败: HTTP $responseCode")
                }
            } catch (e: Exception) {
                UploadResult.Error("自定义图床上传失败: ${e.message}")
            }
        }
    }

    /**
     * 将 URI 转为 Base64
     */
    private fun uriToBase64(uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            val bytes = inputStream.readBytes()
            Base64.getEncoder().encodeToString(bytes)
        } ?: throw IllegalArgumentException("无法读取文件")
    }

    /**
     * 写入表单字段
     */
    private fun writeFormField(
        outputStream: java.io.OutputStream,
        name: String,
        value: String,
        boundary: String
    ) {
        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
        outputStream.write("$value\r\n".toByteArray())
    }

    /**
     * 写入文件数据到输出流
     */
    private fun writeFileData(
        outputStream: java.io.OutputStream,
        uri: Uri,
        fieldName: String,
        boundary: String
    ) {
        val fileName = uri.lastPathSegment ?: "image.png"

        outputStream.write("--$boundary\r\n".toByteArray())
        outputStream.write("Content-Disposition: form-data; name=\"$fieldName\"; filename=\"$fileName\"\r\n".toByteArray())
        outputStream.write("Content-Type: image/*\r\n\r\n".toByteArray())

        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            inputStream.copyTo(outputStream)
        }

        outputStream.write("\r\n".toByteArray())
        outputStream.write("--$boundary--\r\n".toByteArray())
    }

    /**
     * 获取文件大小
     */
    fun getFileSize(uri: Uri): Long {
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use {
                it.statSize
            } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * 检查文件是否为图片
     */
    fun isImageFile(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        return mimeType?.startsWith("image/") == true
    }
}
