package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.AudioAttributes as ExoAudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

/**
 * Shared playback engine used by [AudioViewModel] and [PlaybackService].
 * Local files play through Media3 ExoPlayer; the procedural synth uses [ProceduralSynth].
 */
class PlayerEngine private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val tag = "PlayerEngine"

    private val _currentTrack = MutableStateFlow<AudioTrackEntity?>(null)
    val currentTrack: StateFlow<AudioTrackEntity?> = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<AudioTrackEntity>>(emptyList())
    val activeQueue: StateFlow<List<AudioTrackEntity>> = _activeQueue.asStateFlow()

    private val _waveformAmplitudes = MutableStateFlow(List(24) { 0.1f })
    val waveformAmplitudes: StateFlow<List<Float>> = _waveformAmplitudes.asStateFlow()

    private val _synthCutoff = MutableStateFlow(0.5f)
    val synthCutoff: StateFlow<Float> = _synthCutoff.asStateFlow()

    private val _synthSpeed = MutableStateFlow(1.0f)
    val synthSpeed: StateFlow<Float> = _synthSpeed.asStateFlow()

    /** Unified playback rate for ExoPlayer and the procedural synth (0.5x–2.0x). */
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _volume = MutableStateFlow(0.7f)
    val volume: StateFlow<Float> = _volume.asStateFlow()

    private val _equalizerBands = MutableStateFlow<List<Float>>(emptyList())
    val equalizerBands: StateFlow<List<Float>> = _equalizerBands.asStateFlow()

    private val _equalizerEnabled = MutableStateFlow(false)
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _sleepTimerRemainingMs = MutableStateFlow(0L)
    val sleepTimerRemainingMs: StateFlow<Long> = _sleepTimerRemainingMs.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    private val synth = ProceduralSynth()
    private var visualizer: Visualizer? = null
    private var equalizer: Equalizer? = null
    private var mediaSession: MediaSessionCompat? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    /** User pressed play — keep trying to play across focus blips / activity restarts. */
    private var userWantsPlaying = false
    /** True when we paused only because of a transient focus loss (e.g. phone call). */
    private var pausedByTransientFocusLoss = false
    private var volumeBeforeDuck: Float? = null
    private var attachedAudioSessionId: Int = 0

    private var progressJob: Job? = null
    private var visualizerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var artworkJob: Job? = null

    /** Smoothed visualizer target for reactive but stable bars. */
    private var visualizerTarget = List(24) { 0.1f }

    private val shuffleHistory = ArrayDeque<Int>()
    private var libraryFallback: () -> List<AudioTrackEntity> = { emptyList() }
    var onTrackStarted: ((AudioTrackEntity) -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = player.duration
                    if (dur > 0 && dur != C.TIME_UNSET) {
                        _playbackDuration.value = dur
                    }
                    attachAudioEffects(player.audioSessionId)
                    // If UI came back while we still intend to play, keep going
                    if (userWantsPlaying && !player.isPlaying) {
                        player.playWhenReady = true
                        player.play()
                    }
                    updateSessionMetadata(_currentTrack.value ?: return)
                    updateSessionState()
                }
                Player.STATE_ENDED -> onTrackCompleted()
                else -> Unit
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_currentTrack.value?.uri == AudioRepository.SYNTH_URI) return
            _isPlaying.value = isPlaying
            if (isPlaying) {
                pausedByTransientFocusLoss = false
                ensureServiceRunning()
            } else if (userWantsPlaying) {
                // Brief OEM/focus blip while returning to the app — schedule a resume
                schedulePlaybackReassert()
            }
            updateSessionState()
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(tag, "ExoPlayer error: ${error.message}", error)
            _isPlaying.value = false
            updateSessionState()
        }
    }

    private var reassertJob: Job? = null

    private fun schedulePlaybackReassert() {
        reassertJob?.cancel()
        reassertJob = scope.launch {
            delay(250)
            reassertPlaybackIfNeeded()
        }
    }

    /**
     * Call when the UI returns to the foreground so a focus/lifecycle blip
     * cannot leave the player paused while the user still expects music.
     */
    fun reassertPlaybackIfNeeded() {
        if (!userWantsPlaying) return
        val track = _currentTrack.value ?: return
        if (track.uri == AudioRepository.SYNTH_URI) {
            if (!_isPlaying.value) {
                requestPlaybackFocus()
                synth.start()
                applySynthControls()
                _isPlaying.value = true
                startProgressTracker(isSynth = true)
                ensureServiceRunning()
                updateSessionState()
            }
            return
        }
        val player = exoPlayer ?: return
        if (!player.isPlaying) {
            requestPlaybackFocus()
            player.playWhenReady = true
            try {
                player.play()
            } catch (e: Exception) {
                Log.w(tag, "reassert play failed", e)
            }
            _isPlaying.value = true
            startProgressTracker(isSynth = false)
            ensureServiceRunning()
            updateSessionState()
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Pause for now; keep user intent so reopening the app can resume
                pausedByTransientFocusLoss = true
                restoreDuckedVolume()
                if (_isPlaying.value) pauseForFocusLoss(transient = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                restoreDuckedVolume()
                if (_isPlaying.value) pauseForFocusLoss(transient = true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                if (volumeBeforeDuck == null) {
                    volumeBeforeDuck = _volume.value
                    applyEngineVolumes(_volume.value * 0.25f)
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                restoreDuckedVolume()
                if (userWantsPlaying) {
                    pausedByTransientFocusLoss = false
                    reassertPlaybackIfNeeded()
                }
            }
        }
    }

    private fun pauseForFocusLoss(transient: Boolean) {
        pausedByTransientFocusLoss = transient
        // Keep userWantsPlaying as-is for transient (call); cleared for permanent above
        val track = _currentTrack.value ?: return
        _isPlaying.value = false
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.pause()
        } else {
            exoPlayer?.pause()
        }
        progressJob?.cancel()
        updateSessionState()
    }

    private fun restoreDuckedVolume() {
        val restored = volumeBeforeDuck ?: return
        volumeBeforeDuck = null
        applyEngineVolumes(restored)
    }

    private fun applyEngineVolumes(level: Float) {
        val v = level.coerceIn(0f, 1f)
        exoPlayer?.volume = v
        synth.volume = v * 0.7f
    }

    fun setLibraryProvider(provider: () -> List<AudioTrackEntity>) {
        libraryFallback = provider
    }

    fun initMediaSession() {
        if (mediaSession != null) return

        ensureExoPlayer()

        val sessionActivity = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).setClass(
            appContext,
            MediaButtonReceiver::class.java
        )
        val mediaButtonReceiver = PendingIntent.getBroadcast(
            appContext,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(appContext, "GlassPlayer").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setSessionActivity(sessionActivity)
            setMediaButtonReceiver(mediaButtonReceiver)
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    userWantsPlaying = true
                    requestPlaybackFocus()
                    togglePlayPause(forcePlay = true)
                }

                override fun onPause() {
                    userWantsPlaying = false
                    togglePlayPause(forcePause = true)
                }

                override fun onSkipToNext() = nextTrack()

                override fun onSkipToPrevious() = previousTrack()

                override fun onSeekTo(pos: Long) = seekTo(pos)

                override fun onStop() {
                    userWantsPlaying = false
                    stopEngine()
                    _isPlaying.value = false
                    abandonPlaybackFocus()
                    updateSessionState()
                }

                override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                    return super.onMediaButtonEvent(mediaButtonEvent)
                }
            })
            isActive = true
        }
        try {
            syncSystemVolume()
        } catch (_: Exception) {
        }
        startVisualizerLoop()
        try {
            updateSessionState()
        } catch (e: Exception) {
            Log.w(tag, "Initial session state failed", e)
        }
    }

    private fun ensureExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                ExoAudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus= */ false
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
            .also { it.addListener(playerListener) }
        exoPlayer = player
        applyPlaybackSpeedToEngines()
        applyVolumeToEngines()
        return player
    }

    fun getSessionToken(): MediaSessionCompat.Token? = mediaSession?.sessionToken

    fun getMediaSession(): MediaSessionCompat? = mediaSession

    fun removeFromQueue(track: AudioTrackEntity) {
        val current = _currentTrack.value
        val queue = _activeQueue.value.toMutableList()
        queue.removeAll { it.id == track.id }
        _activeQueue.value = queue
        if (current?.id == track.id) {
            if (queue.isNotEmpty()) {
                playTrack(queue.first(), queue)
            } else {
                togglePlayPause(forcePause = true)
                _currentTrack.value = null
                updateSessionState()
            }
        } else {
            updateSessionState()
        }
    }

    /**
     * Drop tracks whose folder is blacklisted from the active play queue.
     * If the current song is hidden, advances to the next allowed track (or stops).
     */
    fun pruneQueueForBlacklist(blockedFolders: Set<String>) {
        if (blockedFolders.isEmpty() && _activeQueue.value.isEmpty()) return

        fun isAllowed(track: AudioTrackEntity): Boolean {
            return track.uri == AudioRepository.SYNTH_URI || track.folderName !in blockedFolders
        }

        val current = _currentTrack.value
        val pruned = _activeQueue.value.filter(::isAllowed)
        if (pruned.size == _activeQueue.value.size &&
            (current == null || isAllowed(current))
        ) {
            return
        }

        _activeQueue.value = pruned

        if (current != null && !isAllowed(current)) {
            if (pruned.isNotEmpty()) {
                playTrack(pruned.first(), pruned)
            } else {
                userWantsPlaying = false
                togglePlayPause(forcePause = true)
                _currentTrack.value = null
                updateSessionState()
            }
        } else {
            updateSessionState()
        }
    }

    fun playNext(track: AudioTrackEntity) {
        val current = _currentTrack.value
        if (current == null) {
            playTrack(track)
            return
        }
        if (current.id == track.id) return

        val queue = _activeQueue.value.toMutableList()
        queue.removeAll { it.id == track.id }

        var currentIndex = queue.indexOfFirst { it.id == current.id }
        if (currentIndex < 0) {
            queue.add(0, current)
            currentIndex = 0
        }
        queue.add(currentIndex + 1, track)
        _activeQueue.value = queue
        updateSessionState()
    }

    fun playTrack(track: AudioTrackEntity, customQueue: List<AudioTrackEntity>? = null) {
        if (customQueue != null) {
            // Prefer the visible (non-blacklisted) library; always keep the track the user tapped
            val allowedIds = libraryFallback().map { it.id }.toHashSet()
            val filtered = customQueue.filter {
                it.id == track.id || it.id in allowedIds || it.uri == AudioRepository.SYNTH_URI
            }
            _activeQueue.value = filtered.ifEmpty { listOf(track) }
            shuffleHistory.clear()
        } else {
            val currentQueue = _activeQueue.value
            if (currentQueue.isEmpty() || currentQueue.none { it.id == track.id }) {
                val list = libraryFallback()
                _activeQueue.value = if (list.isNotEmpty()) list else listOf(track)
            }
        }

        stopEngine(keepSession = true)
        _currentTrack.value = track
        userWantsPlaying = true
        _isPlaying.value = true
        _playbackPosition.value = 0L
        pausedByTransientFocusLoss = false
        mediaSession?.isActive = true
        requestPlaybackFocus()

        if (track.uri == AudioRepository.SYNTH_URI) {
            _playbackDuration.value = track.durationMs
            applyVolumeToEngines()
            applySynthControls()
            synth.start()
            startProgressTracker(isSynth = true)
        } else {
            try {
                val player = ensureExoPlayer()
                player.setMediaItem(MediaItem.fromUri(Uri.parse(track.uri)))
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.prepare()
                player.playWhenReady = true
                applyPlaybackSpeedToEngines()
                applyVolumeToEngines()
                startProgressTracker(isSynth = false)
            } catch (e: Exception) {
                Log.e(tag, "Failed to start ExoPlayer", e)
                _isPlaying.value = false
                userWantsPlaying = false
            }
        }

        updateSessionMetadata(track)
        updateSessionState()
        ensureServiceRunning()
        onTrackStarted?.invoke(track)
    }

    fun togglePlayPause(forcePlay: Boolean = false, forcePause: Boolean = false) {
        val track = _currentTrack.value ?: return
        val shouldPause = when {
            forcePause -> true
            forcePlay -> false
            else -> _isPlaying.value
        }

        if (shouldPause) {
            // Focus-loss pauses go through pauseForFocusLoss(), not here — so this is always intentional
            userWantsPlaying = false
            pausedByTransientFocusLoss = false
            _isPlaying.value = false
            if (track.uri == AudioRepository.SYNTH_URI) {
                synth.pause()
            } else {
                exoPlayer?.pause()
            }
            progressJob?.cancel()
        } else {
            userWantsPlaying = true
            pausedByTransientFocusLoss = false
            requestPlaybackFocus()
            _isPlaying.value = true
            mediaSession?.isActive = true
            if (track.uri == AudioRepository.SYNTH_URI) {
                synth.start()
                applySynthControls()
                startProgressTracker(isSynth = true)
            } else {
                exoPlayer?.playWhenReady = true
                exoPlayer?.play()
                startProgressTracker(isSynth = false)
            }
            ensureServiceRunning()
        }
        updateSessionState()
    }

    fun nextTrack(fromUser: Boolean = true) {
        val queue = _activeQueue.value
        val current = _currentTrack.value
        if (queue.isEmpty()) return

        if (!fromUser && _repeatMode.value == RepeatMode.ONE && current != null) {
            replayCurrentTrack()
            return
        }

        val currentIndex = queue.indexOfFirst { it.id == current?.id }
        val nextIndex = when {
            _isShuffleEnabled.value -> {
                if (currentIndex >= 0) shuffleHistory.addLast(currentIndex)
                if (shuffleHistory.size > 64) shuffleHistory.removeFirst()
                pickShuffleIndex(queue.size, currentIndex)
            }
            else -> {
                when {
                    currentIndex == -1 -> 0
                    currentIndex >= queue.lastIndex -> {
                        if (_repeatMode.value == RepeatMode.ALL) {
                            0
                        } else {
                            _isPlaying.value = false
                            updateSessionState()
                            return
                        }
                    }
                    else -> currentIndex + 1
                }
            }
        }
        playTrack(queue[nextIndex])
    }

    private fun onTrackCompleted() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> replayCurrentTrack()
            RepeatMode.ALL, RepeatMode.OFF -> nextTrack(fromUser = false)
        }
    }

    private fun replayCurrentTrack() {
        val track = _currentTrack.value ?: return
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(0)
            _playbackPosition.value = 0L
            if (!_isPlaying.value) {
                _isPlaying.value = true
                synth.start()
                applySynthControls()
            }
            startProgressTracker(isSynth = true)
        } else {
            val player = exoPlayer
            if (player != null) {
                try {
                    player.seekTo(0)
                    player.play()
                    _playbackPosition.value = 0L
                    _isPlaying.value = true
                    startProgressTracker(isSynth = false)
                } catch (_: Exception) {
                    playTrack(track, _activeQueue.value)
                }
            } else {
                playTrack(track, _activeQueue.value)
            }
        }
        updateSessionState()
    }

    fun previousTrack() {
        val queue = _activeQueue.value
        val current = _currentTrack.value
        if (queue.isEmpty()) return

        val currentIndex = queue.indexOfFirst { it.id == current?.id }
        val prevIndex = when {
            _isShuffleEnabled.value && shuffleHistory.isNotEmpty() -> shuffleHistory.removeLast()
            _isShuffleEnabled.value -> pickShuffleIndex(queue.size, currentIndex)
            else -> {
                if (_playbackPosition.value > 3000L) {
                    seekTo(0)
                    return
                }
                if (currentIndex <= 0) queue.lastIndex else currentIndex - 1
            }
        }
        playTrack(queue[prevIndex.coerceIn(0, queue.lastIndex)])
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value ?: return
        val clamped = positionMs.coerceIn(0L, max(_playbackDuration.value, 0L))
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(clamped)
            _playbackPosition.value = clamped
        } else {
            exoPlayer?.seekTo(clamped)
            _playbackPosition.value = clamped
        }
        updateSessionState()
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        if (!_isShuffleEnabled.value) shuffleHistory.clear()
        updateSessionState()
    }

    fun toggleLoop() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        updateSessionState()
    }

    fun updateSynthCutoff(cutoff: Float) {
        _synthCutoff.value = cutoff
        applySynthControls()
    }

    fun updateSynthSpeed(speed: Float) {
        _synthSpeed.value = speed.coerceIn(0.5f, 2.5f)
        applySynthControls()
    }

    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed.coerceIn(0.5f, 2.0f)
        // Keep synth tempo in sync when using the unified speed control
        _synthSpeed.value = _playbackSpeed.value
        applyPlaybackSpeedToEngines()
        applySynthControls()
        updateSessionState()
    }

    fun setVolume(level: Float) {
        _volume.value = level.coerceIn(0f, 1f)
        applyVolumeToEngines()
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, (_volume.value * maxVol).toInt(), 0)
    }

    fun syncSystemVolume() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVol = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        _volume.value = current.toFloat() / maxVol
        applyVolumeToEngines()
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        _equalizerEnabled.value = enabled
        equalizer?.enabled = enabled
    }

    fun setEqualizerBand(index: Int, normalized: Float) {
        val eq = equalizer ?: return
        if (index !in 0 until eq.numberOfBands) return
        val minLevel = eq.bandLevelRange[0]
        val maxLevel = eq.bandLevelRange[1]
        val level = (minLevel + (maxLevel - minLevel) * normalized.coerceIn(0f, 1f)).toInt().toShort()
        eq.setBandLevel(index.toShort(), level)
        val bands = _equalizerBands.value.toMutableList()
        if (index < bands.size) {
            bands[index] = normalized.coerceIn(0f, 1f)
            _equalizerBands.value = bands
        }
    }

    fun setSleepTimer(durationMs: Long) {
        sleepTimerJob?.cancel()
        if (durationMs <= 0L) {
            _sleepTimerRemainingMs.value = 0L
            return
        }
        _sleepTimerRemainingMs.value = durationMs
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0)
            }
            if (isActive) {
                togglePlayPause(forcePause = true)
                _sleepTimerRemainingMs.value = 0L
            }
        }
    }

    fun cancelSleepTimer() = setSleepTimer(0L)

    fun patchCurrentTrack(transform: (AudioTrackEntity) -> AudioTrackEntity) {
        val current = _currentTrack.value ?: return
        _currentTrack.value = transform(current)
        _currentTrack.value?.let { updateSessionMetadata(it) }
    }

    fun release() {
        sleepTimerJob?.cancel()
        progressJob?.cancel()
        visualizerJob?.cancel()
        stopEngine(keepSession = false)
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
    }

    private fun pickShuffleIndex(size: Int, avoid: Int): Int {
        if (size <= 1) return 0
        var pick: Int
        do {
            pick = (0 until size).random()
        } while (pick == avoid && size > 1)
        return pick
    }

    private fun applySynthControls() {
        synth.cutoff = _synthCutoff.value
        synth.speed = _synthSpeed.value
        synth.volume = _volume.value * 0.7f
    }

    private fun applyPlaybackSpeedToEngines() {
        val speed = _playbackSpeed.value
        exoPlayer?.setPlaybackSpeed(speed)
    }

    private fun applyVolumeToEngines() {
        if (volumeBeforeDuck != null) return
        applyEngineVolumes(_volume.value)
    }

    private fun attachAudioEffects(sessionId: Int) {
        if (sessionId == 0) return
        // Re-binding Visualizer/EQ on every READY can glitch or stop audio on some OEMs
        if (sessionId == attachedAudioSessionId && (visualizer != null || equalizer != null)) {
            return
        }
        releaseAudioEffects()
        attachedAudioSessionId = sessionId
        try {
            visualizer = Visualizer(sessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            visualizer: Visualizer?,
                            waveform: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (waveform == null) return
                            visualizerTarget = List(24) { i ->
                                val idx = (i * waveform.size / 24).coerceIn(0, waveform.size - 1)
                                val amp = abs(waveform[idx].toInt()) / 128f
                                amp.coerceIn(0.08f, 1f)
                            }
                        }

                        override fun onFftDataCapture(
                            visualizer: Visualizer?,
                            fft: ByteArray?,
                            samplingRate: Int
                        ) {
                            if (fft == null || fft.size < 4) return
                            visualizerTarget = List(24) { i ->
                                val n = fft.size / 2
                                val bin = 1 + (i * (n - 1) / 24).coerceIn(1, n - 1)
                                val re = fft.getOrNull(bin * 2)?.toInt() ?: 0
                                val im = fft.getOrNull(bin * 2 + 1)?.toInt() ?: 0
                                val mag = ln(1.0 + re * re + im * im).toFloat()
                                (mag / 12f).coerceIn(0.08f, 1f)
                            }
                        }
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    false,
                    true
                )
                enabled = true
            }
        } catch (e: Exception) {
            Log.w(tag, "Visualizer unavailable", e)
        }

        try {
            equalizer = Equalizer(0, sessionId).apply {
                enabled = _equalizerEnabled.value
                val bands = numberOfBands.toInt()
                val minLevel = bandLevelRange[0].toInt()
                val maxLevel = bandLevelRange[1].toInt()
                val span = (maxLevel - minLevel).coerceAtLeast(1)
                _equalizerBands.value = List(bands) { i ->
                    (getBandLevel(i.toShort()) - minLevel).toFloat() / span
                }
            }
        } catch (e: Exception) {
            Log.w(tag, "Equalizer unavailable", e)
            _equalizerBands.value = emptyList()
        }
    }

    private fun releaseAudioEffects() {
        attachedAudioSessionId = 0
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        try {
            equalizer?.release()
        } catch (_: Exception) {
        }
        equalizer = null
    }

    private fun stopEngine(keepSession: Boolean = false) {
        progressJob?.cancel()
        synth.stop()
        releaseAudioEffects()
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {
            }
        }
        if (!keepSession) {
            _currentTrack.value = null
            _isPlaying.value = false
            updateSessionState()
        }
    }

    private fun startProgressTracker(isSynth: Boolean) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value && isActive) {
                if (isSynth) {
                    val pos = synth.positionMs()
                    val dur = _playbackDuration.value
                    _playbackPosition.value = if (dur > 0) pos % dur else pos
                    if (dur > 0 && pos >= dur) {
                        onTrackCompleted()
                        if (_repeatMode.value != RepeatMode.ONE) break
                    }
                } else {
                    exoPlayer?.let { player ->
                        if (player.isPlaying || player.playbackState == Player.STATE_READY) {
                            _playbackPosition.value = player.currentPosition.coerceAtLeast(0L)
                            val dur = player.duration
                            if (dur > 0 && dur != C.TIME_UNSET) {
                                _playbackDuration.value = dur
                            }
                        }
                    }
                }
                updateSessionState()
                delay(500)
            }
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val track = _currentTrack.value
                if (_isPlaying.value && track?.uri == AudioRepository.SYNTH_URI) {
                    val rootAmp = (synth.lastWaveformValue + 1.0f) / 2.0f
                    val t = System.currentTimeMillis() * 0.012
                    visualizerTarget = List(24) { index ->
                        val phase = (index / 24f) * Math.PI * 2.0
                        val modulation = kotlin.math.sin(phase + t).toFloat()
                        val harmonic = kotlin.math.sin(phase * 2.0 + t * 1.4).toFloat() * 0.15f
                        ((rootAmp * 0.7f) + (modulation * 0.22f) + harmonic + 0.08f).coerceIn(0.08f, 1f)
                    }
                } else if (!_isPlaying.value) {
                    visualizerTarget = visualizerTarget.map { (it * 0.82f).coerceAtLeast(0.08f) }
                }

                // Smooth interpolation toward target for reactive, performant bars
                val current = _waveformAmplitudes.value
                val smoothed = List(24) { i ->
                    val target = visualizerTarget.getOrElse(i) { 0.1f }
                    val prev = current.getOrElse(i) { 0.1f }
                    val alpha = if (target >= prev) 0.55f else 0.28f
                    (prev + (target - prev) * alpha).coerceIn(0.08f, 1f)
                }
                _waveformAmplitudes.value = smoothed
                delay(33)
            }
        }
    }

    private fun updateSessionMetadata(track: AudioTrackEntity) {
        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, track.artist)
            .putLong(
                MediaMetadataCompat.METADATA_KEY_DURATION,
                _playbackDuration.value.takeIf { it > 0 } ?: track.durationMs
            )
        track.albumArtUri?.let {
            builder.putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, it)
            builder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON_URI, it)
        }
        mediaSession?.setMetadata(builder.build())

        artworkJob?.cancel()
        artworkJob = scope.launch {
            val art = withContext(Dispatchers.IO) { loadArtworkBitmap(track.albumArtUri) }
            if (_currentTrack.value?.id == track.id) {
                val withArt = MediaMetadataCompat.Builder(builder.build())
                if (art != null) {
                    withArt.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    withArt.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
                }
                mediaSession?.setMetadata(withArt.build())
                PlaybackService.updateNotification(appContext)
            }
        }
    }

    private fun loadArtworkBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        return try {
            val uri = Uri.parse(uriString)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            var sample = 1
            val maxDim = 512
            var halfH = bounds.outHeight / 2
            var halfW = bounds.outWidth / 2
            while (halfH / sample >= maxDim && halfW / sample >= maxDim) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
            appContext.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun updateSessionState() {
        val state = if (_isPlaying.value) {
            PlaybackStateCompat.STATE_PLAYING
        } else if (_currentTrack.value != null) {
            PlaybackStateCompat.STATE_PAUSED
        } else {
            PlaybackStateCompat.STATE_STOPPED
        }
        val actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO or
            PlaybackStateCompat.ACTION_STOP
        mediaSession?.isActive = _currentTrack.value != null
        val speed = if (_isPlaying.value) _playbackSpeed.value else 0f
        mediaSession?.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setActions(actions)
                .setState(
                    state,
                    _playbackPosition.value,
                    speed,
                    SystemClock.elapsedRealtime()
                )
                .build()
        )
        PlaybackService.updateNotification(appContext)
    }

    private fun requestPlaybackFocus() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = req
            am.requestAudioFocus(req)
        } else {
            @Suppress("DEPRECATION")
            am.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        hasAudioFocus = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPlaybackFocus() {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            am.abandonAudioFocus(audioFocusChangeListener)
        }
        hasAudioFocus = false
    }

    private fun ensureServiceRunning() {
        try {
            val intent = Intent(appContext, PlaybackService::class.java).apply {
                action = PlaybackService.ACTION_ENSURE_FOREGROUND
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                appContext.startForegroundService(intent)
            } else {
                appContext.startService(intent)
            }
        } catch (e: Exception) {
            // Background start can fail on some OEMs; notification update still helps if service lives
            Log.w(tag, "Could not start PlaybackService", e)
            PlaybackService.updateNotification(appContext)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlayerEngine? = null

        fun getOrNull(): PlayerEngine? = INSTANCE

        fun get(context: Context): PlayerEngine {
            INSTANCE?.let { return it }
            return synchronized(this) {
                INSTANCE?.let { return it }
                val engine = PlayerEngine(context.applicationContext)
                INSTANCE = engine
                engine.initMediaSession()
                engine
            }
        }
    }
}
