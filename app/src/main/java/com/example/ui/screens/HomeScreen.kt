package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.Track
import com.example.player.PlaybackState
import com.example.ui.PulseUiState
import com.example.ui.components.NoMusicFoundStateCard
import com.example.ui.components.PermissionEmptyStateCard
import com.example.ui.components.PulseArtwork
import com.example.ui.components.PulseWordmarkHeader
import com.example.ui.components.TrackListItem
import com.example.util.TimeFormatUtil
import java.util.Calendar

@Composable
fun HomeScreen(
    uiState: PulseUiState,
    playbackState: PlaybackState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRescanLibrary: () -> Unit,
    onEnableDemoSynth: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    onNavigateToPlaylists: () -> Unit,
    modifier: Modifier = Modifier
) {
    val greeting = remember { computeTimeGreeting() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            PulseWordmarkHeader(
                subtitle = greeting,
                trailingContent = {
                    IconButton(
                        onClick = onRescanLibrary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("home_rescan_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan audio library",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }

        // Library Summary Bar
        item {
            LibrarySummaryBanner(
                trackCount = uiState.allTracks.size,
                albumCount = uiState.albums.size,
                artistCount = uiState.artists.size,
                totalPlays = uiState.listeningStats.totalPlays
            )
        }

        if (!uiState.hasAudioPermission && uiState.allTracks.isEmpty()) {
            item {
                PermissionEmptyStateCard(
                    permanentlyDenied = uiState.permissionPermanentlyDenied,
                    onRequestPermission = onRequestPermission,
                    onOpenAppSettings = onOpenAppSettings,
                    onEnableDemoSynth = onEnableDemoSynth
                )
            }
            return@LazyColumn
        }

        if (uiState.allTracks.isEmpty()) {
            item {
                NoMusicFoundStateCard(
                    isScanning = uiState.scanProgress.isScanning,
                    onRescanLibrary = onRescanLibrary,
                    onEnableDemoSynth = onEnableDemoSynth
                )
            }
            return@LazyColumn
        }

        // Primary Actions: Play All & Shuffle All
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_play_all_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Play All", style = MaterialTheme.typography.labelLarge)
                }

                OutlinedButton(
                    onClick = onShuffleAll,
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("home_shuffle_all_btn"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Icon(Icons.Filled.Shuffle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Shuffle All", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        // Continue Listening Card
        val activeTrack = playbackState.currentTrack ?: uiState.recommendations.recentlyPlayed.firstOrNull()
        if (activeTrack != null) {
            item {
                ContinueListeningCard(
                    track = activeTrack,
                    isCurrentlyLoaded = playbackState.currentTrack?.id == activeTrack.id,
                    isPlaying = playbackState.isPlaying,
                    onClick = {
                        if (playbackState.currentTrack?.id == activeTrack.id) {
                            onOpenNowPlaying()
                        } else {
                            onPlayTrack(activeTrack, uiState.sortedTracks)
                        }
                    }
                )
            }
        }

        // Quick Playlists & Favourites Row
        item {
            QuickPlaylistsRow(
                favouritesCount = uiState.favourites.size,
                playlists = uiState.playlists,
                onPlayFavourites = {
                    if (uiState.favourites.isNotEmpty()) {
                        onPlayTrack(uiState.favourites.first(), uiState.favourites)
                    } else {
                        onNavigateToPlaylists()
                    }
                },
                onSelectPlaylist = { playlist ->
                    if (playlist.tracks.isNotEmpty()) {
                        onPlayTrack(playlist.tracks.first(), playlist.tracks)
                    } else {
                        onNavigateToPlaylists()
                    }
                },
                onNavigateToPlaylists = onNavigateToPlaylists
            )
        }

        // Daily Mix Carousel
        if (uiState.recommendations.dailyMix.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Daily Mix",
                    subtitle = "Deterministic rotation from your local library & favourites",
                    actionLabel = "Play Mix",
                    onAction = {
                        val mix = uiState.recommendations.dailyMix
                        onPlayTrack(mix.first(), mix)
                    }
                )
                HorizontalTrackCarousel(
                    tracks = uiState.recommendations.dailyMix,
                    currentTrackId = playbackState.currentTrack?.id,
                    onTrackClick = { track ->
                        onPlayTrack(track, uiState.recommendations.dailyMix)
                    }
                )
            }
        }

        // Recently Played
        if (uiState.recommendations.recentlyPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Played",
                    subtitle = "Your latest local listening sessions"
                )
                HorizontalTrackCarousel(
                    tracks = uiState.recommendations.recentlyPlayed,
                    currentTrackId = playbackState.currentTrack?.id,
                    onTrackClick = { track ->
                        onPlayTrack(track, uiState.recommendations.recentlyPlayed)
                    }
                )
            }
        }

        // Frequently / Most Played Tracks
        if (uiState.recommendations.mostPlayed.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Frequently Played",
                    subtitle = "Ranked by your local play counts"
                )
            }
            items(
                items = uiState.recommendations.mostPlayed.take(5),
                key = { "most_${it.id}" }
            ) { track ->
                TrackListItem(
                    track = track,
                    isCurrentTrack = playbackState.currentTrack?.id == track.id,
                    isPlaying = playbackState.isPlaying,
                    onTrackClick = { onPlayTrack(track, uiState.recommendations.mostPlayed) },
                    onToggleFavourite = { onToggleFavourite(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylistRequest(track) },
                    trailingExtraContent = {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(start = 6.dp)
                        ) {
                            Text(
                                text = "${track.playCount}×",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                )
            }
        }

        // Recently Added
        if (uiState.recommendations.recentlyAdded.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Recently Added",
                    subtitle = "Latest tracks indexed from MediaStore"
                )
            }
            items(
                items = uiState.recommendations.recentlyAdded.take(6),
                key = { "recent_add_${it.id}" }
            ) { track ->
                TrackListItem(
                    track = track,
                    isCurrentTrack = playbackState.currentTrack?.id == track.id,
                    isPlaying = playbackState.isPlaying,
                    onTrackClick = { onPlayTrack(track, uiState.recommendations.recentlyAdded) },
                    onToggleFavourite = { onToggleFavourite(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylistRequest(track) }
                )
            }
        }

        // Rediscover / Discovery Section
        if (uiState.recommendations.rediscover.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "Discovery Rotation",
                    subtitle = "Unplayed or less-visited tracks from your library"
                )
                HorizontalTrackCarousel(
                    tracks = uiState.recommendations.rediscover,
                    currentTrackId = playbackState.currentTrack?.id,
                    onTrackClick = { track ->
                        onPlayTrack(track, uiState.recommendations.rediscover)
                    }
                )
            }
        }

        // Natural recommendation notice when history or library is small
        val notice = uiState.recommendations.historyNotice
        if (!notice.isNullOrBlank()) {
            item {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LibrarySummaryBanner(
    trackCount: Int,
    albumCount: Int,
    artistCount: Int,
    totalPlays: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StatPill(label = "TRACKS", value = trackCount.toString(), modifier = Modifier.weight(1f))
        StatPill(label = "ALBUMS", value = albumCount.toString(), modifier = Modifier.weight(1f))
        StatPill(label = "ARTISTS", value = artistCount.toString(), modifier = Modifier.weight(1f))
        StatPill(label = "PLAYS", value = totalPlays.toString(), modifier = Modifier.weight(1f))
    }
}

@Composable
private fun StatPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ContinueListeningCard(
    track: Track,
    isCurrentlyLoaded: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag("continue_listening_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseArtwork(
                artworkUri = track.albumArtUri,
                contentDescription = track.title,
                modifier = Modifier.size(64.dp),
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isCurrentlyLoaded && isPlaying) "NOW PLAYING" else "CONTINUE LISTENING",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${track.artist} • ${track.album}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Resume playback",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickPlaylistsRow(
    favouritesCount: Int,
    playlists: List<PlaylistSummary>,
    onPlayFavourites: () -> Unit,
    onSelectPlaylist: (PlaylistSummary) -> Unit,
    onNavigateToPlaylists: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        SectionHeader(
            title = "Playlists & Favourites",
            actionLabel = "Manage",
            onAction = onNavigateToPlaylists
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onPlayFavourites)
                        .testTag("quick_favourites_card")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Favorite,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Favourites",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1
                            )
                            Text(
                                text = "$favouritesCount tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            items(playlists, key = { "quick_pl_${it.id}" }) { playlist ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .width(175.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectPlaylist(playlist) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = playlist.name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${playlist.trackCount} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onAction)
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun HorizontalTrackCarousel(
    tracks: List<Track>,
    currentTrackId: Long?,
    onTrackClick: (Track) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        items(tracks, key = { it.id }) { track ->
            val isCurrent = track.id == currentTrackId
            Column(
                modifier = Modifier
                    .width(138.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { onTrackClick(track) }
                    .padding(4.dp)
            ) {
                PulseArtwork(
                    artworkUri = track.albumArtUri,
                    contentDescription = track.title,
                    modifier = Modifier.size(130.dp),
                    cornerRadius = 14.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = TimeFormatUtil.formatDuration(track.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun computeTimeGreeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night session"
    }
}
