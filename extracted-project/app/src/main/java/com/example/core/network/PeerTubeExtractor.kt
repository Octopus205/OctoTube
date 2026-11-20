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
private data class PeerTubeAvatarDto(
    val path: String = ""
)

@Serializable
private data class PeerTubeChannelDto(
    val id: Long = 0L,
    val name: String = "",
    val displayName: String = "",
    val avatars: List<PeerTubeAvatarDto> = emptyList()
)

@Serializable
private data class PeerTubeFileDto(
    val fileUrl: String = "",
    val resolution: PeerTubeResolutionDto? = null,
    val size: Long = 0L
)

@Serializable
private data class PeerTubeResolutionDto(
    val id: Int = 360,
    val label: String = "360p"
)

@Serializable
private data class PeerTubePlaylistDto(
    val playlistUrl: String = "",
    val files: List<PeerTubeFileDto> = emptyList()
)

@Serializable
private data class PeerTubeVideoDto(
    val uuid: String = "",
    val shortUUID: String = "",
    val name: String = "",
    val description: String? = null,
    val duration: Long = 0L,
    val views: Long = 0L,
    val likes: Long = 0L,
    val publishedAt: String = "",
    val thumbnailPath: String = "",
    val previewPath: String = "",
    val channel: PeerTubeChannelDto? = null,
    val files: List<PeerTubeFileDto> = emptyList(),
    val streamingPlaylists: List<PeerTubePlaylistDto> = emptyList()
)

@Serializable
private data class PeerTubeListResponseDto(
    val data: List<PeerTubeVideoDto> = emptyList()
)

@Serializable
private data class PeerTubeCaptionDto(
    val language: PeerTubeLangDto? = null,
    val captionPath: String = ""
)

@Serializable
private data class PeerTubeLangDto(
    val id: String = "en",
    val label: String = "English"
)

@Serializable
private data class PeerTubeCaptionsResponseDto(
    val data: List<PeerTubeCaptionDto> = emptyList()
)

class PeerTubeExtractor {
    suspend fun getTrending(baseUrl: String): List<VideoSummary> {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/videos?sort=-trending&count=20&nsfw=false")
        val response = NetworkModule.json.decodeFromString<PeerTubeListResponseDto>(body)
        return response.data.map { it.toVideoSummary(cleanBase) }
    }

    suspend fun search(baseUrl: String, query: String): List<VideoSummary> {
        val cleanBase = baseUrl.trimEnd('/')
        val encoded = URLEncoder.encode(query, "UTF-8")
        val body = NetworkModule.getString("$cleanBase/api/v1/search/videos?search=$encoded&count=20&nsfw=false")
        val response = NetworkModule.json.decodeFromString<PeerTubeListResponseDto>(body)
        return response.data.map { it.toVideoSummary(cleanBase) }
    }

    suspend fun getStreamDetail(baseUrl: String, videoId: String): StreamDetail {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/videos/$videoId")
        val dto = NetworkModule.json.decodeFromString<PeerTubeVideoDto>(body)

        val captions = try {
            val capBody = NetworkModule.getString("$cleanBase/api/v1/videos/$videoId/captions")
            NetworkModule.json.decodeFromString<PeerTubeCaptionsResponseDto>(capBody).data.map { cap ->
                SubtitleTrack(
                    url = "$cleanBase${cap.captionPath}",
                    languageCode = cap.language?.id ?: "en",
                    languageName = cap.language?.label ?: "Subtitles"
                )
            }
        } catch (_: Exception) {
            emptyList()
        }

        val allFiles = dto.files + dto.streamingPlaylists.flatMap { it.files }
        val videoStreams = allFiles
            .filter { it.fileUrl.isNotBlank() }
            .map {
                val h = it.resolution?.id ?: 720
                VideoStreamOption(
                    url = it.fileUrl,
                    qualityLabel = it.resolution?.label ?: "${h}p",
                    height = h,
                    mimeType = "video/mp4",
                    videoOnly = false,
                    contentLength = it.size
                )
            }
            .sortedByDescending { it.height }

        val hlsUrl = dto.streamingPlaylists.firstOrNull()?.playlistUrl?.takeIf { it.isNotBlank() }
        val thumb = dto.previewPath.ifBlank { dto.thumbnailPath }.let {
            if (it.startsWith("http")) it else "$cleanBase$it"
        }
        val avatar = dto.channel?.avatars?.firstOrNull()?.path?.let {
            if (it.startsWith("http")) it else "$cleanBase$it"
        }

        return StreamDetail(
            id = dto.uuid.ifBlank { videoId },
            title = dto.name,
            description = dto.description.orEmpty(),
            channelId = dto.channel?.name ?: "peertube_channel",
            channelName = dto.channel?.displayName ?: dto.channel?.name ?: "PeerTube",
            channelAvatarUrl = avatar,
            subscriberCountText = "PeerTube Fediverse",
            thumbnailUrl = thumb,
            durationSeconds = dto.duration,
            views = dto.views,
            likes = dto.likes,
            uploadDate = dto.publishedAt.take(10),
            serviceProvider = ServiceProvider.PEERTUBE,
            hlsUrl = hlsUrl,
            videoStreams = videoStreams,
            audioStreams = videoStreams.firstOrNull()?.let {
                listOf(
                    AudioStreamOption(
                        url = it.url,
                        qualityLabel = "مسار الصوت (${it.qualityLabel})",
                        bitrate = 128_000L,
                        mimeType = it.mimeType,
                        contentLength = it.contentLength
                    )
                )
            } ?: emptyList(),
            subtitles = captions,
            chapters = emptyList(),
            relatedVideos = emptyList(),
            sourceInstance = cleanBase
        )
    }

    suspend fun getChannel(baseUrl: String, channelName: String): ChannelDetail {
        val cleanBase = baseUrl.trimEnd('/')
        val body = NetworkModule.getString("$cleanBase/api/v1/video-channels/$channelName/videos?count=20")
        val response = NetworkModule.json.decodeFromString<PeerTubeListResponseDto>(body)
        val firstChannel = response.data.firstOrNull()?.channel
        return ChannelDetail(
            id = channelName,
            name = firstChannel?.displayName ?: channelName,
            avatarUrl = firstChannel?.avatars?.firstOrNull()?.path?.let { "$cleanBase$it" },
            bannerUrl = null,
            description = "قناة مستقلة على شبكة PeerTube",
            subscriberCount = 0L,
            serviceProvider = ServiceProvider.PEERTUBE,
            videos = response.data.map { it.toVideoSummary(cleanBase) }
        )
    }

    private fun PeerTubeVideoDto.toVideoSummary(baseUrl: String): VideoSummary {
        val thumb = thumbnailPath.ifBlank { previewPath }.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        }
        val avatar = channel?.avatars?.firstOrNull()?.path?.let {
            if (it.startsWith("http")) it else "$baseUrl$it"
        }
        return VideoSummary(
            id = uuid.ifBlank { shortUUID },
            title = name,
            channelId = channel?.name ?: "channel",
            channelName = channel?.displayName ?: channel?.name ?: "PeerTube",
            channelAvatarUrl = avatar,
            thumbnailUrl = thumb,
            durationSeconds = duration,
            views = views,
            uploadedText = publishedAt.take(10),
            serviceProvider = ServiceProvider.PEERTUBE
        )
    }
}
