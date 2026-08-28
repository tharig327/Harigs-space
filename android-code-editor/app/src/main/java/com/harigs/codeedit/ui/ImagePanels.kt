package com.harigs.codeedit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harigs.codeedit.data.ImageResult
import com.harigs.codeedit.editor.ImageRef
import com.harigs.codeedit.editor.ImageScale

/** Loads an image reference at a pixel size; supplied by the view model. */
typealias ImageLoad = suspend (ImageRef, Int, Int) -> ImageResult

/** Shows one image full width, with what went wrong when it cannot be shown. */
@Composable
fun ImagePreviewDialog(
    ref: ImageRef,
    load: ImageLoad,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val boxWidth = with(density) { configuration.screenWidthDp.dp.toPx() }.toInt()
    val boxHeight = with(density) { configuration.screenHeightDp.dp.toPx() }.toInt()

    var result by remember(ref) { mutableStateOf<ImageResult?>(null) }
    LaunchedEffect(ref) { result = load(ref, boxWidth, boxHeight) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(ref.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val current = result) {
                    null -> Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    is ImageResult.Success -> {
                        Image(
                            bitmap = current.bitmap,
                            contentDescription = ref.label.ifBlank { ref.target },
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(
                            text = "${current.pixelWidth} × ${current.pixelHeight}" +
                                if (current.sizeBytes >= 0) {
                                    " · ${ImageScale.formatBytes(current.sizeBytes)}"
                                } else {
                                    ""
                                },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    is ImageResult.Failure -> {
                        Text(current.message, style = MaterialTheme.typography.bodyMedium)
                        if (current.needsFolder) {
                            TextButton(onClick = onChooseFolder) { Text("Choose folder…") }
                        }
                    }
                }
                Text(
                    text = ref.target,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

/** Every image in the document, with thumbnails, as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImagesSheet(
    refs: List<ImageRef>,
    load: ImageLoad,
    folderName: String,
    onOpen: (ImageRef) -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (refs.isEmpty()) "Images" else "Images (${refs.size})",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (folderName.isBlank()) {
                    "Relative paths need the document's folder. Choose it once and " +
                        "images will load from then on."
                } else {
                    "Resolving paths inside $folderName."
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (folderName.isBlank()) {
                TextButton(onClick = onChooseFolder) { Text("Choose folder…") }
            }

            if (refs.isEmpty()) {
                Text(
                    "No images referenced in this document.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(refs, key = { it.start }) { ref ->
                    ImageRow(ref = ref, load = load, onClick = { onOpen(ref) })
                }
            }
        }
    }
}

@Composable
private fun ImageRow(ref: ImageRef, load: ImageLoad, onClick: () -> Unit) {
    val density = LocalDensity.current
    val thumbPx = with(density) { 56.dp.toPx() }.toInt()
    // Only rows the list actually shows are decoded.
    val result by produceState<ImageResult?>(initialValue = null, ref) {
        value = load(ref, thumbPx, thumbPx)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            when (val current = result) {
                is ImageResult.Success -> Image(
                    bitmap = current.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp),
                )
                is ImageResult.Failure -> Text(
                    text = "!",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                null -> CircularProgressIndicator(Modifier.size(20.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = ref.displayName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = (result as? ImageResult.Failure)?.message ?: ref.target,
                style = MaterialTheme.typography.labelSmall,
                color = if (result is ImageResult.Failure) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
