package com.example.core.network

import com.example.domain.model.AudioStreamOption
import com.example.domain.model.ChannelDetail
import com.example.domain.model.ServiceProvider
import com.example.domain.model.StreamDetail
import com.example.domain.model.SubtitleTrack
import com.example.domain.model.VideoStreamOption
import com.example.domain.model.VideoSummary
import kotlinx.serialization.Serializable
import java.net.URLEncoder

@Serializable
private data class InvidiousThumbnailDto(
    val url: String = "",
    val width: Int = 0,
    val height: Int = 0
)

@Serializable
private data class InvidiousVideoItemDto(
    val type: String = "video",
    val title: String = "",
    val videoId: String = "",
    val author: String = "",
    val authorId: String = "",
    val lengthSeconds: Long = 0L,
    val viewCount: Long = 0L,
    val publishedText: String = "",
    val videoThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    val authorThumbnails: List<InvidiousThumbnailDto> = emptyList()
)

@Serializable
private data class InvidiousFormatStreamDto(
    val url: String = "",
    val qualityLabel: String = "",
    val type: String = "",
    val size: String = ""
)

@Serializable
private data class InvidiousAdaptiveFormatDto(
    val url: String = "",
    val type: String = "",
    val bitrate: String = "0",
    val qualityLabel: String = "",
    val clen: String = "0"
)

@Serializable
private data class InvidiousCaptionDto(
    val label: String = "",
    val languageCode: String = "",
    val url: String = ""
)

@Serializable
private data class InvidiousVideoDetailDto(
    val title: String = "",
    val videoId: String = "",
    val description: String = "",
    val viewCount: Long = 0L,
    val likeCount: Long = 0L,
    val lengthSeconds: Long = 0L,
    val publishedText: String = "",
    val author: String = "",
    val authorId: String = "",
    val subCountText: String = "",
    val hlsUrl: String? = null,
    val videoThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    val authorThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    val formatStreams: List<InvidiousFormatStreamDto> = emptyList(),
    val adaptiveFormats: List<InvidiousAdaptiveFormatDto> = emptyList(),
    val captions: List<InvidiousCaptionDto> = emptyList(),
    val recommendedVideos: List<InvidiousVideoItemDto> = emptyList()
)

@Serializable
private data class InvidiousChannelDto(
    val author: String = "",
    val authorId: String = "",
    val description: String = "",
    val subCount: Long = 0L,
    val authorThumbnails: List<InvidiousThumbnailDto> = emptyList(),
    val authorBanners: List<InvidiousThumbnailDto> = emptyList(),
    val latestVideos: List<InvidiousVideoItemDto> = emptyList()
)

class InvidiousExtractor {
    suspend fun getTrending(baseUrl: String, region: String = "SA"): List<VideoSummary> {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/trending?region=$region")
        val items = NetworkModule.json.decodeFromString<List<InvidiousVideoItemDto>>(body)
        return items.mapNotNull { it.toVideoSummary(cleanBase) }
    }

    suspend fun search(baseUrl: String, query: String): List<VideoSummary> {
        val cleanBase = baseUrl.trimEnd('/')
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = NetworkModule.getString("$cleanBase/api/v1/search?q=$encoded&type=video")
        val items = NetworkModule.json.decodeFromString<List<InvidiousVideoItemDto>>(body)
        return items.mapNotNull { it.toVideoSummary(cleanBase) }
    }

