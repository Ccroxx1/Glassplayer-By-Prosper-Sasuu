package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun synthUri_isProcedural() {
    assertEquals("procedural://synth", AudioRepository.SYNTH_URI)
  }

  @Test
  fun synthLyrics_arePresent() {
    assertTrue(AudioRepository.SYNTH_LYRICS.contains("Neon Pulse"))
  }
}
