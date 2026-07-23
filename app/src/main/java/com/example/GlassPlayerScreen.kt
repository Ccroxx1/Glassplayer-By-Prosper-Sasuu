package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import coil.size.Size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale

// Vibrant glassmorphic colors — dynamically tinted from album art (see GlassPalette.kt)
val GlassCyan get() = GlassDynamic.cyan
val GlassMagenta get() = GlassDynamic.magenta
val GlassPurple get() = GlassDynamic.purple
val GlassBg = Color(0x2211122B)
val GlassBorderWhite = Color(0x3EFFFFFF)
val GlassTextGlow = Color(0x9E00F0FF)

/** Notifications (lock-screen mini player) are required on Android 10+. */
fun areNotificationsEnabled(context: android.content.Context): Boolean {
    val nm = context.getSystemService(NotificationManager::class.java) ?: return true
    return nm.areNotificationsEnabled()
}

fun openAppNotificationSettings(context: android.content.Context) {
    val intent = Intent().apply {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> {
                action = Settings.ACTION_APP_NOTIFICATION_SETTINGS
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
            else -> {
                action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                data = Uri.fromParts("package", context.packageName, null)
            }
        }
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
}

// Removed EnsureLockScreenNotifications to avoid concurrent ActivityResultLaunchers

@Composable
fun GlassPlayerApp(viewModel: AudioViewModel) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf("Browse") }
    val currentTrackForTheme by viewModel.currentTrack.collectAsState()

    // Dynamic glass accents from the current song's album art
    LaunchedEffect(currentTrackForTheme?.id, currentTrackForTheme?.albumArtUri) {
        extractGlassAccentsFromArt(context, currentTrackForTheme?.albumArtUri)
    }

    // Lock-screen mini player requires notifications on Android 10+
    // EnsureLockScreenNotifications()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070814)) // Deep space base
    ) {
        val isTablet = maxWidth > 680.dp

        // 1. Dynamic glowing accent orbs drifting behind glass panels
        val infiniteTransition = rememberInfiniteTransition(label = "drifting_orbs")
        val driftX1 by infiniteTransition.animateFloat(
            initialValue = -100f, targetValue = 400f,
            animationSpec = infiniteRepeatable(tween(25000, easing = LinearEasing)), label = "driftX1"
        )
        val driftY1 by infiniteTransition.animateFloat(
            initialValue = 200f, targetValue = -200f,
            animationSpec = infiniteRepeatable(tween(30000, easing = LinearEasing)), label = "driftY1"
        )
        val driftX2 by infiniteTransition.animateFloat(
            initialValue = 500f, targetValue = -100f,
            animationSpec = infiniteRepeatable(tween(28000, easing = LinearEasing)), label = "driftX2"
        )
        val driftY2 by infiniteTransition.animateFloat(
            initialValue = -100f, targetValue = 500f,
            animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing)), label = "driftY2"
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    // Draw a gorgeous ambient mesh layout of neon glowing lights
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlassMagenta.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(driftX1 + size.width / 2, driftY1 + size.height / 2),
                            radius = 400.dp.toPx()
                        ),
                        center = Offset(driftX1 + size.width / 2, driftY1 + size.height / 2),
                        radius = 400.dp.toPx()
                    )
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(GlassCyan.copy(alpha = 0.25f), Color.Transparent),
                            center = Offset(driftX2 + size.width / 3, driftY2 + size.height / 3),
                            radius = 450.dp.toPx()
                        ),
                        center = Offset(driftX2 + size.width / 3, driftY2 + size.height / 3),
                        radius = 450.dp.toPx()
                    )
                }
        ) {
            // Apply the generated high-quality background image as background texture
            Image(
                painter = painterResource(id = R.drawable.img_vibrant_bg_1784343645209),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            Modifier.blur(28.dp)
                        } else {
                            Modifier
                        }
                    ),
                contentScale = ContentScale.Crop,
                alpha = 0.5f
            )

            // Scaffolding content
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    val currentTrack by viewModel.currentTrack.collectAsState()
                    GlassHeader(
                        activeTab = activeTab,
                        onTabSelected = { activeTab = it },
                        isTablet = isTablet,
                        currentTrack = currentTrack,
                        onCoverClick = { activeTab = "Now Playing" }
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (isTablet) {
                        // Landscape/Tablet Split Pane Canonical Layout
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Left Pane: Navigation & Track Browsing
                            Box(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .fillMaxHeight()
                                    .glassCard()
                            ) {
                                when (activeTab) {
                                    "Browse" -> TrackBrowserView(viewModel)
                                    "Favorites" -> FavoritesView(viewModel)
                                }
                            }

                            // Right Pane: Standard High-Fidelity Glass Player
                            Box(
                                modifier = Modifier
                                    .weight(1.5f)
                                    .fillMaxHeight()
                                    .glassCard()
                            ) {
                                MainPlayerView(viewModel)
                            }
                        }
                    } else {
                        // Phone Navigation Tabs Flow
                        AnimatedContent(
                            targetState = activeTab,
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                            },
                            label = "screen_navigation"
                        ) { currentScreen ->
                            when (currentScreen) {
                                "Browse" -> Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .glassCard()
                                ) {
                                    TrackBrowserView(viewModel)
                                }
                                "Favorites" -> Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .glassCard()
                                ) {
                                    FavoritesView(viewModel)
                                }
                                "Now Playing" -> Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .glassCard()
                                ) {
                                    MainPlayerView(viewModel)
                                }
                            }
                        }

                        // Sliding Bottom Mini Player for Phone Layout (only shown when not on Now Playing tab and track is selected)
                        val currentTrackState by viewModel.currentTrack.collectAsState()
                        if (activeTab != "Now Playing" && currentTrackState != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .glassCard(borderColor = GlassCyan.copy(alpha = 0.5f))
                                    .clickable { activeTab = "Now Playing" }
                            ) {
                                MiniPlayerView(viewModel)
                            }
                        }
                    }
                }
            }
        }
    }
}

// Reusable glassmorphic layout card modifier (frosted translucent panel)
fun Modifier.glassCard(borderColor: Color = GlassBorderWhite, radius: Float = 24f): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .clip(shape)
        .background(
            Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.12f),
                    Color(0xFF11122B).copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.04f)
                ),
                start = Offset.Zero,
                end = Offset(400f, 600f)
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor.copy(alpha = 0.55f),
                    Color.White.copy(alpha = 0.08f),
                    borderColor.copy(alpha = 0.2f)
                ),
                start = Offset(0f, 0f),
                end = Offset(100f, 400f)
            ),
            shape = shape
        )
}

