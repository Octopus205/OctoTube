package com.example.data.repository

import android.content.Context
import android.net.Uri
import com.example.core.network.FeedFetchResult
import com.example.core.network.NetworkModule
import com.example.core.network.OctoExtractorEngine
import com.example.data.local.DownloadedMediaEntity
import com.example.data.local.OctoDao
import com.example.data.local.OctoPreferences
import com.example.data.local.SubscriptionEntity
import com.example.data.local.WatchHistoryEntity
import com.example.domain.model.ChannelDetail
import com.example.domain.model.DownloadFormat
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceInstanceHealth
import com.example.domain.model.ServiceProvider
import com.example.domain.model.StreamDetail
import com.example.domain.model.VideoSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class OctoRepository(
    private val context: Context,
    private val dao: OctoDao,
    val preferences: OctoPreferences,
    val extractor: OctoExtractorEngine = OctoExtractorEngine()
) {
    val settingsFlow: Flow<OctoSettings> = preferences.settingsFlow
    val subscriptionsFlow: Flow<List<SubscriptionEntity>> = dao.observeSubscriptions()
    val watchHistoryFlow: Flow<List<WatchHistoryEntity>> = dao.observeWatchHistory()

    val downloadsFlow: Flow<List<DownloadTask>> = dao.observeDownloads().map { list ->
        list.map { it.toDomain() }
    }

    fun observeIsSubscribed(channelId: String): Flow<Boolean> =
        dao.observeIsSubscribed(channelId)

    suspend fun getTrending(settings: OctoSettings): FeedFetchResult =
        extractor.fetchTrending(settings)

    suspend fun search(query: String, settings: OctoSettings): FeedFetchResult =
        extractor.search(query, settings)

    fun extractVideoIdIfUrl(input: String): String? =
        extractor.extractVideoIdFromInput(input)

    suspend fun getStreamDetail(
        videoId: String,
        provider: ServiceProvider,
        settings: OctoSettings
    ): StreamDetail {
        val detail = extractor.extractStreamDetail(videoId, provider, settings)
        preferences.incrementAdsBlocked(2)
        return detail
    }

    suspend fun getChannelDetail(
        channelId: String,
        provider: ServiceProvider,
        settings: OctoSettings
    ): ChannelDetail = extractor.fetchChannel(channelId, provider, settings)

    suspend fun toggleSubscription(
        channelId: String,
        channelName: String,
        avatarUrl: String?,
        provider: ServiceProvider
    ): Boolean {
        val currentlySubscribed = dao.observeIsSubscribed(channelId).first()
        return if (currentlySubscribed) {
            dao.deleteSubscription(channelId)
            false
        } else {
            dao.insertSubscription(
                SubscriptionEntity(
                    channelId = channelId,
                    channelName = channelName,
                    avatarUrl = avatarUrl,
                    serviceId = provider.id
                )
            )
            true
        }
    }

    suspend fun fetchSubscriptionsFeed(
        subscriptions: List<SubscriptionEntity>,
        settings: OctoSettings
    ): List<VideoSummary> = coroutineScope {
        if (subscriptions.isEmpty()) return@coroutineScope emptyList()
        subscriptions.take(10).map { sub ->
            async {
                try {
                    val provider = ServiceProvider.fromId(sub.serviceId)
                    extractor.fetchChannel(sub.channelId, provider, settings).videos.take(5)
                } catch (_: Exception) {
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    suspend fun recordWatchProgress(
        videoId: String,
        title: String,
        channelId: String,
        channelName: String,
        thumbnailUrl: String,
        durationSeconds: Long,
        positionMs: Long,
        serviceId: String
    ) {
        dao.upsertWatchHistory(
            WatchHistoryEntity(
                videoId = videoId,
                title = title,
                channelId = channelId,
                channelName = channelName,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = durationSeconds,
                lastPositionMs = positionMs,
                serviceId = serviceId,
                watchedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clearWatchHistory() = dao.clearWatchHistory()

    suspend fun checkAllInstancesHealth(settings: OctoSettings): List<ServiceInstanceHealth> =
        extractor.checkAllInstancesHealth(settings)

    suspend fun exportSubscriptionsToUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val subs = dao.observeSubscriptions().first()
        val json = NetworkModule.json.encodeToString(subs)
        context.contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(json.toByteArray(Charsets.UTF_8))
        }
        subs.size
    }

    suspend fun importSubscriptionsFromUri(uri: Uri): Int = withContext(Dispatchers.IO) {
        val raw = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader(Charsets.UTF_8).readText()
        } ?: return@withContext 0
        val imported = NetworkModule.json.decodeFromString<List<SubscriptionEntity>>(raw)
        if (imported.isNotEmpty()) {
            dao.insertSubscriptions(imported)
        }
        imported.size
    }

    private fun DownloadedMediaEntity.toDomain(): DownloadTask = DownloadTask(
        videoId = videoId,
        title = title,
        channelName = channelName,
        thumbnailUrl = thumbnailUrl,
        qualityLabel = qualityLabel,
        format = runCatching { DownloadFormat.valueOf(formatName) }
            .getOrDefault(DownloadFormat.VIDEO_MP4),
        status = runCatching { DownloadStatus.valueOf(statusName) }
            .getOrDefault(DownloadStatus.QUEUED),
        progress = progress,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        localFilePath = localFilePath,
        subtitleFilePath = subtitleFilePath,
        durationSeconds = durationSeconds,
        serviceId = serviceId,
        createdAt = createdAt
    )
}
