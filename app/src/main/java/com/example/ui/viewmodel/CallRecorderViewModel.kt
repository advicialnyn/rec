package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.CallRecorderApplication
import com.example.data.model.RecordingEntity
import com.example.data.settings.AppSettings
import com.example.player.PlaybackState
import com.example.service.AudioRecorderEngine
import com.example.service.CallAccessibilityService
import com.example.service.CallRecordService
import com.example.service.RecordingStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiRecordingStats(
    val totalCount: Int = 0,
    val totalStorageBytes: Long = 0L
)

class CallRecorderViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as CallRecorderApplication
    private val repository = app.recordingRepository
    private val settingsManager = app.settingsManager
    private val playerManager = app.audioPlayerManager

    val settings: StateFlow<AppSettings> = settingsManager.settings

    val playbackState: StateFlow<PlaybackState> = playerManager.playbackState

    // Live status from the recording engine
    private val _engineStatus = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val engineStatus: StateFlow<RecordingStatus> = _engineStatus.asStateFlow()

    private val _selectedFilter = MutableStateFlow("All")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _manualSourceSelection = MutableStateFlow("Manual")
    val manualSourceSelection: StateFlow<String> = _manualSourceSelection.asStateFlow()

    val filteredRecordings: StateFlow<List<RecordingEntity>> = combine(
        repository.allRecordings,
        _selectedFilter,
        _searchQuery
    ) { recordings, filter, query ->
        recordings.filter { rec ->
            val matchesFilter = when (filter) {
                "All" -> true
                "Starred" -> rec.isStarred
                "WhatsApp" -> rec.sourceApp.equals("WhatsApp", ignoreCase = true)
                "IMO" -> rec.sourceApp.equals("IMO", ignoreCase = true)
                "Messenger" -> rec.sourceApp.equals("Messenger", ignoreCase = true)
                "Phone" -> rec.sourceApp.equals("Phone", ignoreCase = true)
                "Manual" -> rec.sourceApp.equals("Manual", ignoreCase = true)
                else -> true
            }
            val matchesQuery = if (query.isBlank()) true else {
                rec.title.contains(query, ignoreCase = true) ||
                        rec.callerNumberOrName.contains(query, ignoreCase = true) ||
                        rec.sourceApp.contains(query, ignoreCase = true) ||
                        rec.note.contains(query, ignoreCase = true)
            }
            matchesFilter && matchesQuery
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recordingStats: StateFlow<UiRecordingStats> = combine(
        repository.recordingsCount,
        repository.totalStorageUsed
    ) { count, bytes ->
        UiRecordingStats(
            totalCount = count,
            totalStorageBytes = bytes ?: 0L
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiRecordingStats())

    init {
        // Observe engine status if active
        viewModelScope.launch {
            while (true) {
                val currentEngine = CallRecordService.engineInstance
                if (currentEngine != null) {
                    currentEngine.recordingStatus.collect {
                        _engineStatus.value = it
                    }
                }
                kotlinx.coroutines.delay(500)
            }
        }
    }

    fun setManualSource(source: String) {
        _manualSourceSelection.value = source
    }

    fun startManualRecording(sourceApp: String = _manualSourceSelection.value, callerName: String = "Both-Sides Voice Call") {
        CallRecordService.startRecording(
            context = getApplication(),
            sourceApp = sourceApp,
            callerName = callerName,
            isAuto = false
        )
    }

    fun stopRecording() {
        CallRecordService.stopRecording(getApplication())
    }

    fun playRecording(recording: RecordingEntity) {
        playerManager.play(recording)
    }

    fun pausePlayback() {
        playerManager.pause()
    }

    fun resumePlayback() {
        playerManager.resume()
    }

    fun stopPlayback() {
        playerManager.stop()
    }

    fun seekPlayback(posMs: Long) {
        playerManager.seekTo(posMs)
    }

    fun seekRelative(offsetMs: Long) {
        playerManager.seekRelative(offsetMs)
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setSpeed(speed)
    }

    fun toggleSpeaker() {
        playerManager.toggleSpeaker()
    }

    fun deleteRecording(id: Long) {
        viewModelScope.launch {
            if (playerManager.playbackState.value.currentRecording?.id == id) {
                playerManager.stop()
            }
            repository.deleteRecording(id)
        }
    }

    fun toggleStarred(id: Long, current: Boolean) {
        viewModelScope.launch {
            repository.toggleStarred(id, current)
        }
    }

    fun renameRecording(id: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameRecording(id, newTitle)
        }
    }

    fun updateNote(id: Long, note: String) {
        viewModelScope.launch {
            repository.updateNote(id, note)
        }
    }

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleAutoRecord(enabled: Boolean) {
        settingsManager.setAutoRecordEnabled(enabled)
    }

    fun toggleAppRecording(appKey: String, enabled: Boolean) {
        when (appKey) {
            "Phone" -> settingsManager.setRecordPhoneCalls(enabled)
            "WhatsApp" -> settingsManager.setRecordWhatsApp(enabled)
            "IMO" -> settingsManager.setRecordImo(enabled)
            "Messenger" -> settingsManager.setRecordMessenger(enabled)
            "Other" -> settingsManager.setRecordOtherVoip(enabled)
        }
    }

    fun setAudioSource(source: String) {
        settingsManager.setAudioSource(source)
    }

    fun setAudioFormat(format: String) {
        settingsManager.setAudioFormat(format)
    }

    fun setBoostVolume(boost: Boolean) {
        settingsManager.setBoostVolume(boost)
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val context: Context = getApplication()
        val expectedServiceName = "${context.packageName}/${CallAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            val componentName = colonSplitter.next()
            if (componentName.equals(expectedServiceName, ignoreCase = true)) {
                return true
            }
        }
        return CallAccessibilityService.isServiceRunning.value
    }
}
