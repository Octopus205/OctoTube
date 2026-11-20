package com.example.download

import android.content.Context
import android.os.Environment
import com.example.core.network.NetworkModule
import com.example.data.local.DownloadedMediaEntity
import com.example.data.local.OctoDao
import com.example.domain.model.DownloadFormat
import com.example.domain.model.DownloadStatus
import com.example.domain.model.StreamDetail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Call
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class OctoDownloadManager(
    private val context: Context,
    private val dao: OctoDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeCalls = ConcurrentHashMap<String, Call>()

    private fun getDownloadsDir(): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir
        return File(base, "OctoPipe").apply { if (!exists()) mkdirs() }
    }

    fun startDownload(
        detail: StreamDetail,
        format: DownloadFormat,
        streamUrl: String,
        qualityLabel: String
    ) {
        activeJobs[detail.id]?.cancel()

        val safeName = detail.id.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val targetFile = File(getDownloadsDir(), "${safeName}_${format.extension}.${format.extension}")
        val subtitleUrl = detail.subtitles.firstOrNull { !it.url.startsWith("local://") }?.url
        val subtitleFile = if (subtitleUrl != null) {
            File(getDownloadsDir(), "${safeName}.vtt")
        } else null

        val job = scope.launch {
            dao.upsertDownload(
                DownloadedMediaEntity(
                    videoId = detail.id,
                    title = detail.title,
                    channelName = detail.channelName,
                    thumbnailUrl = detail.thumbnailUrl,
                    qualityLabel = qualityLabel,
                    formatName = format.name,
                    statusName = DownloadStatus.DOWNLOADING.name,
                    progress = 0,
                    downloadedBytes = 0L,
                    totalBytes = 0L,
                    localFilePath = targetFile.absolutePath,
                    subtitleFilePath = subtitleFile?.absolutePath,
                    durationSeconds = detail.durationSeconds,
                    serviceId = detail.serviceProvider.id
                )
            )

            try {
                // Download optional subtitle sidecar
                if (subtitleUrl != null && subtitleFile != null) {
                    runCatching {
                        val subText = NetworkModule.getString(subtitleUrl)
                        subtitleFile.writeText(subText, Charsets.UTF_8)
                    }
                }

                val request = Request.Builder().url(streamUrl).get().build()
                val call = NetworkModule.downloadOkHttpClient.newCall(request)
                activeCalls[detail.id] = call
                call.execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IllegalStateException("HTTP ${response.code}")
                    }
                    val contentType = response.header("Content-Type").orEmpty().lowercase()
                    if (contentType.contains("mpegurl") || streamUrl.substringBefore("?").endsWith(".m3u8", true)) {
                        throw IllegalStateException("رابط HLS يحتاج تنزيلًا مجزأً؛ اختر رابط MP4 مباشرًا إن توفر")
                    }
                    val body = response.body ?: throw IllegalStateException("Empty body")
                    val totalBytes = body.contentLength().coerceAtLeast(0L)
                    if (contentType.contains("application/json") || contentType.startsWith("text/html")) {
                        throw IllegalStateException("الخادم أعاد صفحة أو بيانات API بدل ملف فيديو")
                    }
                    targetFile.parentFile?.mkdirs()

                    body.byteStream().use { input ->
                        FileOutputStream(targetFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var downloaded = 0L
                            var lastUiUpdate = 0L

                            while (true) {
                                val read = input.read(buffer)
                                if (read == -1) break
                                output.write(buffer, 0, read)
                                downloaded += read

                                val now = System.currentTimeMillis()
                                if (now - lastUiUpdate >= 250L) {
                                    lastUiUpdate = now
                                    val percent = if (totalBytes > 0L) {
                                        ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99)
                                    } else {
                                        25
                                    }
                                    dao.updateDownloadProgress(
                                        videoId = detail.id,
                                        status = DownloadStatus.DOWNLOADING.name,
                                        progress = percent,
                                        downloaded = downloaded,
                                        total = totalBytes
                                    )
                                }
                            }
                            output.flush()

                            val finalTotal = if (totalBytes > 0L) totalBytes else downloaded
                            dao.updateDownloadProgress(
                                videoId = detail.id,
                                status = DownloadStatus.COMPLETED.name,
                                progress = 100,
                                downloaded = downloaded,
                                total = finalTotal
                            )
                        }
                    }
                }
            } catch (_: CancellationException) {
                dao.updateDownloadProgress(
                    videoId = detail.id,
                    status = DownloadStatus.PAUSED.name,
                    progress = 0,
                    downloaded = targetFile.length(),
                    total = targetFile.length()
                )
            } catch (_: Exception) {
                runCatching { targetFile.delete() }
                dao.updateDownloadProgress(
                    videoId = detail.id,
                    status = DownloadStatus.FAILED.name,
                    progress = 0,
                    downloaded = 0L,
                    total = 0L
                )
            } finally {
                activeCalls.remove(detail.id)
                activeJobs.remove(detail.id)
            }
        }
        activeJobs[detail.id] = job
    }

    fun cancelDownload(videoId: String) {
        activeCalls[videoId]?.cancel()
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        scope.launch {
            dao.updateDownloadProgress(
                videoId = videoId,
                status = DownloadStatus.PAUSED.name,
                progress = 0,
                downloaded = 0L,
                total = 0L
            )
        }
    }

    fun deleteDownload(videoId: String) {
        activeCalls[videoId]?.cancel()
        activeJobs[videoId]?.cancel()
        activeJobs.remove(videoId)
        scope.launch {
            val entity = dao.getDownloadById(videoId)
            entity?.let {
                runCatching { File(it.localFilePath).delete() }
                it.subtitleFilePath?.let { subPath -> runCatching { File(subPath).delete() } }
            }
            dao.deleteDownload(videoId)
        }
    }
}
