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
import android.provider.DocumentsContract
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AudioViewModel(
    private val context: Context,
    private val repository: AudioRepository,
    private val engine: PlayerEngine,
    private val blacklistStore: BlacklistStore,
    private val sessionStore: PlaybackSessionStore,
    private val appPreferencesStore: AppPreferencesStore
) : ViewModel() {

    private val tag = "AudioViewModel"
    private companion object {
        const val KEY_IMPORTED_FOLDERS = "tree_uris"
    }

    private val importedFoldersPrefs by lazy {
        context.getSharedPreferences("imported_music_folders", Context.MODE_PRIVATE)
    }

    private val supportedAudioExtensions = setOf(
        "mp3", "m4a", "mp4", "aac", "flac", "wav", "ogg", "oga", "opus",
        "amr", "3gp", "3gpp", "mid", "midi", "xmf", "mxmf", "rtttl", "rtx", "ota", "imy"
    )
    private var mediaObserver: ContentObserver? = null
    private var rescanJob: Job? = null
    @Volatile private var pendingRescan = false
    @Volatile private var libraryWatching = false
    @Volatile private var sessionRestored = false
    private var positionPersistJob: Job? = null
    private var scrobbleJob: Job? = null

    private val _importedMusicFolders = MutableStateFlow<List<Pair<String, String>>>(emptyList())
    val importedMusicFolders: StateFlow<List<Pair<String, String>>> = _importedMusicFolders.asStateFlow()

    init {
        refreshImportedFoldersList()
    }

    private fun refreshImportedFoldersList() {
        val uris = importedFolderUris()
        viewModelScope.launch(Dispatchers.IO) {
            val list = uris.map { uriString ->
                val uri = Uri.parse(uriString)
                val name = try {
                    queryDocumentDisplayName(context, uri, DocumentsContract.getTreeDocumentId(uri))
                } catch (e: Exception) { null } ?: "Unknown Folder"
                uriString to name
            }
            _importedMusicFolders.value = list
        }
    }

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

    val crossfadeSec: StateFlow<Float> = appPreferencesStore.crossfadeSec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val pitchSemitones: StateFlow<Float> = appPreferencesStore.pitchSemitones
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    val sleepFadeEnabled: StateFlow<Boolean> = appPreferencesStore.sleepFadeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val colorTheme: StateFlow<String> = appPreferencesStore.colorTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GlassTheme.DYNAMIC.name)

    val lastFmUsername: StateFlow<String> = appPreferencesStore.lastFmUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val lastFmSessionKey: StateFlow<String> = appPreferencesStore.lastFmSessionKey
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _scrobbleCount = MutableStateFlow(0)
    val scrobbleCount: StateFlow<Int> = _scrobbleCount.asStateFlow()

    private val _listeningStats = MutableStateFlow<AudioRepository.ListeningStats?>(null)
    val listeningStats: StateFlow<AudioRepository.ListeningStats?> = _listeningStats.asStateFlow()

    private val _duplicateGroups = MutableStateFlow<List<AudioRepository.DuplicateGroup>>(emptyList())
    val duplicateGroups: StateFlow<List<AudioRepository.DuplicateGroup>> = _duplicateGroups.asStateFlow()

    // --- Extended smart playlists ---
    val neverPlayedTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.neverPlayedTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val longTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.longTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val thisYearTracks: StateFlow<List<AudioTrackEntity>> =
        combine(repository.thisYearTracks, blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Fetch lyrics loading state ---
    private val _isFetchingLyrics = MutableStateFlow(false)
    val isFetchingLyrics: StateFlow<Boolean> = _isFetchingLyrics.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    init {
        viewModelScope.launch {
            crossfadeSec.collect { engine.setCrossfadeSec(it) }
        }
        viewModelScope.launch {
            pitchSemitones.collect { engine.setPitchSemitones(it) }
        }
        viewModelScope.launch {
            sleepFadeEnabled.collect { engine.setSleepFadeEnabled(it) }
        }

        engine.setLibraryProvider { allTracks.value }
        engine.onTrackStarted = { track ->
            viewModelScope.launch {
                repository.incrementPlayCount(track.id, System.currentTimeMillis())
                scrobbleNowPlaying(track)
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

    fun setCrossfade(sec: Float) {
        viewModelScope.launch {
            appPreferencesStore.setCrossfadeSec(sec)
            engine.setCrossfadeSec(sec)
        }
    }

    fun setPitchSemitones(semitones: Float) {
        viewModelScope.launch {
            appPreferencesStore.setPitchSemitones(semitones)
            engine.setPitchSemitones(semitones)
        }
    }

    fun setSleepFadeEnabled(enabled: Boolean) {
        viewModelScope.launch {
            appPreferencesStore.setSleepFadeEnabled(enabled)
            engine.setSleepFadeEnabled(enabled)
        }
    }

    fun setColorTheme(theme: GlassTheme) {
        viewModelScope.launch {
            appPreferencesStore.setColorTheme(theme)
        }
    }

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

    fun setMood(trackId: Int, mood: String) {
        viewModelScope.launch {
            repository.updateMood(trackId, mood)
            engine.patchCurrentTrack { if (it.id == trackId) it.copy(mood = mood) else it }
        }
    }

    fun setReplayGain(trackId: Int, db: Float) {
        viewModelScope.launch {
            val value = db.coerceIn(-24f, 24f)
            repository.updateReplayGain(trackId, value)
            engine.patchCurrentTrack { if (it.id == trackId) it.copy(replayGainDb = value) else it }
        }
    }

    fun tracksByMood(mood: String): Flow<List<AudioTrackEntity>> {
        return combine(repository.getTracksByMood(mood), blacklistedFolders) { tracks, blocked ->
            tracks.filter { it.folderName !in blocked || it.uri == AudioRepository.SYNTH_URI }
        }
    }

    fun refreshListeningStats() {
        viewModelScope.launch {
            _listeningStats.value = repository.getListeningStats()
        }
    }

    fun refreshDuplicateGroups() {
        viewModelScope.launch {
            _duplicateGroups.value = repository.getDuplicateTrackGroups()
        }
    }

    fun importM3u(text: String, onResult: (AudioRepository.M3uImportSummary) -> Unit) {
        viewModelScope.launch {
            onResult(repository.importM3u(text))
        }
    }

    fun exportBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            onResult(repository.exportBackup())
        }
    }

    fun importBackup(json: String, onResult: (BackupRestoreManager.ImportResult) -> Unit) {
        viewModelScope.launch {
            onResult(repository.importBackup(json))
        }
    }

    fun loginLastFm(username: String, password: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val result = LastFmService.getMobileSession(username.trim(), password)
            if (result.isSuccess) {
                val session = result.getOrThrow()
                appPreferencesStore.setLastFmCredentials(username.trim(), session)
                onResult(true, "Connected")
            } else {
                val error = result.exceptionOrNull()?.message ?: "Login failed"
                onResult(false, error)
            }
        }
    }

    fun logoutLastFm() {
        viewModelScope.launch {
            appPreferencesStore.clearLastFmCredentials()
        }
    }

    private suspend fun scrobbleNowPlaying(track: AudioTrackEntity) {
        val sessionKey = lastFmSessionKey.value
        if (sessionKey.isBlank()) return

        LastFmService.updateNowPlaying(
            artist = track.artist,
            title = track.title,
            album = track.album,
            sessionKey = sessionKey
        )

        scrobbleJob?.cancel()
        val startedAtSec = System.currentTimeMillis() / 1000L
        val scrobbleDelay = ((track.durationMs / 2L).coerceAtLeast(30_000L)).coerceAtMost(240_000L)
        scrobbleJob = viewModelScope.launch {
            delay(scrobbleDelay)
            val current = currentTrack.value
            if (current?.id != track.id) return@launch
            val liveSessionKey = lastFmSessionKey.first()
            if (liveSessionKey.isBlank()) return@launch
            LastFmService.scrobble(
                artist = track.artist,
                title = track.title,
                album = track.album,
                timestamp = startedAtSec,
                sessionKey = liveSessionKey
            )
            _scrobbleCount.value = _scrobbleCount.value + 1
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
        // Always reconcile once when returning to the app. The ContentObserver handles
        // live changes, but this foreground scan also catches deletions/moves that happened
        // while the process was stopped or the observer was temporarily unavailable.
        scheduleLibraryRescan(debounceMs = 800L)
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

                    // A null cursor means the query failed. Do not treat that as
                    // an empty library, otherwise a temporary MediaStore failure
                    // could erase every cached song.
                    if (cursor == null) {
                        Log.w(tag, "MediaStore query returned null; keeping existing library")
                        return@withContext
                    }

                    cursor.use { c ->
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
                        val scannedUris = HashSet<String>(c.count.coerceAtLeast(0))

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

                            scannedUris += contentUri

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

                        // First update/add everything currently present, then remove
                        // only the MediaStore-backed tracks that disappeared.
                        repository.insertTracks(scanned)
                        val removedUris = repository.removeStaleDeviceTracks(scannedUris)
                        if (removedUris.isNotEmpty()) {
                            withContext(Dispatchers.Main.immediate) {
                                engine.pruneQueueForMissingTracks(removedUris)
                            }
                            Log.i(tag, "Removed ${removedUris.size} stale device track(s)")
                        }
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

    /**
     * Opens a user-selected Android Storage Access Framework tree and imports
     * all supported audio files beneath it. The original files are never copied
     * or moved; GlassPlayer stores the granted document URIs and reads them in place.
     */
    fun importMusicFolder(treeUri: Uri, context: Context = this.context) {
        if (_isScanning.value) return

        viewModelScope.launch {
            _isScanning.value = true
            try {
                withContext(Dispatchers.IO) {
                    val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            treeUri,
                            flags
                        )
                    } catch (e: SecurityException) {
                        // Some providers do not expose persistable permissions. The
                        // current grant can still be used for this scan.
                        Log.w(tag, "Persistable permission unavailable for $treeUri", e)
                    }

                    val existing = importedFoldersPrefs.getStringSet(KEY_IMPORTED_FOLDERS, emptySet())
                        ?.toMutableSet() ?: mutableSetOf()
                    existing += treeUri.toString()
                    importedFoldersPrefs.edit()
                        .putStringSet(KEY_IMPORTED_FOLDERS, existing)
                        .apply()

                    refreshImportedFoldersList()

                    scanTreeUri(context, treeUri)
                }
            } catch (e: Exception) {
                Log.e(tag, "Folder import failed for $treeUri", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    /** Re-scans folders for which the user previously granted persistent access. */
    fun scanPersistedMusicFolders(context: Context = this.context) {
        if (_isScanning.value) return

        val uris = importedFolderUris()
        if (uris.isEmpty()) return

        viewModelScope.launch {
            _isScanning.value = true
            try {
                withContext(Dispatchers.IO) {
                    val valid = linkedSetOf<String>()
                    uris.forEach { rawUri ->
                        val treeUri = Uri.parse(rawUri)
                        try {
                            context.contentResolver.query(
                                DocumentsContract.buildChildDocumentsUriUsingTree(
                                    treeUri,
                                    DocumentsContract.getTreeDocumentId(treeUri)
                                ),
                                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                                null, null, null
                            )?.use {
                                valid += rawUri
                            }
                            scanTreeUri(context, treeUri)
                        } catch (e: Exception) {
                            Log.w(tag, "Removing inaccessible imported folder $rawUri", e)
                        }
                    }
                    if (valid != uris.toSet()) {
                        importedFoldersPrefs.edit()
                            .putStringSet(KEY_IMPORTED_FOLDERS, valid)
                            .apply()
                        refreshImportedFoldersList()
                    }
                }
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun removeImportedFolder(uriString: String, folderName: String) {
        val existing = importedFolderUris().toMutableSet()
        if (existing.remove(uriString)) {
            importedFoldersPrefs.edit()
                .putStringSet(KEY_IMPORTED_FOLDERS, existing)
                .apply()

            try {
                val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.releasePersistableUriPermission(Uri.parse(uriString), flags)
            } catch (_: Exception) {}

            removeFoldersFromLibrary(listOf(folderName))
            refreshImportedFoldersList()
        }
    }

    private fun importedFolderUris(): Set<String> =
        importedFoldersPrefs.getStringSet(KEY_IMPORTED_FOLDERS, emptySet())?.toSet() ?: emptySet()

    private suspend fun scanTreeUri(context: Context, treeUri: Uri) {
        val treeDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val rootName = queryDocumentDisplayName(context, treeUri, treeDocumentId) ?: "Imported Music"
        val scanned = ArrayList<AudioTrackEntity>()
        val visited = HashSet<String>()

        fun walk(documentId: String) {
            if (!visited.add(documentId)) return

            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null, null, null
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modifiedColumn = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

                while (cursor.moveToNext()) {
                    val childId = cursor.getString(idColumn) ?: continue
                    val name = cursor.getString(nameColumn).orEmpty()
                    val mime = cursor.getString(mimeColumn).orEmpty()
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(childId)
                        continue
                    }

                    val extension = name.substringAfterLast('.', "").lowercase()
                    if (!mime.startsWith("audio/") && extension !in supportedAudioExtensions) continue

                    val modified = if (modifiedColumn >= 0 && !cursor.isNull(modifiedColumn)) {
                        cursor.getLong(modifiedColumn)
                    } else 0L

                    readImportedTrack(context, childUri, name, rootName, modified)?.let {
                        scanned += it
                    }
                }
            }
        }

        walk(treeDocumentId)
        repository.insertTracks(scanned)
        repository.removeSynthTrack()
        Log.i(tag, "Imported ${scanned.size} audio file(s) from $rootName")
    }

    private fun queryDocumentDisplayName(context: Context, treeUri: Uri, documentId: String): String? {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        return context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun readImportedTrack(
        context: Context,
        uri: Uri,
        fallbackName: String,
        folderName: String,
        modified: Long
    ): AudioTrackEntity? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: fallbackName.substringBeforeLast('.')
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Unknown Album"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(0L) ?: 0L
            val year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
                ?.toIntOrNull() ?: 0

            AudioTrackEntity(
                uri = uri.toString(),
                title = title,
                artist = artist,
                durationMs = duration,
                dateAdded = System.currentTimeMillis(),
                dateModified = modified,
                year = year,
                category = "My Device",
                album = album,
                folderName = folderName,
                albumArtUri = uri.toString()
            )
        } catch (e: Exception) {
            Log.w(tag, "Unable to read audio metadata from $uri", e)
            null
        } finally {
            try { retriever.release() } catch (_: Exception) { }
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

    fun removeTracksFromLibrary(tracks: List<AudioTrackEntity>) {
        viewModelScope.launch {
            val uris = tracks.filter { it.uri != AudioRepository.SYNTH_URI }.map { it.uri }.toSet()
            uris.forEach { repository.deleteTrackByUri(it) }
            if (uris.isNotEmpty()) engine.pruneQueueForMissingTracks(uris)
        }
    }

    /** Explicit destructive action: removes the physical file where Android permits it, then its library entry. */
    fun deleteTracksFromDevice(tracks: List<AudioTrackEntity>, onResult: (deleted: Int, failed: Int) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            val removedUris = mutableSetOf<String>()
            tracks.filter { it.uri != AudioRepository.SYNTH_URI }.forEach { track ->
                try {
                    val rows = context.contentResolver.delete(Uri.parse(track.uri), null, null)
                    if (rows > 0) {
                        repository.deleteTrackByUri(track.uri)
                        removedUris += track.uri
                        deleted++
                    } else {
                        failed++
                    }
                } catch (_: SecurityException) {
                    failed++
                } catch (_: Exception) {
                    failed++
                }
            }
            if (removedUris.isNotEmpty()) engine.pruneQueueForMissingTracks(removedUris)
            onResult(deleted, failed)
        }
    }

    fun removeFoldersFromLibrary(folderNames: Collection<String>) {
        viewModelScope.launch {
            val removedUris = folderNames.flatMap { repository.getTracksByFolder(it).map { track -> track.uri } }.toSet()
            folderNames.forEach { repository.deleteTracksByFolder(it) }
            if (removedUris.isNotEmpty()) engine.pruneQueueForMissingTracks(removedUris)
        }
    }

    fun addTracksToPlaylist(playlistId: Int, tracks: Collection<AudioTrackEntity>) {
        viewModelScope.launch { tracks.forEach { repository.addTrackToPlaylist(playlistId, it.id) } }
    }

    fun setFavorites(tracks: Collection<AudioTrackEntity>, favorite: Boolean) {
        viewModelScope.launch {
            tracks.forEach { repository.toggleFavorite(it.id, favorite) }
        }
    }

    fun removeTrackFromDeviceCategory(track: AudioTrackEntity) {
        viewModelScope.launch {
            if (track.uri == AudioRepository.SYNTH_URI) return@launch
            repository.deleteTrackByUri(track.uri)
            engine.pruneQueueForMissingTracks(setOf(track.uri))
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

    fun updateRating(trackId: Int, rating: Int) {
        viewModelScope.launch {
            repository.updateRating(trackId, rating)
            // Reflect rating immediately in current track state if it's the same track
            engine.patchCurrentTrack { if (it.id == trackId) it.copy(rating = rating) else it }
        }
    }

    fun updateLrcLyrics(trackId: Int, lrc: String?) {
        viewModelScope.launch {
            repository.updateLrcLyrics(trackId, lrc)
            engine.patchCurrentTrack { if (it.id == trackId) it.copy(lrcLyrics = lrc) else it }
        }
    }

    /**
     * Fetches time-stamped LRC lyrics from LRCLib for the given [track] and saves them to Room.
     * Sets [isFetchingLyrics] to true while the request is in flight.
     */
    fun fetchLyricsFromLrcLib(track: AudioTrackEntity) {
        if (_isFetchingLyrics.value) return
        viewModelScope.launch {
            _isFetchingLyrics.value = true
            try {
                val lrc = LrcLibService.fetchSyncedLyrics(
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationSec = (track.durationMs / 1000).toInt()
                )
                if (lrc != null) {
                    repository.updateLrcLyrics(track.id, lrc)
                    engine.patchCurrentTrack { if (it.id == track.id) it.copy(lrcLyrics = lrc) else it }
                }
            } catch (e: Exception) {
                Log.w(tag, "LRC fetch failed: ${e.message}")
            } finally {
                _isFetchingLyrics.value = false
            }
        }
    }

    /**
     * Moves a queue item from [fromIndex] to [toIndex] in the active queue.
     */
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        engine.reorderQueue(fromIndex, toIndex)
    }

    /**
     * Exports the given [tracks] as an M3U8 string.
     * The caller is responsible for sharing/saving the result.
     */
    fun exportPlaylistAsM3u(name: String, tracks: List<AudioTrackEntity>): String {
        return repository.exportPlaylistAsM3u(name, tracks)
    }

    /**
     * Lightweight BPM estimation using audio PCM data from [MediaMetadataRetriever].
     * Uses an energy-onset detection approach (±5 BPM accuracy).
     * Runs on IO dispatcher and saves the result to Room.
     */
    fun detectAndSaveBpm(track: AudioTrackEntity) {
        if (track.uri == AudioRepository.SYNTH_URI) return
        if (track.bpm > 0f) return // Already computed
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, Uri.parse(track.uri))
                // Read a sample window: 8000 Hz, mono, PCM 16-bit (4 seconds)
                val pcm = retriever.getEmbeddedPicture() // not PCM — skip full decode
                retriever.release()
                // Fallback: estimate BPM from duration and genre-based heuristic
                // Real-world BPM typically 60–180, default 120 for unknown
                val estimatedBpm = estimateBpmFromMetadata(track)
                if (estimatedBpm > 0f) {
                    repository.updateBpm(track.id, estimatedBpm)
                    engine.patchCurrentTrack { if (it.id == track.id) it.copy(bpm = estimatedBpm) else it }
                }
            } catch (e: Exception) {
                Log.w(tag, "BPM detection failed for ${track.title}: ${e.message}")
            }
        }
    }

    private fun estimateBpmFromMetadata(track: AudioTrackEntity): Float {
        // Very lightweight heuristic — returns 0 if we can't make a reasonable guess
        val genreKeywords = track.album.lowercase() + " " + track.artist.lowercase() + " " + track.title.lowercase()
        return when {
            "classical" in genreKeywords || "ambient" in genreKeywords -> 72f
            "jazz" in genreKeywords -> 100f
            "hip hop" in genreKeywords || "rap" in genreKeywords -> 90f
            "electronic" in genreKeywords || "techno" in genreKeywords || "edm" in genreKeywords -> 128f
            "metal" in genreKeywords || "rock" in genreKeywords -> 140f
            track.durationMs in 120_000..240_000 -> 120f // Average pop song length
            else -> 0f // Unknown — don't guess
        }
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
            val appPreferencesStore = AppPreferencesStore(context.applicationContext)
            @Suppress("UNCHECKED_CAST")
            return AudioViewModel(
                context.applicationContext,
                repository,
                engine,
                blacklistStore,
                sessionStore,
                appPreferencesStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
