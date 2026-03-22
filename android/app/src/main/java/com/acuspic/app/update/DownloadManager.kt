package com.acuspic.app.update

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat

/**
 * 下载管理器
 * 支持断点续传、进度回调、速度计算
 */
class DownloadManager(private val context: Context) {

    companion object {
        // 下载目录
        private const val DOWNLOAD_DIR = "Acuspic/Downloads"
        // 缓冲区大小 8KB
        private const val BUFFER_SIZE = 8192
        // 进度更新间隔（毫秒）
        private const val PROGRESS_INTERVAL = 500L
    }

    private var isCancelled = false
    private var currentConnection: HttpURLConnection? = null

    /**
     * 下载文件
     * @param url 下载地址
     * @param fileName 文件名
     * @param enableResume 是否启用断点续传
     * @return 下载状态流
     */
    fun download(
        url: String,
        fileName: String,
        enableResume: Boolean = true
    ): Flow<DownloadStatus> = flow {
        isCancelled = false
        
        try {
            val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
            if (!downloadDir.exists()) {
                downloadDir.mkdirs()
            }
            
            val file = File(downloadDir, fileName)
            val tempFile = File(downloadDir, "$fileName.tmp")
            
            // 已下载的字节数
            var downloadedBytes = if (enableResume && tempFile.exists()) tempFile.length() else 0L
            
            // 获取文件信息
            val (totalBytes, supportsResume) = getFileInfo(url)
            
            if (totalBytes <= 0) {
                emit(DownloadStatus.Error("无法获取文件信息"))
                return@flow
            }
            
            // 如果文件已完整下载
            if (file.exists() && file.length() == totalBytes) {
                emit(DownloadStatus.Success)
                return@flow
            }
            
            // 建立连接
            val connection = createConnection(url, if (enableResume && supportsResume) downloadedBytes else 0L)
            currentConnection = connection
            
            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK && 
                responseCode != HttpURLConnection.HTTP_PARTIAL) {
                emit(DownloadStatus.Error("服务器返回错误: $responseCode"))
                return@flow
            }
            
            // 开始下载
            val inputStream = connection.inputStream
            val randomAccessFile = RandomAccessFile(tempFile, "rw")
            
            if (downloadedBytes > 0) {
                randomAccessFile.seek(downloadedBytes)
            }
            
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var lastUpdateTime = System.currentTimeMillis()
            var lastDownloadedBytes = downloadedBytes
            
            inputStream.use { input ->
                randomAccessFile.use { output ->
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (isCancelled) {
                            emit(DownloadStatus.Cancelled)
                            return@flow
                        }
                        
                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // 计算进度和速度
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime >= PROGRESS_INTERVAL) {
                            val progress = ((downloadedBytes * 100) / totalBytes).toInt()
                            val timeDiff = (currentTime - lastUpdateTime) / 1000.0
                            val bytesDiff = downloadedBytes - lastDownloadedBytes
                            val speed = if (timeDiff > 0) bytesDiff / timeDiff else 0.0
                            
                            emit(DownloadStatus.Progress(
                                progress = progress,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                speed = formatSpeed(speed)
                            ))
                            
                            lastUpdateTime = currentTime
                            lastDownloadedBytes = downloadedBytes
                        }
                    }
                }
            }
            
            // 下载完成，重命名文件
            tempFile.renameTo(file)
            emit(DownloadStatus.Success)
            
        } catch (e: Exception) {
            if (!isCancelled) {
                emit(DownloadStatus.Error("下载失败: ${e.message}"))
            }
        } finally {
            currentConnection?.disconnect()
            currentConnection = null
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 获取文件信息
     */
    private fun getFileInfo(url: String): Pair<Long, Boolean> {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            requestMethod = "HEAD"
            connectTimeout = 10000
            readTimeout = 10000
        }
        
        return try {
            val contentLength = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val acceptRanges = connection.getHeaderField("Accept-Ranges")
            val supportsResume = acceptRanges == "bytes"
            Pair(contentLength, supportsResume)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 创建连接
     */
    private fun createConnection(url: String, startBytes: Long): HttpURLConnection {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.apply {
            connectTimeout = 30000
            readTimeout = 30000
            setRequestProperty("User-Agent", "Acuspic-Android")
            
            if (startBytes > 0) {
                setRequestProperty("Range", "bytes=$startBytes-")
            }
        }
        return connection
    }

    /**
     * 格式化速度
     */
    private fun formatSpeed(bytesPerSecond: Double): String {
        val df = DecimalFormat("0.00")
        return when {
            bytesPerSecond >= 1024 * 1024 -> "${df.format(bytesPerSecond / (1024 * 1024))} MB/s"
            bytesPerSecond >= 1024 -> "${df.format(bytesPerSecond / 1024)} KB/s"
            else -> "${df.format(bytesPerSecond)} B/s"
        }
    }

    /**
     * 取消下载
     */
    fun cancel() {
        isCancelled = true
        currentConnection?.disconnect()
    }

    /**
     * 获取已下载的文件
     */
    fun getDownloadedFile(fileName: String): File? {
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
        val file = File(downloadDir, fileName)
        return if (file.exists()) file else null
    }

    /**
     * 删除下载的文件
     */
    fun deleteDownloadedFile(fileName: String): Boolean {
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
        val file = File(downloadDir, fileName)
        val tempFile = File(downloadDir, "$fileName.tmp")
        
        var deleted = true
        if (file.exists()) {
            deleted = deleted && file.delete()
        }
        if (tempFile.exists()) {
            deleted = deleted && tempFile.delete()
        }
        return deleted
    }

    /**
     * 获取下载目录
     */
    fun getDownloadDirectory(): File {
        val downloadDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
        if (!downloadDir.exists()) {
            downloadDir.mkdirs()
        }
        return downloadDir
    }
}
