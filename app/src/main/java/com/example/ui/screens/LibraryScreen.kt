package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Album
import com.example.domain.model.Artist
import com.example.domain.model.SortOption
import com.example.domain.model.Track
import com.example.player.PlaybackState
import com.example.ui.PulseUiState
import com.example.ui.components.NoMusicFoundStateCard
import com.example.ui.components.PermissionEmptyStateCard
import com.example.ui.components.PulseArtwork
import com.example.ui.components.PulseWordmarkHeader
import com.example.ui.components.TrackListItem
import com.example.util.TimeFormatUtil

private enum class LibraryTab(val label: String, val tag: String) {
    SONGS("Songs", "lib_tab_songs"),
    ALBUMS("Albums", "lib_tab_albums"),
    ARTISTS("Artists", "lib_tab_artists")
}

@Composable
fun LibraryScreen(
    uiState: PulseUiState,
    playbackState: PlaybackState,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRescanLibrary: () -> Unit,
    onEnableDemoSynth: () -> Unit,
    onSortOptionSelected: (SortOption) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by rememberSaveable { mutableStateOf(LibraryTab.SONGS) }
    var selectedAlbumId by rememberSaveable { mutableStateOf<Long?>(null) }
    var selectedAlbumName by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedArtistName by rememberSaveable { mutableStateOf<String?>(null) }
    var sortMenuExpanded by remember { mutableStateOf(false) }

    val activeAlbum = remember(uiState.albums, selectedAlbumId, selectedAlbumName) {
        if (selectedAlbumId == null && selectedAlbumName == null) null
        else uiState.albums.firstOrNull {
            it.id == selectedAlbumId && it.title.equals(selectedAlbumName, ignoreCase = true)
        }
    }

    val activeArtist = remember(uiState.artists, selectedArtistName) {
        selectedArtistName?.let { name ->
            uiState.artists.firstOrNull { it.name.equals(name, ignoreCase = true) }
        }
    }

    if (activeAlbum != null) {
        BackHandler {
            selectedAlbumId = null
            selectedAlbumName = null
        }
        CollectionDetailView(
            title = activeAlbum.title,
            subtitle = "${activeAlbum.artist} • ${activeAlbum.trackCount} tracks • ${TimeFormatUtil.formatTotalDurationCompact(activeAlbum.totalDurationMs)}",
            artworkUri = activeAlbum.artworkUri,
            tracks = activeAlbum.tracks,
            playbackState = playbackState,
            onBack = {
                selectedAlbumId = null
                selectedAlbumName = null
            },
            onPlayTrack = onPlayTrack,
            onToggleFavourite = onToggleFavourite,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylistRequest = onAddToPlaylistRequest
        )
        return
    }

    if (activeArtist != null) {
        BackHandler {
            selectedArtistName = null
        }
        CollectionDetailView(
            title = activeArtist.name,
            subtitle = "${activeArtist.albumCount} albums • ${activeArtist.trackCount} tracks • ${TimeFormatUtil.formatTotalDurationCompact(activeArtist.totalDurationMs)}",
            artworkUri = activeArtist.artworkUri,
            tracks = activeArtist.tracks,
            playbackState = playbackState,
            onBack = { selectedArtistName = null },
            onPlayTrack = onPlayTrack,
            onToggleFavourite = onToggleFavourite,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylistRequest = onAddToPlaylistRequest
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("library_screen")
    ) {
        PulseWordmarkHeader(
            subtitle = "Device Audio Library (${uiState.allTracks.size} tracks)",
            trailingContent = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("library_sort_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort tracks",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                        DropdownMenu(
                            expanded = sortMenuExpanded,
                            onDismissRequest = { sortMenuExpanded = false }
                        ) {
                            SortOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    trailingIcon = {
                                        if (uiState.preferences.sortOption == option) {
                                            Icon(Icons.Filled.Check, contentDescription = "Selected")
                                        }
                                    },
                                    onClick = {
                                        sortMenuExpanded = false
                                        onSortOptionSelected(option)
                                    }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onRescanLibrary,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("library_rescan_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Rescan library",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        )

        if (uiState.scanProgress.isScanning) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Category Tabs: Songs | Albums | Artists
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                val selected = selectedTab == tab
                FilterChip(
                    selected = selected,
                    onClick = { selectedTab = tab },
                    label = { Text(tab.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier.testTag(tab.tag)
                )
            }
        }

        if (!uiState.hasAudioPermission && uiState.allTracks.isEmpty()) {
            PermissionEmptyStateCard(
                permanentlyDenied = uiState.permissionPermanentlyDenied,
                onRequestPermission = onRequestPermission,
                onOpenAppSettings = onOpenAppSettings,
                onEnableDemoSynth = onEnableDemoSynth
            )
            return
        }

        if (uiState.allTracks.isEmpty()) {
            NoMusicFoundStateCard(
                isScanning = uiState.scanProgress.isScanning,
                onRescanLibrary = onRescanLibrary,
                onEnableDemoSynth = onEnableDemoSynth
            )
            return
        }

        when (selectedTab) {
            LibraryTab.SONGS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp)
                ) {
                    items(uiState.sortedTracks, key = { it.id }) { track ->
                        TrackListItem(
                            track = track,
                            isCurrentTrack = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            onTrackClick = { onPlayTrack(track, uiState.sortedTracks) },
                            onToggleFavourite = { onToggleFavourite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylistRequest(track) }
                        )
                    }
                }
            }

            LibraryTab.ALBUMS -> {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 155.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 140.dp
                    ),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.albums, key = { "${it.id}_${it.title}" }) { album ->
                        AlbumGridCard(
                            album = album,
                            onClick = {
                                selectedAlbumId = album.id
                                selectedAlbumName = album.title
                            }
                        )
                    }
                }
            }

            LibraryTab.ARTISTS -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 140.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(uiState.artists, key = { it.name }) { artist ->
                        ArtistRowCard(
                            artist = artist,
                            onClick = { selectedArtistName = artist.name }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            PulseArtwork(
                artworkUri = album.artworkUri,
                contentDescription = album.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(138.dp),
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.trackCount} tracks",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ArtistRowCard(
    artist: Artist,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseArtwork(
                artworkUri = artist.artworkUri,
                contentDescription = artist.name,
                modifier = Modifier.size(52.dp),
                cornerRadius = 26.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artist.albumCount} album(s) • ${artist.trackCount} track(s)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = TimeFormatUtil.formatTotalDurationCompact(artist.totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CollectionDetailView(
    title: String,
    subtitle: String,
    artworkUri: String?,
    tracks: List<Track>,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to library"
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PulseArtwork(
                    artworkUri = artworkUri,
                    contentDescription = title,
                    modifier = Modifier.size(160.dp),
                    cornerRadius = 18.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                onPlayTrack(tracks.first(), tracks)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play")
                    }
                    OutlinedButton(
                        onClick = {
                            if (tracks.isNotEmpty()) {
                                val shuffled = tracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        items(tracks, key = { "detail_${it.id}" }) { track ->
            TrackListItem(
                track = track,
                isCurrentTrack = playbackState.currentTrack?.id == track.id,
                isPlaying = playbackState.isPlaying,
                onTrackClick = { onPlayTrack(track, tracks) },
                onToggleFavourite = { onToggleFavourite(track) },
                onPlayNext = { onPlayNext(track) },
                onAddToQueue = { onAddToQueue(track) },
                onAddToPlaylist = { onAddToPlaylistRequest(track) }
            )
        }
    }
}
