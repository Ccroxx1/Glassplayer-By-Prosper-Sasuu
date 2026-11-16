package com.example

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

enum class SortMode(val label: String) {
    Title("Title"),
    ArtistTitle("Artist / Title"),
    ArtistAlbum("Artist / Album"),
    Album("Album"),
    Duration("Duration"),
    DateModified("Date Modified"),
    DateAdded("Date Added"),
    ArtistYear("Artist / Year"),
    Year("Year")
}

fun List<AudioTrackEntity>.sortedByMode(mode: SortMode, ascending: Boolean): List<AudioTrackEntity> {
    fun str(value: String) = value.lowercase(Locale.ROOT)
    val comparator: Comparator<AudioTrackEntity> = when (mode) {
        SortMode.Title -> compareBy { str(it.title) }
        SortMode.ArtistTitle -> compareBy({ str(it.artist) }, { str(it.title) })
        SortMode.ArtistAlbum -> compareBy({ str(it.artist) }, { str(it.album) })
        SortMode.Album -> compareBy { str(it.album) }
        SortMode.Duration -> compareBy({ it.durationMs }, { str(it.title) })
        SortMode.DateModified -> compareBy({ it.dateModified }, { str(it.title) })
        SortMode.DateAdded -> compareBy({ it.dateAdded }, { str(it.title) })
        SortMode.ArtistYear -> compareBy({ str(it.artist) }, { it.year }, { str(it.title) })
        SortMode.Year -> compareBy({ it.year }, { str(it.title) })
    }
    val sorted = sortedWith(comparator)
    return if (ascending) sorted else sorted.asReversed()
}

@Composable
fun SortTracksDialog(
    currentMode: SortMode,
    currentAscending: Boolean,
    onDismiss: () -> Unit,
    onApply: (SortMode, Boolean) -> Unit
) {
    var selectedMode by remember(currentMode) { mutableStateOf(currentMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Sort Options",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                SortMode.entries.forEach { mode ->
                    val selected = selectedMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected,
                            onClick = { selectedMode = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GlassCyan,
                                unselectedColor = Color.White.copy(alpha = 0.45f)
                            )
                        )
                        Text(
                            text = mode.label,
                            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
                            fontSize = 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { onApply(selectedMode, false) }
                ) {
                    Text(
                        text = "DESCENDING",
                        color = if (!currentAscending && selectedMode == currentMode) GlassCyan else GlassCyan.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 13.sp
                    )
                }
                TextButton(
                    onClick = { onApply(selectedMode, true) }
                ) {
                    Text(
                        text = "ASCENDING",
                        color = if (currentAscending && selectedMode == currentMode) GlassCyan else GlassCyan.copy(alpha = 0.85f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        fontSize = 13.sp
                    )
                }
            }
        },
        containerColor = Color(0xFF0F1026),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(28.dp))
    )
}

fun sortModeFromPrefs(name: String?): SortMode =
    SortMode.entries.firstOrNull { it.name == name } ?: SortMode.Title
