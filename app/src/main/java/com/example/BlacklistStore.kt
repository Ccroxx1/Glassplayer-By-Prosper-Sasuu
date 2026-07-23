package com.example

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.blacklistDataStore by preferencesDataStore(name = "glassplayer_blacklist")

/**
 * Persists folder names hidden from the music library.
 */
class BlacklistStore(private val context: Context) {

    private val key = stringSetPreferencesKey("blacklisted_folders")

    val blacklistedFolders: Flow<Set<String>> = context.blacklistDataStore.data.map { prefs ->
        prefs[key] ?: emptySet()
    }

    suspend fun setBlacklisted(folderName: String, blacklisted: Boolean) {
        context.blacklistDataStore.edit { prefs ->
            val current = prefs[key]?.toMutableSet() ?: mutableSetOf()
            if (blacklisted) current.add(folderName) else current.remove(folderName)
            prefs[key] = current
        }
    }

    suspend fun clearAll() {
        context.blacklistDataStore.edit { prefs ->
            prefs[key] = emptySet()
        }
    }
}
