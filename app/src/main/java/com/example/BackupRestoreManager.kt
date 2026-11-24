package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Complete Playlist Backup, Export, and Import System for GlassPlayer.
 *
 * Supports:
 * - Versioned structured JSON schema (.glassplaylist)
 * - ZIP archive packaging for offline transfers with audio files (.zip)
 * - Both Local and Online (YouTube) playlists
 * - Cross-device matching (URI, hash/fingerprint, artist+title, title, filename)
 * - Conflict resolution (Merge, Replace, Create New)
 * - Interactive preview before restoring
 * - Preservation of unavailable tracks without silent data loss
 * - Database transaction safety
 */
object BackupRestoreManager {

    private const val TAG = "BackupRestoreManager"
    const val CURRENT_SCHEMA_VERSION = 3
    const val MANIFEST_ENTRY_NAME = "manifest.glassplaylist"
    const val TRACKS_DIR = "tracks/"
    const val ARTWORK_DIR = "artwork/"

    // ── Helper Text Normalization ─────────────────────────────────────────────

    fun normalizeText(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    fun normalizeKey(artist: String, title: String): String =
        "${normalizeText(artist)}|${normalizeText(title)}"

    fun sanitizeFileName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "track" }

    // ── Fingerprint Helper for Fast Local Audio Matching ──────────────────────

