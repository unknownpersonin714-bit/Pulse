package com.example.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.repository.MusicRepository
import com.example.domain.model.RepeatModeOption
import com.example.domain.model.Track
import com.example.settings.UserPreferencesRepository
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlaybackState(
    val currentTrack: Track? = null,
    val queue: List<Track> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val playbackError: String? = null
)

class PlaybackController(
    private val context: Context,
    private val musicRepository: MusicRepository,
    private val preferencesRepository: UserPreferencesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player: ExoPlayer = PulsePlayerHolder.getOrCreatePlayer(context)

    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    // Separated high-frequency progress StateFlow to avoid recomposing non-progress UI
    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private var progressJob: Job? = null
    private var hasRestoredSession = false

    // Play history qualification tracking
    private var activeTrackingTrackId: Long? = null
    private var accumulatedPlayMs: Long = 0L
    private var hasRecordedPlayForCurrentItem: Boolean = false
    private var minPlayDurationMs: Long = 10_000L

    private val trackLookupMap = mutableMapOf<Long, Track>()

    init {
        setupPlayerListener()
        connectMediaSessionService()
        observePreferences()
    }

    private fun connectMediaSessionService() {
        runCatching {
            val sessionToken = SessionToken(
                context,
                ComponentName(context, PulsePlaybackService::class.java)
            )
            controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        }
    }

    private fun observePreferences() {
        scope.launch {
            preferencesRepository.preferencesFlow.collect { prefs ->
                minPlayDurationMs = (prefs.minPlayDurationSec * 1000L).coerceAtLeast(3000L)
            }
        }
    }

    fun syncCatalogTracks(tracks: List<Track>) {
        trackLookupMap.clear()
        for (t in tracks) {
            trackLookupMap[t.id] = t
        }

        // Refresh metadata/favourite state in current queue if tracks updated
        val currentQueue = _playbackState.value.queue
        if (currentQueue.isNotEmpty()) {
            val updatedQueue = currentQueue.map { qTrack ->
                trackLookupMap[qTrack.id] ?: qTrack
            }
            val updatedCurrent = _playbackState.value.currentTrack?.let { cur ->
                trackLookupMap[cur.id] ?: cur
            }
            _playbackState.update {
                it.copy(
                    queue = updatedQueue,
                    currentTrack = updatedCurrent
                )
            }
        }

        if (!hasRestoredSession && tracks.isNotEmpty() && _playbackState.value.queue.isEmpty()) {
            hasRestoredSession = true
            restorePreviousQueueIfPossible(tracks)
        }
    }

    private fun restorePreviousQueueIfPossible(availableTracks: List<Track>) {
        scope.launch {
            val prefs = preferencesRepository.preferencesFlow.first()
            player.shuffleModeEnabled = prefs.shuffleEnabled
            player.repeatMode = prefs.repeatMode.toMedia3RepeatMode()
            _playbackState.update {
                it.copy(
                    shuffleEnabled = prefs.shuffleEnabled,
                    repeatMode = prefs.repeatMode
                )
            }

            if (prefs.lastQueueIds.isEmpty()) return@launch
            val byId = availableTracks.associateBy { it.id }
            val restoredQueue = prefs.lastQueueIds.mapNotNull { byId[it] }
            if (restoredQueue.isEmpty()) return@launch

            val targetIndex = prefs.lastPlayedTrackId
                ?.let { targetId -> restoredQueue.indexOfFirst { it.id == targetId } }
                ?.takeIf { it >= 0 }
                ?: 0

            val mediaItems = restoredQueue.map { it.toMediaItem() }
            player.setMediaItems(mediaItems, targetIndex, prefs.lastPositionMs.coerceAtLeast(0L))
            player.prepare()
            player.playWhenReady = false

            val currentTrack = restoredQueue.getOrNull(targetIndex)
            _playbackState.update {
                it.copy(
                    queue = restoredQueue,
                    currentTrack = currentTrack,
                    currentIndex = targetIndex,
                    durationMs = currentTrack?.durationMs ?: 0L,
                    isPlaying = false
                )
            }
            _currentPositionMs.value = prefs.lastPositionMs.coerceAtLeast(0L)
        }
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val newTrackId = mediaItem?.mediaId?.toLongOrNull()
                resetPlayQualificationCounter(newTrackId)
                refreshStateFromPlayer()
                persistSessionSnapshot()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                refreshStateFromPlayer()
                if (isPlaying) {
                    startProgressLoop()
                } else {
                    stopProgressLoop()
                    persistSessionSnapshot()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                refreshStateFromPlayer()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _playbackState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
                scope.launch { preferencesRepository.setShuffleEnabled(shuffleModeEnabled) }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val domainMode = repeatMode.toDomainRepeatMode()
                _playbackState.update { it.copy(repeatMode = domainMode) }
                scope.launch { preferencesRepository.setRepeatMode(domainMode) }
            }

            override fun onPlayerError(error: PlaybackException) {
                val failingTrack = _playbackState.value.currentTrack
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
                    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE,
                    PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
                        "Audio file unavailable or removed: ${failingTrack?.title ?: "Unknown Track"}"
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ->
                        "Unsupported audio format for ${failingTrack?.title ?: "selected track"}"
                    else ->
                        "Playback error (${error.errorCodeName}): ${failingTrack?.title ?: "Unknown"}"
                }

                _playbackState.update {
                    it.copy(
                        isPlaying = false,
                        isBuffering = false,
                        playbackError = message
                    )
                }

                if (failingTrack != null) {
                    scope.launch {
                        musicRepository.markTrackUnavailable(failingTrack.id)
                    }
                }

                // Automatically advance to next valid track if queue has more tracks
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.prepare()
                    player.play()
                }
            }
        })
    }

    private fun resetPlayQualificationCounter(trackId: Long?) {
        activeTrackingTrackId = trackId
        accumulatedPlayMs = 0L
        hasRecordedPlayForCurrentItem = false
    }

    private fun startProgressLoop() {
        progressJob?.cancel()
        progressJob = scope.launch {
            val pollIntervalMs = 250L
            while (isActive && player.isPlaying) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                _currentPositionMs.value = pos

                val dur = player.duration.takeIf { it > 0 } ?: _playbackState.value.currentTrack?.durationMs ?: 0L
                if (dur > 0 && dur != _playbackState.value.durationMs) {
                    _playbackState.update { it.copy(durationMs = dur) }
                }

                // Record play history once threshold is met
                val curTrack = _playbackState.value.currentTrack
                if (curTrack != null && curTrack.id == activeTrackingTrackId && !hasRecordedPlayForCurrentItem) {
                    accumulatedPlayMs += pollIntervalMs
                    val targetThreshold = minOf(minPlayDurationMs, (curTrack.durationMs * 0.45).toLong().coerceAtLeast(3000L))
                    if (accumulatedPlayMs >= targetThreshold) {
                        hasRecordedPlayForCurrentItem = true
                        musicRepository.recordTrackPlay(
                            trackId = curTrack.id,
                            listenedDurationMs = curTrack.durationMs
                        )
                    }
                }

                delay(pollIntervalMs)
            }
        }
    }

    private fun stopProgressLoop() {
        progressJob?.cancel()
        progressJob = null
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
    }

    private fun refreshStateFromPlayer() {
        val count = player.mediaItemCount
        val currentQueue = (0 until count).mapNotNull { idx ->
            val item = player.getMediaItemAt(idx)
            val id = item.mediaId.toLongOrNull() ?: return@mapNotNull null
            trackLookupMap[id] ?: item.toFallbackTrack(id)
        }
        val currentIdx = player.currentMediaItemIndex.takeIf { it in currentQueue.indices } ?: -1
        val currentTrack = currentQueue.getOrNull(currentIdx)
        val playerDuration = player.duration.takeIf { it > 0 } ?: currentTrack?.durationMs ?: 0L

        _playbackState.update { state ->
            state.copy(
                currentTrack = currentTrack,
                queue = currentQueue,
                currentIndex = currentIdx,
                isPlaying = player.isPlaying,
                isBuffering = player.playbackState == Player.STATE_BUFFERING,
                durationMs = playerDuration,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode.toDomainRepeatMode()
            )
        }
        _currentPositionMs.value = player.currentPosition.coerceAtLeast(0L)
    }

    fun playTracks(
        tracks: List<Track>,
        startIndex: Int = 0,
        shuffle: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        clearError()
        for (t in tracks) {
            trackLookupMap[t.id] = t
        }

        val orderedList = if (shuffle) tracks.shuffled() else tracks
        val safeStartIndex = if (shuffle) 0 else startIndex.coerceIn(0, orderedList.lastIndex)

        player.shuffleModeEnabled = shuffle
        val mediaItems = orderedList.map { it.toMediaItem() }
        player.setMediaItems(mediaItems, safeStartIndex, 0L)
        player.prepare()
        player.play()

        resetPlayQualificationCounter(orderedList[safeStartIndex].id)
        refreshStateFromPlayer()
        persistSessionSnapshot()
    }

    fun togglePlayPause() {
        clearError()
        if (player.mediaItemCount == 0) return
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
                if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(player.currentMediaItemIndex, 0L)
                }
            }
            player.play()
        }
        refreshStateFromPlayer()
    }

    fun skipToNext() {
        clearError()
        if (player.mediaItemCount == 0) return
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
        } else if (player.repeatMode == Player.REPEAT_MODE_ALL && player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
        }
        player.play()
        refreshStateFromPlayer()
    }

    fun skipToPrevious() {
        clearError()
        if (player.mediaItemCount == 0) return
        if (player.currentPosition > 3500L) {
            player.seekTo(0L)
        } else if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem()
        } else {
            player.seekTo(0L)
        }
        player.play()
        refreshStateFromPlayer()
    }

    fun seekTo(positionMs: Long) {
        val duration = _playbackState.value.durationMs
        val clamped = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        player.seekTo(clamped)
        _currentPositionMs.value = clamped
        persistSessionSnapshot()
    }

    fun seekForward10s() {
        seekTo(_currentPositionMs.value + 10_000L)
    }

    fun seekBackward10s() {
        seekTo((_currentPositionMs.value - 10_000L).coerceAtLeast(0L))
    }

    fun toggleShuffle() {
        val next = !player.shuffleModeEnabled
        player.shuffleModeEnabled = next
        _playbackState.update { it.copy(shuffleEnabled = next) }
    }

    fun cycleRepeatMode() {
        val nextMode = when (_playbackState.value.repeatMode) {
            RepeatModeOption.OFF -> RepeatModeOption.ALL
            RepeatModeOption.ALL -> RepeatModeOption.ONE
            RepeatModeOption.ONE -> RepeatModeOption.OFF
        }
        player.repeatMode = nextMode.toMedia3RepeatMode()
        _playbackState.update { it.copy(repeatMode = nextMode) }
    }

    fun setRepeatMode(mode: RepeatModeOption) {
        player.repeatMode = mode.toMedia3RepeatMode()
        _playbackState.update { it.copy(repeatMode = mode) }
    }

    fun playNext(track: Track) {
        trackLookupMap[track.id] = track
        if (player.mediaItemCount == 0) {
            playTracks(listOf(track), startIndex = 0)
            return
        }
        val insertIndex = (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
        player.addMediaItem(insertIndex, track.toMediaItem())
        refreshStateFromPlayer()
        persistSessionSnapshot()
    }

    fun addToQueue(track: Track) {
        trackLookupMap[track.id] = track
        if (player.mediaItemCount == 0) {
            playTracks(listOf(track), startIndex = 0)
            return
        }
        player.addMediaItem(track.toMediaItem())
        refreshStateFromPlayer()
        persistSessionSnapshot()
    }

    fun playQueueIndex(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        clearError()
        player.seekTo(index, 0L)
        player.prepare()
        player.play()
        refreshStateFromPlayer()
    }

    fun removeQueueItemAt(index: Int) {
        if (index !in 0 until player.mediaItemCount) return
        player.removeMediaItem(index)
        refreshStateFromPlayer()
        persistSessionSnapshot()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in 0 until player.mediaItemCount || toIndex !in 0 until player.mediaItemCount) return
        if (fromIndex == toIndex) return
        player.moveMediaItem(fromIndex, toIndex)
        refreshStateFromPlayer()
        persistSessionSnapshot()
    }

    fun clearQueue() {
        player.stop()
        player.clearMediaItems()
        stopProgressLoop()
        _playbackState.update {
            it.copy(
                currentTrack = null,
                queue = emptyList(),
                currentIndex = -1,
                isPlaying = false,
                durationMs = 0L
            )
        }
        _currentPositionMs.value = 0L
        persistSessionSnapshot()
    }

    fun clearError() {
        _playbackState.update { it.copy(playbackError = null) }
    }

    private fun persistSessionSnapshot() {
        val state = _playbackState.value
        val queueIds = state.queue.map { it.id }
        val currentId = state.currentTrack?.id
        val pos = player.currentPosition.coerceAtLeast(0L)
        scope.launch {
            preferencesRepository.savePlaybackSession(
                queueTrackIds = queueIds,
                currentTrackId = currentId,
                positionMs = pos
            )
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(artist)
            .setAlbumTitle(album)

        if (!albumArtUri.isNullOrBlank()) {
            metadataBuilder.setArtworkUri(Uri.parse(albumArtUri))
        }

        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(Uri.parse(contentUri))
            .setMediaMetadata(metadataBuilder.build())
            .build()
    }

    private fun MediaItem.toFallbackTrack(id: Long): Track {
        return Track(
            id = id,
            title = mediaMetadata.title?.toString() ?: "Unknown Track",
            artist = mediaMetadata.artist?.toString() ?: "Unknown Artist",
            album = mediaMetadata.albumTitle?.toString() ?: "Unknown Album",
            albumId = 0L,
            durationMs = 0L,
            contentUri = localConfiguration?.uri?.toString() ?: "",
            albumArtUri = mediaMetadata.artworkUri?.toString(),
            dateAddedSec = 0L
        )
    }

    private fun RepeatModeOption.toMedia3RepeatMode(): Int = when (this) {
        RepeatModeOption.OFF -> Player.REPEAT_MODE_OFF
        RepeatModeOption.ALL -> Player.REPEAT_MODE_ALL
        RepeatModeOption.ONE -> Player.REPEAT_MODE_ONE
    }

    private fun Int.toDomainRepeatMode(): RepeatModeOption = when (this) {
        Player.REPEAT_MODE_ALL -> RepeatModeOption.ALL
        Player.REPEAT_MODE_ONE -> RepeatModeOption.ONE
        else -> RepeatModeOption.OFF
    }
}
