package com.example.service

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.data.model.RecordingEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class RecordingStatus {
    object Idle : RecordingStatus()
    data class Recording(
        val sourceApp: String,
        val callerName: String,
        val startTimeMs: Long,
        val durationMs: Long,
        val amplitude: Float, // 0.0f .. 1.0f
        val isAuto: Boolean,
        val filePath: String
    ) : RecordingStatus()
}

class AudioRecorderEngine(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimeMs: Long = 0L
    private var currentSourceApp: String = "Manual"
    private var currentCallerName: String = "Voice Call"
    private var isAutoRecording: Boolean = false

    private val _recordingStatus = MutableStateFlow<RecordingStatus>(RecordingStatus.Idle)
    val recordingStatus: StateFlow<RecordingStatus> = _recordingStatus.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default)
    private var meterJob: Job? = null

    val isRecording: Boolean
        get() = _recordingStatus.value is RecordingStatus.Recording

    /**
     * Start recording both sides of a call or manual voice input.
     * Saved into app internal storage with no announcements.
     */
    @Synchronized
    fun startRecording(
        sourceApp: String = "Manual",
        callerName: String = "Voice Call",
        isAuto: Boolean = false,
        preferredSource: String = "VOICE_COMMUNICATION"
    ): Boolean {
        if (isRecording) {
            Log.w("AudioRecorderEngine", "Already recording, ignoring start request.")
            return false
        }

        val internalDir = File(context.filesDir, "recordings").apply {
            if (!exists()) mkdirs()
        }

        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val cleanCaller = callerName.replace("[^a-zA-Z0-9_ -]".toRegex(), "_").take(20)
        val fileName = "${sourceApp}_${cleanCaller}_$timestampStr.m4a"
        val outputFile = File(internalDir, fileName)

        val recorder = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
        } catch (e: Exception) {
            Log.e("AudioRecorderEngine", "Failed to instantiate MediaRecorder", e)
            return false
        }

        val audioSource = when (preferredSource) {
            "VOICE_COMMUNICATION" -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
            "MIC" -> MediaRecorder.AudioSource.MIC
            "VOICE_RECOGNITION" -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.VOICE_COMMUNICATION
        }

        var initialized = false
        // Try preferred audio source first
        try {
            recorder.setAudioSource(audioSource)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setAudioEncodingBitRate(128000)
            recorder.setAudioSamplingRate(44100)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.prepare()
            recorder.start()
            initialized = true
        } catch (e: Exception) {
            Log.w("AudioRecorderEngine", "Failed with source $audioSource, attempting fallback to MIC", e)
            recorder.reset()
        }

        // Fallback to MIC if VOICE_COMMUNICATION wasn't allowed on device/HAL
        if (!initialized) {
            try {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128000)
                recorder.setAudioSamplingRate(44100)
                recorder.setOutputFile(outputFile.absolutePath)
                recorder.prepare()
                recorder.start()
                initialized = true
            } catch (fallbackError: Exception) {
                Log.e("AudioRecorderEngine", "Fallback to MIC also failed", fallbackError)
                try {
                    recorder.release()
                } catch (ignored: Exception) {}
                return false
            }
        }

        mediaRecorder = recorder
        currentFile = outputFile
        startTimeMs = System.currentTimeMillis()
        currentSourceApp = sourceApp
        currentCallerName = callerName
        isAutoRecording = isAuto

        startAmplitudeMeter()
        return true
    }

    private fun startAmplitudeMeter() {
        meterJob?.cancel()
        meterJob = scope.launch {
            while (isActive) {
                val now = System.currentTimeMillis()
                val duration = now - startTimeMs
                val rawAmp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (e: Exception) {
                    0
                }
                // Normalize 0..32767 to 0.0f..1.0f
                val normalizedAmp = (rawAmp / 32767f).coerceIn(0.05f, 1.0f)

                _recordingStatus.value = RecordingStatus.Recording(
                    sourceApp = currentSourceApp,
                    callerName = currentCallerName,
                    startTimeMs = startTimeMs,
                    durationMs = duration,
                    amplitude = normalizedAmp,
                    isAuto = isAutoRecording,
                    filePath = currentFile?.absolutePath ?: ""
                )
                delay(100)
            }
        }
    }

    /**
     * Stop recording and return the saved recording metadata entity.
     */
    @Synchronized
    fun stopRecording(): RecordingEntity? {
        if (!isRecording) return null

        meterJob?.cancel()
        meterJob = null

        var finalDuration = System.currentTimeMillis() - startTimeMs
        if (finalDuration < 500) {
            finalDuration = 500
        }

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("AudioRecorderEngine", "Error stopping MediaRecorder", e)
        } finally {
            try {
                mediaRecorder?.release()
            } catch (e: Exception) {}
            mediaRecorder = null
        }

        _recordingStatus.value = RecordingStatus.Idle

        val file = currentFile
        if (file == null || !file.exists() || file.length() <= 0) {
            Log.w("AudioRecorderEngine", "Recorded file is empty or missing")
            return null
        }

        val readableTitle = if (currentCallerName.isNotBlank() && currentCallerName != "Voice Call") {
            "$currentSourceApp - $currentCallerName"
        } else {
            "$currentSourceApp Call Record"
        }

        return RecordingEntity(
            title = readableTitle,
            sourceApp = currentSourceApp,
            callerNumberOrName = currentCallerName,
            filePath = file.absolutePath,
            fileName = file.name,
            timestamp = startTimeMs,
            durationMs = finalDuration,
            fileSizeBytes = file.length(),
            isAutoRecorded = isAutoRecording
        )
    }
}
