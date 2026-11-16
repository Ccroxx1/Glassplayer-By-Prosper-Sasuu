package com.example

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports GlassPlayer data (playlists, ratings, moods, lyrics edits)
 * as a self-contained JSON file that can be saved / shared / restored.
 *
 * JSON structure:
 * {
 *   "version": 1,
 *   "exportedAt": <timestamp ms>,
 *   "playlists": [
 *     { "name": "...", "tracks": [ <uri>, ... ] }
 *   ],
 *   "trackMeta": [
 *     { "uri": "...", "rating": 0, "mood": "", "lyrics": null, "lrcLyrics": null,
 *       "userTitle": null, "userArtist": null, "userAlbum": null }
 *   ]
 * }
 */
object BackupRestoreManager {

    private const val TAG = "BackupRestoreManager"

    // ── Export ────────────────────────────────────────────────────────────────

    /**
     * Builds the full backup JSON string.
     * Must be called from a coroutine / suspend context (IO dispatcher).
     */
    suspend fun exportBackup(
        playlists: List<PlaylistEntity>,
        getTracksInPlaylist: suspend (Int) -> List<AudioTrackEntity>,
        allTracks: List<AudioTrackEntity>
    ): String = withContext(Dispatchers.Default) {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        // ── Playlists ────────────────────────────────────────────────────────
        val playlistsArr = JSONArray()
        for (pl in playlists) {
            val plObj = JSONObject()
            plObj.put("name", pl.name)
            val urisArr = JSONArray()
            getTracksInPlaylist(pl.id).forEach { urisArr.put(it.uri) }
            plObj.put("tracks", urisArr)
            playlistsArr.put(plObj)
        }
        root.put("playlists", playlistsArr)

        // ── Per-track metadata ────────────────────────────────────────────────
        val metaArr = JSONArray()
        for (track in allTracks) {
            if (track.uri == AudioRepository.SYNTH_URI) continue
            val anyNonDefault = track.isFavorite || track.rating > 0 || track.mood.isNotEmpty() ||
                track.lyrics != null || track.lrcLyrics != null || track.bpm > 0f ||
                track.replayGainDb != 0f
            if (!anyNonDefault) continue          // skip default-state tracks to keep file small
            val obj = JSONObject()
            obj.put("uri", track.uri)
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

    // ── Import ────────────────────────────────────────────────────────────────

    data class ImportResult(
        val playlistsRestored: Int,
        val tracksPatched: Int,
        val errors: List<String>
    )

    /**
     * Parses the JSON and restores playlists + per-track metadata.
     * Tracks that no longer exist in the library are silently skipped.
     */
    suspend fun importBackup(
        json: String,
        allTracks: List<AudioTrackEntity>,
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
        var playlistsRestored = 0
        var tracksPatched = 0

        try {
            val root = JSONObject(json)
            val version = root.optInt("version", 1)
            if (version != 1) {
                return@withContext ImportResult(0, 0, listOf("Unsupported backup version: $version"))
            }

            val byUri = allTracks.associateBy { it.uri }

            // ── Playlists ────────────────────────────────────────────────────
            val playlistsArr = root.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until playlistsArr.length()) {
                try {
                    val plObj = playlistsArr.getJSONObject(i)
                    val name = plObj.optString("name").ifBlank { "Imported Playlist" }
                    val playlistId = createPlaylist(name)
                    val urisArr = plObj.optJSONArray("tracks") ?: JSONArray()
                    for (j in 0 until urisArr.length()) {
                        val uri = urisArr.getString(j)
                        val track = byUri[uri]
                        if (track != null) {
                            addTrackToPlaylist(playlistId, track.id)
                        }
                    }
                    playlistsRestored++
                } catch (e: Exception) {
                    errors.add("Playlist #$i: ${e.message}")
                }
            }

            // ── Per-track metadata ────────────────────────────────────────────
            val metaArr = root.optJSONArray("trackMeta") ?: JSONArray()
            for (i in 0 until metaArr.length()) {
                try {
                    val obj = metaArr.getJSONObject(i)
                    val uri = obj.optString("uri")
                    val track = byUri[uri] ?: continue
                    if (obj.has("favorite")) updateFavorite(track.id, obj.optBoolean("favorite"))
                    if (obj.has("rating")) updateRating(track.id, obj.getInt("rating"))
                    if (obj.has("mood")) updateMood(track.id, obj.getString("mood"))
                    if (obj.has("lyrics")) updateLyrics(track.id, obj.optString("lyrics"))
                    if (obj.has("lrcLyrics")) updateLrcLyrics(track.id, obj.optString("lrcLyrics"))
                    if (obj.has("bpm")) updateBpm(track.id, obj.optDouble("bpm").toFloat())
                    if (obj.has("replayGainDb")) updateReplayGain(track.id, obj.optDouble("replayGainDb").toFloat())
                    tracksPatched++
                } catch (e: Exception) {
                    errors.add("Track meta #$i: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed", e)
            errors.add("Root parse error: ${e.message}")
        }

        ImportResult(playlistsRestored, tracksPatched, errors)
    }

    // ── M3U / M3U8 import ─────────────────────────────────────────────────────

    data class M3uImportResult(
        val playlistName: String,
        val matchedTracks: List<AudioTrackEntity>,
        val unmatchedUris: List<String>
    )

    /**
     * Parses an M3U/M3U8 text and matches its entries against the library.
     * Matching is attempted by URI equality first, then by filename stem.
     */
    private fun normalizeText(value: String): String =
        value.trim().lowercase().replace(Regex("\\s+"), " ")

    private fun normalizeKey(artist: String, title: String): String =
        "${normalizeText(artist)}|${normalizeText(title)}"

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
        val byTitleArtist = allTracks
            .groupBy { normalizeKey(it.artist, it.title) }
            .mapValues { (_, tracks) -> tracks.first() }
        val byTitle = allTracks
            .groupBy { normalizeText(it.title) }
            .mapValues { (_, tracks) -> tracks.first() }

        val matched = mutableListOf<AudioTrackEntity>()
        val unmatched = mutableListOf<String>()

        for ((index, u) in uriLines.withIndex()) {
            val extInf = pendingExtInfByIndex.getOrNull(index)
            val parsedArtistTitle = parseExtInf(extInf)
            val track = byUri[u]
                ?: parsedArtistTitle?.let { (artist, title) -> byTitleArtist[normalizeKey(artist, title)] }
                ?: parsedArtistTitle?.second?.let { byTitle[normalizeText(it)] }
            if (track != null) matched.add(track) else unmatched.add(u)
        }

        return M3uImportResult(playlistName, matched, unmatched)
    }
}
