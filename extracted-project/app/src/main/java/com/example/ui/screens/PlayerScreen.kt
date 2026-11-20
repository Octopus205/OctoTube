package com.example.ui.screens

import android.app.PictureInPictureParams
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRectF
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toRect
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.util.Consumer
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.domain.model.DownloadFormat
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceProvider
import com.example.domain.model.SponsorCategory
import com.example.domain.model.SponsorSegment
import com.example.domain.model.StreamDetail
import com.example.domain.model.VideoStreamOption
import com.example.domain.model.VideoSummary
import com.example.player.ActivePlaybackUiState
import com.example.ui.UiLoadState
import com.example.ui.components.OctoFormatters
import com.example.ui.components.VideoFeedCard
import com.example.ui.theme.IntroCyan
import com.example.ui.theme.OctoCoral
import com.example.ui.theme.OctoTeal
import com.example.ui.theme.OutroBlue
import com.example.ui.theme.SelfPromoYellow
import com.example.ui.theme.SponsorSegmentGreen

internal fun Context.findActivity(): ComponentActivity {
    var context = this
    while (context is ContextWrapper) {
        if (context is ComponentActivity) return context
        context = context.baseContext
    }
    throw IllegalStateException("PiP requires an Activity context")
}

private fun safePipAspectRatio(videoSize: VideoSize): Rational {
    if (videoSize == VideoSize.UNKNOWN || videoSize.width <= 0 || videoSize.height <= 0) {
        return Rational(16, 9)
    }
    val ratio = videoSize.width.toFloat() / videoSize.height.toFloat()
    val clamped = ratio.coerceIn(1f / 2.35f, 2.35f)
    val width = (clamped * 1000).toInt().coerceAtLeast(100)
    return Rational(width, 1000)
}

