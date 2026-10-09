package com.example

<<<<<<< HEAD
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
=======
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
    private val playlistMutex = Mutex()

    val allTracks: Flow<List<AudioTrackEntity>> = audioDao.getAllTracks()
    val favorites: Flow<List<AudioTrackEntity>> = audioDao.getFavoriteTracks()
    val recentTracks: Flow<List<AudioTrackEntity>> =
        audioDao.getRecentTracks7Days(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L)
    val recentYouTubeTracks: Flow<List<AudioTrackEntity>> =
        audioDao.getRecentYouTubeTracks7Days(System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L)
=======
    val allTracks: Flow<List<AudioTrackEntity>> = audioDao.getAllTracks()
    val favorites: Flow<List<AudioTrackEntity>> = audioDao.getFavoriteTracks()
    val recentTracks: Flow<List<AudioTrackEntity>> = audioDao.getRecentTracks()
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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

<<<<<<< HEAD
    private suspend fun findExistingTrack(track: AudioTrackEntity): AudioTrackEntity? {
        val ytId = track.youtubeVideoId()
        if (ytId != null) {
            audioDao.getTrackByUri(youtubeTrackUri(ytId))?.let { return it }
            audioDao.getTrackByCategory("$YOUTUBE_CATEGORY_PREFIX$ytId")?.let { return it }
        }
        if (track.uri.isNotBlank()) {
            audioDao.getTrackByUri(track.uri)?.let { return it }
        }
        if (track.id > 0) {
            audioDao.getTrackById(track.id)?.let { return it }
        }
        return null
    }

    /**
     * Find-or-create a library row. YouTube identity is always `youtube://VIDEO_ID`.
     * Never persists a blank URI. Never trusts a caller-supplied (including negative) id.
     */
    suspend fun upsertTrack(track: AudioTrackEntity): Long {
        val normalized = track.normalizedForPersistence()
        if (normalized == null) {
            Log.w(TAG, "Refusing to persist track without a stable URI: title=${track.title}")
            return -1L
        }
        val existing = findExistingTrack(normalized) ?: findExistingTrack(track)
        return if (existing != null) {
            audioDao.updateTrackIdentityAndMetadata(
                id = existing.id,
                uri = when {
                    normalized.uri.startsWith(YOUTUBE_URI_PREFIX) -> normalized.uri
                    existing.uri.isNotBlank() -> existing.uri
                    else -> normalized.uri
                },
                title = normalized.title.ifBlank { existing.title },
                artist = normalized.artist.ifBlank { existing.artist },
                durationMs = if (normalized.durationMs > 0) normalized.durationMs else existing.durationMs,
                album = normalized.album.ifBlank { existing.album },
                folderName = normalized.folderName.ifBlank { existing.folderName },
                albumArtUri = normalized.albumArtUri ?: existing.albumArtUri,
                category = normalized.category.ifBlank { existing.category },
                dateAdded = existing.dateAdded.takeIf { it > 0L } ?: normalized.dateAdded,
                dateModified = normalized.dateModified,
                year = normalized.year
            )
            existing.id.toLong()
        } else {
            val inserted = audioDao.insertTrack(normalized.copy(id = 0))
            if (inserted > 0L) {
                inserted
            } else {
                findExistingTrack(normalized)?.id?.toLong() ?: -1L
            }
        }
    }

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    /** Returns tracks for [uris] in the same order as requested (missing URIs omitted). */
    suspend fun getTracksByUrisOrdered(uris: List<String>): List<AudioTrackEntity> {
        if (uris.isEmpty()) return emptyList()
        val found = audioDao.getTracksByUris(uris).associateBy { it.uri }
        return uris.mapNotNull { found[it] }
    }

<<<<<<< HEAD
=======
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

