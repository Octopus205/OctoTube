package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.SubscriptionEntity
import com.example.domain.model.ServiceProvider
import com.example.domain.model.VideoSummary
import com.example.ui.UiLoadState
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.VideoFeedCard

@Composable
fun SubscriptionsScreen(
    subscriptions: List<SubscriptionEntity>,
    feedState: UiLoadState<List<VideoSummary>>,
    onRefreshFeed: () -> Unit,
    onExportUri: (android.net.Uri) -> Unit,
    onImportUri: (android.net.Uri) -> Unit,
    onChannelClick: (String, ServiceProvider) -> Unit,
    onVideoClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(onExportUri) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImportUri) }

    LaunchedEffect(subscriptions.size) {
        if (subscriptions.isNotEmpty()) {
            onRefreshFeed()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "الاشتراكات المحلية",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${subscriptions.size} قناة محفوظة بدون حساب Google",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("import_subscriptions_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.FileUpload,
                        contentDescription = "استيراد الاشتراكات",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = { exportLauncher.launch("octopipe_subscriptions.json") },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("export_subscriptions_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "تصدير الاشتراكات",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(
                    onClick = onRefreshFeed,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("refresh_subscriptions_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "تحديث الاشتراكات",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (subscriptions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    EmptyIllustrationCard(
                        title = "لا توجد اشتراكات بعد",
                        subtitle = "اشترك في قنواتك المفضلة من صفحة الفيديو لتظهر أحدث مقاطعها هنا بخصوصية تامة"
                    )
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("application/json")) },
                        modifier = Modifier.testTag("empty_import_subs_button")
                    ) {
                        Icon(Icons.Filled.FileUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("استيراد اشتراكات (JSON)")
                    }
                }
            }
            return
        }

        // Subscribed Channels Horizontal Strip
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(subscriptions, key = { it.channelId }) { sub ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(72.dp)
                        .testTag("sub_channel_${sub.channelId}")
                        .clickable {
                            onChannelClick(sub.channelId, ServiceProvider.fromId(sub.serviceId))
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!sub.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = sub.avatarUrl,
                                contentDescription = sub.channelName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = sub.channelName.take(1).uppercase(),
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = sub.channelName,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        when (feedState) {
            is UiLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is UiLoadState.Error -> {
                EmptyIllustrationCard(
                    title = "تعذر تحديث التغذية",
                    subtitle = feedState.message,
                    showArtwork = false
                )
            }
            is UiLoadState.Success -> {
                if (feedState.data.isEmpty()) {
                    EmptyIllustrationCard(
                        title = "اضغط على أي قناة أعلاه",
                        subtitle = "أو اضغط تحديث لجلب أحدث الفيديوهات من قنواتك المشترك بها",
                        showArtwork = false
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(feedState.data, key = { "${it.serviceProvider.id}_${it.id}" }) { video ->
                            VideoFeedCard(
                                video = video,
                                onClick = { onVideoClick(video) },
                                onChannelClick = { onChannelClick(video.channelId, video.serviceProvider) }
                            )
                        }
                    }
                }
            }
        }
    }
}
