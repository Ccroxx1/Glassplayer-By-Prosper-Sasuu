package com.example

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun ListeningStatsScreen(
    stats: AudioRepository.ListeningStats?,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Listening Stats", color = Color.White, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) {
                    Text("Refresh", color = GlassCyan)
                }
            }
        },
        text = {
            if (stats == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No stats yet. Refresh after playing tracks.", color = Color.White.copy(alpha = 0.7f))
                }
            } else {
                val totalHours = (stats.totalMs / 3_600_000f)
                val weeklyHours = (stats.weeklyMs / 3_600_000f)
                val gaugeProgress = (totalHours / 250f).coerceIn(0f, 1f)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                            .border(1.dp, GlassCyan.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Canvas(modifier = Modifier.size(120.dp)) {
                                val stroke = 12.dp.toPx()
                                drawArc(
                                    color = Color.White.copy(alpha = 0.15f),
                                    startAngle = 135f,
                                    sweepAngle = 270f,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = GlassCyan,
                                    startAngle = 135f,
                                    sweepAngle = 270f * gaugeProgress,
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Text(
                                text = "${totalHours.roundToInt()}h total",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = String.format("This week: %.1fh", weeklyHours),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Text("Top Artists", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 220.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(stats.topArtists) { (artist, plays) ->
                            val maxPlays = (stats.topArtists.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                            val widthFraction = plays.toFloat() / maxPlays.toFloat()
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Text(artist.ifBlank { "Unknown Artist" }, color = Color.White, fontSize = 13.sp)
                                Spacer(modifier = Modifier.height(5.dp))
                                Canvas(modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)) {
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = 0.12f),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                    )
                                    drawRoundRect(
                                        color = GlassMagenta,
                                        size = Size(size.width * widthFraction, size.height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text("$plays plays", color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        text = "7-day heatmap is represented by weekly hours summary in this build.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan.copy(alpha = 0.28f))
            ) {
                Text("Close", color = Color.White)
            }
        },
        containerColor = Color(0xFF0F1026)
    )
}
