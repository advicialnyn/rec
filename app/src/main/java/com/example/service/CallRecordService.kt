package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.data.db.AppDatabase
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CallRecordService : Service() {

    companion object {
        const val CHANNEL_ID = "call_recording_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_RECORDING = "com.example.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.example.action.STOP_RECORDING"

        const val EXTRA_SOURCE_APP = "extra_source_app"
        const val EXTRA_CALLER_NAME = "extra_caller_name"
        const val EXTRA_IS_AUTO = "extra_is_auto"

        @Volatile
        var engineInstance: AudioRecorderEngine? = null

        fun startRecording(
            context: Context,
            sourceApp: String,
            callerName: String,
            isAuto: Boolean
        ) {
            val intent = Intent(context, CallRecordService::class.java).apply {
                action = ACTION_START_RECORDING
                putExtra(EXTRA_SOURCE_APP, sourceApp)
                putExtra(EXTRA_CALLER_NAME, callerName)
                putExtra(EXTRA_IS_AUTO, isAuto)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopRecording(context: Context) {
            val intent = Intent(context, CallRecordService::class.java).apply {
                action = ACTION_STOP_RECORDING
            }
            context.startService(intent)
        }
    }

    private lateinit var recorderEngine: AudioRecorderEngine
    private val serviceScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        recorderEngine = AudioRecorderEngine(applicationContext)
        engineInstance = recorderEngine
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_RECORDING -> {
                val sourceApp = intent.getStringExtra(EXTRA_SOURCE_APP) ?: "Manual"
                val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "Voice Call"
                val isAuto = intent.getBooleanExtra(EXTRA_IS_AUTO, false)

                val settings = SettingsManager(applicationContext).settings.value

                val notification = buildForegroundNotification(sourceApp, callerName, isAuto)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                val success = recorderEngine.startRecording(
                    sourceApp = sourceApp,
                    callerName = callerName,
                    isAuto = isAuto,
                    preferredSource = settings.audioSource
                )

                if (success) {
                    vibrateFeedback()
                } else {
                    stopSelf()
                }
            }

            ACTION_STOP_RECORDING -> {
                val recording = recorderEngine.stopRecording()
                if (recording != null) {
                    serviceScope.launch {
                        try {
                            AppDatabase.getInstance(applicationContext)
                                .recordingDao()
                                .insertRecording(recording)
                        } catch (e: Exception) {
                            Log.e("CallRecordService", "Failed to save recording to DB", e)
                        }
                    }
                }
                vibrateFeedback()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun vibrateFeedback() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator.vibrate(
                    VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(80)
            }
        } catch (e: Exception) {
            // Ignore if vibration fails
        }
    }

    private fun buildForegroundNotification(
        sourceApp: String,
        callerName: String,
        isAuto: Boolean
    ): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, CallRecordService::class.java).apply {
            action = ACTION_STOP_RECORDING
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val autoBadge = if (isAuto) "[AUTO] " else "[MANUAL] "
        val title = "$autoBadge$sourceApp Call Recording"
        val text = "Recording both sides ($callerName) • No announcement"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_media_pause, "Stop & Save", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording status and controls"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (recorderEngine.isRecording) {
            recorderEngine.stopRecording()
        }
    }
}
