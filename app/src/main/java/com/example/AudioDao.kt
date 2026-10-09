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

<<<<<<< HEAD
    @Query("SELECT * FROM audio_tracks WHERE lastPlayed >= :sevenDaysAgoMs ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentTracks7Days(sevenDaysAgoMs: Long): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE folderName = 'YouTube' AND lastPlayed >= :sevenDaysAgoMs ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentYouTubeTracks7Days(sevenDaysAgoMs: Long): Flow<List<AudioTrackEntity>>

    @Query("UPDATE audio_tracks SET lastPlayed = 0 WHERE lastPlayed > 0 AND lastPlayed < :sevenDaysAgoMs")
    suspend fun expireOldRecentHistory(sevenDaysAgoMs: Long)

    @Query("UPDATE audio_tracks SET lastPlayed = 0 WHERE folderName = 'YouTube' AND lastPlayed > 0")
    suspend fun clearRecentYouTubeHistory()
=======
    @Query("SELECT * FROM audio_tracks WHERE lastPlayed > 0 ORDER BY lastPlayed DESC LIMIT 50")
    fun getRecentTracks(): Flow<List<AudioTrackEntity>>
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

    @Query("SELECT * FROM audio_tracks WHERE dateAdded > 0 ORDER BY dateAdded DESC LIMIT 50")
    fun getRecentlyAddedTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE playCount > 0 ORDER BY playCount DESC, lastPlayed DESC LIMIT 50")
    fun getMostPlayedTracks(): Flow<List<AudioTrackEntity>>

    // --- Smart playlists ---

    @Query("SELECT * FROM audio_tracks WHERE playCount = 0 AND uri NOT LIKE 'procedural://%' ORDER BY title ASC LIMIT 100")
    fun getNeverPlayedTracks(): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE durationMs >= :minMs AND uri NOT LIKE 'procedural://%' ORDER BY durationMs DESC LIMIT 100")
    fun getLongTracks(minMs: Long = 600_000L): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE year = :year AND uri NOT LIKE 'procedural://%' ORDER BY title ASC LIMIT 100")
    fun getTracksReleasedThisYear(year: Int): Flow<List<AudioTrackEntity>>

    @Query("SELECT * FROM audio_tracks WHERE mood = :mood AND uri NOT LIKE 'procedural://%' ORDER BY title ASC")
    fun getTracksByMood(mood: String): Flow<List<AudioTrackEntity>>

    @Query("UPDATE audio_tracks SET mood = :mood WHERE id = :id")
    suspend fun updateMood(id: Int, mood: String)

    /** Total estimated listening time in milliseconds (playCount * durationMs). */
    @Query("SELECT COALESCE(SUM(CAST(playCount AS INTEGER) * CAST(durationMs AS INTEGER)), 0) FROM audio_tracks WHERE uri NOT LIKE 'procedural://%'")
    suspend fun getTotalListeningMs(): Long

    /** Top N artists sorted by total play count. Returns list of 'artist|totalPlays'. */
    @Query("SELECT artist || '|' || SUM(playCount) as combo FROM audio_tracks WHERE playCount > 0 AND uri NOT LIKE 'procedural://%' GROUP BY artist ORDER BY SUM(playCount) DESC LIMIT :limit")
    suspend fun getTopArtistsByPlayCount(limit: Int = 5): List<String>

    /** Returns tracks whose (title, artist) pair appears more than once — potential duplicates. */
    @Query("""
        SELECT * FROM audio_tracks
        WHERE uri NOT LIKE 'procedural://%'
          AND (title || '|' || artist) IN (
            SELECT title || '|' || artist FROM audio_tracks
            WHERE uri NOT LIKE 'procedural://%'
            GROUP BY title, artist HAVING COUNT(*) > 1
          )
        ORDER BY title ASC, artist ASC
    """)
    suspend fun getDuplicateTracks(): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks WHERE uri = :uri LIMIT 1")
    suspend fun getTrackByUri(uri: String): AudioTrackEntity?

<<<<<<< HEAD
    @Query("SELECT * FROM audio_tracks WHERE id = :id LIMIT 1")
    suspend fun getTrackById(id: Int): AudioTrackEntity?

    @Query("SELECT * FROM audio_tracks WHERE category = :category LIMIT 1")
    suspend fun getTrackByCategory(category: String): AudioTrackEntity?

    @Query("SELECT * FROM playlists WHERE id = :playlistId LIMIT 1")
    suspend fun getPlaylistById(playlistId: Int): PlaylistEntity?

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    @Query("SELECT * FROM audio_tracks WHERE uri IN (:uris)")
    suspend fun getTracksByUris(uris: List<String>): List<AudioTrackEntity>

    @Query("SELECT * FROM audio_tracks ORDER BY title ASC")
    suspend fun getAllTracksSnapshot(): List<AudioTrackEntity>

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
<<<<<<< HEAD
            uri = :uri,
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
        WHERE id = :id
        """
    )
    suspend fun updateTrackIdentityAndMetadata(
        id: Int,
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
=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
            title = :title,
            artist = :artist,
            album = :album
        WHERE id = :id
        """
    )
    suspend fun updateUserTags(id: Int, title: String, artist: String, album: String)

    @Query("UPDATE audio_tracks SET lyrics = :lyrics WHERE id = :id")
    suspend fun updateLyrics(id: Int, lyrics: String?)

    @Query("UPDATE audio_tracks SET lrcLyrics = :lrc WHERE id = :id")
    suspend fun updateLrcLyrics(id: Int, lrc: String?)

    @Query("UPDATE audio_tracks SET rating = :rating WHERE id = :id")
    suspend fun updateRating(id: Int, rating: Int)

    @Query("UPDATE audio_tracks SET bpm = :bpm WHERE id = :id")
    suspend fun updateBpm(id: Int, bpm: Float)

    @Query("UPDATE audio_tracks SET replayGainDb = :db WHERE id = :id")
    suspend fun updateReplayGain(id: Int, db: Float)

    @Query("UPDATE audio_tracks SET isFavorite = :isFavorite WHERE id = :id")
    suspend fun toggleFavorite(id: Int, isFavorite: Boolean)

    @Query("UPDATE audio_tracks SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :id")
    suspend fun incrementPlayCount(id: Int, timestamp: Long)

    @Query("DELETE FROM audio_tracks WHERE uri = :uri")
    suspend fun deleteTrackByUri(uri: String)

