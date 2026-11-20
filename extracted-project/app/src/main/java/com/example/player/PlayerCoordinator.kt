package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.TrackGroup
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.domain.model.DownloadTask
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceProvider
import com.example.domain.model.SponsorSegment
import com.example.domain.model.StreamDetail
import com.example.domain.model.SubtitleTrack
import com.example.domain.model.VideoStreamOption
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class ActivePlaybackUiState(
    val currentStream: StreamDetail? = null,
    val offlineTask: DownloadTask? = null,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPercent: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val selectedQuality: VideoStreamOption? = null,
    val availableVideoHeights: List<Int> = emptyList(),
    val selectedVideoHeight: Int? = null,
    val activeSubtitleCode: String? = null,
    val isAudioOnlyBackgroundMode: Boolean = false,
    val videoSize: VideoSize = VideoSize.UNKNOWN,
    val activeSponsorSegment: SponsorSegment? = null,
    val sponsorNoticeText: String? = null,
    val errorMessage: String? = null
)

@OptIn(UnstableApi::class)
class PlayerCoordinator(
    private val appContext: Context,
    private val onSponsorSkipped: suspend (Long) -> Unit,
    private val onSaveProgress: suspend (StreamDetail, Long) -> Unit
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var tickerJob: Job? = null
    private var lastSkippedUuid: String? = null
    private var currentSettings: OctoSettings = OctoSettings()
    private var fallbackCandidates: List<VideoStreamOption?> = emptyList()
    private var fallbackIndex: Int = 0
    private var adaptiveVideoGroups: List<Pair<TrackGroup, Int>> = emptyList()

    private val _playerState = MutableStateFlow(ActivePlaybackUiState())
    val playerState: StateFlow<ActivePlaybackUiState> = _playerState.asStateFlow()

    private val _controllerFlow = MutableStateFlow<MediaController?>(null)
    val controllerFlow: StateFlow<MediaController?> = _controllerFlow.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _playerState.update {
                it.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = (mediaController?.duration ?: 0L).coerceAtLeast(0L)
                )
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            _playerState.update { it.copy(videoSize = videoSize) }
        }

        override fun onTracksChanged(tracks: Tracks) {
            val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
            val options = videoGroups.flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    val height = group.getTrackFormat(index).height
                    if (height > 0 && group.isTrackSupported(index)) height else null
                }
            }.distinct().sortedDescending()
            adaptiveVideoGroups = videoGroups.flatMap { group ->
                (0 until group.length).mapNotNull { index ->
                    val height = group.getTrackFormat(index).height
                    if (height > 0 && group.isTrackSupported(index)) group.mediaTrackGroup to index else null
                }
            }
            val selectedHeight = videoGroups.flatMap { group ->
                (0 until group.length).filter { group.isTrackSelected(it) }
                    .map { group.getTrackFormat(it).height }
            }.firstOrNull { it > 0 }
            _playerState.update {
                it.copy(availableVideoHeights = options, selectedVideoHeight = selectedHeight)
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            val stream = _playerState.value.currentStream
            val nextIndex = fallbackIndex + 1
            if (stream != null && nextIndex < fallbackCandidates.size) {
                fallbackIndex = nextIndex
                val nextOption = fallbackCandidates[nextIndex]
                val nextUrl = nextOption?.url ?: stream.hlsUrl
                val controller = mediaController
                if (!nextUrl.isNullOrBlank() && controller != null) {
                    _playerState.update {
                        it.copy(
                            selectedQuality = nextOption,
                            isBuffering = true,
                            isPlaying = false,
                            errorMessage = "تعذر تشغيل المسار السابق؛ جارٍ تجربة جودة/مسار بديل…"
                        )
                    }
                    val item = buildMediaItem(
                        uri = nextUrl,
                        id = stream.id,
                        title = stream.title,
                        artist = stream.channelName,
                        artworkUrl = stream.thumbnailUrl,
                        subtitles = stream.subtitles,
                        mimeType = nextOption?.mimeType ?: MimeTypes.APPLICATION_M3U8
                    )
                    controller.setMediaItem(item, controller.currentPosition.coerceAtLeast(0L))
                    controller.prepare()
                    controller.playWhenReady = true
                    return
                }
            }

            val cause = error.cause?.message?.takeIf { it.isNotBlank() }
            _playerState.update {
                it.copy(
                    errorMessage = buildString {
                        append("فشل تشغيل جميع مسارات الفيديو المتاحة: ${error.errorCodeName}")
                        if (cause != null) append(" — ").append(cause.take(140))
                        append(". جرّب خادمًا آخر أو أعد المحاولة لاحقًا.")
                    },
                    isBuffering = false,
                    isPlaying = false
                )
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val token = SessionToken(appContext, ComponentName(appContext, OctoPlaybackService::class.java))
        val future = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                try {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    _controllerFlow.value = controller
                    startTicker()
                } catch (error: Exception) {
                    controllerFuture = null
                    _playerState.update {
                        it.copy(
                            isBuffering = false,
                            errorMessage = "تعذر تهيئة مشغل الفيديو: ${error.cause?.message ?: error.message ?: "خطأ غير معروف"}"
                        )
                    }
                }
            },
            ContextCompat.getMainExecutor(appContext)
        )
    }

    fun updateSettings(settings: OctoSettings) {
        currentSettings = settings
    }

    fun playStream(
        detail: StreamDetail,
        settings: OctoSettings,
        startPositionMs: Long = 0L
    ) {
        currentSettings = settings
        connect()
        lastSkippedUuid = null

        val preferredHeight = Regex("(\\d+)").find(settings.preferredQuality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 720
        val chosenVideo = pickBestVideoOption(detail, preferredHeight)
        val progressiveOptions = detail.videoStreams
            .filter { !it.videoOnly && it.url.isNotBlank() && it.mimeType.startsWith("video/", true) }
            .sortedBy { kotlin.math.abs(it.height - preferredHeight) }
        fallbackCandidates = buildList {
            chosenVideo?.let { add(it) }
            progressiveOptions.forEach { if (it.url != chosenVideo?.url) add(it) }
            if (!detail.hlsUrl.isNullOrBlank()) add(null)
        }.distinctBy { it?.url ?: detail.hlsUrl }
        fallbackIndex = 0
        val playableUri = choosePlayableUri(detail, chosenVideo, audioOnly = false)
        if (playableUri.isNullOrBlank()) {
            _playerState.update {
                it.copy(
                    currentStream = detail,
                    isBuffering = false,
                    errorMessage = "الخادم لم يُرجع رابط فيديو صالحًا. جرّب تبديل الخادم من تبويب الخدمات."
                )
            }
            return
        }

        val initialSubCode = if (settings.subtitlesAutoEnable) {
            detail.subtitles.firstOrNull {
                it.languageCode.startsWith(settings.preferredSubtitleLang, ignoreCase = true)
            }?.languageCode ?: detail.subtitles.firstOrNull()?.languageCode
        } else null

        _playerState.update {
            ActivePlaybackUiState(
                currentStream = detail,
                offlineTask = null,
                isBuffering = true,
                durationMs = detail.durationSeconds * 1000L,
                selectedQuality = chosenVideo,
                activeSubtitleCode = initialSubCode,
                isAudioOnlyBackgroundMode = false
            )
        }

        scope.launch {
            val controller = awaitController() ?: return@launch
            val mediaItem = buildMediaItem(
                uri = playableUri,
                id = detail.id,
                title = detail.title,
                artist = detail.channelName,
                artworkUrl = detail.thumbnailUrl,
                subtitles = detail.subtitles,
                mimeType = if (playableUri == detail.hlsUrl) MimeTypes.APPLICATION_M3U8 else chosenVideo?.mimeType
            )
            applySubtitleSelection(controller, initialSubCode)
            controller.setMediaItem(mediaItem, startPositionMs)
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    fun playOfflineTask(task: DownloadTask) {
        connect()
        lastSkippedUuid = null
        val storedUri = runCatching { Uri.parse(task.localFilePath) }.getOrNull()
        val isContentUri = storedUri?.scheme == "content"
        val file = File(task.localFilePath)
        if (isContentUri) {
            val canRead = runCatching { appContext.contentResolver.openFileDescriptor(storedUri!!, "r")?.use { true } ?: false }.getOrDefault(false)
            if (!canRead) {
                _playerState.update { it.copy(errorMessage = "الملف المحمل غير موجود أو لم يعد المجلد متاحًا") }
                return
            }
        } else if (!file.exists()) {
            _playerState.update { it.copy(errorMessage = "الملف المحمل غير موجود على الجهاز") }
            return
        }

        val syntheticDetail = StreamDetail(
            id = task.videoId,
            title = task.title,
            description = "تشغيل محلي بدون إنترنت (${task.qualityLabel})",
            channelId = "offline",
            channelName = task.channelName,
            channelAvatarUrl = null,
            subscriberCountText = "مكتبة التحميلات",
            thumbnailUrl = task.thumbnailUrl,
            durationSeconds = task.durationSeconds,
            views = 0L,
            likes = 0L,
            uploadDate = "محمل محلياً",
            serviceProvider = ServiceProvider.fromId(task.serviceId),
            hlsUrl = null,
            videoStreams = emptyList(),
            audioStreams = emptyList(),
            subtitles = task.subtitleFilePath?.let { path ->
                if (File(path).exists()) {
                    listOf(
                        SubtitleTrack(
                            url = Uri.fromFile(File(path)).toString(),
                            languageCode = "ar",
                            languageName = "الترجمة المحفوظة"
                        )
                    )
                } else emptyList()
            } ?: emptyList(),
            chapters = emptyList(),
            relatedVideos = emptyList(),
            sourceInstance = "Offline Storage"
        )

        _playerState.update {
            ActivePlaybackUiState(
                currentStream = syntheticDetail,
                offlineTask = task,
                durationMs = task.durationSeconds * 1000L,
                isAudioOnlyBackgroundMode = task.format.extension == "m4a"
            )
        }

        scope.launch {
            val controller = awaitController() ?: return@launch
            val mediaItem = buildMediaItem(
                uri = if (isContentUri) storedUri.toString() else Uri.fromFile(file).toString(),
                id = task.videoId,
                title = task.title,
                artist = task.channelName,
                artworkUrl = task.thumbnailUrl,
                subtitles = syntheticDetail.subtitles
            )
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    fun switchQuality(option: VideoStreamOption) {
        val detail = _playerState.value.currentStream ?: return
        val controller = mediaController ?: return
        val resumePos = controller.currentPosition
        val wasPlaying = controller.playWhenReady

        _playerState.update {
            it.copy(
                selectedQuality = option,
                isAudioOnlyBackgroundMode = false,
                isBuffering = true,
                errorMessage = null
            )
        }
        val mediaItem = buildMediaItem(
            uri = option.url,
            id = detail.id,
            title = detail.title,
            artist = detail.channelName,
            artworkUrl = detail.thumbnailUrl,
            subtitles = detail.subtitles,
            mimeType = option.mimeType
        )
        controller.setMediaItem(mediaItem, resumePos)
        controller.prepare()
        controller.playWhenReady = wasPlaying
    }

    fun switchAdaptiveQuality(height: Int?) {
        val controller = mediaController ?: return
        val matching = height?.let { requested ->
            adaptiveVideoGroups.firstOrNull { (group, index) ->
                group.getFormat(index).height == requested
            }
        }
        val builder = controller.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
        if (matching != null) {
            builder.setOverrideForType(TrackSelectionOverride(matching.first, listOf(matching.second)))
        }
        controller.trackSelectionParameters = builder.build()
        _playerState.update { it.copy(selectedVideoHeight = height) }
    }

    fun toggleBackgroundAudioMode(enableAudioOnly: Boolean) {
        val detail = _playerState.value.currentStream ?: return
        val controller = mediaController ?: return
        val resumePos = controller.currentPosition
        val audioUrl = detail.audioStreams.firstOrNull()?.url

        _playerState.update { it.copy(isAudioOnlyBackgroundMode = enableAudioOnly) }
        if (enableAudioOnly && !audioUrl.isNullOrBlank()) {
            val mediaItem = buildMediaItem(
                uri = audioUrl,
                id = detail.id,
                title = detail.title,
                artist = detail.channelName,
                artworkUrl = detail.thumbnailUrl,
                subtitles = emptyList()
            )
            controller.setMediaItem(mediaItem, resumePos)
            controller.prepare()
            controller.playWhenReady = true
        } else if (!enableAudioOnly) {
            val url = choosePlayableUri(detail, _playerState.value.selectedQuality, audioOnly = false)
                ?: return
            val mediaItem = buildMediaItem(
                uri = url,
                id = detail.id,
                title = detail.title,
                artist = detail.channelName,
                artworkUrl = detail.thumbnailUrl,
                subtitles = detail.subtitles
            )
            controller.setMediaItem(mediaItem, resumePos)
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    fun selectSubtitle(languageCode: String?) {
        _playerState.update { it.copy(activeSubtitleCode = languageCode) }
        val controller = mediaController ?: return
        applySubtitleSelection(controller, languageCode)
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) controller.pause() else controller.play()
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _playerState.update { it.copy(positionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun seekBy(deltaMs: Long) {
        val controller = mediaController ?: return
        val target = (controller.currentPosition + deltaMs).coerceIn(0L, controller.duration.coerceAtLeast(0L))
        controller.seekTo(target)
    }

    fun setSpeed(speed: Float) {
        mediaController?.setPlaybackSpeed(speed)
        _playerState.update { it.copy(playbackSpeed = speed) }
    }

    fun skipActiveSponsorSegment() {
        val seg = _playerState.value.activeSponsorSegment ?: return
        lastSkippedUuid = seg.uuid
        seekTo(seg.endMs)
        _playerState.update {
            it.copy(
                activeSponsorSegment = null,
                sponsorNoticeText = "تم تخطي: ${seg.category.labelAr}"
            )
        }
        scope.launch {
            onSponsorSkipped(((seg.endMs - seg.startMs) / 1000L).coerceAtLeast(1L))
            delay(2800L)
            _playerState.update { it.copy(sponsorNoticeText = null) }
        }
    }

    fun stopAndClear() {
        mediaController?.stop()
        mediaController?.clearMediaItems()
        _playerState.value = ActivePlaybackUiState()
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            var historyTick = 0
            while (isActive) {
                val controller = mediaController
                val stream = _playerState.value.currentStream
                if (controller != null && stream != null) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0 } ?: (stream.durationSeconds * 1000L)
                    val buffered = controller.bufferedPercentage

                    // Check SponsorBlock segments
                    val matchingSegment = if (currentSettings.sponsorBlockEnabled) {
                        stream.sponsorSegments.firstOrNull { seg ->
                            pos in seg.startMs until (seg.endMs - 400L)
                        }
                    } else null

                    if (matchingSegment != null &&
                        currentSettings.autoSkipSponsor &&
                        lastSkippedUuid != matchingSegment.uuid
                    ) {
                        lastSkippedUuid = matchingSegment.uuid
                        controller.seekTo(matchingSegment.endMs)
                        val savedSec = ((matchingSegment.endMs - matchingSegment.startMs) / 1000L).coerceAtLeast(1L)
                        launch { onSponsorSkipped(savedSec) }
                        _playerState.update {
                            it.copy(
                                positionMs = matchingSegment.endMs,
                                durationMs = dur,
                                bufferedPercent = buffered,
                                activeSponsorSegment = null,
                                sponsorNoticeText = "SponsorBlock: تم تخطي ${matchingSegment.category.labelAr}"
                            )
                        }
                        launch {
                            delay(3000L)
                            _playerState.update { it.copy(sponsorNoticeText = null) }
                        }
                    } else {
                        _playerState.update {
                            it.copy(
                                positionMs = pos,
                                durationMs = dur,
                                bufferedPercent = buffered,
                                isPlaying = controller.isPlaying,
                                activeSponsorSegment = matchingSegment
                            )
                        }
                    }

                    historyTick++
                    if (historyTick % 10 == 0 && pos > 2000L) {
                        launch(Dispatchers.IO) { onSaveProgress(stream, pos) }
                    }
                }
                delay(400L)
            }
        }
    }

    private suspend fun awaitController(): MediaController? {
        mediaController?.let { return it }
        repeat(100) {
            delay(100L)
            mediaController?.let { return it }
        }
        return null
    }

    private fun pickBestVideoOption(detail: StreamDetail, targetHeight: Int): VideoStreamOption? {
        val progressive = detail.videoStreams.filter {
            !it.videoOnly && it.url.isNotBlank() && it.mimeType.startsWith("video/", ignoreCase = true)
        }
        return progressive.minByOrNull { kotlin.math.abs(it.height - targetHeight) }
    }

    private fun choosePlayableUri(
        detail: StreamDetail,
        chosenVideo: VideoStreamOption?,
        audioOnly: Boolean
    ): String? {
        if (audioOnly) {
            return detail.audioStreams.firstOrNull()?.url ?: chosenVideo?.url ?: detail.hlsUrl
        }
        // Prefer a progressive stream with both audio and video. If none exists,
        // use the provider's adaptive HLS manifest; never send a video-only URL
        // to the player as if it contained audio.
        return chosenVideo?.url?.takeIf { it.isNotBlank() }
            ?: detail.hlsUrl?.takeIf { it.isNotBlank() }
    }

    private fun buildMediaItem(
        uri: String,
        id: String,
        title: String,
        artist: String,
        artworkUrl: String,
        subtitles: List<SubtitleTrack>,
        mimeType: String? = null
    ): MediaItem {
        val subtitleConfigs = subtitles.mapNotNull { track ->
            val resolvedUri = if (track.url == "local://demo/ar") {
                ensureDemoVttUri(id)
            } else {
                Uri.parse(track.url)
            }
            MediaItem.SubtitleConfiguration.Builder(resolvedUri)
                .setMimeType(
                    if (track.mimeType.contains("ttml", ignoreCase = true)) {
                        MimeTypes.APPLICATION_TTML
                    } else {
                        MimeTypes.TEXT_VTT
                    }
                )
                .setLanguage(track.languageCode)
                .setLabel(track.languageName)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setArtworkUri(Uri.parse(artworkUrl))
            .build()

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .setSubtitleConfigurations(subtitleConfigs)
            .apply {
                val path = Uri.parse(uri).lastPathSegment.orEmpty().lowercase()
                val declaredMime = mimeType?.substringBefore(";")?.lowercase().orEmpty()
                when {
                    declaredMime.contains("mpegurl") || declaredMime.contains("vnd.apple") ||
                        path.endsWith(".m3u8") -> setMimeType(MimeTypes.APPLICATION_M3U8)
                    declaredMime.contains("dash+xml") || path.endsWith(".mpd") ->
                        setMimeType(MimeTypes.APPLICATION_MPD)
                    declaredMime.startsWith("video/") -> setMimeType(declaredMime)
                    declaredMime.startsWith("audio/") -> setMimeType(declaredMime)
                }
            }
            .build()
    }

    private fun applySubtitleSelection(controller: MediaController, languageCode: String?) {
        val builder = controller.trackSelectionParameters.buildUpon()
        if (languageCode == null) {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
        } else {
            builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            builder.setPreferredTextLanguage(languageCode)
        }
        controller.trackSelectionParameters = builder.build()
    }

    private fun ensureDemoVttUri(videoId: String): Uri {
        val vttFile = File(appContext.cacheDir, "demo_sub_${videoId}.vtt")
        if (!vttFile.exists()) {
            vttFile.writeText(
                """
                WEBVTT

                00:00:01.000 --> 00:00:07.000
                مرحباً بك في مشغل OctoPipe المستقل الخالي من الإعلانات

                00:00:07.500 --> 00:00:15.000
                يدعم التشغيل بالخلفية، صورة داخل صورة، وتخطي الرعايات تلقائياً

                00:00:15.500 --> 00:00:25.000
                يمكنك تبديل الجودة أو تحميل الفيديو للمشاهدة بدون إنترنت
                """.trimIndent(),
                Charsets.UTF_8
            )
        }
        return Uri.fromFile(vttFile)
    }

    fun release() {
        tickerJob?.cancel()
        mediaController?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        mediaController = null
    }
}
