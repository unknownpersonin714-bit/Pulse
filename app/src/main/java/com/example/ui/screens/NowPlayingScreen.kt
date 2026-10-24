package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.ParsedLyrics
import com.example.domain.model.RepeatModeOption
import com.example.domain.model.Track
import com.example.player.PlaybackState
import com.example.ui.components.PulseArtwork
import com.example.util.LrcParser
import com.example.util.TimeFormatUtil
import kotlinx.coroutines.flow.StateFlow

private enum class NowPlayingPane(val label: String) {
    ARTWORK("Player"),
    QUEUE("Queue"),
    LYRICS("Lyrics")
}

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    currentPositionMsFlow: StateFlow<Long>,
    parsedLyrics: ParsedLyrics?,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekForward10s: () -> Unit,
    onSeekBackward10s: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleFavourite: (Track) -> Unit,
    onPlayQueueIndex: (Int) -> Unit,
    onRemoveQueueIndex: (Int) -> Unit,
    onMoveQueueItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit,
    onImportLyricsUri: (Long, Uri) -> Unit,
    onSaveManualLyrics: (Long, String) -> Unit,
    onClearLyrics: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onClose)

    val currentTrack = playbackState.currentTrack
    if (currentTrack == null) {
        onClose()
        return
    }

    var activePane by rememberSaveable { mutableStateOf(NowPlayingPane.ARTWORK) }
    var showEditLyricsModal by remember { mutableStateOf(false) }

    val lyricsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onImportLyricsUri(currentTrack.id, uri)
        }
    }

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("now_playing_screen"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Top Bar: Collapse button + Pane Switcher (Player | Queue | Lyrics)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("collapse_now_playing_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Collapse Now Playing",
                        modifier = Modifier.size(30.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NowPlayingPane.entries.forEach { pane ->
                        val selected = activePane == pane
                        FilterChip(
                            selected = selected,
                            onClick = { activePane = pane },
                            label = { Text(pane.label) },
                            leadingIcon = {
                                val icon = when (pane) {
                                    NowPlayingPane.ARTWORK -> Icons.Filled.MusicNote
                                    NowPlayingPane.QUEUE -> Icons.AutoMirrored.Filled.QueueMusic
                                    NowPlayingPane.LYRICS -> Icons.Filled.Lyrics
                                }
                                Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("np_pane_${pane.name.lowercase()}")
                        )
                    }
                }
            }

            // Playback Error Banner if any
            if (!playbackState.playbackError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Text(
                        text = playbackState.playbackError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                when (activePane) {
                    NowPlayingPane.ARTWORK -> {
                        val artworkScale by animateFloatAsState(
                            targetValue = if (playbackState.isPlaying) 1.0f else 0.94f,
                            animationSpec = tween(durationMillis = 280),
                            label = "artwork_scale"
                        )
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PulseArtwork(
                                artworkUri = currentTrack.albumArtUri,
                                contentDescription = "${currentTrack.title} album artwork",
                                modifier = Modifier
                                    .fillMaxWidth(0.86f)
                                    .aspectRatio(1f)
                                    .scale(artworkScale),
                                cornerRadius = 24.dp
                            )
                        }
                    }

                    NowPlayingPane.QUEUE -> {
                        NowPlayingQueueSection(
                            queue = playbackState.queue,
                            currentIndex = playbackState.currentIndex,
                            onPlayIndex = onPlayQueueIndex,
                            onRemoveIndex = onRemoveQueueIndex,
                            onMoveItem = onMoveQueueItem,
                            onClearQueue = onClearQueue
                        )
                    }

                    NowPlayingPane.LYRICS -> {
                        NowPlayingLyricsSection(
                            parsedLyrics = parsedLyrics,
                            currentPositionMsFlow = currentPositionMsFlow,
                            onSeekTo = onSeekTo,
                            onImportLyricsFile = {
                                lyricsPickerLauncher.launch(arrayOf("text/*", "application/octet-stream", "*/*"))
                            },
                            onOpenManualEditor = { showEditLyricsModal = true },
                            onClearLyrics = { onClearLyrics(currentTrack.id) }
                        )
                    }
                }
            }

            // Track Metadata & Favourite Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack.title,
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${currentTrack.artist} • ${currentTrack.album}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(
                    onClick = { onToggleFavourite(currentTrack) },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("np_favourite_btn")
                ) {
                    Icon(
                        imageVector = if (currentTrack.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Toggle favourite",
                        tint = if (currentTrack.isFavourite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Interactive Seek Slider & Elapsed/Total Time
            NowPlayingSeekSection(
                durationMs = playbackState.durationMs.takeIf { it > 0 } ?: currentTrack.durationMs,
                currentPositionMsFlow = currentPositionMsFlow,
                onSeekTo = onSeekTo
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Primary Playback Controls Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("np_shuffle_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        }
                    )
                }

                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("np_prev_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = "Previous track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onSeekBackward10s,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Rewind 10 seconds",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(68.dp)
                ) {
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("np_play_pause_btn")
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSeekForward10s,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Forward10,
                        contentDescription = "Forward 10 seconds",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("np_next_btn")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Next track",
                        modifier = Modifier.size(34.dp)
                    )
                }

                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("np_repeat_btn")
                ) {
                    val repeatIcon = when (playbackState.repeatMode) {
                        RepeatModeOption.ONE -> Icons.Filled.RepeatOne
                        else -> Icons.Filled.Repeat
                    }
                    val repeatTint = when (playbackState.repeatMode) {
                        RepeatModeOption.OFF -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        RepeatModeOption.ALL, RepeatModeOption.ONE -> MaterialTheme.colorScheme.primary
                    }
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat mode ${playbackState.repeatMode.name}",
                        tint = repeatTint
                    )
                }
            }
        }
    }

    if (showEditLyricsModal) {
        ManualLyricsEditorDialog(
            initialText = parsedLyrics?.rawText ?: "",
            onDismiss = { showEditLyricsModal = false },
            onSave = { rawText ->
                onSaveManualLyrics(currentTrack.id, rawText)
                showEditLyricsModal = false
            }
        )
    }
}

