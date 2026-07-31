package com.example

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AudioDao {
    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    fun getAllTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE category = :category ORDER BY title ASC")
    fun getTracksByCategory(category: String): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE isFavorite = 1 ORDER BY title ASC")
    fun getFavoriteTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE dateAdded > 0 ORDER BY dateAdded DESC LIMIT 50")
    fun getRecentlyAddedTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE playCount > 0 ORDER BY playCount DESC, lastPlayed DESC LIMIT 50")
    fun getMostPlayedTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): AudioTrackEntity?

    @Query("SELECT * FROM audio_tracks WHERE uri IN (:uris)")
    suspend fun getTracksByUris(uris: List<String>): List<AudioTrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: AudioTrackEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTracks(tracks: List<AudioTrackEntity>)

    @Query(
        """
        UPDATE audio_tracks SET
            title = :title,
            artist = :artist,
            durationMs = :durationMs,
            album = :album,
            folderName = :folderName,
            albumArtUri = :albumArtUri,
            category = :category,
            dateAdded = CASE WHEN :dateAdded > 0 THEN :dateAdded ELSE dateAdded END,
            dateModified = CASE WHEN :dateModified > 0 THEN :dateModified ELSE dateModified END,
            year = CASE WHEN :year > 0 THEN :year ELSE year END
        WHERE uri = :uri
        """
    )
    suspend fun updateTrackMetadata(
        uri: String,
        title: String,
        artist: String,
        durationMs: Long,
        album: String,
        folderName: String,
        albumArtUri: String?,
        category: String,
        dateAdded: Long,
        dateModified: Long,
        year: Int
    )

    @Query(
        """
        UPDATE audio_tracks SET
            title = :title,
            artist = :artist,
            album = :album
        WHERE id = :id
        """
    )
    suspend fun updateUserTags(id: Int, title: String, artist: String, album: String)

    @Query("UPDATE audio_tracks SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Int, lyrics: String?)

    @Query("UPDATE audio_tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Int, isFavorite: Boolean)

    @Query("UPDATE audio_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Int, timestamp: Long)

    @Query("DELETE FROM audio_tracks WHERE uri = :uri")
    suspend fun deleteTrackByUri(uri: String)

    @Query("DELETE FROM audio_tracks WHERE category != 'My Device' AND uri NOT LIKE 'procedural://%'")
    suspend fun deleteNonDeviceTracks()

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRefEntity)

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrackCrossRef(playlistId: Int, trackId: Int)

    @Query(
        """
        SELECT audio_tracks.* FROM audio_tracks 
        INNER JOIN playlist_track_cross_ref ON audio_tracks.id = playlist_track_cross_ref.trackId 
        WHERE playlist_track_cross_ref.playlistId = :playlistId
        ORDER BY audio_tracks.title ASC
        """
    )
    fun getTracksInPlaylist(playlistId: Int): Flow<List<AudioTrackEntity>>

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsForPlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun deleteCrossRefsForTrack(trackId: Int)
}
