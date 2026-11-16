package com.example

import java.net.URI

sealed class YouTubeUrlResult {
    data class SingleVideo(val videoId: String) : YouTubeUrlResult()
    data class Playlist(val playlistId: String, val videoId: String? = null) : YouTubeUrlResult()
    object NotAYouTubeUrl : YouTubeUrlResult()
}

object YouTubeUrlParser {

    private val YOUTUBE_HOSTS = setOf(
        "youtube.com",
        "www.youtube.com",
        "m.youtube.com",
        "music.youtube.com",
        "youtu.be"
    )

    fun parse(input: String): YouTubeUrlResult {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return YouTubeUrlResult.NotAYouTubeUrl

        // Ensure scheme for URL parsing if missing
        val urlString = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            if (isYouTubeDomain(trimmed)) "https://$trimmed" else return YouTubeUrlResult.NotAYouTubeUrl
        } else {
            trimmed
        }

        val uri = try {
            URI(urlString)
        } catch (_: Exception) {
            return YouTubeUrlResult.NotAYouTubeUrl
        }

        val host = uri.host?.lowercase() ?: return YouTubeUrlResult.NotAYouTubeUrl
        if (!YOUTUBE_HOSTS.contains(host)) {
            return YouTubeUrlResult.NotAYouTubeUrl
        }

        val queryParams = parseQueryParams(uri.rawQuery)
        val path = uri.path ?: ""

        // Case 1: Short link format (youtu.be/VIDEO_ID)
        if (host == "youtu.be") {
            val shortId = path.trim('/').substringBefore('?').substringBefore('&')
            if (isPlausibleYouTubeVideoId(shortId)) {
                val listId = queryParams["list"]
                return if (!listId.isNullOrBlank()) {
                    YouTubeUrlResult.Playlist(playlistId = listId, videoId = shortId)
                } else {
                    YouTubeUrlResult.SingleVideo(videoId = shortId)
                }
            }
        }

        // Case 2: Playlist URL (/playlist?list=PLAYLIST_ID)
        val rawListParam = queryParams["list"]
        val listParam = rawListParam?.substringBefore("&")?.substringBefore("?")?.trim()
        if (path.contains("/playlist") && !listParam.isNullOrBlank()) {
            return YouTubeUrlResult.Playlist(playlistId = listParam)
        }

        // Case 3: Watch URL with Video ID (/watch?v=VIDEO_ID)
        val rawVideoParam = queryParams["v"]
        val videoParam = rawVideoParam?.substringBefore("&")?.substringBefore("?")?.trim()
        if (!videoParam.isNullOrBlank() && isPlausibleYouTubeVideoId(videoParam)) {
            return if (!listParam.isNullOrBlank()) {
                YouTubeUrlResult.Playlist(playlistId = listParam, videoId = videoParam)
            } else {
                YouTubeUrlResult.SingleVideo(videoId = videoParam)
            }
        }

        // Case 4: Embed URL (/embed/VIDEO_ID)
        if (path.contains("/embed/")) {
            val embedId = path.substringAfter("/embed/").substringBefore('?').substringBefore('/')
            if (isPlausibleYouTubeVideoId(embedId)) {
                return YouTubeUrlResult.SingleVideo(videoId = embedId)
            }
        }

        return YouTubeUrlResult.NotAYouTubeUrl
    }

    private fun isYouTubeDomain(input: String): Boolean {
        val lower = input.lowercase()
        return YOUTUBE_HOSTS.any { lower.startsWith(it) || lower.contains(".$it") }
    }

    private fun parseQueryParams(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val map = mutableMapOf<String, String>()
        val pairs = rawQuery.split("&")
        for (pair in pairs) {
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0]] = parts[1]
            }
        }
        return map
    }
}
