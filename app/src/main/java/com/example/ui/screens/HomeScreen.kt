package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.RecordingEntity
import com.example.data.settings.AppSettings
import com.example.player.PlaybackState
import com.example.service.RecordingStatus
import com.example.ui.components.RecordingItemCard
import com.example.ui.components.WaveformVisualizer
import com.example.ui.components.formatDuration
import com.example.ui.components.formatFileSize
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.CyanGlow
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.NeonRed
import com.example.ui.theme.NeonRedGlow
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiRecordingStats

@Composable
fun HomeScreen(
    recordingStatus: RecordingStatus,
    settings: AppSettings,
    stats: UiRecordingStats,
    recentRecordings: List<RecordingEntity>,
    playbackState: PlaybackState,
    selectedManualSource: String,
    isAccessibilityEnabled: Boolean,
    onSelectManualSource: (String) -> Unit,
    onStartRecording: (sourceApp: String) -> Unit,
    onStopRecording: () -> Unit,
    onToggleAutoRecord: (Boolean) -> Unit,
    onPlayRecording: (RecordingEntity) -> Unit,
    onDeleteRecording: (Long) -> Unit,
    onToggleStar: (Long, Boolean) -> Unit,
    onRenameRecording: (RecordingEntity) -> Unit,
    onNavigateToRecordings: () -> Unit,
    onNavigateToAutoConfig: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRecording = recordingStatus is RecordingStatus.Recording

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(DarkSurface),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Header: Storage Info & Auto Mode Switch
        item {
            HeaderSection(
                settings = settings,
                stats = stats,
                onToggleAutoRecord = onToggleAutoRecord
            )
        }

        // 2. Central Big Glowing Recording Button / Active Recording Studio
        item {
            RecordingStudioCard(
                recordingStatus = recordingStatus,
                selectedManualSource = selectedManualSource,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onSelectManualSource = onSelectManualSource
            )
        }

        // 3. Zero Announcement Guarantee Banner
        item {
            ZeroAnnouncementBanner()
        }

        // 4. Automatic Call Detection Status Card (VoIP + Phone)
        item {
            AutoDetectionStatusCard(
                isAccessibilityEnabled = isAccessibilityEnabled,
                settings = settings,
                onConfigureClick = onNavigateToAutoConfig,
                onOpenAccessibilitySettings = {
                    try {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            )
        }

        // 5. Recent Recordings Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Recordings",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                TextButton(onClick = onNavigateToRecordings) {
                    Text("View All (${stats.totalCount})", color = CyanAccent)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = null,
                        tint = CyanAccent,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 6. Recent Recordings List Items
        if (recentRecordings.isEmpty()) {
            item {
                EmptyRecentCard(onStartManual = { onStartRecording(selectedManualSource) })
            }
        } else {
            items(recentRecordings.take(3), key = { it.id }) { rec ->
                val isThisPlaying = playbackState.currentRecording?.id == rec.id && playbackState.isPlaying
                RecordingItemCard(
                    recording = rec,
                    isPlaying = isThisPlaying,
                    onPlayToggle = { onPlayRecording(rec) },
                    onDelete = { onDeleteRecording(rec.id) },
                    onToggleStar = { onToggleStar(rec.id, rec.isStarred) },
                    onRename = { onRenameRecording(rec) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun HeaderSection(
    settings: AppSettings,
    stats: UiRecordingStats,
    onToggleAutoRecord: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (settings.isAutoRecordEnabled) EmeraldGreen else TextSecondary)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (settings.isAutoRecordEnabled) "Automatic Recording ON" else "Manual Only Mode",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${stats.totalCount} saved calls • ${formatFileSize(stats.totalStorageBytes)} used",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Switch(
                checked = settings.isAutoRecordEnabled,
                onCheckedChange = onToggleAutoRecord,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Black,
                    checkedTrackColor = EmeraldGreen,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = DarkSurfaceVariant
                ),
                modifier = Modifier.testTag("auto_record_toggle")
            )
        }
    }
}

@Composable
private fun RecordingStudioCard(
    recordingStatus: RecordingStatus,
    selectedManualSource: String,
    onStartRecording: (String) -> Unit,
    onStopRecording: () -> Unit,
    onSelectManualSource: (String) -> Unit
) {
    val isRecording = recordingStatus is RecordingStatus.Recording
    val recData = recordingStatus as? RecordingStatus.Recording

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecording) Color(0xFF1B1824) else DarkSurfaceElevated
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isRecording) NeonRed.copy(alpha = 0.5f) else DarkBorder
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Source Selector Pills (when Idle)
            if (!isRecording) {
                Text(
                    text = "SELECT CALL APP / SOURCE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                val sources = listOf("Manual", "WhatsApp", "IMO", "Messenger", "Phone")
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(sources) { source ->
                        val isSelected = selectedManualSource.equals(source, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) CyanAccent else DarkSurfaceVariant
                                )
                                .clickable { onSelectManualSource(source) }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = source,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
            }

            // Central Glowing Button
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(160.dp)
                    .padding(8.dp)
            ) {
                // Outer Pulse Ring when recording
                if (isRecording) {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(NeonRedGlow)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(148.dp)
                            .clip(CircleShape)
                            .background(CyanGlow)
                    )
                }

                // Inner Main Action Button
                Box(
                    modifier = Modifier
                        .size(118.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                if (isRecording) listOf(Color(0xFFFF2A42), Color(0xFFC00C22))
                                else listOf(Color(0xFF0F263C), Color(0xFF163757))
                            )
                        )
                        .border(
                            2.dp,
                            if (isRecording) NeonRed else CyanAccent,
                            CircleShape
                        )
                        .clickable {
                            if (isRecording) {
                                onStopRecording()
                            } else {
                                onStartRecording(selectedManualSource)
                            }
                        }
                        .testTag("main_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                            contentDescription = if (isRecording) "Stop" else "Record",
                            tint = if (isRecording) Color.White else CyanAccent,
                            modifier = Modifier.size(38.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isRecording) "STOP & SAVE" else "START RECORD",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            color = if (isRecording) Color.White else CyanAccent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Status details and waveform
            if (isRecording && recData != null) {
                Text(
                    text = formatDuration(recData.durationMs),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = NeonRed
                    )
                )

                Text(
                    text = "Recording Both Sides • ${recData.sourceApp} (${recData.callerName})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                WaveformVisualizer(
                    isRecording = true,
                    amplitude = recData.amplitude,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            } else {
                Text(
                    text = "Tap to Record Call (${selectedManualSource})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = TextPrimary
                )
                Text(
                    text = "Captures both incoming & outgoing audio clearly",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
fun ZeroAnnouncementBanner() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF131F2E),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1C3A5A))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Shield,
                contentDescription = "Zero Announcement",
                tint = CyanAccent,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "No Announcement Mode Active",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Text(
                    text = "Records both sides silently without warning tones or robot voice dialer announcements.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
private fun AutoDetectionStatusCard(
    isAccessibilityEnabled: Boolean,
    settings: AppSettings,
    onConfigureClick: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "VoIP & Call Detection Readiness",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                TextButton(onClick = onConfigureClick) {
                    Text("Settings", color = CyanAccent, fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Accessibility Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAccessibilityEnabled) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isAccessibilityEnabled) EmeraldGreen else Color(0xFFF59E0B),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "VoIP Call Accessibility Service",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Text(
                        text = if (isAccessibilityEnabled) "Active • Ready for WhatsApp, IMO, Messenger" else "Needed for automatic WhatsApp, IMO, Messenger detection",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                if (!isAccessibilityEnabled) {
                    Button(
                        onClick = onOpenAccessibilitySettings,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Phone State Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "Cellular Phone State Monitor",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                        color = TextPrimary
                    )
                    Text(
                        text = if (settings.recordPhoneCalls) "Enabled for incoming & outgoing calls" else "Disabled in settings",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyRecentCard(onStartManual: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = DarkSurfaceElevated,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No Calls Recorded Yet",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = "Calls will appear here automatically when received, or tap below to record manually.",
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )
            Button(
                onClick = onStartManual,
                colors = ButtonDefaults.buttonColors(containerColor = CyanAccent, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Start First Recording", fontWeight = FontWeight.Bold)
            }
        }
    }
}
