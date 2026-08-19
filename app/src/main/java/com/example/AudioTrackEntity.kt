package com.example

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "audio_tracks",
    indices = [Index(value = ["uri"], unique = true)]
)
data class AudioTrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0,
    val dateAdded: Long = 0,
    val dateModified: Long = 0,
    val year: Int = 0,
    val category: String = "Library",
    val album: String = "Glassmorphic Dreams",
    val folderName: String = "Default",
    val albumArtUri: String? = null,
    val lyrics: String? = null,
    /** Star rating 0 (unrated) to 5. */
    val rating: Int = 0,
    /** Time-stamped LRC lyrics content, e.g. "[00:12.34] Line text" */
    val lrcLyrics: String? = null,
    /** Detected BPM (0 = not yet computed). */
    val bpm: Float = 0f,
    /** Replay Gain offset in dB; applied as a volume multiplier when loading the track. */
    val replayGainDb: Float = 0f,
    /** User-assigned mood tag: Chill, Hype, Focus, Sad, Party, Workout, or empty. */
    val mood: String = ""
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_track_cross_ref", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRefEntity(
    val playlistId: Int,
    val trackId: Int
)
