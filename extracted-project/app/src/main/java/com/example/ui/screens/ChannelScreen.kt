package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.domain.model.ChannelDetail
import com.example.domain.model.VideoSummary
import com.example.ui.UiLoadState
import com.example.ui.components.EmptyIllustrationCard
import com.example.ui.components.VideoFeedCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelState: UiLoadState<ChannelDetail>,
    isSubscribed: Boolean,
    onBack: () -> Unit,
    onToggleSubscribe: (ChannelDetail) -> Unit,
    onVideoClick: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopAppBar(
            title = {
                Text(
                    text = (channelState as? UiLoadState.Success)?.data?.name ?: "القناة",
                    maxLines = 1
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("channel_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        when (channelState) {
            is UiLoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            is UiLoadState.Error -> {
                EmptyIllustrationCard(
                    title = "تعذر تحميل القناة",
                    subtitle = channelState.message,
                    showArtwork = false
                )
            }
            is UiLoadState.Success -> {
                val channel = channelState.data
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!channel.avatarUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = channel.avatarUrl,
                                        contentDescription = channel.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        text = channel.name.take(1).uppercase(),
                                        style = MaterialTheme.typography.headlineMedium
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(channel.name, style = MaterialTheme.typography.headlineSmall)
                            if (channel.description.isNotBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = channel.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 3
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { onToggleSubscribe(channel) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSubscribed) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ),
                                modifier = Modifier.testTag("channel_subscribe_button")
                            ) {
                                if (isSubscribed) {
                                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("مشترك في القناة")
                                } else {
                                    Text("اشتراك محلي")
                                }
                            }
                        }
                    }

                    items(channel.videos, key = { it.id }) { video ->
                        VideoFeedCard(
                            video = video,
                            onClick = { onVideoClick(video) },
                            onChannelClick = {}
                        )
                    }
                }
            }
        }
    }
}
