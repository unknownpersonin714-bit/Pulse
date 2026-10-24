package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.domain.model.RepeatModeOption
import com.example.domain.model.ThemeOption
import com.example.ui.PulseUiState
import com.example.ui.components.PulseWordmarkHeader
import com.example.util.TimeFormatUtil

@Composable
fun SettingsScreen(
    uiState: PulseUiState,
    onSelectTheme: (ThemeOption) -> Unit,
    onRescanLibrary: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSetDefaultRepeat: (RepeatModeOption) -> Unit,
    onSetDefaultShuffle: (Boolean) -> Unit,
    onSetMinPlayDurationSec: (Int) -> Unit,
    onSetDemoModeEnabled: (Boolean) -> Unit,
    onClearListeningHistory: () -> Unit,
    onClearRecentSearches: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showClearHistoryDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen"),
        contentPadding = PaddingValues(bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            PulseWordmarkHeader(subtitle = "Settings & Offline Diagnostics")
        }

        // Appearance Section
        item {
            SettingsCard(title = "Appearance") {
                Text(
                    text = "Pure Black (#000000) is the default OLED theme designed for minimal glare and crisp monochrome contrast.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ThemeOption.entries.forEach { option ->
                        val selected = uiState.preferences.themeOption == option
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTheme(option) },
                            label = { Text(option.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("theme_chip_${option.name}")
                        )
                    }
                }
            }
        }

        // Library & Permissions Section
        item {
            SettingsCard(title = "Device Audio Library & Permissions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "MediaStore Permission Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (uiState.hasAudioPermission) "Granted (Audio access active)" else "Not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!uiState.hasAudioPermission) {
                        OutlinedButton(
                            onClick = if (uiState.permissionPermanentlyDenied) onOpenAppSettings else onRequestPermission
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Grant")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Indexed Tracks: ${uiState.allTracks.count { !it.isDemoTrack }} MediaStore track(s)",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onRescanLibrary,
                    enabled = !uiState.scanProgress.isScanning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings_rescan_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (uiState.scanProgress.isScanning) "Scanning MediaStore…" else "Rescan Audio Library")
                }
            }
        }

        // Playback Preferences Section
        item {
            SettingsCard(title = "Playback Preferences") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Shuffle Playback",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Persist shuffle state across sessions",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.preferences.shuffleEnabled,
                        onCheckedChange = onSetDefaultShuffle,
                        modifier = Modifier.testTag("settings_shuffle_switch")
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Repeat Mode",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatModeOption.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.preferences.repeatMode == mode,
                            onClick = { onSetDefaultRepeat(mode) },
                            label = { Text("Repeat ${mode.name}") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Play History Qualification Threshold",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Minimum listening duration before a play is recorded in local history",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(5, 10, 15, 30).forEach { sec ->
                        FilterChip(
                            selected = uiState.preferences.minPlayDurationSec == sec,
                            onClick = { onSetMinPlayDurationSec(sec) },
                            label = { Text("${sec}s") }
                        )
                    }
                }
            }
        }

        // Listening Statistics & History Management
        item {
            SettingsCard(title = "Listening Statistics & History") {
                val stats = uiState.listeningStats
                Text(
                    text = "Total Recorded Plays: ${stats.totalPlays}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Total Listening Time: ${TimeFormatUtil.formatTotalDurationCompact(stats.totalListeningTimeMs)}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Unique Tracks Played: ${stats.uniqueTracksPlayed}",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (stats.topArtist != null) {
                    Text(
                        text = "Top Artist: ${stats.topArtist}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { showClearHistoryDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("clear_history_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear History")
                    }
                    OutlinedButton(
                        onClick = onClearRecentSearches,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Clear Searches")
                    }
                }
            }
        }

        // Isolated Debug / Acoustic Synthesizer Mode
        item {
            SettingsCard(title = "Isolated Acoustic Synthesizer (Debug / Demo Mode)") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Local PCM WAV Test Tracks",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Disabled in normal operation. When enabled, generates 3 real mathematical PCM .wav audio files with synchronized LRC lyrics in app cache so you can verify ExoPlayer audio and LRC sync on empty emulators.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = uiState.preferences.demoModeEnabled,
                        onCheckedChange = onSetDemoModeEnabled,
                        modifier = Modifier.testTag("settings_demo_mode_switch")
                    )
                }
            }
        }

        // Privacy & About PULSE
        item {
            SettingsCard(title = "Privacy & About PULSE") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "100% Offline & Private by Design",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "PULSE operates strictly offline. Your music metadata, playlists, favourites, LRC lyrics, and listening statistics never leave your device. No analytics SDKs, cloud accounts, or tracking permissions are included.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PULSE Audio Engine",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Text(
                        text = "v${BuildConfig.VERSION_NAME} (API 24–36 • Media3 ExoPlayer)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear Listening History?") },
            text = {
                Text(
                    "This will reset all play counts, Recently Played records, and listening statistics. Your playlists, favourites, and audio files will not be affected."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearHistoryDialog = false
                        onClearListeningHistory()
                    },
                    modifier = Modifier.testTag("confirm_clear_history_btn")
                ) {
                    Text("Clear History")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
