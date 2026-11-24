package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaylistExportImportTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun testSerializationAndDeserialization_v3Schema() {
        val tracks = listOf(
            BackupPlaylistTrack(
                position = 0,
                title = "Midnight Drive",
                artist = "Neon Synth",
                album = "Glassmorphic Dreams",
                durationMs = 210000L,
                year = 2024,
                category = "My Device",
                folderName = "Music",
                uri = "content://media/external/audio/media/101",
                isOnline = false,
                rating = 5,
                mood = "Chill",
                lyrics = "Driving into neon lights",
                bpm = 120.0f,
                replayGainDb = -1.2f,
                isFavorite = true
            ),
            BackupPlaylistTrack(
                position = 1,
                title = "Never Gonna Give You Up",
                artist = "Rick Astley",
                album = "YouTube Music",
                durationMs = 212000L,
                year = 1987,
                category = "yt:dQw4w9WgXcQ",
                folderName = "YouTube",
                uri = "youtube://dQw4w9WgXcQ",
                isOnline = true,
                youtubeVideoId = "dQw4w9WgXcQ",
                onlineUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                thumbnailUrl = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg",
                rating = 4,
                mood = "Party"
            )
        )

        val playlist = BackupPlaylist(
            id = 1,
            name = "Retro Waves",
            description = "Chill retro synth & stream",
            type = "Mixed",
            createdAt = 1700000000000L,
            tracks = tracks
        )

        val backupPackage = PlaylistBackupPackage(
            app = "GlassPlayer",
            version = 3,
            exportedAt = 1700000050000L,
            type = "single_playlist",
            playlists = listOf(playlist)
        )

        val json = BackupRestoreManager.serializePackage(backupPackage)
        assertTrue(json.contains("\"version\": 3"))
        assertTrue(json.contains("\"app\": \"GlassPlayer\""))
        assertTrue(json.contains("\"name\": \"Retro Waves\""))
        assertTrue(json.contains("\"youtubeVideoId\": \"dQw4w9WgXcQ\""))
        assertTrue(json.contains("\"isOnline\": true"))
        assertTrue(json.contains("\"isFavorite\": true"))

        // Deserialize back and verify fidelity
        val deserialized = BackupRestoreManager.deserializePackage(json)
        assertEquals("GlassPlayer", deserialized.app)
        assertEquals(3, deserialized.version)
        assertEquals(1, deserialized.playlists.size)

        val pl = deserialized.playlists.first()
        assertEquals("Retro Waves", pl.name)
        assertEquals("Mixed", pl.type)
        assertEquals(2, pl.tracks.size)

        val tr0 = pl.tracks[0]
        assertEquals("Midnight Drive", tr0.title)
        assertEquals("Neon Synth", tr0.artist)
        assertFalse(tr0.isOnline)
        assertEquals(5, tr0.rating)
        assertEquals("Chill", tr0.mood)
        assertEquals(120.0f, tr0.bpm, 0.01f)
        assertTrue(tr0.isFavorite)

        val tr1 = pl.tracks[1]
        assertEquals("Never Gonna Give You Up", tr1.title)
        assertTrue(tr1.isOnline)
        assertEquals("dQw4w9WgXcQ", tr1.youtubeVideoId)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", tr1.onlineUrl)
    }

    @Test
    fun testTrackMatchingPreview_identifiesLocalOnlineAndUnavailable() = runBlocking {
        val existingLibrary = listOf(
            AudioTrackEntity(
                id = 10,
                uri = "content://media/external/audio/media/101",
                title = "Midnight Drive",
                artist = "Neon Synth",
                durationMs = 210000L
            ),
            AudioTrackEntity(
                id = 20,
                uri = "content://media/external/audio/media/202",
                title = "Starfall",
                artist = "Cyber Pulse",
                durationMs = 180000L
            )
        )

        val existingPlaylists = listOf(
            PlaylistEntity(id = 1, name = "Retro Waves")
        )

        val backupPackage = PlaylistBackupPackage(
            app = "GlassPlayer",
            version = 3,
            playlists = listOf(
                BackupPlaylist(
                    id = 1,
                    name = "Retro Waves", // Same name -> Conflict!
                    tracks = listOf(
                        // 1. Exact URI match
                        BackupPlaylistTrack(
                            position = 0,
                            title = "Midnight Drive",
                            artist = "Neon Synth",
                            uri = "content://media/external/audio/media/101",
                            isOnline = false
                        ),
                        // 2. Metadata match (different URI, same title & artist)
                        BackupPlaylistTrack(
                            position = 1,
                            title = "Starfall",
                            artist = "Cyber Pulse",
                            uri = "content://old_device/music/starfall.mp3",
                            isOnline = false
                        ),
                        // 3. Online track
                        BackupPlaylistTrack(
                            position = 2,
                            title = "Blinding Lights",
                            artist = "The Weeknd",
                            uri = "youtube://4NRXx6U8ABQ",
                            isOnline = true,
                            youtubeVideoId = "4NRXx6U8ABQ"
                        ),
                        // 4. Missing local file on this device -> Should be marked UNAVAILABLE_LOCAL
                        BackupPlaylistTrack(
                            position = 3,
                            title = "Rare Cassette Track",
                            artist = "Unknown Tape",
                            uri = "/storage/emulated/0/Music/tape.mp3",
                            isOnline = false
                        )
                    )
                )
            )
        )

        val preview = BackupRestoreManager.buildImportPreview(
            context = context,
            backupPackage = backupPackage,
            isZip = false,
            sourceUri = null,
            existingPlaylists = existingPlaylists,
            allTracks = existingLibrary
        )

        assertEquals(1, preview.playlistPreviews.size)
        val plPreview = preview.playlistPreviews.first()
        assertTrue("Should detect name conflict", plPreview.hasConflict)
        assertEquals(1, plPreview.existingPlaylistId)

        assertEquals(4, plPreview.trackPreviews.size)
        assertEquals(TrackMatchStatus.EXACT_URI, plPreview.trackPreviews[0].matchStatus)
        assertEquals(10, plPreview.trackPreviews[0].matchedTrackId)

        assertEquals(TrackMatchStatus.MATCHED_LOCAL_METADATA, plPreview.trackPreviews[1].matchStatus)
        assertEquals(20, plPreview.trackPreviews[1].matchedTrackId)

        assertEquals(TrackMatchStatus.ONLINE_STREAM, plPreview.trackPreviews[2].matchStatus)

        assertEquals(TrackMatchStatus.UNAVAILABLE_LOCAL, plPreview.trackPreviews[3].matchStatus)
    }

    @Test
    fun testZipArchivePackagingAndInspection() {
        val tracks = listOf(
            BackupPlaylistTrack(
                position = 0,
                title = "Local Acoustic",
                artist = "Acoustic Artist",
                uri = "file:///dummy/path.mp3",
                localRelativePath = "tracks/Acoustic Artist - Local Acoustic.mp3",
                isOnline = false
            )
        )
        val pkg = PlaylistBackupPackage(
            app = "GlassPlayer",
            version = 3,
            playlists = listOf(BackupPlaylist(id = 1, name = "Acoustic Jam", tracks = tracks))
        )

        // Write a test zip in memory
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zipOut ->
            // Manifest
            zipOut.putNextEntry(ZipEntry(BackupRestoreManager.MANIFEST_ENTRY_NAME))
            zipOut.write(BackupRestoreManager.serializePackage(pkg).toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // Audio track
            zipOut.putNextEntry(ZipEntry("tracks/Acoustic Artist - Local Acoustic.mp3"))
            zipOut.write("AUDIO_BYTE_CONTENT".toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()
        }

        val zipBytes = baos.toByteArray()

        // Read zip back using ZipInputStream
        var foundManifest = false
        var foundAudio = false
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zipIn ->
            var entry = zipIn.nextEntry
            while (entry != null) {
                if (entry.name == BackupRestoreManager.MANIFEST_ENTRY_NAME) {
                    foundManifest = true
                    val manifestText = zipIn.bufferedReader().readText()
                    val parsed = BackupRestoreManager.deserializePackage(manifestText)
                    assertEquals("Acoustic Jam", parsed.playlists.first().name)
                } else if (entry.name == "tracks/Acoustic Artist - Local Acoustic.mp3") {
                    foundAudio = true
                }
                zipIn.closeEntry()
                entry = zipIn.nextEntry
            }
        }

        assertTrue(foundManifest)
        assertTrue(foundAudio)
    }

    @Test
    fun testBackwardCompatibility_v1AndV2Formats() {
        // v1 format: simple JSON with string URI array
        val v1Json = """
            {
                "version": 1,
                "exportedAt": 1600000000000,
                "playlists": [
                    {
                        "name": "Legacy Rock",
                        "tracks": [
                            "content://media/external/audio/media/42",
                            "youtube://dQw4w9WgXcQ"
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parsedV1 = BackupRestoreManager.deserializePackage(v1Json)
        assertEquals(1, parsedV1.playlists.size)
        val pl1 = parsedV1.playlists.first()
        assertEquals("Legacy Rock", pl1.name)
        assertEquals(2, pl1.tracks.size)
        assertEquals("content://media/external/audio/media/42", pl1.tracks[0].uri)
        assertTrue(pl1.tracks[1].isOnline)
        assertEquals("dQw4w9WgXcQ", pl1.tracks[1].youtubeVideoId)

        // v2 format: tracks are JSON objects without v3 fields
        val v2Json = """
            {
                "version": 2,
                "exportedAt": 1650000000000,
                "playlists": [
                    {
                        "name": "V2 Playlist",
                        "tracks": [
                            {
                                "uri": "content://media/external/audio/media/99",
                                "title": "Synth Horizon",
                                "artist": "Glass Artist",
                                "durationMs": 240000,
                                "youtubeVideoId": null
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val parsedV2 = BackupRestoreManager.deserializePackage(v2Json)
        assertEquals(1, parsedV2.playlists.size)
        val pl2 = parsedV2.playlists.first()
        assertEquals("V2 Playlist", pl2.name)
        assertEquals("Synth Horizon", pl2.tracks.first().title)
        assertEquals("Glass Artist", pl2.tracks.first().artist)
        assertEquals(240000L, pl2.tracks.first().durationMs)
    }

    @Test
    fun testUnavailableTrackHelper_preservesStatusWithoutDataLoss() {
        val unavailableTrack = AudioTrackEntity(
            id = 55,
            uri = "${UNAVAILABLE_URI_PREFIX}test-uuid-1234",
            title = "Missing Track On Device",
            artist = "Lost Band",
            durationMs = 195000L,
            category = FOLDER_UNAVAILABLE,
            folderName = FOLDER_UNAVAILABLE
        )

        assertTrue(unavailableTrack.isUnavailable())
        assertFalse(unavailableTrack.canPlayWithExoPlayer())
        assertEquals("Missing Track On Device", unavailableTrack.title)
    }
}
