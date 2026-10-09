package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.lang.ref.WeakReference

/**
 * Controller bridging communication between Compose / PlayerEngine and the in-app YouTube Music WebView player.
 */
object YouTubeBridge {
    private const val TAG = "YouTubeBridge"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _currentVideoId = MutableStateFlow<String?>(null)
    val currentVideoId = _currentVideoId.asStateFlow()

    private val _isPlayerReady = MutableStateFlow(false)
    val isPlayerReady = _isPlayerReady.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private var activeWebView: WeakReference<WebView>? = null

    // Callbacks registered by PlayerEngine
    var onStateChanged: ((isPlaying: Boolean) -> Unit)? = null
    var onTimeUpdated: ((currentSeconds: Float, durationSeconds: Float) -> Unit)? = null
    var onTrackEnded: (() -> Unit)? = null
    var onError: ((errorCode: Int) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun getOrCreateWebView(context: Context): WebView {
        val existing = activeWebView?.get()
        if (existing != null) {
            return existing
        }
        val newWv = object : WebView(context.applicationContext) {
            override fun onWindowVisibilityChanged(visibility: Int) {
                // Keep WebKit audio playback active when phone screen turns off or Activity is hidden
                super.onWindowVisibilityChanged(VISIBLE)
            }

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                // Prevent WebKit from pausing background audio playback on focus shifts (screen off/on, lock screen)
                super.onWindowFocusChanged(true)
            }
        }.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(0x00000000)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                loadWithOverviewMode = true
                useWideViewPort = true
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: ""
                    return !url.contains("youtube.com") && !url.contains("googlevideo.com")
                }
            }
            addJavascriptInterface(YouTubeJsInterface(), "AndroidBridge")
        }
        activeWebView = WeakReference(newWv)
        return newWv
    }

    fun registerWebView(webView: WebView) {
        activeWebView = WeakReference(webView)
    }

    fun unregisterWebView(webView: WebView) {
        if (activeWebView?.get() == webView) {
            activeWebView = null
            _isPlayerReady.value = false
        }
    }

    fun loadVideo(videoId: String) {
        if (videoId.isBlank()) return
        if (_currentVideoId.value == videoId && _isPlayerReady.value) {
            play()
            return
        }
        _currentVideoId.value = videoId
        lastObservedTime = -1f
        _isLoading.value = true
        mainHandler.postDelayed({ _isLoading.value = false }, 2500)
        mainHandler.post {
            val wv = activeWebView?.get()
            if (wv != null && _isPlayerReady.value) {
                wv.evaluateJavascript("window.loadVideo('$videoId');", null)
            } else if (wv != null) {
                wv.loadDataWithBaseURL(
                    "https://music.youtube.com",
                    generatePlayerHtml(videoId),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    }

    fun play() {
        mainHandler.post {
            val wv = activeWebView?.get()
            if (wv != null) {
                wv.evaluateJavascript("window.playVideo();", null)
            } else {
                val curVid = _currentVideoId.value
                if (!curVid.isNullOrBlank()) {
                    loadVideo(curVid)
                }
            }
        }
    }

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    @Volatile
    private var lastObservedTime = -1f

    fun onJsPlaybackState(playing: Boolean) {
        _isPlaying.value = playing
        onStateChanged?.invoke(playing)
    }

    fun pause() {
        onJsPlaybackState(false)
        mainHandler.post {
            activeWebView?.get()?.evaluateJavascript("window.pauseVideo();", null)
        }
    }

    fun seekTo(seconds: Float) {
        lastObservedTime = -1f
        mainHandler.post {
            activeWebView?.get()?.evaluateJavascript("window.seekTo($seconds);", null)
        }
    }

    // Called from JavaScript interface
    fun onJsReady() {
        _isPlayerReady.value = true
        _isLoading.value = false
    }

    fun onJsState(state: Int) {
        // YT.PlayerState: -1 (unstarted), 0 (ended), 1 (playing), 2 (paused), 3 (buffering), 5 (video cued)
        when (state) {
            1 -> {
                _isLoading.value = false
                onJsPlaybackState(true)
            }
            3 -> {
                // Buffering: Video is actively loading chunks; do not mark as paused
                _isLoading.value = true
            }
            2 -> {
                _isLoading.value = false
                onJsPlaybackState(false)
            }
            0 -> {
                _isLoading.value = false
                onJsPlaybackState(false)
                onTrackEnded?.invoke()
            }
            -1, 5 -> {
                // Unstarted or cued
                _isLoading.value = false
            }
        }
    }

    fun onJsTime(currentTime: Float, duration: Float) {
        if (currentTime > 0) {
            _isLoading.value = false
        }
        if (duration > 0) {
            onTimeUpdated?.invoke(currentTime, duration)
        }
        // If currentTime is progressing, the track is actively playing audio
        if (currentTime > 0f && lastObservedTime >= 0f && currentTime > lastObservedTime) {
            if (!_isPlaying.value) {
                onJsPlaybackState(true)
            }
        }
        lastObservedTime = currentTime
    }

    fun onJsError(errorCode: Int) {
        Log.w(TAG, "YouTube Player JS Error: $errorCode")
        _isLoading.value = false
        onError?.invoke(errorCode)
    }

    fun generatePlayerHtml(videoId: String): String {
        return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
          <style>
            * { margin: 0; padding: 0; box-sizing: border-box; background: transparent; }
            html, body {
              width: 100%;
              height: 100%;
              overflow: hidden;
              background-color: #080a12;
              display: flex;
              align-items: center;
              justify-content: center;
            }
            #container {
              width: 100%;
              height: 100%;
              position: relative;
              overflow: hidden;
              border-radius: 20px;
              background: #080a12;
            }
            iframe {
              width: 100%;
              height: 100%;
              border: none;
              border-radius: 20px;
              object-fit: cover;
            }
            video, .html5-main-video {
              object-fit: cover !important;
            }
          </style>
          <script>
            window.isReady = false;
            window.currentVid = '$videoId';
            window.loadVideo = function(vid) {
              window.currentVid = vid;
              if (window.player && window.isReady && window.player.loadVideoById) {
                window.player.loadVideoById(vid);
                window.player.playVideo();
              }
            };
            window.playVideo = function() {
              try {
                var v = document.querySelector('video') || document.getElementsByTagName('video')[0];
                if (v && v.paused) {
                  v.play();
                }
              } catch(e) {}
              if (window.player && window.player.playVideo) {
                try {
                  window.player.playVideo();
                } catch(e) {}
              }
              if (window.AndroidBridge && window.AndroidBridge.postPlaybackState) {
                window.AndroidBridge.postPlaybackState(true);
              }
              setTimeout(function() {
                try {
                  if (window.player && window.player.getPlayerState) {
                    var st = window.player.getPlayerState();
                    if (st !== 1 && st !== 3 && window.currentVid) {
                      var curTime = window.player.getCurrentTime() || 0;
                      if (window.player.loadVideoById) {
                        window.player.loadVideoById(window.currentVid, curTime);
                        window.player.playVideo();
                      }
                    }
                  }
                } catch(e) {}
              }, 250);
            };
            window.pauseVideo = function() {
              if (window.player && window.isReady && window.player.pauseVideo) {
                window.player.pauseVideo();
              }
              try {
                var v = document.querySelector('video') || document.getElementsByTagName('video')[0];
                if (v && !v.paused) {
                  v.pause();
                }
              } catch(e) {}
              if (window.AndroidBridge && window.AndroidBridge.postPlaybackState) {
                window.AndroidBridge.postPlaybackState(false);
              }
            };
            window.seekTo = function(sec) {
              if (window.player && window.isReady && window.player.seekTo) {
                window.player.seekTo(sec, true);
              }
            };
          </script>
        </head>
        <body>
          <div id="container">
            <div id="player"></div>
          </div>
          <script src="https://www.youtube.com/iframe_api"></script>
          <script>
            function onYouTubeIframeAPIReady() {
              window.player = new YT.Player('player', {
                height: '100%',
                width: '100%',
                videoId: window.currentVid,
                playerVars: {
                  'autoplay': 1,
                  'controls': 1,
                  'playsinline': 1,
                  'rel': 0,
                  'modestbranding': 1,
                  'fs': 0,
                  'enablejsapi': 1,
                  'iv_load_policy': 3,
                  'widget_referrer': 'https://music.youtube.com',
                  'origin': 'https://music.youtube.com'
                },
                events: {
                  'onReady': onPlayerReady,
                  'onStateChange': onPlayerStateChange,
                  'onError': onPlayerError
                }
              });
            }

            function applyVideoCover() {
              try {
                var v = document.querySelector('video') || document.getElementsByTagName('video')[0];
                if (v) {
                  v.style.objectFit = 'cover';
                  v.style.width = '100%';
                  v.style.height = '100%';
                }
              } catch(e) {}
            }

            function onPlayerReady(event) {
              window.isReady = true;
              applyVideoCover();
              setInterval(applyVideoCover, 400);
              if (window.AndroidBridge) {
                window.AndroidBridge.postReady();
              }
              if (window.currentVid) {
                event.target.playVideo();
              }
              setInterval(function() {
                try {
                  if (window.player && window.isReady && window.player.getCurrentTime && window.player.getDuration) {
                    var cur = window.player.getCurrentTime() || 0;
                    var dur = window.player.getDuration() || 0;
                    if (window.AndroidBridge) {
                      window.AndroidBridge.postTime(cur, dur);
                      var st = (window.player && window.player.getPlayerState) ? window.player.getPlayerState() : -1;
                      var v = document.querySelector('video') || document.getElementsByTagName('video')[0];
                      var isPlaying = (st === 1) || (v && !v.paused && !v.ended && cur > 0);
                      if (window.AndroidBridge.postPlaybackState) {
                        if (isPlaying) {
                          window.AndroidBridge.postPlaybackState(true);
                        } else if (st === 2 || (v && v.paused && st !== 3 && st !== 1)) {
                          window.AndroidBridge.postPlaybackState(false);
                        }
                      }
                    }
                  }
                } catch(e) {}
              }, 250);
            }

            function onPlayerStateChange(event) {
              if (window.AndroidBridge) {
                window.AndroidBridge.postState(event.data);
                if (window.AndroidBridge.postPlaybackState) {
                  if (event.data === 1) {
                    window.AndroidBridge.postPlaybackState(true);
                  } else if (event.data === 2 || event.data === 0) {
                    window.AndroidBridge.postPlaybackState(false);
                  }
                }
              }
            }

            function onPlayerError(event) {
              if (window.AndroidBridge) {
                window.AndroidBridge.postError(event.data);
              }
            }
          </script>
        </body>
        </html>
        """.trimIndent()
    }
}

/**
 * JavaScript interface attached to WebView to receive playback events from YouTube IFrame API.
 */
class YouTubeJsInterface {
    @JavascriptInterface
    fun postReady() {
        YouTubeBridge.onJsReady()
    }

    @JavascriptInterface
    fun postState(state: Int) {
        YouTubeBridge.onJsState(state)
    }

    @JavascriptInterface
    fun postPlaybackState(isPlaying: Boolean) {
        YouTubeBridge.onJsPlaybackState(isPlaying)
    }

    @JavascriptInterface
    fun postTime(current: Float, duration: Float) {
        YouTubeBridge.onJsTime(current, duration)
    }

    @JavascriptInterface
    fun postError(errorCode: Int) {
        YouTubeBridge.onJsError(errorCode)
    }
}

/**
 * Seamless, in-app YouTube Music Web/IFrame Player with Glassmorphic styling.
 * Designed to fit naturally within [MainPlayerView] with consistent curves, glass reflections, and ambient glow.
 */
@Composable
fun YouTubeMusicGlassPlayer(
    videoId: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    albumArtUri: String? = null
) {
    val isLoading by YouTubeBridge.isLoading.collectAsState()

    LaunchedEffect(videoId) {
        if (videoId.isNotBlank()) {
            YouTubeBridge.loadVideo(videoId)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Glass Brand Badge for YouTube Music
        Row(
            modifier = Modifier
                .padding(bottom = 10.dp)
                .background(
                    color = Color(0xFFFF0033).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color(0xFFFF0033).copy(alpha = 0.38f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 14.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF0033)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = "YouTube Music",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = Color.White
            )
            Text(
                text = "• Glass Player",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.65f)
            )

            if (isPlaying) {
                Spacer(modifier = Modifier.width(2.dp))
                AnimatedEqualizerBars()
            }
        }

        // Main Glass Player Outer Glow Box (16:9 aspect ratio cleanly matches video artwork)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            // Rotated gradient back-plate layer matching rotating vinyl background design language
            Box(
                modifier = Modifier
                    .fillMaxSize(0.96f)
                    .rotate(-2f)
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFFFF0033).copy(alpha = 0.35f),
                                GlassMagenta.copy(alpha = 0.25f),
                                GlassCyan.copy(alpha = 0.3f)
                            )
                        )
                    )
            )

            // Glass Container Card
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .shadow(
                        elevation = 18.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0xFFFF0033).copy(alpha = 0.35f),
                        spotColor = GlassCyan.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF131524),
                                Color(0xFF080A12)
                            )
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                GlassBorderWhite,
                                Color(0xFFFF0033).copy(alpha = 0.5f),
                                GlassBorderWhite.copy(alpha = 0.25f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                // High-Quality Cover Art Artwork Display
                val ytVid = videoId.ifBlank { albumArtUri?.substringAfter("vi/")?.substringBefore("/") }
                    .orEmpty().substringBefore("&").substringBefore("?").trim()
                val primaryArt = albumArtUri?.takeIf { it.isNotBlank() && it.startsWith("http") && !it.contains("proxy.piped") }
                val fallbackHqUrl = if (ytVid.isNotBlank()) "https://i.ytimg.com/vi/$ytVid/hqdefault.jpg" else "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                val initialCoverUrl = primaryArt ?: fallbackHqUrl

                var resolvedCoverUrl by remember(videoId, albumArtUri) { mutableStateOf(initialCoverUrl) }

                LaunchedEffect(ytVid) {
                    if (ytVid.isNotBlank()) {
                        withContext(Dispatchers.IO) {
                            val maxRes = "https://i.ytimg.com/vi/$ytVid/maxresdefault.jpg"
                            try {
                                val conn = (URL(maxRes).openConnection() as HttpURLConnection).apply {
                                    requestMethod = "HEAD"
                                    connectTimeout = 1500
                                    readTimeout = 1500
                                    instanceFollowRedirects = true
                                }
                                if (conn.responseCode == 200) {
                                    resolvedCoverUrl = maxRes
                                }
                                conn.disconnect()
                            } catch (_: Exception) { }
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(22.dp))
                ) {
                    // Layer 1: Ambient Blurred Background fill
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedCoverUrl)
                            .crossfade(true)
                            .transformations(CleanArtworkTransformation())
                            .error(R.drawable.img_app_icon_1784343634612)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                    Modifier.blur(25.dp)
                                } else {
                                    Modifier
                                }
                            ),
                        alpha = 0.85f
                    )

                    // Layer 2: Main Cover Art - clean, fully visible without unwanted cropping or black bars
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(resolvedCoverUrl)
                            .crossfade(true)
                            .transformations(CleanArtworkTransformation())
                            .error(R.drawable.img_app_icon_1784343634612)
                            .placeholder(R.drawable.img_app_icon_1784343634612)
                            .build(),
                        contentDescription = "Song Cover Art",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(22.dp))
                    )

                    // Layer 3: Glass Vignette Overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.12f),
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.25f)
                                    )
                                )
                            )
                    )
                }

                // Top Specular Highlight Gloss Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0.12f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Loading overlay
                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF080A12).copy(alpha = 0.65f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Color(0xFFFF0033),
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Loading YouTube Stream...",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Animated equalizer indicator inside YouTube Music brand badge
 */
@Composable
private fun AnimatedEqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq_bars")
    val bar1 by transition.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(400, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by transition.animateFloat(
        initialValue = 0.8f, targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(550, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(350, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(11.dp)
    ) {
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height((11 * bar1).dp)
                .clip(CircleShape)
                .background(Color(0xFFFF0033))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height((11 * bar2).dp)
                .clip(CircleShape)
                .background(Color(0xFFFF0033))
        )
        Box(
            modifier = Modifier
                .width(2.5.dp)
                .height((11 * bar3).dp)
                .clip(CircleShape)
                .background(Color(0xFFFF0033))
        )
    }
}
