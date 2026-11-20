package com.example.core.network

import com.example.domain.model.ChannelDetail
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceInstanceHealth
import com.example.domain.model.ServiceProvider
import com.example.domain.model.StreamDetail
import com.example.domain.model.VideoSummary
import java.net.URI
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull

data class FeedFetchResult(
    val videos: List<VideoSummary>,
    val activeInstance: String,
    val usedFallbackCatalog: Boolean,
    val warningMessage: String? = null
)

class OctoExtractorEngine(
    private val piped: PipedExtractor = PipedExtractor(),
    private val invidious: InvidiousExtractor = InvidiousExtractor(),
    private val peerTube: PeerTubeExtractor = PeerTubeExtractor(),
    private val sponsorBlock: SponsorBlockClient = SponsorBlockClient()
) {

    fun extractVideoIdFromInput(input: String): String? {
        val trimmed = input.trim()
        if (!trimmed.contains("http") && !trimmed.contains("youtu")) return null
        val patterns = listOf(
            Regex("(?:v=|/)([0-9A-Za-z_-]{11})(?:[?&/]|$)"),
            Regex("youtu\\.be/([0-9A-Za-z_-]{11})")
        )
        for (regex in patterns) {
            val match = regex.find(trimmed)
            val id = match?.groupValues?.getOrNull(1)
            if (!id.isNullOrBlank()) return id
        }
        return null
    }

    suspend fun fetchTrending(settings: OctoSettings): FeedFetchResult {
        val provider = settings.activeProvider
        val candidates = buildInstanceList(provider, settings)
        for (baseUrl in candidates) {
            try {
                val items = when (provider) {
                    ServiceProvider.PIPED -> piped.getTrending(baseUrl)
                    ServiceProvider.INVIDIOUS -> invidious.getTrending(baseUrl)
                    ServiceProvider.PEERTUBE -> peerTube.getTrending(baseUrl)
                }
                if (items.isNotEmpty()) {
                    return FeedFetchResult(items, baseUrl, usedFallbackCatalog = false)
                }
            } catch (_: Exception) {
                // Try next mirror instance
            }
        }

        // Cross-provider failover before OpenMediaCatalog
        if (provider == ServiceProvider.PIPED) {
            for (invUrl in ServiceProvider.INVIDIOUS.fallbackUrls) {
                try {
                    val items = invidious.getTrending(invUrl)
                    if (items.isNotEmpty()) {
                        return FeedFetchResult(
                            videos = items,
                            activeInstance = invUrl,
                            usedFallbackCatalog = false,
                            warningMessage = "تم التحويل تلقائياً إلى Invidious لضمان استقرار البث"
                        )
                    }
                } catch (_: Exception) {
                }
            }
        }

        return FeedFetchResult(
            videos = OpenMediaCatalog.getSummaries(provider),
            activeInstance = "Open Media CC",
            usedFallbackCatalog = true,
            warningMessage = "تعذر الوصول إلى خوادم البث العامة حالياً؛ تم عرض مكتبة الوسائط المفتوحة التجريبية"
        )
    }

    suspend fun search(query: String, settings: OctoSettings): FeedFetchResult {
        val cleanQuery = query.trim()
        if (cleanQuery.isBlank()) return fetchTrending(settings)

        val provider = settings.activeProvider
        val candidates = buildInstanceList(provider, settings)
        for (baseUrl in candidates) {
            try {
                val items = when (provider) {
                    ServiceProvider.PIPED -> piped.search(baseUrl, cleanQuery)
                    ServiceProvider.INVIDIOUS -> invidious.search(baseUrl, cleanQuery)
                    ServiceProvider.PEERTUBE -> peerTube.search(baseUrl, cleanQuery)
                }
                if (items.isNotEmpty()) {
                    return FeedFetchResult(items, baseUrl, usedFallbackCatalog = false)
                }
            } catch (_: Exception) {
            }
        }

        val localMatches = OpenMediaCatalog.getSummaries(provider).filter {
            it.title.contains(cleanQuery, ignoreCase = true) ||
                it.channelName.contains(cleanQuery, ignoreCase = true)
        }
        return FeedFetchResult(
            videos = localMatches.ifEmpty { OpenMediaCatalog.getSummaries(provider) },
            activeInstance = "Open Media CC",
            usedFallbackCatalog = true,
            warningMessage = "خوادم البحث العامة مشغولة حالياً؛ تم إظهار النتائج المتاحة محلياً"
        )
    }

    suspend fun extractStreamDetail(
        videoId: String,
        provider: ServiceProvider,
        settings: OctoSettings
    ): StreamDetail = coroutineScope {
        OpenMediaCatalog.getDetail(videoId, provider)?.let { return@coroutineScope it }

        val sponsorJob = async {
            if (settings.sponsorBlockEnabled && provider != ServiceProvider.PEERTUBE) {
                sponsorBlock.fetchSkipSegments(videoId, settings.enabledSponsorCategories)
            } else emptyList()
        }
        val newPipeJob = if (provider != ServiceProvider.PEERTUBE) {
            async {
                withTimeoutOrNull(3_500L) {
                    runCatching { NewPipeYouTubeExtractor.extract(videoId) }.getOrNull()
                }
            }
        } else null

        // Race public instances and both YouTube extractor backends. The first
        // valid response wins; slower mirrors are cancelled instead of awaited.
        val candidates = if (provider == ServiceProvider.PEERTUBE) {
            buildInstanceList(provider, settings).map { provider to it }
        } else {
            val alternate = if (provider == ServiceProvider.PIPED) {
                ServiceProvider.INVIDIOUS
            } else ServiceProvider.PIPED
            (buildInstanceList(provider, settings).map { provider to it } +
                buildInstanceList(alternate, settings).map { alternate to it })
                .distinctBy { it.second }
        }

        val resultChannel = kotlinx.coroutines.channels.Channel<Pair<StreamDetail, String>>(candidates.size)
        val requests = candidates.map { (sourceProvider, baseUrl) ->
            launch {
                try {
                    val detail = when (sourceProvider) {
                        ServiceProvider.PIPED -> piped.getStreamDetail(baseUrl, videoId)
                        ServiceProvider.INVIDIOUS -> invidious.getStreamDetail(baseUrl, videoId)
                        ServiceProvider.PEERTUBE -> peerTube.getStreamDetail(baseUrl, videoId)
                    }
                    if (detail.videoStreams.isNotEmpty() || !detail.hlsUrl.isNullOrBlank()) {
                        resultChannel.send(detail.copy(serviceProvider = sourceProvider, sourceInstance = baseUrl) to baseUrl)
                    }
                } catch (_: Exception) {
                    // Another instance may still return a valid stream.
                }
            }
        }

        val winning = withTimeoutOrNull(15_000L) { resultChannel.receive() }
        requests.forEach { it.cancel() }
        resultChannel.close()
        val (baseDetail, sourceUrl) = winning
            ?: throw IllegalStateException("تعذر استخراج الفيديو من جميع المصادر المتاحة. حاول مرة أخرى بعد قليل.")

        // Fetch adaptive variants without blocking initial playback for long.
        val hlsVariants = baseDetail.hlsUrl?.let { manifestUrl ->
            withTimeoutOrNull(2_000L) {
                runCatching {
                    val manifest = NetworkModule.getMediaString(manifestUrl)
                    val lines = manifest.lines()
                    lines.mapIndexedNotNull { index, raw ->
                        val line = raw.trim()
                        if (!line.startsWith("#EXT-X-STREAM-INF", true)) return@mapIndexedNotNull null
                        val nextUri = lines.drop(index + 1).firstOrNull {
                            it.isNotBlank() && !it.trimStart().startsWith("#")
                        }?.trim() ?: return@mapIndexedNotNull null
                        val height = Regex("RESOLUTION=\\d+x(\\d+)", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapIndexedNotNull null
                        val bandwidth = Regex("BANDWIDTH=(\\d+)", RegexOption.IGNORE_CASE)
                            .find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                        if (height !in setOf(144, 240, 360, 720, 1080)) return@mapIndexedNotNull null
                        VideoStreamOption(
                            url = URI(manifestUrl).resolve(nextUri).toString(),
                            qualityLabel = "${height}p",
                            height = height,
                            mimeType = "application/vnd.apple.mpegurl",
                            videoOnly = false,
                            bitrate = bandwidth
                        )
                    }.distinctBy { it.height }.sortedByDescending { it.height }
                }.getOrDefault(emptyList())
            }
        } ?: emptyList()

        val newPipeFormats = newPipeJob?.await()
        val segments = sponsorJob.await()
        val videoOptions = (baseDetail.videoStreams + hlsVariants + newPipeFormats?.videos.orEmpty())
            .filter { it.height in setOf(144, 240, 360, 720, 1080) }
            .distinctBy { "${it.height}:${it.videoOnly}:${it.mimeType}:${it.url.substringBefore("?")}" }
            .sortedByDescending { it.height }
        val audioOptions = (baseDetail.audioStreams + newPipeFormats?.audio.orEmpty())
            .filter { it.bitrate in 1L..256L }
            .distinctBy { "${it.bitrate}:${it.mimeType}:${it.url.substringBefore("?")}" }
            .sortedByDescending { it.bitrate }
            .take(5)

        baseDetail.copy(
            videoStreams = videoOptions,
            audioStreams = audioOptions,
            sponsorSegments = segments,
            sourceInstance = sourceUrl
        )
    }

    suspend fun fetchChannel(
        channelId: String,
        provider: ServiceProvider,
        settings: OctoSettings
    ): ChannelDetail {
        OpenMediaCatalog.getChannel(channelId, provider)?.let { return it }

        val candidates = buildInstanceList(provider, settings)
        for (baseUrl in candidates) {
            try {
                return when (provider) {
                    ServiceProvider.PIPED -> piped.getChannel(baseUrl, channelId)
                    ServiceProvider.INVIDIOUS -> invidious.getChannel(baseUrl, channelId)
                    ServiceProvider.PEERTUBE -> peerTube.getChannel(baseUrl, channelId)
                }
            } catch (_: Exception) {
            }
        }
        throw IllegalStateException("تعذر تحميل بيانات القناة من الخادم المحدد")
    }

    suspend fun checkAllInstancesHealth(settings: OctoSettings): List<ServiceInstanceHealth> = coroutineScope {
        val targets = listOf(
            ServiceProvider.PIPED to settings.customPipedUrl.ifBlank { ServiceProvider.PIPED.defaultBaseUrl },
            ServiceProvider.INVIDIOUS to settings.customInvidiousUrl.ifBlank { ServiceProvider.INVIDIOUS.defaultBaseUrl },
            ServiceProvider.PEERTUBE to settings.customPeerTubeUrl.ifBlank { ServiceProvider.PEERTUBE.defaultBaseUrl }
        )
        targets.map { (provider, url) ->
            async {
                val testEndpoint = when (provider) {
                    ServiceProvider.PIPED -> "${url.trimEnd('/')}/trending?region=US"
                    ServiceProvider.INVIDIOUS -> "${url.trimEnd('/')}/api/v1/stats"
                    ServiceProvider.PEERTUBE -> "${url.trimEnd('/')}/api/v1/videos?count=1"
                }
                val (ok, ms) = NetworkModule.pingUrl(testEndpoint)
                ServiceInstanceHealth(
                    provider = provider,
                    url = url,
                    isReachable = ok,
                    latencyMs = ms,
                    statusMessage = if (ok) "متصل (${ms}ms)" else "غير مستجيب"
                )
            }
        }.map { it.await() }
    }

    private fun buildInstanceList(provider: ServiceProvider, settings: OctoSettings): List<String> {
        val custom = when (provider) {
            ServiceProvider.PIPED -> settings.customPipedUrl
            ServiceProvider.INVIDIOUS -> settings.customInvidiousUrl
            ServiceProvider.PEERTUBE -> settings.customPeerTubeUrl
        }.trim()
        return (listOf(custom) + provider.fallbackUrls)
            .filter { it.startsWith("http") }
            .distinct()
    }
}
