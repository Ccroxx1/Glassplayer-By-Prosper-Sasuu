package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.flow.StateFlow

/**
 * A custom waveform seek bar that visualises a set of pre-sampled amplitude bars,
 * a glowing playhead line, and support for tap/drag seeking.
 *
 * @param amplitudes  Live amplitude bars from [PlayerEngine._waveformAmplitudes] (24 values, 0..1)
 * @param positionMs  Current playback position in milliseconds
 * @param durationMs  Total track duration in milliseconds
 * @param onSeek      Called with the seeked position in milliseconds when user taps or drags
 * @param height      Height of the waveform bar area
 */
@Composable
fun WaveformSeekBar(
    amplitudes: List<Float>,
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 56.dp,
    barColor: Color = GlassCyan,
    playedColor: Color = GlassCyan,
    unplayedColor: Color = Color.White.copy(alpha = 0.18f),
    playheadColor: Color = Color.White
) {
    val density = LocalDensity.current

    // Normalise position to 0..1 fraction
    val progress = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f

    var dragProgress by remember { mutableFloatStateOf(-1f) }
    var componentWidth by remember { mutableFloatStateOf(1f) }

    // Waveform gradient — played portion glows cyan→purple, unplayed is dim
    val playedBrush = Brush.verticalGradient(
        colors = listOf(GlassCyan.copy(alpha = 0.9f), GlassPurple.copy(alpha = 0.7f))
    )
    val unplayedBrush = Brush.verticalGradient(
        colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.06f))
    )

    Column(modifier = modifier) {
        // Waveform canvas
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(durationMs) {
                    detectTapGestures(
                        onTap = { offset ->
                            val frac = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek((frac * durationMs).toLong())
                        }
                    )
                }
                .pointerInput(durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            dragProgress = (offset.x / size.width).coerceIn(0f, 1f)
                            componentWidth = size.width.toFloat()
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            dragProgress =
                                (dragProgress + dragAmount / componentWidth).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            if (dragProgress >= 0f) {
                                onSeek((dragProgress * durationMs).toLong())
                                dragProgress = -1f
                            }
                        },
                        onDragCancel = { dragProgress = -1f }
                    )
                }
        ) {
            val displayProgress = if (dragProgress >= 0f) dragProgress else progress
            drawWaveformBars(
                amplitudes = amplitudes,
                progress = displayProgress,
                playedBrush = playedBrush,
                unplayedBrush = unplayedBrush,
                playheadColor = playheadColor
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Time labels
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatWaveformTime(positionMs),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp
            )
            Text(
                text = formatWaveformTime(durationMs),
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )
        }
    }
}

private fun formatWaveformTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun DrawScope.drawWaveformBars(
    amplitudes: List<Float>,
    progress: Float,
    playedBrush: Brush,
    unplayedBrush: Brush,
    playheadColor: Color
) {
    if (amplitudes.isEmpty()) return
    val barCount = amplitudes.size
    val totalWidth = size.width
    val totalHeight = size.height
    val gap = (totalWidth * 0.015f).coerceAtLeast(1.5f)
    val barWidth = ((totalWidth - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
    val playheadX = totalWidth * progress

    amplitudes.forEachIndexed { i, amp ->
        val barLeft = i * (barWidth + gap)
        val barRight = barLeft + barWidth
        val barHeight = (amp.coerceIn(0.05f, 1f) * totalHeight)
        val barTop = (totalHeight - barHeight) / 2f
        val isPlayed = barLeft < playheadX

        // Clip canvas paint to bar rect for gradient effect
        val paint = if (isPlayed) {
            Paint().also {
                playedBrush.applyTo(
                    Size(barWidth, barHeight),
                    it,
                    1f
                )
            }
        } else {
            Paint().also {
                unplayedBrush.applyTo(
                    Size(barWidth, barHeight),
                    it,
                    1f
                )
            }
        }
        drawRect(
            brush = if (isPlayed) playedBrush else unplayedBrush,
            topLeft = Offset(barLeft, barTop),
            size = Size(barWidth, barHeight)
        )
    }

    // Glowing playhead scrubber line
    if (progress > 0f && progress < 1f) {
        // Soft glow
        drawLine(
            color = playheadColor.copy(alpha = 0.22f),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, totalHeight),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )
        // Sharp edge
        drawLine(
            color = playheadColor.copy(alpha = 0.85f),
            start = Offset(playheadX, 0f),
            end = Offset(playheadX, totalHeight),
            strokeWidth = 2.5f,
            cap = StrokeCap.Round
        )
        // Playhead knob circle
        drawCircle(
            color = playheadColor,
            radius = 6f,
            center = Offset(playheadX, totalHeight / 2f)
        )
    }
}
