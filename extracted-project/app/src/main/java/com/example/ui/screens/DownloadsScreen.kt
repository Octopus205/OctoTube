package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.DownloadFormat
import com.example.domain.model.DownloadStatus
import com.example.domain.model.DownloadTask
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.OctoFormatters
import com.example.ui.theme.OctoTeal

private enum class DownloadFilter(val label: String) {
    ALL("الكل"),
    VIDEO("فيديو MP4"),
    AUDIO("صوت فقط"),
    ACTIVE("قيد التحميل")
}

@Composable
fun DownloadsScreen(
    downloads: List<DownloadTask>,
    onPlayOffline: (DownloadTask) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteDownload: (String) -> Unit,
    onChooseFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(DownloadFilter.ALL) }

    val filtered = remember(downloads, selectedFilter) {
        when (selectedFilter) {
            DownloadFilter.ALL -> downloads
            DownloadFilter.VIDEO -> downloads.filter { it.format == DownloadFormat.VIDEO_MP4 || it.format == DownloadFormat.VIDEO_HLS_TS }
            DownloadFilter.AUDIO -> downloads.filter { it.format == DownloadFormat.AUDIO_M4A }
            DownloadFilter.ACTIVE -> downloads.filter {
                it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
            }
        }
    }

    val completedCount = downloads.count { it.status == DownloadStatus.COMPLETED }
    val totalUsedBytes = downloads.filter { it.status == DownloadStatus.COMPLETED }
        .sumOf { it.downloadedBytes }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header + Storage Summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "التحميلات والأوفلاين",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "$completedCount ملف جاهز • ${OctoFormatters.formatBytes(totalUsedBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                androidx.compose.material3.TextButton(onClick = onChooseFolder) {
                    Icon(Icons.Filled.DownloadDone, contentDescription = null)
                    Text("مجلد الحفظ")
                }
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.DownloadDone,
                        contentDescription = null,
                        tint = OctoTeal,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "بدون إنترنت",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(DownloadFilter.entries) { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag("download_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyIllustrationCard(
                    title = "لا توجد تحميلات",
                    subtitle = "افتح أي فيديو واضغط على زر «تحميل» لحفظه بجودة الفيديو أو كملف صوتي للاستماع دون إنترنت"
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filtered, key = { it.videoId }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPlay = { onPlayOffline(task) },
                        onCancel = { onCancelDownload(task.videoId) },
                        onDelete = { onDeleteDownload(task.videoId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPlay: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val isCompleted = task.status == DownloadStatus.COMPLETED
    val isDownloading = task.status == DownloadStatus.DOWNLOADING

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("download_card_${task.videoId}")
            .clickable(enabled = isCompleted, onClick = onPlay),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 104.dp, height = 64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    AsyncImage(
                        model = task.thumbnailUrl,
                        contentDescription = task.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = if (task.format == DownloadFormat.AUDIO_M4A) {
                                    Icons.Filled.Audiotrack
                                } else {
                                    Icons.Filled.Videocam
                                },
                                contentDescription = null,
                                tint = OctoTeal,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = task.qualityLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${task.channelName} • ${task.status.labelAr}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isCompleted) OctoTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (task.downloadedBytes > 0L) {
                        val sizeText = if (task.totalBytes > 0L) {
                            "${OctoFormatters.formatBytes(task.downloadedBytes)} / ${OctoFormatters.formatBytes(task.totalBytes)}"
                        } else {
                            OctoFormatters.formatBytes(task.downloadedBytes)
                        }
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isCompleted) {
                    IconButton(
                        onClick = onPlay,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("play_offline_${task.videoId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "تشغيل بدون إنترنت",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                } else if (isDownloading) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("pause_download_${task.videoId}")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Pause,
                            contentDescription = "إيقاف التحميل",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_download_${task.videoId}")
                ) {
                    Icon(
                        imageVector = Icons.Filled.DeleteOutline,
                        contentDescription = "حذف الملف",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (task.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}
