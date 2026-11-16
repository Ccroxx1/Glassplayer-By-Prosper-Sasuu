package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

data class OnlineTrack(
    val id: String, // YouTube Video ID
    val streamUrl: String = "",
    val title: String,
    val artist: String,
    val durationText: String = "3:30",
    val durationMs: Long = 210_000L,
    val thumbnail: String,
    val views: String = "YouTube",
    val mood: String = "Trending",
    val webLink: String = "",
    val source: String = "YouTube"
) {
    /** Converts OnlineTrack to AudioTrackEntity with a stable unique URI (never blank). */
    fun toAudioTrackEntity(indexHint: Int = 0): AudioTrackEntity {
        val trackUri = youtubeTrackUri(id)
        val folder = FOLDER_YOUTUBE
        val categoryTag = "$YOUTUBE_CATEGORY_PREFIX$id"
        val albumTag = "YouTube Music"

        val baseHash = abs(id.hashCode().coerceAtLeast(1))
        val tempId = -(baseHash + (indexHint * 10007))

        val cleanVid = id.substringBefore("&").substringBefore("?").trim()
        var art = when {
            thumbnail.isNotBlank() && !thumbnail.contains("proxy.piped") -> thumbnail
            cleanVid.isNotBlank() -> "https://i.ytimg.com/vi/$cleanVid/hq720.jpg"
            else -> "https://i.ytimg.com/vi/$id/hq720.jpg"
        }
        if (art.startsWith("//")) {
            art = "https:$art"
        }

        return AudioTrackEntity(
            id = tempId,
            uri = trackUri,
            title = title,
            artist = artist,
            durationMs = durationMs,
            album = albumTag,
            albumArtUri = art,
            folderName = folder,
            category = categoryTag,
            dateAdded = System.currentTimeMillis(),
            mood = ""
        )
    }
}

data class OnlinePlaylistResult(
    val playlistId: String,
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val tracks: List<OnlineTrack>
)

object OnlineMusicService {

    private const val TAG = "OnlineMusicService"
    private const val TIMEOUT_MS = 10000

    // RapidAPI YTStream Credentials
    private const val RAPID_API_KEY = "db688ed599msh91af8b1ceff2dcep14dab6jsn84a17c45d461"
    private const val RAPID_API_HOST = "ytstream-download-youtube-videos.p.rapidapi.com"

    val GENRE_CATEGORIES = listOf(
        "Trending", "Pop", "Hip-Hop", "Rock", "Electronic",
        "R&B", "Afrobeats", "Latin", "Chill", "Workout"
    )

    private val CATEGORY_QUERIES = mapOf(
        "Trending" to "Trending music 2024",
        "Pop" to "Pop hits 2024",
        "Hip-Hop" to "Hip hop rap hits",
        "Rock" to "Rock classics hits",
        "Electronic" to "EDM electronic dance hits",
        "R&B" to "RnB soul hits",
        "Afrobeats" to "Afrobeats hits",
        "Latin" to "Latin reggaeton hits",
        "Chill" to "Chill lofi beats",
        "Workout" to "Gym workout motivation music"
    )

    // In-memory cache for resolved direct stream URLs
    private val streamCache = ConcurrentHashMap<String, String>()

    /** Fetches single video metadata for a YouTube VIDEO_ID using official YouTube oEmbed & Innertube APIs */
    suspend fun fetchTrackByVideoId(videoId: String): OnlineTrack? =
        withContext(Dispatchers.IO) {
            val cleanId = videoId.substringBefore("&").substringBefore("?").substringBefore("#").trim()
            if (cleanId.isBlank()) return@withContext null

            // 1. Primary: YouTube Official oEmbed & Innertube Player API (100% reliable, zero proxy issues)
            val officialTrack = fetchTrackFromOfficialYouTube(cleanId)
            if (officialTrack != null) {
                return@withContext officialTrack
            }

            // 2. Secondary Fallback: Piped Stream API
            val pipedTrack = fetchTrackFromPipedStream(cleanId)
            if (pipedTrack != null) {
                return@withContext pipedTrack
            }

            null
        }

