package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.settings.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CallAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CallAccessibility"

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        // Package mappings
        const val PKG_WHATSAPP = "com.whatsapp"
        const val PKG_WHATSAPP_BUS = "com.whatsapp.w4b"
        const val PKG_IMO = "com.imo.android.imoim"
        const val PKG_IMO_BETA = "com.imo.android.imoimbeta"
        const val PKG_MESSENGER = "com.facebook.orca"
        const val PKG_MESSENGER_LITE = "com.facebook.mlite"
        const val PKG_TELEGRAM = "org.telegram.messenger"
        const val PKG_VIBER = "com.viber.voip"
    }

    private lateinit var settingsManager: SettingsManager
    private var isVoipCallActive = false
    private var activeVoipPackage: String? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        _isServiceRunning.value = true
        settingsManager = SettingsManager(applicationContext)
        Log.i(TAG, "CallAccessibilityService connected and monitoring VoIP calls")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val packageName = event.packageName?.toString() ?: return

        val settings = settingsManager.settings.value
        if (!settings.isAutoRecordEnabled) return

        when {
            (packageName == PKG_WHATSAPP || packageName == PKG_WHATSAPP_BUS) && settings.recordWhatsApp -> {
                inspectCallUi(packageName, "WhatsApp", event)
            }
            (packageName == PKG_IMO || packageName == PKG_IMO_BETA) && settings.recordImo -> {
                inspectCallUi(packageName, "IMO", event)
            }
            (packageName == PKG_MESSENGER || packageName == PKG_MESSENGER_LITE) && settings.recordMessenger -> {
                inspectCallUi(packageName, "Messenger", event)
            }
            (packageName == PKG_TELEGRAM || packageName == PKG_VIBER) && settings.recordOtherVoip -> {
                val appName = if (packageName == PKG_TELEGRAM) "Telegram" else "Viber"
                inspectCallUi(packageName, appName, event)
            }
        }
    }

    private fun inspectCallUi(pkg: String, appName: String, event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        val textList = mutableListOf<String>()
        extractTextFromNode(rootNode, textList)

        val fullText = textList.joinToString(" ").lowercase()

        val hasCallIndicator = fullText.contains("calling") ||
                fullText.contains("ringing") ||
                fullText.contains("incoming call") ||
                fullText.contains("ongoing call") ||
                fullText.contains("voice call") ||
                fullText.contains("video call") ||
                fullText.contains("in call") ||
                fullText.contains("call duration") ||
                hasTimeDurationPattern(fullText)

        val hasCallEndIndicator = fullText.contains("call ended") ||
                fullText.contains("call declined") ||
                fullText.contains("disconnected")

        if (hasCallEndIndicator && isVoipCallActive && activeVoipPackage == pkg) {
            handleCallEnded()
            return
        }

        if (hasCallIndicator && !isVoipCallActive) {
            // Find possible caller name: first meaningful string
            val callerCandidate = textList.firstOrNull { candidate ->
                val clean = candidate.trim()
                clean.isNotEmpty() &&
                        !clean.equals("calling", ignoreCase = true) &&
                        !clean.equals("ringing", ignoreCase = true) &&
                        !clean.equals("incoming call", ignoreCase = true) &&
                        !clean.equals("whatsapp call", ignoreCase = true) &&
                        !clean.equals("imo call", ignoreCase = true) &&
                        clean.length in 2..40
            } ?: "$appName User"

            isVoipCallActive = true
            activeVoipPackage = pkg

            CallRecordService.startRecording(
                context = applicationContext,
                sourceApp = appName,
                callerName = callerCandidate,
                isAuto = true
            )
        }
    }

    private fun hasTimeDurationPattern(text: String): Boolean {
        // Matches mm:ss like 00:05, 01:23, 10:45
        val regex = Regex("\\b\\d{1,2}:\\d{2}\\b")
        return regex.containsMatchIn(text)
    }

    private fun extractTextFromNode(node: AccessibilityNodeInfo?, list: MutableList<String>) {
        if (node == null) return
        val text = node.text?.toString()
        val desc = node.contentDescription?.toString()
        if (!text.isNullOrBlank()) list.add(text)
        if (!desc.isNullOrBlank() && desc != text) list.add(desc)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            extractTextFromNode(child, list)
        }
    }

    private fun handleCallEnded() {
        if (isVoipCallActive) {
            isVoipCallActive = false
            activeVoipPackage = null
            CallRecordService.stopRecording(applicationContext)
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "CallAccessibilityService interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        _isServiceRunning.value = false
        if (isVoipCallActive) {
            handleCallEnded()
        }
    }
}
