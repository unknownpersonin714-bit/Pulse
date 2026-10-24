package com.example.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.AppContainer
import com.example.data.repository.MusicRepository
import com.example.domain.model.Album
import com.example.domain.model.Artist
import com.example.domain.model.ListeningStats
import com.example.domain.model.ParsedLyrics
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.RepeatModeOption
import com.example.domain.model.ScanProgress
import com.example.domain.model.SortOption
import com.example.domain.model.ThemeOption
import com.example.domain.model.Track
import com.example.domain.usecase.SmartRecommendations
import com.example.domain.usecase.SmartRecommendationsUseCase
import com.example.player.PlaybackController
import com.example.player.PlaybackState
import com.example.settings.UserPreferencesRepository
import com.example.settings.UserPreferencesState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchResultsState(
    val query: String = "",
    val matchingTracks: List<Track> = emptyList(),
    val matchingAlbums: List<Album> = emptyList(),
    val matchingArtists: List<Artist> = emptyList(),
    val matchingPlaylists: List<PlaylistSummary> = emptyList()
) {
    val isEmpty: Boolean
        get() = matchingTracks.isEmpty() &&
            matchingAlbums.isEmpty() &&
            matchingArtists.isEmpty() &&
            matchingPlaylists.isEmpty()
}

data class PulseUiState(
    val isInitialLoading: Boolean = true,
    val hasAudioPermission: Boolean = false,
    val permissionPermanentlyDenied: Boolean = false,
    val allTracks: List<Track> = emptyList(),
    val sortedTracks: List<Track> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val playlists: List<PlaylistSummary> = emptyList(),
    val favourites: List<Track> = emptyList(),
    val recommendations: SmartRecommendations = SmartRecommendations(),
    val listeningStats: ListeningStats = ListeningStats(),
    val recentSearches: List<String> = emptyList(),
    val preferences: UserPreferencesState = UserPreferencesState(),
    val scanProgress: ScanProgress = ScanProgress(),
    val snackbarMessage: String? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class PulseViewModel(
    private val musicRepository: MusicRepository,
    private val preferencesRepository: UserPreferencesRepository,
    val playbackController: PlaybackController
) : ViewModel() {

    private val _hasPermission = MutableStateFlow(false)
    private val _permissionDeniedOnce = MutableStateFlow(false)
    private val _snackbarMessage = MutableStateFlow<String?>(null)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private var scanJob: Job? = null

    val playbackState: StateFlow<PlaybackState> = playbackController.playbackState
    val currentPositionMs: StateFlow<Long> = playbackController.currentPositionMs

    val currentTrackLyrics: StateFlow<ParsedLyrics?> = playbackState
        .map { it.currentTrack?.id }
        .distinctUntilChanged()
        .flatMapLatest { trackId ->
            if (trackId == null) {
                flowOf(null)
            } else {
                musicRepository.observeLyricsForTrack(trackId)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private data class CoreCatalogSnapshot(
        val tracks: List<Track>,
        val playlists: List<PlaylistSummary>,
        val favourites: List<Track>,
        val stats: ListeningStats,
        val recentSearches: List<String>
    )

    private val catalogFlow = combine(
        musicRepository.tracksFlow,
        musicRepository.playlistsFlow,
        musicRepository.favouritesFlow,
        musicRepository.listeningStatsFlow,
        musicRepository.recentSearchesFlow
    ) { tracks, playlists, favourites, stats, recentSearches ->
        CoreCatalogSnapshot(
            tracks = tracks,
            playlists = playlists,
            favourites = favourites,
            stats = stats,
            recentSearches = recentSearches
        )
    }

    val uiState: StateFlow<PulseUiState> = combine(
        catalogFlow,
        preferencesRepository.preferencesFlow,
        musicRepository.scanProgress,
        _hasPermission,
        combine(_permissionDeniedOnce, _snackbarMessage) { denied, msg -> denied to msg }
    ) { catalog, prefs, scanProg, hasPerm, (deniedOnce, snackbar) ->
        playbackController.syncCatalogTracks(catalog.tracks)

        val sorted = sortTracks(catalog.tracks, prefs.sortOption)
        val albums = musicRepository.groupAlbums(catalog.tracks)
        val artists = musicRepository.groupArtists(catalog.tracks)
        val recs = SmartRecommendationsUseCase.compute(catalog.tracks)

        PulseUiState(
            isInitialLoading = false,
            hasAudioPermission = hasPerm,
            permissionPermanentlyDenied = deniedOnce && !hasPerm,
            allTracks = catalog.tracks,
            sortedTracks = sorted,
            albums = albums,
            artists = artists,
            playlists = catalog.playlists,
            favourites = catalog.favourites,
            recommendations = recs,
            listeningStats = catalog.stats,
            recentSearches = catalog.recentSearches,
            preferences = prefs,
            scanProgress = scanProg,
            snackbarMessage = snackbar
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PulseUiState()
    )

    val searchResults: StateFlow<SearchResultsState> = combine(
        _searchQuery.debounce(180L),
        uiState
    ) { query, state ->
        val clean = query.trim()
        if (clean.isEmpty()) {
            SearchResultsState(query = "")
        } else {
            val matchingTracks = state.allTracks.filter {
                it.title.contains(clean, ignoreCase = true) ||
                    it.artist.contains(clean, ignoreCase = true) ||
                    it.album.contains(clean, ignoreCase = true)
            }
            val matchingAlbums = state.albums.filter {
                it.title.contains(clean, ignoreCase = true) ||
                    it.artist.contains(clean, ignoreCase = true)
            }
            val matchingArtists = state.artists.filter {
                it.name.contains(clean, ignoreCase = true)
            }
            val matchingPlaylists = state.playlists.filter {
                it.name.contains(clean, ignoreCase = true)
            }
            SearchResultsState(
                query = clean,
                matchingTracks = matchingTracks,
                matchingAlbums = matchingAlbums,
                matchingArtists = matchingArtists,
                matchingPlaylists = matchingPlaylists
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchResultsState()
    )

    fun onPermissionStatusChanged(granted: Boolean, deniedByUser: Boolean = false) {
        val previouslyGranted = _hasPermission.value
        _hasPermission.value = granted
        if (deniedByUser && !granted) {
            _permissionDeniedOnce.value = true
        }
        if (granted && !previouslyGranted) {
            rescanLibrary(showFeedback = false)
        }
    }

    fun rescanLibrary(showFeedback: Boolean = true) {
        if (scanJob?.isActive == true) return
        scanJob = viewModelScope.launch {
            val result = musicRepository.rescanMediaStore()
            if (showFeedback) {
                result.fold(
                    onSuccess = { count ->
                        showMessage("Indexed $count local audio track(s)")
                    },
                    onFailure = { err ->
                        showMessage("Scan error: ${err.localizedMessage ?: "Unknown"}")
                    }
                )
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun commitSearchQueryToHistory(query: String = _searchQuery.value) {
        val trimmed = query.trim()
        if (trimmed.length >= 2) {
            viewModelScope.launch {
                musicRepository.saveRecentSearch(trimmed)
            }
        }
    }

    fun deleteRecentSearch(query: String) {
        viewModelScope.launch {
            musicRepository.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            musicRepository.clearRecentSearches()
        }
    }

    // --- Playback Actions ---
    fun playTrack(track: Track, contextTracks: List<Track> = uiState.value.sortedTracks) {
        val list = if (contextTracks.isNotEmpty() && contextTracks.any { it.id == track.id }) {
            contextTracks
        } else {
            listOf(track)
        }
        val startIndex = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playTracks(list, startIndex = startIndex, shuffle = false)
    }

    fun playAll(tracks: List<Track> = uiState.value.sortedTracks) {
        if (tracks.isEmpty()) return
        playbackController.playTracks(tracks, startIndex = 0, shuffle = false)
    }

    fun shuffleAll(tracks: List<Track> = uiState.value.sortedTracks) {
        if (tracks.isEmpty()) return
        playbackController.playTracks(tracks, startIndex = 0, shuffle = true)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun skipToNext() = playbackController.skipToNext()
    fun skipToPrevious() = playbackController.skipToPrevious()
    fun seekTo(positionMs: Long) = playbackController.seekTo(positionMs)
    fun seekForward10s() = playbackController.seekForward10s()
    fun seekBackward10s() = playbackController.seekBackward10s()
    fun toggleShuffle() = playbackController.toggleShuffle()
    fun cycleRepeatMode() = playbackController.cycleRepeatMode()

    fun playNext(track: Track) {
        playbackController.playNext(track)
        showMessage("Playing next: ${track.title}")
    }

    fun addToQueue(track: Track) {
        playbackController.addToQueue(track)
        showMessage("Added to queue: ${track.title}")
    }

    fun playQueueIndex(index: Int) = playbackController.playQueueIndex(index)
    fun removeQueueItemAt(index: Int) = playbackController.removeQueueItemAt(index)
    fun moveQueueItem(fromIndex: Int, toIndex: Int) = playbackController.moveQueueItem(fromIndex, toIndex)
    fun clearQueue() {
        playbackController.clearQueue()
        showMessage("Playback queue cleared")
    }

    fun clearPlaybackError() = playbackController.clearError()

    // --- Favourites & Playlists ---
    fun toggleFavourite(track: Track) {
        viewModelScope.launch {
            val isNowFav = musicRepository.toggleFavourite(track.id)
            showMessage(if (isNowFav) "Added to Favourites" else "Removed from Favourites")
        }
    }

    fun createPlaylist(name: String, initialTrackId: Long? = null) {
        val clean = name.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            val newId = musicRepository.createPlaylist(clean)
            if (initialTrackId != null) {
                musicRepository.addTrackToPlaylist(newId, initialTrackId)
                showMessage("Created \"$clean\" and added track")
            } else {
                showMessage("Created playlist \"$clean\"")
            }
        }
    }

    fun renamePlaylist(playlistId: Long, newName: String) {
        val clean = newName.trim()
        if (clean.isEmpty()) return
        viewModelScope.launch {
            musicRepository.renamePlaylist(playlistId, clean)
            showMessage("Renamed playlist to \"$clean\"")
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            musicRepository.deletePlaylist(playlistId)
            showMessage("Playlist deleted (device audio files kept)")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        viewModelScope.launch {
            musicRepository.addTrackToPlaylist(playlistId, track.id)
            showMessage("Added \"${track.title}\" to playlist")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
        viewModelScope.launch {
            musicRepository.removeTrackFromPlaylist(playlistId, trackId)
        }
    }

    fun moveTrackInPlaylist(playlistId: Long, currentTracks: List<Track>, fromIndex: Int, toIndex: Int) {
        if (fromIndex !in currentTracks.indices || toIndex !in currentTracks.indices || fromIndex == toIndex) return
        val mutable = currentTracks.map { it.id }.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)
        viewModelScope.launch {
            musicRepository.reorderPlaylistTracks(playlistId, mutable)
        }
    }

    // --- Lyrics ---
    fun importLyricsFromUri(trackId: Long, uri: Uri) {
        viewModelScope.launch {
            val result = musicRepository.importLyricsFromUri(trackId, uri)
            result.fold(
                onSuccess = { parsed ->
                    val mode = if (parsed.isSynced) "synchronized LRC" else "plain text"
                    showMessage("Imported $mode lyrics (${parsed.lines.size} lines)")
                },
                onFailure = { e ->
                    showMessage("Lyrics import failed: ${e.localizedMessage ?: "Invalid file"}")
                }
            )
        }
    }

    fun saveCustomLyricsText(trackId: Long, rawContent: String) {
        viewModelScope.launch {
            musicRepository.saveCustomLyricsText(trackId, rawContent)
            showMessage("Saved lyrics for current track")
        }
    }

    fun clearLyricsForTrack(trackId: Long) {
        viewModelScope.launch {
            musicRepository.deleteLyricsForTrack(trackId)
            showMessage("Removed saved lyrics")
        }
    }

    // --- Preferences & Settings ---
    fun setSortOption(option: SortOption) {
        viewModelScope.launch {
            preferencesRepository.setSortOption(option)
        }
    }

    fun setThemeOption(option: ThemeOption) {
        viewModelScope.launch {
            preferencesRepository.setThemeOption(option)
        }
    }

    fun setDefaultRepeatMode(mode: RepeatModeOption) {
        playbackController.setRepeatMode(mode)
        viewModelScope.launch {
            preferencesRepository.setRepeatMode(mode)
        }
    }

    fun setDefaultShuffle(enabled: Boolean) {
        if (playbackState.value.shuffleEnabled != enabled) {
            playbackController.toggleShuffle()
        }
        viewModelScope.launch {
            preferencesRepository.setShuffleEnabled(enabled)
        }
    }

    fun setMinPlayDurationSec(seconds: Int) {
        viewModelScope.launch {
            preferencesRepository.setMinPlayDurationSec(seconds)
            showMessage("Play history threshold set to ${seconds}s")
        }
    }

    fun setDemoModeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDemoModeEnabled(enabled)
            musicRepository.syncDemoModeTracks(enabled)
            if (enabled) {
                showMessage("Isolated Acoustic Synth Demo tracks loaded")
            } else {
                showMessage("Demo tracks removed")
            }
        }
    }

    fun clearListeningHistory() {
        viewModelScope.launch {
            musicRepository.clearPlayHistory()
            showMessage("Listening history cleared")
        }
    }

    fun consumeSnackbarMessage() {
        _snackbarMessage.value = null
    }

    private fun showMessage(msg: String) {
        _snackbarMessage.update { msg }
    }

    private fun sortTracks(tracks: List<Track>, sortOption: SortOption): List<Track> {
        return when (sortOption) {
            SortOption.TITLE -> tracks.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> tracks.sortedWith(
                compareBy<Track> { it.artist.lowercase() }.thenBy { it.title.lowercase() }
            )
            SortOption.ALBUM -> tracks.sortedWith(
                compareBy<Track> { it.album.lowercase() }
                    .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                    .thenBy { it.title.lowercase() }
            )
            SortOption.DURATION -> tracks.sortedByDescending { it.durationMs }
            SortOption.DATE_ADDED -> tracks.sortedByDescending { it.dateAddedSec }
        }
    }

    companion object {
        fun provideFactory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PulseViewModel(
                        musicRepository = container.musicRepository,
                        preferencesRepository = container.preferencesRepository,
                        playbackController = container.playbackController
                    ) as T
                }
            }
    }
}
