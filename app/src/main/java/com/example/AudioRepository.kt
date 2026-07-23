package com.example

import kotlinx.coroutines.flow.Flow

class AudioRepository(private val audioDao: AudioDao) {
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
                dateAdded = track.dateAdded
            )
            existing.id.toLong()
        } else {
            audioDao.insertTrack(track)
        }
    }

    suspend fun insertTrack(track: AudioTrackEntity): Long = upsertTrack(track)

    suspend fun insertTracks(tracks: List<AudioTrackEntity>) {
        tracks.forEach { upsertTrack(it) }
    }

    suspend fun ensureSynthTrack() {
        val uri = SYNTH_URI
        if (audioDao.getTrackByUri(uri) == null) {
            audioDao.insertTrack(
                AudioTrackEntity(
                    uri = uri,
                    title = "Neon Pulse",
                    artist = "GlassPlayer Synth",
                    durationMs = 180_000L,
                    dateAdded = System.currentTimeMillis(),
                    category = "My Device",
                    album = "Procedural",
                    folderName = "Synth",
                    lyrics = SYNTH_LYRICS
                )
            )
        }
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
