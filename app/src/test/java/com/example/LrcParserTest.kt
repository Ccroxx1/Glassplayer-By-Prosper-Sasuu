package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {
    @Test
    fun parsesMultipleTimestampsOnOneLine() {
        val lines = LrcParser.parse("[00:01.20][00:03.45]Hello")
        assertEquals(2, lines.size)
        assertEquals(1_200L, lines[0].timestampMs)
        assertEquals(3_450L, lines[1].timestampMs)
        assertTrue(lines.all { it.text == "Hello" })
    }

    @Test
    fun ignoresMetadataTags() {
        val lines = LrcParser.parse("[ar:Artist]\n[ti:Title]\n[00:12.34]Lyrics")
        assertEquals(1, lines.size)
        assertEquals(12_340L, lines[0].timestampMs)
        assertEquals("Lyrics", lines[0].text)
    }

    @Test
    fun activeIndexReturnsLastLineAtOrBeforePosition() {
        val lines = LrcParser.parse("[00:01.00]One\n[00:03.00]Two")
        assertEquals(-1, LrcParser.activeIndex(lines, 999))
        assertEquals(0, LrcParser.activeIndex(lines, 1_500))
        assertEquals(1, LrcParser.activeIndex(lines, 3_000))
    }
}
