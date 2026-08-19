package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Fetches time-stamped (synced) LRC lyrics from the free LRCLib API.
 * No API key required. Returns the raw LRC string or null on failure.
 *
 * API docs: https://lrclib.net/docs
 */
object LrcLibService {

    private const val BASE_URL = "https://lrclib.net/api/get"
    private const val TAG = "LrcLibService"
    private const val TIMEOUT_MS = 8_000

    /**
     * Attempts to fetch synced lyrics. Falls back to plain-text lyrics if synced is not available.
     *
     * @param title Track title
     * @param artist Artist name
     * @param album Album name (optional, improves match accuracy)
     * @param durationSec Track duration in seconds (optional, improves match accuracy)
     * @return LRC-formatted string, plain lyrics string, or null if not found / error
     */
    suspend fun fetchSyncedLyrics(
        title: String,
        artist: String,
        album: String = "",
        durationSec: Int = 0
    ): String? = withContext(Dispatchers.IO) {
        try {
            val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
            val urlBuilder = StringBuilder(BASE_URL)
            urlBuilder.append("?track_name=${enc(title)}")
            urlBuilder.append("&artist_name=${enc(artist)}")
            if (album.isNotBlank()) urlBuilder.append("&album_name=${enc(album)}")
            if (durationSec > 0) urlBuilder.append("&duration=$durationSec")

            val url = URL(urlBuilder.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", "GlassPlayer/1.0 (Android)")
                setRequestProperty("Accept", "application/json")
            }

            val responseCode = connection.responseCode
            if (responseCode == 404) {
                Log.d(TAG, "No lyrics found for: $title — $artist")
                return@withContext null
            }
            if (responseCode != 200) {
                Log.w(TAG, "LRCLib returned HTTP $responseCode for: $title")
                return@withContext null
            }

            val body = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(body)
            // Prefer synced (LRC) lyrics; fall back to plain text
            val synced = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() }
            val plain = json.optString("plainLyrics", "").takeIf { it.isNotBlank() }
            synced ?: plain
        } catch (e: Exception) {
            Log.w(TAG, "LRCLib fetch failed for '$title': ${e.message}")
            null
        }
    }
}
