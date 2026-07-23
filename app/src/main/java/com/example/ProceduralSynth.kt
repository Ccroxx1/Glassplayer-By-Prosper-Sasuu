package com.example

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class ProceduralSynth {
    private val sampleRate = 22050
    private var audioTrack: AudioTrack? = null
    private var synthJob: Job? = null
    private val synthScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    var isPlaying = false
        private set

    @Volatile
    private var isPaused = false

    var cutoff = 0.5f
    var resonance = 0.2f
    var speed = 1.0f
    var volume = 0.5f

    @Volatile
    var lastWaveformValue = 0f

    // Persistent sequencer state so pause/resume continues mid-phrase
    private var phaseBass = 0.0
    private var phasePad1 = 0.0
    private var phasePad2 = 0.0
    private var phasePad3 = 0.0
    private var phaseLead = 0.0
    private var currentChordIndex = 0
    private var step = 0
    private var sampleCount = 0
    private var filterState = 0.0
    private var seekSampleTarget: Long? = null

    private val progressions = listOf(
        listOf(110.0, 130.81, 164.81, 196.00),
        listOf(87.31, 130.81, 174.61, 220.00),
        listOf(130.81, 164.81, 196.00, 261.63),
        listOf(98.00, 146.83, 196.00, 246.94)
    )

    fun start() {
        if (isPlaying && !isPaused) return
        if (isPaused && audioTrack != null) {
            isPaused = false
            audioTrack?.play()
            return
        }

        isPlaying = true
        isPaused = false

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
        startGenerationLoop()
    }

    fun pause() {
        if (!isPlaying || isPaused) return
        isPaused = true
        try {
            audioTrack?.pause()
        } catch (_: Exception) {
        }
    }

    fun seekToMs(positionMs: Long) {
        val targetSample = ((positionMs / 1000.0) * sampleRate).toLong().coerceAtLeast(0)
        seekSampleTarget = targetSample
        // Fast-forward sequencer state without writing audio
        advanceToSample(targetSample)
        seekSampleTarget = null
    }

    fun positionMs(): Long = ((sampleCount.toDouble() / sampleRate) * 1000.0).toLong()

    fun stop() {
        isPlaying = false
        isPaused = false
        synthJob?.cancel()
        synthJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (_: Exception) {
        }
        audioTrack = null
        lastWaveformValue = 0f
        resetState()
    }

    private fun resetState() {
        phaseBass = 0.0
        phasePad1 = 0.0
        phasePad2 = 0.0
        phasePad3 = 0.0
        phaseLead = 0.0
        currentChordIndex = 0
        step = 0
        sampleCount = 0
        filterState = 0.0
    }

    private fun advanceToSample(targetSample: Long) {
        resetState()
        // Approximate seek by stepping the sequencer clock (skip oscillator phase for speed)
        val samplesPerStepBase = (sampleRate * 0.25f / speed.coerceAtLeast(0.1f)).toInt().coerceAtLeast(1000)
        val stepsAdvanced = (targetSample / samplesPerStepBase).toInt()
        step = stepsAdvanced
        sampleCount = targetSample.toInt()
        currentChordIndex = ((stepsAdvanced / 16) % progressions.size)
    }

    private fun startGenerationLoop() {
        synthJob?.cancel()
        synthJob = synthScope.launch {
            val bufferSize = 1024
            val buffer = ShortArray(bufferSize)

            while (isActive && isPlaying) {
                if (isPaused) {
                    kotlinx.coroutines.delay(40)
                    continue
                }

                val samplesPerStep = (sampleRate * 0.25f / speed).toInt().coerceAtLeast(1000)
                val currentChord = progressions[currentChordIndex]

                val bassFreq = currentChord[0] / 2.0
                val pad1Freq = currentChord[1]
                val pad2Freq = currentChord[2]
                val pad3Freq = currentChord[3]

                val leadNote = when (step % 8) {
                    0 -> currentChord[0] * 2.0
                    1 -> currentChord[1] * 2.0
                    2 -> currentChord[2] * 2.0
                    3 -> currentChord[3] * 2.0
                    4 -> currentChord[2] * 4.0
                    5 -> currentChord[1] * 4.0
                    6 -> currentChord[3] * 2.0
                    7 -> currentChord[0] * 3.0
                    else -> currentChord[0] * 2.0
                }

                for (i in 0 until bufferSize) {
                    phaseBass += (2.0 * Math.PI * bassFreq) / sampleRate
                    phasePad1 += (2.0 * Math.PI * pad1Freq) / sampleRate
                    phasePad2 += (2.0 * Math.PI * pad2Freq) / sampleRate
                    phasePad3 += (2.0 * Math.PI * pad3Freq) / sampleRate
                    phaseLead += (2.0 * Math.PI * leadNote) / sampleRate

                    if (phaseBass > 2.0 * Math.PI) phaseBass -= 2.0 * Math.PI
                    if (phasePad1 > 2.0 * Math.PI) phasePad1 -= 2.0 * Math.PI
                    if (phasePad2 > 2.0 * Math.PI) phasePad2 -= 2.0 * Math.PI
                    if (phasePad3 > 2.0 * Math.PI) phasePad3 -= 2.0 * Math.PI
                    if (phaseLead > 2.0 * Math.PI) phaseLead -= 2.0 * Math.PI

                    val bassVal = if (phaseBass < Math.PI) {
                        -1.0 + (2.0 * phaseBass / Math.PI)
                    } else {
                        3.0 - (2.0 * phaseBass / Math.PI)
                    } * 0.4

                    val padVal = (sin(phasePad1) + sin(phasePad2) + sin(phasePad3)) * 0.25
                    val stepSampleIndex = sampleCount % samplesPerStep
                    val envelope = 1.0 - (stepSampleIndex.toDouble() / samplesPerStep)
                    val leadSaw = (1.0 - (phaseLead / Math.PI)) * envelope * 0.25

                    var rawMixed = bassVal + padVal + leadSaw
                    val dynamicCutoff = (cutoff + 0.3 * sin(sampleCount.toDouble() * 0.0001)).coerceIn(0.05, 0.95)
                    filterState = filterState + dynamicCutoff * (rawMixed - filterState)
                    val finalVal = (filterState * volume).coerceIn(-1.0, 1.0)
                    buffer[i] = (finalVal * 32767.0).toInt().toShort()

                    if (i == 0) lastWaveformValue = finalVal.toFloat()

                    sampleCount++
                    if (sampleCount % samplesPerStep == 0) {
                        step++
                        if (step % 16 == 0) {
                            currentChordIndex = (currentChordIndex + 1) % progressions.size
                        }
                    }
                }

                audioTrack?.write(buffer, 0, bufferSize)
            }
        }
    }
}
