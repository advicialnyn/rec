package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
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

data class PlaybackState(
    val currentRecording: RecordingEntity? = null,
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val currentPositionMs: Long = 0L,
    val totalDurationMs: Long = 0L,
    val speed: Float = 1.0f,
    val isSpeaker: Boolean = true
)

class AudioPlayerManager(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(Dispatchers.Main)
    private var progressJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    fun play(recording: RecordingEntity) {
        val file = File(recording.filePath)
        if (!file.exists()) {
            Log.e("AudioPlayerManager", "File not found: ${recording.filePath}")
            return
        }

        // If same recording is paused, resume
        if (_playbackState.value.currentRecording?.id == recording.id && _playbackState.value.isPaused) {
            resume()
            return
        }

        stop()

        try {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(file.absolutePath)
                prepare()
                val targetSpeed = _playbackState.value.speed
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && targetSpeed != 1.0f) {
                    playbackParams = playbackParams.setSpeed(targetSpeed)
                }
                start()
            }

            player.setOnCompletionListener {
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = false,
                    currentPositionMs = 0L
                )
                stopProgressUpdates()
            }

            mediaPlayer = player
            _playbackState.value = _playbackState.value.copy(
                currentRecording = recording,
                isPlaying = true,
                isPaused = false,
                currentPositionMs = 0L,
                totalDurationMs = player.duration.toLong()
            )

            startProgressUpdates()
        } catch (e: Exception) {
            Log.e("AudioPlayerManager", "Error playing recording", e)
        }
    }

    fun pause() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                _playbackState.value = _playbackState.value.copy(
                    isPlaying = false,
                    isPaused = true
                )
            }
        }
    }

    fun resume() {
        mediaPlayer?.let {
            it.start()
            _playbackState.value = _playbackState.value.copy(
                isPlaying = true,
                isPaused = false
            )
            startProgressUpdates()
        }
    }

    fun stop() {
        stopProgressUpdates()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        mediaPlayer = null
        _playbackState.value = _playbackState.value.copy(
            isPlaying = false,
            isPaused = false,
            currentPositionMs = 0L
        )
    }

    fun seekTo(positionMs: Long) {
        mediaPlayer?.let {
            val target = positionMs.coerceIn(0L, it.duration.toLong())
            it.seekTo(target.toInt())
            _playbackState.value = _playbackState.value.copy(currentPositionMs = target)
        }
    }

    fun seekRelative(offsetMs: Long) {
        mediaPlayer?.let {
            val current = it.currentPosition.toLong()
            val target = (current + offsetMs).coerceIn(0L, it.duration.toLong())
            it.seekTo(target.toInt())
            _playbackState.value = _playbackState.value.copy(currentPositionMs = target)
        }
    }

    fun setSpeed(speed: Float) {
        _playbackState.value = _playbackState.value.copy(speed = speed)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.let {
                try {
                    it.playbackParams = it.playbackParams.setSpeed(speed)
                } catch (e: Exception) {
                    Log.e("AudioPlayerManager", "Error setting speed", e)
                }
            }
        }
    }

    fun toggleSpeaker() {
        val newSpeaker = !_playbackState.value.isSpeaker
        try {
            audioManager.isSpeakerphoneOn = newSpeaker
            _playbackState.value = _playbackState.value.copy(isSpeaker = newSpeaker)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressJob = scope.launch {
            while (isActive) {
                mediaPlayer?.let {
                    if (it.isPlaying) {
                        _playbackState.value = _playbackState.value.copy(
                            currentPositionMs = it.currentPosition.toLong()
                        )
                    }
                }
                delay(200)
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }
}
