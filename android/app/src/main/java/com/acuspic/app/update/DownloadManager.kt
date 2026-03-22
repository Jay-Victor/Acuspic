package com.acuspic.app.update

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 下载管理器
 * 负责文件下载、进度跟踪、断点续传等功能
 */
class DownloadManager(private val context: Context) {

    companion object {
        private const val TAG = "DownloadManager"
        private const val BUFFER_SIZE = 8192
        private const val CONNECT_TIMEOUT = 30000
        private const val READ_TIMEOUT = 30000
        private const val MAX_REDIRECTS = 5
    }

    private var isCancelled = false
    private var currentConnection: HttpURLConnection? = null

    private fun getDownloadDir(): File {
        val dir = File(context.getExternalFilesDir(null), "Acuspic/Downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 下载文件
     * @param url 下载地址
     * @param fileName 文件名
     * @param showProgress 是否显示进度
     * @return 下载状态流
     */
    fun download(url: String, fileName: String, showProgress: Boolean = true): Flow<DownloadStatus> = flow {
        isCancelled = false
        
        emit(DownloadStatus.Connecting)
        
        try {
            val file = File(getDownloadDir(), fileName)
            
            val finalUrl = followRedirects(url)
            Log.d(TAG, "最终下载地址: $finalUrl")
            
            val connection = URL(finalUrl).openConnection() as HttpURLConnection
            currentConnection = connection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT
                readTimeout = READ_TIMEOUT
                setRequestProperty("User-Agent", "Acuspic-Android-App")
                setRequestProperty("Accept", "*/*")
            }
            
            connection.connect()
            
            val responseCode = connection.responseCode
            Log.d(TAG, "HTTP响应码: $responseCode")
            
            if (responseCode != HttpURLConnection.HTTP_OK) {
                val errorStream = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                Log.e(TAG, "下载失败: HTTP $responseCode, $errorStream")
                emit(DownloadStatus.Error("服务器返回错误: HTTP $responseCode"))
                return@flow
            }
            
            val contentLength = connection.contentLengthLong
            Log.i(TAG, "文件大小: $contentLength bytes")
            
            if (contentLength <= 0) {
                emit(DownloadStatus.Error("无法获取文件大小"))
                return@flow
            }
            
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(file)
            
            val buffer = ByteArray(BUFFER_SIZE)
            var totalBytesRead = 0L
            var bytesRead: Int
            var lastProgressTime = System.currentTimeMillis()
            var lastBytesRead = 0L
            var speed = ""
            
            emit(DownloadStatus.Progress(0, "正在连接..."))
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                if (isCancelled) {
                    outputStream.close()
                    inputStream.close()
                    file.delete()
                    emit(DownloadStatus.Cancelled)
                    return@flow
                }
                
                outputStream.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                if (showProgress) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastProgressTime >= 500) {
                        val progress = ((totalBytesRead * 100) / contentLength).toInt()
                        val timeDiff = (currentTime - lastProgressTime) / 1000.0
                        val bytesDiff = totalBytesRead - lastBytesRead
                        speed = formatSpeed((bytesDiff / timeDiff).toLong())
                        
                        emit(DownloadStatus.Progress(progress, speed))
                        
                        lastProgressTime = currentTime
                        lastBytesRead = totalBytesRead
                    }
                }
            }
            
            outputStream.flush()
            outputStream.close()
            inputStream.close()
            
            if (totalBytesRead < contentLength) {
                file.delete()
                emit(DownloadStatus.Error("下载不完整"))
                return@flow
            }
            
            Log.i(TAG, "下载完成: ${file.absolutePath}, 大小: ${file.length()} bytes")
            emit(DownloadStatus.Success(file.absolutePath))
            
        } catch (e: Exception) {
            Log.e(TAG, "下载失败: ${e.message}", e)
            if (isCancelled) {
                emit(DownloadStatus.Cancelled)
            } else {
                emit(DownloadStatus.Error(getErrorMessage(e)))
            }
        } finally {
            currentConnection?.disconnect()
            currentConnection = null
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 跟随重定向
     */
    private fun followRedirects(url: String): String {
        var currentUrl = url
        var redirects = 0
        
        while (redirects < MAX_REDIRECTS) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            try {
                connection.connect()
                val responseCode = connection.responseCode
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    val location = connection.getHeaderField("Location")
                    if (location != null) {
                        currentUrl = location
                        redirects++
                        Log.d(TAG, "重定向 #$redirects: $currentUrl")
                        continue
                    }
                }
                
                break
            } finally {
                connection.disconnect()
            }
        }
        
        return currentUrl
    }

    /**
     * 格式化下载速度
     */
    private fun formatSpeed(bytesPerSecond: Long): String {
        return when {
            bytesPerSecond >= 1024 * 1024 -> String.format("%.1f MB/s", bytesPerSecond / (1024.0 * 1024.0))
            bytesPerSecond >= 1024 -> String.format("%.1f KB/s", bytesPerSecond / 1024.0)
            else -> "$bytesPerSecond B/s"
        }
    }

    /**
     * 获取友好的错误信息
     */
    private fun getErrorMessage(e: Exception): String {
        return when (e) {
            is java.net.SocketTimeoutException -> "连接超时，请检查网络"
            is java.net.UnknownHostException -> "无法连接服务器，请检查网络"
            is java.net.ConnectException -> "网络连接失败"
            is java.io.IOException -> "网络错误: ${e.message}"
            else -> "下载失败: ${e.message}"
        }
    }

    /**
     * 取消下载
     */
    fun cancel() {
        isCancelled = true
        currentConnection?.disconnect()
        currentConnection = null
    }

    /**
     * 获取已下载的文件
     */
    fun getDownloadedFile(fileName: String): File? {
        val file = File(getDownloadDir(), fileName)
        return if (file.exists()) file else null
    }

    /**
     * 删除已下载的文件
     */
    fun deleteDownloadedFile(fileName: String) {
        val file = File(getDownloadDir(), fileName)
        if (file.exists()) {
            file.delete()
            Log.i(TAG, "已删除文件: $fileName")
        }
    }

    /**
     * 获取下载目录大小
     */
    fun getDownloadDirSize(): Long {
        val dir = getDownloadDir()
        if (!dir.exists()) return 0
        
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
    }

    /**
     * 清空下载目录
     */
    fun clearDownloadDir() {
        val dir = getDownloadDir()
        if (dir.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            Log.i(TAG, "已清空下载目录")
        }
    }
}
