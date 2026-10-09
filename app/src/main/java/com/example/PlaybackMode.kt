package com.example

/**
 * Represents the 4 core playback states:
 * - NORMAL: Sequential order, stops at the end of the playlist.
 * - SHUFFLE: Shuffled order, plays every track once then stops.
 * - REPEAT_ALL: Sequential order, continuously loops from beginning upon reaching the end.
 * - SHUFFLE_REPEAT_ALL: Shuffled order, generates a new shuffle cycle upon exhaustion and continues.
 * - REPEAT_ONE: Continuously loops the currently playing track.
 */
enum class PlaybackMode {
    NORMAL,
    SHUFFLE,
    REPEAT_ALL,
    SHUFFLE_REPEAT_ALL,
    REPEAT_ONE
}
