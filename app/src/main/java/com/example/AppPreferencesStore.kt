package com.example

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appPrefsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "app_preferences")

/**
 * Persists all new user-configurable app settings via DataStore.
 * Covers: crossfade, pitch, sleep-timer fade, color theme, Last.fm credentials.
 */
class AppPreferencesStore(private val context: Context) {

    companion object {
        private val KEY_CROSSFADE_SEC = floatPreferencesKey("crossfade_sec")
        private val KEY_PITCH_SEMITONES = floatPreferencesKey("pitch_semitones")
        private val KEY_SLEEP_FADE_ENABLED = booleanPreferencesKey("sleep_fade_enabled")
        private val KEY_COLOR_THEME = stringPreferencesKey("color_theme")
        private val KEY_LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        private val KEY_LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
    }

    // ── Crossfade ────────────────────────────────────────────────────────────
    val crossfadeSec: Flow<Float> = context.appPrefsDataStore.data.map {
        it[KEY_CROSSFADE_SEC] ?: 0f
    }

    suspend fun setCrossfadeSec(sec: Float) {
        context.appPrefsDataStore.edit { it[KEY_CROSSFADE_SEC] = sec.coerceIn(0f, 10f) }
    }

    // ── Pitch ────────────────────────────────────────────────────────────────
    val pitchSemitones: Flow<Float> = context.appPrefsDataStore.data.map {
        it[KEY_PITCH_SEMITONES] ?: 0f
    }

    suspend fun setPitchSemitones(semitones: Float) {
        context.appPrefsDataStore.edit {
            it[KEY_PITCH_SEMITONES] = semitones.coerceIn(-6f, 6f)
        }
    }

    // ── Sleep timer fade ─────────────────────────────────────────────────────
    val sleepFadeEnabled: Flow<Boolean> = context.appPrefsDataStore.data.map {
        it[KEY_SLEEP_FADE_ENABLED] ?: false
    }

    suspend fun setSleepFadeEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[KEY_SLEEP_FADE_ENABLED] = enabled }
    }

    // ── Color theme ──────────────────────────────────────────────────────────
    val colorTheme: Flow<String> = context.appPrefsDataStore.data.map {
        it[KEY_COLOR_THEME] ?: GlassTheme.DYNAMIC.name
    }

    suspend fun setColorTheme(theme: GlassTheme) {
        context.appPrefsDataStore.edit { it[KEY_COLOR_THEME] = theme.name }
    }

    // ── Last.fm ──────────────────────────────────────────────────────────────
    val lastFmUsername: Flow<String> = context.appPrefsDataStore.data.map {
        it[KEY_LASTFM_USERNAME] ?: ""
    }

    val lastFmSessionKey: Flow<String> = context.appPrefsDataStore.data.map {
        it[KEY_LASTFM_SESSION_KEY] ?: ""
    }

    suspend fun setLastFmCredentials(username: String, sessionKey: String) {
        context.appPrefsDataStore.edit {
            it[KEY_LASTFM_USERNAME] = username
            it[KEY_LASTFM_SESSION_KEY] = sessionKey
        }
    }

    suspend fun clearLastFmCredentials() {
        context.appPrefsDataStore.edit {
            it.remove(KEY_LASTFM_USERNAME)
            it.remove(KEY_LASTFM_SESSION_KEY)
        }
    }
}
