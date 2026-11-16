package com.example

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackSessionDataStore by preferencesDataStore(name = "glassplayer_playback_session")

/**
 * Snapshot of the last playback session for restore across process death and reboots.
 * Track identity and queue order use media URIs (stable across Room upserts).
 * Album artwork is restored via [AudioTrackEntity.albumArtUri] once tracks are resolved.
 */
data class PlaybackSession(
    val trackUri: String,
    val queueUris: List<String>,
    val positionMs: Long,
    val wasPlaying: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: RepeatMode
)

/**
 * Persists the active playback session (song, queue, position, play/pause, shuffle/repeat).
 */
class PlaybackSessionStore(private val context: Context) {

    private val trackUriKey = stringPreferencesKey("track_uri")
    private val queueUrisKey = stringPreferencesKey("queue_uris")
    private val positionMsKey = longPreferencesKey("position_ms")
    private val wasPlayingKey = booleanPreferencesKey("was_playing")
    private val shuffleKey = booleanPreferencesKey("shuffle_enabled")
    private val repeatModeKey = stringPreferencesKey("repeat_mode")

    val sessionFlow: Flow<PlaybackSession?> = context.playbackSessionDataStore.data.map { prefs ->
        val trackUri = prefs[trackUriKey] ?: return@map null
        if (trackUri.isBlank()) return@map null
        PlaybackSession(
            trackUri = trackUri,
            queueUris = decodeQueue(prefs[queueUrisKey]),
            positionMs = (prefs[positionMsKey] ?: 0L).coerceAtLeast(0L),
            wasPlaying = prefs[wasPlayingKey] ?: false,
            shuffleEnabled = prefs[shuffleKey] ?: false,
            repeatMode = runCatching {
                RepeatMode.valueOf(prefs[repeatModeKey] ?: RepeatMode.OFF.name)
            }.getOrDefault(RepeatMode.OFF)
        )
    }

    suspend fun loadSession(): PlaybackSession? = sessionFlow.first()

    suspend fun saveSession(session: PlaybackSession) {
        context.playbackSessionDataStore.edit { prefs ->
            prefs[trackUriKey] = session.trackUri
            prefs[queueUrisKey] = encodeQueue(session.queueUris)
            prefs[positionMsKey] = session.positionMs.coerceAtLeast(0L)
            prefs[wasPlayingKey] = session.wasPlaying
            prefs[shuffleKey] = session.shuffleEnabled
            prefs[repeatModeKey] = session.repeatMode.name
        }
    }

    suspend fun clearSession() {
        context.playbackSessionDataStore.edit { prefs ->
            prefs.remove(trackUriKey)
            prefs.remove(queueUrisKey)
            prefs.remove(positionMsKey)
            prefs.remove(wasPlayingKey)
            prefs.remove(shuffleKey)
            prefs.remove(repeatModeKey)
        }
    }

    companion object {
        private const val QUEUE_SEPARATOR = "\u001F"

        fun encodeQueue(uris: List<String>): String =
            uris.filter { it.isNotBlank() }.joinToString(QUEUE_SEPARATOR)

        fun decodeQueue(encoded: String?): List<String> {
            if (encoded.isNullOrBlank()) return emptyList()
            return encoded.split(QUEUE_SEPARATOR).filter { it.isNotBlank() }
        }
    }
}