<<<<<<< HEAD
    @Query("DELETE FROM audio_tracks WHERE id = :id")
    suspend fun deleteTrackById(id: Int)

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    @Query("SELECT * FROM audio_tracks WHERE folderName = :folderName")
    suspend fun getTracksByFolder(folderName: String): List<AudioTrackEntity>

    @Query("DELETE FROM audio_tracks WHERE folderName = :folderName")
    suspend fun deleteTracksByFolder(folderName: String)

    @Query("DELETE FROM audio_tracks WHERE category != 'My Device' AND uri NOT LIKE 'procedural://%'")
    suspend fun deleteNonDeviceTracks()

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    suspend fun getAllPlaylistsSnapshot(): List<PlaylistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Int)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
<<<<<<< HEAD
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRefEntity): Long

    @Query("SELECT EXISTS(SELECT 1 FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId)")
    suspend fun isTrackInPlaylist(playlistId: Int, trackId: Int): Boolean

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun getMaxPlaylistPosition(playlistId: Int): Int

    @Query("SELECT * FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun getCrossRefsForTrack(trackId: Int): List<PlaylistTrackCrossRefEntity>

    @Query("SELECT * FROM playlist_track_cross_ref")
    suspend fun getAllCrossRefs(): List<PlaylistTrackCrossRefEntity>
=======
    suspend fun insertPlaylistTrackCrossRef(crossRef: PlaylistTrackCrossRefEntity)
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e

    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun deletePlaylistTrackCrossRef(playlistId: Int, trackId: Int)

    @Query(
        """
        SELECT audio_tracks.* FROM audio_tracks 
        INNER JOIN playlist_track_cross_ref ON audio_tracks.id = playlist_track_cross_ref.trackId 
        WHERE playlist_track_cross_ref.playlistId = :playlistId
<<<<<<< HEAD
        ORDER BY playlist_track_cross_ref.position ASC, playlist_track_cross_ref.trackId ASC
=======
        ORDER BY audio_tracks.title ASC
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
        """
    )
    fun getTracksInPlaylist(playlistId: Int): Flow<List<AudioTrackEntity>>

        @Query(
                """
                SELECT audio_tracks.* FROM audio_tracks 
                INNER JOIN playlist_track_cross_ref ON audio_tracks.id = playlist_track_cross_ref.trackId 
                WHERE playlist_track_cross_ref.playlistId = :playlistId
<<<<<<< HEAD
                ORDER BY playlist_track_cross_ref.position ASC, playlist_track_cross_ref.trackId ASC
=======
                ORDER BY audio_tracks.title ASC
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
                """
        )
        suspend fun getTracksInPlaylistSnapshot(playlistId: Int): List<AudioTrackEntity>

        @Query("""
                SELECT * FROM audio_tracks
                WHERE uri NOT LIKE 'procedural://%'
                    AND (title || '|' || artist) IN (
                        SELECT title || '|' || artist FROM audio_tracks
                        WHERE uri NOT LIKE 'procedural://%'
                        GROUP BY title, artist HAVING COUNT(*) > 1
                    )
                ORDER BY title ASC, artist ASC
        """)
        suspend fun getTracksWithDuplicateTitles(): List<AudioTrackEntity>

<<<<<<< HEAD
    @Query("DELETE FROM audio_tracks WHERE (uri = '' OR uri IS NULL) AND folderName = 'YouTube'")
    suspend fun deleteInvalidEmptyUriTracks()

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId NOT IN (SELECT id FROM audio_tracks)")
    suspend fun deleteOrphanCrossRefs()

=======
>>>>>>> 8eae55c7096dcedd8d935cf41932467cdb84c41e
    @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
    suspend fun deleteCrossRefsForPlaylist(playlistId: Int)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId = :trackId")
    suspend fun deleteCrossRefsForTrack(trackId: Int)
}
