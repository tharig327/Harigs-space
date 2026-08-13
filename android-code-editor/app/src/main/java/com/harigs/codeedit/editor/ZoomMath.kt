package com.harigs.codeedit.editor

/**
 * Turns a continuous pinch gesture into whole font-size steps.
 *
 * The editor's font size is an integer number of sp, so a pinch cannot be
 * applied directly. The leftover scale is carried between events instead, which
 * keeps a slow pinch smooth and stops a fast one from skipping sizes.
 */
object ZoomMath {

    /** How far the fingers must spread before the size changes by one step. */
    const val STEP = 1.12f

    data class Result(val fontSize: Int, val remainingScale: Float)

    fun applyScale(currentSize: Int, scale: Float, range: IntRange): Result {
        if (!scale.isFinite() || scale <= 0f) return Result(currentSize, 1f)

        var size = currentSize.coerceIn(range)
        var remaining = scale

        while (remaining >= STEP && size < range.last) {
            size++
            remaining /= STEP
        }
        while (remaining <= 1f / STEP && size > range.first) {
            size--
            remaining *= STEP
        }

        // At either limit the leftover would otherwise grow without bound and
        // the size would jump the moment the pinch reversed.
        return Result(size, remaining.coerceIn(1f / STEP, STEP))
    }
}
