package com.example.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppSettings(
    val isAutoRecordEnabled: Boolean = true,
    val recordPhoneCalls: Boolean = true,
    val recordWhatsApp: Boolean = true,
    val recordImo: Boolean = true,
    val recordMessenger: Boolean = true,
    val recordOtherVoip: Boolean = true,
    val audioSource: String = "VOICE_COMMUNICATION", // "VOICE_COMMUNICATION", "MIC", "VOICE_RECOGNITION"
    val audioFormat: String = "M4A", // "M4A", "AAC", "WAV"
    val boostVolume: Boolean = true,
    val noAnnouncementMode: Boolean = true,
    val storageDirectory: String = "Internal Storage"
)

class SettingsManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("call_recorder_settings", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private fun loadSettings(): AppSettings {
        return AppSettings(
            isAutoRecordEnabled = prefs.getBoolean("isAutoRecordEnabled", true),
            recordPhoneCalls = prefs.getBoolean("recordPhoneCalls", true),
            recordWhatsApp = prefs.getBoolean("recordWhatsApp", true),
            recordImo = prefs.getBoolean("recordImo", true),
            recordMessenger = prefs.getBoolean("recordMessenger", true),
            recordOtherVoip = prefs.getBoolean("recordOtherVoip", true),
            audioSource = prefs.getString("audioSource", "VOICE_COMMUNICATION") ?: "VOICE_COMMUNICATION",
            audioFormat = prefs.getString("audioFormat", "M4A") ?: "M4A",
            boostVolume = prefs.getBoolean("boostVolume", true),
            noAnnouncementMode = prefs.getBoolean("noAnnouncementMode", true),
            storageDirectory = prefs.getString("storageDirectory", "Internal Storage") ?: "Internal Storage"
        )
    }

    fun setAutoRecordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("isAutoRecordEnabled", enabled).apply()
        _settings.value = _settings.value.copy(isAutoRecordEnabled = enabled)
    }

    fun setRecordPhoneCalls(enabled: Boolean) {
        prefs.edit().putBoolean("recordPhoneCalls", enabled).apply()
        _settings.value = _settings.value.copy(recordPhoneCalls = enabled)
    }

    fun setRecordWhatsApp(enabled: Boolean) {
        prefs.edit().putBoolean("recordWhatsApp", enabled).apply()
        _settings.value = _settings.value.copy(recordWhatsApp = enabled)
    }

    fun setRecordImo(enabled: Boolean) {
        prefs.edit().putBoolean("recordImo", enabled).apply()
        _settings.value = _settings.value.copy(recordImo = enabled)
    }

    fun setRecordMessenger(enabled: Boolean) {
        prefs.edit().putBoolean("recordMessenger", enabled).apply()
        _settings.value = _settings.value.copy(recordMessenger = enabled)
    }

    fun setRecordOtherVoip(enabled: Boolean) {
        prefs.edit().putBoolean("recordOtherVoip", enabled).apply()
        _settings.value = _settings.value.copy(recordOtherVoip = enabled)
    }

    fun setAudioSource(source: String) {
        prefs.edit().putString("audioSource", source).apply()
        _settings.value = _settings.value.copy(audioSource = source)
    }

    fun setAudioFormat(format: String) {
        prefs.edit().putString("audioFormat", format).apply()
        _settings.value = _settings.value.copy(audioFormat = format)
    }

    fun setBoostVolume(boost: Boolean) {
        prefs.edit().putBoolean("boostVolume", boost).apply()
        _settings.value = _settings.value.copy(boostVolume = boost)
    }
}
