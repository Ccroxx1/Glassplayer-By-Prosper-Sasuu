<<<<<<< HEAD
@file:OptIn(UnstableApi::class)

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
import android.os.PowerManager
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
import androidx.media3.common.util.UnstableApi
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
=======
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

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

<<<<<<< HEAD
    data class PlaybackProgress(
        val position: Long = 0L,
        val duration: Long = 0L,
        val isPlaying: Boolean = false
    )

    private val _playbackProgress = MutableStateFlow(PlaybackProgress())
    val playbackProgress: StateFlow<PlaybackProgress> = _playbackProgress.asStateFlow()

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition: StateFlow<Long> = _playbackPosition.asStateFlow()

    private val _playbackDuration = MutableStateFlow(0L)
    val playbackDuration: StateFlow<Long> = _playbackDuration.asStateFlow()

<<<<<<< HEAD
    private fun publishPosition(
        position: Long = _playbackPosition.value,
        duration: Long = _playbackDuration.value,
        isPlaying: Boolean = _isPlaying.value
    ) {
        val safePos = position.coerceAtLeast(0L)
        _playbackPosition.value = safePos
        if (duration > 0 && duration != C.TIME_UNSET) {
            _playbackDuration.value = duration
        }
        _playbackProgress.value = PlaybackProgress(
            position = safePos,
            duration = _playbackDuration.value,
            isPlaying = isPlaying
        )
    }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    private val _isShuffleEnabled = MutableStateFlow(false)
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(RepeatMode.OFF)
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    private val _activeQueue = MutableStateFlow<List<AudioTrackEntity>>(emptyList())
    val activeQueue: StateFlow<List<AudioTrackEntity>> = _activeQueue.asStateFlow()

<<<<<<< HEAD
    private var originalQueue: List<AudioTrackEntity> = emptyList()
    private var shuffledQueue: List<AudioTrackEntity> = emptyList()
    private var shuffleIndex: Int = -1

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            val current = _currentTrack.value
            if (current != null && (current.isYouTubeTrack() || current.uri == AudioRepository.SYNTH_URI)) return
            val player = exoPlayer ?: return
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration
            Log.d(
                tag,
                "LOCAL PLAYER onPlaybackStateChanged: isPlaying=${player.isPlaying} currentPosition=$pos duration=$dur playbackState=$playbackState mediaItem=${player.currentMediaItem?.mediaId}"
            )
            when (playbackState) {
                Player.STATE_READY -> {
                    val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value
                    publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)
=======
            val player = exoPlayer ?: return
            when (playbackState) {
                Player.STATE_READY -> {
                    val dur = player.duration
                    if (dur > 0 && dur != C.TIME_UNSET) {
                        _playbackDuration.value = dur
                    }
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                    attachAudioEffects(player.audioSessionId)
                    // Resume only when the user still wants play and we are not paused for a call/focus loss
                    if (userWantsPlaying && !pausedByTransientFocusLoss && !player.isPlaying && hasAudioFocus) {
                        player.playWhenReady = true
                        player.play()
                    }
                    updateSessionMetadata(_currentTrack.value ?: return)
                    updateSessionState()
                }
<<<<<<< HEAD
                Player.STATE_BUFFERING -> {
                    updateSessionState()
                }
                Player.STATE_ENDED -> {
                    _isPlaying.value = false
                    stopVisualizer()
                    progressJob?.cancel()
                    val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value
                    publishPosition(position = validDur, duration = validDur, isPlaying = false)
=======
                Player.STATE_ENDED -> {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                    // Only handle completion if the playlist is empty or we reached the end
                    if (player.mediaItemCount <= 1 || player.nextMediaItemIndex == C.INDEX_UNSET) {
                        onTrackCompleted()
                    }
<<<<<<< HEAD
                    updateSessionState()
                }
                Player.STATE_IDLE -> {
                    _isPlaying.value = false
                    stopVisualizer()
                    progressJob?.cancel()
                    publishPosition(position = pos, duration = _playbackDuration.value, isPlaying = false)
                    updateSessionState()
                }
=======
                }
                else -> Unit
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
<<<<<<< HEAD
            val current = _currentTrack.value
            if (current != null && (current.isYouTubeTrack() || current.uri == AudioRepository.SYNTH_URI)) return
            val player = exoPlayer ?: return
            val trackId = mediaItem?.mediaId?.toIntOrNull() ?: return
            val track = _activeQueue.value.find { it.id == trackId } ?: return
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration
            val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else track.durationMs

            Log.d(tag, "Local onMediaItemTransition: track=${track.title}, reason=$reason, pos=$pos, dur=$validDur")
=======
            val player = exoPlayer ?: return
            val trackId = mediaItem?.mediaId?.toIntOrNull() ?: return
            val track = _activeQueue.value.find { it.id == trackId } ?: return
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                // Gapless auto-transition
                _currentTrack.value = track
<<<<<<< HEAD
                if (_isShuffleEnabled.value) {
                    shuffleIndex = shuffledQueue.indexOfFirst { it.id == track.id }
                }
                publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)
=======
                _playbackPosition.value = 0L
                val dur = player.duration
                _playbackDuration.value = if (dur > 0 && dur != C.TIME_UNSET) dur else track.durationMs
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                updateSessionMetadata(track)
                updateSessionState()
                ensureServiceRunning()
                onTrackStarted?.invoke(track)
                notifySessionChanged()

                // Pre-load the next track into the playlist if crossfade is disabled
                if (_crossfadeSec.value <= 0f) {
                    prepareNextTrackForGapless()
                }
<<<<<<< HEAD
            } else {
                publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            val player = exoPlayer ?: return
            val current = _currentTrack.value ?: return
            if (current.isYouTubeTrack() || current.uri == AudioRepository.SYNTH_URI) return

            Log.d(
                tag,
                "LOCAL PLAYER onPositionDiscontinuity: reason=$reason isPlaying=${player.isPlaying} currentPosition=${player.currentPosition} duration=${player.duration} playbackState=${player.playbackState} mediaItem=${player.currentMediaItem?.mediaId}"
            )
            val pos = player.currentPosition.coerceAtLeast(0L)
            val dur = player.duration
            val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value
            publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)
            if (player.isPlaying) {
                startLocalProgressTracker()
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            val current = _currentTrack.value ?: return
            if (current.isYouTubeTrack() || current.uri == AudioRepository.SYNTH_URI) return

            if (events.containsAny(
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_IS_PLAYING_CHANGED,
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_POSITION_DISCONTINUITY,
                Player.EVENT_MEDIA_ITEM_TRANSITION
            )) {
                val pos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration
                val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value
                Log.d(
                    tag,
                    "LOCAL PLAYER onEvents: isPlaying=${player.isPlaying} currentPosition=$pos duration=$validDur playbackState=${player.playbackState} mediaItem=${player.currentMediaItem?.mediaId}"
                )
                publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
<<<<<<< HEAD
            val current = _currentTrack.value
            if (current?.uri == AudioRepository.SYNTH_URI || current?.isYouTubeTrack() == true) return
            _isPlaying.value = isPlaying
            Log.d(
                tag,
                "LOCAL PLAYER onIsPlayingChanged: isPlaying=$isPlaying currentPosition=${exoPlayer?.currentPosition} duration=${exoPlayer?.duration} playbackState=${exoPlayer?.playbackState} mediaItem=${exoPlayer?.currentMediaItem?.mediaId}"
            )
            if (isPlaying) {
                pausedByTransientFocusLoss = false
                acquireWakeLock()
                ensureServiceRunning()
                startLocalProgressTracker()
                startVisualizerLoop()
            } else {
                releaseWakeLock()
                stopVisualizer()
                progressJob?.cancel()
                exoPlayer?.let { player ->
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value
                    publishPosition(position = pos, duration = validDur, isPlaying = false)
                }
                if (userWantsPlaying && !pausedByTransientFocusLoss && exoPlayer?.playbackState == Player.STATE_IDLE) {
                    // Recover from unexpected idle
                    schedulePlaybackReassert()
                }
            }
            updateSessionState()
            notifySessionChanged()
        }

        override fun onPlayerError(error: PlaybackException) {
            val current = _currentTrack.value
            if (current != null && (current.isYouTubeTrack() || current.uri == AudioRepository.SYNTH_URI)) return
            Log.e(tag, "ExoPlayer error: ${error.message}", error)
            _isPlaying.value = false
            stopVisualizer()
            progressJob?.cancel()
            publishPosition(isPlaying = false)
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            updateSessionState()
        }
    }

