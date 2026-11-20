package com.example.core.network

import com.example.domain.model.ChannelDetail
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceInstanceHealth
import com.example.domain.model.ServiceProvider
import com.example.domain.model.StreamDetail
import com.example.domain.model.VideoSummary
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

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
        OpenMediaCatalog.getDetail(videoId, provider)?.let { fallback ->
            return@coroutineScope fallback
        }

        val sponsorJob = async {
            if (settings.sponsorBlockEnabled && provider != ServiceProvider.PEERTUBE) {
                sponsorBlock.fetchSkipSegments(videoId, settings.enabledSponsorCategories)
            } else {
                emptyList()
            }
        }

        val candidates = buildInstanceList(provider, settings)
        for (baseUrl in candidates) {
            try {
                val detail = when (provider) {
                    ServiceProvider.PIPED -> piped.getStreamDetail(baseUrl, videoId)
                    ServiceProvider.INVIDIOUS -> invidious.getStreamDetail(baseUrl, videoId)
                    ServiceProvider.PEERTUBE -> peerTube.getStreamDetail(baseUrl, videoId)
                }
                val segments = sponsorJob.await()
                return@coroutineScope detail.copy(sponsorSegments = segments)
            } catch (_: Exception) {
            }
        }

        // Cross-engine YouTube failover (Piped <-> Invidious)
        val altProvider = if (provider == ServiceProvider.PIPED) {
            ServiceProvider.INVIDIOUS
        } else {
            ServiceProvider.PIPED
        }
        for (baseUrl in altProvider.fallbackUrls) {
            try {
                val detail = when (altProvider) {
                    ServiceProvider.PIPED -> piped.getStreamDetail(baseUrl, videoId)
                    ServiceProvider.INVIDIOUS -> invidious.getStreamDetail(baseUrl, videoId)
                    ServiceProvider.PEERTUBE -> peerTube.getStreamDetail(baseUrl, videoId)
                }
                val segments = sponsorJob.await()
                return@coroutineScope detail.copy(sponsorSegments = segments)
            } catch (_: Exception) {
            }
        }

        throw IllegalStateException("تعذر استخراج روابط البث من الخوادم الحالية. جرّب تبديل الخادم في تبويب الخدمات.")
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
