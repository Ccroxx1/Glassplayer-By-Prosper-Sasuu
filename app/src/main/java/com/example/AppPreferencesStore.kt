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
        private val KEY_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
        private val KEY_LASTFM_USERNAME = stringPreferencesKey("lastfm_username")
        private val KEY_LASTFM_SESSION_KEY = stringPreferencesKey("lastfm_session_key")
        private val KEY_EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        private val KEY_EQ_BAND_0 = floatPreferencesKey("eq_band_0")
        private val KEY_EQ_BAND_1 = floatPreferencesKey("eq_band_1")
        private val KEY_EQ_BAND_2 = floatPreferencesKey("eq_band_2")
        private val KEY_EQ_BAND_3 = floatPreferencesKey("eq_band_3")
        private val KEY_EQ_BAND_4 = floatPreferencesKey("eq_band_4")
        private val KEY_EQ_BASS_BOOST = floatPreferencesKey("eq_bass_boost")
        private val KEY_EQ_VIRTUALIZER = floatPreferencesKey("eq_virtualizer")
        private val KEY_EQ_PRESET = stringPreferencesKey("eq_preset")
        private val KEY_VISUALIZER_STYLE = stringPreferencesKey("visualizer_style")
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

    // ── Language ─────────────────────────────────────────────────────────────
    val appLanguage: Flow<String> = context.appPrefsDataStore.data.map {
        it[KEY_LANGUAGE] ?: "English"
    }

    suspend fun setAppLanguage(lang: String) {
        context.appPrefsDataStore.edit { it[KEY_LANGUAGE] = lang }
    }

    val isFirstLaunch: Flow<Boolean> = context.appPrefsDataStore.data.map {
        it[KEY_FIRST_LAUNCH] ?: true
    }

    suspend fun setFirstLaunchCompleted() {
        context.appPrefsDataStore.edit { it[KEY_FIRST_LAUNCH] = false }
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

    // ── Equalizer ────────────────────────────────────────────────────────────
    val equalizerEnabled: Flow<Boolean> = context.appPrefsDataStore.data.map {
        it[KEY_EQ_ENABLED] ?: true
    }
    suspend fun setEqualizerEnabled(enabled: Boolean) {
        context.appPrefsDataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    val equalizerBands: Flow<List<Float>> = context.appPrefsDataStore.data.map {
        val b0 = it[KEY_EQ_BAND_0] ?: 0.5f
        val b1 = it[KEY_EQ_BAND_1] ?: 0.5f
        val b2 = it[KEY_EQ_BAND_2] ?: 0.5f
        val b3 = it[KEY_EQ_BAND_3] ?: 0.5f
        val b4 = it[KEY_EQ_BAND_4] ?: 0.5f
        listOf(b0, b1, b2, b3, b4)
    }
    suspend fun setEqualizerBands(bands: List<Float>) {
        if (bands.size != 5) return
        context.appPrefsDataStore.edit {
            it[KEY_EQ_BAND_0] = bands[0]
            it[KEY_EQ_BAND_1] = bands[1]
            it[KEY_EQ_BAND_2] = bands[2]
            it[KEY_EQ_BAND_3] = bands[3]
            it[KEY_EQ_BAND_4] = bands[4]
        }
    }

    val bassBoostStrength: Flow<Float> = context.appPrefsDataStore.data.map {
        it[KEY_EQ_BASS_BOOST] ?: 0f
    }
    suspend fun setBassBoostStrength(str: Float) {
        context.appPrefsDataStore.edit { it[KEY_EQ_BASS_BOOST] = str }
    }

    val virtualizerStrength: Flow<Float> = context.appPrefsDataStore.data.map {
        it[KEY_EQ_VIRTUALIZER] ?: 0f
    }
    suspend fun setVirtualizerStrength(str: Float) {
        context.appPrefsDataStore.edit { it[KEY_EQ_VIRTUALIZER] = str }
    }

    val equalizerPreset: Flow<String> = context.appPrefsDataStore.data.map {
        it[KEY_EQ_PRESET] ?: "Flat"
    }
    suspend fun setEqualizerPreset(preset: String) {
        context.appPrefsDataStore.edit { it[KEY_EQ_PRESET] = preset }
    }

    val visualizerStyle: Flow<VisualizerStyle> = context.appPrefsDataStore.data.map {
        val name = it[KEY_VISUALIZER_STYLE] ?: VisualizerStyle.BARS.name
        try { VisualizerStyle.valueOf(name) } catch (_: Exception) { VisualizerStyle.BARS }
    }
    suspend fun setVisualizerStyle(style: VisualizerStyle) {
        context.appPrefsDataStore.edit { it[KEY_VISUALIZER_STYLE] = style.name }
    }
}
