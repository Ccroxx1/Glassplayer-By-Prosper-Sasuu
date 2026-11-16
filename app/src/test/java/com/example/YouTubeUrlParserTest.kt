package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeUrlParserTest {

    @Test
    fun parse_normalSearchText_returnsNotAYouTubeUrl() {
        val result = YouTubeUrlParser.parse("Blinding Lights")
        assertTrue(result is YouTubeUrlResult.NotAYouTubeUrl)
    }

    @Test
    fun parse_youtubeMusicWatchUrl_returnsSingleVideo() {
        val result = YouTubeUrlParser.parse("https://music.youtube.com/watch?v=fHI8X4OXluQ")
        assertTrue(result is YouTubeUrlResult.SingleVideo)
        assertEquals("fHI8X4OXluQ", (result as YouTubeUrlResult.SingleVideo).videoId)
    }

    @Test
    fun parse_standardYoutubeWatchUrl_returnsSingleVideo() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        assertTrue(result is YouTubeUrlResult.SingleVideo)
        assertEquals("dQw4w9WgXcQ", (result as YouTubeUrlResult.SingleVideo).videoId)
    }

    @Test
    fun parse_youtuBeShortUrl_returnsSingleVideo() {
        val result = YouTubeUrlParser.parse("https://youtu.be/dQw4w9WgXcQ")
        assertTrue(result is YouTubeUrlResult.SingleVideo)
        assertEquals("dQw4w9WgXcQ", (result as YouTubeUrlResult.SingleVideo).videoId)
    }

    @Test
    fun parse_youtubeMusicPlaylistUrl_returnsPlaylist() {
        val result = YouTubeUrlParser.parse("https://music.youtube.com/playlist?list=RDCLAK5uy_m5_91-y4Zz3g")
        assertTrue(result is YouTubeUrlResult.Playlist)
        assertEquals("RDCLAK5uy_m5_91-y4Zz3g", (result as YouTubeUrlResult.Playlist).playlistId)
    }

    @Test
    fun parse_youtubeWatchWithPlaylistList_returnsPlaylist() {
        val result = YouTubeUrlParser.parse("https://www.youtube.com/watch?v=fHI8X4OXluQ&list=PL123456789")
        assertTrue(result is YouTubeUrlResult.Playlist)
        val pl = result as YouTubeUrlResult.Playlist
        assertEquals("PL123456789", pl.playlistId)
        assertEquals("fHI8X4OXluQ", pl.videoId)
    }

    @Test
    fun parse_invalidUrl_returnsNotAYouTubeUrl() {
        val result = YouTubeUrlParser.parse("https://example.com/watch?v=12345")
        assertTrue(result is YouTubeUrlResult.NotAYouTubeUrl)
    }
}
