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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.PlaylistSummary
import com.example.domain.model.Track
import com.example.player.PlaybackState
import com.example.ui.PulseUiState
import com.example.ui.components.PulseArtwork
import com.example.ui.components.PulseWordmarkHeader
import com.example.ui.components.TrackListItem
import com.example.util.TimeFormatUtil

@Composable
fun PlaylistsScreen(
    uiState: PulseUiState,
    playbackState: PlaybackState,
    onCreatePlaylist: (String) -> Unit,
    onRenamePlaylist: (Long, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onAddTrackToPlaylist: (Long, Track) -> Unit,
    onRemoveTrackFromPlaylist: (Long, Long) -> Unit,
    onMoveTrackInPlaylist: (Long, List<Track>, Int, Int) -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var viewingFavourites by rememberSaveable { mutableStateOf(false) }
    var selectedPlaylistId by rememberSaveable { mutableStateOf<Long?>(null) }

    var showCreateDialog by remember { mutableStateOf(false) }
    var playlistToRename by remember { mutableStateOf<PlaylistSummary?>(null) }
    var playlistToDelete by remember { mutableStateOf<PlaylistSummary?>(null) }
    var showAddTracksModalForPlaylist by remember { mutableStateOf<PlaylistSummary?>(null) }

    val selectedPlaylist = remember(uiState.playlists, selectedPlaylistId) {
        selectedPlaylistId?.let { id -> uiState.playlists.firstOrNull { it.id == id } }
    }

    if (viewingFavourites) {
        BackHandler { viewingFavourites = false }
        FavouritesDetailScreen(
            favourites = uiState.favourites,
            playbackState = playbackState,
            onBack = { viewingFavourites = false },
            onPlayTrack = onPlayTrack,
            onToggleFavourite = onToggleFavourite,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylistRequest = onAddToPlaylistRequest
        )
        return
    }

    if (selectedPlaylist != null) {
        BackHandler { selectedPlaylistId = null }
        PlaylistDetailScreen(
            playlist = selectedPlaylist,
            playbackState = playbackState,
            onBack = { selectedPlaylistId = null },
            onOpenAddTracksModal = { showAddTracksModalForPlaylist = selectedPlaylist },
            onRenameRequest = { playlistToRename = selectedPlaylist },
            onDeleteRequest = { playlistToDelete = selectedPlaylist },
            onPlayTrack = onPlayTrack,
            onRemoveTrack = { trackId -> onRemoveTrackFromPlaylist(selectedPlaylist.id, trackId) },
            onMoveTrack = { fromIdx, toIdx ->
                onMoveTrackInPlaylist(selectedPlaylist.id, selectedPlaylist.tracks, fromIdx, toIdx)
            }
        )
    } else {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag("playlists_screen"),
            containerColor = Color.Transparent,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .padding(bottom = 86.dp)
                        .testTag("create_playlist_fab")
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Create new playlist")
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(bottom = 140.dp)
            ) {
                item {
                    PulseWordmarkHeader(
                        subtitle = "Playlists & Favourites",
                        trailingContent = {
                            OutlinedButton(
                                onClick = { showCreateDialog = true },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("new_playlist_header_btn")
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New")
                            }
                        }
                    )
                }

                // Dedicated Favourites Collection Card
                item {
                    val favTotalDuration = uiState.favourites.sumOf { it.durationMs }
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { viewingFavourites = true }
                            .testTag("favourites_collection_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = "Favourites",
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Favourites",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Text(
                                    text = "${uiState.favourites.size} tracks • ${TimeFormatUtil.formatTotalDurationCompact(favTotalDuration)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (uiState.favourites.isNotEmpty()) {
                                IconButton(
                                    onClick = {
                                        onPlayTrack(uiState.favourites.first(), uiState.favourites)
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play Favourites"
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Your Local Playlists (${uiState.playlists.size})",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }

                if (uiState.playlists.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(40.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No Custom Playlists Yet",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Create offline playlists that persist across app restarts. Reorder tracks, shuffle, or organize by mood.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(
                                    onClick = { showCreateDialog = true },
                                    modifier = Modifier.testTag("empty_create_playlist_btn")
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Create First Playlist")
                                }
                            }
                        }
                    }
                } else {
                    items(uiState.playlists, key = { it.id }) { playlist ->
                        PlaylistRowItem(
                            playlist = playlist,
                            onClick = { selectedPlaylistId = playlist.id },
                            onPlay = {
                                if (playlist.tracks.isNotEmpty()) {
                                    onPlayTrack(playlist.tracks.first(), playlist.tracks)
                                } else {
                                    selectedPlaylistId = playlist.id
                                }
                            },
                            onRename = { playlistToRename = playlist },
                            onDelete = { playlistToDelete = playlist }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "New Playlist",
            initialName = "",
            confirmLabel = "Create",
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                onCreatePlaylist(name)
                showCreateDialog = false
            }
        )
    }

    playlistToRename?.let { target ->
        PlaylistNameDialog(
            title = "Rename Playlist",
            initialName = target.name,
            confirmLabel = "Save",
            onDismiss = { playlistToRename = null },
            onConfirm = { newName ->
                onRenamePlaylist(target.id, newName)
                playlistToRename = null
            }
        )
    }

    playlistToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete \"${target.name}\"?") },
            text = {
                Text(
                    "This removes the playlist from PULSE. Your original audio files on the device will NOT be deleted."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val deletingId = target.id
                        playlistToDelete = null
                        if (selectedPlaylistId == deletingId) {
                            selectedPlaylistId = null
                        }
                        onDeletePlaylist(deletingId)
                    },
                    modifier = Modifier.testTag("confirm_delete_playlist_btn")
                ) {
                    Text("Delete Playlist")
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    showAddTracksModalForPlaylist?.let { targetPlaylist ->
        AddTracksToPlaylistModal(
            playlist = targetPlaylist,
            allTracks = uiState.sortedTracks,
            onDismiss = { showAddTracksModalForPlaylist = null },
            onAddSelectedTracks = { tracksToAdd ->
                tracksToAdd.forEach { track ->
                    onAddTrackToPlaylist(targetPlaylist.id, track)
                }
                showAddTracksModalForPlaylist = null
            }
        )
    }
}

@Composable
private fun PlaylistRowItem(
    playlist: PlaylistSummary,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("playlist_card_${playlist.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PulseArtwork(
                artworkUri = playlist.coverArtworkUri,
                contentDescription = playlist.name,
                modifier = Modifier.size(54.dp),
                cornerRadius = 12.dp
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${playlist.trackCount} tracks • ${TimeFormatUtil.formatTotalDurationCompact(playlist.totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onRename,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("rename_playlist_${playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Rename ${playlist.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("delete_playlist_${playlist.id}")
            ) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Delete ${playlist.name}",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
            IconButton(
                onClick = onPlay,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play ${playlist.name}",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun PlaylistDetailScreen(
    playlist: PlaylistSummary,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onOpenAddTracksModal: () -> Unit,
    onRenameRequest: () -> Unit,
    onDeleteRequest: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onRemoveTrack: (Long) -> Unit,
    onMoveTrack: (Int, Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to playlists")
                    }
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(onClick = onRenameRequest, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.Edit, contentDescription = "Rename playlist")
                    }
                    IconButton(onClick = onDeleteRequest, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete playlist")
                    }
                }
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
                    artworkUri = playlist.coverArtworkUri,
                    contentDescription = playlist.name,
                    modifier = Modifier.size(140.dp),
                    cornerRadius = 18.dp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "${playlist.trackCount} tracks • ${TimeFormatUtil.formatTotalDurationCompact(playlist.totalDurationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (playlist.tracks.isNotEmpty()) {
                                onPlayTrack(playlist.tracks.first(), playlist.tracks)
                            }
                        },
                        enabled = playlist.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play")
                    }

                    OutlinedButton(
                        onClick = {
                            if (playlist.tracks.isNotEmpty()) {
                                val shuffled = playlist.tracks.shuffled()
                                onPlayTrack(shuffled.first(), shuffled)
                            }
                        },
                        enabled = playlist.tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }

                    OutlinedButton(
                        onClick = onOpenAddTracksModal,
                        modifier = Modifier.height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add songs")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add")
                    }
                }
            }
        }

        if (playlist.tracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "This playlist is empty",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap Add above to pick tracks from your local library.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = playlist.tracks,
                key = { _, track -> "pl_trk_${track.id}" }
            ) { index, track ->
                val isCurrent = playbackState.currentTrack?.id == track.id
                Surface(
                    color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayTrack(track, playlist.tracks) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(26.dp)
                        )
                        PulseArtwork(
                            artworkUri = track.albumArtUri,
                            contentDescription = track.title,
                            modifier = Modifier.size(44.dp),
                            cornerRadius = 8.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${track.artist} • ${TimeFormatUtil.formatDuration(track.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = { onMoveTrack(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowUpward,
                                contentDescription = "Move track up",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveTrack(index, index + 1) },
                            enabled = index < playlist.tracks.lastIndex,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Move track down",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onRemoveTrack(track.id) },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.RemoveCircleOutline,
                                contentDescription = "Remove from playlist",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FavouritesDetailScreen(
    favourites: List<Track>,
    playbackState: PlaybackState,
    onBack: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit
) {
    val totalDuration = favourites.sumOf { it.durationMs }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 140.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(text = "Favourites", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "${favourites.size} tracks • ${TimeFormatUtil.formatTotalDurationCompact(totalDuration)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (favourites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Tap the heart icon on any track to save it to your Favourites.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onPlayTrack(favourites.first(), favourites) },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Play Favourites")
                    }
                    OutlinedButton(
                        onClick = {
                            val shuffled = favourites.shuffled()
                            onPlayTrack(shuffled.first(), shuffled)
                        },
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }

            items(favourites, key = { "fav_${it.id}" }) { track ->
                TrackListItem(
                    track = track,
                    isCurrentTrack = playbackState.currentTrack?.id == track.id,
                    isPlaying = playbackState.isPlaying,
                    onTrackClick = { onPlayTrack(track, favourites) },
                    onToggleFavourite = { onToggleFavourite(track) },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylistRequest(track) }
                )
            }
        }
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Playlist name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_name_dialog_input")
            )
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("playlist_name_dialog_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AddTracksToPlaylistModal(
    playlist: PlaylistSummary,
    allTracks: List<Track>,
    onDismiss: () -> Unit,
    onAddSelectedTracks: (List<Track>) -> Unit
) {
    val existingIds = remember(playlist.tracks) { playlist.tracks.map { it.id }.toSet() }
    val availableToAdd = remember(allTracks, existingIds) {
        allTracks.filter { it.id !in existingIds }
    }
    val selectedTracks = remember { mutableStateListOf<Track>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs to \"${playlist.name}\"") },
        text = {
            if (availableToAdd.isEmpty()) {
                Text(
                    text = "All indexed library songs are already in this playlist.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(availableToAdd, key = { it.id }) { track ->
                        val checked = selectedTracks.any { it.id == track.id }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (checked) selectedTracks.removeAll { it.id == track.id }
                                    else selectedTracks.add(track)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    if (isChecked) selectedTracks.add(track)
                                    else selectedTracks.removeAll { it.id == track.id }
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.bodyMedium,
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
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (availableToAdd.isNotEmpty()) {
                Button(
                    onClick = { onAddSelectedTracks(selectedTracks.toList()) },
                    enabled = selectedTracks.isNotEmpty()
                ) {
                    Text("Add (${selectedTracks.size})")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
