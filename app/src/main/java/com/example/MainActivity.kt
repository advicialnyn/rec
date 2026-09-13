package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.service.RecordingStatus
import com.example.ui.components.BottomPlayerBar
import com.example.ui.screens.AutoVoipScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.RecordingsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.EmeraldGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.NeonRed
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.CallRecorderViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: CallRecorderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: CallRecorderViewModel) {
    val context = LocalContext.current

    // Observe StateFlows
    val recordingStatus by viewModel.engineStatus.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val recordings by viewModel.filteredRecordings.collectAsStateWithLifecycle()
    val stats by viewModel.recordingStats.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedManualSource by viewModel.manualSourceSelection.collectAsStateWithLifecycle()

    var currentTab by remember { mutableIntStateOf(0) }
    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Permission launcher for initial audio & phone permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasAudioPermission = results[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val isAccessibilityEnabled = remember(currentTab) {
        viewModel.isAccessibilityServiceEnabled()
    }

    val isCurrentlyRecording = recordingStatus is RecordingStatus.Recording

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkBg,
        topBar = {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                CenterAlignedTopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (isCurrentlyRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(NeonRed)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Text(
                                text = if (isCurrentlyRecording) "RECORDING IN PROGRESS" else "CALL RECORDER",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp
                                ),
                                color = if (isCurrentlyRecording) NeonRed else TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = DarkBg
                    )
                )

                // Subtitle Badge Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF132233))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "BOTH SIDES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = CyanAccent
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF162520))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "NO ANNOUNCEMENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldGreen
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Text(
                        text = "Internal Storage",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        },
        bottomBar = {
            Column {
                // Floating audio playback bar if player has active item
                if (playbackState.currentRecording != null) {
                    BottomPlayerBar(
                        playbackState = playbackState,
                        onPlayPause = {
                            if (playbackState.isPlaying) viewModel.pausePlayback()
                            else viewModel.resumePlayback()
                        },
                        onSeekTo = { viewModel.seekPlayback(it) },
                        onSeekRelative = { viewModel.seekRelative(it) },
                        onSpeedChange = { viewModel.setPlaybackSpeed(it) },
                        onToggleSpeaker = { viewModel.toggleSpeaker() },
                        onClose = { viewModel.stopPlayback() }
                    )
                }

                NavigationBar(
                    containerColor = DarkSurfaceElevated,
                    contentColor = TextPrimary
                ) {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 0) Icons.Default.Mic else Icons.Outlined.Mic,
                                contentDescription = "Recorder"
                            )
                        },
                        label = { Text("Recorder", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_recorder")
                    )

                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 1) Icons.Default.Headphones else Icons.Outlined.Headphones,
                                contentDescription = "Recordings"
                            )
                        },
                        label = { Text("Recordings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_recordings")
                    )

                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 2) Icons.Default.Phone else Icons.Outlined.Phone,
                                contentDescription = "Auto VoIP"
                            )
                        },
                        label = { Text("Auto VoIP", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_auto_voip")
                    )

                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = {
                            Icon(
                                imageVector = if (currentTab == 3) Icons.Default.Settings else Icons.Outlined.Settings,
                                contentDescription = "Settings"
                            )
                        },
                        label = { Text("Settings", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        ),
                        modifier = Modifier.testTag("nav_tab_settings")
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Check if microphone permission is denied
            if (!hasAudioPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2B1D15)),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Microphone Permission Required", fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Audio recording is necessary to capture both sides of calls.", fontSize = 12.sp, color = TextSecondary)
                        }
                        Button(
                            onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B), contentColor = Color.Black)
                        ) {
                            Text("Allow", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            when (currentTab) {
                0 -> HomeScreen(
                    recordingStatus = recordingStatus,
                    settings = settings,
                    stats = stats,
                    recentRecordings = recordings,
                    playbackState = playbackState,
                    selectedManualSource = selectedManualSource,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onSelectManualSource = { viewModel.setManualSource(it) },
                    onStartRecording = { viewModel.startManualRecording(it) },
                    onStopRecording = { viewModel.stopRecording() },
                    onToggleAutoRecord = { viewModel.toggleAutoRecord(it) },
                    onPlayRecording = { viewModel.playRecording(it) },
                    onDeleteRecording = { viewModel.deleteRecording(it) },
                    onToggleStar = { id, current -> viewModel.toggleStarred(id, current) },
                    onRenameRecording = { rec -> viewModel.renameRecording(rec.id, rec.title) },
                    onNavigateToRecordings = { currentTab = 1 },
                    onNavigateToAutoConfig = { currentTab = 2 }
                )

                1 -> RecordingsScreen(
                    recordings = recordings,
                    stats = stats,
                    playbackState = playbackState,
                    selectedFilter = selectedFilter,
                    searchQuery = searchQuery,
                    onFilterSelect = { viewModel.setFilter(it) },
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onPlayRecording = { viewModel.playRecording(it) },
                    onDeleteRecording = { viewModel.deleteRecording(it) },
                    onToggleStar = { id, current -> viewModel.toggleStarred(id, current) },
                    onRenameRecording = { id, title, note ->
                        viewModel.renameRecording(id, title)
                        if (note.isNotBlank()) viewModel.updateNote(id, note)
                    }
                )

                2 -> AutoVoipScreen(
                    settings = settings,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    onToggleAutoRecord = { viewModel.toggleAutoRecord(it) },
                    onToggleAppRecording = { app, enabled -> viewModel.toggleAppRecording(app, enabled) }
                )

                3 -> SettingsScreen(
                    settings = settings,
                    onSetAudioSource = { viewModel.setAudioSource(it) },
                    onSetAudioFormat = { viewModel.setAudioFormat(it) },
                    onSetBoostVolume = { viewModel.setBoostVolume(it) }
                )
            }
        }
    }
}
