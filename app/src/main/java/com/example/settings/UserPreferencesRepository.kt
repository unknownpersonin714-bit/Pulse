package com.example.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.RepeatModeOption
import com.example.domain.model.SortOption
import com.example.domain.model.ThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.pulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_preferences")

data class UserPreferencesState(
    val themeOption: ThemeOption = ThemeOption.DARK,
    val sortOption: SortOption = SortOption.TITLE,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatModeOption = RepeatModeOption.OFF,
    val lastQueueIds: List<Long> = emptyList(),
    val lastPlayedTrackId: Long? = null,
    val lastPositionMs: Long = 0L,
    val minPlayDurationSec: Int = 10,
    val demoModeEnabled: Boolean = false
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME = stringPreferencesKey("theme_option")
        val SORT = stringPreferencesKey("sort_option")
        val SHUFFLE = booleanPreferencesKey("shuffle_enabled")
        val REPEAT = stringPreferencesKey("repeat_mode")
        val LAST_QUEUE_IDS = stringPreferencesKey("last_queue_ids")
        val LAST_TRACK_ID = longPreferencesKey("last_track_id")
        val LAST_POSITION_MS = longPreferencesKey("last_position_ms")
        val MIN_PLAY_SEC = intPreferencesKey("min_play_duration_sec")
        val DEMO_MODE = booleanPreferencesKey("demo_mode_enabled")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.pulseDataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val theme = runCatching {
                ThemeOption.valueOf(prefs[Keys.THEME] ?: ThemeOption.DARK.name)
            }.getOrDefault(ThemeOption.DARK)

            val sort = runCatching {
                SortOption.valueOf(prefs[Keys.SORT] ?: SortOption.TITLE.name)
            }.getOrDefault(SortOption.TITLE)

            val repeat = runCatching {
                RepeatModeOption.valueOf(prefs[Keys.REPEAT] ?: RepeatModeOption.OFF.name)
            }.getOrDefault(RepeatModeOption.OFF)

            val queueIds = prefs[Keys.LAST_QUEUE_IDS]
                ?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?: emptyList()

            UserPreferencesState(
                themeOption = theme,
                sortOption = sort,
                shuffleEnabled = prefs[Keys.SHUFFLE] ?: false,
                repeatMode = repeat,
                lastQueueIds = queueIds,
                lastPlayedTrackId = prefs[Keys.LAST_TRACK_ID],
                lastPositionMs = prefs[Keys.LAST_POSITION_MS] ?: 0L,
                minPlayDurationSec = prefs[Keys.MIN_PLAY_SEC] ?: 10,
                demoModeEnabled = prefs[Keys.DEMO_MODE] ?: false
            )
        }

    suspend fun setThemeOption(option: ThemeOption) {
        context.pulseDataStore.edit { it[Keys.THEME] = option.name }
    }

    suspend fun setSortOption(option: SortOption) {
        context.pulseDataStore.edit { it[Keys.SORT] = option.name }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.SHUFFLE] = enabled }
    }

    suspend fun setRepeatMode(mode: RepeatModeOption) {
        context.pulseDataStore.edit { it[Keys.REPEAT] = mode.name }
    }

    suspend fun setMinPlayDurationSec(seconds: Int) {
        context.pulseDataStore.edit { it[Keys.MIN_PLAY_SEC] = seconds.coerceIn(5, 60) }
    }

    suspend fun setDemoModeEnabled(enabled: Boolean) {
        context.pulseDataStore.edit { it[Keys.DEMO_MODE] = enabled }
    }

    suspend fun savePlaybackSession(
        queueTrackIds: List<Long>,
        currentTrackId: Long?,
        positionMs: Long
    ) {
        context.pulseDataStore.edit { prefs ->
            prefs[Keys.LAST_QUEUE_IDS] = queueTrackIds.joinToString(",")
            if (currentTrackId != null) {
                prefs[Keys.LAST_TRACK_ID] = currentTrackId
            } else {
                prefs.remove(Keys.LAST_TRACK_ID)
            }
            prefs[Keys.LAST_POSITION_MS] = positionMs.coerceAtLeast(0L)
        }
    }
}
