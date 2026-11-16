package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Holds live accent colors extracted from the current track's album art via Palette API.
 * Composables that read [GlassCyan] / [GlassMagenta] / [GlassPurple] recompose when these change.
 */
object GlassDynamic {
    var cyan by mutableStateOf(Color(0xFF00F0FF))
    var magenta by mutableStateOf(Color(0xFFFF007F))
    var purple by mutableStateOf(Color(0xFF9D00FF))

    fun reset() {
        cyan = Color(0xFF00F0FF)
        magenta = Color(0xFFFF007F)
        purple = Color(0xFF9D00FF)
    }
}

suspend fun extractGlassAccentsFromArt(context: Context, albumArtUri: String?) {
    if (albumArtUri.isNullOrBlank()) {
        GlassDynamic.reset()
        return
    }
    val bitmap = withContext(Dispatchers.IO) { decodeAlbumArt(context, albumArtUri) }
    if (bitmap == null) {
        GlassDynamic.reset()
        return
    }
    val palette = withContext(Dispatchers.Default) {
        Palette.from(bitmap).maximumColorCount(16).generate()
    }
    val vibrant = palette.vibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
    val muted = palette.darkVibrantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
    val secondary = palette.lightMutedSwatch?.rgb
        ?: palette.vibrantSwatch?.rgb
        ?: muted

    withContext(Dispatchers.Main.immediate) {
        GlassDynamic.cyan = vibrant?.let { Color(it).liftForGlass() } ?: Color(0xFF00F0FF)
        GlassDynamic.magenta = muted?.let { Color(it).liftForGlass(preferWarm = true) } ?: Color(0xFFFF007F)
        GlassDynamic.purple = secondary?.let { Color(it).liftForGlass() } ?: Color(0xFF9D00FF)
    }
}

private fun Color.liftForGlass(preferWarm: Boolean = false): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(
        android.graphics.Color.argb(
            255,
            (red * 255).toInt().coerceIn(0, 255),
            (green * 255).toInt().coerceIn(0, 255),
            (blue * 255).toInt().coerceIn(0, 255)
        ),
        hsv
    )
    hsv[1] = hsv[1].coerceIn(0.45f, 0.95f)
    hsv[2] = hsv[2].coerceIn(0.55f, 1f)
    if (preferWarm && hsv[0] in 180f..260f) {
        hsv[0] = (hsv[0] + 80f) % 360f
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun decodeAlbumArt(context: Context, uriString: String): Bitmap? {
    return try {
        val uri = Uri.parse(uriString)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        var sample = 1
        val maxDim = 256
        var halfH = bounds.outHeight / 2
        var halfW = bounds.outWidth / 2
        while (halfH / sample >= maxDim && halfW / sample >= maxDim) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample.coerceAtLeast(1) }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }
    } catch (_: Exception) {
        null
    }
}
