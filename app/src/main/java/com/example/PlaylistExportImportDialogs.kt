package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties

/**
 * Modern Glassmorphic Dialog for choosing Playlist Export Format and filename.
 */
@Composable
fun ExportPlaylistDialog(
    state: AudioViewModel.ExportDialogState,
    onDismiss: () -> Unit,
    onConfirmExport: (format: ExportFormatOption, fileName: String) -> Unit,
    strings: LanguageStrings
) {
    var selectedFormat by remember { mutableStateOf(ExportFormatOption.PLAYLIST_ONLY) }
    var fileName by remember { mutableStateOf(state.suggestedName) }

    val isMulti = state.playlists.size > 1
    val titleText = if (isMulti) {
        "Export ${state.playlists.size} Playlists"
    } else {
        "Export Playlist: ${state.playlists.first().name}"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.FileDownload,
                    contentDescription = null,
                    tint = GlassCyan,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = titleText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Filename Input
                Column {
                    Text(
                        text = "File Name",
                        color = GlassCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = fileName,
                        onValueChange = { fileName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.White.copy(alpha = 0.05f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.03f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = GlassCyan,
                            unfocusedBorderColor = GlassBorderWhite
                        ),
                        trailingIcon = {
                            Text(
                                text = if (selectedFormat == ExportFormatOption.PLAYLIST_ONLY) ".glassplaylist" else ".zip",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Format Options
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Export Type",
                        color = GlassCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Option 1: Playlist Only (.glassplaylist)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedFormat == ExportFormatOption.PLAYLIST_ONLY)
                                    GlassCyan.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                1.dp,
                                if (selectedFormat == ExportFormatOption.PLAYLIST_ONLY)
                                    GlassCyan.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedFormat = ExportFormatOption.PLAYLIST_ONLY }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == ExportFormatOption.PLAYLIST_ONLY,
                            onClick = { selectedFormat = ExportFormatOption.PLAYLIST_ONLY },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GlassCyan,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.playlistOnlyOption,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Lightweight file with song metadata, order, ratings & online tracks. Ideal for backups and matching songs on another phone.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }

                    // Option 2: Playlist + Audio Files (.zip)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selectedFormat == ExportFormatOption.PLAYLIST_WITH_AUDIO)
                                    GlassMagenta.copy(alpha = 0.12f)
                                else Color.White.copy(alpha = 0.03f)
                            )
                            .border(
                                1.dp,
                                if (selectedFormat == ExportFormatOption.PLAYLIST_WITH_AUDIO)
                                    GlassMagenta.copy(alpha = 0.6f)
                                else Color.White.copy(alpha = 0.08f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedFormat = ExportFormatOption.PLAYLIST_WITH_AUDIO }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedFormat == ExportFormatOption.PLAYLIST_WITH_AUDIO,
                            onClick = { selectedFormat = ExportFormatOption.PLAYLIST_WITH_AUDIO },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = GlassMagenta,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.playlistWithAudioOption,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Packages the playlist manifest together with the actual local audio files into a ZIP archive for complete offline transfer.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val safeName = fileName.trim().ifBlank { state.suggestedName }
                    onConfirmExport(selectedFormat, safeName)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (selectedFormat == ExportFormatOption.PLAYLIST_ONLY) GlassCyan.copy(alpha = 0.8f) else GlassMagenta.copy(alpha = 0.8f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Export",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = strings.close, color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF111329),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(24.dp))
    )
}

/**
 * Interactive Preview & Conflict Management Dialog before restoring imported playlists.
 */
@Composable
fun ImportPlaylistPreviewDialog(
    preview: PlaylistImportPreview,
    onDismiss: () -> Unit,
    onConfirmImport: (PlaylistImportPreview) -> Unit,
    strings: LanguageStrings
) {
    var previewState by remember { mutableStateOf(preview) }
    var expandedPlaylistUuids by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(max = 680.dp)
            .border(1.dp, GlassBorderWhite, RoundedCornerShape(26.dp)),
        title = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (previewState.isZipPackage) Icons.Rounded.FolderZip else Icons.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = GlassCyan,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Import Playlists Preview",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${previewState.fileName} • ${previewState.playlistPreviews.size} playlist(s), ${previewState.totalTracks} total songs",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = previewState.playlistPreviews,
                    key = { it.playlist.uuid }
                ) { plPreview ->
                    val isExpanded = plPreview.playlist.uuid in expandedPlaylistUuids

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .border(
                                1.dp,
                                if (plPreview.hasConflict) Color(0xFFFFB86B).copy(alpha = 0.4f) else GlassBorderWhite,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp)
                    ) {
                        // Header row with checkbox and name
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = plPreview.selected,
                                onCheckedChange = { isChecked ->
                                    val updated = previewState.playlistPreviews.map {
                                        if (it.playlist.uuid == plPreview.playlist.uuid) it.copy(selected = isChecked) else it
                                    }
                                    previewState = previewState.copy(playlistPreviews = updated)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = GlassCyan,
                                    uncheckedColor = Color.White.copy(alpha = 0.4f)
                                )
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = plPreview.playlist.name,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "${plPreview.playlist.tracks.size} tracks (${plPreview.playlist.type})",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            IconButton(
                                onClick = {
                                    expandedPlaylistUuids = if (isExpanded) {
                                        expandedPlaylistUuids - plPreview.playlist.uuid
                                    } else {
                                        expandedPlaylistUuids + plPreview.playlist.uuid
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = GlassCyan
                                )
                            }
                        }

                        // Summary badges row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (plPreview.matchedCount > 0) {
                                StatusBadge(label = "${plPreview.matchedCount} matched", color = Color(0xFF4ECCA3))
                            }
                            if (plPreview.onlineCount > 0) {
                                StatusBadge(label = "${plPreview.onlineCount} online", color = GlassCyan)
                            }
                            if (plPreview.zipCount > 0) {
                                StatusBadge(label = "${plPreview.zipCount} in ZIP", color = GlassMagenta)
                            }
                            if (plPreview.unavailableCount > 0) {
                                StatusBadge(label = "${plPreview.unavailableCount} missing", color = Color(0xFFFFB86B))
                            }
                        }

                        // Conflict Resolution Section
                        if (plPreview.hasConflict) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFB86B).copy(alpha = 0.1f))
                                    .border(1.dp, Color(0xFFFFB86B).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                                    .padding(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.WarningAmber,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB86B),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = strings.conflictDetected,
                                        color = Color(0xFFFFB86B),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    ConflictStrategyButton(
                                        title = strings.createNewOption,
                                        isSelected = plPreview.conflictStrategy == ImportConflictStrategy.CREATE_NEW,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val updated = previewState.playlistPreviews.map {
                                            if (it.playlist.uuid == plPreview.playlist.uuid) it.copy(conflictStrategy = ImportConflictStrategy.CREATE_NEW) else it
                                        }
                                        previewState = previewState.copy(playlistPreviews = updated)
                                    }
                                    ConflictStrategyButton(
                                        title = strings.mergeOption,
                                        isSelected = plPreview.conflictStrategy == ImportConflictStrategy.MERGE,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val updated = previewState.playlistPreviews.map {
                                            if (it.playlist.uuid == plPreview.playlist.uuid) it.copy(conflictStrategy = ImportConflictStrategy.MERGE) else it
                                        }
                                        previewState = previewState.copy(playlistPreviews = updated)
                                    }
                                    ConflictStrategyButton(
                                        title = strings.replaceOption,
                                        isSelected = plPreview.conflictStrategy == ImportConflictStrategy.REPLACE,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val updated = previewState.playlistPreviews.map {
                                            if (it.playlist.uuid == plPreview.playlist.uuid) it.copy(conflictStrategy = ImportConflictStrategy.REPLACE) else it
                                        }
                                        previewState = previewState.copy(playlistPreviews = updated)
                                    }
                                }
                            }
                        }

                        // Expandable track list preview
                        AnimatedVisibility(
                            visible = isExpanded,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                plPreview.trackPreviews.forEachIndexed { idx, trackPreview ->
                                    TrackMatchPreviewRow(index = idx + 1, preview = trackPreview)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val selectedCount = previewState.playlistPreviews.count { it.selected }
            Button(
                onClick = { onConfirmImport(previewState) },
                enabled = selectedCount > 0,
                colors = ButtonDefaults.buttonColors(containerColor = GlassCyan),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Import ($selectedCount)",
                    color = Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = strings.close, color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF111329),
        shape = RoundedCornerShape(26.dp)
    )
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConflictStrategyButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) GlassCyan.copy(alpha = 0.25f) else Color.Transparent
        ),
        border = BorderStroke(
            1.dp,
            if (isSelected) GlassCyan else Color.White.copy(alpha = 0.2f)
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(
            text = title,
            color = if (isSelected) GlassCyan else Color.White.copy(alpha = 0.8f),
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun TrackMatchPreviewRow(
    index: Int,
    preview: TrackMatchPreview
) {
    val track = preview.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.02f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index.",
            color = Color.White.copy(alpha = 0.4f),
            fontSize = 11.sp,
            modifier = Modifier.width(24.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                color = Color.White,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.artist,
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        when (preview.matchStatus) {
            TrackMatchStatus.EXACT_URI, TrackMatchStatus.MATCHED_LOCAL_METADATA -> {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Matched",
                    tint = Color(0xFF4ECCA3),
                    modifier = Modifier.size(16.dp)
                )
            }
            TrackMatchStatus.ONLINE_STREAM -> {
                Icon(
                    imageVector = Icons.Rounded.CloudDone,
                    contentDescription = "Online",
                    tint = GlassCyan,
                    modifier = Modifier.size(16.dp)
                )
            }
            TrackMatchStatus.INCLUDED_IN_ZIP -> {
                Icon(
                    imageVector = Icons.Rounded.FolderZip,
                    contentDescription = "In ZIP",
                    tint = GlassMagenta,
                    modifier = Modifier.size(16.dp)
                )
            }
            TrackMatchStatus.UNAVAILABLE_LOCAL -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Unavailable",
                        color = Color(0xFFFFB86B),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.WarningAmber,
                        contentDescription = "Unavailable",
                        tint = Color(0xFFFFB86B),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

/**
 * Modern Glassmorphic Progress Dialog for background export and import operations.
 */
@Composable
fun BackupProgressDialog(
    progress: BackupProgress?
) {
    if (progress == null) return

    AlertDialog(
        onDismissRequest = {},
        confirmButton = {},
        title = {
            Text(
                text = "Processing...",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (progress.total > 0) {
                    LinearProgressIndicator(
                        progress = { progress.progressFraction },
                        color = GlassCyan,
                        trackColor = Color.White.copy(alpha = 0.1f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                } else {
                    CircularProgressIndicator(
                        color = GlassCyan,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = progress.message.ifBlank { "Please wait..." },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp
                )

                if (progress.currentItemName.isNotBlank()) {
                    Text(
                        text = progress.currentItemName,
                        color = GlassMagenta,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        containerColor = Color(0xFF111329),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.border(1.dp, GlassBorderWhite, RoundedCornerShape(20.dp))
    )
}
