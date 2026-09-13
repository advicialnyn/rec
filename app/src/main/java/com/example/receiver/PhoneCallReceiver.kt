package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.data.settings.SettingsManager
import com.example.service.CallRecordService

class PhoneCallReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PhoneCallReceiver"
        private var lastState = TelephonyManager.EXTRA_STATE_IDLE
        private var savedNumber: String? = null
        private var isCallRecording = false
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        if (!number.isNullOrBlank()) {
            savedNumber = number
        }

        val settingsManager = SettingsManager(context.applicationContext)
        val settings = settingsManager.settings.value

        if (!settings.isAutoRecordEnabled || !settings.recordPhoneCalls) {
            return
        }

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                lastState = TelephonyManager.EXTRA_STATE_RINGING
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call answered or outgoing call started
                if (!isCallRecording) {
                    val callerDisplay = savedNumber ?: "Phone Call"
                    Log.i(TAG, "Starting automatic recording for phone call: $callerDisplay")
                    isCallRecording = true
                    CallRecordService.startRecording(
                        context = context.applicationContext,
                        sourceApp = "Phone",
                        callerName = callerDisplay,
                        isAuto = true
                    )
                }
                lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended
                if (isCallRecording) {
                    Log.i(TAG, "Stopping automatic phone call recording")
                    isCallRecording = false
                    CallRecordService.stopRecording(context.applicationContext)
                    savedNumber = null
                }
                lastState = TelephonyManager.EXTRA_STATE_IDLE
            }
        }
    }
}
