package com.example

/**
 * Parses standard LRC (Lyric) format into a list of timed lines.
 *
 * Supported formats:
 *   [mm:ss.xx] lyric text
 *   [mm:ss.xxx] lyric text
 *   [mm:ss] lyric text
 */
data class LrcLine(val timestampMs: Long, val text: String)

object LrcParser {

    private val TIMESTAMP_REGEX = Regex("""\[(\d{2}):(\d{2})(?:[.:](\d{1,3}))?\]""")

    /**
     * Parses raw LRC string into a time-sorted list of [LrcLine].
     * Lines without timestamps (e.g. metadata tags like [ti:Title]) are ignored.
     */
    fun parse(raw: String): List<LrcLine> {
        if (raw.isBlank()) return emptyList()
        val result = mutableListOf<LrcLine>()
        raw.lineSequence().forEach { line ->
            val matches = TIMESTAMP_REGEX.findAll(line).toList()
            if (matches.isEmpty()) return@forEach

            // Standard LRC may attach several timestamps to one lyric line,
            // e.g. [00:12.00][00:42.50]Same lyric. Create one entry per timestamp.
            val text = line.substring(matches.last().range.last + 1).trim()
            if (text.isEmpty()) return@forEach

            matches.forEach { match ->
                val minutes = match.groupValues[1].toLongOrNull() ?: return@forEach
                val seconds = match.groupValues[2].toLongOrNull() ?: return@forEach
                val fracRaw = match.groupValues[3]
                val millis = when (fracRaw.length) {
                    0 -> 0L
                    1 -> fracRaw.toLongOrNull()?.times(100) ?: 0L
                    2 -> fracRaw.toLongOrNull()?.times(10) ?: 0L
                    else -> fracRaw.take(3).toLongOrNull() ?: 0L
                }
                result.add(
                    LrcLine(
                        timestampMs = minutes * 60_000L + seconds * 1_000L + millis,
                        text = text
                    )
                )
            }
        }
        return result.sortedBy { it.timestampMs }
    }

    /**
     * Returns the index of the currently active lyric line for [positionMs].
     * Returns -1 if before the first line.
     */
    fun activeIndex(lines: List<LrcLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var idx = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= positionMs) idx = i else break
        }
        return idx
    }
}
