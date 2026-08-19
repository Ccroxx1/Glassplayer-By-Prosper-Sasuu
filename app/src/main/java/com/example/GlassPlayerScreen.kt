@file:Suppress("DEPRECATION")
@file:OptIn(ExperimentalFoundationApi::class)

package com.example

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val musicFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let { viewModel.importMusicFolder(it, context) }
    }
    val currentTrackForTheme by viewModel.currentTrack.collectAsState()
    val selectedThemeName by viewModel.colorTheme.collectAsState()

    // Dynamic glass accents from the current song's album art
    LaunchedEffect(currentTrackForTheme?.id, currentTrackForTheme?.albumArtUri, selectedThemeName) {
        extractGlassAccentsFromArt(context, currentTrackForTheme?.albumArtUri)
        val theme = GlassTheme.values().firstOrNull { it.name == selectedThemeName } ?: GlassTheme.DYNAMIC
        val resolved = ColorThemeManager.resolve(
            theme = theme,
            dynamic = GlassColors(
                cyan = GlassDynamic.cyan,
                magenta = GlassDynamic.magenta,
                purple = GlassDynamic.purple,
                accent = GlassDynamic.cyan,
                bgTint = Color(0x2211122B),
                glow = Color(0x9E00F0FF)
            )
        )
        GlassDynamic.cyan = resolved.cyan
        GlassDynamic.magenta = resolved.magenta
        GlassDynamic.purple = resolved.purple
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
                        onTabSelected = { tab ->
                            activeTab = tab
                        },
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
                                    "Browse" -> TrackBrowserView(viewModel, onAddSource = { musicFolderPicker.launch(null) })
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
                                    TrackBrowserView(viewModel, onAddSource = { musicFolderPicker.launch(null) })
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

/**
 * Primary glassmorphic play/pause control — frosted disc, neon halo, and
 * AnimatedContent icon swap. Used by [MainPlayerView] and [MiniPlayerView].
 */
@Composable
fun GlassPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    iconSize: Dp = 30.dp,
    contentDescription: String = "Play Pause",
    testTag: String = "play_pause_button"
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val buttonDiameter = size

    val accent by animateColorAsState(
        targetValue = if (isPlaying) GlassMagenta else GlassCyan,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "play_pause_accent"
    )
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.62f, stiffness = 520f),
        label = "play_pause_press"
    )
    val haloAlpha by animateFloatAsState(
        targetValue = if (isPlaying) 0.55f else 0.38f,
        animationSpec = tween(320),
        label = "play_pause_halo"
    )

    val pulse = rememberInfiniteTransition(label = "play_pause_pulse")
    val rawPulse by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "play_pause_pulse_scale"
    )
    val pulseScale = if (isPlaying) rawPulse else 1f

    Box(
        modifier = modifier
            .size(buttonDiameter)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .drawBehind {
                val r = buttonDiameter.toPx() / 2f
                // Soft neon bloom (pulses gently while playing)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = haloAlpha * 0.85f),
                            accent.copy(alpha = 0.12f),
                            Color.Transparent
                        ),
                        radius = r * 1.35f * pulseScale
                    ),
                    radius = r * 1.2f * pulseScale
                )
                // Inner highlight ring
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f),
                    radius = r * 0.78f
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Frosted glass disc
        Box(
            modifier = Modifier
                .fillMaxSize(0.82f)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.22f),
                            Color(0xFF11122B).copy(alpha = 0.72f),
                            accent.copy(alpha = 0.18f)
                        ),
                        start = Offset.Zero,
                        end = Offset(120f, 180f)
                    )
                )
                .border(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            accent.copy(alpha = 0.65f),
                            Color.White.copy(alpha = 0.12f)
                        ),
                        start = Offset(0f, 0f),
                        end = Offset(90f, 140f)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = isPlaying,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(
                        initialScale = 0.72f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    )) togetherWith (fadeOut(tween(140)) + scaleOut(
                        targetScale = 0.72f,
                        animationSpec = tween(160)
                    ))
                },
                label = "play_pause_icon"
            ) { playing ->
                Icon(
                    imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(iconSize)
                        // Optical centering for the play triangle
                        .graphicsLayer {
                            translationX = if (!playing) iconSize.toPx() * 0.06f else 0f
                        }
                )
            }
        }
    }
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
fun TrackBrowserView(viewModel: AudioViewModel, onAddSource: () -> Unit) {
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

    // One reusable selection controller for every Browse entity type. IDs are stable keys, never positions.
    val selection = rememberSaveable(saver = BrowseSelectionState.Saver) { BrowseSelectionState() }
    var showBulkDeleteDialog by remember { mutableStateOf(false) }
    var showBulkPlaylistDialog by remember { mutableStateOf(false) }
    var bulkActionMessage by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = selection.active) { selection.exit() }

    // Dialog states
    var trackToAddToPlaylist by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var trackToEditTags by remember { mutableStateOf<AudioTrackEntity?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSourcesDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    val sortPrefs = remember {
        context.getSharedPreferences("library_sort", android.content.Context.MODE_PRIVATE)
    }
    var sortMode by remember {
        mutableStateOf(sortModeFromPrefs(sortPrefs.getString("mode", SortMode.Title.name)))
    }
    var sortAscending by remember {
        mutableStateOf(sortPrefs.getBoolean("ascending", true))
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
        if (selection.active) {
            val selectedTracks = when (selection.kind) {
                SelectionKind.SONG -> allTracksRaw.filter { it.uri in selection.selectedKeys }
                SelectionKind.FOLDER -> selection.selectedKeys.flatMap { key ->
                    allTracksRaw.filter { it.folderName == key.removePrefix("folder:") }
                }
                SelectionKind.ALBUM -> allTracksRaw.filter { it.album in selection.selectedKeys.map { key -> key.removePrefix("album:") } }
                SelectionKind.ARTIST -> allTracksRaw.filter { it.artist in selection.selectedKeys.map { key -> key.removePrefix("artist:") } }
                SelectionKind.SMART -> selection.selectedKeys.flatMap { key ->
                    when (key.removePrefix("smart:")) {
                        "Recently Added" -> recentlyAdded
                        "Most Played" -> mostPlayed
                        "Never Played" -> viewModel.neverPlayedTracks.value
                        "Long Tracks" -> viewModel.longTracks.value
                        "Released This Year" -> viewModel.thisYearTracks.value
                        else -> emptyList()
                    }
                }
                SelectionKind.PLAYLIST -> emptyList()
            }
            SelectionActionBar(
                kind = selection.kind,
                selectedCount = selection.count,
                selectedTracks = selectedTracks,
                selectedFolderNames = selection.selectedKeys.map { it.removePrefix("folder:") },
                onAddToPlaylist = { showBulkPlaylistDialog = true },
                onOpenSelected = {
                    val key = selection.selectedKeys.firstOrNull()
                    when (selection.kind) {
                        SelectionKind.FOLDER -> selectedFolder = key?.removePrefix("folder:")
                        SelectionKind.ALBUM -> selectedAlbum = key?.removePrefix("album:")
                        SelectionKind.ARTIST -> selectedArtist = key?.removePrefix("artist:")
                        SelectionKind.SMART -> selectedSmart = key?.removePrefix("smart:")
                        SelectionKind.PLAYLIST -> selectedPlaylist = playlists.firstOrNull { "playlist:${it.id}" == key }
                        SelectionKind.SONG -> selectedTracks.firstOrNull()?.let { viewModel.playTrack(it, selectedTracks) }
                    }
                    selection.exit()
                },
                onQueue = {
                    selectedTracks.firstOrNull()?.let { first -> viewModel.playTrack(first, selectedTracks) }
                    selection.exit()
                },
                onFavorite = { viewModel.setFavorites(selectedTracks, selectedTracks.any { !it.isFavorite }); selection.exit() },
                onShare = {
                    shareTracks(context, selectedTracks)
                    selection.exit()
                },
                onHide = {
                    val foldersToHide = if (selection.kind == SelectionKind.FOLDER) {
                        selection.selectedKeys.map { it.removePrefix("folder:") }
                    } else {
                        selectedTracks.map { it.folderName }.distinct()
                    }
                    foldersToHide.filter { it.isNotBlank() }.forEach { viewModel.setFolderBlacklisted(it, true) }
                    selection.exit()
                },
                onRefresh = {
                    if (ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED) {
                        viewModel.scanDeviceAudio(context)
                        viewModel.startWatchingLibrary()
                    }
                },
                onRemoveLibrary = {
                    if (selection.kind == SelectionKind.FOLDER) viewModel.removeFoldersFromLibrary(selection.selectedKeys.map { it.removePrefix("folder:") })
                    else viewModel.removeTracksFromLibrary(selectedTracks)
                    selection.exit()
                },
                onDeleteDevice = { showBulkDeleteDialog = true }
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else {
            // Search bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            "Search songs, artists, albums…",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(54.dp)
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
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    singleLine = true
                )

                // Sort library
                IconButton(
                    onClick = { showSortDialog = true },
                    modifier = Modifier
                        .size(54.dp)
                        .glassCard()
                        .testTag("sort_library_button")
                ) {
                    Icon(Icons.Rounded.Sort, contentDescription = "Sort options", tint = GlassCyan)
                }

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
                                selection.exit()
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
        }

        // Bounded height so LazyColumn virtualizes (smooth scrolling)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Main display logic based on selected tab and drilldown
            when (browserTab) {
                "Songs" -> {
                    val filteredTracks = remember(tracks, searchQuery, sortMode, sortAscending) {
                        val q = searchQuery.trim().lowercase(Locale.ROOT)
                        val filtered = if (q.isEmpty()) tracks
                        else tracks.filter { track ->
                            track.title.lowercase(Locale.ROOT).contains(q) ||
                                track.artist.lowercase(Locale.ROOT).contains(q) ||
                                track.album.lowercase(Locale.ROOT).contains(q) ||
                                track.folderName.lowercase(Locale.ROOT).contains(q)
                        }
                        filtered.sortedByMode(sortMode, sortAscending)
                    }

                    if (filteredTracks.isEmpty()) {
                        SongEmptyState(permissionLauncher)
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SelectionHeader(
                                selection = selection,
                                label = "Select songs",
                                allKeys = filteredTracks.map { it.uri }
                            )
                            TrackList(
                                tracks = filteredTracks,
                                viewModel = viewModel,
                                onAddToPlaylist = { trackToAddToPlaylist = it },
                                onEditTags = { trackToEditTags = it },
                                selection = selection,
                                selectionContext = "songs:${filteredTracks.joinToString("|") { it.uri }}"
                            )
                        }
                    }
                }

                "Smart" -> {
                    val neverPlayed by viewModel.neverPlayedTracks.collectAsState()
                    val longTracks by viewModel.longTracks.collectAsState()
                    val thisYear by viewModel.thisYearTracks.collectAsState()

                    if (selectedSmart != null) {
                        val smartTracks = when (selectedSmart) {
                            "Recently Added" -> recentlyAdded
                            "Most Played" -> mostPlayed
                            "Never Played" -> neverPlayed
                            "Long Tracks" -> longTracks
                            "Released This Year" -> thisYear
                            else -> emptyList()
                        }
                        GroupDetailsView(
                            title = selectedSmart!!,
                            tracks = smartTracks,
                            viewModel = viewModel,
                            onBack = { selectedSmart = null },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onEditTags = { trackToEditTags = it },
                            selection = selection,
                            selectionContext = "groupSongs"
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            SelectionHeader(selection, "Select smart playlists", listOf("Recently Added", "Most Played", "Never Played", "Long Tracks", "Released This Year").map { "smart:$it" })
                            val smartCategories = listOf(
                                Triple("Recently Added", Icons.Rounded.NewReleases, recentlyAdded.size),
                                Triple("Most Played", Icons.Rounded.Whatshot, mostPlayed.size),
                                Triple("Never Played", Icons.Rounded.FiberNew, neverPlayed.size),
                                Triple("Long Tracks", Icons.Rounded.HourglassBottom, longTracks.size),
                                Triple("Released This Year", Icons.Rounded.CalendarToday, thisYear.size)
                            )
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(smartCategories, key = { it.first }) { (name, icon, count) ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .listRowSurface(highlighted = "smart:$name" in selection.selectedKeys)
                                            .combinedClickable(
                                                onClick = { if (selection.active) selection.toggle("smart:$name") else selectedSmart = name },
                                                onLongClick = { if (!selection.active) selection.begin(SelectionKind.SMART, "smart", "smart:$name") }
                                            )
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SelectionCheckIndicator(selected = "smart:$name" in selection.selectedKeys, visible = selection.active)
                                        Spacer(modifier = Modifier.width(4.dp))
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
                }

                "Playlists" -> {
                    if (selectedPlaylist != null) {
                        val playlistTracksRaw by viewModel.getTracksInPlaylist(selectedPlaylist!!.id).collectAsState(initial = emptyList())
                        val playlistTracks = remember(playlistTracksRaw, sortMode, sortAscending) {
                            playlistTracksRaw.sortedByMode(sortMode, sortAscending)
                        }

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
                                    IconButton(
                                        onClick = {
                                            val m3u = viewModel.exportPlaylistAsM3u(selectedPlaylist!!.name, playlistTracks)
                                            val share = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_TEXT, m3u)
                                                putExtra(Intent.EXTRA_SUBJECT, "${selectedPlaylist!!.name}.m3u8")
                                            }
                                            context.startActivity(Intent.createChooser(share, "Export M3U"))
                                        },
                                        modifier = Modifier
                                            .size(48.dp)
                                            .glassCard()
                                    ) {
                                        Icon(Icons.Rounded.FileDownload, contentDescription = "Export M3U", tint = GlassMagenta)
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))

                                val currentTrack by viewModel.currentTrack.collectAsState()
                                val isPlaying by viewModel.isPlaying.collectAsState()
                                SelectionHeader(selection, "Select songs", playlistTracks.map { it.uri })
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
                                            selection = selection,
                                            selectionContext = "playlistSongs:${selectedPlaylist!!.id}",
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
                                SelectionHeader(selection, "Select playlists", playlists.map { "playlist:${it.id}" })
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(playlists) { playlist ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .glassCard()
                                                .combinedClickable(
                                                    onClick = { if (selection.active) selection.toggle("playlist:${playlist.id}") else selectedPlaylist = playlist },
                                                    onLongClick = { if (!selection.active) selection.begin(SelectionKind.PLAYLIST, "playlists", "playlist:${playlist.id}") }
                                                )
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SelectionCheckIndicator(selected = "playlist:${playlist.id}" in selection.selectedKeys, visible = selection.active)
                                            Spacer(modifier = Modifier.width(4.dp))
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

                                            if (!selection.active) {
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
                }

                "Folders" -> {
                    if (selectedFolder != null) {
                        val folderTracks = remember(allTracksRaw, selectedFolder, sortMode, sortAscending) {
                            allTracksRaw
                                .filter { it.folderName == selectedFolder }
                                .sortedByMode(sortMode, sortAscending)
                        }
                        GroupDetailsView(
                            title = selectedFolder!!,
                            tracks = folderTracks,
                            viewModel = viewModel,
                            onBack = { selectedFolder = null },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onEditTags = { trackToEditTags = it },
                            selection = selection,
                            selectionContext = "groupSongs"
                        )
                    } else {
                        val folderGroups = remember(allTracksRaw, searchQuery) {
                            val q = searchQuery.trim().lowercase(Locale.ROOT)
                            allTracksRaw.groupBy { it.folderName }.filterKeys { name ->
                                q.isEmpty() || name.lowercase(Locale.ROOT).contains(q)
                            }
                        }
                        Column(modifier = Modifier.fillMaxSize()) {
                            SelectionHeader(
                                selection = selection,
                                label = "Select folders",
                                allKeys = folderGroups.keys.map { "folder:$it" }
                            )
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!selection.active && searchQuery.isEmpty()) {
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .listRowSurface()
                                                .clickable { showSourcesDialog = true }
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(GlassCyan.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(Icons.Rounded.LibraryMusic, null, tint = GlassCyan, modifier = Modifier.size(24.dp))
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("Manage Music Sources", color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("Add or remove imported folders", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                            }
                                            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.3f))
                                        }
                                    }
                                }

                                if (folderGroups.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = 40.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("No folders found.", color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                } else {
                                    items(
                                        items = folderGroups.keys.toList(),
                                        key = { it }
                                    ) { folderName ->
                                        val folderTracks = folderGroups[folderName] ?: emptyList()
                                        val isHidden = folderName in blacklistedFolders
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .listRowSurface(highlighted = "folder:$folderName" in selection.selectedKeys)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (selection.active) selection.toggle("folder:$folderName") else selectedFolder = folderName
                                                    },
                                                    onLongClick = {
                                                        if (!selection.active) selection.begin(SelectionKind.FOLDER, "folders", "folder:$folderName")
                                                    }
                                                )
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            SelectionCheckIndicator(selected = "folder:$folderName" in selection.selectedKeys, visible = selection.active)
                                            Spacer(modifier = Modifier.width(4.dp))
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

                                            if (!selection.active) {
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
                }

                "Albums" -> {
                    if (selectedAlbum != null) {
                        val albumTracks = remember(tracks, selectedAlbum, sortMode, sortAscending) {
                            tracks
                                .filter { it.album == selectedAlbum }
                                .sortedByMode(sortMode, sortAscending)
                        }
                        GroupDetailsView(
                            title = selectedAlbum!!,
                            tracks = albumTracks,
                            viewModel = viewModel,
                            onBack = { selectedAlbum = null },
                            onAddToPlaylist = { trackToAddToPlaylist = it },
                            onEditTags = { trackToEditTags = it },
                            selection = selection,
                            selectionContext = "groupSongs"
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            SelectionHeader(selection, "Select albums", albumGroups.keys.map { "album:$it" })
                            LazyColumn(
                                state = rememberLazyListState(),
                                modifier = Modifier.weight(1f),
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
                                            .listRowSurface(highlighted = "album:$albumName" in selection.selectedKeys)
                                            .combinedClickable(
                                                onClick = { if (selection.active) selection.toggle("album:$albumName") else selectedAlbum = albumName },
                                                onLongClick = { if (!selection.active) selection.begin(SelectionKind.ALBUM, "albums", "album:$albumName") }
                                            )
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SelectionCheckIndicator(selected = "album:$albumName" in selection.selectedKeys, visible = selection.active)
                                        Spacer(modifier = Modifier.width(4.dp))
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
            }

            "Artists" -> {
                if (selectedArtist != null) {
                    val artistTracks = remember(tracks, selectedArtist, sortMode, sortAscending) {
                        tracks
                            .filter { it.artist == selectedArtist }
                            .sortedByMode(sortMode, sortAscending)
                    }
                    GroupDetailsView(
                        title = selectedArtist!!,
                        tracks = artistTracks,
                        viewModel = viewModel,
                        onBack = { selectedArtist = null },
                        onAddToPlaylist = { trackToAddToPlaylist = it },
                        onEditTags = { trackToEditTags = it },
                        selection = selection,
                        selectionContext = "groupSongs"
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
                        Column(modifier = Modifier.fillMaxSize()) {
                            SelectionHeader(selection, "Select artists", artistGroups.keys.map { "artist:$it" })
                            LazyColumn(
                                modifier = Modifier.weight(1f),
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
                                            .listRowSurface(highlighted = "artist:$artistName" in selection.selectedKeys)
                                            .combinedClickable(
                                                onClick = { if (selection.active) selection.toggle("artist:$artistName") else selectedArtist = artistName },
                                                onLongClick = { if (!selection.active) selection.begin(SelectionKind.ARTIST, "artists", "artist:$artistName") }
                                            )
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        SelectionCheckIndicator(selected = "artist:$artistName" in selection.selectedKeys, visible = selection.active)
                                        Spacer(modifier = Modifier.width(4.dp))
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
    }
}
    // Modal dialogs
    if (showSortDialog) {
        SortTracksDialog(
            currentMode = sortMode,
            currentAscending = sortAscending,
            onDismiss = { showSortDialog = false },
            onApply = { mode, ascending ->
                sortMode = mode
                sortAscending = ascending
                sortPrefs.edit()
                    .putString("mode", mode.name)
                    .putBoolean("ascending", ascending)
                    .apply()
                showSortDialog = false
            }
        )
    }

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

    if (showBulkPlaylistDialog) {
        AddTracksToPlaylistDialog(
            tracks = when (selection.kind) {
                SelectionKind.SONG -> allTracksRaw.filter { it.uri in selection.selectedKeys }
                SelectionKind.FOLDER -> selection.selectedKeys.flatMap { key -> allTracksRaw.filter { it.folderName == key.removePrefix("folder:") } }
                SelectionKind.ALBUM -> allTracksRaw.filter { it.album in selection.selectedKeys.map { key -> key.removePrefix("album:") } }
                SelectionKind.ARTIST -> allTracksRaw.filter { it.artist in selection.selectedKeys.map { key -> key.removePrefix("artist:") } }
                SelectionKind.SMART -> emptyList()
                SelectionKind.PLAYLIST -> emptyList()
            },
            playlists = playlists,
            onDismiss = { showBulkPlaylistDialog = false },
            onPlaylistSelected = { playlistId ->
                val chosenTracks = when (selection.kind) {
                    SelectionKind.SONG -> allTracksRaw.filter { it.uri in selection.selectedKeys }
                    SelectionKind.FOLDER -> selection.selectedKeys.flatMap { key -> allTracksRaw.filter { it.folderName == key.removePrefix("folder:") } }
                    SelectionKind.ALBUM -> allTracksRaw.filter { it.album in selection.selectedKeys.map { key -> key.removePrefix("album:") } }
                    SelectionKind.ARTIST -> allTracksRaw.filter { it.artist in selection.selectedKeys.map { key -> key.removePrefix("artist:") } }
                    SelectionKind.SMART, SelectionKind.PLAYLIST -> emptyList()
                }
                viewModel.addTracksToPlaylist(playlistId, chosenTracks)
                showBulkPlaylistDialog = false
                selection.exit()
            },
            onCreatePlaylistInline = { name -> viewModel.createPlaylist(name) }
        )
    }

    if (showBulkDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteDialog = false },
            title = { Text("Delete selected songs?", color = Color.White) },
            text = { Text("These files will be permanently deleted from your device.", color = Color.White.copy(alpha = 0.75f)) },
            confirmButton = {
                TextButton(onClick = {
                    val selectedTracks = allTracksRaw.filter { it.uri in selection.selectedKeys }
                    viewModel.deleteTracksFromDevice(selectedTracks) { deleted, failed ->
                        bulkActionMessage = if (failed == 0) "$deleted songs deleted" else "$deleted deleted, $failed could not be deleted"
                    }
                    showBulkDeleteDialog = false
                    selection.exit()
                }) { Text("Delete", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = { TextButton(onClick = { showBulkDeleteDialog = false }) { Text("Cancel", color = GlassCyan) } },
            containerColor = Color(0xFF111329),
            shape = RoundedCornerShape(22.dp)
        )
    }

    bulkActionMessage?.let { message ->
        LaunchedEffect(message) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            bulkActionMessage = null
        }
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

    if (showSourcesDialog) {
        MusicSourcesDialog(
            viewModel = viewModel,
            onDismiss = { showSourcesDialog = false },
            onAddSource = onAddSource
        )
    }
}


@Composable
private fun SelectionHeader(
    selection: BrowseSelectionState,
    label: String,
    allKeys: List<String>
) {
    if (!selection.active) return
    val allSelected = allKeys.isNotEmpty() && allKeys.all { it in selection.selectedKeys }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { selection.exit() },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Exit selection", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        Spacer(modifier = Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = Color.White.copy(alpha = 0.9f), fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text("${selection.count} selected", color = GlassCyan.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
        TextButton(
            onClick = {
                if (allSelected) selection.clearSelection() else selection.selectAll(allKeys)
            },
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            Text(
                if (allSelected) "Deselect All" else "Select All", 
                color = GlassCyan, 
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SelectionCheckIndicator(selected: Boolean, visible: Boolean = true) {
    if (!visible) return
    val gold = Color(0xFFFFD700)
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) gold.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.05f))
            .border(1.dp, if (selected) gold.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.2f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = gold, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun SelectionActionBar(
    kind: SelectionKind,
    selectedCount: Int,
    selectedTracks: List<AudioTrackEntity>,
    selectedFolderNames: List<String>,
    onAddToPlaylist: () -> Unit,
    onOpenSelected: () -> Unit,
    onQueue: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onHide: () -> Unit,
    onRefresh: () -> Unit,
    onRemoveLibrary: () -> Unit,
    onDeleteDevice: () -> Unit
) {
    val hasTracks = selectedTracks.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .glassCard(radius = 22f)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(0.38f)
        ) {
            Text(
                text = "$selectedCount selected", 
                color = Color.White, 
                fontWeight = FontWeight.Bold, 
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = when (kind) {
                    SelectionKind.SONG -> "Songs"
                    SelectionKind.FOLDER -> "Folders"
                    SelectionKind.ALBUM -> "Albums"
                    SelectionKind.ARTIST -> "Artists"
                    SelectionKind.PLAYLIST -> "Playlists"
                    SelectionKind.SMART -> "Smart playlists"
                },
                color = GlassCyan.copy(alpha = 0.7f), 
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        Row(
            modifier = Modifier
                .weight(0.62f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            if (selectedCount == 1 && kind != SelectionKind.SONG) {
                IconButton(onClick = onOpenSelected) { 
                    Icon(Icons.Rounded.OpenInNew, "Open", tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(20.dp)) 
                }
            }
            
            if (hasTracks) {
                IconButton(onClick = onAddToPlaylist) { Icon(Icons.Rounded.PlaylistAdd, "Add", tint = GlassCyan, modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onQueue) { Icon(Icons.Rounded.PlayArrow, "Play", tint = GlassCyan, modifier = Modifier.size(22.dp)) }
                IconButton(onClick = onFavorite) { Icon(Icons.Rounded.Favorite, "Heart", tint = GlassMagenta, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onShare) { Icon(Icons.Rounded.Share, "Share", tint = Color.White.copy(alpha = 0.7f), modifier = Modifier.size(20.dp)) }
            }
            
            if (kind == SelectionKind.FOLDER) {
                IconButton(onClick = onHide) { Icon(Icons.Rounded.VisibilityOff, "Hide", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onRefresh) { Icon(Icons.Rounded.Refresh, "Refresh", tint = GlassCyan, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onRemoveLibrary) { Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = Color(0xFFFFB86B), modifier = Modifier.size(20.dp)) }
            } else if (kind == SelectionKind.SONG) {
                IconButton(onClick = onHide) { Icon(Icons.Rounded.VisibilityOff, "Hide", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onRemoveLibrary) { Icon(Icons.Rounded.RemoveCircleOutline, "Remove", tint = Color(0xFFFFB86B), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onDeleteDevice) { Icon(Icons.Rounded.Delete, "Delete", tint = Color(0xFFFF6B6B), modifier = Modifier.size(20.dp)) }
            }
        }
    }
}

private fun shareTracks(context: android.content.Context, tracks: List<AudioTrackEntity>) {
    if (tracks.isEmpty()) return
    val uris = tracks.mapNotNull { runCatching { Uri.parse(it.uri) }.getOrNull() }
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "audio/*"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        putExtra(Intent.EXTRA_TEXT, tracks.joinToString("\n") { "${it.title} — ${it.artist}" })
    }
    context.startActivity(Intent.createChooser(intent, "Share selected songs"))
}

@Composable
private fun AddTracksToPlaylistDialog(
    tracks: List<AudioTrackEntity>,
    playlists: List<PlaylistEntity>,
    onDismiss: () -> Unit,
    onPlaylistSelected: (Int) -> Unit,
    onCreatePlaylistInline: (String) -> Unit
) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add ${tracks.size} songs to playlist", color = Color.White, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.heightIn(max = 360.dp)) {
                if (playlists.isEmpty()) Text("No playlists yet. Create one below.", color = Color.White.copy(alpha = 0.6f))
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(playlists, key = { it.id }) { playlist ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.05f)).clickable { onPlaylistSelected(playlist.id) }.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.QueueMusic, null, tint = GlassCyan)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(playlist.name, color = Color.White, modifier = Modifier.weight(1f))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White, focusedBorderColor = GlassCyan, unfocusedBorderColor = Color.White.copy(alpha = 0.25f), focusedLabelColor = GlassCyan, unfocusedLabelColor = Color.White.copy(alpha = 0.55f))
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (newName.isNotBlank()) { onCreatePlaylistInline(newName.trim()); newName = "" } }) { Text("Create", color = GlassCyan) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Color.White.copy(alpha = 0.7f)) } },
        containerColor = Color(0xFF111329),
        shape = RoundedCornerShape(22.dp)
    )
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
fun ColumnScope.TrackList(
    tracks: List<AudioTrackEntity>,
    viewModel: AudioViewModel,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    onEditTags: (AudioTrackEntity) -> Unit = {},
    selection: BrowseSelectionState? = null,
    selectionContext: String = ""
) {
    val currentTrack by viewModel.currentTrack.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val listState = rememberLazyListState()

    LazyColumn(
        state = listState,
        modifier = Modifier.weight(1f),
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
                selection = selection,
                selectionContext = selectionContext,
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
    selection: BrowseSelectionState? = null,
    selectionContext: String = "",
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

    val isSelected = selection?.selectedKeys?.contains(track.uri) == true
    val selectionEnabled = selection != null
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .listRowSurface(highlighted = playing || isSelected)
                .combinedClickable(
                    onClick = {
                        if (selectionEnabled && selection!!.active) selection.toggle(track.uri)
                        else viewModel.playTrack(track, customQueue)
                    },
                    onLongClick = {
                        if (selectionEnabled) {
                            if (!selection!!.active) selection.begin(SelectionKind.SONG, selectionContext, track.uri)
                            else selection.toggle(track.uri)
                        } else showMenu = true
                    }
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionEnabled) {
                SelectionCheckIndicator(selected = isSelected, visible = selection?.active == true)
                Spacer(modifier = Modifier.width(4.dp))
            }
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.artist,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.mood.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${moodEmoji(track.mood)} ${track.mood}",
                            color = GlassMagenta.copy(alpha = 0.9f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
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

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(20.dp)
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
    val context = LocalContext.current
    var showFileInfo by remember { mutableStateOf(false) }

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
            .widthIn(min = 230.dp, max = 280.dp)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF161735).copy(alpha = 0.94f),
                        Color(0xFF0D0E22).copy(alpha = 0.96f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            )
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        GlassCyan.copy(alpha = 0.65f),
                        Color.White.copy(alpha = 0.15f),
                        GlassMagenta.copy(alpha = 0.35f)
                    )
                ),
                shape = RoundedCornerShape(18.dp)
            ),
        shape = RoundedCornerShape(18.dp),
        containerColor = Color.Transparent,
        tonalElevation = 6.dp,
        shadowElevation = 12.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PopupCoverBlurThumb(track = track)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            color = Color.White.copy(alpha = 0.12f)
        )

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
        if (track.uri != AudioRepository.SYNTH_URI) {
            DropdownMenuItem(
                text = { Text("Share", fontWeight = FontWeight.Medium) },
                onClick = {
                    shareTrack(context, track)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Share, contentDescription = null, tint = GlassCyan)
                },
                colors = glassItemColors
            )
            DropdownMenuItem(
                text = { Text("Use as ringtone", fontWeight = FontWeight.Medium) },
                onClick = {
                    useTrackAsRingtone(context, track)
                    onDismiss()
                },
                leadingIcon = {
                    Icon(Icons.Rounded.NotificationsActive, contentDescription = null, tint = GlassMagenta)
                },
                colors = glassItemColors.copy(
                    leadingIconColor = GlassMagenta
                )
            )
        }
        DropdownMenuItem(
            text = { Text("File info", fontWeight = FontWeight.Medium) },
            onClick = {
                showFileInfo = true
                onDismiss()
            },
            leadingIcon = {
                Icon(Icons.Rounded.Info, contentDescription = null, tint = GlassCyan)
            },
            colors = glassItemColors
        )
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

    if (showFileInfo) {
        FileInfoDialog(
            track = track,
            onDismiss = { showFileInfo = false }
        )
    }
}

@Composable
private fun PopupCoverBlurThumb(track: AudioTrackEntity) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center
    ) {
        if (!track.albumArtUri.isNullOrBlank() && track.uri != AudioRepository.SYNTH_URI) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(track.albumArtUri)
                    .size(Size(96, 96))
                    .crossfade(false)
                    .allowHardware(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                error = painterResource(id = R.drawable.img_app_icon_1784343634612),
                placeholder = painterResource(id = R.drawable.img_app_icon_1784343634612)
            )
        } else {
            Icon(
                imageVector = if (track.uri == AudioRepository.SYNTH_URI) Icons.Rounded.Tune else Icons.Rounded.AudioFile,
                contentDescription = null,
                tint = if (track.uri == AudioRepository.SYNTH_URI) GlassMagenta else GlassCyan,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun shareTrack(context: android.content.Context, track: AudioTrackEntity) {
    if (track.uri == AudioRepository.SYNTH_URI) {
        Toast.makeText(context, "Synth track cannot be shared", Toast.LENGTH_SHORT).show()
        return
    }
    runCatching {
        val uri = Uri.parse(track.uri)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "audio/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, track.title)
            putExtra(Intent.EXTRA_TEXT, "${track.artist} - ${track.title}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share track").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "Unable to share this file", Toast.LENGTH_SHORT).show()
    }
}

private fun useTrackAsRingtone(context: android.content.Context, track: AudioTrackEntity) {
    if (track.uri == AudioRepository.SYNTH_URI) {
        Toast.makeText(context, "Synth track cannot be used as ringtone", Toast.LENGTH_SHORT).show()
        return
    }

    // Android M+ requires write-settings access to set the default ringtone directly.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.System.canWrite(context)) {
        val permissionIntent = Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(permissionIntent) }
        Toast.makeText(
            context,
            "Allow Modify system settings, then tap Use as ringtone again",
            Toast.LENGTH_LONG
        ).show()
        return
    }

    val uri = Uri.parse(track.uri)
    val setResult = runCatching {
        RingtoneManager.setActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE, uri)
    }

    if (setResult.isSuccess) {
        Toast.makeText(context, "Ringtone set", Toast.LENGTH_SHORT).show()
        return
    }

    // Fallback: open system picker with this track pre-selected.
    val pickerIntent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    if (pickerIntent.resolveActivity(context.packageManager) != null) {
        runCatching { context.startActivity(pickerIntent) }
            .onFailure {
                Toast.makeText(context, "Unable to open ringtone picker", Toast.LENGTH_SHORT).show()
            }
    } else {
        Toast.makeText(context, "Ringtone action is not available on this device", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun FileInfoDialog(
    track: AudioTrackEntity,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val infoText = remember(track) {
        buildFileInfoText(context, track)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File info", color = Color.White) },
        text = {
            Text(
                text = infoText,
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 13.sp
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = GlassCyan)
            }
        },
        containerColor = Color(0xFF0F1026)
    )
}

private fun buildFileInfoText(context: android.content.Context, track: AudioTrackEntity): String {
    val uri = Uri.parse(track.uri)
    var displayName: String? = null
    var sizeText = "Unknown"

    if (uri.scheme == "content") {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                            val bytes = cursor.getLong(sizeIndex)
                            sizeText = formatBytes(bytes)
                        }
                    }
                }
        }
    }

    val fileName = displayName ?: uri.lastPathSegment ?: "Unknown"
    val pathPart = uri.path ?: "Unknown"
    return buildString {
        appendLine(track.title)
        appendLine(track.artist)
        appendLine()
        appendLine("Album: ${track.album}")
        appendLine("Duration: ${formatDuration(track.durationMs)}")
        appendLine("Category: ${track.category}")
        appendLine("Folder: ${track.folderName}")
        appendLine("File name: $fileName")
        appendLine("Size: $sizeText")
        appendLine("URI: ${track.uri}")
        append("Path: $pathPart")
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = 1024.0
    val mb = kb * 1024.0
    val gb = mb * 1024.0
    val value = bytes.toDouble()
    return when {
        value >= gb -> String.format(Locale.ROOT, "%.2f GB", value / gb)
        value >= mb -> String.format(Locale.ROOT, "%.2f MB", value / mb)
        value >= kb -> String.format(Locale.ROOT, "%.1f KB", value / kb)
        else -> "$bytes B"
    }
}

@Composable
fun GroupDetailsView(
    title: String,
    tracks: List<AudioTrackEntity>,
    viewModel: AudioViewModel,
    onBack: () -> Unit,
    onAddToPlaylist: (AudioTrackEntity) -> Unit,
    onEditTags: (AudioTrackEntity) -> Unit = {},
    selection: BrowseSelectionState? = null,
    selectionContext: String = "group"
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

            if (selection != null) {
                SelectionHeader(selection, "Select songs", tracks.map { it.uri })
            }

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
                        selection = selection,
                        selectionContext = selectionContext,
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
    val playbackDuration by viewModel.playbackDuration.collectAsState()
    val isShuffleEnabled by viewModel.isShuffleEnabled.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val volume by viewModel.volume.collectAsState()
    val equalizerBands by viewModel.equalizerBands.collectAsState()
    val equalizerEnabled by viewModel.equalizerEnabled.collectAsState()
    val sleepRemaining by viewModel.sleepTimerRemainingMs.collectAsState()
    val isFetchingLyrics by viewModel.isFetchingLyrics.collectAsState()
    val waveformAmplitudes by viewModel.waveformAmplitudes.collectAsState()
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    val context = LocalContext.current

    // Haptic helper
    val haptic: () -> Unit = {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                val vm = context.getSystemService(android.os.VibratorManager::class.java)
                vm?.defaultVibrator?.vibrate(
                    android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                @Suppress("DEPRECATION")
                val vib = context.getSystemService(android.os.Vibrator::class.java)
                vib?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } catch (_: Exception) { }
    }

    // Synth control flows
    val synthCutoff by viewModel.synthCutoff.collectAsState()
    val synthSpeed by viewModel.synthSpeed.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val crossfadeSec by viewModel.crossfadeSec.collectAsState()
    val pitchSemitones by viewModel.pitchSemitones.collectAsState()
    val sleepFadeEnabled by viewModel.sleepFadeEnabled.collectAsState()
    val selectedThemeName by viewModel.colorTheme.collectAsState()
    val lastFmUsername by viewModel.lastFmUsername.collectAsState()
    val scrobbleCount by viewModel.scrobbleCount.collectAsState()
    val listeningStats by viewModel.listeningStats.collectAsState()
    val duplicateGroups by viewModel.duplicateGroups.collectAsState()

    var showExtras by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showDrivingMode by remember { mutableStateOf(false) }
    var showStats by remember { mutableStateOf(false) }
    var showDuplicates by remember { mutableStateOf(false) }
    var showBackupDialog by remember { mutableStateOf(false) }
    var showLastFmDialog by remember { mutableStateOf(false) }
    var showM3uImportDialog by remember { mutableStateOf(false) }
    var backupPayload by remember { mutableStateOf("") }
    var m3uPayload by remember { mutableStateOf("") }
    var lastFmUserInput by remember { mutableStateOf("") }
    var lastFmPasswordInput by remember { mutableStateOf("") }
    var actionMessage by remember { mutableStateOf<String?>(null) }
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

                // Rotating vinyl — only animate while playing to save CPU when paused
                val rotation = remember { Animatable(0f) }
                LaunchedEffect(isPlaying) {
                    if (isPlaying) {
                        while (true) {
                            val next = rotation.value + 360f
                            rotation.animateTo(
                                targetValue = next,
                                animationSpec = tween(durationMillis = 12_000, easing = LinearEasing)
                            )
                            // Keep value bounded so it doesn't grow forever
                            rotation.snapTo(rotation.value % 360f)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize(0.88f)
                        .rotate(rotation.value)
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

            // Waveform Seek Bar (replaces flat slider)
            val waveformAmps by viewModel.waveformAmplitudes.collectAsState()
            val position by viewModel.playbackPosition.collectAsState()
            WaveformSeekBar(
                amplitudes = waveformAmps,
                positionMs = position,
                durationMs = playbackDuration,
                onSeek = { viewModel.seekTo(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

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
                    onClick = { haptic(); viewModel.previousTrack() },
                    modifier = Modifier.testTag("prev_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                GlassPlayPauseButton(
                    isPlaying = isPlaying,
                    onClick = { haptic(); viewModel.togglePlayPause() },
                    size = 76.dp,
                    iconSize = 34.dp,
                    contentDescription = "Play Pause",
                    testTag = "play_pause_button"
                )

                IconButton(
                    onClick = { haptic(); viewModel.nextTrack() },
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

            // 5-Star Rating Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Rate: ",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
                (1..5).forEach { star ->
                    IconButton(
                        onClick = {
                            haptic()
                            viewModel.updateRating(track.id, if (track.rating == star) 0 else star)
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (star <= track.rating) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                            contentDescription = "$star stars",
                            tint = if (star <= track.rating) Color(0xFFFFD700) else Color.White.copy(alpha = 0.3f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                if (track.bpm > 0f) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${track.bpm.toInt()} BPM",
                        color = GlassCyan.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = {
                        val nextMood = nextMood(track.mood)
                        viewModel.setMood(track.id, nextMood)
                    },
                    label = {
                        val mood = track.mood.ifBlank { "Unassigned" }
                        Text("Mood: ${moodEmoji(mood)} $mood")
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = GlassMagenta.copy(alpha = 0.18f),
                        labelColor = Color.White
                    )
                )
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
                IconButton(onClick = { haptic(); viewModel.toggleFavorite(track) }) {
                    Icon(
                        imageVector = if (track.isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (track.isFavorite) GlassMagenta else Color.White.copy(alpha = 0.5f)
                    )
                }
                // Drive Mode button
                IconButton(onClick = { haptic(); showDrivingMode = true }) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = "Driving Mode",
                        tint = GlassCyan.copy(alpha = 0.8f)
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                haptic()
                                viewModel.setPlaybackSpeed(1.0f)
                            },
                            enabled = playbackSpeed != 1.0f
                        ) {
                            Text("Reset speed", color = GlassCyan)
                        }
                    }

                    Text("Pitch", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "-6",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = pitchSemitones,
                            onValueChange = { viewModel.setPitchSemitones(it) },
                            valueRange = -6f..6f,
                            steps = 11,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassMagenta,
                                activeTrackColor = GlassMagenta,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%+.0f st", pitchSemitones),
                            fontSize = 12.sp,
                            color = GlassMagenta,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(56.dp)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                haptic()
                                viewModel.setPitchSemitones(0f)
                            },
                            enabled = pitchSemitones != 0f
                        ) {
                            Text("Reset pitch", color = GlassMagenta)
                        }
                    }

                    Text("Crossfade", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "0s",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f),
                            fontFamily = FontFamily.Monospace
                        )
                        Slider(
                            value = crossfadeSec,
                            onValueChange = { viewModel.setCrossfade(it) },
                            valueRange = 0f..10f,
                            steps = 9,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = GlassCyan,
                                activeTrackColor = GlassCyan,
                                inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                            )
                        )
                        Text(
                            text = String.format(Locale.ROOT, "%.0fs", crossfadeSec),
                            fontSize = 12.sp,
                            color = GlassCyan,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(40.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sleep fade (last 30s)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Switch(
                            checked = sleepFadeEnabled,
                            onCheckedChange = { viewModel.setSleepFadeEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedTrackColor = GlassMagenta.copy(alpha = 0.5f),
                                checkedThumbColor = GlassMagenta
                            )
                        )
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Text("Theme", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(ColorThemeManager.allThemes()) { theme ->
                            val selected = selectedThemeName == theme.name
                            AssistChip(
                                onClick = { viewModel.setColorTheme(theme) },
                                label = { Text("${theme.emoji} ${theme.displayName}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) GlassCyan.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                                    labelColor = Color.White
                                )
                            )
                        }
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

                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                viewModel.refreshListeningStats()
                                showStats = true
                            },
                            label = { Text("Stats") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = GlassCyan.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        AssistChip(
                            onClick = {
                                viewModel.refreshDuplicateGroups()
                                showDuplicates = true
                            },
                            label = { Text("Duplicates") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = GlassMagenta.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        AssistChip(
                            onClick = { showM3uImportDialog = true },
                            label = { Text("M3U Import") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = GlassPurple.copy(alpha = 0.2f),
                                labelColor = Color.White
                            )
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = {
                                viewModel.exportBackup { backupPayload = it }
                                showBackupDialog = true
                            },
                            label = { Text("Backup") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = GlassCyan.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        AssistChip(
                            onClick = {
                                lastFmUserInput = if (lastFmUsername.isBlank()) "" else lastFmUsername
                                showLastFmDialog = true
                            },
                            label = { Text(if (lastFmUsername.isBlank()) "Last.fm Login" else "Last.fm Connected") },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = GlassMagenta.copy(alpha = 0.15f),
                                labelColor = Color.White
                            )
                        )
                        Text(
                            text = "Scrobbles: $scrobbleCount",
                            color = Color.White.copy(alpha = 0.65f),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
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
                    // Header with fetch button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Lyrics", color = GlassMagenta, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isFetchingLyrics) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = GlassCyan,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                TextButton(
                                    onClick = { viewModel.fetchLyricsFromLrcLib(track) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Download,
                                        contentDescription = "Auto-fetch lyrics",
                                        tint = GlassCyan,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Auto-fetch", color = GlassCyan, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Karaoke-style synced lyrics display
                    val lrcLines = remember(track.lrcLyrics) {
                        track.lrcLyrics?.let { LrcParser.parse(it) } ?: emptyList()
                    }
                    if (lrcLines.isNotEmpty()) {
                        val activeIdx = remember(playbackPosition, lrcLines) {
                            LrcParser.activeIndex(lrcLines, playbackPosition)
                        }
                        val lrcListState = rememberLazyListState()
                        LaunchedEffect(activeIdx) {
                            if (activeIdx >= 0) {
                                lrcListState.animateScrollToItem(
                                    (activeIdx - 1).coerceAtLeast(0)
                                )
                            }
                        }
                        LazyColumn(
                            state = lrcListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 160.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(lrcLines.size) { idx ->
                                val line = lrcLines[idx]
                                val isActive = idx == activeIdx
                                Text(
                                    text = line.text,
                                    color = if (isActive) GlassCyan else Color.White.copy(alpha = if (idx < activeIdx) 0.4f else 0.65f),
                                    fontSize = if (isActive) 16.sp else 13.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 2.dp)
                                )
                            }
                        }
                    } else {
                        // Plain text / edit fallback
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

        // Driving Mode full-screen overlay
        if (showDrivingMode) {
            DrivingModeScreen(
                viewModel = viewModel,
                onDismiss = { showDrivingMode = false }
            )
        }

        if (showStats) {
            ListeningStatsScreen(
                stats = listeningStats,
                onDismiss = { showStats = false },
                onRefresh = { viewModel.refreshListeningStats() }
            )
        }

        if (showDuplicates) {
            AlertDialog(
                onDismissRequest = { showDuplicates = false },
                title = { Text("Duplicate Tracks", color = Color.White) },
                text = {
                    if (duplicateGroups.isEmpty()) {
                        Text("No duplicates found.", color = Color.White.copy(alpha = 0.7f))
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(duplicateGroups) { group ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                        .padding(10.dp)
                                ) {
                                    Text("${group.title} - ${group.artist}", color = Color.White, fontSize = 13.sp)
                                    Text("${group.tracks.size} copies", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showDuplicates = false }) {
                        Text("Close", color = GlassCyan)
                    }
                },
                containerColor = Color(0xFF0F1026)
            )
        }

        if (showBackupDialog) {
            AlertDialog(
                onDismissRequest = { showBackupDialog = false },
                title = { Text("Backup / Restore", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = backupPayload,
                            onValueChange = { backupPayload = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 180.dp, max = 320.dp),
                            label = { Text("Backup JSON", color = Color.White.copy(alpha = 0.6f)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GlassCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Text(
                            "Tip: tap Backup first, then copy/share this JSON.",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp
                        )
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = {
                            viewModel.exportBackup { backupPayload = it }
                        }) {
                            Text("Backup", color = GlassCyan)
                        }
                        TextButton(onClick = {
                            viewModel.importBackup(backupPayload) { result ->
                                actionMessage = "Restored ${result.playlistsRestored} playlists, ${result.tracksPatched} tracks"
                            }
                        }) {
                            Text("Restore", color = GlassMagenta)
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBackupDialog = false }) {
                        Text("Close", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF0F1026)
            )
        }

        if (showM3uImportDialog) {
            AlertDialog(
                onDismissRequest = { showM3uImportDialog = false },
                title = { Text("M3U Import", color = Color.White) },
                text = {
                    OutlinedTextField(
                        value = m3uPayload,
                        onValueChange = { m3uPayload = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 300.dp),
                        label = { Text("Paste M3U/M3U8 text", color = Color.White.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GlassPurple,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.importM3u(m3uPayload) { summary ->
                            actionMessage = "Imported ${summary.addedCount} tracks to ${summary.playlistName} (${summary.unmatchedCount} unmatched)"
                            showM3uImportDialog = false
                            m3uPayload = ""
                        }
                    }) {
                        Text("Import", color = GlassCyan)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showM3uImportDialog = false }) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF0F1026)
            )
        }

        if (showLastFmDialog) {
            AlertDialog(
                onDismissRequest = { showLastFmDialog = false },
                title = { Text("Last.fm", color = Color.White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = lastFmUserInput,
                            onValueChange = { lastFmUserInput = it },
                            singleLine = true,
                            label = { Text("Username") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GlassCyan,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        OutlinedTextField(
                            value = lastFmPasswordInput,
                            onValueChange = { lastFmPasswordInput = it },
                            singleLine = true,
                            label = { Text("Password") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = GlassMagenta,
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        if (lastFmUsername.isNotBlank()) {
                            Text("Connected as $lastFmUsername", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.loginLastFm(lastFmUserInput, lastFmPasswordInput) { ok, msg ->
                            actionMessage = if (ok) "Last.fm connected" else msg
                            if (ok) {
                                showLastFmDialog = false
                                lastFmPasswordInput = ""
                            }
                        }
                    }) {
                        Text("Login", color = GlassCyan)
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (lastFmUsername.isNotBlank()) {
                            TextButton(onClick = {
                                viewModel.logoutLastFm()
                                actionMessage = "Last.fm disconnected"
                            }) {
                                Text("Logout", color = GlassMagenta)
                            }
                        }
                        TextButton(onClick = { showLastFmDialog = false }) {
                            Text("Close", color = Color.White.copy(alpha = 0.7f))
                        }
                    }
                },
                containerColor = Color(0xFF0F1026)
            )
        }

        if (actionMessage != null) {
            AlertDialog(
                onDismissRequest = { actionMessage = null },
                title = { Text("Info", color = Color.White) },
                text = { Text(actionMessage ?: "", color = Color.White.copy(alpha = 0.8f)) },
                confirmButton = {
                    TextButton(onClick = { actionMessage = null }) {
                        Text("OK", color = GlassCyan)
                    }
                },
                containerColor = Color(0xFF0F1026)
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
            GlassPlayPauseButton(
                isPlaying = isPlaying,
                onClick = { viewModel.togglePlayPause() },
                size = 48.dp,
                iconSize = 22.dp,
                contentDescription = "Play/Pause",
                testTag = "mini_play_pause_button"
            )

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
                        itemsIndexed(tracks, key = { _, t -> t.id }) { index, track ->
                            val isCurrentlyPlaying = currentTrack?.id == track.id
                            var showMenu by remember { mutableStateOf(false) }
                            var isDragging by remember { mutableStateOf(false) }
                            val rowHeight = 58f // approx dp per row
                            Box {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isDragging) GlassCyan.copy(alpha = 0.18f)
                                            else if (isCurrentlyPlaying) GlassCyan.copy(alpha = 0.12f)
                                            else Color.White.copy(alpha = 0.03f)
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (isDragging) GlassCyan.copy(alpha = 0.6f)
                                                    else if (isCurrentlyPlaying) GlassCyan.copy(alpha = 0.3f)
                                                    else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .combinedClickable(
                                            onClick = { viewModel.playTrack(track, tracks) },
                                            onLongClick = { showMenu = true }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Drag handle
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .pointerInput(index, tracks.size) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { isDragging = true },
                                                    onDragEnd = { isDragging = false },
                                                    onDragCancel = { isDragging = false },
                                                    onDrag = { change, dragAmount ->
                                                        change.consume()
                                                        val delta = dragAmount.y
                                                        val steps = (delta / rowHeight).toInt()
                                                        if (steps != 0) {
                                                            val newIndex = (index + steps).coerceIn(0, tracks.size - 1)
                                                            if (newIndex != index) {
                                                                viewModel.reorderQueue(index, newIndex)
                                                            }
                                                        }
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = Color.White.copy(alpha = 0.3f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(4.dp))

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

@Composable
private fun NowPlayingWaveform(viewModel: AudioViewModel) {
    val visualizerBars by viewModel.waveformAmplitudes.collectAsState()
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
}

@Composable
private fun NowPlayingSeekBar(
    viewModel: AudioViewModel,
    playbackDuration: Long
) {
    val playbackPosition by viewModel.playbackPosition.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        var sliderValueOverride by remember { mutableStateOf<Float?>(null) }
        val currentSliderValue = sliderValueOverride
            ?: (if (playbackDuration > 0) playbackPosition.toFloat() / playbackDuration else 0f)

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
                text = formatDuration(
                    if (sliderValueOverride != null) {
                        (sliderValueOverride!! * playbackDuration).toLong()
                    } else {
                        playbackPosition
                    }
                ),
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
}

// Utility to format duration in MM:SS
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
}

private fun moodEmoji(mood: String): String = when (mood) {
    "Chill" -> "😌"
    "Hype" -> "🔥"
    "Focus" -> "🎯"
    "Sad" -> "🌧️"
    "Party" -> "🎉"
    "Workout" -> "💪"
    else -> "🎵"
}

private fun nextMood(current: String): String {
    val options = listOf("", "Chill", "Hype", "Focus", "Sad", "Party", "Workout")
    val idx = options.indexOf(current).takeIf { it >= 0 } ?: 0
    return options[(idx + 1) % options.size]
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourcesDialog(
    viewModel: AudioViewModel,
    onDismiss: () -> Unit,
    onAddSource: () -> Unit
) {
    val importedFolders by viewModel.importedMusicFolders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.LibraryMusic, null, tint = GlassCyan)
                Spacer(Modifier.width(12.dp))
                Text("Music Sources", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "GlassPlayer scans these folders for audio files. You can add multiple folders from your device or SD card.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    if (importedFolders.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text("No folders added yet.", color = Color.White.copy(alpha = 0.4f))
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(importedFolders) { (uri, name) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Folder, null, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text(
                                            Uri.parse(uri).path ?: "External Storage", 
                                            color = Color.White.copy(alpha = 0.4f), 
                                            fontSize = 10.sp, 
                                            maxLines = 1, 
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = { viewModel.removeImportedFolder(uri, name) }) {
                                        Icon(Icons.Rounded.Delete, null, tint = Color(0xFFFF6B6B).copy(alpha = 0.8f), modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Button(
                    onClick = { onAddSource() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, GlassCyan.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Rounded.Add, null, tint = GlassCyan)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Folder", color = Color.White)
                }

                if (importedFolders.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { viewModel.scanPersistedMusicFolders(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GlassCyan, strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text("Rescan All Folders", color = GlassCyan)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done", color = GlassCyan) }
        },
        containerColor = Color(0xFF111329),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(28.dp))
    )
}
