package com.example

import android.graphics.Bitmap
import android.graphics.Rect
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Intelligent Coil transformation that eliminates dark/black letterbox and pillarbox bars
 * commonly baked into YouTube thumbnails (e.g., 4:3 hqdefault.jpg with 16:9 content)
 * or improperly framed track artwork.
 *
 * Ensures all track thumbnails consistently fill their containers with clean, visually centred artwork,
 * while preventing unintended cropping on true 1:1 album covers or clean HD artwork.
 */
class CleanArtworkTransformation : Transformation {
    override val cacheKey: String = "CleanArtworkTransformation_v2"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        return ArtworkTransformer.cropLetterboxBars(input)
    }

    override fun equals(other: Any?): Boolean = other is CleanArtworkTransformation
    override fun hashCode(): Int = cacheKey.hashCode()
}

object ArtworkTransformer {

    /**
     * Checks if a row of pixels consists entirely of dark/black pixels.
     * Uses multiple sample points across the row to be fast and resilient to slight noise.
     */
    private fun isRowDark(bitmap: Bitmap, y: Int, sampleXs: IntArray, threshold: Int = 26): Boolean {
        for (x in sampleXs) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r > threshold || g > threshold || b > threshold) return false
        }
        return true
    }

    /**
     * Checks if a column of pixels consists entirely of dark/black pixels.
     */
    private fun isColDark(bitmap: Bitmap, x: Int, sampleYs: IntArray, threshold: Int = 26): Boolean {
        for (y in sampleYs) {
            val pixel = bitmap.getPixel(x, y)
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            if (r > threshold || g > threshold || b > threshold) return false
        }
        return true
    }

    /**
     * Calculates the bounding rectangle of the actual artwork content inside [bitmap],
     * stripping any black letterbox (top/bottom) or pillarbox (left/right) bars.
     */
    fun findContentBounds(bitmap: Bitmap): Rect {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 16 || h <= 16) return Rect(0, 0, w, h)

        val ratio = w.toFloat() / h.toFloat()

        // 1:1 square artwork (e.g. standard album covers) never has letterbox bars
        if (abs(ratio - 1.0f) < 0.04f) {
            return Rect(0, 0, w, h)
        }

        val sampleXs = intArrayOf(
            (w * 0.12f).toInt(),
            (w * 0.25f).toInt(),
            (w * 0.38f).toInt(),
            (w * 0.50f).toInt(),
            (w * 0.62f).toInt(),
            (w * 0.75f).toInt(),
            (w * 0.88f).toInt()
        )

        // Check top letterbox
        var top = 0
        val maxScanY = (h * 0.24f).toInt()
        while (top < maxScanY && isRowDark(bitmap, top, sampleXs)) {
            top++
        }

        // Check bottom letterbox
        var bottom = h - 1
        val minScanY = (h * 0.76f).toInt()
        while (bottom > minScanY && isRowDark(bitmap, bottom, sampleXs)) {
            bottom--
        }

        val topBarHeight = top
        val bottomBarHeight = h - 1 - bottom

        // Detect YouTube 4:3 letterboxing (where 16:9 video has ~12.5% black bars top & bottom)
        // or any symmetric top/bottom letterboxing >= 4% of height
        val is4to3Letterbox = (ratio in 1.25f..1.45f) &&
                topBarHeight >= (h * 0.08f).toInt() &&
                bottomBarHeight >= (h * 0.08f).toInt()

        val isGeneralLetterbox = topBarHeight >= (h * 0.04f).toInt() &&
                bottomBarHeight >= (h * 0.04f).toInt() &&
                abs(topBarHeight - bottomBarHeight) <= (h * 0.05f).toInt()

        val cropTop = if (is4to3Letterbox || isGeneralLetterbox) top else 0
        val cropBottom = if (is4to3Letterbox || isGeneralLetterbox) (bottom + 1).coerceAtMost(h) else h

        // Check left/right pillarbox bars (e.g. 4:3 inside 16:9 canvas)
        val sampleYs = intArrayOf(
            (h * 0.25f).toInt(),
            (h * 0.40f).toInt(),
            (h * 0.50f).toInt(),
            (h * 0.60f).toInt(),
            (h * 0.75f).toInt()
        )

        var left = 0
        val maxScanX = (w * 0.24f).toInt()
        while (left < maxScanX && isColDark(bitmap, left, sampleYs)) {
            left++
        }

        var right = w - 1
        val minScanX = (w * 0.76f).toInt()
        while (right > minScanX && isColDark(bitmap, right, sampleYs)) {
            right--
        }

        val leftBarWidth = left
        val rightBarWidth = w - 1 - right

        val isPillarbox = leftBarWidth >= (w * 0.04f).toInt() &&
                rightBarWidth >= (w * 0.04f).toInt() &&
                abs(leftBarWidth - rightBarWidth) <= (w * 0.05f).toInt()

        val cropLeft = if (isPillarbox) left else 0
        val cropRight = if (isPillarbox) (right + 1).coerceAtMost(w) else w

        return Rect(cropLeft, cropTop, cropRight, cropBottom)
    }

    /**
     * Crops any black letterbox or pillarbox bars out of the given [input] bitmap.
     * Returns the cleaned bitmap, or the original if no cropping is required.
     */
    fun cropLetterboxBars(input: Bitmap): Bitmap {
        return try {
            val bounds = findContentBounds(input)
            val cropWidth = bounds.width()
            val cropHeight = bounds.height()

            if (cropWidth <= 0 || cropHeight <= 0) return input
            if (cropWidth >= input.width && cropHeight >= input.height) return input

            Bitmap.createBitmap(input, bounds.left, bounds.top, cropWidth, cropHeight)
        } catch (_: Exception) {
            input
        }
    }
}
