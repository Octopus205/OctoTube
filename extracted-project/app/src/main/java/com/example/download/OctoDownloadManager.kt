package com.example.download

import android.content.Context
import android.os.Environment
import android.net.Uri
import android.provider.DocumentsContract
import com.example.core.network.NetworkModule
import java.net.URI
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

    fun setDownloadFolder(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        )
        context.getSharedPreferences("octotube_downloads", Context.MODE_PRIVATE)
            .edit().putString("folder_uri", uri.toString()).apply()
    }

    private fun selectedFolderUri(): Uri? =
        context.getSharedPreferences("octotube_downloads", Context.MODE_PRIVATE)
            .getString("folder_uri", null)?.let(Uri::parse)

    private fun createTargetDocument(folder: Uri, name: String, mimeType: String): Uri? {
        val treeId = DocumentsContract.getTreeDocumentId(folder)
        val parent = DocumentsContract.buildDocumentUriUsingTree(folder, treeId)
        return DocumentsContract.createDocument(context.contentResolver, parent, mimeType, name)
    }

    private fun openTargetOutputStream(targetUri: Uri?, fallbackFile: File) =
        if (targetUri != null) {
            context.contentResolver.openOutputStream(targetUri, "wt")
                ?: throw IllegalStateException("تعذر فتح الملف داخل مجلد الحفظ المحدد")
        } else {
            FileOutputStream(fallbackFile)
        }

    private fun deleteStoredLocation(location: String) {
        val uri = runCatching { Uri.parse(location) }.getOrNull()
        if (uri?.scheme == "content") {
            runCatching { context.contentResolver.delete(uri, null, null) }
        } else {
            runCatching { File(location).delete() }
        }
    }

    fun startDownload(
        detail: StreamDetail,
        format: DownloadFormat,
        streamUrl: String,
        qualityLabel: String
    ) {
        activeJobs[detail.id]?.cancel()

        val safeTitle = detail.title
            .replace(Regex("[\\\\/:*?\"<>|\\\\x00-\\\\x1F]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(110)
            .ifBlank { "OctoTube_${detail.id}" }
        val qualitySuffix = qualityLabel
            .replace(Regex("[^A-Za-z0-9p._-]"), "")
            .takeIf { it.isNotBlank() }
            ?.let { "_$it" }.orEmpty()
        val outputName = "${safeTitle}${qualitySuffix}.${format.extension}"
        val targetFile = File(getDownloadsDir(), outputName)
        val selectedFolder = selectedFolderUri()
        val mimeType = when (format) {
            DownloadFormat.VIDEO_HLS_TS -> "video/mp2t"
            DownloadFormat.AUDIO_M4A -> "audio/mp4"
            else -> "video/mp4"
        }
        val subtitleUrl = detail.subtitles.firstOrNull { !it.url.startsWith("local://") }?.url
        val subtitleFile = if (subtitleUrl != null) {
            File(getDownloadsDir(), "${safeName}.vtt")
        } else null

        val job = scope.launch {
            val targetUri = selectedFolder?.let {
                runCatching {
                    createTargetDocument(it, outputName, mimeType)
                }.getOrNull()
            }
            if (selectedFolder != null && targetUri == null) {
                dao.upsertDownload(
                    DownloadedMediaEntity(
                        videoId = detail.id, title = detail.title, channelName = detail.channelName,
                        thumbnailUrl = detail.thumbnailUrl, qualityLabel = qualityLabel,
                        formatName = format.name, statusName = DownloadStatus.FAILED.name,
                        progress = 0, downloadedBytes = 0L, totalBytes = 0L,
                        localFilePath = "", subtitleFilePath = null,
                        durationSeconds = detail.durationSeconds, serviceId = detail.serviceProvider.id
                    )
                )
                return@launch
            }
            val targetLocation = targetUri?.toString() ?: targetFile.absolutePath
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
                    localFilePath = targetLocation,
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
                    targetFile.parentFile?.mkdirs()
                    val isHls = contentType.contains("mpegurl") ||
                        streamUrl.substringBefore("?").endsWith(".m3u8", true)
                    if (isHls) {
                        response.close()
                        val playlist = NetworkModule.getMediaString(streamUrl)
                        if (playlist.contains("#EXT-X-KEY") && !playlist.contains("METHOD=NONE")) {
                            throw IllegalStateException("هذا البث مشفر ولا يمكن تنزيله بهذه الطريقة")
                        }
                        if (playlist.contains("#EXT-X-MAP")) {
                            throw IllegalStateException("بث HLS بصيغة fMP4 غير مدعوم حاليًا")
                        }
                        val segments = playlist.lineSequence().map { it.trim() }
                            .filter { it.isNotBlank() && !it.startsWith("#") }
                            .map { URI(streamUrl).resolve(it).toString() }.toList()
                        if (segments.isEmpty()) throw IllegalStateException("قائمة HLS لا تحتوي مقاطع فيديو")
                        var downloaded = 0L
                        openTargetOutputStream(targetUri, targetFile).use { output ->
                            segments.forEachIndexed { index, segmentUrl ->
                                val segmentRequest = Request.Builder().url(segmentUrl).get().build()
                                val segmentCall = NetworkModule.downloadOkHttpClient.newCall(segmentRequest)
                                activeCalls[detail.id] = segmentCall
                                segmentCall.execute().use { segmentResponse ->
                                    if (!segmentResponse.isSuccessful) throw IllegalStateException("HLS segment HTTP ${segmentResponse.code}")
                                    val segmentBody = segmentResponse.body ?: throw IllegalStateException("Empty HLS segment")
                                    segmentBody.byteStream().use { input ->
                                        val buffer = ByteArray(32 * 1024)
                                        while (true) {
                                            val count = input.read(buffer)
                                            if (count < 0) break
                                            output.write(buffer, 0, count)
                                            downloaded += count
                                        }
                                    }
                                }
                                dao.updateDownloadProgress(
                                    videoId = detail.id,
                                    status = DownloadStatus.DOWNLOADING.name,
                                    progress = ((index + 1) * 100 / segments.size).coerceAtMost(99),
                                    downloaded = downloaded,
                                    total = 0L
                                )
                            }
                            output.flush()
                        }
                        if (downloaded <= 0L) throw IllegalStateException("لم يتم تنزيل أي بيانات")
                        dao.updateDownloadProgress(
                            videoId = detail.id,
                            status = DownloadStatus.COMPLETED.name,
                            progress = 100,
                            downloaded = downloaded,
                            total = downloaded
                        )
                    } else {
                        val body = response.body ?: throw IllegalStateException("Empty body")
                        val totalBytes = body.contentLength().coerceAtLeast(0L)
                        if (contentType.contains("application/json") || contentType.startsWith("text/html")) {
                            throw IllegalStateException("الخادم أعاد صفحة أو بيانات API بدل ملف فيديو")
                        }
                        body.byteStream().use { input ->
                            (targetUri?.let { context.contentResolver.openOutputStream(it, "wt") } ?: FileOutputStream(targetFile)).use { output ->
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
                                        val percent = if (totalBytes > 0L) ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 99) else 25
                                        dao.updateDownloadProgress(detail.id, DownloadStatus.DOWNLOADING.name, percent, downloaded, totalBytes)
                                    }
                                }
                                output.flush()
                                if (downloaded <= 0L) throw IllegalStateException("الملف الذي أعاده الخادم فارغ")
                                val finalTotal = if (totalBytes > 0L) totalBytes else downloaded
                                dao.updateDownloadProgress(detail.id, DownloadStatus.COMPLETED.name, 100, downloaded, finalTotal)
                            }
                        }
                    }
                }
            } catch (_: CancellationException) {
                dao.updateDownloadProgress(
                    videoId = detail.id,
                    status = DownloadStatus.PAUSED.name,
                    progress = 0,
                    downloaded = if (targetUri == null) targetFile.length() else 0L,
                    total = if (targetUri == null) targetFile.length() else 0L
                )
            } catch (_: Exception) {
                if (targetUri != null) runCatching { context.contentResolver.delete(targetUri, null, null) } else runCatching { targetFile.delete() }
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
                deleteStoredLocation(it.localFilePath)
                it.subtitleFilePath?.let { subPath -> runCatching { File(subPath).delete() } }
            }
            dao.deleteDownload(videoId)
        }
    }
}