    fun computeAudioFingerprint(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val md = MessageDigest.getInstance("MD5")
                val buffer = ByteArray(65536) // 64KB header fingerprint
                val bytesRead = input.read(buffer)
                if (bytesRead > 0) {
                    md.update(buffer, 0, bytesRead)
                    md.digest().joinToString("") { "%02x".format(it) }
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    // ── JSON Serialization (Schema v3) ────────────────────────────────────────

    fun serializePackage(pkg: PlaylistBackupPackage): String {
        val root = JSONObject()
        root.put("app", pkg.app)
        root.put("version", pkg.version)
        root.put("exportedAt", pkg.exportedAt)
        root.put("type", pkg.type)

        val playlistsArr = JSONArray()
        for (pl in pkg.playlists) {
            val plObj = JSONObject()
            plObj.put("id", pl.id)
            plObj.put("uuid", pl.uuid)
            plObj.put("name", pl.name)
            plObj.put("description", pl.description)
            plObj.put("type", pl.type)
            plObj.put("createdAt", pl.createdAt)
            pl.artworkPath?.let { plObj.put("artworkPath", it) }

            val tracksArr = JSONArray()
            for (track in pl.tracks) {
                val tObj = JSONObject()
                tObj.put("position", track.position)
                tObj.put("title", track.title)
                tObj.put("artist", track.artist)
                tObj.put("album", track.album)
                tObj.put("durationMs", track.durationMs)
                tObj.put("year", track.year)
                tObj.put("category", track.category)
                tObj.put("folderName", track.folderName)
                tObj.put("uri", track.uri)
                track.localRelativePath?.let { tObj.put("localRelativePath", it) }
                track.fileName?.let { tObj.put("fileName", it) }
                track.fileHash?.let { tObj.put("fileHash", it) }
                if (track.fileSize > 0L) tObj.put("fileSize", track.fileSize)
                tObj.put("isOnline", track.isOnline)
                track.youtubeVideoId?.let { tObj.put("youtubeVideoId", it) }
                track.onlineUrl?.let { tObj.put("onlineUrl", it) }
                track.thumbnailUrl?.let { tObj.put("thumbnailUrl", it) }
                tObj.put("isAvailable", track.isAvailable)
                if (track.rating > 0) tObj.put("rating", track.rating)
                if (track.mood.isNotBlank()) tObj.put("mood", track.mood)
                track.lyrics?.let { tObj.put("lyrics", it) }
                track.lrcLyrics?.let { tObj.put("lrcLyrics", it) }
                if (track.bpm > 0f) tObj.put("bpm", track.bpm.toDouble())
                if (track.replayGainDb != 0f) tObj.put("replayGainDb", track.replayGainDb.toDouble())
                if (track.isFavorite) tObj.put("isFavorite", true)

                tracksArr.put(tObj)
            }
            plObj.put("tracks", tracksArr)
            playlistsArr.put(plObj)
        }
        root.put("playlists", playlistsArr)

        val metaArr = JSONArray()
        for (m in pkg.trackMeta) {
            val mObj = JSONObject()
            mObj.put("uri", m.uri)
            mObj.put("title", m.title)
            mObj.put("artist", m.artist)
            m.youtubeVideoId?.let { mObj.put("youtubeVideoId", it) }
            if (m.favorite) mObj.put("favorite", true)
            if (m.rating > 0) mObj.put("rating", m.rating)
            if (m.mood.isNotBlank()) mObj.put("mood", m.mood)
            m.lyrics?.let { mObj.put("lyrics", it) }
            m.lrcLyrics?.let { mObj.put("lrcLyrics", it) }
            if (m.bpm > 0f) mObj.put("bpm", m.bpm.toDouble())
            if (m.replayGainDb != 0f) mObj.put("replayGainDb", m.replayGainDb.toDouble())
            metaArr.put(mObj)
        }
        root.put("trackMeta", metaArr)

        return root.toString(2)
    }

    // ── JSON Deserialization (Compatible with v1, v2, v3) ─────────────────────

    fun deserializePackage(json: String): PlaylistBackupPackage {
        val root = JSONObject(json)
        val app = root.optString("app", "GlassPlayer")
        val version = root.optInt("version", 1)
        val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
        val type = root.optString("type", "backup")

        val playlists = mutableListOf<BackupPlaylist>()
        val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()

        for (i in 0 until playlistsArr.length()) {
            val plObj = playlistsArr.optJSONObject(i) ?: continue
            val id = plObj.optInt("id", 0)
            val uuid = plObj.optString("uuid", UUID.randomUUID().toString())
            val name = plObj.optString("name", "Imported Playlist")
            val description = plObj.optString("description", "")
            val rawType = plObj.optString("type", "Mixed")
            val createdAt = plObj.optLong("createdAt", System.currentTimeMillis())
            val artworkPath = plObj.optString("artworkPath").takeIf { it.isNotBlank() }

            val tracks = mutableListOf<BackupPlaylistTrack>()
            val tracksArr = plObj.optJSONArray("tracks") ?: JSONArray()

            for (j in 0 until tracksArr.length()) {
                val item = tracksArr.get(j)
                if (item is JSONObject) {
                    val uri = item.optString("uri", "")
                    val title = item.optString("title", "Unknown Track")
                    val artist = item.optString("artist", "Unknown Artist")
                    val album = item.optString("album", "Glassmorphic Dreams")
                    val durationMs = item.optLong("durationMs", 0L)
                    val year = item.optInt("year", 0)
                    val category = item.optString("category", "Library")
                    val folderName = item.optString("folderName", "Default")
                    val localRelativePath = item.optString("localRelativePath").takeIf { it.isNotBlank() }
                    val fileName = item.optString("fileName").takeIf { it.isNotBlank() }
                    val fileHash = item.optString("fileHash").takeIf { it.isNotBlank() }
                    val fileSize = item.optLong("fileSize", 0L)

                    val tempTrack = AudioTrackEntity(uri = uri, title = title, artist = artist, durationMs = durationMs)
                    val ytId = item.optString("youtubeVideoId").takeIf { it.isNotBlank() } ?: tempTrack.youtubeVideoId()
                    val isOnline = item.optBoolean("isOnline", ytId != null || tempTrack.isYouTubeTrack())
                    val onlineUrl = item.optString("onlineUrl").takeIf { it.isNotBlank() }
                        ?: ytId?.let { "https://www.youtube.com/watch?v=$it" }
                    val thumbnailUrl = item.optString("thumbnailUrl").takeIf { it.isNotBlank() }
                        ?: ytId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" }
                    val isAvailable = item.optBoolean("isAvailable", true)

                    val rating = item.optInt("rating", 0)
                    val mood = item.optString("mood", "")
                    val lyrics = item.optString("lyrics").takeIf { it.isNotBlank() }
                    val lrcLyrics = item.optString("lrcLyrics").takeIf { it.isNotBlank() }
                    val bpm = item.optDouble("bpm", 0.0).toFloat()
                    val replayGainDb = item.optDouble("replayGainDb", 0.0).toFloat()
                    val isFavorite = item.optBoolean("isFavorite", false)

                    tracks.add(
                        BackupPlaylistTrack(
                            position = item.optInt("position", j),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            year = year,
                            category = category,
                            folderName = folderName,
                            uri = uri,
                            localRelativePath = localRelativePath,
                            fileName = fileName,
                            fileHash = fileHash,
                            fileSize = fileSize,
                            isOnline = isOnline,
                            youtubeVideoId = ytId,
                            onlineUrl = onlineUrl,
                            thumbnailUrl = thumbnailUrl,
                            isAvailable = isAvailable,
                            rating = rating,
                            mood = mood,
                            lyrics = lyrics,
                            lrcLyrics = lrcLyrics,
                            bpm = bpm,
                            replayGainDb = replayGainDb,
                            isFavorite = isFavorite
                        )
                    )
                } else if (item is String) {
                    // Backward compatibility for v1 string URI array
                    val temp = AudioTrackEntity(uri = item, title = "", artist = "", durationMs = 0L)
                    val ytId = temp.youtubeVideoId()
                    tracks.add(
                        BackupPlaylistTrack(
                            position = j,
                            title = item.substringAfterLast('/').substringBefore('.').ifBlank { "Track $j" },
                            artist = "Unknown",
                            uri = item,
                            isOnline = ytId != null,
                            youtubeVideoId = ytId
                        )
                    )
                }
            }

            val determinedType = when {
                tracks.isEmpty() -> rawType
                tracks.all { it.isOnline } -> "Online"
                tracks.none { it.isOnline } -> "Local"
                else -> "Mixed"
            }

            playlists.add(
                BackupPlaylist(
                    id = id,
                    uuid = uuid,
                    name = name,
                    description = description,
                    type = determinedType,
                    createdAt = createdAt,
                    artworkPath = artworkPath,
                    tracks = tracks
                )
            )
        }

        val trackMeta = mutableListOf<BackupTrackMeta>()
        val metaArr = root.optJSONArray("trackMeta") ?: JSONArray()
        for (i in 0 until metaArr.length()) {
            val mObj = metaArr.optJSONObject(i) ?: continue
            trackMeta.add(
                BackupTrackMeta(
                    uri = mObj.optString("uri", ""),
                    title = mObj.optString("title", ""),
                    artist = mObj.optString("artist", ""),
                    youtubeVideoId = mObj.optString("youtubeVideoId").takeIf { it.isNotBlank() },
                    favorite = mObj.optBoolean("favorite", false),
                    rating = mObj.optInt("rating", 0),
                    mood = mObj.optString("mood", ""),
                    lyrics = mObj.optString("lyrics").takeIf { it.isNotBlank() },
                    lrcLyrics = mObj.optString("lrcLyrics").takeIf { it.isNotBlank() },
                    bpm = mObj.optDouble("bpm", 0.0).toFloat(),
                    replayGainDb = mObj.optDouble("replayGainDb", 0.0).toFloat()
                )
            )
        }

        return PlaylistBackupPackage(
            app = app,
            version = version,
            exportedAt = exportedAt,
            type = type,
            playlists = playlists,
            trackMeta = trackMeta
        )
    }

    // ── Build Backup Package from Database ────────────────────────────────────

    suspend fun buildBackupPackage(
        context: Context,
        playlists: List<PlaylistEntity>,
        getTracksInPlaylist: suspend (Int) -> List<AudioTrackEntity>,
        allTracks: List<AudioTrackEntity>,
        exportType: String = "backup",
        includeRelativeFilePaths: Boolean = false
    ): PlaylistBackupPackage = withContext(Dispatchers.Default) {
        val backupPlaylists = mutableListOf<BackupPlaylist>()

        for (pl in playlists) {
            val tracks = getTracksInPlaylist(pl.id)
            val backupTracks = tracks.mapIndexed { index, track ->
                val isOnline = track.isYouTubeTrack()
                val ytId = track.youtubeVideoId()
                val fileName = if (!isOnline && track.uri.isNotBlank()) {
                    try {
                        val uri = Uri.parse(track.uri)
                        uri.lastPathSegment?.substringAfterLast('/') ?: "${sanitizeFileName("${track.artist} - ${track.title}")}.mp3"
                    } catch (_: Exception) {
                        "${sanitizeFileName("${track.artist} - ${track.title}")}.mp3"
                    }
                } else null

                val localRelativePath = if (includeRelativeFilePaths && !isOnline) {
                    val safeExt = fileName?.substringAfterLast('.', "mp3") ?: "mp3"
                    "$TRACKS_DIR${sanitizeFileName("${track.artist} - ${track.title}")}.$safeExt"
                } else null

                val hash = if (!isOnline && track.uri.isNotBlank()) {
                    try {
                        computeAudioFingerprint(context, Uri.parse(track.uri))
                    } catch (_: Exception) { null }
                } else null

                BackupPlaylistTrack(
                    position = index,
                    title = track.title,
                    artist = track.artist,
                    album = track.album,
                    durationMs = track.durationMs,
                    year = track.year,
                    category = track.category,
                    folderName = track.folderName,
                    uri = track.uri,
                    localRelativePath = localRelativePath,
                    fileName = fileName,
                    fileHash = hash,
                    fileSize = 0L,
                    isOnline = isOnline,
                    youtubeVideoId = ytId,
                    onlineUrl = ytId?.let { "https://www.youtube.com/watch?v=$it" },
                    thumbnailUrl = if (isOnline) track.albumArtUri ?: ytId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" } else null,
                    isAvailable = !track.isUnavailable(),
                    rating = track.rating,
                    mood = track.mood,
                    lyrics = track.lyrics,
                    lrcLyrics = track.lrcLyrics,
                    bpm = track.bpm,
                    replayGainDb = track.replayGainDb,
                    isFavorite = track.isFavorite
                )
            }

            val determinedType = when {
                backupTracks.isEmpty() -> "Local"
                backupTracks.all { it.isOnline } -> "Online"
                backupTracks.none { it.isOnline } -> "Local"
                else -> "Mixed"
            }

            backupPlaylists.add(
                BackupPlaylist(
                    id = pl.id,
                    name = pl.name,
                    createdAt = pl.createdAt,
                    type = determinedType,
                    tracks = backupTracks
                )
            )
        }

        // Global track metadata (only relevant when doing multi-playlist / library backup)
        val metaList = mutableListOf<BackupTrackMeta>()
        for (track in allTracks) {
            if (track.uri == AudioRepository.SYNTH_URI) continue
            val anyNonDefault = track.isFavorite || track.rating > 0 || track.mood.isNotEmpty() ||
                track.lyrics != null || track.lrcLyrics != null || track.bpm > 0f ||
                track.replayGainDb != 0f
            if (!anyNonDefault) continue
            metaList.add(
                BackupTrackMeta(
                    uri = track.uri,
                    title = track.title,
                    artist = track.artist,
                    youtubeVideoId = track.youtubeVideoId(),
                    favorite = track.isFavorite,
                    rating = track.rating,
                    mood = track.mood,
                    lyrics = track.lyrics,
                    lrcLyrics = track.lrcLyrics,
                    bpm = track.bpm,
                    replayGainDb = track.replayGainDb
                )
            )
        }

        PlaylistBackupPackage(
            app = "GlassPlayer",
            version = CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            type = exportType,
            playlists = backupPlaylists,
            trackMeta = metaList
        )
    }

    // ── Export ZIP with Audio Files ───────────────────────────────────────────

    suspend fun exportBackupZip(
        context: Context,
        outputStream: OutputStream,
        backupPackage: PlaylistBackupPackage,
        onProgress: ((BackupProgress) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var zipOut: ZipOutputStream? = null
        try {
            zipOut = ZipOutputStream(BufferedOutputStream(outputStream))

            // 1. Write manifest.glassplaylist
            val manifestJson = serializePackage(backupPackage)
            val manifestEntry = ZipEntry(MANIFEST_ENTRY_NAME)
            zipOut.putNextEntry(manifestEntry)
            zipOut.write(manifestJson.toByteArray(Charsets.UTF_8))
            zipOut.closeEntry()

            // 2. Identify unique local tracks to include
            val localTracksToCopy = mutableMapOf<String, BackupPlaylistTrack>()
            for (pl in backupPackage.playlists) {
                for (tr in pl.tracks) {
                    if (!tr.isOnline && tr.uri.isNotBlank() && !tr.uri.startsWith(UNAVAILABLE_URI_PREFIX)) {
                        val path = tr.localRelativePath ?: "$TRACKS_DIR${sanitizeFileName("${tr.artist} - ${tr.title}")}.mp3"
                        localTracksToCopy[path] = tr
                    }
                }
            }

            val totalFiles = localTracksToCopy.size
            var currentFile = 0

            val buffer = ByteArray(32768)
            for ((entryPath, track) in localTracksToCopy) {
                currentFile++
                onProgress?.invoke(
                    BackupProgress(
                        current = currentFile,
                        total = totalFiles,
                        currentItemName = "${track.artist} - ${track.title}",
                        message = "Packaging audio file $currentFile of $totalFiles..."
                    )
                )

                try {
                    val uri = Uri.parse(track.uri)
                    val input = if (track.uri.startsWith("file://") || track.uri.startsWith("/")) {
                        val path = if (track.uri.startsWith("file://")) uri.path ?: track.uri.removePrefix("file://") else track.uri
                        File(path).inputStream()
                    } else {
                        context.contentResolver.openInputStream(uri)
                    }

                    if (input != null) {
                        input.use { inStream ->
                            val zipEntry = ZipEntry(entryPath)
                            zipOut.putNextEntry(zipEntry)
                            var read: Int
                            while (inStream.read(buffer).also { read = it } != -1) {
                                zipOut.write(buffer, 0, read)
                            }
                            zipOut.closeEntry()
                        }
                    } else {
                        Log.w(TAG, "Could not open stream for local track: ${track.title} (${track.uri})")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error copying track file to ZIP: ${track.title}", e)
                }
            }

            zipOut.finish()
            zipOut.flush()
            true
        } catch (e: Exception) {
            Log.e(TAG, "ZIP export failed", e)
            false
        } finally {
            try {
                zipOut?.close()
            } catch (_: Exception) {}
        }
    }

    // ── Inspect and Parse Backup File from URI ────────────────────────────────

    suspend fun inspectBackupFromUri(
        context: Context,
        sourceUri: Uri
    ): Pair<PlaylistBackupPackage, Boolean> = withContext(Dispatchers.IO) {
        val cr = context.contentResolver

        // Check if stream is a ZIP archive
        var isZip = false
        try {
            cr.openInputStream(sourceUri)?.use { stream ->
                val header = ByteArray(4)
                val read = stream.read(header)
                if (read == 4 && header[0] == 0x50.toByte() && header[1] == 0x4B.toByte() &&
                    header[2] == 0x03.toByte() && header[3] == 0x04.toByte()
                ) {
                    isZip = true
                }
            }
        } catch (_: Exception) {}

        if (isZip) {
            var manifestText: String? = null
            val packagedEntries = mutableSetOf<String>()

            cr.openInputStream(sourceUri)?.use { rawIn ->
                ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        val name = entry.name
                        packagedEntries.add(name)
                        if (name == MANIFEST_ENTRY_NAME || name == "manifest.json" ||
                            name.endsWith(".glassplaylist", ignoreCase = true)
                        ) {
                            manifestText = zipIn.bufferedReader(Charsets.UTF_8).readText()
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }

            if (manifestText == null) {
                throw IllegalArgumentException("The selected ZIP file does not contain a valid GlassPlayer backup manifest ($MANIFEST_ENTRY_NAME).")
            }

            val pkg = deserializePackage(manifestText!!)
            // Verify if local tracks match entries inside the zip
            val updatedPlaylists = pkg.playlists.map { pl ->
                val updatedTracks = pl.tracks.map { tr ->
                    val path = tr.localRelativePath
                    val inZip = path != null && packagedEntries.contains(path)
                    if (inZip) tr.copy(localRelativePath = path) else tr
                }
                pl.copy(tracks = updatedTracks)
            }
            return@withContext pkg.copy(playlists = updatedPlaylists) to true
        } else {
            // Plain text (.glassplaylist or .json)
            val jsonText = cr.openInputStream(sourceUri)?.use {
                it.bufferedReader(Charsets.UTF_8).readText()
            } ?: throw IllegalArgumentException("Could not read content from selected file.")

            val pkg = deserializePackage(jsonText)
            return@withContext pkg to false
        }
    }

    // ── Build Interactive Import Preview ──────────────────────────────────────

    suspend fun buildImportPreview(
        context: Context,
        backupPackage: PlaylistBackupPackage,
        isZip: Boolean,
        sourceUri: Uri?,
        existingPlaylists: List<PlaylistEntity>,
        allTracks: List<AudioTrackEntity>
    ): PlaylistImportPreview = withContext(Dispatchers.Default) {
        val byUri = allTracks.associateBy { it.uri }
        val byYoutubeId = allTracks.mapNotNull { it.youtubeVideoId()?.let { yt -> yt to it } }.toMap()
        val byTitleArtist = allTracks.groupBy { normalizeKey(it.artist, it.title) }
            .mapValues { (_, list) -> list.first() }
        val byTitle = allTracks.groupBy { normalizeText(it.title) }
            .mapValues { (_, list) -> list.first() }
        val existingPlaylistsByName = existingPlaylists.associateBy { normalizeText(it.name) }

        val playlistPreviews = mutableListOf<PlaylistItemPreview>()

        for (pl in backupPackage.playlists) {
            val normalizedName = normalizeText(pl.name)
            val existing = existingPlaylistsByName[normalizedName]

            val trackPreviews = mutableListOf<TrackMatchPreview>()

            for (tr in pl.tracks) {
                val ytId = tr.youtubeVideoId
                val inZip = isZip && tr.localRelativePath != null

                val matchStatus: TrackMatchStatus
                var matchedTrackId: Int? = null
                var matchedTrackUri: String? = null

                if (inZip) {
                    matchStatus = TrackMatchStatus.INCLUDED_IN_ZIP
                } else if (tr.isOnline && ytId != null && isPlausibleYouTubeVideoId(ytId)) {
                    matchStatus = TrackMatchStatus.ONLINE_STREAM
                    byYoutubeId[ytId]?.let {
                        matchedTrackId = it.id
                        matchedTrackUri = it.uri
                    }
                } else {
                    // Try matching local track
                    val exactUri = byUri[tr.uri]
                    val exactTitleArtist = byTitleArtist[normalizeKey(tr.artist, tr.title)]
                    val titleOnly = if (tr.title.isNotBlank()) byTitle[normalizeText(tr.title)] else null

                    val matched = exactUri ?: exactTitleArtist ?: titleOnly
                    if (matched != null) {
                        matchStatus = if (exactUri != null) TrackMatchStatus.EXACT_URI else TrackMatchStatus.MATCHED_LOCAL_METADATA
                        matchedTrackId = matched.id
                        matchedTrackUri = matched.uri
                    } else {
                        matchStatus = TrackMatchStatus.UNAVAILABLE_LOCAL
                    }
                }

                trackPreviews.add(
                    TrackMatchPreview(
                        track = tr,
                        matchStatus = matchStatus,
                        matchedTrackId = matchedTrackId,
                        matchedTrackUri = matchedTrackUri
                    )
                )
            }

            playlistPreviews.add(
                PlaylistItemPreview(
                    playlist = pl,
                    selected = true,
                    existingPlaylistId = existing?.id,
                    existingPlaylistName = existing?.name,
                    conflictStrategy = if (existing != null) ImportConflictStrategy.CREATE_NEW else ImportConflictStrategy.CREATE_NEW,
                    trackPreviews = trackPreviews
                )
            )
        }

        val fileName = sourceUri?.let { uri ->
            try {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && cursor.moveToFirst()) cursor.getString(nameIdx) else null
                }
            } catch (_: Exception) { null } ?: uri.lastPathSegment ?: "backup.glassplaylist"
        } ?: "backup.glassplaylist"

        PlaylistImportPreview(
            backupPackage = backupPackage,
            playlistPreviews = playlistPreviews,
            isZipPackage = isZip,
            sourceUriString = sourceUri?.toString(),
            fileName = fileName,
            validationErrors = emptyList()
        )
    }

    // ── Execute Import (Atomic Database Transaction & ZIP Extraction) ─────────

    suspend fun executeImport(
        context: Context,
        preview: PlaylistImportPreview,
        audioDao: AudioDao,
        database: AudioDatabase,
        onProgress: ((BackupProgress) -> Unit)? = null
    ): PlaylistImportResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        var playlistsImported = 0
        var playlistsMerged = 0
        var playlistsReplaced = 0
        var tracksRestored = 0
        var tracksExtracted = 0
        var onlineRestored = 0
        var unavailableMarked = 0

        val extractedFilesMap = mutableMapOf<String, AudioTrackEntity>() // relativePath -> inserted TrackEntity

        // 1. If ZIP: extract files to app's music directory
        if (preview.isZipPackage && preview.sourceUriString != null) {
            try {
                val sourceUri = Uri.parse(preview.sourceUriString)
                val destDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.let {
                    File(it, "Restored Tracks").apply { mkdirs() }
                } ?: File(context.filesDir, "restored_tracks").apply { mkdirs() }

                val neededRelativePaths = preview.playlistPreviews
                    .filter { it.selected }
                    .flatMap { it.trackPreviews }
                    .filter { it.matchStatus == TrackMatchStatus.INCLUDED_IN_ZIP }
                    .mapNotNull { it.track.localRelativePath }
                    .toSet()

                if (neededRelativePaths.isNotEmpty()) {
                    var extractedCount = 0
                    context.contentResolver.openInputStream(sourceUri)?.use { rawIn ->
                        ZipInputStream(BufferedInputStream(rawIn)).use { zipIn ->
                            var entry = zipIn.nextEntry
                            val buffer = ByteArray(32768)
                            while (entry != null) {
                                val name = entry.name
                                if (neededRelativePaths.contains(name)) {
                                    val safeFileName = name.substringAfterLast('/')
                                    val targetFile = File(destDir, safeFileName)
                                    FileOutputStream(targetFile).use { out ->
                                        var read: Int
                                        while (zipIn.read(buffer).also { read = it } != -1) {
                                            out.write(buffer, 0, read)
                                        }
                                    }
                                    val fileUri = Uri.fromFile(targetFile).toString()

                                    // Find matching track metadata
                                    val trackMeta = preview.playlistPreviews
                                        .flatMap { it.playlist.tracks }
                                        .firstOrNull { it.localRelativePath == name }

                                    val newTrackEntity = AudioTrackEntity(
                                        uri = fileUri,
                                        title = trackMeta?.title ?: safeFileName.substringBeforeLast('.'),
                                        artist = trackMeta?.artist ?: "Unknown Artist",
                                        album = trackMeta?.album ?: "Restored Backup",
                                        durationMs = trackMeta?.durationMs ?: 0L,
                                        year = trackMeta?.year ?: 0,
                                        folderName = "Restored Tracks",
                                        category = "My Device",
                                        dateAdded = System.currentTimeMillis()
                                    )

                                    val insertedId = audioDao.insertTrack(newTrackEntity)
                                    val finalId = if (insertedId > 0L) insertedId.toInt() else {
                                        audioDao.getTrackByUri(fileUri)?.id ?: 0
                                    }
                                    extractedFilesMap[name] = newTrackEntity.copy(id = finalId)
                                    extractedCount++
                                    tracksExtracted++
                                }
                                zipIn.closeEntry()
                                entry = zipIn.nextEntry
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract ZIP files", e)
                errors.add("Failed to extract audio files from ZIP: ${e.message}")
            }
        }

        // 2. Perform Database Restoration within a single Room transaction
        try {
            database.withTransaction {
                val librarySnapshot = audioDao.getAllTracksSnapshot()
                val byUri = librarySnapshot.associateBy { it.uri }.toMutableMap()
                val byYoutubeId = librarySnapshot.mapNotNull { tr -> tr.youtubeVideoId()?.let { it to tr } }.toMap().toMutableMap()
                val byTitleArtist = librarySnapshot.groupBy { normalizeKey(it.artist, it.title) }
                    .mapValues { (_, list) -> list.first() }.toMutableMap()
                val byTitle = librarySnapshot.groupBy { normalizeText(it.title) }
                    .mapValues { (_, list) -> list.first() }.toMutableMap()

                val totalPlaylists = preview.playlistPreviews.count { it.selected }
                var currentPlIndex = 0

                for (plPreview in preview.playlistPreviews) {
                    if (!plPreview.selected) continue
                    currentPlIndex++
                    onProgress?.invoke(
                        BackupProgress(
                            current = currentPlIndex,
                            total = totalPlaylists,
                            currentItemName = plPreview.playlist.name,
                            message = "Restoring playlist $currentPlIndex of $totalPlaylists: ${plPreview.playlist.name}..."
                        )
                    )

                    val origName = plPreview.playlist.name.trim().ifBlank { "Restored Playlist" }
                    val conflictId = plPreview.existingPlaylistId
                    val strategy = plPreview.conflictStrategy

                    val targetPlaylistId: Int

                    if (conflictId != null && conflictId > 0) {
                        when (strategy) {
                            ImportConflictStrategy.MERGE -> {
                                targetPlaylistId = conflictId
                                playlistsMerged++
                            }
                            ImportConflictStrategy.REPLACE -> {
                                targetPlaylistId = conflictId
                                audioDao.deleteCrossRefsForPlaylist(conflictId)
                                playlistsReplaced++
                            }
                            ImportConflictStrategy.CREATE_NEW -> {
                                var newName = "$origName (Imported)"
                                val existingNames = audioDao.getAllPlaylistsSnapshot().map { normalizeText(it.name) }.toSet()
                                var counter = 1
                                while (existingNames.contains(normalizeText(newName))) {
                                    counter++
                                    newName = "$origName (Imported $counter)"
                                }
                                targetPlaylistId = audioDao.insertPlaylist(PlaylistEntity(name = newName)).toInt()
                                playlistsImported++
                            }
                        }
                    } else {
                        targetPlaylistId = audioDao.insertPlaylist(PlaylistEntity(name = origName)).toInt()
                        playlistsImported++
                    }

                    if (targetPlaylistId <= 0) {
                        errors.add("Could not create playlist '$origName'")
                        continue
                    }

                    // Insert tracks and track cross refs
                    for (trackPreview in plPreview.trackPreviews) {
                        val track = trackPreview.track
                        val ytId = track.youtubeVideoId

                        var resolvedTrackId: Int? = null

                        // Check if extracted from ZIP
                        if (track.localRelativePath != null && extractedFilesMap.containsKey(track.localRelativePath)) {
                            resolvedTrackId = extractedFilesMap[track.localRelativePath]?.id
                        }

                        // Check if online track
                        if (resolvedTrackId == null && track.isOnline && ytId != null && isPlausibleYouTubeVideoId(ytId)) {
                            val existingYt = byYoutubeId[ytId] ?: audioDao.getTrackByUri(youtubeTrackUri(ytId))
                            if (existingYt != null) {
                                resolvedTrackId = existingYt.id
                            } else {
                                val newYtTrack = AudioTrackEntity(
                                    uri = youtubeTrackUri(ytId),
                                    title = track.title.ifBlank { "YouTube Track" },
                                    artist = track.artist.ifBlank { "YouTube" },
                                    durationMs = track.durationMs,
                                    category = "$YOUTUBE_CATEGORY_PREFIX$ytId",
                                    folderName = FOLDER_YOUTUBE,
                                    album = track.album.ifBlank { "YouTube Music" },
                                    albumArtUri = track.thumbnailUrl ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg",
                                    dateAdded = System.currentTimeMillis()
                                ).normalizedForPersistence()

                                if (newYtTrack != null) {
                                    val newId = audioDao.insertTrack(newYtTrack).toInt()
                                    if (newId > 0) {
                                        resolvedTrackId = newId
                                        byYoutubeId[ytId] = newYtTrack.copy(id = newId)
                                        onlineRestored++
                                    }
                                }
                            }
                        }

                        // Check local matches
                        if (resolvedTrackId == null && !track.isOnline) {
                            val exactUri = byUri[track.uri]
                            val byTA = byTitleArtist[normalizeKey(track.artist, track.title)]
                            val byT = if (track.title.isNotBlank()) byTitle[normalizeText(track.title)] else null
                            val matched = exactUri ?: byTA ?: byT
                            if (matched != null) {
                                resolvedTrackId = matched.id
                            } else {
                                // Mark unavailable: do not discard song! Preserve in database
                                val offlineUri = "$UNAVAILABLE_URI_PREFIX${UUID.randomUUID()}"
                                val unavailTrack = AudioTrackEntity(
                                    uri = offlineUri,
                                    title = track.title.ifBlank { "Unavailable Song" },
                                    artist = track.artist.ifBlank { "Unknown Artist" },
                                    album = track.album,
                                    durationMs = track.durationMs,
                                    year = track.year,
                                    category = FOLDER_UNAVAILABLE,
                                    folderName = FOLDER_UNAVAILABLE,
                                    rating = track.rating,
                                    mood = track.mood,
                                    lyrics = track.lyrics,
                                    lrcLyrics = track.lrcLyrics,
                                    bpm = track.bpm,
                                    replayGainDb = track.replayGainDb,
                                    isFavorite = track.isFavorite,
                                    dateAdded = System.currentTimeMillis()
                                )
                                val insId = audioDao.insertTrack(unavailTrack).toInt()
                                if (insId > 0) {
                                    resolvedTrackId = insId
                                    unavailableMarked++
                                }
                            }
                        }

                        if (resolvedTrackId != null && resolvedTrackId > 0) {
                            if (!audioDao.isTrackInPlaylist(targetPlaylistId, resolvedTrackId)) {
                                val nextPos = audioDao.getMaxPlaylistPosition(targetPlaylistId) + 1
                                audioDao.insertPlaylistTrackCrossRef(
                                    PlaylistTrackCrossRefEntity(
                                        playlistId = targetPlaylistId,
                                        trackId = resolvedTrackId,
                                        position = nextPos
                                    )
                                )
                                tracksRestored++
                            }
                        }
                    }
                }

                // Restore per-track metadata (ratings, lyrics, mood, bpm, replayGain)
                for (m in preview.backupPackage.trackMeta) {
                    try {
                        val ytId = m.youtubeVideoId
                        val track = if (ytId != null) {
                            byYoutubeId[ytId] ?: audioDao.getTrackByUri(youtubeTrackUri(ytId))
                        } else {
                            byUri[m.uri] ?: byTitleArtist[normalizeKey(m.artist, m.title)] ?: byTitle[normalizeText(m.title)]
                        }
                        if (track != null) {
                            if (m.favorite) audioDao.toggleFavorite(track.id, true)
                            if (m.rating > 0) audioDao.updateRating(track.id, m.rating)
                            if (m.mood.isNotBlank()) audioDao.updateMood(track.id, m.mood)
                            if (m.lyrics != null) audioDao.updateLyrics(track.id, m.lyrics)
                            if (m.lrcLyrics != null) audioDao.updateLrcLyrics(track.id, m.lrcLyrics)
                            if (m.bpm > 0f) audioDao.updateBpm(track.id, m.bpm)
                            if (m.replayGainDb != 0f) audioDao.updateReplayGain(track.id, m.replayGainDb)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error patching track meta: ${m.title}", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Transaction failed during import", e)
            errors.add("Database transaction error: ${e.message}")
            return@withContext PlaylistImportResult(
                success = false,
                errors = errors
            )
        }

        PlaylistImportResult(
            success = errors.isEmpty() || playlistsImported > 0 || playlistsMerged > 0 || playlistsReplaced > 0,
            playlistsImported = playlistsImported,
            playlistsMerged = playlistsMerged,
            playlistsReplaced = playlistsReplaced,
            tracksRestored = tracksRestored,
            tracksExtractedFromZip = tracksExtracted,
            onlineTracksRestored = onlineRestored,
            unavailableTracksMarked = unavailableMarked,
            errors = errors
        )
    }

    // ── Backward-compatible exportBackup string helper ────────────────────────

    suspend fun exportBackup(
        playlists: List<PlaylistEntity>,
        getTracksInPlaylist: suspend (Int) -> List<AudioTrackEntity>,
        allTracks: List<AudioTrackEntity>
    ): String = withContext(Dispatchers.Default) {
        val root = JSONObject()
        root.put("app", "GlassPlayer")
        root.put("version", CURRENT_SCHEMA_VERSION)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("type", "backup")

        val playlistsArr = JSONArray()
        for (pl in playlists) {
            val plObj = JSONObject()
            plObj.put("id", pl.id)
            plObj.put("name", pl.name)
            plObj.put("createdAt", pl.createdAt)

            val tracks = getTracksInPlaylist(pl.id)
            val tracksArr = JSONArray()
            tracks.forEachIndexed { idx, track ->
                val trackObj = JSONObject()
                trackObj.put("position", idx)
                trackObj.put("uri", track.uri)
                trackObj.put("title", track.title)
                trackObj.put("artist", track.artist)
                trackObj.put("album", track.album)
                trackObj.put("durationMs", track.durationMs)
                trackObj.put("year", track.year)
                val isOnline = track.isYouTubeTrack()
                trackObj.put("isOnline", isOnline)
                track.youtubeVideoId()?.let { ytId ->
                    trackObj.put("youtubeVideoId", ytId)
                    trackObj.put("onlineUrl", "https://www.youtube.com/watch?v=$ytId")
                    trackObj.put("thumbnailUrl", track.albumArtUri ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg")
                }
                trackObj.put("isAvailable", !track.isUnavailable())
                if (track.rating > 0) trackObj.put("rating", track.rating)
                if (track.mood.isNotBlank()) trackObj.put("mood", track.mood)
                if (track.lyrics != null) trackObj.put("lyrics", track.lyrics)
                if (track.lrcLyrics != null) trackObj.put("lrcLyrics", track.lrcLyrics)
                if (track.bpm > 0f) trackObj.put("bpm", track.bpm.toDouble())
                if (track.replayGainDb != 0f) trackObj.put("replayGainDb", track.replayGainDb.toDouble())
                if (track.isFavorite) trackObj.put("isFavorite", true)

                tracksArr.put(trackObj)
            }
            plObj.put("tracks", tracksArr)
            playlistsArr.put(plObj)
        }
        root.put("playlists", playlistsArr)

        val metaArr = JSONArray()
        for (track in allTracks) {
            if (track.uri == AudioRepository.SYNTH_URI) continue
            val anyNonDefault = track.isFavorite || track.rating > 0 || track.mood.isNotEmpty() ||
                track.lyrics != null || track.lrcLyrics != null || track.bpm > 0f ||
                track.replayGainDb != 0f
            if (!anyNonDefault) continue
            val obj = JSONObject()
            obj.put("uri", track.uri)
            obj.put("title", track.title)
            obj.put("artist", track.artist)
            track.youtubeVideoId()?.let { obj.put("youtubeVideoId", it) }
            if (track.isFavorite) obj.put("favorite", true)
            if (track.rating > 0) obj.put("rating", track.rating)
            if (track.mood.isNotEmpty()) obj.put("mood", track.mood)
            if (track.lyrics != null) obj.put("lyrics", track.lyrics)
            if (track.lrcLyrics != null) obj.put("lrcLyrics", track.lrcLyrics)
            if (track.bpm > 0f) obj.put("bpm", track.bpm)
            if (track.replayGainDb != 0f) obj.put("replayGainDb", track.replayGainDb)
            metaArr.put(obj)
        }
        root.put("trackMeta", metaArr)

        root.toString(2)
    }

    // ── Legacy ImportResult Overload ──────────────────────────────────────────

    data class ImportResult(
        val playlistsRestored: Int,
        val tracksPatched: Int,
        val localTracksMatched: Int,
        val onlineTracksMatched: Int,
        val unmatchedTracks: List<String>,
        val errors: List<String>
    )

    suspend fun importBackup(
        json: String,
        allTracks: List<AudioTrackEntity>,
        findOrInsertTrack: suspend (AudioTrackEntity) -> Int,
        createPlaylist: suspend (String) -> Int,
        addTrackToPlaylist: suspend (Int, Int) -> Unit,
        updateFavorite: suspend (Int, Boolean) -> Unit,
        updateRating: suspend (Int, Int) -> Unit,
        updateMood: suspend (Int, String) -> Unit,
        updateLyrics: suspend (Int, String?) -> Unit,
        updateLrcLyrics: suspend (Int, String?) -> Unit,
        updateBpm: suspend (Int, Float) -> Unit,
        updateReplayGain: suspend (Int, Float) -> Unit
    ): ImportResult = withContext(Dispatchers.Default) {
        val errors = mutableListOf<String>()
        val unmatched = mutableListOf<String>()
        var playlistsRestored = 0
        var tracksPatched = 0
        var localMatched = 0
        var onlineMatched = 0

        try {
            val pkg = deserializePackage(json)
            val byUri = allTracks.associateBy { it.uri }
            val byYoutubeId = allTracks.mapNotNull { it.youtubeVideoId()?.let { id -> id to it } }.toMap()
            val byTitleArtist = allTracks.groupBy { normalizeKey(it.artist, it.title) }
                .mapValues { (_, list) -> list.first() }
            val byTitle = allTracks.groupBy { normalizeText(it.title) }
                .mapValues { (_, list) -> list.first() }

            for (pl in pkg.playlists) {
                try {
                    val playlistId = createPlaylist(pl.name)
                    for (track in pl.tracks) {
                        val ytId = track.youtubeVideoId
                        val resolved = when {
                            byUri.containsKey(track.uri) -> {
                                localMatched++
                                byUri[track.uri]
                            }
                            ytId != null && isPlausibleYouTubeVideoId(ytId) -> {
                                val existing = byYoutubeId[ytId]
                                if (existing != null) {
                                    onlineMatched++
                                    existing
                                } else {
                                    val newTrack = AudioTrackEntity(
                                        uri = youtubeTrackUri(ytId),
                                        title = track.title.ifBlank { "YouTube Track" },
                                        artist = track.artist.ifBlank { "YouTube" },
                                        durationMs = track.durationMs,
                                        category = "$YOUTUBE_CATEGORY_PREFIX$ytId",
                                        folderName = FOLDER_YOUTUBE,
                                        album = track.album.ifBlank { "YouTube Music" },
                                        albumArtUri = track.thumbnailUrl ?: "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
                                    ).normalizedForPersistence()
                                    if (newTrack != null) {
                                        val newId = findOrInsertTrack(newTrack)
                                        onlineMatched++
                                        newTrack.copy(id = newId)
                                    } else null
                                }
                            }
                            track.title.isNotBlank() && track.artist.isNotBlank() &&
                                byTitleArtist.containsKey(normalizeKey(track.artist, track.title)) -> {
                                localMatched++
                                byTitleArtist[normalizeKey(track.artist, track.title)]
                            }
                            track.title.isNotBlank() && byTitle.containsKey(normalizeText(track.title)) -> {
                                localMatched++
                                byTitle[normalizeText(track.title)]
                            }
                            else -> null
                        }

                        if (resolved != null && resolved.id > 0) {
                            addTrackToPlaylist(playlistId, resolved.id)
                        } else {
                            unmatched.add("${track.artist} - ${track.title}")
                        }
                    }
                    playlistsRestored++
                } catch (e: Exception) {
                    errors.add("Playlist '${pl.name}': ${e.message}")
                }
            }

            for (m in pkg.trackMeta) {
                try {
                    val ytId = m.youtubeVideoId
                    val resolved = if (ytId != null) byYoutubeId[ytId] else byUri[m.uri] ?: byTitleArtist[normalizeKey(m.artist, m.title)]
                    if (resolved != null) {
                        if (m.favorite) updateFavorite(resolved.id, true)
                        if (m.rating > 0) updateRating(resolved.id, m.rating)
                        if (m.mood.isNotBlank()) updateMood(resolved.id, m.mood)
                        if (m.lyrics != null) updateLyrics(resolved.id, m.lyrics)
                        if (m.lrcLyrics != null) updateLrcLyrics(resolved.id, m.lrcLyrics)
                        if (m.bpm > 0f) updateBpm(resolved.id, m.bpm)
                        if (m.replayGainDb != 0f) updateReplayGain(resolved.id, m.replayGainDb)
                        tracksPatched++
                    }
                } catch (e: Exception) {
                    errors.add("Track meta '${m.title}': ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Legacy import failed", e)
            errors.add("Root parse error: ${e.message}")
        }

        ImportResult(playlistsRestored, tracksPatched, localMatched, onlineMatched, unmatched, errors)
    }

    // ── M3U / M3U8 import ─────────────────────────────────────────────────────

    data class M3uImportResult(
        val playlistName: String,
        val matchedTracks: List<AudioTrackEntity>,
        val unmatchedUris: List<String>
    )

    private fun parseExtInf(extInf: String?): Pair<String, String>? {
        if (extInf.isNullOrBlank()) return null
        val comma = extInf.indexOf(',')
        if (comma < 0 || comma == extInf.lastIndex) return null
        val label = extInf.substring(comma + 1).trim()
        val separator = label.indexOf(" - ")
        if (separator <= 0 || separator >= label.lastIndex) return null
        return label.substring(0, separator).trim() to label.substring(separator + 3).trim()
    }

    fun importM3u(
        m3uText: String,
        allTracks: List<AudioTrackEntity>,
        suggestedName: String = "Imported"
    ): M3uImportResult {
        val lines = m3uText.lines()
        var playlistName = suggestedName
        val uriLines = mutableListOf<String>()
        val pendingExtInfByIndex = mutableListOf<String?>()
        var pendingExtInf: String? = null

        for (raw in lines) {
            val line = raw.trim()
            if (line.isBlank() || line == "#EXTM3U") continue
            if (line.startsWith("# GlassPlayer Playlist:")) {
                playlistName = line.removePrefix("# GlassPlayer Playlist:").trim()
                continue
            }
            if (line.startsWith("#EXTINF:")) {
                pendingExtInf = line
                continue
            }
            if (!line.startsWith("#")) {
                uriLines.add(line)
                pendingExtInfByIndex.add(pendingExtInf)
                pendingExtInf = null
            }
        }

        val byUri = allTracks.associateBy { it.uri }
        val byYoutubeId = allTracks.mapNotNull { it.youtubeVideoId()?.let { id -> id to it } }.toMap()
        val byTitleArtist = allTracks.groupBy { normalizeKey(it.artist, it.title) }
            .mapValues { (_, tracks) -> tracks.first() }
        val byTitle = allTracks.groupBy { normalizeText(it.title) }
            .mapValues { (_, tracks) -> tracks.first() }

        val matched = mutableListOf<AudioTrackEntity>()
        val unmatched = mutableListOf<String>()

        for ((index, u) in uriLines.withIndex()) {
            val extInf = pendingExtInfByIndex.getOrNull(index)
            val parsedArtistTitle = parseExtInf(extInf)

            val tempTrack = AudioTrackEntity(
                uri = u,
                title = parsedArtistTitle?.second ?: "",
                artist = parsedArtistTitle?.first ?: "",
                durationMs = 0L
            )
            val ytId = tempTrack.youtubeVideoId()

            val track = byUri[u]
                ?: (ytId?.let { byYoutubeId[it] })
                ?: parsedArtistTitle?.let { (artist, title) -> byTitleArtist[normalizeKey(artist, title)] }
                ?: parsedArtistTitle?.second?.let { byTitle[normalizeText(it)] }
                ?: byTitle[normalizeText(u.substringAfterLast('/').substringBefore('.'))]

            if (track != null) {
                matched.add(track)
            } else {
                unmatched.add(u)
            }
        }

        return M3uImportResult(playlistName, matched, unmatched)
    }

    // ── XSPF (.xspf) Export ───────────────────────────────────────────────────

    fun exportXspf(
        playlists: List<PlaylistEntity>,
        getTracksInPlaylist: suspend (Int) -> List<AudioTrackEntity>,
        allTracks: List<AudioTrackEntity>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<playlist version=\"1\" xmlns=\"http://xspf.org/ns/0/\">")
        sb.appendLine("    <title>GlassPlayer Playlists Export</title>")
        sb.appendLine("    <trackList>")

        val exportedUris = mutableSetOf<String>()
        for (track in allTracks) {
            if (track.uri == AudioRepository.SYNTH_URI) continue
            if (!exportedUris.add(track.uri)) continue

            sb.appendLine("        <track>")
            sb.appendLine("            <location>${escapeXml(track.uri)}</location>")
            sb.appendLine("            <title>${escapeXml(track.title)}</title>")
            sb.appendLine("            <creator>${escapeXml(track.artist)}</creator>")
            sb.appendLine("            <album>${escapeXml(track.album)}</album>")
            sb.appendLine("            <duration>${track.durationMs}</duration>")
            track.youtubeVideoId()?.let { ytId ->
                sb.appendLine("            <extension application=\"http://glassplayer.example\">")
                sb.appendLine("                <youtubeId>$ytId</youtubeId>")
                sb.appendLine("            </extension>")
            }
            sb.appendLine("        </track>")
        }

        sb.appendLine("    </trackList>")
        sb.appendLine("</playlist>")
        return sb.toString()
    }

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
