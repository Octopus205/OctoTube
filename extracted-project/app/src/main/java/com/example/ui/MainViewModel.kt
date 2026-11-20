package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.OctoDatabase
import com.example.data.local.OctoPreferences
import com.example.data.local.SubscriptionEntity
import com.example.data.local.WatchHistoryEntity
import com.example.data.repository.OctoRepository
import com.example.domain.model.ChannelDetail
import com.example.domain.model.DownloadFormat
import com.example.domain.model.DownloadTask
import com.example.domain.model.FeedCategory
import com.example.domain.model.OctoSettings
import com.example.domain.model.ServiceInstanceHealth
import com.example.domain.model.ServiceProvider
import com.example.domain.model.StreamDetail
import com.example.domain.model.VideoStreamOption
import com.example.domain.model.VideoSummary
import com.example.download.OctoDownloadManager
import com.example.player.ActivePlaybackUiState
import com.example.player.PlayerCoordinator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiLoadState<out T> {
    data object Loading : UiLoadState<Nothing>
    data class Success<T>(val data: T) : UiLoadState<T>
    data class Error(val message: String) : UiLoadState<Nothing>
}

data class DiscoverUiState(
    val searchQuery: String = "",
    val selectedCategory: FeedCategory = FeedCategory.TRENDING,
    val videosState: UiLoadState<List<VideoSummary>> = UiLoadState.Loading,
    val activeInstanceLabel: String = "",
    val bannerMessage: String? = null
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = OctoDatabase.getInstance(application)
    private val dao = database.octoDao()
    private val preferences = OctoPreferences(application)
    private val repository = OctoRepository(application, dao, preferences)
    val downloadManager = OctoDownloadManager(application, dao)

    val playerCoordinator = PlayerCoordinator(
        appContext = application,
        onSponsorSkipped = { savedSeconds ->
            preferences.addSponsorSecondsSaved(savedSeconds)
            preferences.incrementAdsBlocked(1)
        },
        onSaveProgress = { detail, posMs ->
            repository.recordWatchProgress(
                videoId = detail.id,
                title = detail.title,
                channelId = detail.channelId,
                channelName = detail.channelName,
                thumbnailUrl = detail.thumbnailUrl,
                durationSeconds = detail.durationSeconds,
                positionMs = posMs,
                serviceId = detail.serviceProvider.id
            )
        }
    )

    val settings: StateFlow<OctoSettings> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OctoSettings())

    val subscriptions: StateFlow<List<SubscriptionEntity>> = repository.subscriptionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val watchHistory: StateFlow<List<WatchHistoryEntity>> = repository.watchHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadTask>> = repository.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val playbackState: StateFlow<ActivePlaybackUiState> = playerCoordinator.playerState

    private val _discoverState = MutableStateFlow(DiscoverUiState())
    val discoverState: StateFlow<DiscoverUiState> = _discoverState.asStateFlow()

    private val _subscriptionFeed = MutableStateFlow<UiLoadState<List<VideoSummary>>>(UiLoadState.Success(emptyList()))
    val subscriptionFeed: StateFlow<UiLoadState<List<VideoSummary>>> = _subscriptionFeed.asStateFlow()

    private val _playerExtractState = MutableStateFlow<UiLoadState<StreamDetail>>(UiLoadState.Loading)
    val playerExtractState: StateFlow<UiLoadState<StreamDetail>> = _playerExtractState.asStateFlow()

    private val _channelState = MutableStateFlow<UiLoadState<ChannelDetail>>(UiLoadState.Loading)
    val channelState: StateFlow<UiLoadState<ChannelDetail>> = _channelState.asStateFlow()

    private val _instanceHealthList = MutableStateFlow<List<ServiceInstanceHealth>>(emptyList())
    val instanceHealthList: StateFlow<List<ServiceInstanceHealth>> = _instanceHealthList.asStateFlow()

    private val _isPingingInstances = MutableStateFlow(false)
    val isPingingInstances: StateFlow<Boolean> = _isPingingInstances.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    init {
        playerCoordinator.connect()
        viewModelScope.launch {
            settings.collect { current ->
                playerCoordinator.updateSettings(current)
            }
        }
        loadDiscoverFeed()
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun showToast(message: String) {
        _toastMessage.value = message
    }

    fun onSearchQueryChanged(query: String) {
        _discoverState.update { it.copy(searchQuery = query) }
    }

    fun selectCategory(category: FeedCategory) {
        _discoverState.update { it.copy(selectedCategory = category, searchQuery = category.searchQuery) }
        if (category == FeedCategory.TRENDING) {
            loadDiscoverFeed()
        } else {
            searchVideos(category.searchQuery)
        }
    }

    fun loadDiscoverFeed() {
        viewModelScope.launch {
            _discoverState.update { it.copy(videosState = UiLoadState.Loading, bannerMessage = null) }
            try {
                val currentSettings = repository.settingsFlow.first()
                val result = repository.getTrending(currentSettings)
                _discoverState.update {
                    it.copy(
                        videosState = UiLoadState.Success(result.videos),
                        activeInstanceLabel = result.activeInstance,
                        bannerMessage = result.warningMessage
                    )
                }
            } catch (e: Exception) {
                _discoverState.update {
                    it.copy(videosState = UiLoadState.Error(e.message ?: "تعذر تحميل الفيديوهات"))
                }
            }
        }
    }

    fun submitSearch(onDirectVideoUrl: (String, ServiceProvider) -> Unit) {
        val query = _discoverState.value.searchQuery.trim()
        val directId = repository.extractVideoIdIfUrl(query)
        if (directId != null) {
            onDirectVideoUrl(directId, settings.value.activeProvider)
            return
        }
        if (query.isBlank()) {
            loadDiscoverFeed()
        } else {
            searchVideos(query)
        }
    }

    private fun searchVideos(query: String) {
        viewModelScope.launch {
            _discoverState.update { it.copy(videosState = UiLoadState.Loading, bannerMessage = null) }
            try {
                val currentSettings = repository.settingsFlow.first()
                val result = repository.search(query, currentSettings)
                _discoverState.update {
                    it.copy(
                        videosState = UiLoadState.Success(result.videos),
                        activeInstanceLabel = result.activeInstance,
                        bannerMessage = result.warningMessage
                    )
                }
            } catch (e: Exception) {
                _discoverState.update {
                    it.copy(videosState = UiLoadState.Error(e.message ?: "تعذر إتمام البحث"))
                }
            }
        }
    }

    fun loadVideoForPlayback(
        videoId: String,
        provider: ServiceProvider,
        startPosMs: Long = 0L,
        forceReload: Boolean = false
    ) {
        val currentPlaying = playbackState.value.currentStream
        if (!forceReload && currentPlaying?.id == videoId) {
            _playerExtractState.value = UiLoadState.Success(currentPlaying)
            return
        }
        viewModelScope.launch {
            _playerExtractState.value = UiLoadState.Loading
            try {
                val currentSettings = repository.settingsFlow.first()
                val detail = repository.getStreamDetail(videoId, provider, currentSettings)
                _playerExtractState.value = UiLoadState.Success(detail)
                playerCoordinator.playStream(detail, currentSettings, startPosMs)
            } catch (e: Exception) {
                _playerExtractState.value = UiLoadState.Error(
                    e.message ?: "تعذر استخراج بيانات البث"
                )
            }
        }
    }

    fun playOfflineDownload(task: DownloadTask) {
        playerCoordinator.playOfflineTask(task)
        playbackState.value.currentStream?.let {
            _playerExtractState.value = UiLoadState.Success(it)
        }
    }

    fun loadChannel(channelId: String, provider: ServiceProvider) {
        viewModelScope.launch {
            _channelState.value = UiLoadState.Loading
            try {
                val currentSettings = repository.settingsFlow.first()
                val detail = repository.getChannelDetail(channelId, provider, currentSettings)
                _channelState.value = UiLoadState.Success(detail)
            } catch (e: Exception) {
                _channelState.value = UiLoadState.Error(e.message ?: "تعذر تحميل القناة")
            }
        }
    }

    fun toggleSubscription(
        channelId: String,
        channelName: String,
        avatarUrl: String?,
        provider: ServiceProvider
    ) {
        viewModelScope.launch {
            val subscribed = repository.toggleSubscription(channelId, channelName, avatarUrl, provider)
            _toastMessage.value = if (subscribed) {
                "تمت إضافة $channelName إلى اشتراكاتك"
            } else {
                "تم إلغاء الاشتراك من $channelName"
            }
            refreshSubscriptionsFeed()
        }
    }

    fun refreshSubscriptionsFeed() {
        viewModelScope.launch {
            val subs = repository.subscriptionsFlow.first()
            if (subs.isEmpty()) {
                _subscriptionFeed.value = UiLoadState.Success(emptyList())
                return@launch
            }
            _subscriptionFeed.value = UiLoadState.Loading
            try {
                val currentSettings = repository.settingsFlow.first()
                val feed = repository.fetchSubscriptionsFeed(subs, currentSettings)
                _subscriptionFeed.value = UiLoadState.Success(feed)
            } catch (e: Exception) {
                _subscriptionFeed.value = UiLoadState.Error(e.message ?: "تعذر تحديث تغذية الاشتراكات")
            }
        }
    }

    fun startDownload(detail: StreamDetail, format: DownloadFormat, chosenVideo: VideoStreamOption?) {
        val effectiveFormat = if (
            format == DownloadFormat.VIDEO_MP4 &&
            chosenVideo?.mimeType?.contains("mpegurl", ignoreCase = true) == true
        ) DownloadFormat.VIDEO_HLS_TS else format
        val url = when (effectiveFormat) {
            DownloadFormat.VIDEO_MP4, DownloadFormat.VIDEO_HLS_TS -> {
                chosenVideo?.takeIf {
                    !it.videoOnly && (
                        it.mimeType.contains("video/mp4", ignoreCase = true) ||
                            it.mimeType.contains("mpegurl", ignoreCase = true)
                    )
                }?.url ?: detail.videoStreams.firstOrNull {
                    !it.videoOnly && it.mimeType.contains("video/mp4", ignoreCase = true)
                }?.url
            }
            DownloadFormat.AUDIO_M4A -> {
                detail.audioStreams.firstOrNull {
                    it.mimeType.contains("audio/mp4", ignoreCase = true) ||
                        it.mimeType.contains("audio/aac", ignoreCase = true)
                }?.url
            }
        }
        if (url.isNullOrBlank()) {
            _toastMessage.value = "لا يتوفر رابط تحميل مباشر لهذا المقطع"
            return
        }
        val qualityLabel = if (format == DownloadFormat.AUDIO_M4A) {
            detail.audioStreams.firstOrNull()?.qualityLabel ?: "128 kbps"
        } else {
            chosenVideo?.qualityLabel ?: detail.videoStreams.firstOrNull {
                !it.videoOnly && it.mimeType.contains("video/mp4", ignoreCase = true)
            }?.qualityLabel ?: "MP4"
        }
        downloadManager.startDownload(detail, effectiveFormat, url, qualityLabel)
        _toastMessage.value = "بدأ التحميل: ${detail.title}"
    }

    fun exportSubscriptions(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val count = repository.exportSubscriptionsToUri(uri)
                _toastMessage.value = "تم تصدير $count قناة بنجاح"
            }.onFailure {
                _toastMessage.value = "فشل تصدير الاشتراكات"
            }
        }
    }

    fun importSubscriptions(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val count = repository.importSubscriptionsFromUri(uri)
                _toastMessage.value = "تم استيراد $count قناة بنجاح"
                refreshSubscriptionsFeed()
            }.onFailure {
                _toastMessage.value = "تعذر قراءة ملف الاشتراكات"
            }
        }
    }

    // Settings & Services
    fun switchServiceProvider(provider: ServiceProvider) {
        viewModelScope.launch {
            preferences.setActiveProvider(provider)
            _toastMessage.value = "تم التحويل إلى محرك ${provider.displayName}"
            loadDiscoverFeed()
        }
    }

    fun updateCustomInstanceUrl(provider: ServiceProvider, url: String) {
        viewModelScope.launch {
            preferences.setCustomUrl(provider, url)
            _toastMessage.value = "تم تحديث رابط الخادم"
        }
    }

    fun pingAllInstances() {
        viewModelScope.launch {
            _isPingingInstances.value = true
            val current = repository.settingsFlow.first()
            _instanceHealthList.value = repository.checkAllInstancesHealth(current)
            _isPingingInstances.value = false
        }
    }

    fun setSponsorBlockEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setSponsorBlockEnabled(enabled)
    }

    fun setAutoSkipSponsor(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoSkipSponsor(enabled)
    }

    fun toggleSponsorCategory(key: String) = viewModelScope.launch {
        preferences.toggleSponsorCategory(key)
    }

    fun setBackgroundPlayOnLeave(enabled: Boolean) = viewModelScope.launch {
        preferences.setBackgroundPlayOnLeave(enabled)
    }

    fun setAutoPipEnabled(enabled: Boolean) = viewModelScope.launch {
        preferences.setAutoPipEnabled(enabled)
    }

    fun setSubtitlesAutoEnable(enabled: Boolean) = viewModelScope.launch {
        preferences.setSubtitlesAutoEnable(enabled)
    }

    fun setPreferredSubtitleLang(lang: String) = viewModelScope.launch {
        preferences.setPreferredSubtitleLang(lang)
    }

    fun setPreferredQuality(quality: String) = viewModelScope.launch {
        preferences.setPreferredQuality(quality)
    }

    fun setDarkTheme(dark: Boolean) = viewModelScope.launch {
        preferences.setDarkTheme(dark)
    }

    fun clearHistory() = viewModelScope.launch {
        repository.clearWatchHistory()
        _toastMessage.value = "تم مسح سجل المشاهدة المحلي"
    }

    override fun onCleared() {
        playerCoordinator.release()
        super.onCleared()
    }
}
