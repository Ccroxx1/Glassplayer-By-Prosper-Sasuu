package com.example

import android.net.Uri
import java.util.UUID

/**
 * Supported export format options for playlists.
 */
enum class ExportFormatOption {
    PLAYLIST_ONLY,      // Lightweight .glassplaylist (JSON metadata only)
    PLAYLIST_WITH_AUDIO // .zip package containing .glassplaylist manifest + audio files + artwork
}

/**
 * Conflict resolution strategy when an imported playlist shares a name with an existing playlist.
 */
enum class ImportConflictStrategy {
    MERGE,      // Keep existing tracks, append new unique tracks from backup preserving order
    REPLACE,    // Replace existing tracks in playlist with imported tracks
    CREATE_NEW  // Create a new playlist with name suffix e.g. "(Imported)" or "(1)"
}

/**
 * Match status of an imported track against the target device's library.
 */
enum class TrackMatchStatus {
    EXACT_URI,              // URI exists exactly on this device
    MATCHED_LOCAL_METADATA, // Found existing track via Artist+Title / Title / Hash / Filename
    ONLINE_STREAM,          // YouTube track ready to play and stream online
    INCLUDED_IN_ZIP,        // Packaged audio file will be extracted into device music library
    UNAVAILABLE_LOCAL       // Local audio file not found; preserved with metadata marked unavailable
}

/**
 * Representation of an individual track in a playlist backup.
 */
data class BackupPlaylistTrack(
    val position: Int = 0,
    val title: String,
    val artist: String,
    val album: String = "Glassmorphic Dreams",
    val durationMs: Long = 0L,
    val year: Int = 0,
    val category: String = "Library",
    val folderName: String = "Default",
    val uri: String = "",
    val localRelativePath: String? = null,
    val fileName: String? = null,
    val fileHash: String? = null,
    val fileSize: Long = 0L,
    val isOnline: Boolean = false,
    val youtubeVideoId: String? = null,
    val onlineUrl: String? = null,
    val thumbnailUrl: String? = null,
    val isAvailable: Boolean = true,
    val rating: Int = 0,
    val mood: String = "",
    val lyrics: String? = null,
    val lrcLyrics: String? = null,
    val bpm: Float = 0f,
    val replayGainDb: Float = 0f,
    val isFavorite: Boolean = false
)

/**
 * Representation of a playlist in a backup package.
 */
data class BackupPlaylist(
    val id: Int = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val type: String = "Mixed", // "Local", "Online", or "Mixed"
    val createdAt: Long = System.currentTimeMillis(),
    val artworkPath: String? = null,
    val tracks: List<BackupPlaylistTrack> = emptyList()
)

/**
 * Per-track global metadata (ratings, lyrics, favorites) in a backup package.
 */
data class BackupTrackMeta(
    val uri: String = "",
    val title: String = "",
    val artist: String = "",
    val youtubeVideoId: String? = null,
    val favorite: Boolean = false,
    val rating: Int = 0,
    val mood: String = "",
    val lyrics: String? = null,
    val lrcLyrics: String? = null,
    val bpm: Float = 0f,
    val replayGainDb: Float = 0f
)

/**
 * Root container for a .glassplaylist file or manifest inside a .zip backup.
 */
data class PlaylistBackupPackage(
    val app: String = "GlassPlayer",
    val version: Int = 3,
    val exportedAt: Long = System.currentTimeMillis(),
    val type: String = "backup", // "single_playlist", "selected_playlists", "all_playlists"
    val playlists: List<BackupPlaylist> = emptyList(),
    val trackMeta: List<BackupTrackMeta> = emptyList()
)

/**
 * Preview of a track match during import.
 */
data class TrackMatchPreview(
    val track: BackupPlaylistTrack,
    val matchStatus: TrackMatchStatus,
    val matchedTrackId: Int? = null,
    val matchedTrackUri: String? = null
)

/**
 * Preview of a single playlist being imported.
 */
data class PlaylistItemPreview(
    val playlist: BackupPlaylist,
    var selected: Boolean = true,
    val existingPlaylistId: Int? = null,
    val existingPlaylistName: String? = null,
    var conflictStrategy: ImportConflictStrategy = ImportConflictStrategy.CREATE_NEW,
    val trackPreviews: List<TrackMatchPreview> = emptyList()
) {
    val localCount: Int get() = trackPreviews.count { !it.track.isOnline }
    val onlineCount: Int get() = trackPreviews.count { it.track.isOnline }
    val matchedCount: Int get() = trackPreviews.count { 
        it.matchStatus == TrackMatchStatus.EXACT_URI || 
        it.matchStatus == TrackMatchStatus.MATCHED_LOCAL_METADATA 
    }
    val zipCount: Int get() = trackPreviews.count { it.matchStatus == TrackMatchStatus.INCLUDED_IN_ZIP }
    val unavailableCount: Int get() = trackPreviews.count { it.matchStatus == TrackMatchStatus.UNAVAILABLE_LOCAL }
    val hasConflict: Boolean get() = existingPlaylistId != null
}

/**
 * Complete preview of an import file before confirming the restore.
 */
data class PlaylistImportPreview(
    val backupPackage: PlaylistBackupPackage,
    val playlistPreviews: List<PlaylistItemPreview>,
    val isZipPackage: Boolean = false,
    val sourceUriString: String? = null,
    val fileName: String = "playlist_backup.glassplaylist",
    val validationErrors: List<String> = emptyList()
) {
    val totalTracks: Int get() = playlistPreviews.sumOf { it.playlist.tracks.size }
    val selectedPlaylistsCount: Int get() = playlistPreviews.count { it.selected }
}

/**
 * Result returned after performing an import.
 */
data class PlaylistImportResult(
    val success: Boolean,
    val playlistsImported: Int = 0,
    val playlistsMerged: Int = 0,
    val playlistsReplaced: Int = 0,
    val tracksRestored: Int = 0,
    val tracksExtractedFromZip: Int = 0,
    val onlineTracksRestored: Int = 0,
    val unavailableTracksMarked: Int = 0,
    val errors: List<String> = emptyList()
)

/**
 * Active progress state during export or import.
 */
data class BackupProgress(
    val current: Int,
    val total: Int,
    val currentItemName: String = "",
    val message: String = ""
) {
    val progressFraction: Float get() = if (total > 0) current.toFloat() / total.toFloat() else 0f
}
