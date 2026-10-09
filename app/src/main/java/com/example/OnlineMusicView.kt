package com.example

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val YoutubeRed = Color(0xFFFF0033)
private val YoutubeDarkRed = Color(0xFFCC0000)

data class OnlineAlbum(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val tracks: List<OnlineTrack>
)

data class OnlineArtist(
    val name: String,
    val avatarUrl: String,
    val tracks: List<OnlineTrack>
)

@Composable
fun OnlineMusicView(
    viewModel: AudioViewModel,
    strings: LanguageStrings,
    modifier: Modifier = Modifier,
    onTrackSelected: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var searchQuery by remember { mutableStateOf("") }
    var activeCategory by remember { mutableStateOf("Trending") }
    var subTab by remember { mutableStateOf("Songs") } // "Songs", "Albums", "Artists", "Playlists", "Recent"
    var trackList by remember { mutableStateOf<List<OnlineTrack>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }

    // Multi-select state
    var isSelectMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }

    // Playlist dialog & Link Import states
    var trackForPlaylist by remember { mutableStateOf<OnlineTrack?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var activeImportedPlaylist by remember { mutableStateOf<OnlinePlaylistResult?>(null) }
    var urlDetectionLabel by remember { mutableStateOf<String?>(null) }

    val currentTrackState by viewModel.currentTrack.collectAsState()
    val isPlayingState by viewModel.isPlaying.collectAsState()
    val recentYouTubeTracks by viewModel.recentYouTubeTracks.collectAsState()
    val allPlaylists by viewModel.allPlaylists.collectAsState()

    fun playTrack(track: OnlineTrack) {
        val trackIdx = trackList.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        val currentEntity = track.toAudioTrackEntity(trackIdx)
        val queueEntities = trackList.mapIndexed { idx, item -> item.toAudioTrackEntity(idx) }
        viewModel.playTrack(currentEntity, queueEntities)
        onTrackSelected()
    }

    fun playEntityTrack(track: AudioTrackEntity) {
        val queue = recentYouTubeTracks.ifEmpty { listOf(track) }
        viewModel.playTrack(track, queue)
        onTrackSelected()
    }

    fun playAllSongs() {
        if (trackList.isEmpty()) return
        val currentEntity = trackList.first().toAudioTrackEntity(0)
        val queueEntities = trackList.mapIndexed { idx, item -> item.toAudioTrackEntity(idx) }
        viewModel.playTrack(currentEntity, queueEntities)
        onTrackSelected()
    }

    fun playSelectedSongs() {
        val selected = trackList.filter { it.id in selectedIds }
        if (selected.isEmpty()) return
        val currentEntity = selected.first().toAudioTrackEntity(0)
        val queueEntities = selected.mapIndexed { idx, item -> item.toAudioTrackEntity(idx) }
        viewModel.playTrack(currentEntity, queueEntities)
        isSelectMode = false
        selectedIds = emptySet()
        onTrackSelected()
    }

    fun playTrackList(list: List<OnlineTrack>) {
        if (list.isEmpty()) return
        val currentEntity = list.first().toAudioTrackEntity(0)
        val queueEntities = list.mapIndexed { idx, item -> item.toAudioTrackEntity(idx) }
        viewModel.playTrack(currentEntity, queueEntities)
        onTrackSelected()
    }

    fun playUserPlaylist(playlistId: Int) {
        scope.launch {
            val tracks = viewModel.getTracksInPlaylist(playlistId).first()
            if (tracks.isNotEmpty()) {
                viewModel.playTrack(tracks.first(), tracks)
                onTrackSelected()
            } else {
                Toast.makeText(context, "Playlist is empty", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun processSearchOrImport(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        isLoading = true
        activeImportedPlaylist = null
        urlDetectionLabel = null

        scope.launch {
            when (val parsed = YouTubeUrlParser.parse(trimmed)) {
                is YouTubeUrlResult.SingleVideo -> {
                    urlDetectionLabel = "YouTube Song Link"
                    val singleTrack = OnlineMusicService.fetchTrackByVideoId(parsed.videoId)
                    if (singleTrack != null) {
                        trackList = listOf(singleTrack)
                        subTab = "Songs"
                    } else {
                        Toast.makeText(context, "Unable to resolve YouTube song link", Toast.LENGTH_SHORT).show()
                        trackList = emptyList()
                    }
                }
                is YouTubeUrlResult.Playlist -> {
                    urlDetectionLabel = "YouTube Playlist Link"
                    val plResult = OnlineMusicService.fetchPlaylistById(parsed.playlistId)
                    if (plResult != null && plResult.tracks.isNotEmpty()) {
                        activeImportedPlaylist = plResult
                        trackList = plResult.tracks
                        subTab = "Songs"
                    } else {
                        Toast.makeText(context, "Unable to load playlist or playlist is private", Toast.LENGTH_SHORT).show()
                        trackList = emptyList()
                    }
                }
                is YouTubeUrlResult.NotAYouTubeUrl -> {
                    trackList = OnlineMusicService.fetchTracks(trimmed)
                }
            }
            isLoading = false
        }
    }

    fun selectCategory(cat: String) {
        activeCategory = cat
        activeImportedPlaylist = null
        urlDetectionLabel = null
        isLoading = true
        scope.launch {
            trackList = OnlineMusicService.fetchTracks(cat)
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (trackList.isEmpty()) {
            isLoading = true
            trackList = OnlineMusicService.fetchTracks("Trending")
            isLoading = false
        }
    }

    // Derive Albums & Artists from trackList
    val albumList = remember(trackList) {
        trackList.groupBy { it.artist }.map { (artist, tracks) ->
            OnlineAlbum(
                title = "$artist Hits",
                artist = artist,
                coverUrl = tracks.firstOrNull()?.thumbnail.orEmpty(),
                tracks = tracks
            )
        }
    }

    val artistList = remember(trackList) {
        trackList.groupBy { it.artist }.map { (artist, tracks) ->
            OnlineArtist(
                name = artist,
                avatarUrl = tracks.firstOrNull()?.thumbnail.orEmpty(),
                tracks = tracks
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // Header with YouTube branding badge
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(YoutubeRed, YoutubeDarkRed)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            "YouTube Music",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            "In-App Stream • Paste Links or Search",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(YoutubeRed.copy(alpha = 0.18f))
                        .border(1.dp, YoutubeRed.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        "ONLINE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = YoutubeRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar & URL Auto-Detector
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Rounded.Search,
                        contentDescription = null,
                        tint = YoutubeRed,
                        modifier = Modifier.size(20.dp)
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                "Search songs or paste YouTube / YouTube Music link...",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 13.sp
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            processSearchOrImport(searchQuery)
                            focusManager.clearFocus()
                        }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = YoutubeRed,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = {
                            searchQuery = ""
                            activeImportedPlaylist = null
                            urlDetectionLabel = null
                            selectCategory(activeCategory)
                        }) {
                            Icon(
                                Icons.Rounded.Close,
                                contentDescription = "Clear",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // URL Detection Tag Helper
                val parsedType = YouTubeUrlParser.parse(searchQuery)
                if (parsedType !is YouTubeUrlResult.NotAYouTubeUrl) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(YoutubeRed.copy(alpha = 0.2f))
                            .border(1.dp, YoutubeRed.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                            .clickable {
                                processSearchOrImport(searchQuery)
                                focusManager.clearFocus()
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Link,
                            contentDescription = null,
                            tint = YoutubeRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = when (parsedType) {
                                is YouTubeUrlResult.Playlist -> "YouTube Playlist Link Detected • Tap Search or Enter to Import"
                                is YouTubeUrlResult.SingleVideo -> "YouTube Song Link Detected • Tap Search or Enter to Resolve"
                                else -> "YouTube Link Detected"
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Sub-Navigation Tabs: Songs | Albums | Artists | Playlists | Recent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Songs", "Albums", "Artists", "Playlists", "Recent").forEach { tab ->
                    val isTabSelected = subTab == tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isTabSelected) YoutubeRed
                                else Color.Transparent
                            )
                            .clickable {
                                subTab = tab
                                isSelectMode = false
                            }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            fontSize = 13.sp,
                            fontWeight = if (isTabSelected) FontWeight.Bold else FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Genre Categories Pill Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OnlineMusicService.GENRE_CATEGORIES.forEach { cat ->
                    val isSelected = activeCategory == cat
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isSelected) YoutubeRed.copy(alpha = 0.35f)
                                else Color.White.copy(alpha = 0.07f)
                            )
                            .border(
                                1.dp,
                                if (isSelected) YoutubeRed.copy(alpha = 0.85f)
                                else Color.White.copy(alpha = 0.12f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectCategory(cat) }
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            cat,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Imported Playlist Hero Banner
            if (activeImportedPlaylist != null) {
                val pl = activeImportedPlaylist!!
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(YoutubeRed.copy(alpha = 0.35f), Color(0xFF131524))
                            )
                        )
                        .border(1.dp, YoutubeRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.DarkGray)
                            ) {
                                if (pl.thumbnail.isNotBlank()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(pl.thumbnail)
                                            .transformations(CleanArtworkTransformation())
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                                        contentDescription = null,
                                        tint = YoutubeRed,
                                        modifier = Modifier.size(30.dp).align(Alignment.Center)
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pl.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${pl.uploader} • ${pl.tracks.size} tracks",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "YouTube Playlist Link",
                                    fontSize = 11.sp,
                                    color = YoutubeRed,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                viewModel.importYouTubePlaylist(pl) { newPlId, count ->
                                    Toast.makeText(
                                        context,
                                        "Saved playlist '${pl.title}' with $count songs",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.PlaylistAdd,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Save Playlist", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Tab Content Rendering
            when (subTab) {
                "Songs" -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = { playAllSongs() },
                                colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                enabled = trackList.isNotEmpty()
                            ) {
                                Icon(
                                    Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = {
                                    isSelectMode = !isSelectMode
                                    if (!isSelectMode) selectedIds = emptySet()
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelectMode) YoutubeRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f)
                                ),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    if (isSelectMode) Icons.Rounded.Close else Icons.Rounded.SelectAll,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isSelectMode) "Cancel" else "Select Songs",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        if (isSelectMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "${selectedIds.size} selected",
                                    fontSize = 12.sp,
                                    color = YoutubeRed,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Button(
                                    onClick = {
                                        if (selectedIds.size == trackList.size) {
                                            selectedIds = emptySet()
                                        } else {
                                            selectedIds = trackList.map { it.id }.toSet()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                    shape = RoundedCornerShape(16.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        if (selectedIds.size == trackList.size) "Deselect All" else "Select All",
                                        fontSize = 11.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }

                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YoutubeRed, modifier = Modifier.size(36.dp))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            itemsIndexed(trackList, key = { index, item -> "${item.id}_$index" }) { _, track ->
                                val isCurrent = currentTrackState?.isSameTrack(track.toAudioTrackEntity()) == true && isPlayingState
                                val isSelected = track.id in selectedIds
                                YoutubeTrackRow(
                                    track = track,
                                    isCurrent = isCurrent,
                                    isResolving = resolvingTrackId == track.id,
                                    isSelectMode = isSelectMode,
                                    isSelected = isSelected,
                                    onSelectToggle = {
                                        selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                    },
                                    onAddToPlaylist = { trackForPlaylist = track },
                                    onClick = {
                                        if (isSelectMode) {
                                            selectedIds = if (isSelected) selectedIds - track.id else selectedIds + track.id
                                        } else {
                                            playTrack(track)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                "Albums" -> {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YoutubeRed, modifier = Modifier.size(36.dp))
                        }
                    } else if (albumList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No albums found", color = Color.White.copy(alpha = 0.6f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            itemsIndexed(albumList, key = { index, alb -> "${alb.title}_$index" }) { _, album ->
                                AlbumCardRow(
                                    album = album,
                                    onPlayAlbum = { playTrackList(album.tracks) }
                                )
                            }
                        }
                    }
                }

                "Artists" -> {
                    if (isLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = YoutubeRed, modifier = Modifier.size(36.dp))
                        }
                    } else if (artistList.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No artists found", color = Color.White.copy(alpha = 0.6f))
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            itemsIndexed(artistList, key = { index, art -> "${art.name}_$index" }) { _, artist ->
                                ArtistCardRow(
                                    artist = artist,
                                    onPlayArtist = { playTrackList(artist.tracks) }
                                )
                            }
                        }
                    }
                }

                "Playlists" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Your Playlists (${allPlaylists.size})",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Button(
                                    onClick = { showCreatePlaylistDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                                    shape = RoundedCornerShape(18.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Create Playlist", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        if (allPlaylists.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Rounded.QueueMusic,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.4f),
                                            modifier = Modifier.size(42.dp)
                                        )
                                        Text(
                                            "No custom playlists yet",
                                            fontSize = 14.sp,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            "Tap 'Create Playlist' above or paste YouTube playlist links!",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                    }
                                }
                            }
                        } else {
                            itemsIndexed(allPlaylists, key = { _, pl -> pl.id }) { _, userPl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(YoutubeRed.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Rounded.QueueMusic,
                                                contentDescription = null,
                                                tint = YoutubeRed,
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Column {
                                            Text(userPl.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                            Text("Custom Playlist", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Button(
                                            onClick = { playUserPlaylist(userPl.id) },
                                            colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        IconButton(onClick = { viewModel.deletePlaylist(userPl.id) }) {
                                            Icon(
                                                Icons.Rounded.Delete,
                                                contentDescription = "Delete",
                                                tint = Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "Recent" -> {
                    if (recentYouTubeTracks.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Rounded.History,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(42.dp)
                                )
                                Text(
                                    "No recently played YouTube tracks yet",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    "Search and play songs to see your history here!",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 90.dp)
                        ) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Recently Played (${recentYouTubeTracks.size})",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { viewModel.clearRecentYouTubeHistory() },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                                        ) {
                                            Text("Clear Recent", fontSize = 11.sp, color = Color.White.copy(alpha = 0.8f))
                                        }

                                        Button(
                                            onClick = {
                                                val first = recentYouTubeTracks.first()
                                                playEntityTrack(first)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
                                            shape = RoundedCornerShape(16.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Play Recent Queue", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }

                            itemsIndexed(recentYouTubeTracks, key = { index, t -> "${t.id}_$index" }) { _, entityTrack ->
                                val isCurrent = entityTrack.isSameTrack(currentTrackState) && isPlayingState
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isCurrent) YoutubeRed.copy(alpha = 0.22f)
                                            else Color.White.copy(alpha = 0.04f)
                                        )
                                        .border(
                                            1.dp,
                                            if (isCurrent) YoutubeRed.copy(alpha = 0.7f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable { playEntityTrack(entityTrack) }
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color.DarkGray)
                                    ) {
                                        if (!entityTrack.albumArtUri.isNullOrBlank()) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(entityTrack.albumArtUri)
                                                    .transformations(CleanArtworkTransformation())
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        } else {
                                            Icon(
                                                Icons.Rounded.GraphicEq,
                                                contentDescription = null,
                                                tint = YoutubeRed,
                                                modifier = Modifier.size(24.dp).align(Alignment.Center)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            entityTrack.title,
                                            fontSize = 14.sp,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                            color = if (isCurrent) YoutubeRed else Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            entityTrack.artist,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    IconButton(
                                        onClick = { playEntityTrack(entityTrack) },
                                        modifier = Modifier.size(30.dp)
                                    ) {
                                        Icon(
                                            if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                                            contentDescription = "Play",
                                            tint = if (isCurrent) YoutubeRed else Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Action Bar when selecting songs
        if (isSelectMode && selectedIds.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(YoutubeRed)
                    .clickable { playSelectedSongs() }
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White)
                    Text(
                        "Play Selected (${selectedIds.size})",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Dialog for adding YouTube track to playlist
        if (trackForPlaylist != null) {
            val targetTrack = trackForPlaylist!!.toAudioTrackEntity()
            AddTrackToPlaylistDialog(
                track = targetTrack,
                playlists = allPlaylists,
                onDismiss = { trackForPlaylist = null },
                onPlaylistSelected = { playlistId ->
                    viewModel.addTrackEntityToPlaylist(playlistId, targetTrack) { added ->
                        Toast.makeText(
                            context,
                            if (added == PlaylistAddResult.ADDED) "Added to playlist" else "Already in playlist",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    trackForPlaylist = null
                },
                onCreatePlaylistInline = { name ->
                    viewModel.createPlaylistWithTrack(name, targetTrack) { newId, addResult ->
                        Toast.makeText(
                            context,
                            "Created \"$name\" and added song",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    trackForPlaylist = null
                }
            )
        }

        // Dialog for creating a new playlist
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("Create New Playlist", color = Color.White, fontWeight = FontWeight.Bold) },
                text = {
                    TextField(
                        value = newPlaylistName,
                        onValueChange = { newPlaylistName = it },
                        placeholder = { Text("Playlist name...", color = Color.White.copy(alpha = 0.4f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.05f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = YoutubeRed
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistName.isNotBlank()) {
                                viewModel.createPlaylist(newPlaylistName.trim())
                                newPlaylistName = ""
                                showCreatePlaylistDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed)
                    ) {
                        Text("Create", color = Color.White)
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showCreatePlaylistDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent)
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                },
                containerColor = Color(0xFF131524)
            )
        }
    }
}

@Composable
private fun YoutubeTrackRow(
    track: OnlineTrack,
    isCurrent: Boolean,
    isResolving: Boolean,
    isSelectMode: Boolean = false,
    isSelected: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isCurrent) YoutubeRed.copy(alpha = 0.22f)
                else if (isSelected) YoutubeRed.copy(alpha = 0.15f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                1.dp,
                if (isCurrent) YoutubeRed.copy(alpha = 0.7f)
                else if (isSelected) YoutubeRed.copy(alpha = 0.5f)
                else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSelectMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onSelectToggle() },
                colors = CheckboxDefaults.colors(
                    checkedColor = YoutubeRed,
                    uncheckedColor = Color.White.copy(alpha = 0.5f)
                ),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.DarkGray)
        ) {
            if (track.thumbnail.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(track.thumbnail)
                        .transformations(CleanArtworkTransformation())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF282828)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = YoutubeRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = YoutubeRed,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title & Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                track.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrent) YoutubeRed else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artist,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Add to Playlist Button
        IconButton(
            onClick = onAddToPlaylist,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.AutoMirrored.Rounded.PlaylistAdd,
                contentDescription = "Add to playlist",
                tint = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Duration
        if (isResolving) {
            CircularProgressIndicator(
                color = YoutubeRed,
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Text(
                track.durationText,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Play Button
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                if (isCurrent) Icons.Rounded.GraphicEq else Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = if (isCurrent) YoutubeRed else Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AlbumCardRow(
    album: OnlineAlbum,
    onPlayAlbum: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .clickable { onPlayAlbum() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.DarkGray)
        ) {
            if (album.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(album.coverUrl)
                        .transformations(CleanArtworkTransformation())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.Album,
                    contentDescription = null,
                    tint = YoutubeRed,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                album.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                album.artist,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${album.tracks.size} tracks • Album",
                fontSize = 11.sp,
                color = YoutubeRed,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onPlayAlbum,
            colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Play", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ArtistCardRow(
    artist: OnlineArtist,
    onPlayArtist: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
            .clickable { onPlayArtist() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(Color.DarkGray)
        ) {
            if (artist.avatarUrl.isNotBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(artist.avatarUrl)
                        .transformations(CleanArtworkTransformation())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    Icons.Rounded.Person,
                    contentDescription = null,
                    tint = YoutubeRed,
                    modifier = Modifier
                        .size(32.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                artist.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${artist.tracks.size} top tracks • Artist",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Button(
            onClick = onPlayArtist,
            colors = ButtonDefaults.buttonColors(containerColor = YoutubeRed),
            shape = RoundedCornerShape(16.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Play All", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}
