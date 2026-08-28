package com.harigs.codeedit.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.documentfile.provider.DocumentFile
import com.harigs.codeedit.editor.ImageRef
import com.harigs.codeedit.editor.ImageRefs
import com.harigs.codeedit.editor.ImageScale
import java.io.ByteArrayInputStream
import java.io.InputStream

sealed interface ImageResult {
    data class Success(
        val bitmap: ImageBitmap,
        val pixelWidth: Int,
        val pixelHeight: Int,
        val sizeBytes: Long,
        val source: String,
    ) : ImageResult

    /** [needsFolder] marks the one failure the user can fix from the dialog. */
    data class Failure(val message: String, val needsFolder: Boolean = false) : ImageResult
}

/**
 * Turns an image reference into something drawable.
 *
 * A relative path such as `figures/fig1.jpg` cannot be opened on its own: the
 * Storage Access Framework grants access to the file that was opened, not to
 * its neighbours. Resolving one needs a folder the user has granted, which is
 * why previewing relative images asks for the containing folder.
 */
class ImageLoader(context: Context) {

    private val app = context.applicationContext

    /** Decodes [ref], fitting the result inside the given box in pixels. */
    fun load(ref: ImageRef, folder: Uri?, boxWidth: Int, boxHeight: Int): ImageResult {
        val target = ref.target

        return when {
            ImageRefs.isDataUri(target) -> {
                if (!ImageRefs.looksLikeImage(target)) {
                    return ImageResult.Failure("That data URI does not hold an image")
                }
                val bytes = ImageRefs.decodeDataUri(target)
                    ?: return ImageResult.Failure("The embedded image data could not be decoded")
                decode(
                    open = { ByteArrayInputStream(bytes) },
                    sizeBytes = bytes.size.toLong(),
                    source = "embedded data",
                    boxWidth = boxWidth,
                    boxHeight = boxHeight,
                )
            }

            ImageRefs.isRemote(target) -> ImageResult.Failure(
                "This image is on the web. The app has no internet access, so it " +
                    "can only show images stored on this device.",
            )

            ImageRefs.isAbsoluteLocal(target) -> {
                val uri = runCatching { Uri.parse(target) }.getOrNull()
                    ?: return ImageResult.Failure("That location could not be read")
                openUri(uri, target, boxWidth, boxHeight)
            }

            else -> {
                if (ImageRefs.escapesFolder(target)) {
                    return ImageResult.Failure(
                        "This path points outside the folder you opened, which " +
                            "Android does not allow the app to read.",
                    )
                }
                if (folder == null) {
                    return ImageResult.Failure(
                        "Open the folder that contains this document and its images " +
                            "will show up here.",
                        needsFolder = true,
                    )
                }
                val resolved = resolveInFolder(folder, target)
                    ?: return ImageResult.Failure(
                        "No file named \"$target\" in the folder you opened.",
                        needsFolder = true,
                    )
                openUri(resolved, target, boxWidth, boxHeight)
            }
        }
    }

    /** Walks a relative path down from a granted folder. */
    fun resolveInFolder(folder: Uri, target: String): Uri? {
        val segments = ImageRefs.normalizeRelativePath(target)
        if (segments.isEmpty() || ImageRefs.escapesFolder(target)) return null
        var current = runCatching { DocumentFile.fromTreeUri(app, folder) }.getOrNull() ?: return null
        segments.forEachIndexed { index, segment ->
            val child = current.findFile(segment) ?: return null
            val last = index == segments.lastIndex
            if (!last && !child.isDirectory) return null
            current = child
        }
        return if (current.isFile) current.uri else null
    }

    private fun openUri(uri: Uri, source: String, boxWidth: Int, boxHeight: Int): ImageResult {
        val size = runCatching { DocumentFile.fromSingleUri(app, uri)?.length() ?: -1L }
            .getOrDefault(-1L)
        return decode(
            open = { app.contentResolver.openInputStream(uri) },
            sizeBytes = size,
            source = source,
            boxWidth = boxWidth,
            boxHeight = boxHeight,
        )
    }

    /**
     * Decodes in two passes: the first reads only the dimensions so the second
     * can subsample, which keeps a large photo from allocating a full-size
     * bitmap just to be shown on a phone screen.
     */
    private fun decode(
        open: () -> InputStream?,
        sizeBytes: Long,
        source: String,
        boxWidth: Int,
        boxHeight: Int,
    ): ImageResult {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        try {
            open()?.use { BitmapFactory.decodeStream(it, null, bounds) }
                ?: return ImageResult.Failure("That image could not be opened")
        } catch (e: SecurityException) {
            return ImageResult.Failure("No permission to read that image")
        } catch (e: Exception) {
            return ImageResult.Failure(e.message ?: "That image could not be opened")
        }

        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return ImageResult.Failure("That file is not an image this device can read")
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = ImageScale.sampleSize(
                sourceWidth = bounds.outWidth,
                sourceHeight = bounds.outHeight,
                targetWidth = boxWidth,
                targetHeight = boxHeight,
            )
        }
        val bitmap = try {
            open()?.use { BitmapFactory.decodeStream(it, null, options) }
        } catch (e: OutOfMemoryError) {
            return ImageResult.Failure("That image is too large to display")
        } catch (e: Exception) {
            return ImageResult.Failure(e.message ?: "That image could not be decoded")
        } ?: return ImageResult.Failure("That image could not be decoded")

        return ImageResult.Success(
            bitmap = bitmap.asImageBitmap(),
            pixelWidth = bounds.outWidth,
            pixelHeight = bounds.outHeight,
            sizeBytes = sizeBytes,
            source = source,
        )
    }
}
