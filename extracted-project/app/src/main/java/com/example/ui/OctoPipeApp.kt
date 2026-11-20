package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.domain.model.ServiceProvider
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.ChannelScreen
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.DownloadsScreen
import com.example.ui.screens.PlayerScreen
import com.example.ui.screens.ServicesScreen
import com.example.ui.screens.SubscriptionsScreen
import com.example.ui.theme.OctoPipeTheme
import kotlinx.serialization.Serializable

@Serializable data object DiscoverRoute
@Serializable data object SubscriptionsRoute
@Serializable data object DownloadsRoute
@Serializable data object ServicesRoute
@Serializable data class PlayerRoute(val videoId: String, val serviceId: String, val startPosMs: Long = 0L)
@Serializable data class ChannelRoute(val channelId: String, val serviceId: String)

private data class TopTabItem(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String
)

@Composable
fun OctoPipeApp(
    isInPipMode: Boolean,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val discoverState by viewModel.discoverState.collectAsStateWithLifecycle()
    val subscriptions by viewModel.subscriptions.collectAsStateWithLifecycle()
    val subscriptionFeed by viewModel.subscriptionFeed.collectAsStateWithLifecycle()
    val watchHistory by viewModel.watchHistory.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val extractState by viewModel.playerExtractState.collectAsStateWithLifecycle()
    val channelState by viewModel.channelState.collectAsStateWithLifecycle()
    val healthList by viewModel.instanceHealthList.collectAsStateWithLifecycle()
    val isPinging by viewModel.isPingingInstances.collectAsStateWithLifecycle()
    val mediaController by viewModel.playerCoordinator.controllerFlow.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    val notifLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    fun ensureNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(toastMessage) {
        toastMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearToast()
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = backStackEntry?.destination
    val isPlayerScreen = currentDest?.hasRoute<PlayerRoute>() == true

    val tabs = remember {
        listOf(
            TopTabItem("استكشاف", DiscoverRoute, Icons.Filled.Explore, Icons.Outlined.Explore, "nav_discover"),
            TopTabItem("الاشتراكات", SubscriptionsRoute, Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions, "nav_subscriptions"),
            TopTabItem("التحميلات", DownloadsRoute, Icons.Filled.Download, Icons.Outlined.Download, "nav_downloads"),
            TopTabItem("الخدمات", ServicesRoute, Icons.Filled.CloudQueue, Icons.Outlined.CloudQueue, "nav_services")
        )
    }

    OctoPipeTheme(darkTheme = settings.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp && !isInPipMode

            Scaffold(
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                bottomBar = {
                    if (!isInPipMode && !isPlayerScreen && !useRail) {
                        Column {
                            if (playbackState.currentStream != null) {
                                MiniPlayerBar(
                                    playbackState = playbackState,
                                    onExpand = {
                                        val active = playbackState.currentStream ?: return@MiniPlayerBar
                                        navController.navigate(
                                            PlayerRoute(
                                                videoId = active.id,
                                                serviceId = active.serviceProvider.id
                                            )
                                        )
                                    },
                                    onTogglePlayPause = { viewModel.playerCoordinator.togglePlayPause() },
                                    onClose = { viewModel.playerCoordinator.stopAndClear() }
                                )
                            }
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface
                            ) {
                                tabs.forEach { tab ->
                                    val selected = when (tab.route) {
                                        is DiscoverRoute -> currentDest?.hasRoute<DiscoverRoute>() == true
                                        is SubscriptionsRoute -> currentDest?.hasRoute<SubscriptionsRoute>() == true
                                        is DownloadsRoute -> currentDest?.hasRoute<DownloadsRoute>() == true
                                        is ServicesRoute -> currentDest?.hasRoute<ServicesRoute>() == true
                                        else -> false
                                    }
                                    NavigationBarItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                                contentDescription = tab.label
                                            )
                                        },
                                        label = { Text(tab.label) },
                                        modifier = Modifier.testTag(tab.tag)
                                    )
                                }
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isInPipMode) androidx.compose.foundation.layout.PaddingValues(0.dp) else innerPadding)
                ) {
                    if (useRail && !isPlayerScreen) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            tabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is DiscoverRoute -> currentDest?.hasRoute<DiscoverRoute>() == true
                                    is SubscriptionsRoute -> currentDest?.hasRoute<SubscriptionsRoute>() == true
                                    is DownloadsRoute -> currentDest?.hasRoute<DownloadsRoute>() == true
                                    is ServicesRoute -> currentDest?.hasRoute<ServicesRoute>() == true
                                    else -> false
                                }
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.tag)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = DiscoverRoute,
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = if (isPlayerScreen || isInPipMode) 1200.dp else 840.dp)
                        ) {
                            composable<DiscoverRoute> {
                                DiscoverScreen(
                                    discoverState = discoverState,
                                    settings = settings,
                                    watchHistory = watchHistory,
                                    onSearchQueryChange = viewModel::onSearchQueryChanged,
                                    onSearchSubmit = {
                                        viewModel.submitSearch { videoId, provider ->
                                            navController.navigate(PlayerRoute(videoId, provider.id))
                                        }
                                    },
                                    onSelectCategory = viewModel::selectCategory,
                                    onRefresh = viewModel::loadDiscoverFeed,
                                    onOpenServices = { navController.navigate(ServicesRoute) },
                                    onVideoClick = { video ->
                                        ensureNotificationPermissionIfNeeded()
                                        navController.navigate(PlayerRoute(video.id, video.serviceProvider.id))
                                    },
                                    onHistoryItemClick = { entry ->
                                        navController.navigate(
                                            PlayerRoute(
                                                videoId = entry.videoId,
                                                serviceId = entry.serviceId,
                                                startPosMs = entry.lastPositionMs
                                            )
                                        )
                                    },
                                    onChannelClick = { channelId, provider ->
                                        navController.navigate(ChannelRoute(channelId, provider.id))
                                    }
                                )
                            }

                            composable<SubscriptionsRoute> {
                                SubscriptionsScreen(
                                    subscriptions = subscriptions,
                                    feedState = subscriptionFeed,
                                    onRefreshFeed = viewModel::refreshSubscriptionsFeed,
                                    onExportUri = viewModel::exportSubscriptions,
                                    onImportUri = viewModel::importSubscriptions,
                                    onChannelClick = { channelId, provider ->
                                        navController.navigate(ChannelRoute(channelId, provider.id))
                                    },
                                    onVideoClick = { video ->
                                        navController.navigate(PlayerRoute(video.id, video.serviceProvider.id))
                                    }
                                )
                            }

                            composable<DownloadsRoute> {
                                DownloadsScreen(
                                    downloads = downloads,
                                    onPlayOffline = { task ->
                                        viewModel.playOfflineDownload(task)
                                        navController.navigate(PlayerRoute(task.videoId, task.serviceId))
                                    },
                                    onCancelDownload = viewModel.downloadManager::cancelDownload,
                                    onDeleteDownload = viewModel.downloadManager::deleteDownload
                                )
                            }

                            composable<ServicesRoute> {
                                ServicesScreen(
                                    settings = settings,
                                    healthList = healthList,
                                    isPinging = isPinging,
                                    onSelectProvider = viewModel::switchServiceProvider,
                                    onUpdateCustomUrl = viewModel::updateCustomInstanceUrl,
                                    onPingAll = viewModel::pingAllInstances,
                                    onToggleSponsorBlock = viewModel::setSponsorBlockEnabled,
                                    onToggleAutoSkipSponsor = viewModel::setAutoSkipSponsor,
                                    onToggleSponsorCategory = viewModel::toggleSponsorCategory,
                                    onToggleBackgroundOnLeave = viewModel::setBackgroundPlayOnLeave,
                                    onToggleAutoPip = viewModel::setAutoPipEnabled,
                                    onToggleSubtitlesAuto = viewModel::setSubtitlesAutoEnable,
                                    onSelectPreferredQuality = viewModel::setPreferredQuality,
                                    onSelectPreferredSubLang = viewModel::setPreferredSubtitleLang,
                                    onToggleDarkTheme = viewModel::setDarkTheme,
                                    onClearHistory = viewModel::clearHistory
                                )
                            }

                            composable<PlayerRoute> { backStack ->
                                val route = backStack.toRoute<PlayerRoute>()
                                val provider = ServiceProvider.fromId(route.serviceId)

                                LaunchedEffect(route.videoId, route.serviceId) {
                                    ensureNotificationPermissionIfNeeded()
                                    viewModel.loadVideoForPlayback(route.videoId, provider, route.startPosMs)
                                }

                                val currentChannelId = (extractState as? UiLoadState.Success)?.data?.channelId.orEmpty()
                                val isSubscribed = subscriptions.any { it.channelId == currentChannelId }

                                PlayerScreen(
                                    extractState = extractState,
                                    playbackState = playbackState,
                                    mediaController = mediaController,
                                    settings = settings,
                                    isSubscribed = isSubscribed,
                                    isInPipMode = isInPipMode,
                                    onBack = { navController.popBackStack() },
                                    onMinimizeToBackground = {
                                        viewModel.showToast("يستمر الصوت في الخلفية عبر شريط الإشعارات")
                                        navController.popBackStack()
                                    },
                                    onTogglePlayPause = viewModel.playerCoordinator::togglePlayPause,
                                    onSeekTo = viewModel.playerCoordinator::seekTo,
                                    onSeekBy = viewModel.playerCoordinator::seekBy,
                                    onSelectQuality = viewModel.playerCoordinator::switchQuality,
                                    onSelectSubtitle = viewModel.playerCoordinator::selectSubtitle,
                                    onSetSpeed = viewModel.playerCoordinator::setSpeed,
                                    onToggleAudioOnlyMode = viewModel.playerCoordinator::toggleBackgroundAudioMode,
                                    onSkipSponsorNow = viewModel.playerCoordinator::skipActiveSponsorSegment,
                                    onStartDownload = { detail, format, opt ->
                                        ensureNotificationPermissionIfNeeded()
                                        viewModel.startDownload(detail, format, opt)
                                    },
                                    onToggleSubscribe = { detail ->
                                        viewModel.toggleSubscription(
                                            channelId = detail.channelId,
                                            channelName = detail.channelName,
                                            avatarUrl = detail.channelAvatarUrl,
                                            provider = detail.serviceProvider
                                        )
                                    },
                                    onOpenChannel = { channelId, srv ->
                                        navController.navigate(ChannelRoute(channelId, srv.id))
                                    },
                                    onPlayRelated = { related ->
                                        navController.navigate(
                                            PlayerRoute(related.id, related.serviceProvider.id)
                                        )
                                    }
                                )
                            }

                            composable<ChannelRoute> { backStack ->
                                val route = backStack.toRoute<ChannelRoute>()
                                val provider = ServiceProvider.fromId(route.serviceId)
                                LaunchedEffect(route.channelId, route.serviceId) {
                                    viewModel.loadChannel(route.channelId, provider)
                                }
                                val isSubscribed = subscriptions.any { it.channelId == route.channelId }
                                ChannelScreen(
                                    channelState = channelState,
                                    isSubscribed = isSubscribed,
                                    onBack = { navController.popBackStack() },
                                    onToggleSubscribe = { ch ->
                                        viewModel.toggleSubscription(
                                            channelId = ch.id,
                                            channelName = ch.name,
                                            avatarUrl = ch.avatarUrl,
                                            provider = ch.serviceProvider
                                        )
                                    },
                                    onVideoClick = { video ->
                                        navController.navigate(PlayerRoute(video.id, video.serviceProvider.id))
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