>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        return playlistMutex.withLock {
            audioDao.insertPlaylist(PlaylistEntity(name = name))
        }
    }

    suspend fun deletePlaylist(playlistId: Int) {
        playlistMutex.withLock {
            database.withTransaction {
                audioDao.deleteCrossRefsForPlaylist(playlistId)
                audioDao.deletePlaylist(playlistId)
            }
        }
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        if (playlistId <= 0 || trackId <= 0) return
        playlistMutex.withLock {
            addTrackIdToPlaylistInternal(playlistId, trackId)
        }
    }

    /**
     * Find-or-create [track], then attach it to [playlistId] using the real Room id.
     * Duplicate membership in the same playlist is ignored.
     */
    suspend fun addTrackEntityToPlaylist(playlistId: Int, track: AudioTrackEntity): PlaylistAddResult {
        if (playlistId <= 0) return PlaylistAddResult.FAILED
        return playlistMutex.withLock {
            try {
                database.withTransaction {
                    addTrackEntityToPlaylistInternal(playlistId, track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add track to playlist $playlistId", e)
                PlaylistAddResult.FAILED
            }
        }
    }

    suspend fun createPlaylistWithTrack(name: String, track: AudioTrackEntity): Pair<Int, PlaylistAddResult> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return 0 to PlaylistAddResult.FAILED
        return playlistMutex.withLock {
            try {
                database.withTransaction {
                    val playlistId = audioDao.insertPlaylist(PlaylistEntity(name = trimmed)).toInt()
                    if (playlistId <= 0) {
                        0 to PlaylistAddResult.FAILED
                    } else {
                        playlistId to addTrackEntityToPlaylistInternal(playlistId, track)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist with track", e)
                0 to PlaylistAddResult.FAILED
            }
        }
    }

    suspend fun createPlaylistWithTracks(name: String, tracks: Collection<AudioTrackEntity>): Pair<Int, Int> {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return 0 to 0
        return playlistMutex.withLock {
            try {
                database.withTransaction {
                    val playlistId = audioDao.insertPlaylist(PlaylistEntity(name = trimmed)).toInt()
                    if (playlistId <= 0) return@withTransaction 0 to 0
                    var added = 0
                    tracks.forEach { track ->
                        if (addTrackEntityToPlaylistInternal(playlistId, track) == PlaylistAddResult.ADDED) {
                            added++
                        }
                    }
                    playlistId to added
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create playlist with tracks", e)
                0 to 0
            }
        }
    }

    suspend fun importYouTubePlaylist(
        onlinePlaylist: OnlinePlaylistResult,
        customName: String? = null
    ): Pair<Int, Int> {
        val playlistName = (customName ?: onlinePlaylist.title).trim().ifBlank { "YouTube Playlist" }
        return playlistMutex.withLock {
            try {
                database.withTransaction {
                    val existingPlaylists = audioDao.getAllPlaylistsSnapshot()
                    val targetPlaylist = existingPlaylists.firstOrNull {
                        it.name.equals(playlistName, ignoreCase = true)
                    }
                    val playlistId = targetPlaylist?.id ?: audioDao.insertPlaylist(
                        PlaylistEntity(name = playlistName)
                    ).toInt()

                    if (playlistId <= 0) return@withTransaction 0 to 0

                    var importedCount = 0
                    onlinePlaylist.tracks.forEachIndexed { index, onlineTrack ->
                        val trackEntity = onlineTrack.toAudioTrackEntity(index)
                        val realTrackId = upsertTrack(trackEntity).toInt()
                        if (realTrackId > 0) {
                            if (!audioDao.isTrackInPlaylist(playlistId, realTrackId)) {
                                val nextPos = audioDao.getMaxPlaylistPosition(playlistId) + 1
                                audioDao.insertPlaylistTrackCrossRef(
                                    PlaylistTrackCrossRefEntity(
                                        playlistId = playlistId,
                                        trackId = realTrackId,
                                        position = nextPos
                                    )
                                )
                                importedCount++
                            }
                        }
                    }
                    playlistId to importedCount
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to import YouTube playlist ${onlinePlaylist.playlistId}", e)
                0 to 0
            }
        }
    }

    private suspend fun addTrackEntityToPlaylistInternal(
        playlistId: Int,
        track: AudioTrackEntity
    ): PlaylistAddResult {
        val playlist = audioDao.getPlaylistById(playlistId)
        if (playlist == null) {
            Log.w(TAG, "Playlist $playlistId does not exist")
            return PlaylistAddResult.FAILED
        }
        val realTrackId = upsertTrack(track).toInt()
        if (realTrackId <= 0) {
            Log.w(TAG, "Could not persist track for playlist: ${track.title}")
            return PlaylistAddResult.FAILED
        }
        val confirmed = audioDao.getTrackById(realTrackId)
        if (confirmed == null) {
            Log.w(TAG, "Persisted track id $realTrackId was not found")
            return PlaylistAddResult.FAILED
        }
        return addTrackIdToPlaylistInternal(playlistId, realTrackId)
    }

    private suspend fun addTrackIdToPlaylistInternal(playlistId: Int, trackId: Int): PlaylistAddResult {
        if (audioDao.isTrackInPlaylist(playlistId, trackId)) {
            return PlaylistAddResult.ALREADY_IN_PLAYLIST
        }
        val nextPosition = audioDao.getMaxPlaylistPosition(playlistId) + 1
        val rowId = audioDao.insertPlaylistTrackCrossRef(
            PlaylistTrackCrossRefEntity(playlistId = playlistId, trackId = trackId, position = nextPosition)
        )
        return when {
            rowId > 0L -> PlaylistAddResult.ADDED
            audioDao.isTrackInPlaylist(playlistId, trackId) -> PlaylistAddResult.ALREADY_IN_PLAYLIST
            else -> {
                Log.w(TAG, "Cross-ref insert failed for playlist=$playlistId track=$trackId")
                PlaylistAddResult.FAILED
            }
        }
    }

    suspend fun clearRecentYouTubeHistory() {
        audioDao.clearRecentYouTubeHistory()
    }

    suspend fun cleanInvalidData() {
        try {
            database.withTransaction {
                val sevenDaysAgoMs = System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1000L
                audioDao.expireOldRecentHistory(sevenDaysAgoMs)
                repairOnlineTrackIdentities()
                audioDao.deleteInvalidEmptyUriTracks()
                audioDao.deleteOrphanCrossRefs()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Data cleanup failed", e)
        }
    }

    /**
     * Repair historical YouTube rows that used a blank or stream URI, merging duplicates
     * that share the same video id so playlist membership is preserved.
     */
    private suspend fun repairOnlineTrackIdentities() {
        val all = audioDao.getAllTracksSnapshot()
        val groups = all.groupBy { track ->
            track.youtubeVideoId()
        }
        groups.forEach { (identity, tracks) ->
            if (identity.isNullOrBlank()) {
                tracks.filter { it.uri.isBlank() && it.folderName == FOLDER_YOUTUBE }
                    .forEach { broken ->
                        audioDao.deleteCrossRefsForTrack(broken.id)
                        audioDao.deleteTrackById(broken.id)
                    }
                return@forEach
            }
            val keeper = tracks.maxWithOrNull(
                compareBy<AudioTrackEntity> { if (it.uri.startsWith(YOUTUBE_URI_PREFIX)) 1 else 0 }
                    .thenBy { if (!it.albumArtUri.isNullOrBlank()) 1 else 0 }
                    .thenBy { it.id }
            ) ?: return@forEach
            val normalized = keeper.normalizedForPersistence() ?: return@forEach
            audioDao.updateTrackIdentityAndMetadata(
                id = keeper.id,
                uri = normalized.uri,
                title = keeper.title,
                artist = keeper.artist,
                durationMs = keeper.durationMs,
                album = normalized.album,
                folderName = normalized.folderName,
                albumArtUri = normalized.albumArtUri ?: keeper.albumArtUri,
                category = normalized.category,
                dateAdded = keeper.dateAdded,
                dateModified = keeper.dateModified,
                year = keeper.year
            )
            tracks.filter { it.id != keeper.id }.forEach { duplicate ->
                audioDao.getCrossRefsForTrack(duplicate.id).forEach { ref ->
                    if (!audioDao.isTrackInPlaylist(ref.playlistId, keeper.id)) {
                        audioDao.insertPlaylistTrackCrossRef(
                            PlaylistTrackCrossRefEntity(ref.playlistId, keeper.id, ref.position)
                        )
                    }
                    audioDao.deletePlaylistTrackCrossRef(ref.playlistId, duplicate.id)
                }
                audioDao.deleteTrackById(duplicate.id)
            }
        }
=======
        return audioDao.insertPlaylist(PlaylistEntity(name = name))
    }

    suspend fun deletePlaylist(playlistId: Int) {
        audioDao.deleteCrossRefsForPlaylist(playlistId)
        audioDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Int, trackId: Int) {
        audioDao.insertPlaylistTrackCrossRef(PlaylistTrackCrossRefEntity(playlistId, trackId))
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
<<<<<<< HEAD
        private const val TAG = "AudioRepository"
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
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
