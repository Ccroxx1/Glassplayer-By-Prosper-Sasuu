package com.example

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioViewModel(
    private val context: Context,
    private val repository: AudioRepository,
    private val engine: PlayerEngine,
    private val blacklistStore: BlacklistStore,
    private val sessionStore: PlaybackSessionStore
) : ViewModel() {

    private val tag = "AudioViewModel"
    private var mediaObserver: ContentObserver? = null
    private var rescanJob: Job? = null
    @Volatile private var pendingRescan = false
    @Volatile private var libraryWatching = false
    @Volatile private var sessionRestored = false
    private var positionPersistJob: Job? = null

    val blacklistedFolders: StateFlow<Set<String>> = blacklistStore.blacklistedFolders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val rawTracks: StateFlow<List<AudioTrackEntity>> = repository.allTracks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Library tracks with blacklisted folders removed (synth always kept). */
    val allTracks: StateFlow<List<AudioTrackEntity>> =
        combine(rawTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { track ->
                track.uri == AudioRepository.SYNTH_URI || track.folderName !in blocked
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** All scanned tracks including blacklisted — used by Folders / Blacklist management. */
    val allTracksIncludingBlacklisted: StateFlow<List<AudioTrackEntity>> = rawTracks

    val favoriteTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.favorites, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked || it.uri == AudioRepository.SYNTH_URI }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.recentTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked || it.uri == AudioRepository.SYNTH_URI }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyAddedTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.recentlyAddedTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked || it.uri == AudioRepository.SYNTH_URI }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val mostPlayedTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.mostPlayedTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked || it.uri == AudioRepository.SYNTH_URI }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<PlaylistEntity>> = repository.allPlaylists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val currentTrack = engine.currentTrack
    val isPlaying = engine.isPlaying
    val playbackPosition = engine.playbackPosition
    val playbackDuration = engine.playbackDuration
    val isShuffleEnabled = engine.isShuffleEnabled
    val repeatMode = engine.repeatMode
    val activeQueue = engine.activeQueue
    val waveformAmplitudes = engine.waveformAmplitudes
    val synthCutoff = engine.synthCutoff
    val synthSpeed = engine.synthSpeed
    val playbackSpeed = engine.playbackSpeed
    val volume = engine.volume
    val equalizerBands = engine.equalizerBands
    val equalizerEnabled = engine.equalizerEnabled
    val sleepTimerRemainingMs = engine.sleepTimerRemainingMs

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        engine.setLibraryProvider { allTracks.value }
        engine.onTrackStarted = { track ->
            viewModelScope.launch {
                repository.incrementPlayCount(track.id, System.currentTimeMillis())
            }
        }
        engine.onSessionChanged = {
            persistPlaybackSession()
        }
        viewModelScope.launch {
            try {
                repository.deleteNonDeviceTracks()
                // Drop built-in Neon Pulse so it never reappears in the library
                if (engine.currentTrack.value?.uri == AudioRepository.SYNTH_URI) {
                    engine.togglePlayPause(forcePause = true)
                }
                repository.removeSynthTrack()
                // Restore as soon as Room is ready (Flow may lag until collectors attach)
                if (!sessionRestored && engine.currentTrack.value == null) {
                    restorePlaybackSession(emptyList())
                }
            } catch (e: Exception) {
                Log.e(tag, "Init library failed", e)
            }
        }
        // Keep the play queue free of tracks from newly blacklisted folders
        viewModelScope.launch {
            blacklistedFolders.collect { blocked ->
                engine.pruneQueueForBlacklist(blocked)
            }
        }
        // Restore last session once the library has enough data to resolve tracks
        viewModelScope.launch {
            allTracks.collect { tracks ->
                if (!sessionRestored && tracks.isNotEmpty()) {
                    restorePlaybackSession(tracks)
                }
            }
        }
        // Throttled position persistence while a track is loaded
        viewModelScope.launch {
            engine.playbackPosition.collect {
                if (engine.currentTrack.value == null) return@collect
                positionPersistJob?.cancel()
                positionPersistJob = viewModelScope.launch {
                    delay(2_000L)
                    persistPlaybackSession()
                }
            }
        }
    }

    /** Flush the current engine session to disk (safe to call from Activity lifecycle). */
    fun persistPlaybackSession() {
        viewModelScope.launch {
            persistPlaybackSessionNow()
        }
    }

    private suspend fun persistPlaybackSessionNow() {
        try {
            val snapshot = engine.captureSession()
            if (snapshot == null) {
                // Only clear after we have successfully restored once — avoid wiping
                // a saved session during the brief window before restore runs.
                if (sessionRestored) {
                    sessionStore.clearSession()
                }
                return
            }
            sessionStore.saveSession(snapshot)
        } catch (e: Exception) {
            Log.w(tag, "Failed to persist playback session", e)
        }
    }

    private suspend fun restorePlaybackSession(library: List<AudioTrackEntity>) {
        if (sessionRestored) return
        // Process still has an active engine session (e.g. FGS survived) — keep it
        if (engine.currentTrack.value != null) {
            sessionRestored = true
            return
        }
        val saved = try {
            sessionStore.loadSession()
        } catch (e: Exception) {
            Log.w(tag, "Failed to load playback session", e)
            sessionRestored = true
            return
        }
        if (saved == null) {
            sessionRestored = true
            return
        }

        val byUri = library.associateBy { it.uri }
        // Room is authoritative across cold start; allTracks may still be catching up
        var track = repository.getTrackByUri(saved.trackUri) ?: byUri[saved.trackUri]
        if (track == null) {
            // Track permanently gone from the library database
            Log.i(tag, "Saved track no longer in library; clearing session")
            sessionStore.clearSession()
            sessionRestored = true
            return
        }
        // Prefer freshest library entity (artwork + tags) when available
        track = byUri[track.uri] ?: track

        val queueUris = saved.queueUris.ifEmpty { listOf(saved.trackUri) }
        var queue = repository.getTracksByUrisOrdered(queueUris)
        if (queue.isEmpty()) {
            queue = queueUris.mapNotNull { byUri[it] }
        }
        // Overlay fresher library copies when present
        if (byUri.isNotEmpty()) {
            queue = queue.map { byUri[it.uri] ?: it }
        }
        if (queue.none { it.uri == track.uri }) {
            queue = listOf(track) + queue
        }

        val blocked = blacklistedFolders.value
        fun allowed(t: AudioTrackEntity) =
            t.uri == AudioRepository.SYNTH_URI || t.folderName !in blocked
        queue = queue.filter(::allowed)
        if (!allowed(track)) {
            sessionStore.clearSession()
            sessionRestored = true
            return
        }
        if (queue.isEmpty()) queue = listOf(track)

        try {
            // After pause + exit, wasPlaying is false — restore paused at saved position.
            // Artwork comes back with the entity's albumArtUri for Coil / MediaSession.
            engine.restoreSession(
                track = track,
                queue = queue,
                positionMs = saved.positionMs,
                resumePlayback = saved.wasPlaying,
                shuffleEnabled = saved.shuffleEnabled,
                repeatMode = saved.repeatMode
            )
            Log.i(
                tag,
                "Restored session: ${track.title} @ ${saved.positionMs}ms " +
                    "(playing=${saved.wasPlaying}, queue=${queue.size})"
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to restore playback session", e)
        } finally {
            sessionRestored = true
        }
    }

    fun playTrack(track: AudioTrackEntity, customQueue: List<AudioTrackEntity>? = null) {
        engine.playTrack(track, customQueue)
    }

    fun playNext(track: AudioTrackEntity) {
        engine.playNext(track)
    }

    fun removeFromQueue(track: AudioTrackEntity) {
        engine.removeFromQueue(track)
    }

    fun togglePlayPause() = engine.togglePlayPause()
    fun nextTrack() = engine.nextTrack()
    fun previousTrack() = engine.previousTrack()
    fun seekTo(positionMs: Long) = engine.seekTo(positionMs)
    fun toggleShuffle() = engine.toggleShuffle()
    fun toggleLoop() = engine.toggleLoop()
    fun updateSynthCutoff(cutoff: Float) = engine.updateSynthCutoff(cutoff)
    fun updateSynthSpeed(speed: Float) = engine.updateSynthSpeed(speed)
    fun setPlaybackSpeed(speed: Float) = engine.setPlaybackSpeed(speed)
    fun setVolume(level: Float) = engine.setVolume(level)
    fun syncSystemVolume() = engine.syncSystemVolume()
    fun setEqualizerEnabled(enabled: Boolean) = engine.setEqualizerEnabled(enabled)
    fun setEqualizerBand(index: Int, normalized: Float) = engine.setEqualizerBand(index, normalized)
    fun setSleepTimer(durationMs: Long) = engine.setSleepTimer(durationMs)
    fun cancelSleepTimer() = engine.cancelSleepTimer()

    fun toggleFavorite(track: AudioTrackEntity) {
        viewModelScope.launch {
            val newValue = !track.isFavorite
            repository.toggleFavorite(track.id, newValue)
            engine.patchCurrentTrack { it.copy(isFavorite = newValue) }
        }
    }

    fun updateLyrics(trackId: Int, lyrics: String?) {
        viewModelScope.launch {
            repository.updateLyrics(trackId, lyrics)
            engine.patchCurrentTrack { if (it.id == trackId) it.copy(lyrics = lyrics) else it }
        }
    }

    fun updateTrackTags(track: AudioTrackEntity, title: String, artist: String, album: String) {
        viewModelScope.launch {
            val cleanTitle = title.trim().ifBlank { track.title }
            val cleanArtist = artist.trim().ifBlank { track.artist }
            val cleanAlbum = album.trim().ifBlank { track.album }
            repository.updateUserTags(track.id, cleanTitle, cleanArtist, cleanAlbum)
            engine.patchCurrentTrack {
                if (it.id == track.id) it.copy(title = cleanTitle, artist = cleanArtist, album = cleanAlbum)
                else it
            }
            withContext(Dispatchers.IO) {
                writeTagsToMediaStore(track.uri, cleanTitle, cleanArtist, cleanAlbum)
            }
        }
    }

    private fun writeTagsToMediaStore(uriString: String, title: String, artist: String, album: String) {
        if (uriString.startsWith("procedural://")) return
        try {
            val uri = Uri.parse(uriString)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.Audio.Media.ARTIST, artist)
                put(MediaStore.Audio.Media.ALBUM, album)
            }
            context.contentResolver.update(uri, values, null, null)
        } catch (e: Exception) {
            Log.w(tag, "Could not write MediaStore tags for $uriString", e)
        }
    }

    fun setFolderBlacklisted(folderName: String, blacklisted: Boolean) {
        viewModelScope.launch {
            blacklistStore.setBlacklisted(folderName, blacklisted)
            // Prune immediately (DataStore emit can lag a frame)
            val blocked = if (blacklisted) {
                blacklistedFolders.value + folderName
            } else {
                blacklistedFolders.value - folderName
            }
            engine.pruneQueueForBlacklist(blocked)
        }
    }

    fun clearBlacklist() {
        viewModelScope.launch {
            blacklistStore.clearAll()
            engine.pruneQueueForBlacklist(emptySet())
        }
    }

    fun startWatchingLibrary() {
        if (libraryWatching || !hasAudioPermission()) return
        libraryWatching = true
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                onChange(selfChange, null)
            }

            override fun onChange(selfChange: Boolean, uri: Uri?) {
                scheduleLibraryRescan(debounceMs = 1_500L)
            }
        }
        mediaObserver = observer
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.i(tag, "Watching MediaStore for new audio")
        } catch (e: Exception) {
            Log.e(tag, "Failed to register media observer", e)
            libraryWatching = false
            mediaObserver = null
        }
    }

    fun stopWatchingLibrary() {
        mediaObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Exception) {
            }
        }
        mediaObserver = null
        libraryWatching = false
        rescanJob?.cancel()
        rescanJob = null
    }

    fun onAppForegrounded() {
        if (!hasAudioPermission()) return
        startWatchingLibrary()
        // Don't hammer MediaStore on every resume while music is playing
        if (!engine.isPlaying.value) {
            scheduleLibraryRescan(debounceMs = 800L)
        }
    }

    fun reassertPlaybackIfNeeded() {
        engine.reassertPlaybackIfNeeded()
    }

    fun scheduleLibraryRescan(debounceMs: Long = 1_000L) {
        if (!hasAudioPermission()) return
        rescanJob?.cancel()
        rescanJob = viewModelScope.launch {
            delay(debounceMs)
            scanDeviceAudio(context)
        }
    }

    private fun hasAudioPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun scanDeviceAudio(context: Context = this.context) {
        if (!hasAudioPermission()) return
        if (_isScanning.value) {
            pendingRescan = true
            return
        }
        viewModelScope.launch {
            _isScanning.value = true
            try {
                withContext(Dispatchers.IO) {
                    val projection = mutableListOf(
                        MediaStore.Audio.Media._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ALBUM_ID,
                        MediaStore.Audio.Media.DATE_ADDED,
                        MediaStore.Audio.Media.DATE_MODIFIED,
                        MediaStore.Audio.Media.YEAR
                    )

                    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                    val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                    val attributionContext =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            context.createAttributionContext("audio_player")
                        } else {
                            context
                        }

                    val cursor = attributionContext.contentResolver.query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        projection.toTypedArray(),
                        selection,
                        null,
                        sortOrder
                    )

                    cursor?.use { c ->
                        val idColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val durationColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val dataColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                        val albumColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                        val albumIdColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                        val dateAddedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                        val dateModifiedColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                        val yearColumn = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)

                        val scanned = ArrayList<AudioTrackEntity>(c.count.coerceAtLeast(0))
                        while (c.moveToNext()) {
                            val id = c.getLong(idColumn)
                            val title = c.getString(titleColumn) ?: "Unknown Track"
                            val artist = c.getString(artistColumn) ?: "Unknown Artist"
                            val duration = c.getLong(durationColumn)
                            val dataPath = c.getString(dataColumn) ?: ""
                            val albumName = c.getString(albumColumn) ?: "Unknown Album"
                            val albumId = c.getLong(albumIdColumn)
                            val dateAddedSec = c.getLong(dateAddedColumn)
                            val dateModifiedSec = c.getLong(dateModifiedColumn)
                            val year = c.getInt(yearColumn)
                            val contentUri = ContentUris.withAppendedId(
                                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                id
                            ).toString()

                            val albumArtUri = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()

                            val folderName = if (dataPath.isNotEmpty()) {
                                try {
                                    java.io.File(dataPath).parentFile?.name ?: "Music"
                                } catch (_: Exception) {
                                    "Music"
                                }
                            } else {
                                "Music"
                            }

                            scanned.add(
                                AudioTrackEntity(
                                    uri = contentUri,
                                    title = title,
                                    artist = artist,
                                    durationMs = duration,
                                    dateAdded = dateAddedSec * 1000L,
                                    dateModified = dateModifiedSec * 1000L,
                                    year = year,
                                    category = "My Device",
                                    album = albumName,
                                    folderName = folderName,
                                    albumArtUri = albumArtUri
                                )
                            )
                        }
                        repository.insertTracks(scanned)
                    }
                    repository.removeSynthTrack()
                }
            } catch (e: Exception) {
                Log.e(tag, "Scan failed", e)
            } finally {
                _isScanning.value = false
                if (pendingRescan) {
                    pendingRescan = false
                    scanDeviceAudio(context)
                }
            }
        }
    }

    fun addLocalTrack(uriString: String, title: String, artist: String, durationMs: Long) {
        viewModelScope.launch {
            var resolvedDuration = durationMs
            var resolvedTitle = title
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(context, Uri.parse(uriString))
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0 }
                        ?.let { resolvedDuration = it }
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { resolvedTitle = it }
                    retriever.release()
                } catch (_: Exception) {
                }
            }
            repository.insertTrack(
                AudioTrackEntity(
                    uri = uriString,
                    title = resolvedTitle,
                    artist = artist,
                    durationMs = resolvedDuration,
                    dateAdded = System.currentTimeMillis(),
                    category = "My Device",
                    album = "Local Imports",
                    folderName = "Downloads",
                    albumArtUri = uriString
                )
            )
        }
    }

    fun removeTrackFromDeviceCategory(track: AudioTrackEntity) {
        viewModelScope.launch {
            if (track.uri == AudioRepository.SYNTH_URI) return@launch
            repository.deleteTrackByUri(track.uri)
            if (engine.currentTrack.value?.uri == track.uri) {
                engine.togglePlayPause(forcePause = true)
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { repository.createPlaylist(name) }
    }

    fun deletePlaylist(playlistId: Int) {
        viewModelScope.launch { repository.deletePlaylist(playlistId) }
    }

    fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch { repository.addTrackToPlaylist(playlistId, trackId) }
    }

    fun removeTrackFromPlaylist(playlistId: Int, trackId: Int) {
        viewModelScope.launch { repository.removeTrackFromPlaylist(playlistId, trackId) }
    }

    fun getTracksInPlaylist(playlistId: Int): Flow<List<AudioTrackEntity>> {
        return repository.getTracksInPlaylist(playlistId)
    }

    fun playlistShareText(name: String, tracks: List<AudioTrackEntity>): String {
        val body = tracks.mapIndexed { index, t ->
            "${index + 1}. ${t.title} — ${t.artist}"
        }.joinToString("\n")
        return "GlassPlayer playlist: $name\n\n$body"
    }

    override fun onCleared() {
        stopWatchingLibrary()
        engine.onSessionChanged = null
        // Best-effort flush; ViewModelScope is cancelling so use a blocking-friendly path
        // via the shared engine snapshot — PlaybackService also saves on task removed.
        super.onCleared()
    }
}

class AudioViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AudioViewModel::class.java)) {
            val database = AudioDatabase.getDatabase(context)
            val repository = AudioRepository(database.audioDao(), database)
            val engine = PlayerEngine.get(context.applicationContext)
            val blacklistStore = BlacklistStore(context.applicationContext)
            val sessionStore = PlaybackSessionStore(context.applicationContext)
            @Suppress("UNCHECKED_CAST")
            return AudioViewModel(
                context.applicationContext,
                repository,
                engine,
                blacklistStore,
                sessionStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
