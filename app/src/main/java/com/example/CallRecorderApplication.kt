package com.example

import android.app.Application
import com.example.data.db.AppDatabase
import com.example.data.repository.RecordingRepository
import com.example.data.settings.SettingsManager
import com.example.player.AudioPlayerManager

class CallRecorderApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var recordingRepository: RecordingRepository
        private set

    lateinit var settingsManager: SettingsManager
        private set

    lateinit var audioPlayerManager: AudioPlayerManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.getInstance(this)
        recordingRepository = RecordingRepository(database.recordingDao(), this)
        settingsManager = SettingsManager(this)
        audioPlayerManager = AudioPlayerManager(this)
    }

    companion object {
        lateinit var instance: CallRecorderApplication
            private set
    }
}