    private fun fetchTrackFromOfficialYouTube(videoId: String): OnlineTrack? {
        try {
            var title = ""
            var author = "YouTube Artist"
            var thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            // A) YouTube oEmbed API
            try {
                val oembedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$videoId&format=json"
                val conn = (URL(oembedUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    title = json.optString("title", "").trim()
                    author = json.optString("author_name", "YouTube Artist").trim()
                    thumbnail = json.optString("thumbnail_url", thumbnail)
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "oEmbed error for $videoId", e)
            }

            // B) Innertube Player API for duration & metadata verification
            var durationSec = 210L
            try {
                val playerUrl = "https://www.youtube.com/youtubei/v1/player?key=AIzaSyAO_C12yB2jCjC8-29"
                val bodyJson = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB")
                            put("clientVersion", "2.20240101.00.00")
                        })
                    })
                    put("videoId", videoId)
                }

                val pConn = (URL(playerUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 6000
                    readTimeout = 6000
                    doOutput = true
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Content-Type", "application/json")
                }

                pConn.outputStream.use { os ->
                    os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
                }

                if (pConn.responseCode == 200) {
                    val pBody = pConn.inputStream.bufferedReader().use { it.readText() }
                    pConn.disconnect()
                    val pJson = JSONObject(pBody)
                    val details = pJson.optJSONObject("videoDetails")
                    if (details != null) {
                        if (title.isBlank()) {
                            title = details.optString("title", "").trim()
                        }
                        if (author == "YouTube Artist" || author.isBlank()) {
                            author = details.optString("author", "YouTube Artist").trim()
                        }
                        val lenSec = details.optLong("lengthSeconds", 0L)
                        if (lenSec > 0L) {
                            durationSec = lenSec
                        }
                    }
                } else {
                    pConn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Innertube player detail error for $videoId", e)
            }

            if (title.isNotBlank()) {
                val mins = durationSec / 60
                val secs = durationSec % 60
                val durStr = String.format("%d:%02d", mins, secs)
                return OnlineTrack(
                    id = videoId,
                    title = title,
                    artist = author.removeSuffix(" - Topic").trim(),
                    durationText = durStr,
                    durationMs = durationSec * 1000L,
                    thumbnail = thumbnail.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
                    views = "YouTube",
                    mood = "YouTube Stream",
                    webLink = "https://www.youtube.com/watch?v=$videoId",
                    source = "YouTube"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error resolving official YouTube track $videoId", e)
        }
        return null
    }

    private fun fetchTrackFromPipedStream(videoId: String): OnlineTrack? {
        val streamEndpoints = listOf(
            "https://api.piped.private.coffee/streams/$videoId",
            "https://pipedapi.tokhmi.xyz/streams/$videoId"
        )

        for (endpoint in streamEndpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    val title = json.optString("title", "").trim()
                    val uploader = json.optString("uploader", "YouTube Artist").trim()
                    val thumbnail = json.optString("thumbnailUrl", "https://i.ytimg.com/vi/$videoId/hqdefault.jpg")
                    val durationSec = json.optLong("duration", 210L).coerceAtLeast(1L)
                    val durationMs = durationSec * 1000L
                    val mins = durationSec / 60
                    val secs = durationSec % 60
                    val durStr = String.format("%d:%02d", mins, secs)

                    if (title.isNotBlank()) {
                        return OnlineTrack(
                            id = videoId,
                            title = title,
                            artist = uploader,
                            durationText = durStr,
                            durationMs = durationMs,
                            thumbnail = thumbnail.ifBlank { "https://i.ytimg.com/vi/$videoId/hqdefault.jpg" },
                            views = "YouTube",
                            mood = "YouTube Stream",
                            webLink = "https://www.youtube.com/watch?v=$videoId",
                            source = "YouTube"
                        )
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching stream metadata from $endpoint: ${e.message}")
            }
        }
        return null
    }

    /** Fetches playlist metadata & items for a YouTube PLAYLIST_ID */
    suspend fun fetchPlaylistById(playlistId: String): OnlinePlaylistResult? =
        withContext(Dispatchers.IO) {
            val cleanPlId = playlistId.trim()
            if (cleanPlId.isBlank()) return@withContext null

            // 1. Primary: Direct YouTube Innertube API
            val innertubeResult = fetchPlaylistFromInnertube(cleanPlId)
            if (innertubeResult != null && innertubeResult.tracks.isNotEmpty()) {
                Log.d(TAG, "Successfully loaded ${innertubeResult.tracks.size} tracks from Innertube for playlist $cleanPlId")
                return@withContext innertubeResult
            }

            // 2. Secondary Fallback: Piped Proxy Endpoints
            val pipedResult = fetchPlaylistFromPiped(cleanPlId)
            if (pipedResult != null && pipedResult.tracks.isNotEmpty()) {
                Log.d(TAG, "Successfully loaded ${pipedResult.tracks.size} tracks from Piped for playlist $cleanPlId")
                return@withContext pipedResult
            }

            null
        }

    private fun fetchPlaylistFromInnertube(playlistId: String): OnlinePlaylistResult? {
        try {
            val browseId = if (playlistId.startsWith("VL")) playlistId else "VL$playlistId"
            val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_C12yB2jCjC8-29"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240101.00.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("browseId", browseId)
            }

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Content-Type", "application/json")
            }

            conn.outputStream.use { os ->
                os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(responseBody)
                return parseInnertubePlaylistJson(playlistId, json)
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching playlist $playlistId from Innertube", e)
        }
        return null
    }

    private fun parseInnertubePlaylistJson(playlistId: String, json: JSONObject): OnlinePlaylistResult? {
        val metadata = json.optJSONObject("metadata")?.optJSONObject("playlistMetadataRenderer")
        val header = json.optJSONObject("header")
        val title = metadata?.optString("title")
            ?: header?.optJSONObject("musicDetailHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: header?.optJSONObject("playlistHeaderRenderer")?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "YouTube Playlist"

        val uploader = metadata?.optString("author")
            ?: header?.optJSONObject("playlistHeaderRenderer")?.optJSONObject("ownerText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text")
            ?: "YouTube Music"

        val tracks = mutableListOf<OnlineTrack>()
        extractInnertubeVideoItems(json, tracks, title)

        // Iterative Continuation Pagination Loop supporting up to 2,000 tracks
        var continuationToken = findContinuationToken(json)
        val visitedTokens = mutableSetOf<String>()
        var maxPages = 25 // Cap at 25 continuation pages (~2,500 tracks max)

        while (!continuationToken.isNullOrBlank() && tracks.size < 2000 && maxPages > 0) {
            if (visitedTokens.contains(continuationToken)) break
            visitedTokens.add(continuationToken)

            val nextJson = fetchInnertubeContinuationJson(continuationToken) ?: break
            val previousTrackCount = tracks.size

            extractInnertubeVideoItems(nextJson, tracks, title)

            // Break if no new tracks were added to prevent infinite loops
            if (tracks.size == previousTrackCount) break

            continuationToken = findContinuationToken(nextJson)
            maxPages--
        }

        if (tracks.isEmpty()) return null

        val thumb = "https://i.ytimg.com/vi/${tracks.first().id}/hqdefault.jpg"
        return OnlinePlaylistResult(
            playlistId = playlistId,
            title = title.ifBlank { "YouTube Playlist" },
            uploader = uploader.ifBlank { "YouTube Music" },
            thumbnail = thumb,
            tracks = tracks
        )
    }

    /** Recursive helper to traverse JSON and locate valid Innertube continuation tokens at any depth. */
    fun findContinuationToken(node: Any): String? {
        when (node) {
            is JSONObject -> {
                // Check direct continuation structures first
                if (node.has("continuationItemRenderer")) {
                    val cir = node.optJSONObject("continuationItemRenderer")
                    val token = cir?.let { findContinuationToken(it) }
                    if (!token.isNullOrBlank()) return token
                }
                if (node.has("continuationEndpoint")) {
                    val ep = node.optJSONObject("continuationEndpoint")
                    val cmd = ep?.optJSONObject("continuationCommand")
                    val token = cmd?.optString("token", "") ?: ""
                    if (isValidContinuationToken(token)) return token
                }
                if (node.has("continuationCommand")) {
                    val cmd = node.optJSONObject("continuationCommand")
                    val token = cmd?.optString("token", "") ?: ""
                    if (isValidContinuationToken(token)) return token
                }
                if (node.has("nextContinuationData")) {
                    val ncd = node.optJSONObject("nextContinuationData")
                    val token = ncd?.optString("continuation", "") ?: ""
                    if (isValidContinuationToken(token)) return token
                }
                if (node.has("continuationData")) {
                    val cd = node.optJSONObject("continuationData")
                    val token = cd?.optString("continuation", "") ?: ""
                    if (isValidContinuationToken(token)) return token
                }

                // Fallback recursive key traversal
                val keys = node.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = node.opt(key) ?: continue

                    if ((key == "token" || key == "continuation") && child is String) {
                        if (isValidContinuationToken(child) && (node.has("command") || node.has("clickTrackingParams") || key == "continuation")) {
                            return child
                        }
                    }

                    val token = findContinuationToken(child)
                    if (!token.isNullOrBlank()) return token
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val item = node.opt(i) ?: continue
                    val token = findContinuationToken(item)
                    if (!token.isNullOrBlank()) return token
                }
            }
        }
        return null
    }

    private fun isValidContinuationToken(token: String): Boolean {
        return token.isNotBlank() && token.length >= 10 && !token.contains("{") && !token.contains("}") && !token.contains(" ") && !token.contains("[") && !token.contains("]")
    }

    private fun fetchInnertubeContinuationJson(token: String): JSONObject? {
        try {
            val url = "https://www.youtube.com/youtubei/v1/browse?key=AIzaSyAO_C12yB2jCjC8-29"
            val bodyJson = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB")
                        put("clientVersion", "2.20240101.00.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("continuation", token)
            }

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 8000
                readTimeout = 8000
                doOutput = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                setRequestProperty("Content-Type", "application/json")
            }

            conn.outputStream.use { os ->
                os.write(bodyJson.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 200) {
                val responseBody = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                return JSONObject(responseBody)
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching Innertube continuation tracks", e)
        }
        return null
    }

    private fun parseDurationTextToMs(text: String): Long {
        val parts = text.split(":").mapNotNull { it.trim().toLongOrNull() }
        return when (parts.size) {
            1 -> parts[0] * 1000L
            2 -> (parts[0] * 60L + parts[1]) * 1000L
            3 -> (parts[0] * 3600L + parts[1] * 60L + parts[2]) * 1000L
            else -> 210_000L
        }
    }

    private fun extractInnertubeVideoItems(node: Any, list: MutableList<OnlineTrack>, playlistTitle: String) {
        when (node) {
            is JSONObject -> {
                if (node.has("lockupViewModel")) {
                    val lum = node.optJSONObject("lockupViewModel")
                    val contentType = lum?.optString("contentType", "")
                    if (!contentType.isNullOrBlank() && contentType != "LOCKUP_CONTENT_TYPE_VIDEO") {
                        return
                    }

                    val contentId = lum?.optString("contentId", "") ?: ""
                    if (contentId.isBlank() || contentId.length != 11 || contentId.startsWith("PL") || contentId.startsWith("RD")) {
                        return
                    }

                    val meta = lum?.optJSONObject("metadata")?.optJSONObject("lockupMetadataViewModel")
                    val title = meta?.optJSONObject("title")?.optString("content", "") ?: ""
                    val subRows = meta?.optJSONObject("metadata")?.optJSONObject("contentMetadataViewModel")?.optJSONArray("metadataRows")
                    var artist = "YouTube Artist"
                    if (subRows != null && subRows.length() > 0) {
                        val firstRow = subRows.optJSONObject(0)?.optJSONArray("metadataParts")
                        if (firstRow != null && firstRow.length() > 0) {
                            artist = firstRow.optJSONObject(0)?.optJSONObject("text")?.optString("content", artist) ?: artist
                        }
                    }

                    var durationText = "3:30"
                    val overlays = lum?.optJSONObject("contentImage")?.optJSONObject("thumbnailViewModel")?.optJSONArray("overlays")
                    if (overlays != null) {
                        for (i in 0 until overlays.length()) {
                            val badge = overlays.optJSONObject(i)?.optJSONObject("thumbnailBottomOverlayViewModel")?.optJSONArray("badges")?.optJSONObject(0)?.optJSONObject("thumbnailBadgeViewModel")
                            val text = badge?.optString("text", "")
                            if (!text.isNullOrBlank() && (text.contains(":") || text.all { it.isDigit() })) {
                                durationText = text
                                break
                            }
                        }
                    }

                    if (contentId.isNotBlank() && title.isNotBlank()) {
                        if (list.none { it.id == contentId }) {
                            val durationMs = parseDurationTextToMs(durationText)
                            list.add(
                                OnlineTrack(
                                    id = contentId,
                                    title = title,
                                    artist = artist,
                                    durationText = durationText,
                                    durationMs = durationMs,
                                    thumbnail = "https://i.ytimg.com/vi/$contentId/hqdefault.jpg",
                                    views = "YouTube",
                                    mood = playlistTitle,
                                    webLink = "https://www.youtube.com/watch?v=$contentId",
                                    source = "YouTube"
                                )
                            )
                        }
                    }
                    return
                }

                if (node.has("playlistVideoRenderer")) {
                    val pvr = node.optJSONObject("playlistVideoRenderer")
                    val videoId = pvr?.optString("videoId", "") ?: ""
                    val title = pvr?.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "") ?: ""
                    val artist = pvr?.optJSONObject("shortBylineText")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text", "YouTube Artist") ?: "YouTube Artist"
                    val lengthText = pvr?.optJSONObject("lengthText")?.optString("simpleText", "3:30") ?: "3:30"

                    if (videoId.isNotBlank() && title.isNotBlank() && videoId.length == 11) {
                        if (list.none { it.id == videoId }) {
                            val durationMs = parseDurationTextToMs(lengthText)
                            list.add(
                                OnlineTrack(
                                    id = videoId,
                                    title = title,
                                    artist = artist,
                                    durationText = lengthText,
                                    durationMs = durationMs,
                                    thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    views = "YouTube",
                                    mood = playlistTitle,
                                    webLink = "https://www.youtube.com/watch?v=$videoId",
                                    source = "YouTube"
                                )
                            )
                        }
                    }
                    return
                }

                if (node.has("musicResponsiveListItemRenderer")) {
                    val mrlir = node.optJSONObject("musicResponsiveListItemRenderer")
                    var videoId = mrlir?.optJSONObject("playlistItemData")?.optString("videoId", "") ?: ""

                    if (videoId.isBlank()) {
                        val mrlirStr = mrlir?.toString() ?: ""
                        if (mrlirStr.contains("watchEndpoint")) {
                            videoId = mrlirStr.substringAfter("\"videoId\":\"").substringBefore("\"")
                            if (videoId.contains("{") || videoId.length != 11) videoId = ""
                        }
                    }

                    var title = ""
                    var artist = "YouTube Artist"
                    val flexColumns = mrlir?.optJSONArray("flexColumns")
                    if (flexColumns != null && flexColumns.length() > 0) {
                        val col0 = flexColumns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val runs0 = col0?.optJSONObject("text")?.optJSONArray("runs")
                        if (runs0 != null && runs0.length() > 0) {
                            title = runs0.optJSONObject(0)?.optString("text", "") ?: ""
                        }

                        if (flexColumns.length() > 1) {
                            val col1 = flexColumns.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            val runs1 = col1?.optJSONObject("text")?.optJSONArray("runs")
                            if (runs1 != null && runs1.length() > 0) {
                                artist = runs1.optJSONObject(0)?.optString("text", "YouTube Artist") ?: "YouTube Artist"
                            }
                        }
                    }

                    var lengthText = "3:30"
                    val fixedColumns = mrlir?.optJSONArray("fixedColumns")
                    if (fixedColumns != null && fixedColumns.length() > 0) {
                        val fcol0 = fixedColumns.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")
                        val runs = fcol0?.optJSONObject("text")?.optJSONArray("runs")
                        if (runs != null && runs.length() > 0) {
                            val txt = runs.optJSONObject(0)?.optString("text", "") ?: ""
                            if (txt.contains(":") || txt.all { it.isDigit() }) {
                                lengthText = txt
                            }
                        }
                    }

                    if (videoId.isNotBlank() && videoId.length == 11 && title.isNotBlank()) {
                        if (list.none { it.id == videoId }) {
                            val durationMs = parseDurationTextToMs(lengthText)
                            list.add(
                                OnlineTrack(
                                    id = videoId,
                                    title = title,
                                    artist = artist,
                                    durationText = lengthText,
                                    durationMs = durationMs,
                                    thumbnail = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg",
                                    views = "YouTube",
                                    mood = playlistTitle,
                                    webLink = "https://www.youtube.com/watch?v=$videoId",
                                    source = "YouTube"
                                )
                            )
                        }
                    }
                    return
                }

                val keys = node.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val child = node.opt(k)
                    if (child != null) {
                        extractInnertubeVideoItems(child, list, playlistTitle)
                    }
                }
            }
            is JSONArray -> {
                for (i in 0 until node.length()) {
                    val item = node.opt(i)
                    if (item != null) {
                        extractInnertubeVideoItems(item, list, playlistTitle)
                    }
                }
            }
        }
    }

    private fun fetchPlaylistFromPiped(playlistId: String): OnlinePlaylistResult? {
        val cleanPlId = playlistId.trim()
        val playlistEndpoints = listOf(
            "https://api.piped.private.coffee/playlists/$cleanPlId",
            "https://pipedapi.tokhmi.xyz/playlists/$cleanPlId"
        )

        for (endpoint in playlistEndpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    val title = json.optString("name", "YouTube Playlist").trim().ifBlank { "YouTube Playlist" }
                    val uploader = json.optString("uploader", "YouTube Music").trim()
                    val bannerUrl = json.optString("bannerUrl", "")
                    val thumbnailUrl = json.optString("thumbnailUrl", bannerUrl)

                    val relatedStreams = json.optJSONArray("relatedStreams") ?: json.optJSONArray("items") ?: JSONArray()
                    val tracks = parsePipedTracks(relatedStreams, title)

                    if (tracks.isNotEmpty()) {
                        val playlistThumb = thumbnailUrl.ifBlank {
                            tracks.firstOrNull()?.thumbnail ?: ""
                        }
                        return OnlinePlaylistResult(
                            playlistId = cleanPlId,
                            title = title,
                            uploader = uploader,
                            thumbnail = playlistThumb,
                            tracks = tracks
                        )
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching playlist from $endpoint: ${e.message}")
            }
        }
        return null
    }

    /** Search tracks via Piped YouTube proxy endpoint */
    suspend fun fetchTracks(queryOrGenre: String = "Trending"): List<OnlineTrack> =
        withContext(Dispatchers.IO) {
            val trimmed = queryOrGenre.trim()
            val rawQuery = when {
                trimmed.isBlank() || trimmed.equals("Trending", ignoreCase = true) -> "Trending music 2024"
                CATEGORY_QUERIES.containsKey(trimmed) -> CATEGORY_QUERIES[trimmed]!!
                else -> trimmed
            }
            val encodedQuery = URLEncoder.encode(rawQuery, "UTF-8")

            val searchEndpoints = listOf(
                "https://api.piped.private.coffee/search?q=$encodedQuery&filter=music_songs",
                "https://pipedapi.tokhmi.xyz/search?q=$encodedQuery&filter=music_songs"
            )

            for (endpoint in searchEndpoints) {
                try {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 7000
                        readTimeout = 7000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        setRequestProperty("Accept", "application/json")
                    }

                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        conn.disconnect()
                        val json = JSONObject(body)
                        val items = json.optJSONArray("items") ?: JSONArray()
                        val tracks = parsePipedTracks(items, trimmed.ifBlank { "Trending" })
                        if (tracks.isNotEmpty()) {
                            Log.d(TAG, "Successfully loaded ${tracks.size} tracks for '$queryOrGenre' from $endpoint")
                            return@withContext tracks
                        }
                    } else {
                        conn.disconnect()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error fetching from $endpoint: ${e.message}")
                }
            }

            emptyList()
        }

    /** Resolves the full duration audio stream URL via RapidAPI YTStream or Piped fallback */
    suspend fun resolveStreamUrl(videoId: String): String? =
        withContext(Dispatchers.IO) {
            val cached = streamCache[videoId]
            if (!cached.isNullOrBlank()) {
                return@withContext cached
            }

            // 1. Primary: RapidAPI YTStream (highest bitrate selection for HD quality)
            val dlUrl = "https://$RAPID_API_HOST/dl?id=$videoId"
            try {
                val conn = (URL(dlUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = TIMEOUT_MS
                    readTimeout = TIMEOUT_MS
                    setRequestProperty("x-rapidapi-key", RAPID_API_KEY)
                    setRequestProperty("x-rapidapi-host", RAPID_API_HOST)
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    val formats = json.optJSONArray("adaptiveFormats") ?: json.optJSONArray("formats") ?: JSONArray()
                    
                    var bestAudioUrl: String? = null
                    var highestBitrate = 0L

                    for (i in 0 until formats.length()) {
                        val f = formats.optJSONObject(i) ?: continue
                        val mime = f.optString("mimeType", "")
                        if (mime.contains("audio", ignoreCase = true)) {
                            val bitrate = f.optLong("bitrate", 0L)
                            val url = f.optString("url", "")
                            if (url.isNotBlank()) {
                                // Strictly select highest bitrate for HD sound quality
                                if (bestAudioUrl == null || bitrate > highestBitrate) {
                                    bestAudioUrl = url
                                    highestBitrate = bitrate
                                }
                            }
                        }
                    }

                    if (!bestAudioUrl.isNullOrBlank()) {
                        val finalUrl = followRedirectIfNeeded(bestAudioUrl)
                        streamCache[videoId] = finalUrl
                        Log.d(TAG, "Successfully resolved HD audio stream for $videoId (bitrate $highestBitrate)")
                        return@withContext finalUrl
                    }
                } else {
                    val err = conn.errorStream?.bufferedReader()?.use { it.readText() }
                    conn.disconnect()
                    Log.e(TAG, "RapidAPI YTStream failed: HTTP ${conn.responseCode} - $err")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving audio stream for $videoId via RapidAPI", e)
            }

            // 2. Fallback: Piped HD Audio Stream Resolver
            val pipedUrl = resolveAudioStreamFromPiped(videoId)
            if (!pipedUrl.isNullOrBlank()) {
                streamCache[videoId] = pipedUrl
                return@withContext pipedUrl
            }

            null
        }

    private fun resolveAudioStreamFromPiped(videoId: String): String? {
        val streamEndpoints = listOf(
            "https://api.piped.private.coffee/streams/$videoId",
            "https://pipedapi.tokhmi.xyz/streams/$videoId"
        )

        for (endpoint in streamEndpoints) {
            try {
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "Mozilla/5.0")
                    setRequestProperty("Accept", "application/json")
                }

                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    val json = JSONObject(body)
                    val audioStreams = json.optJSONArray("audioStreams") ?: JSONArray()
                    
                    var bestUrl: String? = null
                    var highestBitrate = 0L

                    for (i in 0 until audioStreams.length()) {
                        val stream = audioStreams.optJSONObject(i) ?: continue
                        val url = stream.optString("url", "")
                        val bitrate = stream.optLong("bitrate", 0L)
                        if (url.isNotBlank() && (bestUrl == null || bitrate > highestBitrate)) {
                            bestUrl = url
                            highestBitrate = bitrate
                        }
                    }

                    if (!bestUrl.isNullOrBlank()) {
                        Log.d(TAG, "Successfully resolved HD audio stream from Piped for $videoId (bitrate $highestBitrate)")
                        return bestUrl
                    }
                } else {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error resolving audio stream from Piped endpoint $endpoint: ${e.message}")
            }
        }
        return null
    }

    /** Follows 302 redirect from redirector.googlevideo.com to direct CDN node */
    private fun followRedirectIfNeeded(initialUrl: String): String {
        if (!initialUrl.contains("redirector.googlevideo.com")) return initialUrl
        try {
            val conn = (URL(initialUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "Mozilla/5.0")
            }
            val redirectLoc = conn.getHeaderField("Location")
            conn.disconnect()
            if (!redirectLoc.isNullOrBlank()) {
                return redirectLoc
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve 302 redirect, using initial URL", e)
        }
        return initialUrl
    }

    private fun parsePipedTracks(array: JSONArray, categoryLabel: String): List<OnlineTrack> {
        val list = mutableListOf<OnlineTrack>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val urlPath = item.optString("url", "")
            val videoId = when {
                urlPath.contains("/watch?v=") -> urlPath.substringAfter("/watch?v=").substringBefore("&")
                urlPath.isNotBlank() -> urlPath
                else -> continue
            }
            val title = item.optString("title", "").trim()
            if (videoId.isBlank() || title.isBlank()) continue

            val artist = item.optString("uploaderName", "Unknown Artist").trim()
            val cleanVid = videoId.substringBefore("&").substringBefore("?").trim()
            val thumbnail = if (cleanVid.isNotBlank()) {
                "https://i.ytimg.com/vi/$cleanVid/hqdefault.jpg"
            } else {
                "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            }
            val durationSec = item.optLong("duration", 210L).coerceAtLeast(1L)
            val durationMs = durationSec * 1000L
            val mins = durationSec / 60
            val secs = durationSec % 60
            val durStr = String.format("%d:%02d", mins, secs)
            val webLink = "https://www.youtube.com/watch?v=$videoId"

            list.add(
                OnlineTrack(
                    id = videoId,
                    streamUrl = "", // resolved on-demand when clicked
                    title = title,
                    artist = artist,
                    durationText = durStr,
                    durationMs = durationMs,
                    thumbnail = thumbnail,
                    views = "YouTube",
                    mood = categoryLabel,
                    webLink = webLink,
                    source = "YouTube"
                )
            )
        }
        return list
    }
}
