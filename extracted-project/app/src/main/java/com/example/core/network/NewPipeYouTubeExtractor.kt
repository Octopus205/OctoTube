package com.example.core.network

import com.example.domain.model.AudioStreamOption
import com.example.domain.model.VideoStreamOption
import io.github.shalva97.initNewPipe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfo

/**
 * NewPipe-based YouTube extractor. Network work is kept off the main thread.
 * Only common resolutions up to 1080p are exposed to the app.
 */
object NewPipeYouTubeExtractor {
    private val initLock = Any()
    @Volatile private var initialized = false

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(initLock) {
            if (!initialized) {
                initNewPipe()
                initialized = true
            }
        }
    }

    data class Result(
        val title: String,
        val uploader: String,
        val videos: List<VideoStreamOption>,
        val audio: List<AudioStreamOption>
    )

    suspend fun extract(videoId: String): Result = withContext(Dispatchers.IO) {
        ensureInitialized()
        val info = StreamInfo.getInfo(
            ServiceList.YouTube,
            "https://www.youtube.com/watch?v=$videoId"
        )

        val supportedHeights = setOf(144, 240, 360, 480, 720, 1080)
        val videos = (info.videoStreams + info.videoOnlyStreams)
            .asSequence()
            .filter { it.isUrl && it.content.isNotBlank() }
            .mapNotNull { stream ->
                val height = stream.height.takeIf { it > 0 }
                    ?: Regex("(\\d+)p").find(stream.resolution.orEmpty())
                        ?.groupValues?.getOrNull(1)?.toIntOrNull()
                    ?: 0
                if (height !in supportedHeights) return@mapNotNull null
                VideoStreamOption(
                    url = stream.content,
                    qualityLabel = "${height}p",
                    height = height,
                    mimeType = stream.format?.mimeType ?: "video/mp4",
                    videoOnly = stream.isVideoOnly,
                    bitrate = stream.bitrate.toLong()
                )
            }
            .distinctBy { "${it.height}:${it.videoOnly}:${it.mimeType}" }
            .sortedWith(compareBy<VideoStreamOption> { it.height }.thenBy { it.videoOnly })
            .toList()

        val audio = info.audioStreams
            .asSequence()
            .filter { it.isUrl && it.content.isNotBlank() }
            .mapNotNull { stream ->
                val bitrate = stream.averageBitrate
                if (bitrate <= 0 || bitrate > 256) return@mapNotNull null
                AudioStreamOption(
                    url = stream.content,
                    qualityLabel = "${bitrate} kbps",
                    bitrate = bitrate.toLong(),
                    mimeType = stream.format?.mimeType ?: "audio/mp4"
                )
            }
            .distinctBy { it.bitrate }
            .sortedByDescending { it.bitrate }
            .take(5)
            .toList()

        Result(
            title = info.name.orEmpty(),
            uploader = info.uploaderName.orEmpty(),
            videos = videos,
            audio = audio
        )
    }
}