    suspend fun getStreamDetail(baseUrl: String, videoId: String): StreamDetail {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/videos/$videoId")
        val dto = NetworkModule.json.decodeFromString<InvidiousVideoDetailDto>(body)

        val progressiveStreams = dto.formatStreams.filter { it.url.isNotBlank() }.map {
            val height = parseHeight(it.qualityLabel)
            VideoStreamOption(
                url = it.url,
                qualityLabel = it.qualityLabel.ifBlank { "${height}p" },
                height = height,
                mimeType = it.type.substringBefore(";").ifBlank { "video/mp4" },
                videoOnly = false
            )
        }

        val adaptiveVideo = dto.adaptiveFormats
            .filter { it.url.isNotBlank() && it.type.startsWith("video/") }
            .map {
                val height = parseHeight(it.qualityLabel)
                VideoStreamOption(
                    url = it.url,
                    qualityLabel = it.qualityLabel.ifBlank { "${height}p" },
                    height = height,
                    mimeType = it.type.substringBefore(";"),
                    videoOnly = true,
                    bitrate = it.bitrate.toLongOrNull() ?: 0L,
                    contentLength = it.clen.toLongOrNull() ?: 0L
                )
            }

        val audioStreams = dto.adaptiveFormats
            .filter { it.url.isNotBlank() && it.type.startsWith("audio/") }
            .map {
                val bitrate = it.bitrate.toLongOrNull() ?: 0L
                AudioStreamOption(
                    url = it.url,
                    qualityLabel = "${bitrate / 1000} kbps",
                    bitrate = bitrate,
                    mimeType = it.type.substringBefore(";"),
                    contentLength = it.clen.toLongOrNull() ?: 0L
                )
            }
            .sortedByDescending { it.bitrate }

        val subtitles = dto.captions.filter { it.url.isNotBlank() }.map {
            val fullUrl = if (it.url.startsWith("http")) it.url else "$cleanBase${it.url}"
            SubtitleTrack(
                url = fullUrl,
                languageCode = it.languageCode.ifBlank { "en" },
                languageName = it.label.ifBlank { it.languageCode }
            )
        }

        val thumb = dto.videoThumbnails.maxByOrNull { it.width }?.url?.normalizeUrl(cleanBase)
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        val avatar = dto.authorThumbnails.maxByOrNull { it.width }?.url?.normalizeUrl(cleanBase)

        return StreamDetail(
            id = dto.videoId.ifBlank { videoId },
            title = dto.title,
            description = dto.description,
            channelId = dto.authorId,
            channelName = dto.author,
            channelAvatarUrl = avatar,
            subscriberCountText = dto.subCountText,
            thumbnailUrl = thumb,
            durationSeconds = dto.lengthSeconds,
            views = dto.viewCount,
            likes = dto.likeCount,
            uploadDate = dto.publishedText,
            serviceProvider = ServiceProvider.INVIDIOUS,
            hlsUrl = dto.hlsUrl,
            videoStreams = (progressiveStreams + adaptiveVideo).sortedWith(
                compareBy<VideoStreamOption> { it.videoOnly }.thenByDescending { it.height }
            ),
            audioStreams = audioStreams,
            subtitles = subtitles,
            chapters = emptyList(),
            relatedVideos = dto.recommendedVideos.mapNotNull { it.toVideoSummary(cleanBase) },
            sourceInstance = cleanBase
        )
    }

    suspend fun getChannel(baseUrl: String, channelId: String): ChannelDetail {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/channels/$channelId")
        val dto = NetworkModule.json.decodeFromString<InvidiousChannelDto>(body)
        return ChannelDetail(
            id = dto.authorId.ifBlank { channelId },
            name = dto.author,
            avatarUrl = dto.authorThumbnails.maxByOrNull { it.width }?.url?.normalizeUrl(cleanBase),
            bannerUrl = dto.authorBanners.maxByOrNull { it.width }?.url?.normalizeUrl(cleanBase),
            description = dto.description,
            subscriberCount = dto.subCount,
            serviceProvider = ServiceProvider.INVIDIOUS,
            videos = dto.latestVideos.mapNotNull { it.toVideoSummary(cleanBase) }
        )
    }

    private fun InvidiousVideoItemDto.toVideoSummary(baseUrl: String): VideoSummary? {
        if (videoId.isBlank() || title.isBlank()) return null
        val thumb = videoThumbnails.maxByOrNull { it.width }?.url?.normalizeUrl(baseUrl)
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
        return VideoSummary(
            id = videoId,
            title = title,
            channelId = authorId,
            channelName = author.ifBlank { "قناة" },
            channelAvatarUrl = authorThumbnails.maxByOrNull { it.width }?.url?.normalizeUrl(baseUrl),
            thumbnailUrl = thumb,
            durationSeconds = lengthSeconds,
            views = viewCount,
            uploadedText = publishedText,
            serviceProvider = ServiceProvider.INVIDIOUS,
            isLive = lengthSeconds <= 0L
        )
    }

    private fun parseHeight(label: String): Int =
        Regex("(\\d{3,4})p").find(label)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 360

    private fun String.normalizeUrl(baseUrl: String): String = when {
        startsWith("http") -> this
        startsWith("//") -> "https:$this"
        startsWith("/") -> "$baseUrl$this"
        else -> this
    }
}
