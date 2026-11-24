package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
  @Test
  fun synthUri_isProcedural() {
    assertEquals("procedural://synth", AudioRepository.SYNTH_URI)
  }

  @Test
  fun synthLyrics_arePresent() {
    assertTrue(AudioRepository.SYNTH_LYRICS.contains("Neon Pulse"))
  }

  @Test
  fun testCrossDevicePlaylistImportExport() = kotlinx.coroutines.runBlocking {
    val localTrack = AudioTrackEntity(
        id = 1,
        uri = "content://media/external/audio/media/111",
        title = "Local Song",
        artist = "Artist A",
        album = "Album A",
        durationMs = 200000L
    )
    val ytTrack = AudioTrackEntity(
        id = 2,
        uri = "youtube://dQw4w9WgXcQ",
        title = "Never Gonna Give You Up",
        artist = "Rick Astley",
        category = "yt:dQw4w9WgXcQ",
        folderName = "YouTube",
        durationMs = 212000L
    )

    val playlists = listOf(PlaylistEntity(id = 1, name = "My Favorite Mix"))
    val playlistTracks = mapOf(1 to listOf(localTrack, ytTrack))

    val json = BackupRestoreManager.exportBackup(
        playlists = playlists,
        getTracksInPlaylist = { plId -> playlistTracks[plId] ?: emptyList() },
        allTracks = listOf(localTrack, ytTrack)
    )

    // Simulate import on a different phone where URIs are completely changed
    val phoneBLocalTrack = localTrack.copy(id = 10, uri = "content://media/external/audio/media/999") // different URI!
    val phoneBLibrary = listOf(phoneBLocalTrack) // ytTrack is not yet in phone B library, will be auto-restored!

    val createdPlaylists = mutableListOf<String>()
    val playlistTrackMap = mutableMapOf<Int, MutableList<Int>>()

    val result = BackupRestoreManager.importBackup(
        json = json,
        allTracks = phoneBLibrary,
        findOrInsertTrack = { _ -> 20 },
        createPlaylist = { name -> createdPlaylists.add(name); createdPlaylists.size },
        addTrackToPlaylist = { plId, trackId -> playlistTrackMap.getOrPut(plId) { mutableListOf() }.add(trackId) },
        updateFavorite = { _, _ -> },
        updateRating = { _, _ -> },
        updateMood = { _, _ -> },
        updateLyrics = { _, _ -> },
        updateLrcLyrics = { _, _ -> },
        updateBpm = { _, _ -> },
        updateReplayGain = { _, _ -> }
    )

    assertEquals(1, result.playlistsRestored)
    assertEquals(1, result.localTracksMatched)
    assertEquals(1, result.onlineTracksMatched)
    assertTrue(result.unmatchedTracks.isEmpty())
  }
}