/** Lightweight row chrome for long song lists — much cheaper to scroll than [glassCard]. */
fun Modifier.listRowSurface(highlighted: Boolean = false, radius: Float = 16f): Modifier {
    val shape = RoundedCornerShape(radius.dp)
    return this
        .clip(shape)
        .background(
            if (highlighted) GlassCyan.copy(alpha = 0.12f)
            else Color.White.copy(alpha = 0.06f)
        )
        .border(
            width = 1.dp,
            color = if (highlighted) GlassCyan.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
            shape = shape
        )
}

@Composable
fun GlassHeader(
    activeTab: String,
    onTabSelected: (String) -> Unit,
    isTablet: Boolean,
    currentTrack: AudioTrackEntity? = null,
    onCoverClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val frameShape = RoundedCornerShape(24.dp)
    val coverShape = RoundedCornerShape(20.dp)

    // Glass frame: cover fills the inside (like the reference), brand + tabs stay on top
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(frameShape)
            .background(Color(0x6611122B))
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassCyan.copy(alpha = 0.7f),
                        Color.White.copy(alpha = 0.2f),
                        GlassMagenta.copy(alpha = 0.45f)
                    )
                ),
                shape = frameShape
            )
            .padding(4.dp) // small border gap between glass edge and cover
            .clip(coverShape)
    ) {
        // Cover art fills the glass interior (matched to stacked content height)
        Box(modifier = Modifier.matchParentSize()) {
            if (currentTrack != null) {
                if (currentTrack.uri == AudioRepository.SYNTH_URI) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        GlassMagenta.copy(alpha = 0.45f),
                                        Color(0xFF11122B),
                                        GlassCyan.copy(alpha = 0.35f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.35f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                } else if (!currentTrack.albumArtUri.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(currentTrack.albumArtUri)
                            .size(Size(960, 320))
                            .crossfade(true)
                            .allowHardware(true)
                            .build(),
                        contentDescription = currentTrack.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(id = R.drawable.img_app_icon_1784343634612),
                        placeholder = painterResource(id = R.drawable.img_app_icon_1784343634612)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        GlassCyan.copy(alpha = 0.35f),
                                        Color(0xFF11122B),
                                        GlassMagenta.copy(alpha = 0.3f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Soft glass scrim so brand + tabs stay readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0xCC070814),
                                    Color(0x88070814),
                                    Color(0xB3070814)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.10f),
                                    Color(0xFF11122B).copy(alpha = 0.55f),
                                    Color.White.copy(alpha = 0.04f)
                                )
                            )
                        )
                )
            }
        }

        // Brand on top, tabs below — full width so nothing is clipped
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = currentTrack != null,
                        onClick = onCoverClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = GlassCyan,
                    modifier = Modifier
                        .size(26.dp)
                        .drawBehind {
                            drawCircle(
                                color = GlassCyan.copy(alpha = 0.35f),
                                radius = 18.dp.toPx()
                            )
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GlassPlayer",
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White,
                            shadow = Shadow(
                                color = GlassTextGlow,
                                offset = Offset(0f, 0f),
                                blurRadius = 12f
                            )
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "By Prosper Sasuu",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.6.sp,
                        color = GlassCyan.copy(alpha = 0.9f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val tabs = if (isTablet) {
                    listOf("Browse", "Favorites")
                } else {
                    listOf("Browse", "Now Playing", "Favorites")
                }
                tabs.forEach { tab ->
                    val isSelected = activeTab == tab
                    val tabGlowColor = if (tab == "Now Playing") GlassMagenta else GlassCyan

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) tabGlowColor.copy(alpha = 0.22f)
                                else Color.Black.copy(alpha = 0.25f)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) {
                                    tabGlowColor.copy(alpha = 0.5f)
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onTabSelected(tab) }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrackBrowserView(viewModel: AudioViewModel) {
    val context = LocalContext.current
    val tracks by viewModel.allTracks.collectAsState()
    val allTracksRaw by viewModel.allTracksIncludingBlacklisted.collectAsState()
    val playlists by viewModel.allPlaylists.collectAsState()
    val blacklistedFolders by viewModel.blacklistedFolders.collectAsState()
    val recentlyAdded by viewModel.recentlyAddedTracks.collectAsState()
    val mostPlayed by viewModel.mostPlayedTracks.collectAsState()
    
    var searchQuery by remember { mutableStateOf("") }
    var browserTab by remember { mutableStateOf("Songs") }
    
    // Drill-down selection states
    var selectedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedAlbum by remember { mutableStateOf<String?>(null) }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var selectedSmart by remember { mutableStateOf<String?>(null) }
    
    // Dialog states
    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var trackToEditTags by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // File pickers
    val audioFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            var title = "Imported File"
            var duration = 180000L
            try {
                val attributionContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.createAttributionContext("audio_player")
                } else {
                    context
                }
                attributionContext.contentResolver.query(selectedUri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
                    val durIdx = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                    if (cursor.moveToFirst()) {
                        if (nameIdx != -1) title = cursor.getString(nameIdx) ?: title
                        if (durIdx != -1) duration = cursor.getLong(durIdx).takeIf { it > 0 } ?: duration
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            viewModel.addLocalTrack(selectedUri.toString(), title, "Local Audio", duration)
        }
    }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val isScanning by viewModel.isScanning.collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results[audioPermission] == true) {
            viewModel.scanDeviceAudio(context)
            viewModel.startWatchingLibrary()
        }
    }

    // Auto-scan on first launch + keep watching MediaStore for new downloads
    LaunchedEffect(Unit) {
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, audioPermission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(audioPermission)
            }
            // POST_NOTIFICATIONS is also requested in EnsureLockScreenNotifications (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
        }
        if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.scanDeviceAudio(context)
            viewModel.startWatchingLibrary()
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Search & Import Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search songs, artists, albums, folders…", color = Color.White.copy(alpha = 0.4f)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.6f)) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.05f))
                    .testTag("search_field"),
                colors = TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            // Rescan device library (new downloads)
            IconButton(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, audioPermission)
                        == PackageManager.PERMISSION_GRANTED
                    ) {
                        viewModel.scanDeviceAudio(context)
                        viewModel.startWatchingLibrary()
                    } else {
                        permissionLauncher.launch(arrayOf(audioPermission))
                    }
                },
                modifier = Modifier
                    .size(54.dp)
                    .glassCard()
                    .testTag("refresh_library_button")
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = GlassCyan,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh library", tint = GlassCyan)
                }
            }

            // Import local file button
            IconButton(
                onClick = { audioFilePickerLauncher.launch("audio/*") },
                modifier = Modifier
                    .size(54.dp)
                    .glassCard()
                    .testTag("import_audio_button")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Import file", tint = GlassCyan)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Sub-tabs navigation bar
        androidx.compose.foundation.lazy.LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val tabs = listOf("Songs", "Smart", "Playlists", "Folders", "Albums", "Artists", "Blacklist")
            items(tabs) { tab ->
                val isSel = browserTab == tab
                val glowColor = if (isSel) GlassCyan else Color.White.copy(alpha = 0.4f)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSel) GlassCyan.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.05f))
                        .border(
                            width = 1.dp,
                            color = if (isSel) GlassCyan.copy(alpha = 0.4f) else Color.Transparent,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            browserTab = tab
                            selectedPlaylist = null
                            selectedFolder = null
                            selectedAlbum = null
                            selectedArtist = null
                            selectedSmart = null
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val tabIcon = when (tab) {
                            "Songs" -> Icons.Rounded.MusicNote
                            "Smart" -> Icons.Rounded.AutoAwesome
                            "Playlists" -> Icons.Rounded.QueueMusic
                            "Folders" -> Icons.Rounded.Folder
                            "Albums" -> Icons.Rounded.Album
                            "Blacklist" -> Icons.Rounded.VisibilityOff
                            else -> Icons.Rounded.Person
                        }
                        Icon(
                            imageVector = tabIcon,
                            contentDescription = null,
                            tint = glowColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tab,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSel) Color.White else Color.White.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Bounded height so LazyColumn virtualizes (smooth scrolling)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // Main display logic based on selected tab and drilldown
        when (browserTab) {
            "Songs" -> {
                val filteredTracks = remember(tracks, searchQuery) {
                    val q = searchQuery.trim().lowercase(Locale.ROOT)
                    if (q.isEmpty()) tracks
                    else tracks.filter { track ->
                        track.title.lowercase(Locale.ROOT).contains(q) ||
                            track.artist.lowercase(Locale.ROOT).contains(q) ||
                            track.album.lowercase(Locale.ROOT).contains(q) ||
                            track.folderName.lowercase(Locale.ROOT).contains(q)
                    }
                }

                if (filteredTracks.isEmpty()) {
                    SongEmptyState(permissionLauncher)
                } else {
                    TrackList(
                        tracks = filteredTracks,
                        viewModel = viewModel,
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it }
                    )
                }
            }

            "Smart" -> {
                if (selectedSmart != null) {
                    val smartTracks = when (selectedSmart) {
                        "Recently Added" -> recentlyAdded
                        "Most Played" -> mostPlayed
                        else -> emptyList()
                    }
                    GroupDetailsView(
                        title = selectedSmart!!,
                        tracks = smartTracks,
                        viewModel = viewModel,
                        onBack = { selectedSmart = null },
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it }
                    )
                } else {
                    val smartCategories = listOf(
                        Triple("Recently Added", Icons.Rounded.NewReleases, recentlyAdded.size),
                        Triple("Most Played", Icons.Rounded.Whatshot, mostPlayed.size)
                    )
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(smartCategories, key = { it.first }) { (name, icon, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .listRowSurface()
                                    .clickable { selectedSmart = name }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(GlassMagenta.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = GlassMagenta, modifier = Modifier.size(24.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text(
                                        "$count songs · Smart playlist",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 12.sp
                                    )
                                }
                                Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
                            }
                        }
                    }
                }
            }

            "Playlists" -> {
                if (selectedPlaylist != null) {
                    val playlistTracks by viewModel.getTracksInPlaylist(selectedPlaylist!!.id).collectAsState(initial = emptyList())
                    
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPlaylist = null }
                                .padding(vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = GlassCyan,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = selectedPlaylist!!.name,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (playlistTracks.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.playTrack(playlistTracks.first(), playlistTracks) },
                                    colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.15f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .border(1.dp, GlassCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                                    contentPadding = PaddingValues(vertical = 12.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = GlassCyan)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Play All", color = Color.White)
                                }
                                IconButton(
                                    onClick = {
                                        val share = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                viewModel.playlistShareText(
                                                    selectedPlaylist!!.name,
                                                    playlistTracks
                                                )
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(share, "Share playlist"))
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .glassCard()
                                ) {
                                    Icon(Icons.Rounded.Share, contentDescription = "Share playlist", tint = GlassCyan)
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            val currentTrack by viewModel.currentTrack.collectAsState()
                            val isPlaying by viewModel.isPlaying.collectAsState()
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = playlistTracks,
                                    key = { it.id },
                                    contentType = { "track" }
                                ) { track ->
                                    TrackItemRow(
                                        track = track,
                                        viewModel = viewModel,
                                        onAddToPlaylist = { trackToAddToPlaylist = it },
                                        onEditTags = { trackToEditTags = it },
                                        customQueue = playlistTracks,
                                        onRemoveFromPlaylist = {
                                            viewModel.removeTrackFromPlaylist(selectedPlaylist!!.id, track.id)
                                        },
                                        isCurrentlyPlaying = currentTrack?.id == track.id,
                                        isPlayingActive = isPlaying && currentTrack?.id == track.id
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Rounded.PlaylistAdd,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.3f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "No songs in this playlist yet.",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Button(
                            onClick = { showCreatePlaylistDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassMagenta.copy(alpha = 0.15f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, GlassMagenta.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null, tint = GlassMagenta)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create New Playlist", color = Color.White)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (playlists.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No playlists created yet.", color = Color.White.copy(alpha = 0.5f))
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(playlists) { playlist ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .glassCard()
                                            .clickable { selectedPlaylist = playlist }
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(GlassMagenta.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = GlassMagenta,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = playlist.name,
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )
                                            Text(
                                                text = "Playlist",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 12.sp
                                            )
                                        }

                                        IconButton(onClick = { viewModel.deletePlaylist(playlist.id) }) {
                                            Icon(
                                                imageVector = Icons.Rounded.Delete,
                                                contentDescription = "Delete Playlist",
                                                tint = Color.Red.copy(alpha = 0.6f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Folders" -> {
                if (selectedFolder != null) {
                    val folderTracks = allTracksRaw.filter { it.folderName == selectedFolder }
                    GroupDetailsView(
                        title = selectedFolder!!,
                        tracks = folderTracks,
                        viewModel = viewModel,
                        onBack = { selectedFolder = null },
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it }
                    )
                } else {
                    val folderGroups = remember(allTracksRaw, searchQuery) {
                        val q = searchQuery.trim().lowercase(Locale.ROOT)
                        allTracksRaw.groupBy { it.folderName }.filterKeys { name ->
                            q.isEmpty() || name.lowercase(Locale.ROOT).contains(q)
                        }
                    }
                    if (folderGroups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No folders found.", color = Color.White.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = folderGroups.keys.toList(),
                                key = { it }
                            ) { folderName ->
                                val folderTracks = folderGroups[folderName] ?: emptyList()
                                val isHidden = folderName in blacklistedFolders
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .listRowSurface()
                                        .clickable { selectedFolder = folderName }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GlassCyan.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Folder,
                                            contentDescription = null,
                                            tint = if (isHidden) Color.White.copy(alpha = 0.35f) else GlassCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = folderName,
                                            color = if (isHidden) Color.White.copy(alpha = 0.45f) else Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = if (isHidden) "Hidden from library · ${folderTracks.size} songs"
                                            else "${folderTracks.size} songs",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            viewModel.setFolderBlacklisted(folderName, !isHidden)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isHidden) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                            contentDescription = if (isHidden) "Show folder" else "Hide folder",
                                            tint = if (isHidden) GlassMagenta else GlassCyan
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "Albums" -> {
                if (selectedAlbum != null) {
                    val albumTracks = tracks.filter { it.album == selectedAlbum }
                    GroupDetailsView(
                        title = selectedAlbum!!,
                        tracks = albumTracks,
                        viewModel = viewModel,
                        onBack = { selectedAlbum = null },
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it }
                    )
                } else {
                    val albumGroups = remember(tracks, searchQuery) {
                        val q = searchQuery.trim().lowercase(Locale.ROOT)
                        tracks.groupBy { it.album }.filterKeys { name ->
                            q.isEmpty() || name.lowercase(Locale.ROOT).contains(q) ||
                                tracks.any {
                                    it.album == name && it.artist.lowercase(Locale.ROOT).contains(q)
                                }
                        }
                    }
                    if (albumGroups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No albums found.", color = Color.White.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            state = rememberLazyListState(),
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(
                                items = albumGroups.keys.toList(),
                                key = { it }
                            ) { albumName ->
                                val albumTracks = albumGroups[albumName] ?: emptyList()
                                val artistName = albumTracks.firstOrNull()?.artist ?: "Unknown Artist"
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .listRowSurface()
                                        .clickable { selectedAlbum = albumName }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(GlassPurple.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Album,
                                            contentDescription = null,
                                            tint = GlassPurple,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = albumName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "$artistName • ${albumTracks.size} songs",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "Artists" -> {
                if (selectedArtist != null) {
                    val artistTracks = tracks.filter { it.artist == selectedArtist }
                    GroupDetailsView(
                        title = selectedArtist!!,
                        tracks = artistTracks,
                        viewModel = viewModel,
                        onBack = { selectedArtist = null },
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it }
                    )
                } else {
                    val artistGroups = remember(tracks, searchQuery) {
                        val q = searchQuery.trim().lowercase(Locale.ROOT)
                        tracks.groupBy { it.artist }.filterKeys { name ->
                            q.isEmpty() || name.lowercase(Locale.ROOT).contains(q)
                        }
                    }
                    if (artistGroups.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No artists found.", color = Color.White.copy(alpha = 0.5f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(
                                items = artistGroups.keys.toList(),
                                key = { it }
                            ) { artistName ->
                                val artistTracks = artistGroups[artistName] ?: emptyList()
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .listRowSurface()
                                        .clickable { selectedArtist = artistName }
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(GlassCyan.copy(alpha = 0.1f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Person,
                                            contentDescription = null,
                                            tint = GlassCyan,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = artistName,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp
                                        )
                                        Text(
                                            text = "${artistTracks.size} songs",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 12.sp
                                        )
                                    }

                                    Icon(
                                        imageVector = Icons.Rounded.ChevronRight,
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "Blacklist" -> {
                val blocked = blacklistedFolders.toList().sorted()
                if (blocked.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                "No hidden folders",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                            Text(
                                "Use the eye icon in Folders to hide ringtones, voice notes, etc.",
                                color = Color.White.copy(alpha = 0.35f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = rememberLazyListState(),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(blocked, key = { it }) { folderName ->
                            val count = allTracksRaw.count { it.folderName == folderName }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .listRowSurface()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.VisibilityOff, null, tint = GlassMagenta, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(folderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("$count songs hidden", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                }
                                TextButton(onClick = { viewModel.setFolderBlacklisted(folderName, false) }) {
                                    Text("Restore", color = GlassCyan, fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        } // end bounded browse content Box
    }

    // Modal dialogs
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = {
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            onConfirm = {
                if (newPlaylistName.isNotBlank()) {
                    viewModel.createPlaylist(newPlaylistName)
                }
                showCreatePlaylistDialog = false
                newPlaylistName = ""
            },
            playlistName = newPlaylistName,
            onNameChange = { newPlaylistName = it }
        )
    }

    if (trackToAddToPlaylist != null) {
        AddTrackToPlaylistDialog(
            track = trackToAddToPlaylist!!,
            playlists = playlists,
            onDismiss = { trackToAddToPlaylist = null },
            onPlaylistSelected = { playlistId ->
                viewModel.addTrackToPlaylist(playlistId, trackToAddToPlaylist!!.id)
                trackToAddToPlaylist = null
            },
            onCreatePlaylistInline = { name ->
                viewModel.createPlaylist(name)
            }
        )
    }

    if (trackToEditTags != null) {
        EditTagsDialog(
            track = trackToEditTags!!,
            onDismiss = { trackToEditTags = null },
            onSave = { title, artist, album ->
                viewModel.updateTrackTags(trackToEditTags!!, title, artist, album)
                trackToEditTags = null
            }
        )
    }
}

@Composable
fun SongEmptyState(permissionLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicOff,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No audio tracks found on device",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }
                    permissionLauncher.launch(arrayOf(perm))
                },
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.2f)),
                modifier = Modifier.border(1.dp, GlassCyan, RoundedCornerShape(24.dp))
            ) {
                Text("Scan Local Storage", color = Color.White)
            }
        }
    }
}

@Composable
fun TrackList(
    tracks: List<AudioTrackEntity>,
    viewModel: AudioViewModel,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    onEditTags: (AudioTrackEntity) -> Unit = {}
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        items(
            items = tracks,
            key = { it.id },
            contentType = { "track" }
        ) { track ->
            TrackItemRow(
                track = track,
                viewModel = viewModel,
                onAddToPlaylist = onAddToPlaylist,
                onEditTags = onEditTags,
                isCurrentlyPlaying = currentTrack?.id == track.id,
                isPlayingActive = isPlaying && currentTrack?.id == track.id
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackItemRow(
    track: AudioTrackEntity,
    viewModel: AudioViewModel,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    onEditTags: (AudioTrackEntity) -> Unit = {},
    customQueue: List<AudioTrackEntity>? = null,
    onRemoveFromPlaylist: (() -> Unit)? = null,
    isCurrentlyPlaying: Boolean,
    isPlayingActive: Boolean
) {
    val playing = isCurrentlyPlaying
    val playingActive = isPlayingActive
    var showMenu by remember { mutableStateOf(false) }
    val canDeleteFromLibrary =
        onRemoveFromPlaylist == null &&
            track.category == "My Device" &&
            track.uri != AudioRepository.SYNTH_URI

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .listRowSurface(highlighted = playing)
                .combinedClickable(
                    onClick = { viewModel.playTrack(track, customQueue) },
                    onLongClick = { showMenu = true }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(
                track = track,
                modifier = Modifier.size(48.dp),
                corner = 12.dp
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = if (playing) GlassCyan else Color.White,
                    fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (playingActive) {
                // Live bars from engine waveform / FFT — only on the active row
                MiniBeatVisualizer(viewModel = viewModel)
            } else {
                Text(
                    text = formatDuration(track.durationMs),
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
            }
        }

        SongActionsDropdown(
            expanded = showMenu,
            onDismiss = { showMenu = false },
            track = track,
            showPlayNext = !playing,
            onPlayNext = { viewModel.playNext(track) },
            onToggleFavorite = { viewModel.toggleFavorite(track) },
            onAddToPlaylist = { onAddToPlaylist(track) },
            onEditTags = if (track.uri != AudioRepository.SYNTH_URI) {
                { onEditTags(track) }
            } else null,
            deleteLabel = when {
                onRemoveFromPlaylist != null -> "Remove from playlist"
                canDeleteFromLibrary -> "Delete"
                else -> null
            },
            onDelete = {
                when {
                    onRemoveFromPlaylist != null -> onRemoveFromPlaylist()
                    canDeleteFromLibrary -> viewModel.removeTrackFromDeviceCategory(track)
                }
            }
        )
    }
}

/** Compact beat-reactive bars for the currently playing list row. */
@Composable
private fun MiniBeatVisualizer(viewModel: AudioViewModel) {
    val bars by viewModel.waveformAmplitudes.collectAsState()
    val sample = remember(bars) {
        // Pick a few bands across the spectrum for a tight equalizer look
        listOf(
            bars.getOrElse(2) { 0.2f },
            bars.getOrElse(6) { 0.35f },
            bars.getOrElse(11) { 0.5f },
            bars.getOrElse(16) { 0.35f },
            bars.getOrElse(21) { 0.25f }
        )
    }

    Row(
        modifier = Modifier
            .width(28.dp)
            .height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        sample.forEach { amp ->
            val h = (amp.coerceIn(0.12f, 1f) * 18f).dp
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(1.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(GlassCyan, GlassPurple)
                        )
                    )
            )
        }
    }
}

@Composable
private fun SongActionsDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    track: AudioTrackEntity,
    showPlayNext: Boolean,
    onPlayNext: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: (() -> Unit)? = null,
    deleteLabel: String?,
    onDelete: () -> Unit
) {
    val glassItemColors = MenuDefaults.itemColors(
        textColor = Color.White,
        leadingIconColor = GlassCyan,
        trailingIconColor = Color.White.copy(alpha = 0.8f),
        disabledTextColor = Color.White.copy(alpha = 0.4f),
        disabledLeadingIconColor = Color.White.copy(alpha = 0.3f),
        disabledTrailingIconColor = Color.White.copy(alpha = 0.3f)
    )
    val dangerItemColors = MenuDefaults.itemColors(
        textColor = Color(0xFFFF8A9A),
        leadingIconColor = Color(0xFFFF6B6B),
        trailingIconColor = Color(0xFFFF6B6B),
        disabledTextColor = Color.White.copy(alpha = 0.4f),
        disabledLeadingIconColor = Color.White.copy(alpha = 0.3f),
        disabledTrailingIconColor = Color.White.copy(alpha = 0.3f)
    )

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .widthIn(min = 230.dp)
            .glassCard(borderColor = GlassCyan.copy(alpha = 0.55f), radius = 18f),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.Transparent,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        if (showPlayNext) {
            DropdownMenuItem(
                text = { Text("Play next", fontWeight = FontWeight.Medium) },
                onClick = {
                    onPlayNext()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.QueuePlayNext, contentDescription = null, tint = GlassCyan)
                },
                colors = glassItemColors
            )
        }
        DropdownMenuItem(
            text = {
                Text(
                    if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                    fontWeight = FontWeight.Medium
                )
            },
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
            leadingIcon = {
                Icon(
                    imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = null,
                    tint = GlassMagenta
                )
            },
            colors = glassItemColors.copy(
                leadingIconColor = GlassMagenta
            )
        )
        DropdownMenuItem(
            text = { Text("Add to playlist", fontWeight = FontWeight.Medium) },
            onClick = {
                onAddToPlaylist()
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, tint = GlassCyan)
            },
            colors = glassItemColors
        )
        if (onEditTags != null) {
            DropdownMenuItem(
                text = { Text("Edit tags", fontWeight = FontWeight.Medium) },
                onClick = {
                    onEditTags()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Edit, contentDescription = null, tint = GlassCyan)
                },
                colors = glassItemColors
            )
        }
        if (deleteLabel != null) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                color = Color.White.copy(alpha = 0.12f)
            )
            DropdownMenuItem(
                text = { Text(deleteLabel, fontWeight = FontWeight.Medium) },
                onClick = {
                    onDelete()
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = Color(0xFFFF6B6B))
                },
                colors = dangerItemColors
            )
        }
    }
}

@Composable
fun GroupDetailsView(
    title: String,
    tracks: List<AudioTrackEntity>,
    viewModel: AudioViewModel,
    onBack: () -> Unit,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    onEditTags: (AudioTrackEntity) -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onBack() }
                .padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = GlassCyan,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (tracks.isNotEmpty()) {
            Button(
                onClick = { viewModel.playTrack(tracks.first(), tracks) },
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, GlassCyan.copy(alpha = 0.5f), RoundedCornerShape(24.dp)),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = GlassCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All (${tracks.size} Songs)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(14.dp))

            val currentTrack by viewModel.currentTrack.collectAsState()
            val isPlaying by viewModel.isPlaying.collectAsState()
            LazyColumn(
                state = rememberLazyListState(),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = tracks,
                    key = { it.id },
                    contentType = { "track" }
                ) { track ->
                    TrackItemRow(
                        track = track,
                        viewModel = viewModel,
                        onAddToPlaylist = onAddToPlaylist,
                        onEditTags = onEditTags,
                        customQueue = tracks,
                        isCurrentlyPlaying = currentTrack?.id == track.id,
                        isPlayingActive = isPlaying && currentTrack?.id == track.id
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text("No songs found in this group.", color = Color.White.copy(alpha = 0.5f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    playlistName: String,
    onNameChange: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Enter a name for your new playlist:", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                TextField(
                    value = playlistName,
                    onValueChange = onNameChange,
                    placeholder = { Text("Chill Vibes...", color = Color.White.copy(alpha = 0.4f)) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedContainerColor = Color.White.copy(alpha = 0.05f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                        focusedIndicatorColor = GlassMagenta,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = GlassMagenta),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Create", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF0F1026),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(28.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTagsDialog(
    track: AudioTrackEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String) -> Unit
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }

    val fieldColors = TextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedContainerColor = Color.White.copy(alpha = 0.05f),
        unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
        focusedIndicatorColor = GlassCyan,
        unfocusedIndicatorColor = Color.Transparent,
        focusedLabelColor = GlassCyan,
        unfocusedLabelColor = Color.White.copy(alpha = 0.45f)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Tags", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Correct Title, Artist, and Album metadata for this track.",
                    color = Color.White.copy(alpha = 0.65f),
                    fontSize = 13.sp
                )
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, artist, album) },
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Save", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF0F1026),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(28.dp))
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTrackToPlaylistDialog(
    track: AudioTrackEntity,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreatePlaylistInline: (String) -> Unit
) {
    var showInlineCreate by remember { mutableStateOf(false) }
    var inlineName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Text(
                text = "Add to Playlist", 
                color = Color.White, 
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            ) 
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 300.dp)
            ) {
                Text(
                    text = "Add \"${track.title}\" to:",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(14.dp))

                if (showInlineCreate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = inlineName,
                            onValueChange = { inlineName = it },
                            placeholder = { Text("Name...", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedContainerColor = Color.White.copy(alpha = 0.05f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.05f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp)),
                            textStyle = TextStyle(fontSize = 14.sp)
                        )
                        Button(
                            onClick = {
                                if (inlineName.isNotBlank()) {
                                    onCreatePlaylistInline(inlineName)
                                    inlineName = ""
                                    showInlineCreate = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = GlassCyan),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Text("Save", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                } else {
                    TextButton(
                        onClick = { showInlineCreate = true },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, tint = GlassCyan, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Create New Playlist", color = GlassCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (playlists.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No playlists found.", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(playlists) { playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.03f))
                                    .clickable { onPlaylistSelected(playlist.id) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = GlassMagenta,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = playlist.name,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.6f))
            }
        },
        containerColor = Color(0xFF0F1026),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(28.dp))
    )
}


@Composable
fun FavoritesView(viewModel: AudioViewModel) {
    val favorites by viewModel.favoriteTracks.collectAsState()
    val recents by viewModel.recentTracks.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "My Library Favorites",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (favorites.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .glassCard()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No favorites yet", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1.2f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(favorites) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard()
                            .clickable { viewModel.playTrack(track) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = GlassMagenta,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(track.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                        }
                        IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                            Icon(Icons.Rounded.Favorite, null, tint = GlassMagenta, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Recently Played",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        if (recents.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(0.8f)
                    .fillMaxWidth()
                    .glassCard()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No recent play history", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(0.8f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(recents) { track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .glassCard()
                            .clickable { viewModel.playTrack(track) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.History, null, tint = GlassCyan, modifier = Modifier.padding(horizontal = 8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(track.title, color = Color.White, fontSize = 13.sp, maxLines = 1)
                            Text(track.artist, color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AlbumArtThumb(
    track: AudioTrackEntity,
    modifier: Modifier = Modifier,
    corner: androidx.compose.ui.unit.Dp = 12.dp
) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (track.uri == AudioRepository.SYNTH_URI) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = GlassMagenta,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        } else if (!track.albumArtUri.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.albumArtUri)
                    .size(Size(128, 128))
                    .crossfade(false)
                    .allowHardware(true)
                    .build(),
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.img_app_icon_1784343634612),
                placeholder = painterResource(id = R.drawable.img_app_icon_1784343634612)
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.AudioFile,
                contentDescription = null,
                tint = GlassCyan,
                modifier = Modifier.fillMaxSize(0.5f)
            )
        }
    }
}

@Composable
fun MainPlayerView(viewModel: AudioViewModel) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val playbackDuration by viewModel.playbackDuration.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val visualizerBars by viewModel.waveformAmplitudes.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsState()

    // Synth control flows
    val synthCutoff by viewModel.synthCutoff.collectAsState()
    val synthSpeed by viewModel.synthSpeed.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()

    var showExtras by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var lyricsDraft by remember { mutableStateOf("") }

    if (currentTrack == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Rounded.Headphones,
                    contentDescription = null,
                    tint = GlassCyan.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(96.dp)
                        .drawBehind {
                            drawCircle(
                                color = GlassCyan.copy(alpha = 0.15f),
                                radius = 70.dp.toPx()
                            )
                        }
                )
                Spacer(modifier = Modifier.height(18.dp))
                Text(
                    text = "Select a track to start playing",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        return
    }

    val track = currentTrack!!
    var isQueueExpanded by remember { mutableStateOf(false) }
    var queueTrackToAdd by remember { mutableStateOf<AudioTrackEntity?>(null) }
    val playlists by viewModel.allPlaylists.collectAsState()
    val scrollState = rememberScrollState()

    LaunchedEffect(track.id) {
        lyricsDraft = track.lyrics.orEmpty()
        viewModel.syncSystemVolume()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Main upper content area (Scrollable to prevent overflows on compact heights)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Track detail top title
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "NOW PLAYING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    color = GlassCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = track.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Center section: Rotated offset background glow plate, rotating artwork, and vinyl spindle
            // Swipe left/right on the vinyl to skip tracks
            var vinylDragAccum by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .pointerInput(track.id) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    vinylDragAccum < -80f -> viewModel.nextTrack()
                                    vinylDragAccum > 80f -> viewModel.previousTrack()
                                }
                                vinylDragAccum = 0f
                            },
                            onDragCancel = { vinylDragAccum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                vinylDragAccum += dragAmount
                            }
                        )
                    }
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(GlassCyan.copy(alpha = 0.3f), Color.Transparent),
                                radius = 130.dp.toPx()
                            )
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                // Rotated gradient back-plate layer from the design theme spec
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.92f)
                        .rotate(6f)
                        .clip(RoundedCornerShape(36.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(GlassMagenta.copy(alpha = 0.45f), GlassCyan.copy(alpha = 0.35f))
                            )
                        )
                )

                // Rotating animation state
                val rotationTransition = rememberInfiniteTransition(label = "album_rot")
                val rotationAngle by rotationTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(12000, easing = LinearEasing)
                    ),
                    label = "rot"
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.88f)
                        .rotate(if (isPlaying) rotationAngle else 0f)
                        .clip(CircleShape)
                        .border(4.dp, GlassBorderWhite, CircleShape)
                        .border(8.dp, Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    AlbumArtThumb(
                        track = track,
                        modifier = Modifier.fillMaxSize(),
                        corner = 100.dp
                    )

                    // Vinyl grooves effect
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                for (r in listOf(30, 50, 70, 90)) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.12f),
                                        radius = r.dp.toPx(),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                                    )
                                }
                            }
                    )
                }

                // Center spindle
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                        .border(2.dp, GlassCyan, CircleShape)
                )
            }

            // Real-time Waveform spectrum bar visualizer (pulsing in harmony!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                visualizerBars.forEach { amplitude ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(amplitude)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(GlassCyan, GlassPurple)
                                )
                            )
                    )
                }
            }

            // Seek Bar (Slider) & Timer
            Column(modifier = Modifier.fillMaxWidth()) {
                var sliderValueOverride by remember { mutableStateOf<Float?>(null) }
                val currentSliderValue = sliderValueOverride ?: (if (playbackDuration > 0) playbackPosition.toFloat() / playbackDuration else 0f)

                Slider(
                    value = currentSliderValue.coerceIn(0f, 1f),
                    onValueChange = { factor ->
                        sliderValueOverride = factor
                    },
                    onValueChangeFinished = {
                        sliderValueOverride?.let { factor ->
                            viewModel.seekTo((factor * playbackDuration).toLong())
                        }
                        sliderValueOverride = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("track_progress_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = GlassCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(if (sliderValueOverride != null) (sliderValueOverride!! * playbackDuration).toLong() else playbackPosition),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                    Text(
                        text = formatDuration(playbackDuration),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            // Action controllers row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleShuffle() },
                    modifier = Modifier.testTag("shuffle_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffleEnabled) GlassCyan else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(22.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.previousTrack() },
                    modifier = Modifier.testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Big glowing Play/Pause button with custom neon halo shadow
                IconButton(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(68.dp)
                        .drawBehind {
                            drawCircle(
                                color = if (isPlaying) GlassMagenta.copy(alpha = 0.45f) else GlassCyan.copy(alpha = 0.45f),
                                radius = 34.dp.toPx()
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.25f),
                                radius = 28.dp.toPx()
                            )
                        }
                        .testTag("play_pause_button")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                        contentDescription = "Play Pause",
                        tint = if (isPlaying) GlassMagenta else GlassCyan,
                        modifier = Modifier.size(56.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.nextTrack() },
                    modifier = Modifier.testTag("next_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                IconButton(
                    onClick = { viewModel.toggleLoop() },
                    modifier = Modifier.testTag("loop_button")
                ) {
                    when (repeatMode) {
                        RepeatMode.OFF -> Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Repeat off",
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(22.dp)
                        )
                        RepeatMode.ALL -> Icon(
                            imageVector = Icons.Rounded.Repeat,
                            contentDescription = "Repeat all",
                            tint = GlassCyan,
                            modifier = Modifier.size(22.dp)
                        )
                        RepeatMode.ONE -> Icon(
                            imageVector = Icons.Rounded.RepeatOne,
                            contentDescription = "Repeat one",
                            tint = GlassCyan,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Volume
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = null,
                    tint = GlassCyan,
                    modifier = Modifier.size(20.dp)
                )
                Slider(
                    value = volume,
                    onValueChange = { viewModel.setVolume(it) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("volume_slider"),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = GlassCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    )
                )
                Text(
                    text = "${(volume * 100).toInt()}%",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.width(40.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { showExtras = !showExtras },
                    label = { Text(if (showExtras) "Hide extras" else "EQ / Speed / Timer") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GlassPurple.copy(alpha = 0.2f),
                        labelColor = Color.White
                    )
                )
                AssistChip(
                    onClick = { showLyrics = !showLyrics },
                    label = { Text("Lyrics") },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GlassMagenta.copy(alpha = 0.2f),
                        labelColor = Color.White
                    )
                )
                IconButton(onClick = { viewModel.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) GlassMagenta else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            AnimatedVisibility(visible = showExtras) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Equalizer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Switch(
                            checked = equalizerEnabled,
                            onCheckedChange = { viewModel.setEqualizerEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = GlassCyan.copy(alpha = 0.5f),
                                checkedThumbColor = GlassCyan
                            )
                        )
                    }
                    if (equalizerBands.isEmpty()) {
                        Text(
                            "EQ available while a local track is playing",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    } else {
                        equalizerBands.forEachIndexed { index, value ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "B${index + 1}",
                                    color = Color.White.copy(alpha = 0.6f),
                                    fontSize = 10.sp,
                                    modifier = Modifier.width(28.dp),
                                    fontFamily = FontFamily.Monospace
                                )
                                Slider(
                                    value = value,
                                    onValueChange = { viewModel.setEqualizerBand(index, it) },
                                    enabled = equalizerEnabled,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = GlassPurple,
                                        activeTrackColor = GlassPurple,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Text("Playback speed", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "0.5x",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = playbackSpeed,
                            onValueChange = { viewModel.setPlaybackSpeed(it) },
                            valueRange = 0.5f..2.0f,
                            steps = 5,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                                .testTag("playback_speed_slider"),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = GlassCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.1fx", playbackSpeed),
                            fontSize = 12.sp,
                            color = GlassCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Text("Sleep timer", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    if (sleepRemaining > 0) {
                        Text(
                            "Stops in ${formatDuration(sleepRemaining)}",
                            color = GlassCyan,
                            fontSize = 12.sp
                        )
                        TextButton(onClick = { viewModel.cancelSleepTimer() }) {
                            Text("Cancel timer", color = GlassMagenta)
                        }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(5L to "5m", 15L to "15m", 30L to "30m", 60L to "60m").forEach { (mins, label) ->
                                AssistChip(
                                    onClick = { viewModel.setSleepTimer(mins * 60_000L) },
                                    label = { Text(label) },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = GlassCyan.copy(alpha = 0.15f),
                                        labelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(visible = showLyrics) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(borderColor = GlassMagenta.copy(alpha = 0.35f))
                        .padding(12.dp)
                ) {
                    Text("Lyrics", color = GlassMagenta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = lyricsDraft,
                        onValueChange = { lyricsDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = {
                            Text("Paste or write lyrics…", color = Color.White.copy(alpha = 0.4f))
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GlassMagenta.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                            cursorColor = GlassMagenta
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.updateLyrics(track.id, lyricsDraft.ifBlank { null }) },
                        colors = ButtonDefaults.buttonColors(containerColor = GlassMagenta.copy(alpha = 0.25f))
                    ) {
                        Text("Save lyrics", color = Color.White)
                    }
                }
            }

            // Animated expansion of procedural interactive panel when playing procedural synth
            AnimatedVisibility(
                visible = track.uri == AudioRepository.SYNTH_URI,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(borderColor = GlassMagenta.copy(alpha = 0.4f))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SYNTH MODULATION CONTROL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = GlassMagenta,
                            letterSpacing = 1.sp
                        )
                        Icon(
                            imageVector = Icons.Rounded.SettingsInputHdmi,
                            contentDescription = null,
                            tint = GlassMagenta,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Cutoff Modulation Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "FILTER",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(50.dp),
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = synthCutoff,
                            onValueChange = { viewModel.updateSynthCutoff(it) },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassMagenta,
                                activeTrackColor = GlassMagenta,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.1fHz", synthCutoff * 2000),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.width(45.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Speed Modulation Slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "TEMPO",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.width(50.dp),
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = synthSpeed,
                            onValueChange = { viewModel.updateSynthSpeed(it) },
                            valueRange = 0.5f..2.5f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassMagenta,
                                activeTrackColor = GlassMagenta,
                                inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                            )
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.1fx", synthSpeed),
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.width(45.dp),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Interactively scrollable Play Queue component pinning to the bottom
        InteractiveQueueView(
            viewModel = viewModel,
            isExpanded = isQueueExpanded,
            onToggleExpand = { isQueueExpanded = !isQueueExpanded },
            onAddToPlaylist = { queueTrackToAdd = it }
        )

        if (queueTrackToAdd != null) {
            AddTrackToPlaylistDialog(
                track = queueTrackToAdd!!,
                playlists = playlists,
                onDismiss = { queueTrackToAdd = null },
                onPlaylistSelected = { playlistId ->
                    viewModel.addTrackToPlaylist(playlistId, queueTrackToAdd!!.id)
                    queueTrackToAdd = null
                },
                onCreatePlaylistInline = { name ->
                    viewModel.createPlaylist(name)
                }
            )
        }
    }
}

@Composable
fun MiniPlayerView(viewModel: AudioViewModel) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    if (currentTrack == null) return
    val track = currentTrack!!

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArtThumb(
                track = track,
                modifier = Modifier.size(40.dp),
                corner = 8.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier.testTag("mini_play_pause_button")
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled,
                    contentDescription = "Play/Pause",
                    tint = GlassCyan,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = { viewModel.nextTrack() },
                modifier = Modifier.testTag("mini_next_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    contentDescription = "Next",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun InteractiveQueueView(
    viewModel: AudioViewModel,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    val tracks by viewModel.activeQueue.collectAsState()
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassCard()
            .animateContentSize()
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleExpand() },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.QueueMusic,
                    contentDescription = null,
                    tint = GlassCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Play Queue (${tracks.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                    contentDescription = if (isExpanded) "Collapse Queue" else "Expand Queue",
                    tint = Color.White.copy(alpha = 0.7f)
                )
            }
        }

        if (isExpanded) {
            Spacer(modifier = Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
            ) {
                if (tracks.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks in queue",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("queue_tracklist"),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(tracks) { track ->
                            val isCurrentlyPlaying = currentTrack?.id == track.id
                            var showMenu by remember { mutableStateOf(false) }
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrentlyPlaying) GlassCyan.copy(alpha = 0.12f)
                                            else Color.White.copy(alpha = 0.03f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isCurrentlyPlaying) GlassCyan.copy(alpha = 0.3f) else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = { viewModel.playTrack(track, tracks) },
                                            onLongClick = { showMenu = true }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.size(28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isCurrentlyPlaying && isPlaying) {
                                            MiniBeatVisualizer(viewModel = viewModel)
                                        } else {
                                            Icon(
                                                imageVector = if (isCurrentlyPlaying) Icons.Rounded.PlayArrow else Icons.Rounded.MusicNote,
                                                contentDescription = null,
                                                tint = if (isCurrentlyPlaying) GlassCyan else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title,
                                            color = if (isCurrentlyPlaying) GlassCyan else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = if (isCurrentlyPlaying) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist,
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Text(
                                        text = formatDuration(track.durationMs),
                                        color = Color.White.copy(alpha = 0.4f),
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }

                                SongActionsDropdown(
                                    expanded = showMenu,
                                    onDismiss = { showMenu = false },
                                    track = track,
                                    showPlayNext = !isCurrentlyPlaying,
                                    onPlayNext = { viewModel.playNext(track) },
                                    onToggleFavorite = { viewModel.toggleFavorite(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    deleteLabel = "Remove from queue",
                                    onDelete = { viewModel.removeFromQueue(track) }
                                )
                            }
                        }
                    }
                }
            }
        } else {
            val currentIdx = tracks.indexOfFirst { it.id == currentTrack?.id }
            val nextTrack = if (currentIdx != -1 && currentIdx + 1 < tracks.size) tracks[currentIdx + 1] else null

            if (nextTrack != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Next: ",
                            fontSize = 11.sp,
                            color = GlassCyan,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${nextTrack.title} • ${nextTrack.artist}",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatDuration(nextTrack.durationMs),
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// Utility to format duration in MM:SS
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}