<<<<<<< HEAD
    private var wakeLock: PowerManager.WakeLock? = null

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GlassPlayer:BackgroundAudio")?.apply {
                setReferenceCounted(false)
            }
        }
        try {
            if (wakeLock?.isHeld == false) {
                wakeLock?.acquire(3 * 60 * 60 * 1000L)
            }
        } catch (e: Exception) {
            Log.w(tag, "WakeLock acquire failed", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.w(tag, "WakeLock release failed", e)
        }
    }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        val track = _currentTrack.value ?: return
        val currentlyPlaying = when {
            track.uri == AudioRepository.SYNTH_URI -> _isPlaying.value
            track.isYouTubeTrack() -> _isPlaying.value
            else -> exoPlayer?.isPlaying == true
        }
        if (currentlyPlaying) {
            _isPlaying.value = true
            return
        }

        if (!userWantsPlaying || pausedByTransientFocusLoss) return
        if (!requestPlaybackFocus()) return

        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.start()
            applySynthControls()
            _isPlaying.value = true
            startProgressTracker(isSynth = true)
            startVisualizerLoop()
            ensureServiceRunning()
            updateSessionState()
            return
        }

        if (track.isYouTubeTrack()) {
            _isPlaying.value = true
            publishPosition(isPlaying = true)
            YouTubeBridge.play()
            ensureServiceRunning()
            updateSessionState()
            return
        }

        val player = exoPlayer ?: return
        if (!player.isPlaying) {
            if (player.playbackState == Player.STATE_IDLE) {
                if (player.mediaItemCount == 0) {
                    val mediaItem = MediaItem.Builder()
                        .setUri(Uri.parse(track.uri))
                        .setMediaId(track.id.toString())
                        .build()
                    player.setMediaItem(mediaItem)
                }
                player.prepare()
            } else if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
                player.prepare()
            }
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            player.playWhenReady = true
            try {
                player.play()
            } catch (e: Exception) {
                Log.w(tag, "reassert play failed", e)
            }
<<<<<<< HEAD
=======
            _isPlaying.value = true
            startProgressTracker(isSynth = false)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        exoPlayer?.let { player ->
            val validDur = if (player.duration > 0 && player.duration != C.TIME_UNSET) player.duration else _playbackDuration.value
            publishPosition(position = player.currentPosition.coerceAtLeast(0L), duration = validDur, isPlaying = false)
        }
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
                override fun onSetShuffleMode(shuffleMode: Int) {
                    setShuffleEnabled(
                        shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL ||
                        shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_GROUP
                    )
                }

                override fun onSetRepeatMode(repeatModeInt: Int) {
                    when (repeatModeInt) {
                        PlaybackStateCompat.REPEAT_MODE_ONE -> setRepeatMode(RepeatMode.ONE)
                        PlaybackStateCompat.REPEAT_MODE_ALL, PlaybackStateCompat.REPEAT_MODE_GROUP -> setRepeatMode(RepeatMode.ALL)
                        else -> setRepeatMode(RepeatMode.OFF)
                    }
                }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        YouTubeBridge.onStateChanged = { playing ->
            val current = _currentTrack.value
            if (current != null && current.isYouTubeTrack()) {
                _isPlaying.value = playing
                publishPosition(isPlaying = playing)
                if (playing) {
                    userWantsPlaying = true
                    acquireWakeLock()
                    ensureServiceRunning()
                    startVisualizerLoop()
                } else {
                    releaseWakeLock()
                    stopVisualizer()
                }
                updateSessionState()
                notifySessionChanged()
            }
        }
        YouTubeBridge.onTimeUpdated = { curSec, durSec ->
            val current = _currentTrack.value
            if (current != null && current.isYouTubeTrack()) {
                val pos = (curSec * 1000).toLong().coerceAtLeast(0L)
                val dur = if (durSec > 0) (durSec * 1000).toLong() else _playbackDuration.value
                publishPosition(position = pos, duration = dur, isPlaying = _isPlaying.value)
            } else {
                Log.d(tag, "Ignoring YouTube onTimeUpdated (track is ${current?.title ?: "null"}): curSec=$curSec, durSec=$durSec")
            }
        }
        YouTubeBridge.onTrackEnded = {
            val current = _currentTrack.value
            if (current != null && current.isYouTubeTrack()) {
                onTrackCompleted()
            }
        }
        YouTubeBridge.onError = { code ->
            Log.w(tag, "YouTube playback error code=$code")
            val current = _currentTrack.value
            if (current != null && current.isYouTubeTrack()) {
                _isPlaying.value = false
                userWantsPlaying = false
                // Unavailable/private/deleted videos: advance within the current queue.
                if (code == 100 || code == 101 || code == 150) {
                    nextTrack(fromUser = false)
                }
                updateSessionState()
                notifySessionChanged()
            }
        }
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        try {
            updateSessionState()
        } catch (e: Exception) {
            Log.w(tag, "Initial session state failed", e)
        }
    }

