package com.example

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Minimal Last.fm API client using HttpURLConnection (no extra dependency).
 *
 * Supported calls:
 *  - [getMobileSession]   — authenticates and returns a session key
 *  - [updateNowPlaying]   — signals now-playing to Last.fm
 *  - [scrobble]           — submits a completed scrobble
 *
 * API key / secret are sourced from BuildConfig (Gradle property or env var).
 * Obtain your own at https://www.last.fm/api/account/create
 */
object LastFmService {

    private val API_KEY: String
        get() = BuildConfig.LASTFM_API_KEY

    private val API_SECRET: String
        get() = BuildConfig.LASTFM_API_SECRET

    private const val BASE_URL = "https://ws.audioscrobbler.com/2.0/"
    private const val TAG      = "LastFmService"

    // ── Auth ─────────────────────────────────────────────────────────────────

    /**
     * Authenticates with Last.fm using username + password (mobile session).
     * Returns a Result containing the session key or an error message.
     */
    suspend fun getMobileSession(username: String, password: String): Result<String> =
        withContext(Dispatchers.IO) {
            if (API_KEY.isBlank() || API_SECRET.isBlank()) {
                Log.w(TAG, "Missing LASTFM_API_KEY/LASTFM_API_SECRET in BuildConfig")
                return@withContext Result.failure(Exception("API keys missing. Check local.properties"))
            }
            try {
                val params = sortedMapOf(
                    "method"   to "auth.getMobileSession",
                    "username" to username,
                    "password" to password,
                    "api_key"  to API_KEY
                )
                params["api_sig"] = sign(params)
                params["format"]  = "json"

                val response = post(params)
                if (response.isSuccess) {
                    val key = parseSessionKey(response.getOrThrow())
                    if (key != null) Result.success(key)
                    else Result.failure(Exception("Unable to parse session key"))
                } else {
                    Result.failure(response.exceptionOrNull() ?: Exception("Unknown error"))
                }
            } catch (e: Exception) {
                Log.w(TAG, "getMobileSession failed: ${e.message}")
                Result.failure(e)
            }
        }

    // ── Now playing ──────────────────────────────────────────────────────────

    suspend fun updateNowPlaying(
        artist: String,
        title: String,
        album: String,
        sessionKey: String
    ) = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank() || API_SECRET.isBlank()) return@withContext Unit
        try {
            val params = sortedMapOf(
                "method"     to "track.updateNowPlaying",
                "artist"     to artist,
                "track"      to title,
                "album"      to album,
                "api_key"    to API_KEY,
                "sk"         to sessionKey
            )
            params["api_sig"] = sign(params)
            params["format"]  = "json"
            post(params)
        } catch (e: Exception) {
            Log.w(TAG, "updateNowPlaying failed: ${e.message}")
        }
        Unit
    }

    // ── Scrobble ─────────────────────────────────────────────────────────────

    /**
     * Scrobbles a track. [timestamp] is Unix epoch seconds of when the track started.
     * Scrobbling is only submitted if the track was listened to for ≥ 50% of its
     * duration or ≥ 4 minutes — enforce this in the caller.
     */
    suspend fun scrobble(
        artist: String,
        title: String,
        album: String,
        timestamp: Long,
        sessionKey: String
    ) = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank() || API_SECRET.isBlank()) return@withContext Unit
        try {
            val params = sortedMapOf(
                "method"        to "track.scrobble",
                "artist[0]"     to artist,
                "track[0]"      to title,
                "album[0]"      to album,
                "timestamp[0]"  to timestamp.toString(),
                "api_key"       to API_KEY,
                "sk"            to sessionKey
            )
            params["api_sig"] = sign(params)
            params["format"]  = "json"
            post(params)
        } catch (e: Exception) {
            Log.w(TAG, "scrobble failed: ${e.message}")
        }
        Unit
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Generates the MD5 API signature required by Last.fm.
     * All params (excluding format/callback) sorted alphabetically,
     * concatenated as key+value, then appended with the secret.
     */
    private fun sign(params: Map<String, String>): String {
        val raw = params
            .filter { it.key != "format" && it.key != "callback" }
            .entries
            .sortedBy { it.key }
            .joinToString("") { "${it.key}${it.value}" } + API_SECRET
        return md5(raw)
    }

    private fun md5(input: String): String {
        val bytes = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun post(params: Map<String, String>): Result<String> {
        return try {
            val body = params.entries.joinToString("&") {
                "${java.net.URLEncoder.encode(it.key, "UTF-8")}=${java.net.URLEncoder.encode(it.value, "UTF-8")}"
            }
            val conn = URL(BASE_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            conn.setRequestProperty("User-Agent", "GlassPlayer/1.0")
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            DataOutputStream(conn.outputStream).use { it.writeBytes(body) }

            val code = conn.responseCode
            if (code in 200..299) {
                Result.success(conn.inputStream.bufferedReader().readText().also { conn.disconnect() })
            } else {
                val errorJson = conn.errorStream?.bufferedReader()?.readText() ?: ""
                val errorMessage = parseErrorMessage(errorJson) ?: "HTTP $code"
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseErrorMessage(json: String): String? {
        // Tiny inline JSON extract for "message"
        val marker = "\"message\""
        val start = json.indexOf(marker)
        if (start == -1) return null
        val q1 = json.indexOf('"', start + marker.length + 1)
        if (q1 == -1) return null
        val q2 = json.indexOf('"', q1 + 1)
        if (q2 == -1) return null
        return json.substring(q1 + 1, q2)
    }

    private fun parseSessionKey(json: String): String? {
        // Tiny inline JSON extract — avoids adding a JSON library dep
        val marker = "\"key\""
        val start = json.indexOf(marker)
        if (start == -1) return null
        val q1 = json.indexOf('"', start + marker.length + 1)
        if (q1 == -1) return null
        val q2 = json.indexOf('"', q1 + 1)
        if (q2 == -1) return null
        return json.substring(q1 + 1, q2).ifBlank { null }
    }
}