@OptIn(UnstableApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    extractState: UiLoadState<StreamDetail>,
    playbackState: ActivePlaybackUiState,
    mediaController: MediaController?,
    settings: OctoSettings,
    isSubscribed: Boolean,
    isInPipMode: Boolean,
    onBack: () -> Unit,
    onRetryPlayback: () -> Unit,
    onMinimizeToBackground: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSelectQuality: (VideoStreamOption) -> Unit,
    onSelectAdaptiveQuality: (Int?) -> Unit,
    onSelectSubtitle: (String?) -> Unit,
    onSetSpeed: (Float) -> Unit,
    onToggleAudioOnlyMode: (Boolean) -> Unit,
    onSkipSponsorNow: () -> Unit,
    onStartDownload: (StreamDetail, DownloadFormat, VideoStreamOption?) -> Unit,
    onToggleSubscribe: (StreamDetail) -> Unit,
    onOpenChannel: (String, ServiceProvider) -> Unit,
    onPlayRelated: (VideoSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showControls by remember { mutableStateOf(true) }
    var showQualitySheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showDownloadSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var descriptionExpanded by remember { mutableStateOf(false) }
    var isFullScreen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullScreen) {
        isFullScreen = false
    }

    DisposableEffect(isFullScreen, context) {
        val window = context.findActivity().window
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isFullScreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    val shouldAutoPip = settings.autoPipEnabled &&
        playbackState.isPlaying &&
        !playbackState.isAudioOnlyBackgroundMode

    // Pre-Android 12 PiP on leave
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S
    ) {
        DisposableEffect(context, shouldAutoPip, playbackState.videoSize) {
            val activity = context.findActivity()
            val listener = Runnable {
                if (shouldAutoPip) {
                    val params = PictureInPictureParams.Builder()
                        .setAspectRatio(safePipAspectRatio(playbackState.videoSize))
                        .build()
                    activity.enterPictureInPictureMode(params)
                }
            }
            activity.addOnUserLeaveHintListener(listener)
            onDispose {
                activity.removeOnUserLeaveHintListener(listener)
            }
        }
    }

    val pipModifier = Modifier.onGloballyPositioned { layoutCoordinates ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val builder = PictureInPictureParams.Builder()
            val rect = layoutCoordinates.boundsInWindow().toAndroidRectF().toRect()
            if (rect.width() > 0 && rect.height() > 0) {
                builder.setSourceRectHint(rect)
            }
            builder.setAspectRatio(safePipAspectRatio(playbackState.videoSize))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(shouldAutoPip)
            }
            runCatching {
                context.findActivity().setPictureInPictureParams(builder.build())
            }
        }
    }

    // Pure full-bleed video surface when inside PiP window
    if (isInPipMode) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            Media3VideoSurface(
                mediaController = mediaController,
                modifier = Modifier.fillMaxSize()
            )
        }
        return
    }

    val stream = (extractState as? UiLoadState.Success)?.data ?: playbackState.currentStream

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val wideLandscape = maxWidth >= 840.dp && maxWidth > maxHeight
        val playerHeight = maxWidth * (9f / 16f)
        // In tablet landscape, keep the player on the left and scrollable details/related videos on the right.
        // In portrait, the same content remains stacked vertically.
        // Cinema Player Box
        Box(
            modifier = if (isFullScreen) {
                Modifier.fillMaxSize().background(Color.Black)
            } else {
                Modifier
                    .width(if (wideLandscape) maxWidth * 0.58f else maxWidth)
                    .height(if (wideLandscape) maxHeight else playerHeight)
                    .background(Color.Black)
            }
                .then(pipModifier)
                .clickable { showControls = !showControls }
        ) {
            if (playbackState.isAudioOnlyBackgroundMode && stream != null) {
                AsyncImage(
                    model = stream.thumbnailUrl,
                    contentDescription = stream.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.Headphones,
                            contentDescription = "وضع الصوت في الخلفية",
                            tint = OctoTeal,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "وضع التشغيل بالخلفية (صوت فقط)",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                    }
                }
            } else {
                Media3VideoSurface(
                    mediaController = mediaController,
                    modifier = Modifier.fillMaxSize()
                )
            }

            if (playbackState.isBuffering || extractState is UiLoadState.Loading) {
                CircularProgressIndicator(
                    color = OctoCoral,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // SponsorBlock floating notification or manual Skip button
            playbackState.sponsorNoticeText?.let { notice ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 12.dp),
                    shape = CircleShape,
                    color = SponsorSegmentGreen.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            playbackState.activeSponsorSegment?.let { seg ->
                Button(
                    onClick = onSkipSponsorNow,
                    colors = ButtonDefaults.buttonColors(containerColor = SponsorSegmentGreen),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 52.dp)
                        .testTag("skip_sponsor_now_button")
                ) {
                    Icon(Icons.Filled.SkipNext, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("تخطي ${seg.category.labelAr}", color = Color.Black)
                }
            }

            // Overlay Video Controls
            androidx.compose.animation.AnimatedVisibility(
                visible = showControls,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f))
                ) {
                    // Top bar inside player
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("player_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = Color.White
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { showSubtitleSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_cc_button")
                            ) {
                                Icon(
                                    imageVector = if (playbackState.activeSubtitleCode != null) {
                                        Icons.Filled.ClosedCaption
                                    } else {
                                        Icons.Filled.ClosedCaptionOff
                                    },
                                    contentDescription = "الترجمة",
                                    tint = if (playbackState.activeSubtitleCode != null) OctoTeal else Color.White
                                )
                            }
                            IconButton(
                                onClick = { showSpeedSheet = true },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_speed_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Speed,
                                    contentDescription = "السرعة",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val params = PictureInPictureParams.Builder()
                                            .setAspectRatio(safePipAspectRatio(playbackState.videoSize))
                                            .build()
                                        context.findActivity().enterPictureInPictureMode(params)
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_pip_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PictureInPictureAlt,
                                    contentDescription = "صورة داخل صورة",
                                    tint = Color.White
                                )
                            }
                            IconButton(
                                onClick = { isFullScreen = !isFullScreen },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("player_fullscreen_button")
                            ) {
                                Icon(
                                    imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                    contentDescription = if (isFullScreen) "إنهاء ملء الشاشة" else "ملء الشاشة",
                                    tint = Color.White
                                )
                            }
                        }
                    }

                    // Center transport controls
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { onSeekBy(-10_000L) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_rewind_10")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Replay10,
                                contentDescription = "ترجيع 10 ثوان",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = OctoCoral.copy(alpha = 0.9f),
                            modifier = Modifier
                                .size(56.dp)
                                .testTag("player_play_pause")
                                .clickable(onClick = onTogglePlayPause)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    contentDescription = if (playbackState.isPlaying) "إيقاف" else "تشغيل",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = { onSeekBy(10_000L) },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("player_forward_10")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Forward10,
                                contentDescription = "تقديم 10 ثوان",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    // Bottom seekbar with SponsorBlock markers
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        val duration = playbackState.durationMs.coerceAtLeast(1L)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Slider(
                                value = playbackState.positionMs.toFloat().coerceIn(0f, duration.toFloat()),
                                onValueChange = { onSeekTo(it.toLong()) },
                                valueRange = 0f..duration.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = OctoCoral,
                                    activeTrackColor = OctoCoral,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.35f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                            )
                            if (stream != null && stream.sponsorSegments.isNotEmpty() && duration > 1000L) {
                                SponsorTimelineOverlay(
                                    segments = stream.sponsorSegments,
                                    durationMs = duration,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .align(Alignment.Center)
                                        .padding(horizontal = 10.dp)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${OctoFormatters.formatMillis(playbackState.positionMs)} / ${OctoFormatters.formatMillis(duration)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White
                            )
                            Text(
                                text = "${playbackState.selectedQuality?.qualityLabel ?: "Auto"} • ${playbackState.playbackSpeed}x",
                                style = MaterialTheme.typography.labelSmall,
                                color = OctoTeal
                            )
                        }
                    }
                }
            }
        }

        // Keep the video surface edge-to-edge while fullscreen is active.
        if (!isFullScreen) {
        // Scrollable Details Area
        when (extractState) {
            is UiLoadState.Loading -> {
                Box(
                    modifier = Modifier
                        .width(if (wideLandscape) maxWidth * 0.42f else maxWidth)
                        .height(if (wideLandscape) maxHeight else (maxHeight - playerHeight).coerceAtLeast(0.dp))
                        .align(if (wideLandscape) Alignment.TopEnd else Alignment.TopStart)
                        .offset(y = if (wideLandscape) 0.dp else playerHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "جاري تجهيز البث والترجمات وفحص SponsorBlock…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            is UiLoadState.Error -> {
                Column(
                    modifier = Modifier
                        .width(if (wideLandscape) maxWidth * 0.42f else maxWidth)
                        .height(if (wideLandscape) maxHeight else (maxHeight - playerHeight).coerceAtLeast(0.dp))
                        .align(if (wideLandscape) Alignment.TopEnd else Alignment.TopStart)
                        .offset(y = if (wideLandscape) 0.dp else playerHeight)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = extractState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onBack) {
                        Text("العودة")
                    }
                }
            }

            is UiLoadState.Success -> {
                val detail = extractState.data
                LazyColumn(
                    modifier = Modifier
                        .width(if (wideLandscape) maxWidth * 0.42f else maxWidth)
                        .height(if (wideLandscape) maxHeight else (maxHeight - playerHeight).coerceAtLeast(0.dp))
                        .align(if (wideLandscape) Alignment.TopEnd else Alignment.TopStart)
                        .offset(y = if (wideLandscape) 0.dp else playerHeight),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title & Stats
                    item {
                        Column {
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = buildString {
                                    if (detail.views > 0) {
                                        append(OctoFormatters.formatViews(detail.views))
                                        append("  •  ")
                                    }
                                    append(detail.uploadDate)
                                    append("  •  ")
                                    append(detail.sourceInstance)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            playbackState.errorMessage?.let { err ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = err,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            if (playbackState.errorMessage != null) {
                                TextButton(
                                    onClick = onRetryPlayback,
                                    modifier = Modifier.testTag("player_retry_button")
                                ) {
                                    Text("إعادة محاولة التشغيل")
                                }
                            }
                        }
                    }

                    // Primary Action Buttons Strip (Background, PiP, Download, Quality, Subtitles)
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilledTonalButton(
                                    onClick = {
                                        onToggleAudioOnlyMode(!playbackState.isAudioOnlyBackgroundMode)
                                        if (!playbackState.isAudioOnlyBackgroundMode) {
                                            onMinimizeToBackground()
                                        }
                                    },
                                    modifier = Modifier.testTag("action_background_play")
                                ) {
                                    Icon(Icons.Filled.Headphones, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (playbackState.isAudioOnlyBackgroundMode) "إظهار الفيديو" else "تشغيل بالخلفية"
                                    )
                                }
                            }
                            item {
                                FilledTonalButton(
                                    onClick = { showDownloadSheet = true },
                                    modifier = Modifier.testTag("action_download")
                                ) {
                                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("تحميل")
                                }
                            }
                            item {
                                FilledTonalButton(
                                    onClick = { showQualitySheet = true },
                                    modifier = Modifier.testTag("action_quality")
                                ) {
                                    Icon(Icons.Filled.HighQuality, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(playbackState.selectedQuality?.qualityLabel ?: "الجودة")
                                }
                            }
                            item {
                                FilledTonalButton(
                                    onClick = { showSubtitleSheet = true },
                                    modifier = Modifier.testTag("action_subtitles")
                                ) {
                                    Icon(Icons.Filled.ClosedCaption, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (playbackState.activeSubtitleCode != null) {
                                            "ترجمة: ${playbackState.activeSubtitleCode}"
                                        } else {
                                            "الترجمة (${detail.subtitles.size})"
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Channel Bar & Local Subscribe Toggle
                    item {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenChannel(detail.channelId, detail.serviceProvider) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!detail.channelAvatarUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = detail.channelAvatarUrl,
                                                contentDescription = detail.channelName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Text(detail.channelName.take(1).uppercase())
                                        }
                                    }
                                    Column {
                                        Text(
                                            text = detail.channelName,
                                            style = MaterialTheme.typography.titleMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (detail.subscriberCountText.isNotBlank()) {
                                            Text(
                                                text = detail.subscriberCountText,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }

                                Button(
                                    onClick = { onToggleSubscribe(detail) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSubscribed) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                    ),
                                    modifier = Modifier.testTag("subscribe_channel_button")
                                ) {
                                    if (isSubscribed) {
                                        Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("مشترك")
                                    } else {
                                        Text("اشتراك")
                                    }
                                }
                            }
                        }
                    }

                    // SponsorBlock Segments Summary
                    if (detail.sponsorSegments.isNotEmpty()) {
                        item {
                            Card(
                                shape = MaterialTheme.shapes.medium,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Filled.FastForward, contentDescription = null, tint = SponsorSegmentGreen)
                                        Text(
                                            text = "مقاطع SponsorBlock المكتشفة (${detail.sponsorSegments.size})",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    detail.sponsorSegments.forEach { seg ->
                                        Text(
                                            text = "• ${seg.category.labelAr}: ${OctoFormatters.formatMillis(seg.startMs)} → ${OctoFormatters.formatMillis(seg.endMs)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { onSeekTo(seg.endMs) }
                                                .padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Chapters
                    if (detail.chapters.isNotEmpty()) {
                        item {
                            Column {
                                Text(
                                    text = "فصول الفيديو",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(detail.chapters) { chapter ->
                                        OutlinedButton(
                                            onClick = { onSeekTo(chapter.startSeconds * 1000L) },
                                            modifier = Modifier.testTag("chapter_${chapter.startSeconds}")
                                        ) {
                                            Text("${OctoFormatters.formatDuration(chapter.startSeconds)} • ${chapter.title}")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Description
                    if (detail.description.isNotBlank()) {
                        item {
                            Surface(
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { descriptionExpanded = !descriptionExpanded }
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "الوصف",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = detail.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = if (descriptionExpanded) 40 else 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Related Videos
                    if (detail.relatedVideos.isNotEmpty()) {
                        item {
                            Text(
                                text = "فيديوهات ذات صلة",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        items(detail.relatedVideos.take(12), key = { it.id }) { related ->
                            VideoFeedCard(
                                video = related,
                                onClick = { onPlayRelated(related) },
                                onChannelClick = { onOpenChannel(related.channelId, related.serviceProvider) }
                            )
                        }
                    }
                }
            }
        }
        }
    }

    // Quality Sheet
    if (showQualitySheet && stream != null) {
        ModalBottomSheet(onDismissRequest = { showQualitySheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("اختر جودة الفيديو", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                val options = stream.videoStreams.filter {
                    !it.videoOnly && it.mimeType.startsWith("video/", ignoreCase = true)
                }
                if (playbackState.availableVideoHeights.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onSelectAdaptiveQuality(null)
                            showQualitySheet = false
                        }.padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("تلقائي (Auto)")
                        if (playbackState.selectedVideoHeight == null) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                        }
                    }
                    HorizontalDivider()
                    playbackState.availableVideoHeights.forEach { height ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onSelectAdaptiveQuality(height)
                                showQualitySheet = false
                            }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${height}p • بث تكيفي")
                            if (playbackState.selectedVideoHeight == height) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                if (options.isEmpty() && playbackState.availableVideoHeights.isEmpty()) {
                    Text(
                        if (!stream.hlsUrl.isNullOrBlank()) {
                            "لا توجد روابط جودة مباشرة؛ يتم استخدام البث التكيفي HLS تلقائيًا."
                        } else {
                            "الخادم لم يوفر مسار فيديو يحتوي على الصوت والصورة."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    options.forEach { opt ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelectQuality(opt)
                                    showQualitySheet = false
                                }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${opt.qualityLabel} (${opt.mimeType})")
                            if (playbackState.selectedQuality?.url == opt.url) {
                                Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Subtitles Sheet
    if (showSubtitleSheet && stream != null) {
        ModalBottomSheet(onDismissRequest = { showSubtitleSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("الترجمة النصية (Subtitles)", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectSubtitle(null)
                            showSubtitleSheet = false
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("إيقاف الترجمة")
                    if (playbackState.activeSubtitleCode == null) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                    }
                }
                HorizontalDivider()
                stream.subtitles.forEach { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectSubtitle(track.languageCode)
                                showSubtitleSheet = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${track.languageName} (${track.languageCode})")
                        if (playbackState.activeSubtitleCode == track.languageCode) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Speed Sheet
    if (showSpeedSheet) {
        ModalBottomSheet(onDismissRequest = { showSpeedSheet = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("سرعة التشغيل", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(10.dp))
                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSetSpeed(speed)
                                showSpeedSheet = false
                            }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${speed}x")
                        if (playbackState.playbackSpeed == speed) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = OctoTeal)
                        }
                    }
                    HorizontalDivider()
                }
                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }

    // Download Sheet
    if (showDownloadSheet && stream != null) {
        ModalBottomSheet(onDismissRequest = { showDownloadSheet = false }) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("تحميل للمشاهدة بدون إنترنت", style = MaterialTheme.typography.titleLarge)
                val videoOpts = stream.videoStreams.filter {
                    !it.videoOnly && it.mimeType.contains("video/mp4", ignoreCase = true)
                }
                if (videoOpts.isEmpty()) {
                    Text(
                        "لا يوجد رابط MP4 مباشر لهذا الفيديو. قد يوفر الخادم بث HLS فقط، وهذا النوع يحتاج مدير تنزيل مخصصًا.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                videoOpts.take(5).forEach { opt ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onStartDownload(stream, DownloadFormat.VIDEO_MP4, opt)
                                showDownloadSheet = false
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Videocam, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("فيديو MP4 • ${opt.qualityLabel}", style = MaterialTheme.typography.titleSmall)
                                if (opt.contentLength > 0) {
                                    Text(OctoFormatters.formatBytes(opt.contentLength), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Icon(Icons.Filled.Download, contentDescription = null)
                        }
                    }
                }
                if (stream.audioStreams.any {
                    it.mimeType.contains("audio/mp4", ignoreCase = true) ||
                        it.mimeType.contains("audio/aac", ignoreCase = true)
                }) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onStartDownload(stream, DownloadFormat.AUDIO_M4A, null)
                            showDownloadSheet = false
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Filled.Audiotrack, contentDescription = null, tint = OctoTeal)
                        Column(modifier = Modifier.weight(1f)) {
                            Text("صوت فقط M4A (للاستماع بالخلفية)", style = MaterialTheme.typography.titleSmall)
                            Text(
                                stream.audioStreams.firstOrNull()?.qualityLabel ?: "128 kbps",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Icon(Icons.Filled.Download, contentDescription = null)
                    }
                }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun Media3VideoSurface(
    mediaController: MediaController?,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                player = mediaController
            }
        },
        update = { playerView ->
            if (playerView.player !== mediaController) {
                playerView.player = mediaController
            }
        },
        modifier = modifier
    )
}

@Composable
private fun SponsorTimelineOverlay(
    segments: List<SponsorSegment>,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val totalWidth = size.width
        val h = size.height
        segments.forEach { seg ->
            val startFrac = (seg.startMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            val endFrac = (seg.endMs.toFloat() / durationMs.toFloat()).coerceIn(startFrac, 1f)
            val x = startFrac * totalWidth
            val w = ((endFrac - startFrac) * totalWidth).coerceAtLeast(4f)
            val color = when (seg.category) {
                SponsorCategory.SPONSOR -> SponsorSegmentGreen
                SponsorCategory.SELF_PROMO -> SelfPromoYellow
                SponsorCategory.INTRO -> IntroCyan
                SponsorCategory.OUTRO -> OutroBlue
                else -> OctoCoral
            }
            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(w, h)
            )
        }
    }
}
