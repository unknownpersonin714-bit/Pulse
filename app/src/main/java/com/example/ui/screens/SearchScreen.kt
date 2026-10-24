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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Track
import com.example.player.PlaybackState
import com.example.ui.PulseUiState
import com.example.ui.SearchResultsState
import com.example.ui.components.PulseArtwork
import com.example.ui.components.PulseWordmarkHeader
import com.example.ui.components.TrackListItem

@Composable
fun SearchScreen(
    query: String,
    searchResults: SearchResultsState,
    uiState: PulseUiState,
    playbackState: PlaybackState,
    onQueryChange: (String) -> Unit,
    onCommitSearch: (String) -> Unit,
    onDeleteRecentSearch: (String) -> Unit,
    onClearRecentSearches: () -> Unit,
    onPlayTrack: (Track, List<Track>) -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayNext: (Track) -> Unit,
    onAddToQueue: (Track) -> Unit,
    onAddToPlaylistRequest: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen")
    ) {
        PulseWordmarkHeader(subtitle = "Search Indexed Library")

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = "Search songs, artists, albums, playlists…",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search icon"
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("search_clear_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Clear,
                            contentDescription = "Clear search query"
                        )
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    onCommitSearch(query)
                    focusManager.clearFocus()
                }
            ),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .testTag("search_input")
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            if (query.isBlank()) {
                if (uiState.recentSearches.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Recent Searches",
                                style = MaterialTheme.typography.titleMedium
                            )
                            TextButton(
                                onClick = onClearRecentSearches,
                                modifier = Modifier.testTag("clear_recent_searches_btn")
                            ) {
                                Text("Clear All")
                            }
                        }
                    }

                    items(uiState.recentSearches, key = { "recent_$it" }) { recent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onQueryChange(recent)
                                    onCommitSearch(recent)
                                }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = recent,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeleteRecentSearch(recent) },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Clear,
                                    contentDescription = "Remove recent search $recent",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                } else {
                    item {
                        EmptySearchPrompt(
                            title = "Search Your Offline Collection",
                            subtitle = "Find any indexed track, album, artist, or local playlist with instant case-insensitive matching."
                        )
                    }
                }
            } else if (searchResults.isEmpty) {
                item {
                    EmptySearchPrompt(
                        title = "No Matches for \"${searchResults.query}\"",
                        subtitle = "Check the spelling or rescan your device MediaStore if you recently added new audio files."
                    )
                }
            } else {
                // Matching Artists
                if (searchResults.matchingArtists.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Artists (${searchResults.matchingArtists.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults.matchingArtists, key = { "s_art_${it.name}" }) { artist ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .width(175.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onCommitSearch(query)
                                            if (artist.tracks.isNotEmpty()) {
                                                onPlayTrack(artist.tracks.first(), artist.tracks)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        PulseArtwork(
                                            artworkUri = artist.artworkUri,
                                            contentDescription = artist.name,
                                            modifier = Modifier.size(42.dp),
                                            cornerRadius = 21.dp
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = artist.name,
                                                style = MaterialTheme.typography.titleSmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${artist.trackCount} tracks",
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

                // Matching Albums
                if (searchResults.matchingAlbums.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Albums (${searchResults.matchingAlbums.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults.matchingAlbums, key = { "s_alb_${it.id}_${it.title}" }) { album ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .width(150.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onCommitSearch(query)
                                            if (album.tracks.isNotEmpty()) {
                                                onPlayTrack(album.tracks.first(), album.tracks)
                                            }
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        PulseArtwork(
                                            artworkUri = album.artworkUri,
                                            contentDescription = album.title,
                                            modifier = Modifier.size(130.dp),
                                            cornerRadius = 10.dp
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = album.title,
                                            style = MaterialTheme.typography.titleSmall,
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
                                    }
                                }
                            }
                        }
                    }
                }

                // Matching Playlists
                if (searchResults.matchingPlaylists.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Playlists (${searchResults.matchingPlaylists.size})")
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(searchResults.matchingPlaylists, key = { "s_pl_${it.id}" }) { playlist ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    modifier = Modifier
                                        .width(175.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            onCommitSearch(query)
                                            if (playlist.tracks.isNotEmpty()) {
                                                onPlayTrack(playlist.tracks.first(), playlist.tracks)
                                            }
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.QueueMusic,
                                            contentDescription = null,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = playlist.name,
                                                style = MaterialTheme.typography.titleSmall,
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

                // Matching Songs
                if (searchResults.matchingTracks.isNotEmpty()) {
                    item {
                        SearchCategoryHeader("Songs (${searchResults.matchingTracks.size})")
                    }
                    items(searchResults.matchingTracks, key = { "s_trk_${it.id}" }) { track ->
                        TrackListItem(
                            track = track,
                            isCurrentTrack = playbackState.currentTrack?.id == track.id,
                            isPlaying = playbackState.isPlaying,
                            onTrackClick = {
                                onCommitSearch(query)
                                onPlayTrack(track, searchResults.matchingTracks)
                            },
                            onToggleFavourite = { onToggleFavourite(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onAddToPlaylist = { onAddToPlaylistRequest(track) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchCategoryHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
    )
}

@Composable
private fun EmptySearchPrompt(
    title: String,
    subtitle: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
