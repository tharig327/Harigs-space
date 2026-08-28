package com.harigs.codeedit.editor

/**
 * Chooses how far to downscale an image while decoding it. A phone photo is
 * easily 4000 px wide; decoding one at full size to show it in a dialog wastes
 * tens of megabytes and risks running the app out of memory.
 */
object ImageScale {

    /**
     * The `inSampleSize` for BitmapFactory: the largest power of two that still
     * leaves the image at least as large as the target box.
     */
    fun sampleSize(sourceWidth: Int, sourceHeight: Int, targetWidth: Int, targetHeight: Int): Int {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1
        if (targetWidth <= 0 || targetHeight <= 0) return 1
        var sample = 1
        while (sourceWidth / (sample * 2) >= targetWidth && sourceHeight / (sample * 2) >= targetHeight) {
            sample *= 2
        }
        return sample
    }

    /** A human-readable file size for the preview caption. */
    fun formatBytes(bytes: Long): String = when {
        bytes < 0 -> "unknown size"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
