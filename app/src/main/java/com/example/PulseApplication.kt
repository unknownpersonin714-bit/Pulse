package com.example

import android.app.Application
import android.content.Context
import com.example.data.local.PulseDatabase
import com.example.data.repository.MediaStoreScanner
import com.example.data.repository.MusicRepository
import com.example.player.PlaybackController
import com.example.settings.UserPreferencesRepository

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    val database: PulseDatabase by lazy { PulseDatabase.getInstance(appContext) }
    val mediaStoreScanner: MediaStoreScanner by lazy { MediaStoreScanner(appContext) }
    val preferencesRepository: UserPreferencesRepository by lazy { UserPreferencesRepository(appContext) }
    val musicRepository: MusicRepository by lazy {
        MusicRepository(
            context = appContext,
            dao = database.pulseDao(),
            scanner = mediaStoreScanner
        )
    }
    val playbackController: PlaybackController by lazy {
        PlaybackController(
            context = appContext,
            musicRepository = musicRepository,
            preferencesRepository = preferencesRepository
        )
    }
}

class PulseApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
