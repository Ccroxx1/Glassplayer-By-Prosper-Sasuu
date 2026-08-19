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
import android.os.Bundle
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
import kotlinx.coroutines.cancel
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

    /** Crossfade duration in seconds (0 = disabled). */
    private val _crossfadeSec = MutableStateFlow(0f)
    val crossfadeSec: StateFlow<Float> = _crossfadeSec.asStateFlow()

    /** Pitch shift in semitones (-6..+6, 0 = normal). */
    private val _pitchSemitones = MutableStateFlow(0f)
    val pitchSemitones: StateFlow<Float> = _pitchSemitones.asStateFlow()

    /** When true, the sleep timer fades volume to 0 over the last 30 s before stopping. */
    private val _sleepFadeEnabled = MutableStateFlow(false)
    val sleepFadeEnabled: StateFlow<Boolean> = _sleepFadeEnabled.asStateFlow()

    private var exoPlayer: ExoPlayer? = null
    /** Secondary ExoPlayer used during crossfade transitions. */
    private var crossfadePlayer: ExoPlayer? = null
    private var crossfadeJob: Job? = null
    private var autoCrossfadeTriggerTrackId: Int? = null
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
    @Volatile
    private var visualizerTarget = List(24) { 0.1f }

    private val shuffleHistory = ArrayDeque<Int>()
    private var libraryFallback: () -> List<AudioTrackEntity> = { emptyList() }
    var onTrackStarted: ((AudioTrackEntity) -> Unit)? = null
    /** Fired when session-worthy state changes (track, queue, play/pause, seek, shuffle/repeat). */
    var onSessionChanged: (() -> Unit)? = null

    private var lastEnsureServiceAt = 0L
    private var lastNotificationKey: String? = null
    private var lastNotificationAt = 0L
    /** Suppress session-change callbacks while a restore is in flight. */
    private var restoringSession = false

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
                    // Resume only when the user still wants play and we are not paused for a call/focus loss
                    if (userWantsPlaying && !pausedByTransientFocusLoss && !player.isPlaying && hasAudioFocus) {
                        player.playWhenReady = true
                        player.play()
                    }
                    updateSessionMetadata(_currentTrack.value ?: return)
                    updateSessionState()
                }
                Player.STATE_ENDED -> {
                    // Only handle completion if the playlist is empty or we reached the end
                    if (player.mediaItemCount <= 1 || player.nextMediaItemIndex == C.INDEX_UNSET) {
                        onTrackCompleted()
                    }
                }
                else -> Unit
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val player = exoPlayer ?: return
            val trackId = mediaItem?.mediaId?.toIntOrNull() ?: return
            val track = _activeQueue.value.find { it.id == trackId } ?: return

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                // Gapless auto-transition
                _currentTrack.value = track
                _playbackPosition.value = 0L
                val dur = player.duration
                _playbackDuration.value = if (dur > 0 && dur != C.TIME_UNSET) dur else track.durationMs
                updateSessionMetadata(track)
                updateSessionState()
                ensureServiceRunning()
                onTrackStarted?.invoke(track)
                notifySessionChanged()

                // Pre-load the next track into the playlist if crossfade is disabled
                if (_crossfadeSec.value <= 0f) {
                    prepareNextTrackForGapless()
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (_currentTrack.value?.uri == AudioRepository.SYNTH_URI) return
            _isPlaying.value = isPlaying
            if (isPlaying) {
                pausedByTransientFocusLoss = false
                ensureServiceRunning()
            } else if (userWantsPlaying && !pausedByTransientFocusLoss) {
                // Brief OEM blip while returning to the app — schedule a resume
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
        if (pausedByTransientFocusLoss || !userWantsPlaying) return
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
        if (!userWantsPlaying || pausedByTransientFocusLoss) return
        val track = _currentTrack.value ?: return
        if (!requestPlaybackFocus()) return
        if (track.uri == AudioRepository.SYNTH_URI) {
            if (!_isPlaying.value) {
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
                // Another app took audio permanently — stop fighting it
                userWantsPlaying = false
                pausedByTransientFocusLoss = false
                restoreDuckedVolume()
                if (_isPlaying.value) pauseForFocusLoss(transient = false)
                abandonPlaybackFocus()
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
                hasAudioFocus = true
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
        // Keep userWantsPlaying as-is for transient (call); cleared for permanent by caller
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
        val base = level.coerceIn(0f, 1f)
        val replayGainDb = _currentTrack.value?.replayGainDb ?: 0f
        // Apply per-track ReplayGain inside ExoPlayer while preserving the system master volume.
        val gainMultiplier = Math.pow(10.0, (replayGainDb / 20.0).toDouble()).toFloat()
        val v = (base * gainMultiplier).coerceIn(0f, 1f)
        exoPlayer?.volume = v
        synth.volume = base * 0.7f
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
            @Suppress("DEPRECATION")
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

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    val id = mediaId ?: return
                    val track = libraryFallback().firstOrNull { it.uri == id }
                    if (track != null) {
                        playTrack(track)
                        return
                    }
                    val numericId = id.toIntOrNull()
                    if (numericId != null) {
                        libraryFallback().firstOrNull { it.id == numericId }?.let { playTrack(it) }
                    }
                }

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

    private fun ensureCrossfadePlayer(): ExoPlayer {
        crossfadePlayer?.let { return it }
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
        crossfadePlayer = player
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
                notifySessionChanged()
            }
        } else {
            if (_crossfadeSec.value <= 0f) {
                prepareNextTrackForGapless()
            }
            updateSessionState()
            notifySessionChanged()
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
                notifySessionChanged()
            }
        } else {
            if (_crossfadeSec.value <= 0f) {
                prepareNextTrackForGapless()
            }
            updateSessionState()
            notifySessionChanged()
        }
    }

    /**
     * Removes tracks that disappeared from MediaStore from the active queue.
     * If the current track disappeared, move to the first remaining track.
     * A previously paused track remains paused after the replacement is loaded.
     */
    fun pruneQueueForMissingTracks(missingUris: Set<String>) {
        if (missingUris.isEmpty()) return

        val current = _currentTrack.value
        val wasPlaying = _isPlaying.value
        val pruned = _activeQueue.value.filterNot { it.uri in missingUris }
        val currentMissing = current?.uri?.let { it in missingUris } == true

        if (pruned.size == _activeQueue.value.size && !currentMissing) return

        _activeQueue.value = pruned

        if (currentMissing) {
            if (pruned.isNotEmpty()) {
                val next = pruned.first()
                playTrack(next, pruned)
                if (!wasPlaying) {
                    togglePlayPause(forcePause = true)
                }
            } else {
                userWantsPlaying = false
                togglePlayPause(forcePause = true)
                _currentTrack.value = null
                _playbackPosition.value = 0L
                _playbackDuration.value = 0L
                updateSessionState()
                notifySessionChanged()
            }
        } else {
            if (_crossfadeSec.value <= 0f) {
                prepareNextTrackForGapless()
            }
            updateSessionState()
            notifySessionChanged()
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
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
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
        autoCrossfadeTriggerTrackId = null
        userWantsPlaying = true
        _playbackPosition.value = 0L
        pausedByTransientFocusLoss = false
        mediaSession?.isActive = true
        val focusGranted = requestPlaybackFocus()
        _isPlaying.value = focusGranted

        if (track.uri == AudioRepository.SYNTH_URI) {
            _playbackDuration.value = track.durationMs
            applyVolumeToEngines()
            applySynthControls()
            if (focusGranted) {
                synth.start()
                startProgressTracker(isSynth = true)
            }
        } else {
            try {
                val player = ensureExoPlayer()
                player.stop()
                player.clearMediaItems()
                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(track.uri))
                    .setMediaId(track.id.toString())
                    .build()
                player.setMediaItem(mediaItem)
                
                player.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                
                player.prepare()
                player.playWhenReady = focusGranted
                
                if (_crossfadeSec.value <= 0f) {
                    prepareNextTrackForGapless()
                }

                if (focusGranted) {
                    applyPlaybackSpeedToEngines()
                    applyVolumeToEngines()
                    startProgressTracker(isSynth = false)
                } else {
                    applyPlaybackSpeedToEngines()
                    applyVolumeToEngines()
                }
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
        notifySessionChanged()
    }

    /** Calculates and adds the next track to ExoPlayer's playlist for gapless playback. */
    private fun prepareNextTrackForGapless() {
        val player = exoPlayer ?: return
        val current = _currentTrack.value ?: return
        val queue = _activeQueue.value
        if (queue.isEmpty() || current.uri == AudioRepository.SYNTH_URI) return

        // Clean up playlist: keep only the currently playing item at index 0
        val curIdx = player.currentMediaItemIndex
        if (curIdx > 0) {
            repeat(curIdx) {
                player.removeMediaItem(0)
            }
        }
        while (player.mediaItemCount > 1) {
            player.removeMediaItem(1)
        }

        // If repeating one song, ExoPlayer's REPEAT_MODE_ONE handles gapless internally
        if (_repeatMode.value == RepeatMode.ONE) return

        val currentIndex = queue.indexOfFirst { it.id == current.id }
        val nextIndex = when {
            _isShuffleEnabled.value -> pickShuffleIndex(queue.size, currentIndex)
            else -> {
                if (currentIndex >= queue.lastIndex) {
                    if (_repeatMode.value == RepeatMode.ALL) 0 else -1
                } else currentIndex + 1
            }
        }

        if (nextIndex != -1) {
            val nextTrack = queue[nextIndex]
            if (nextTrack.uri != AudioRepository.SYNTH_URI) {
                player.addMediaItem(
                    MediaItem.Builder()
                        .setUri(Uri.parse(nextTrack.uri))
                        .setMediaId(nextTrack.id.toString())
                        .build()
                )
            }
        }
    }

    /**
     * Restores a previously saved session: same track, queue, artwork metadata,
     * seek position, and play/pause intent — without bumping play count.
     */
    fun restoreSession(
        track: AudioTrackEntity,
        queue: List<AudioTrackEntity>,
        positionMs: Long,
        resumePlayback: Boolean,
        shuffleEnabled: Boolean = _isShuffleEnabled.value,
        repeatMode: RepeatMode = _repeatMode.value
    ) {
        restoringSession = true
        try {
            val restoredQueue = queue.ifEmpty { listOf(track) }
            _activeQueue.value = restoredQueue
            shuffleHistory.clear()
            _isShuffleEnabled.value = shuffleEnabled
            _repeatMode.value = repeatMode

            stopEngine(keepSession = true)
            _currentTrack.value = track
            autoCrossfadeTriggerTrackId = null
            val clampedPos = positionMs.coerceAtLeast(0L)
            _playbackPosition.value = clampedPos
            _playbackDuration.value = track.durationMs.coerceAtLeast(0L)
            pausedByTransientFocusLoss = false
            mediaSession?.isActive = true

            userWantsPlaying = resumePlayback
            val focusGranted = if (resumePlayback) requestPlaybackFocus() else false
            val shouldPlay = resumePlayback && focusGranted
            _isPlaying.value = shouldPlay

            if (track.uri == AudioRepository.SYNTH_URI) {
                applyVolumeToEngines()
                applySynthControls()
                synth.seekToMs(clampedPos)
                if (shouldPlay) {
                    synth.start()
                    startProgressTracker(isSynth = true)
                } else {
                    synth.pause()
                }
            } else {
                try {
                    val player = ensureExoPlayer()
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(track.uri))
                        .setMediaId(track.id.toString())
                        .build()
                    player.setMediaItem(mediaItem)
                    player.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    player.prepare()
                    player.seekTo(clampedPos)
                    player.playWhenReady = shouldPlay
                    
                    if (_crossfadeSec.value <= 0f) {
                        prepareNextTrackForGapless()
                    }

                    applyPlaybackSpeedToEngines()
                    applyVolumeToEngines()
                    if (shouldPlay) {
                        startProgressTracker(isSynth = false)
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to restore ExoPlayer session", e)
                    _isPlaying.value = false
                    userWantsPlaying = false
                }
            }

            updateSessionMetadata(track)
            updateSessionState()
            if (shouldPlay) {
                ensureServiceRunning()
            }
            // Paused restore: UI shows the track from StateFlows; no FGS/notification needed
        } finally {
            restoringSession = false
        }
        notifySessionChanged()
    }

    /** Snapshot suitable for [PlaybackSessionStore]; null when nothing is loaded. */
    fun captureSession(): PlaybackSession? {
        val track = _currentTrack.value ?: return null
        val queue = _activeQueue.value
        return PlaybackSession(
            trackUri = track.uri,
            queueUris = if (queue.isNotEmpty()) queue.map { it.uri } else listOf(track.uri),
            positionMs = _playbackPosition.value.coerceAtLeast(0L),
            wasPlaying = userWantsPlaying || _isPlaying.value,
            shuffleEnabled = _isShuffleEnabled.value,
            repeatMode = _repeatMode.value
        )
    }

    private fun notifySessionChanged() {
        if (restoringSession) return
        try {
            onSessionChanged?.invoke()
        } catch (e: Exception) {
            Log.w(tag, "onSessionChanged failed", e)
        }
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
            if (!requestPlaybackFocus()) {
                _isPlaying.value = false
                updateSessionState()
                return
            }
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
        notifySessionChanged()
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
        val next = queue[nextIndex]
        if (tryCrossfadeTo(next)) return
        playTrack(next)
    }

    private fun tryCrossfadeTo(nextTrack: AudioTrackEntity): Boolean {
        val currentTrack = _currentTrack.value ?: return false
        val fadeSec = _crossfadeSec.value
        if (fadeSec <= 0f) return false
        if (currentTrack.uri == AudioRepository.SYNTH_URI || nextTrack.uri == AudioRepository.SYNTH_URI) return false
        if (currentTrack.id == nextTrack.id) return false

        val primary = exoPlayer ?: return false
        if (!requestPlaybackFocus()) return false

        return try {
            val incoming = ensureCrossfadePlayer()
            incoming.stop()
            incoming.clearMediaItems()
            incoming.setMediaItem(MediaItem.fromUri(Uri.parse(nextTrack.uri)))
            incoming.repeatMode = Player.REPEAT_MODE_OFF
            incoming.volume = 0f
            incoming.prepare()
            applyPlaybackSpeedToEngines()
            incoming.playWhenReady = true
            incoming.play()

            _currentTrack.value = nextTrack
            _playbackPosition.value = 0L
            _playbackDuration.value = nextTrack.durationMs
            _isPlaying.value = true
            userWantsPlaying = true
            pausedByTransientFocusLoss = false
            updateSessionMetadata(nextTrack)
            updateSessionState()
            ensureServiceRunning()
            onTrackStarted?.invoke(nextTrack)
            notifySessionChanged()

            crossfadeJob?.cancel()
            crossfadeJob = scope.launch {
                val steps = (fadeSec * 20f).toInt().coerceIn(8, 240)
                val stepDelayMs = (fadeSec * 1000f / steps).toLong().coerceAtLeast(10L)
                val base = _volume.value.coerceIn(0f, 1f)
                val outgoingBase = if (primary.isPlaying) base else 0f

                repeat(steps) { idx ->
                    val t = (idx + 1).toFloat() / steps.toFloat()
                    primary.volume = outgoingBase * (1f - t)
                    incoming.volume = base * t
                    delay(stepDelayMs)
                }

                // Swap players so progress/completion listeners target the active one.
                primary.removeListener(playerListener)
                incoming.addListener(playerListener)
                exoPlayer = incoming
                crossfadePlayer = primary
                try {
                    primary.pause()
                    primary.stop()
                    primary.clearMediaItems()
                } catch (_: Exception) {
                }
                applyVolumeToEngines()
                startProgressTracker(isSynth = false)
            }
            true
        } catch (e: Exception) {
            Log.w(tag, "Crossfade transition failed", e)
            false
        }
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
        notifySessionChanged()
    }

    fun toggleShuffle() {
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        if (!_isShuffleEnabled.value) shuffleHistory.clear()
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
    }

    fun toggleLoop() {
        _repeatMode.value = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        exoPlayer?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
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

    fun setCrossfadeSec(sec: Float) {
        val old = _crossfadeSec.value
        _crossfadeSec.value = sec.coerceIn(0f, 10f)
        if (old <= 0f && _crossfadeSec.value > 0f) {
            // Transitioning from gapless to crossfade: clear pre-loaded items
            exoPlayer?.let { player ->
                while (player.mediaItemCount > 1) {
                    player.removeMediaItem(1)
                }
            }
        } else if (old > 0f && _crossfadeSec.value <= 0f) {
            // Transitioning from crossfade to gapless: pre-load next track
            prepareNextTrackForGapless()
        }
    }

    fun setPitchSemitones(semitones: Float) {
        _pitchSemitones.value = semitones.coerceIn(-6f, 6f)
        applyPlaybackSpeedToEngines()
    }

    fun setSleepFadeEnabled(enabled: Boolean) {
        _sleepFadeEnabled.value = enabled
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
        val fadeMs = 30_000L   // fade starts in the last 30 s
        sleepTimerJob = scope.launch {
            var remaining = durationMs
            var fadingVolume: Float? = null
            while (remaining > 0 && isActive) {
                delay(1000)
                remaining -= 1000
                _sleepTimerRemainingMs.value = remaining.coerceAtLeast(0)

                // Volume fade: when sleepFade is enabled and we're in the last fadeMs
                if (_sleepFadeEnabled.value && remaining in 0L..fadeMs) {
                    val fraction = remaining.toFloat() / fadeMs.toFloat()   // 1.0 -> 0.0
                    if (fadingVolume == null) fadingVolume = _volume.value
                    applyEngineVolumes(fadingVolume!! * fraction)
                }
            }
            if (isActive) {
                // Restore volume before stopping so next play starts normally
                if (fadingVolume != null) applyEngineVolumes(fadingVolume!!)
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

    /** Moves the queue item at [fromIndex] to [toIndex]. No-op if indices are out of range. */
    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _activeQueue.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= queue.size) return
        if (toIndex < 0 || toIndex >= queue.size) return
        if (fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        _activeQueue.value = queue
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
    }


    /**
     * Fully stops playback and releases ExoPlayer, synth, audio focus, effects,
     * and the media session. Clears the singleton so the next [get] creates a fresh engine.
     */
    fun release() {
        userWantsPlaying = false
        pausedByTransientFocusLoss = false
        volumeBeforeDuck = null
        sleepTimerJob?.cancel()
        progressJob?.cancel()
        visualizerJob?.cancel()
        artworkJob?.cancel()
        reassertJob?.cancel()
        sleepTimerJob = null
        progressJob = null
        visualizerJob = null
        artworkJob = null
        reassertJob = null
        stopEngine(keepSession = false)
        abandonPlaybackFocus()
        try {
            mediaSession?.isActive = false
            mediaSession?.setCallback(null)
            mediaSession?.release()
        } catch (e: Exception) {
            Log.w(tag, "MediaSession release failed", e)
        }
        mediaSession = null
        exoPlayer?.removeListener(playerListener)
        try {
            exoPlayer?.release()
        } catch (e: Exception) {
            Log.w(tag, "ExoPlayer release failed", e)
        }
        exoPlayer = null
        try {
            crossfadePlayer?.release()
        } catch (e: Exception) {
            Log.w(tag, "Crossfade ExoPlayer release failed", e)
        }
        crossfadePlayer = null
        onTrackStarted = null
        onSessionChanged = null
        _activeQueue.value = emptyList()
        _playbackPosition.value = 0L
        _playbackDuration.value = 0L
        _sleepTimerRemainingMs.value = 0L
        _waveformAmplitudes.value = List(24) { 0.1f }
        try {
            scope.cancel()
        } catch (_: Exception) {
        }
        clearInstance(this)
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
        // Convert semitone shift to pitch multiplier: 2^(n/12)
        val pitchMultiplier = Math.pow(2.0, (_pitchSemitones.value / 12.0).toDouble()).toFloat()
        exoPlayer?.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(speed, pitchMultiplier)
        )
        crossfadePlayer?.setPlaybackParameters(
            androidx.media3.common.PlaybackParameters(speed, pitchMultiplier)
        )
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
        crossfadeJob?.cancel()
        synth.stop()
        releaseAudioEffects()
        exoPlayer?.let { player ->
            try {
                player.stop()
                player.clearMediaItems()
            } catch (_: Exception) {
            }
        }
        crossfadePlayer?.let { player ->
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

                            val track = _currentTrack.value
                            val fadeMs = (_crossfadeSec.value * 1000f).toLong().coerceAtLeast(250L)
                            if (
                                track != null &&
                                track.uri != AudioRepository.SYNTH_URI &&
                                _crossfadeSec.value > 0f &&
                                _repeatMode.value != RepeatMode.ONE &&
                                autoCrossfadeTriggerTrackId != track.id &&
                                player.isPlaying
                            ) {
                                val remainingMs = dur - _playbackPosition.value
                                if (remainingMs in 1L..fadeMs) {
                                    autoCrossfadeTriggerTrackId = track.id
                                    nextTrack(fromUser = false)
                                    continue
                                }
                            }
                        }
                    }
                }
                // Position-only MediaSession update — avoid rebuilding the notification every tick
                updateSessionPlaybackState(notify = false)
                delay(500)
            }
        }
    }

    private fun startVisualizerLoop() {
        visualizerJob?.cancel()
        visualizerJob = scope.launch(Dispatchers.Default) {
            while (isActive) {
                val playing = _isPlaying.value
                val hasTrack = _currentTrack.value != null
                if (!playing && !hasTrack) {
                    // Idle — sleep hard to save CPU/battery
                    if (_waveformAmplitudes.value.any { it > 0.09f }) {
                        _waveformAmplitudes.value = List(24) { 0.08f }
                    }
                    delay(250)
                    continue
                }

                val track = _currentTrack.value
                if (playing && track?.uri == AudioRepository.SYNTH_URI) {
                    val rootAmp = (synth.lastWaveformValue + 1.0f) / 2.0f
                    val t = System.currentTimeMillis() * 0.012
                    visualizerTarget = List(24) { index ->
                        val phase = (index / 24f) * Math.PI * 2.0
                        val modulation = kotlin.math.sin(phase + t).toFloat()
                        val harmonic = kotlin.math.sin(phase * 2.0 + t * 1.4).toFloat() * 0.15f
                        ((rootAmp * 0.7f) + (modulation * 0.22f) + harmonic + 0.08f).coerceIn(0.08f, 1f)
                    }
                } else if (!playing) {
                    visualizerTarget = visualizerTarget.map { (it * 0.82f).coerceAtLeast(0.08f) }
                }

                // Smooth interpolation toward target for reactive, performant bars
                val current = _waveformAmplitudes.value
                val targetSnapshot = visualizerTarget
                val smoothed = List(24) { i ->
                    val target = targetSnapshot.getOrElse(i) { 0.1f }
                    val prev = current.getOrElse(i) { 0.1f }
                    val alpha = if (target >= prev) 0.55f else 0.28f
                    (prev + (target - prev) * alpha).coerceIn(0.08f, 1f)
                }
                _waveformAmplitudes.value = smoothed
                delay(if (playing) 33L else 120L)
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
                maybeUpdateNotification(force = true)
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
        updateSessionPlaybackState(notify = true)
    }

    private fun updateSessionPlaybackState(notify: Boolean) {
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
        if (notify) {
            maybeUpdateNotification(force = true)
        }
    }

    private fun maybeUpdateNotification(force: Boolean = false) {
        val track = _currentTrack.value
        val key = "${track?.id}|${_isPlaying.value}"
        val now = SystemClock.elapsedRealtime()
        if (!force && key == lastNotificationKey && now - lastNotificationAt < 2_000L) {
            return
        }
        lastNotificationKey = key
        lastNotificationAt = now
        PlaybackService.updateNotification(appContext)
        try {
            GlassPlayerWidget.notifyUpdate(appContext)
        } catch (_: Exception) {
            // Widgets are optional; playback must never depend on AppWidgetManager.
        }
    }

    /** @return true when audio focus was granted immediately. */
    private fun requestPlaybackFocus(): Boolean {
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
        // Keep user intent when focus is delayed — AUDIOFOCUS_GAIN will resume
        return hasAudioFocus
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
        if (_currentTrack.value == null) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastEnsureServiceAt < 1_500L) return
        lastEnsureServiceAt = now
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
            maybeUpdateNotification(force = true)
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

        private fun clearInstance(engine: PlayerEngine) {
            synchronized(this) {
                if (INSTANCE === engine) {
                    INSTANCE = null
                }
            }
        }
    }
}
