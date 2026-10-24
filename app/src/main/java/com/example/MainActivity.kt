package com.example

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.model.Track
import com.example.navigation.PulseDestination
import com.example.permissions.PermissionHelper
import com.example.ui.PulseViewModel
import com.example.ui.components.AddToPlaylistDialog
import com.example.ui.components.MiniPlayerBar
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.PulseTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as PulseApplication).container

        setContent {
            val viewModel: PulseViewModel = viewModel(
                factory = PulseViewModel.provideFactory(appContainer)
            )
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            PulseTheme(themeOption = uiState.preferences.themeOption) {
                PulseAppRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun PulseAppRoot(viewModel: PulseViewModel) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val currentTrackLyrics by viewModel.currentTrackLyrics.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(PulseDestination.HOME) }
    var isNowPlayingExpanded by rememberSaveable { mutableStateOf(false) }
    var trackForPlaylistDialog by remember { mutableStateOf<Track?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onPermissionStatusChanged(granted = isGranted, deniedByUser = !isGranted)
    }

    // Observe lifecycle resume to check if user granted audio permission in Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = PermissionHelper.hasAudioPermission(context)
                viewModel.onPermissionStatusChanged(granted = granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.snackbarMessage) {
        val msg = uiState.snackbarMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.consumeSnackbarMessage()
        }
    }

    // Back handler when on non-Home tab
    if (!isNowPlayingExpanded && currentDestination != PulseDestination.HOME) {
        BackHandler {
            currentDestination = PulseDestination.HOME
        }
    }

    val openSystemAppSettings = {
        runCatching {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        Unit
    }

    val requestAudioPermission = {
        permissionLauncher.launch(PermissionHelper.requiredAudioPermission())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState)
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val activeTrack = playbackState.currentTrack
                    if (activeTrack != null) {
                        MiniPlayerBar(
                            currentTrack = activeTrack,
                            isPlaying = playbackState.isPlaying,
                            durationMs = playbackState.durationMs,
                            currentPositionMsFlow = viewModel.currentPositionMs,
                            onExpandPlayer = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isNowPlayingExpanded = true
                            },
                            onTogglePlayPause = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.togglePlayPause()
                            },
                            onSkipNext = viewModel::skipToNext,
                            onToggleFavourite = { viewModel.toggleFavourite(activeTrack) }
                        )
                    }

                    HorizontalDivider(
                        thickness = 0.75.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.background,
                        tonalElevation = 0.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        PulseDestination.entries.forEach { destination ->
                            val selected = currentDestination == destination
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    currentDestination = destination
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.label
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.label,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag(destination.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentDestination) {
                    PulseDestination.HOME -> {
                        HomeScreen(
                            uiState = uiState,
                            playbackState = playbackState,
                            onRequestPermission = requestAudioPermission,
                            onOpenAppSettings = openSystemAppSettings,
                            onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
                            onEnableDemoSynth = { viewModel.setDemoModeEnabled(true) },
                            onPlayTrack = { track, contextList ->
                                viewModel.playTrack(track, contextList)
                            },
                            onPlayAll = { viewModel.playAll() },
                            onShuffleAll = { viewModel.shuffleAll() },
                            onOpenNowPlaying = { isNowPlayingExpanded = true },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onPlayNext = viewModel::playNext,
                            onAddToQueue = viewModel::addToQueue,
                            onAddToPlaylistRequest = { trackForPlaylistDialog = it },
                            onNavigateToPlaylists = { currentDestination = PulseDestination.PLAYLISTS }
                        )
                    }

                    PulseDestination.LIBRARY -> {
                        LibraryScreen(
                            uiState = uiState,
                            playbackState = playbackState,
                            onRequestPermission = requestAudioPermission,
                            onOpenAppSettings = openSystemAppSettings,
                            onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
                            onEnableDemoSynth = { viewModel.setDemoModeEnabled(true) },
                            onSortOptionSelected = viewModel::setSortOption,
                            onPlayTrack = { track, contextList ->
                                viewModel.playTrack(track, contextList)
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onPlayNext = viewModel::playNext,
                            onAddToQueue = viewModel::addToQueue,
                            onAddToPlaylistRequest = { trackForPlaylistDialog = it }
                        )
                    }

                    PulseDestination.SEARCH -> {
                        SearchScreen(
                            query = searchQuery,
                            searchResults = searchResults,
                            uiState = uiState,
                            playbackState = playbackState,
                            onQueryChange = viewModel::updateSearchQuery,
                            onCommitSearch = viewModel::commitSearchQueryToHistory,
                            onDeleteRecentSearch = viewModel::deleteRecentSearch,
                            onClearRecentSearches = viewModel::clearRecentSearches,
                            onPlayTrack = { track, contextList ->
                                viewModel.playTrack(track, contextList)
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onPlayNext = viewModel::playNext,
                            onAddToQueue = viewModel::addToQueue,
                            onAddToPlaylistRequest = { trackForPlaylistDialog = it }
                        )
                    }

                    PulseDestination.PLAYLISTS -> {
                        PlaylistsScreen(
                            uiState = uiState,
                            playbackState = playbackState,
                            onCreatePlaylist = { name -> viewModel.createPlaylist(name) },
                            onRenamePlaylist = viewModel::renamePlaylist,
                            onDeletePlaylist = viewModel::deletePlaylist,
                            onAddTrackToPlaylist = viewModel::addTrackToPlaylist,
                            onRemoveTrackFromPlaylist = viewModel::removeTrackFromPlaylist,
                            onMoveTrackInPlaylist = viewModel::moveTrackInPlaylist,
                            onPlayTrack = { track, contextList ->
                                viewModel.playTrack(track, contextList)
                            },
                            onToggleFavourite = viewModel::toggleFavourite,
                            onPlayNext = viewModel::playNext,
                            onAddToQueue = viewModel::addToQueue,
                            onAddToPlaylistRequest = { trackForPlaylistDialog = it }
                        )
                    }

                    PulseDestination.SETTINGS -> {
                        SettingsScreen(
                            uiState = uiState,
                            onSelectTheme = viewModel::setThemeOption,
                            onRescanLibrary = { viewModel.rescanLibrary(showFeedback = true) },
                            onRequestPermission = requestAudioPermission,
                            onOpenAppSettings = openSystemAppSettings,
                            onSetDefaultRepeat = viewModel::setDefaultRepeatMode,
                            onSetDefaultShuffle = viewModel::setDefaultShuffle,
                            onSetMinPlayDurationSec = viewModel::setMinPlayDurationSec,
                            onSetDemoModeEnabled = viewModel::setDemoModeEnabled,
                            onClearListeningHistory = viewModel::clearListeningHistory,
                            onClearRecentSearches = viewModel::clearRecentSearches
                        )
                    }
                }
            }
        }

        // Full-Screen Now Playing Overlay
        AnimatedVisibility(
            visible = isNowPlayingExpanded && playbackState.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            NowPlayingScreen(
                playbackState = playbackState,
                currentPositionMsFlow = viewModel.currentPositionMs,
                parsedLyrics = currentTrackLyrics,
                onClose = { isNowPlayingExpanded = false },
                onTogglePlayPause = viewModel::togglePlayPause,
                onSkipPrevious = viewModel::skipToPrevious,
                onSkipNext = viewModel::skipToNext,
                onSeekTo = viewModel::seekTo,
                onSeekForward10s = viewModel::seekForward10s,
                onSeekBackward10s = viewModel::seekBackward10s,
                onToggleShuffle = viewModel::toggleShuffle,
                onCycleRepeat = viewModel::cycleRepeatMode,
                onToggleFavourite = viewModel::toggleFavourite,
                onPlayQueueIndex = viewModel::playQueueIndex,
                onRemoveQueueIndex = viewModel::removeQueueItemAt,
                onMoveQueueItem = viewModel::moveQueueItem,
                onClearQueue = {
                    viewModel.clearQueue()
                    isNowPlayingExpanded = false
                },
                onImportLyricsUri = viewModel::importLyricsFromUri,
                onSaveManualLyrics = viewModel::saveCustomLyricsText,
                onClearLyrics = viewModel::clearLyricsForTrack
            )
        }
    }

    trackForPlaylistDialog?.let { targetTrack ->
        AddToPlaylistDialog(
            track = targetTrack,
            playlists = uiState.playlists,
            onDismiss = { trackForPlaylistDialog = null },
            onSelectPlaylist = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, targetTrack)
            },
            onCreateNewPlaylistWithTrack = { name ->
                viewModel.createPlaylist(name, initialTrackId = targetTrack.id)
            }
        )
    }
}
