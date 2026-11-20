package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface OctoDao {
    // Subscriptions
    @Query("SELECT * FROM subscriptions ORDER BY subscribedAt DESC")
    fun observeSubscriptions(): Flow<List<SubscriptionEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM subscriptions WHERE channelId = :channelId)")
    fun observeIsSubscribed(channelId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscription(subscription: SubscriptionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubscriptions(subscriptions: List<SubscriptionEntity>)

    @Query("DELETE FROM subscriptions WHERE channelId = :channelId")
    suspend fun deleteSubscription(channelId: String)

    // Watch History
    @Query("SELECT * FROM watch_history ORDER BY watchedAt DESC LIMIT 30")
    fun observeWatchHistory(): Flow<List<WatchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(entry: WatchHistoryEntity)

    @Query("DELETE FROM watch_history")
    suspend fun clearWatchHistory()

    // Downloaded Media
    @Query("SELECT * FROM downloaded_media ORDER BY createdAt DESC")
    fun observeDownloads(): Flow<List<DownloadedMediaEntity>>

    @Query("SELECT * FROM downloaded_media WHERE videoId = :videoId LIMIT 1")
    suspend fun getDownloadById(videoId: String): DownloadedMediaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(download: DownloadedMediaEntity)

    @Query("UPDATE downloaded_media SET statusName = :status, progress = :progress, downloadedBytes = :downloaded, totalBytes = :total WHERE videoId = :videoId")
    suspend fun updateDownloadProgress(
        videoId: String,
        status: String,
        progress: Int,
        downloaded: Long,
        total: Long
    )

    @Query("DELETE FROM downloaded_media WHERE videoId = :videoId")
    suspend fun deleteDownload(videoId: String)
}