@Composable
private fun NowPlayingSeekSection(
    durationMs: Long,
    currentPositionMsFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit
) {
    val livePositionMs by currentPositionMsFlow.collectAsStateWithLifecycle()
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val safeDuration = durationMs.coerceAtLeast(1L)
    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        (livePositionMs.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    }
    val displayedPositionMs = if (isDragging) {
        (dragFraction * safeDuration).toLong()
    } else {
        livePositionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = currentFraction,
            onValueChange = { fraction ->
                isDragging = true
                dragFraction = fraction
            },
            onValueChangeFinished = {
                val targetMs = (dragFraction * safeDuration).toLong()
                isDragging = false
                onSeekTo(targetMs)
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("np_seek_slider")
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = TimeFormatUtil.formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = TimeFormatUtil.formatDuration(durationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NowPlayingQueueSection(
    queue: List<Track>,
    currentIndex: Int,
    onPlayIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onClearQueue: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playback Queue (${queue.size})",
                style = MaterialTheme.typography.titleLarge
            )
            if (queue.isNotEmpty()) {
                TextButton(
                    onClick = onClearQueue,
                    modifier = Modifier.testTag("clear_queue_btn")
                ) {
                    Icon(Icons.Filled.ClearAll, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear")
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(queue, key = { idx, trk -> "q_${idx}_${trk.id}" }) { index, track ->
                val isCurrent = index == currentIndex
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isCurrent) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onPlayIndex(index) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${index + 1}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = MaterialTheme.typography.titleSmall,
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
                        IconButton(
                            onClick = { onMoveItem(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onMoveItem(index, index + 1) },
                            enabled = index < queue.lastIndex,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onRemoveIndex(index) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from queue", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingLyricsSection(
    parsedLyrics: ParsedLyrics?,
    currentPositionMsFlow: StateFlow<Long>,
    onSeekTo: (Long) -> Unit,
    onImportLyricsFile: () -> Unit,
    onOpenManualEditor: () -> Unit,
    onClearLyrics: () -> Unit
) {
    val positionMs by currentPositionMsFlow.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    val activeIndex = remember(parsedLyrics, positionMs) {
        if (parsedLyrics != null && parsedLyrics.isSynced) {
            LrcParser.findActiveLineIndex(parsedLyrics.lines, positionMs)
        } else {
            -1
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            runCatching {
                listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (parsedLyrics != null && parsedLyrics.lines.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = if (parsedLyrics.isSynced) {
                            "LRC SYNCHRONIZED • ${parsedLyrics.sourceLabel}"
                        } else {
                            "PLAIN TEXT • ${parsedLyrics.sourceLabel}"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                Text(
                    text = "Offline Lyrics Viewer",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(
                    onClick = onImportLyricsFile,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("import_lrc_btn")
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = "Import .lrc or .txt file")
                }
                IconButton(
                    onClick = onOpenManualEditor,
                    modifier = Modifier
                        .size(44.dp)
                        .testTag("edit_lrc_btn")
                ) {
                    Icon(Icons.Filled.EditNote, contentDescription = "Paste or edit lyrics")
                }
                if (parsedLyrics != null) {
                    IconButton(
                        onClick = onClearLyrics,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove lyrics")
                    }
                }
            }
        }

        if (parsedLyrics == null || parsedLyrics.lines.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Lyrics,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No Local Lyrics Found",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Import a compatible .lrc or .txt file from your device storage, or paste timestamped [mm:ss.xx] lyrics to synchronize with playback offline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = onImportLyricsFile) {
                            Icon(Icons.Filled.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import .LRC File")
                        }
                        OutlinedButton(onClick = onOpenManualEditor) {
                            Text("Paste LRC")
                        }
                    }
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxSize()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    itemsIndexed(parsedLyrics.lines) { idx, line ->
                        val isActive = parsedLyrics.isSynced && idx == activeIndex
                        val textColor = when {
                            !parsedLyrics.isSynced -> MaterialTheme.colorScheme.onBackground
                            isActive -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isActive) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                                )
                                .clickable(enabled = line.timestampMs != null) {
                                    line.timestampMs?.let { onSeekTo(it) }
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (line.timestampMs != null) {
                                Text(
                                    text = TimeFormatUtil.formatDuration(line.timestampMs),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(44.dp)
                                )
                            }
                            Text(
                                text = line.text,
                                style = if (isActive) {
                                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                } else {
                                    MaterialTheme.typography.bodyLarge
                                },
                                color = textColor,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ManualLyricsEditorDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialText) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste or Edit Lyrics (.LRC or Plain Text)") },
        text = {
            Column {
                Text(
                    text = "Tip: Include [mm:ss.xx] timestamps for synchronized highlighting, or enter plain text lines.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text("[00:00.00] First line\n[00:04.50] Second line")
                    },
                    minLines = 6,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("manual_lyrics_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                modifier = Modifier.testTag("save_manual_lyrics_btn")
            ) {
                Text("Save Lyrics")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
