package com.example

import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.media.MediaBrowserServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android Auto browse service backed by MediaBrowserServiceCompat.
 * Exposes root sections + playable track items from the existing Room database.
 */
class GlassAutoService : MediaBrowserServiceCompat() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var dao: AudioDao

    override fun onCreate() {
        super.onCreate()
        dao = AudioDatabase.getDatabase(applicationContext).audioDao()
        val engine = PlayerEngine.get(applicationContext)
        engine.initMediaSession()
        sessionToken = engine.getSessionToken()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot(ROOT_ID, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        result.detach()
        scope.launch(Dispatchers.IO) {
            val items = when {
                parentId == ROOT_ID -> rootItems()
                parentId == NODE_ALL -> trackItems(dao.getAllTracksSnapshot())
                parentId == NODE_FAVORITES -> trackItems(dao.getAllTracksSnapshot().filter { it.isFavorite })
                parentId == NODE_RECENT -> trackItems(
                    dao.getAllTracksSnapshot()
                        .filter { it.lastPlayed > 0L }
                        .sortedByDescending { it.lastPlayed }
                        .take(50)
                )
                parentId == NODE_PLAYLISTS -> playlistItems(dao.getAllPlaylistsSnapshot())
                parentId.startsWith(PLAYLIST_PREFIX) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX).toIntOrNull()
                    if (playlistId == null) emptyList() else trackItems(dao.getTracksInPlaylistSnapshot(playlistId))
                }
                else -> emptyList()
            }
            withContext(Dispatchers.Main) {
                result.sendResult(items.toMutableList())
            }
        }
    }

    private fun rootItems(): List<MediaBrowserCompat.MediaItem> {
        return listOf(
            browsableItem(NODE_ALL, "All Songs", "Library"),
            browsableItem(NODE_FAVORITES, "Favorites", "Liked tracks"),
            browsableItem(NODE_RECENT, "Recently Played", "Latest sessions"),
            browsableItem(NODE_PLAYLISTS, "Playlists", "Custom collections")
        )
    }

    private fun playlistItems(playlists: List<PlaylistEntity>): List<MediaBrowserCompat.MediaItem> {
        return playlists.map { playlist ->
            browsableItem(
                id = "$PLAYLIST_PREFIX${playlist.id}",
                title = playlist.name,
                subtitle = "Playlist"
            )
        }
    }

    private fun trackItems(tracks: List<AudioTrackEntity>): List<MediaBrowserCompat.MediaItem> {
        return tracks
            .asSequence()
            .filter { it.uri != AudioRepository.SYNTH_URI }
            .map { track ->
                val extras = Bundle().apply {
                    putString("album", track.album)
                }
                val description = MediaDescriptionCompat.Builder()
                    .setMediaId(track.uri)
                    .setTitle(track.title)
                    .setSubtitle(track.artist)
                    .setDescription(track.album)
                    .setExtras(extras)
                    .build()
                MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE)
            }
            .toList()
    }

    private fun browsableItem(id: String, title: String, subtitle: String): MediaBrowserCompat.MediaItem {
        val description = MediaDescriptionCompat.Builder()
            .setMediaId(id)
            .setTitle(title)
            .setSubtitle(subtitle)
            .build()
        return MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE)
    }

    companion object {
        private const val ROOT_ID = "root"
        private const val NODE_ALL = "all"
        private const val NODE_FAVORITES = "favorites"
        private const val NODE_RECENT = "recent"
        private const val NODE_PLAYLISTS = "playlists"
        private const val PLAYLIST_PREFIX = "playlist:"
    }
}
