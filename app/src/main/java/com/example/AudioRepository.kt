package com.example

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow

class AudioRepository(
    private val audioDao: AudioDao,
    private val database: AudioDatabase
) {
    val allTracks: Flow<List<AudioTrackEntity>> = audioDao.getAllTracks()
    val favorites: Flow<List<AudioTrackEntity>> = audioDao.getFavoriteTracks()
    val recentTracks: Flow<List<AudioTrackEntity>> = audioDao.getRecentTracks()
    val recentlyAddedTracks: Flow<List<AudioTrackEntity>> = audioDao.getRecentlyAddedTracks()
    val mostPlayedTracks: Flow<List<AudioTrackEntity>> = audioDao.getMostPlayedTracks()
    val allPlaylists: Flow<List<PlaylistEntity>> = audioDao.getAllPlaylists()

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

    suspend fun updateUserTags(id: Int, title: String, artist: String, album: String) {
        audioDao.updateUserTags(id, title, artist, album)
    }

    suspend fun deleteTrackByUri(uri: String) {
        audioDao.deleteTrackByUri(uri)
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
