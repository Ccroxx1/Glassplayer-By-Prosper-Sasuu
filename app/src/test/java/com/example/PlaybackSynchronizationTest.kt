package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlaybackSynchronizationTest {

    @Test
    fun youtubeBridge_bufferingIsNotReportedAsPlaying() {
        var observedPlaying = false
        YouTubeBridge.onStateChanged = { playing ->
            observedPlaying = playing
        }

        // State 3: Buffering
        YouTubeBridge.onJsState(3)
        assertFalse("Buffering state (3) must not be reported as playing", observedPlaying)
        assertTrue("Buffering state must set isLoading to true", YouTubeBridge.isLoading.value)

        // State 1: Playing
        YouTubeBridge.onJsState(1)
        assertTrue("Playing state (1) must be reported as playing", observedPlaying)
        assertFalse("Playing state must clear isLoading", YouTubeBridge.isLoading.value)

        // State 2: Paused
        YouTubeBridge.onJsState(2)
        assertFalse("Paused state (2) must be reported as paused", observedPlaying)

        // State 0: Ended
        var endedFired = false
        YouTubeBridge.onTrackEnded = { endedFired = true }
        YouTubeBridge.onJsState(0)
        assertFalse("Ended state (0) must be reported as paused", observedPlaying)
        assertTrue("Ended state must trigger onTrackEnded callback", endedFired)
    }

    @Test
    fun playerEngine_initialAndPausedAmplitudesRestAtBaseline() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val engine = PlayerEngine.get(context)

        assertFalse("Engine should initially not be playing", engine.isPlaying.value)
        val amps = engine.waveformAmplitudes.value
        assertEquals("Waveform should have 24 amplitude bands", 24, amps.size)
        assertTrue("Idle/paused amplitudes must rest at baseline (<= 0.1f)", amps.all { it <= 0.1f })
    }
}
