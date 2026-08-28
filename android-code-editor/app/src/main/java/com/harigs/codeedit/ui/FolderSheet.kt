package com.harigs.codeedit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harigs.codeedit.data.FolderEntry
import com.harigs.codeedit.editor.ImageScale

/**
 * Browses the granted folder. Opening a document from here also fixes the base
 * for its relative image paths, which is what makes previews work.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    folderName: String,
    trail: List<FolderEntry>,
    entries: List<FolderEntry>,
    loading: Boolean,
    onEnter: (FolderEntry) -> Unit,
    onUp: () -> Unit,
    onOpenFile: (FolderEntry) -> Unit,
    onChooseFolder: () -> Unit,
    onForget: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trail.isNotEmpty()) {
                    IconButton(onClick = onUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up one folder")
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = trail.lastOrNull()?.name ?: folderName.ifBlank { "Folder" },
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = (listOf(folderName) + trail.map { it.name }).joinToString(" / "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())

            if (!loading && entries.isEmpty()) {
                Text(
                    "This folder is empty.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }

            LazyColumn(Modifier.heightIn(max = 400.dp)) {
                items(entries, key = { it.uri.toString() }) { entry ->
                    val enabled = entry.isDirectory || entry.isEditable
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) {
                                if (entry.isDirectory) onEnter(entry) else onOpenFile(entry)
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = if (entry.isDirectory) {
                                Icons.Default.Folder
                            } else {
                                Icons.AutoMirrored.Filled.InsertDriveFile
                            },
                            contentDescription = null,
                            tint = if (enabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = entry.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (enabled) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.outline
                                },
                            )
                            if (!entry.isDirectory) {
                                Text(
                                    text = ImageScale.formatBytes(entry.sizeBytes) +
                                        if (!entry.isEditable) " · not a text file" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onChooseFolder) { Text("Change folder") }
                TextButton(onClick = onForget) { Text("Forget") }
            }
        }
    }
}
