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
    val lyrics: String? = null
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