<<<<<<< HEAD
    private fun createMediaSourceFactory(): androidx.media3.exoplayer.source.MediaSource.Factory {
        val httpFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(appContext, httpFactory)
        return androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory)
    }

    private fun ensureExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = ExoPlayer.Builder(appContext)
            .setMediaSourceFactory(createMediaSourceFactory())
=======
    private fun ensureExoPlayer(): ExoPlayer {
        exoPlayer?.let { return it }
        val player = ExoPlayer.Builder(appContext)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            .setMediaSourceFactory(createMediaSourceFactory())
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        originalQueue = originalQueue.filter { !it.isSameTrack(track) }
        shuffledQueue = shuffledQueue.filter { !it.isSameTrack(track) }

        val activeList = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
        _activeQueue.value = activeList

        if (track.isSameTrack(current)) {
            if (activeList.isNotEmpty()) {
                val nextTrack = if (_isShuffleEnabled.value) {
                    shuffleIndex = shuffleIndex.coerceIn(0, shuffledQueue.lastIndex)
                    shuffledQueue[shuffleIndex]
                } else {
                    activeList.first()
                }
                playTrackInternal(nextTrack)
            } else {
                stopPlaybackAtQueueEnd()
                _currentTrack.value = null
            }
        } else {
            if (_isShuffleEnabled.value && current != null) {
                shuffleIndex = shuffledQueue.indexOfFirst { it.isSameTrack(current) }
            }
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        originalQueue = originalQueue.filter(::isAllowed)
        shuffledQueue = shuffledQueue.filter(::isAllowed)

        val pruned = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
=======
        val pruned = _activeQueue.value.filter(::isAllowed)
        if (pruned.size == _activeQueue.value.size &&
            (current == null || isAllowed(current))
        ) {
            return
        }

>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        _activeQueue.value = pruned

        if (current != null && !isAllowed(current)) {
            if (pruned.isNotEmpty()) {
<<<<<<< HEAD
                val next = if (_isShuffleEnabled.value) {
                    shuffleIndex = shuffleIndex.coerceIn(0, shuffledQueue.lastIndex)
                    shuffledQueue[shuffleIndex]
                } else {
                    pruned.first()
                }
                playTrackInternal(next)
            } else {
                stopPlaybackAtQueueEnd()
                _currentTrack.value = null
            }
        } else {
            if (_isShuffleEnabled.value && current != null) {
                shuffleIndex = shuffledQueue.indexOfFirst { it.id == current.id }
            }
=======
                playTrack(pruned.first(), pruned)
            } else {
                userWantsPlaying = false
                togglePlayPause(forcePause = true)
                _currentTrack.value = null
                updateSessionState()
                notifySessionChanged()
            }
        } else {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        originalQueue = originalQueue.filterNot { it.uri in missingUris }
        shuffledQueue = shuffledQueue.filterNot { it.uri in missingUris }

        val pruned = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
        _activeQueue.value = pruned
        val currentMissing = current?.uri?.let { it in missingUris } == true

        if (currentMissing) {
            if (pruned.isNotEmpty()) {
                val next = if (_isShuffleEnabled.value) {
                    shuffleIndex = shuffleIndex.coerceIn(0, shuffledQueue.lastIndex)
                    shuffledQueue[shuffleIndex]
                } else {
                    pruned.first()
                }
                playTrackInternal(next)
=======
        val pruned = _activeQueue.value.filterNot { it.uri in missingUris }
        val currentMissing = current?.uri?.let { it in missingUris } == true

        if (pruned.size == _activeQueue.value.size && !currentMissing) return

        _activeQueue.value = pruned

        if (currentMissing) {
            if (pruned.isNotEmpty()) {
                val next = pruned.first()
                playTrack(next, pruned)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                if (!wasPlaying) {
                    togglePlayPause(forcePause = true)
                }
            } else {
<<<<<<< HEAD
                stopPlaybackAtQueueEnd()
                _currentTrack.value = null
            }
        } else {
            if (_isShuffleEnabled.value && current != null) {
                shuffleIndex = shuffledQueue.indexOfFirst { it.id == current.id }
            }
=======
                userWantsPlaying = false
                togglePlayPause(forcePause = true)
                _currentTrack.value = null
                _playbackPosition.value = 0L
                _playbackDuration.value = 0L
                updateSessionState()
                notifySessionChanged()
            }
        } else {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        if (track.isSameTrack(current)) return

        val oQueue = originalQueue.toMutableList()
        oQueue.removeAll { it.isSameTrack(track) }
        var curOrigIdx = oQueue.indexOfFirst { it.isSameTrack(current) }
        if (curOrigIdx < 0) {
            oQueue.add(0, current)
            curOrigIdx = 0
        }
        oQueue.add(curOrigIdx + 1, track)
        originalQueue = oQueue

        if (_isShuffleEnabled.value) {
            val sQueue = shuffledQueue.toMutableList()
            sQueue.removeAll { it.isSameTrack(track) }
            val insertIdx = (shuffleIndex + 1).coerceIn(0, sQueue.size)
            sQueue.add(insertIdx, track)
            shuffledQueue = sQueue
            _activeQueue.value = shuffledQueue
        } else {
            _activeQueue.value = originalQueue
        }

=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
    }

<<<<<<< HEAD
    private fun buildNewShuffledQueue(startingTrack: AudioTrackEntity?) {
        if (originalQueue.isEmpty()) {
            shuffledQueue = emptyList()
            shuffleIndex = -1
            return
        }
        if (originalQueue.size == 1) {
            shuffledQueue = originalQueue.toList()
            shuffleIndex = 0
            return
        }

        val list = originalQueue.toMutableList()
        val start = startingTrack ?: list.random()
        list.removeAll { it.isSameTrack(start) }
        list.shuffle()
        shuffledQueue = listOf(start) + list
        shuffleIndex = 0
    }

    fun playTrack(track: AudioTrackEntity, customQueue: List<AudioTrackEntity>? = null) {
        val isSameQueue = customQueue != null && (
            customQueue === _activeQueue.value || 
            customQueue === shuffledQueue || 
            customQueue === originalQueue ||
            (originalQueue.any { it.isSameTrack(track) } && customQueue.size == originalQueue.size && customQueue.map { it.uri }.toSet() == originalQueue.map { it.uri }.toSet())
        )

        if (customQueue != null && !isSameQueue) {
            originalQueue = customQueue.ifEmpty { listOf(track) }
            if (_isShuffleEnabled.value) {
                buildNewShuffledQueue(startingTrack = track)
                _activeQueue.value = shuffledQueue
            } else {
                _activeQueue.value = originalQueue
                shuffleIndex = -1
            }
        } else {
            if (originalQueue.isEmpty() || originalQueue.none { it.isSameTrack(track) }) {
                val list = libraryFallback()
                originalQueue = if (list.isNotEmpty()) list else listOf(track)
            }
            if (_isShuffleEnabled.value) {
                val existingIdx = shuffledQueue.indexOfFirst { it.isSameTrack(track) }
                if (existingIdx >= 0) {
                    shuffleIndex = existingIdx
                } else {
                    buildNewShuffledQueue(startingTrack = track)
                    _activeQueue.value = shuffledQueue
                }
            } else {
                _activeQueue.value = originalQueue
                shuffleIndex = -1
            }
        }
        shuffleHistory.clear()

        playTrackInternal(track)
    }

    fun addToQueue(track: AudioTrackEntity) {
        val oQueue = originalQueue.toMutableList()
        oQueue.removeAll { it.id == track.id }
        oQueue.add(track)
        originalQueue = oQueue

        if (_isShuffleEnabled.value) {
            val sQueue = shuffledQueue.toMutableList()
            sQueue.removeAll { it.id == track.id }
            sQueue.add(track)
            shuffledQueue = sQueue
            _activeQueue.value = shuffledQueue
        } else {
            _activeQueue.value = originalQueue
        }
        updateSessionState()
        notifySessionChanged()
    }

    private fun playYouTubeTrack(track: AudioTrackEntity) {
        stopEngine(keepSession = true)
        _currentTrack.value = track
        publishPosition(position = 0L, duration = track.durationMs, isPlaying = false)
        userWantsPlaying = true
        _isPlaying.value = false
        stopVisualizer()
        requestPlaybackFocus()
        updateSessionMetadata(track)
        updateSessionState()
        ensureServiceRunning()
        onTrackStarted?.invoke(track)
        notifySessionChanged()

        val videoId = track.youtubeVideoId() ?: track.category.removePrefix("yt:").ifBlank { track.id.toString() }
        YouTubeBridge.loadVideo(videoId)
    }

    private fun playTrackInternal(track: AudioTrackEntity) {
        when (track.trackSource()) {
            TrackSource.YOUTUBE -> {
                playYouTubeTrack(track)
                return
            }
            TrackSource.SYNTH, TrackSource.LOCAL -> Unit
        }

        if (!track.canPlayWithExoPlayer() && track.trackSource() != TrackSource.SYNTH) {
            Log.w(tag, "Cannot play track with empty or non-stream URI: ${track.title}")
            _currentTrack.value = track
            _isPlaying.value = false
            userWantsPlaying = false
            stopVisualizer()
            publishPosition(position = 0L, duration = track.durationMs, isPlaying = false)
            updateSessionMetadata(track)
            updateSessionState()
            notifySessionChanged()
            return
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        }

        stopEngine(keepSession = true)
        _currentTrack.value = track
        autoCrossfadeTriggerTrackId = null
        userWantsPlaying = true
<<<<<<< HEAD
        publishPosition(position = 0L, duration = track.durationMs, isPlaying = false)
        pausedByTransientFocusLoss = false
        mediaSession?.isActive = true
        val focusGranted = requestPlaybackFocus()
        _isPlaying.value = false
        stopVisualizer()
=======
        _playbackPosition.value = 0L
        pausedByTransientFocusLoss = false
        mediaSession?.isActive = true
        val focusGranted = requestPlaybackFocus()
        _isPlaying.value = focusGranted
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

        if (track.uri == AudioRepository.SYNTH_URI) {
            _playbackDuration.value = track.durationMs
            applyVolumeToEngines()
            applySynthControls()
            if (focusGranted) {
                synth.start()
<<<<<<< HEAD
                _isPlaying.value = true
                startProgressTracker(isSynth = true)
                startVisualizerLoop()
=======
                startProgressTracker(isSynth = true)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD

                player.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF

                player.prepare()
                player.playWhenReady = focusGranted

=======
                
                player.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                
                player.prepare()
                player.playWhenReady = focusGranted
                
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                if (_crossfadeSec.value <= 0f) {
                    prepareNextTrackForGapless()
                }

<<<<<<< HEAD
                applyPlaybackSpeedToEngines()
                applyVolumeToEngines()
=======
                if (focusGranted) {
                    applyPlaybackSpeedToEngines()
                    applyVolumeToEngines()
                    startProgressTracker(isSynth = false)
                } else {
                    applyPlaybackSpeedToEngines()
                    applyVolumeToEngines()
                }
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            } catch (e: Exception) {
                Log.e(tag, "Failed to start ExoPlayer", e)
                _isPlaying.value = false
                userWantsPlaying = false
<<<<<<< HEAD
                stopVisualizer()
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        val queue = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
        if (queue.isEmpty() || current.uri == AudioRepository.SYNTH_URI) return

        val curIdx = player.currentMediaItemIndex
        if (curIdx > 0) {
            repeat(curIdx) { player.removeMediaItem(0) }
=======
        val queue = _activeQueue.value
        if (queue.isEmpty() || current.uri == AudioRepository.SYNTH_URI) return

        // Clean up playlist: keep only the currently playing item at index 0
        val curIdx = player.currentMediaItemIndex
        if (curIdx > 0) {
            repeat(curIdx) {
                player.removeMediaItem(0)
            }
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        }
        while (player.mediaItemCount > 1) {
            player.removeMediaItem(1)
        }

<<<<<<< HEAD
        if (_repeatMode.value == RepeatMode.ONE) return

        val nextTrack: AudioTrackEntity? = if (_isShuffleEnabled.value) {
            val nextIdx = if (shuffleIndex >= 0) shuffleIndex + 1 else shuffledQueue.indexOfFirst { it.id == current.id } + 1
            if (nextIdx < shuffledQueue.size) {
                shuffledQueue[nextIdx]
            } else if (_repeatMode.value == RepeatMode.ALL && originalQueue.isNotEmpty()) {
                shuffledQueue.firstOrNull()
            } else {
                null
            }
        } else {
            val cur = originalQueue.indexOfFirst { it.id == current.id }
            if (cur != -1 && cur + 1 < originalQueue.size) {
                originalQueue[cur + 1]
            } else if (_repeatMode.value == RepeatMode.ALL && originalQueue.isNotEmpty()) {
                originalQueue[0]
            } else {
                null
            }
        }

        if (nextTrack != null && nextTrack.uri.isNotBlank() && nextTrack.uri != AudioRepository.SYNTH_URI) {
            player.addMediaItem(
                MediaItem.Builder()
                    .setUri(Uri.parse(nextTrack.uri))
                    .setMediaId(nextTrack.id.toString())
                    .build()
            )
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            originalQueue = queue.ifEmpty { listOf(track) }
            _isShuffleEnabled.value = shuffleEnabled
            _repeatMode.value = repeatMode
            shuffleHistory.clear()

            if (shuffleEnabled) {
                buildNewShuffledQueue(startingTrack = track)
                _activeQueue.value = shuffledQueue
            } else {
                _activeQueue.value = originalQueue
                shuffleIndex = -1
            }
=======
            val restoredQueue = queue.ifEmpty { listOf(track) }
            _activeQueue.value = restoredQueue
            shuffleHistory.clear()
            _isShuffleEnabled.value = shuffleEnabled
            _repeatMode.value = repeatMode
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

            stopEngine(keepSession = true)
            _currentTrack.value = track
            autoCrossfadeTriggerTrackId = null
            val clampedPos = positionMs.coerceAtLeast(0L)
<<<<<<< HEAD
=======
            _playbackPosition.value = clampedPos
            _playbackDuration.value = track.durationMs.coerceAtLeast(0L)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            pausedByTransientFocusLoss = false
            mediaSession?.isActive = true

            userWantsPlaying = resumePlayback
            val focusGranted = if (resumePlayback) requestPlaybackFocus() else false
            val shouldPlay = resumePlayback && focusGranted
            _isPlaying.value = shouldPlay
<<<<<<< HEAD
            publishPosition(position = clampedPos, duration = track.durationMs.coerceAtLeast(0L), isPlaying = shouldPlay)
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

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
<<<<<<< HEAD
                    player.repeatMode = if (repeatMode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
                    player.prepare()
                    player.seekTo(clampedPos)
                    player.playWhenReady = shouldPlay
                    if (shouldPlay) {
                        applyPlaybackSpeedToEngines()
                        applyVolumeToEngines()
                        startProgressTracker(isSynth = false)
                    } else {
                        applyPlaybackSpeedToEngines()
                        applyVolumeToEngines()
                    }
                    if (_crossfadeSec.value <= 0f) {
                        prepareNextTrackForGapless()
                    }
                } catch (e: Exception) {
                    Log.e(tag, "Failed to restore ExoPlayer", e)
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                    _isPlaying.value = false
                    userWantsPlaying = false
                }
            }

            updateSessionMetadata(track)
            updateSessionState()
            if (shouldPlay) {
                ensureServiceRunning()
            }
<<<<<<< HEAD
=======
            // Paused restore: UI shows the track from StateFlows; no FGS/notification needed
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        } finally {
            restoringSession = false
        }
        notifySessionChanged()
    }

    /** Snapshot suitable for [PlaybackSessionStore]; null when nothing is loaded. */
    fun captureSession(): PlaybackSession? {
        val track = _currentTrack.value ?: return null
<<<<<<< HEAD
        val queue = originalQueue.ifEmpty { _activeQueue.value }
=======
        val queue = _activeQueue.value
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        val currentPlaying = when {
            track.uri == AudioRepository.SYNTH_URI -> _isPlaying.value
            track.isYouTubeTrack() -> _isPlaying.value
            else -> exoPlayer?.isPlaying == true
        }
        val shouldPause = when {
            forcePause -> true
            forcePlay -> false
            else -> currentPlaying
        }

        if (shouldPause) {
=======
        val shouldPause = when {
            forcePause -> true
            forcePlay -> false
            else -> _isPlaying.value
        }

        if (shouldPause) {
            // Focus-loss pauses go through pauseForFocusLoss(), not here — so this is always intentional
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            userWantsPlaying = false
            pausedByTransientFocusLoss = false
            _isPlaying.value = false
            if (track.uri == AudioRepository.SYNTH_URI) {
                synth.pause()
<<<<<<< HEAD
            } else if (track.isYouTubeTrack()) {
                publishPosition(isPlaying = false)
                YouTubeBridge.pause()
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            } else {
                exoPlayer?.pause()
            }
            progressJob?.cancel()
<<<<<<< HEAD
            stopVisualizer()
            exoPlayer?.let { player ->
                val validDur = if (player.duration > 0 && player.duration != C.TIME_UNSET) player.duration else _playbackDuration.value
                publishPosition(position = player.currentPosition.coerceAtLeast(0L), duration = validDur, isPlaying = false)
            }
        } else {
            userWantsPlaying = true
            pausedByTransientFocusLoss = false
            val focusGranted = requestPlaybackFocus()
            if (!focusGranted) {
                Log.w(tag, "Audio focus not granted")
                return
            }
            if (track.uri == AudioRepository.SYNTH_URI) {
                synth.start()
                applySynthControls()
                _isPlaying.value = true
                startProgressTracker(isSynth = true)
                startVisualizerLoop()
            } else if (track.isYouTubeTrack()) {
                _isPlaying.value = true
                publishPosition(isPlaying = true)
                YouTubeBridge.play()
            } else {
                val player = ensureExoPlayer()
                if (player.playbackState == Player.STATE_IDLE) {
                    if (player.mediaItemCount == 0) {
                        val mediaItem = MediaItem.Builder()
                            .setUri(Uri.parse(track.uri))
                            .setMediaId(track.id.toString())
                            .build()
                        player.setMediaItem(mediaItem)
                    }
                    player.prepare()
                } else if (player.playbackState == Player.STATE_ENDED) {
                    player.seekTo(0)
                    player.prepare()
                }
                player.playWhenReady = true
                player.play()
                applyPlaybackSpeedToEngines()
                applyVolumeToEngines()
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            }
            ensureServiceRunning()
        }
        updateSessionState()
        notifySessionChanged()
    }

    fun nextTrack(fromUser: Boolean = true) {
<<<<<<< HEAD
        val current = _currentTrack.value
        if (originalQueue.isEmpty() || current == null) return

        // 1. REPEAT ONE:
        if (!fromUser && _repeatMode.value == RepeatMode.ONE) {
=======
        val queue = _activeQueue.value
        val current = _currentTrack.value
        if (queue.isEmpty()) return

        if (!fromUser && _repeatMode.value == RepeatMode.ONE && current != null) {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            replayCurrentTrack()
            return
        }

<<<<<<< HEAD
        // 2. SHUFFLE MODE:
        if (_isShuffleEnabled.value) {
            if (shuffleIndex < 0) {
                shuffleIndex = shuffledQueue.indexOfFirst { it.isSameTrack(current) }
            }
            val nextIdx = shuffleIndex + 1

            if (nextIdx < shuffledQueue.size) {
                if (shuffleIndex >= 0) shuffleHistory.addLast(shuffleIndex)
                if (shuffleHistory.size > 64) shuffleHistory.removeFirst()
                shuffleIndex = nextIdx
                val nextTrack = shuffledQueue[shuffleIndex]
                if (tryCrossfadeTo(nextTrack)) return
                playTrackInternal(nextTrack)
            } else {
                if (_repeatMode.value == RepeatMode.ALL) {
                    val nextCycle = originalQueue.toMutableList()
                    if (nextCycle.size > 1) {
                        val lastTrack = shuffledQueue.lastOrNull()
                        val candidates = nextCycle.filter { !it.isSameTrack(lastTrack) }
                        val firstTrack = if (candidates.isNotEmpty()) candidates.random() else nextCycle.random()
                        nextCycle.removeAll { it.isSameTrack(firstTrack) }
                        nextCycle.shuffle()
                        shuffledQueue = listOf(firstTrack) + nextCycle
                    } else {
                        shuffledQueue = nextCycle
                    }
                    shuffleHistory.clear()
                    shuffleIndex = 0
                    _activeQueue.value = shuffledQueue
                    val nextTrack = shuffledQueue[0]
                    if (tryCrossfadeTo(nextTrack)) return
                    playTrackInternal(nextTrack)
                } else {
                    stopPlaybackAtQueueEnd()
                }
            }
            return
        }

        // 3. NORMAL / REPEAT ALL (Sequential order):
        val curIdx = originalQueue.indexOfFirst { it.isSameTrack(current) }
        val nextIdx = curIdx + 1

        if (curIdx != -1 && nextIdx < originalQueue.size) {
            val nextTrack = originalQueue[nextIdx]
            if (tryCrossfadeTo(nextTrack)) return
            playTrackInternal(nextTrack)
        } else {
            if (_repeatMode.value == RepeatMode.ALL && originalQueue.isNotEmpty()) {
                val nextTrack = originalQueue[0]
                if (tryCrossfadeTo(nextTrack)) return
                playTrackInternal(nextTrack)
            } else {
                stopPlaybackAtQueueEnd()
            }
        }
    }

    private fun stopPlaybackAtQueueEnd() {
        userWantsPlaying = false
        _isPlaying.value = false
        if (_currentTrack.value?.uri == AudioRepository.SYNTH_URI) {
            synth.pause()
            synth.seekToMs(0)
        } else {
            exoPlayer?.pause()
            exoPlayer?.seekTo(0)
        }
        publishPosition(position = 0L, isPlaying = false)
        abandonPlaybackFocus()
        updateSessionState()
        notifySessionChanged()
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
=======
            _playbackPosition.value = 0L
            _playbackDuration.value = nextTrack.durationMs
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            _isPlaying.value = true
            userWantsPlaying = true
            pausedByTransientFocusLoss = false
            updateSessionMetadata(nextTrack)
            updateSessionState()
            ensureServiceRunning()
            onTrackStarted?.invoke(nextTrack)
            notifySessionChanged()

<<<<<<< HEAD
            // Swap players so progress/completion listeners target the active incoming player immediately.
            primary.removeListener(playerListener)
            incoming.addListener(playerListener)
            exoPlayer = incoming
            crossfadePlayer = primary
            publishPosition(position = 0L, duration = nextTrack.durationMs, isPlaying = true)
            startLocalProgressTracker()

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
=======
                // Swap players so progress/completion listeners target the active one.
                primary.removeListener(playerListener)
                incoming.addListener(playerListener)
                exoPlayer = incoming
                crossfadePlayer = primary
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                try {
                    primary.pause()
                    primary.stop()
                    primary.clearMediaItems()
                } catch (_: Exception) {
                }
                applyVolumeToEngines()
<<<<<<< HEAD
=======
                startProgressTracker(isSynth = false)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            RepeatMode.ALL -> {
                if (originalQueue.size <= 1) {
                    replayCurrentTrack()
                } else {
                    nextTrack(fromUser = false)
                }
            }
            RepeatMode.OFF -> nextTrack(fromUser = false)
=======
            RepeatMode.ALL, RepeatMode.OFF -> nextTrack(fromUser = false)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        }
    }

    private fun replayCurrentTrack() {
        val track = _currentTrack.value ?: return
<<<<<<< HEAD
        publishPosition(position = 0L, duration = track.durationMs)
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(0)
=======
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(0)
            _playbackPosition.value = 0L
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            if (!_isPlaying.value) {
                _isPlaying.value = true
                synth.start()
                applySynthControls()
            }
            startProgressTracker(isSynth = true)
<<<<<<< HEAD
        } else if (track.isYouTubeTrack()) {
            YouTubeBridge.seekTo(0f)
            YouTubeBridge.play()
            _isPlaying.value = true
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        } else {
            val player = exoPlayer
            if (player != null) {
                try {
                    player.seekTo(0)
<<<<<<< HEAD
                    player.playWhenReady = true
                    player.play()
                    _isPlaying.value = true
                    startProgressTracker(isSynth = false)
                } catch (_: Exception) {
                    playTrackInternal(track)
                }
            } else {
                playTrackInternal(track)
=======
                    player.play()
                    _playbackPosition.value = 0L
                    _isPlaying.value = true
                    startProgressTracker(isSynth = false)
                } catch (_: Exception) {
                    playTrack(track, _activeQueue.value)
                }
            } else {
                playTrack(track, _activeQueue.value)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            }
        }
        updateSessionState()
    }

    fun previousTrack() {
<<<<<<< HEAD
        val current = _currentTrack.value ?: return
        val queue = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
        if (queue.isEmpty()) return

        if (_playbackPosition.value > 3000L) {
            seekTo(0)
            return
        }

        if (_isShuffleEnabled.value) {
            if (shuffleHistory.isNotEmpty()) {
                val prevIdx = shuffleHistory.removeLast()
                if (prevIdx in shuffledQueue.indices) {
                    shuffleIndex = prevIdx
                    val prevTrack = shuffledQueue[shuffleIndex]
                    if (tryCrossfadeTo(prevTrack)) return
                    playTrackInternal(prevTrack)
                    return
                }
            }
            if (shuffleIndex > 0) {
                shuffleIndex--
                val prevTrack = shuffledQueue[shuffleIndex]
                if (tryCrossfadeTo(prevTrack)) return
                playTrackInternal(prevTrack)
            } else {
                if (_repeatMode.value == RepeatMode.ALL && shuffledQueue.isNotEmpty()) {
                    shuffleIndex = shuffledQueue.lastIndex
                    val prevTrack = shuffledQueue[shuffleIndex]
                    if (tryCrossfadeTo(prevTrack)) return
                    playTrackInternal(prevTrack)
                } else {
                    seekTo(0)
                }
            }
        } else {
            val curIdx = originalQueue.indexOfFirst { it.isSameTrack(current) }
            if (curIdx > 0) {
                val prevTrack = originalQueue[curIdx - 1]
                if (tryCrossfadeTo(prevTrack)) return
                playTrackInternal(prevTrack)
            } else {
                if (_repeatMode.value == RepeatMode.ALL && originalQueue.isNotEmpty()) {
                    val prevTrack = originalQueue.last()
                    if (tryCrossfadeTo(prevTrack)) return
                    playTrackInternal(prevTrack)
                } else {
                    seekTo(0)
                }
            }
        }
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    }

    fun seekTo(positionMs: Long) {
        val track = _currentTrack.value ?: return
        val clamped = positionMs.coerceIn(0L, max(_playbackDuration.value, 0L))
<<<<<<< HEAD
        Log.d(tag, "Local seekTo: target=$positionMs, clamped=$clamped, currentExoPos=${exoPlayer?.currentPosition}")
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(clamped)
            publishPosition(position = clamped, duration = _playbackDuration.value)
        } else if (track.isYouTubeTrack()) {
            YouTubeBridge.seekTo(clamped / 1000f)
            publishPosition(position = clamped, duration = _playbackDuration.value)
        } else {
            exoPlayer?.let { player ->
                player.seekTo(clamped)
                val realPos = player.currentPosition.coerceAtLeast(0L)
                val targetPos = if (realPos == 0L && clamped > 0L) clamped else realPos
                val validDur = if (player.duration > 0 && player.duration != C.TIME_UNSET) player.duration else _playbackDuration.value
                publishPosition(position = targetPos, duration = validDur, isPlaying = player.isPlaying)
                if (player.isPlaying) {
                    startLocalProgressTracker()
                }
            }
=======
        if (track.uri == AudioRepository.SYNTH_URI) {
            synth.seekToMs(clamped)
            _playbackPosition.value = clamped
        } else {
            exoPlayer?.seekTo(clamped)
            _playbackPosition.value = clamped
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        }
        updateSessionState()
        notifySessionChanged()
    }

    fun toggleShuffle() {
<<<<<<< HEAD
        setShuffleEnabled(!_isShuffleEnabled.value)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        if (_isShuffleEnabled.value == enabled) return
        _isShuffleEnabled.value = enabled
        shuffleHistory.clear()

        val current = _currentTrack.value
        if (enabled) {
            buildNewShuffledQueue(startingTrack = current)
            _activeQueue.value = shuffledQueue
        } else {
            _activeQueue.value = originalQueue
            shuffleIndex = -1
        }

=======
        _isShuffleEnabled.value = !_isShuffleEnabled.value
        if (!_isShuffleEnabled.value) shuffleHistory.clear()
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
    }

    fun toggleLoop() {
<<<<<<< HEAD
        val next = when (_repeatMode.value) {
=======
        _repeatMode.value = when (_repeatMode.value) {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
<<<<<<< HEAD
        setRepeatMode(next)
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        exoPlayer?.repeatMode = if (mode == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
=======
        exoPlayer?.repeatMode = if (_repeatMode.value == RepeatMode.ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        if (_crossfadeSec.value <= 0f) {
            prepareNextTrackForGapless()
        }
        updateSessionState()
        notifySessionChanged()
    }

<<<<<<< HEAD
    fun cyclePlaybackMode() {
        when {
            _repeatMode.value == RepeatMode.ONE -> {
                _repeatMode.value = RepeatMode.OFF
                exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                setShuffleEnabled(false)
            }
            !_isShuffleEnabled.value && _repeatMode.value == RepeatMode.OFF -> {
                _repeatMode.value = RepeatMode.OFF
                exoPlayer?.repeatMode = Player.REPEAT_MODE_OFF
                setShuffleEnabled(true)
            }
            _isShuffleEnabled.value && _repeatMode.value != RepeatMode.ALL -> {
                setShuffleEnabled(false)
                setRepeatMode(RepeatMode.ALL)
            }
            else -> {
                setShuffleEnabled(false)
                setRepeatMode(RepeatMode.ONE)
            }
        }
    }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        val targetList = if (_isShuffleEnabled.value) shuffledQueue else originalQueue
        if (fromIndex !in targetList.indices || toIndex !in targetList.indices || fromIndex == toIndex) return

        val mutable = targetList.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)

        if (_isShuffleEnabled.value) {
            shuffledQueue = mutable
            _activeQueue.value = shuffledQueue
            _currentTrack.value?.let { current ->
                shuffleIndex = shuffledQueue.indexOfFirst { it.id == current.id }
            }
        } else {
            originalQueue = mutable
            _activeQueue.value = originalQueue
        }

=======
        val queue = _activeQueue.value.toMutableList()
        if (fromIndex < 0 || fromIndex >= queue.size) return
        if (toIndex < 0 || toIndex >= queue.size) return
        if (fromIndex == toIndex) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        _activeQueue.value = queue
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD

        val hasRecordAudio = androidx.core.content.ContextCompat.checkSelfPermission(
            appContext,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasRecordAudio) {
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
                visualizer = null
            }
        } else {
            visualizer = null
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        YouTubeBridge.pause()
        releaseAudioEffects()
        stopVisualizer()
=======
        releaseAudioEffects()
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
    private fun startLocalProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            var lastPos = -1L
            while (_isPlaying.value && isActive) {
                val player = exoPlayer
                if (player != null && (player.isPlaying || player.playbackState == Player.STATE_READY)) {
                    val pos = player.currentPosition.coerceAtLeast(0L)
                    val dur = player.duration
                    val validDur = if (dur > 0 && dur != C.TIME_UNSET) dur else _playbackDuration.value

                    Log.d(
                        tag,
                        "LOCAL PLAYER isPlaying=${player.isPlaying} currentPosition=${player.currentPosition} duration=${player.duration} playbackState=${player.playbackState} mediaItem=${player.currentMediaItem?.mediaId}"
                    )

                    if (lastPos >= 0L && pos < lastPos && (lastPos - pos) > 1000L) {
                        Log.w(tag, "Local playback position jumped backward: $lastPos -> $pos (ExoPlayer currentPosition: ${player.currentPosition})")
                    }
                    lastPos = pos

                    publishPosition(position = pos, duration = validDur, isPlaying = player.isPlaying)

                    val track = _currentTrack.value
                    val fadeMs = (_crossfadeSec.value * 1000f).toLong().coerceAtLeast(250L)
                    if (
                        track != null &&
                        track.uri != AudioRepository.SYNTH_URI &&
                        _crossfadeSec.value > 0f &&
                        _repeatMode.value != RepeatMode.ONE &&
                        autoCrossfadeTriggerTrackId != track.id &&
                        player.isPlaying &&
                        validDur > 0
                    ) {
                        val remainingMs = validDur - pos
                        if (remainingMs in 1L..fadeMs) {
                            autoCrossfadeTriggerTrackId = track.id
                            nextTrack(fromUser = false)
                            continue
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                        }
                    }
                }
                // Position-only MediaSession update — avoid rebuilding the notification every tick
                updateSessionPlaybackState(notify = false)
<<<<<<< HEAD
                delay(250L)
=======
                delay(500)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            }
        }
    }

<<<<<<< HEAD
    private fun startSynthProgressTracker() {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (_isPlaying.value && isActive) {
                val pos = synth.positionMs()
                val dur = _playbackDuration.value
                val safePos = if (dur > 0) pos % dur else pos
                publishPosition(position = safePos, duration = dur, isPlaying = true)
                if (dur > 0 && pos >= dur) {
                    onTrackCompleted()
                    if (_repeatMode.value != RepeatMode.ONE) break
                }
                updateSessionPlaybackState(notify = false)
                delay(250L)
            }
        }
    }

    private fun startProgressTracker(isSynth: Boolean) {
        if (isSynth) {
            startSynthProgressTracker()
        } else {
            startLocalProgressTracker()
        }
    }

    private fun stopVisualizer() {
        visualizerJob?.cancel()
        visualizerJob = null
        visualizerTarget = List(24) { 0.08f }
        _waveformAmplitudes.value = List(24) { 0.08f }
    }

    private fun startVisualizerLoop() {
        if (!_isPlaying.value) {
            stopVisualizer()
            return
        }
        if (visualizerJob?.isActive == true) return
        visualizerJob = scope.launch(Dispatchers.Default) {
            while (isActive && _isPlaying.value) {
                val track = _currentTrack.value
                if (track?.uri == AudioRepository.SYNTH_URI) {
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                    val rootAmp = (synth.lastWaveformValue + 1.0f) / 2.0f
                    val t = System.currentTimeMillis() * 0.012
                    visualizerTarget = List(24) { index ->
                        val phase = (index / 24f) * Math.PI * 2.0
                        val modulation = kotlin.math.sin(phase + t).toFloat()
                        val harmonic = kotlin.math.sin(phase * 2.0 + t * 1.4).toFloat() * 0.15f
                        ((rootAmp * 0.7f) + (modulation * 0.22f) + harmonic + 0.08f).coerceIn(0.08f, 1f)
                    }
<<<<<<< HEAD
                } else if (track != null) {
                    if (visualizer == null) {
                        // Dynamic live visualizer for audio / YouTube music tracks
                        val t = System.currentTimeMillis() * 0.008
                        val seed = (track.id.hashCode() and 0xFFFF) * 0.001
                        visualizerTarget = List(24) { i ->
                            val phase = (i / 24f) * Math.PI * 2.0
                            val wave1 = sin(phase * 2f + t * 1.8 + seed).toFloat() * 0.35f
                            val wave2 = cos(phase * 3f - t * 2.5).toFloat() * 0.25f
                            val beatPulse = sin(t * 3.14159 * 2.2).toFloat().coerceAtLeast(0f) * 0.22f
                            val base = 0.25f + (sin(i * 0.8 + seed * 5f).toFloat() * 0.15f)
                            (base + wave1 + wave2 + beatPulse).coerceIn(0.12f, 0.95f)
                        }
                    }
=======
                } else if (!playing) {
                    visualizerTarget = visualizerTarget.map { (it * 0.82f).coerceAtLeast(0.08f) }
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
                delay(33L)
            }
            visualizerTarget = List(24) { 0.08f }
            _waveformAmplitudes.value = List(24) { 0.08f }
=======
                delay(if (playing) 33L else 120L)
            }
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            if (track.isSameTrack(_currentTrack.value)) {
=======
            if (_currentTrack.value?.id == track.id) {
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            val bitmap = if (uriString.startsWith("http://") || uriString.startsWith("https://")) {
                val conn = (URL(uriString).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)
                }
            } else {
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
            }
            if (bitmap != null) ArtworkTransformer.cropLetterboxBars(bitmap) else null
=======
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
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE or
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE
        mediaSession?.isActive = _currentTrack.value != null
        val speed = if (_isPlaying.value) _playbackSpeed.value else 0f
        mediaSession?.setShuffleMode(
            if (_isShuffleEnabled.value) PlaybackStateCompat.SHUFFLE_MODE_ALL
            else PlaybackStateCompat.SHUFFLE_MODE_NONE
        )
        mediaSession?.setRepeatMode(
            when (_repeatMode.value) {
                RepeatMode.OFF -> PlaybackStateCompat.REPEAT_MODE_NONE
                RepeatMode.ALL -> PlaybackStateCompat.REPEAT_MODE_ALL
                RepeatMode.ONE -> PlaybackStateCompat.REPEAT_MODE_ONE
            }
        )
=======
            PlaybackStateCompat.ACTION_STOP
        mediaSession?.isActive = _currentTrack.value != null
        val speed = if (_isPlaying.value) _playbackSpeed.value else 0f
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        if (hasAudioFocus) return true
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
=======
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(audioFocusChangeListener)
                .setAcceptsDelayedFocusGain(true)
                .setWillPauseWhenDucked(false)
<<<<<<< HEAD
                .build().also { audioFocusRequest = it }
=======
                .build()
            audioFocusRequest = req
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        if (!hasAudioFocus) return
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
