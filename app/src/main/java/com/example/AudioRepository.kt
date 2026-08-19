package com.example

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.Calendar

class AudioRepository(
    private val audioDao: AudioDao,
    private val database: AudioDatabase
) {
    data class ListeningStats(
        val totalMs: Long,
        val topArtists: List<Pair<String, Int>>,
        val weeklyMs: Long
    )

    data class DuplicateGroup(
        val title: String,
        val artist: String,
        val tracks: List<AudioTrackEntity>
    )

    data class M3uImportSummary(
        val playlistName: String,
        val addedCount: Int,
        val unmatchedCount: Int
    )

    val allTracks: Flow<List<AudioTrackEntity>> = audioDao.getAllTracks()
    val favorites: Flow<List<AudioTrackEntity>> = audioDao.getFavoriteTracks()
    val recentTracks: Flow<List<AudioTrackEntity>> = audioDao.getRecentTracks()
    val recentlyAddedTracks: Flow<List<AudioTrackEntity>> = audioDao.getRecentlyAddedTracks()
    val mostPlayedTracks: Flow<List<AudioTrackEntity>> = audioDao.getMostPlayedTracks()
    val allPlaylists: Flow<List<PlaylistEntity>> = audioDao.getAllPlaylists()

    // Extended smart playlists
    val neverPlayedTracks: Flow<List<AudioTrackEntity>> = audioDao.getNeverPlayedTracks()
    val longTracks: Flow<List<AudioTrackEntity>> = audioDao.getLongTracks(600_000L)
    val thisYearTracks: Flow<List<AudioTrackEntity>> =
        audioDao.getTracksReleasedThisYear(Calendar.getInstance().get(Calendar.YEAR))

    fun getTracksByCategory(category: String): Flow<List<AudioTrackEntity>> {
        return audioDao.getTracksByCategory(category)
    }

    fun getTracksInPlaylist(playlistId: Int): Flow<List<AudioTrackEntity>> {
        return audioDao.getTracksInPlaylist(playlistId)
    }

    suspend fun getTrackByUri(uri: String): AudioTrackEntity? = audioDao.getTrackByUri(uri)

    /** Returns tracks for [uris] in the same order as requested (missing URIs omitted). */
    suspend fun getTracksByUrisOrdered(uris: List<String>): List<AudioTrackEntity> {
        if (uris.isEmpty()) return emptyList()
        val found = audioDao.getTracksByUris(uris).associateBy { it.uri }
        return uris.mapNotNull { found[it] }
    }

    suspend fun upsertTrack(track: AudioTrackEntity): Long {
        val existing = audioDao.getTrackByUri(track.uri)
        return if (existing != null) {
            audioDao.updateTrackMetadata(
                uri = track.uri,
                title = track.title,
                artist = track.artist,
                durationMs = track.durationMs,
                album = track.album,
                folderName = track.folderName,
                albumArtUri = track.albumArtUri,
                category = track.category,
                dateAdded = track.dateAdded,
                dateModified = track.dateModified,
                year = track.year
            )
            existing.id.toLong()
        } else {
            audioDao.insertTrack(track)
        }
    }

    suspend fun insertTrack(track: AudioTrackEntity): Long = upsertTrack(track)

    /** Single Room transaction so the UI gets one Flow emit instead of one per track. */
    suspend fun insertTracks(tracks: List<AudioTrackEntity>) {
        if (tracks.isEmpty()) return
        database.withTransaction {
            tracks.forEach { upsertTrack(it) }
        }
    }

    /** Removes the built-in Neon Pulse procedural track if present. */
    suspend fun removeSynthTrack() {
        audioDao.deleteTrackByUri(SYNTH_URI)
    }

    suspend fun toggleFavorite(id: Int, isFavorite: Boolean) {
        audioDao.toggleFavorite(id, isFavorite)
    }

    suspend fun incrementPlayCount(id: Int, timestamp: Long) {
        audioDao.incrementPlayCount(id, timestamp)
    }

    suspend fun updateLyrics(id: Int, lyrics: String?) {
        audioDao.updateLyrics(id, lyrics)
    }

    suspend fun updateLrcLyrics(id: Int, lrc: String?) {
        audioDao.updateLrcLyrics(id, lrc)
    }

    suspend fun updateRating(id: Int, rating: Int) {
        audioDao.updateRating(id, rating.coerceIn(0, 5))
    }

    suspend fun updateBpm(id: Int, bpm: Float) {
        audioDao.updateBpm(id, bpm.coerceAtLeast(0f))
    }

    suspend fun updateReplayGain(id: Int, db: Float) {
        audioDao.updateReplayGain(id, db.coerceIn(-24f, 24f))
    }

    suspend fun updateMood(id: Int, mood: String) {
        audioDao.updateMood(id, mood)
    }

    fun getTracksByMood(mood: String): Flow<List<AudioTrackEntity>> =
        audioDao.getTracksByMood(mood)

    /** Returns total estimated listening time across the library in milliseconds. */
    suspend fun getTotalListeningMs(): Long = audioDao.getTotalListeningMs()

    /** Returns top-N artists as pairs of (artistName, totalPlayCount). */
    suspend fun getTopArtistsByPlayCount(limit: Int = 5): List<Pair<String, Int>> =
        audioDao.getTopArtistsByPlayCount(limit).mapNotNull { combo ->
            val parts = combo.split("|")
            if (parts.size == 2) Pair(parts[0], parts[1].toIntOrNull() ?: 0) else null
        }

    /** Returns tracks that share the same title+artist — potential duplicates. */
    suspend fun getDuplicateTracks(): List<AudioTrackEntity> = audioDao.getDuplicateTracks()

    suspend fun getDuplicateTrackGroups(): List<DuplicateGroup> {
        return audioDao.getTracksWithDuplicateTitles()
            .groupBy { it.title to it.artist }
            .map { (key, tracks) ->
                DuplicateGroup(
                    title = key.first,
                    artist = key.second,
                    tracks = tracks.sortedBy { it.album }
                )
            }
            .sortedWith(compareBy({ it.title }, { it.artist }))
    }

    suspend fun getListeningStats(): ListeningStats {
        val total = audioDao.getTotalListeningMs().coerceAtLeast(0L)
        val top = getTopArtistsByPlayCount(limit = 5)
        val weekStart = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
        val weekly = audioDao.getAllTracksSnapshot()
            .asSequence()
            .filter { it.uri != SYNTH_URI && it.lastPlayed >= weekStart }
            .sumOf { (it.playCount.toLong().coerceAtLeast(0L) * it.durationMs.coerceAtLeast(0L)) }
        return ListeningStats(totalMs = total, topArtists = top, weeklyMs = weekly)
    }

    suspend fun updateUserTags(id: Int, title: String, artist: String, album: String) {
        audioDao.updateUserTags(id, title, artist, album)
    }

    /**
     * Deletes a track and any playlist references that point to it.
     * This keeps playlists consistent when a song is removed from the device
     * or removed through the app.
     */
    suspend fun getTracksByFolder(folderName: String): List<AudioTrackEntity> = audioDao.getTracksByFolder(folderName)

    suspend fun deleteTracksByFolder(folderName: String) {
        database.withTransaction {
            audioDao.getTracksByFolder(folderName).forEach { audioDao.deleteCrossRefsForTrack(it.id) }
            audioDao.deleteTracksByFolder(folderName)
        }
    }

    suspend fun deleteTrackByUri(uri: String) {
        database.withTransaction {
            val track = audioDao.getTrackByUri(uri)
            if (track != null) {
                audioDao.deleteCrossRefsForTrack(track.id)
            }
            audioDao.deleteTrackByUri(uri)
        }
    }

    /**
     * Removes stale MediaStore-backed device tracks after a successful scan.
     * Only MediaStore audio URIs are synchronized; imported document URIs are
     * left alone so user-imported tracks are not accidentally removed.
     * Returns the URIs that were deleted so the playback engine can prune them.
     */
    suspend fun removeStaleDeviceTracks(currentMediaStoreUris: Set<String>): Set<String> {
        return database.withTransaction {
            val stale = audioDao.getAllTracksSnapshot()
                .asSequence()
                .filter { it.category == "My Device" }
                .filter { it.uri.startsWith("content://media/external/audio/media/") }
                .filter { it.uri !in currentMediaStoreUris }
                .toList()

            stale.forEach { track ->
                audioDao.deleteCrossRefsForTrack(track.id)
                audioDao.deleteTrackByUri(track.uri)
            }
            stale.map { it.uri }.toSet()
        }
    }

    suspend fun deleteNonDeviceTracks() {
        audioDao.deleteNonDeviceTracks()
    }

    suspend fun createPlaylist(name: String): Long {
        return audioDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) {
        audioDao.deleteCrossRefsForPlaylist(playlistId)
        audioDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        audioDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRefEntity(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Int, trackId: Int) {
        audioDao.deletePlaylistTrackCrossRef(playlistId, trackId)
    }

    suspend fun importM3u(m3uText: String): M3uImportSummary {
        val library = audioDao.getAllTracksSnapshot()
        val parsed = BackupRestoreManager.importM3u(m3uText, library, suggestedName = "Imported M3U")
        val playlistId = createPlaylist(parsed.playlistName).toInt()
        parsed.matchedTracks.forEach { addTrackToPlaylist(playlistId, it.id) }
        return M3uImportSummary(
            playlistName = parsed.playlistName,
            addedCount = parsed.matchedTracks.size,
            unmatchedCount = parsed.unmatchedUris.size
        )
    }

    suspend fun exportBackup(): String {
        val playlists = allPlaylistsSnapshot()
        val tracks = audioDao.getAllTracksSnapshot()
        return BackupRestoreManager.exportBackup(
            playlists = playlists,
            getTracksInPlaylist = { playlistId -> audioDao.getTracksInPlaylistSnapshot(playlistId) },
            allTracks = tracks
        )
    }

    suspend fun importBackup(json: String): BackupRestoreManager.ImportResult {
        val tracks = audioDao.getAllTracksSnapshot()
        return BackupRestoreManager.importBackup(
            json = json,
            allTracks = tracks,
            createPlaylist = { name -> createPlaylist(name).toInt() },
            addTrackToPlaylist = { playlistId, trackId -> addTrackToPlaylist(playlistId, trackId) },
            updateFavorite = { id, favorite -> toggleFavorite(id, favorite) },
            updateRating = { id, rating -> updateRating(id, rating) },
            updateMood = { id, mood -> updateMood(id, mood) },
            updateLyrics = { id, lyrics -> updateLyrics(id, lyrics) },
            updateLrcLyrics = { id, lrc -> updateLrcLyrics(id, lrc) },
            updateBpm = { id, bpm -> updateBpm(id, bpm) },
            updateReplayGain = { id, db -> updateReplayGain(id, db) }
        )
    }

    private suspend fun allPlaylistsSnapshot(): List<PlaylistEntity> {
        return allPlaylists.first()
    }

    /**
     * Exports a playlist as M3U8 plain-text format.
     * Each line after the header contains the track file path or URI.
     */
    fun exportPlaylistAsM3u(name: String, tracks: List<AudioTrackEntity>): String {
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("# GlassPlayer Playlist: $name")
        tracks.forEach { track ->
            sb.appendLine("#EXTINF:${track.durationMs / 1000},${track.artist} - ${track.title}")
            sb.appendLine(track.uri)
        }
        return sb.toString()
    }

    companion object {
        const val SYNTH_URI = "procedural://synth"
        val SYNTH_LYRICS = """
            [Neon Pulse]
            Filter sweeps across the glass
            Tempo drifts through cyan night
            Am to F, then C to G
            Procedural light, forever free
        """.trimIndent()
    }
}
