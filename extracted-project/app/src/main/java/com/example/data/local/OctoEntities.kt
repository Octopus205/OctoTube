package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(tableName = "subscriptions")
data class SubscriptionEntity(
    @PrimaryKey val channelId: String,
    val channelName: String,
    val avatarUrl: String?,
    val serviceId: String,
    val subscribedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "watch_history")
data class WatchHistoryEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelId: String,
    val channelName: String,
    val thumbnailUrl: String,
    val durationSeconds: Long,
    val lastPositionMs: Long,
    val serviceId: String,
    val watchedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_media")
data class DownloadedMediaEntity(
    @PrimaryKey val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String,
    val qualityLabel: String,
    val formatName: String,
    val statusName: String,
    val progress: Int,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val localFilePath: String,
    val subtitleFilePath: String?,
    val durationSeconds: Long,
    val serviceId: String,
    val createdAt: Long = System.currentTimeMillis()
)
