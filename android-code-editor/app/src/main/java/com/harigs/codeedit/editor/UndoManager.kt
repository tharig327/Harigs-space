package com.harigs.codeedit.editor

/** A point-in-time state of the document, used for undo and redo. */
data class EditorSnapshot(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int,
)

/**
 * A bounded undo/redo history.
 *
 * Consecutive single-character edits made in quick succession are merged into
 * one entry so that undo steps back by a word rather than by a keystroke.
 */
class UndoManager(
    private val maxEntries: Int = 200,
    private val mergeWindowMillis: Long = 700L,
) {
    private val undoStack = ArrayDeque<EditorSnapshot>()
    private val redoStack = ArrayDeque<EditorSnapshot>()

    private var current: EditorSnapshot = EditorSnapshot("", 0, 0)
    private var lastPushTime = 0L
    private var canMergeNext = false

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Discards all history and restarts from [snapshot] (used when a file is opened). */
    fun reset(snapshot: EditorSnapshot) {
        undoStack.clear()
        redoStack.clear()
        current = snapshot
        lastPushTime = 0L
        canMergeNext = false
    }

    /**
     * Records an edit. Selection-only changes are tracked but never create a
     * history entry, so undo always changes the text.
     */
    fun record(next: EditorSnapshot, now: Long = System.currentTimeMillis()) {
        if (next.text == current.text) {
            current = next
            return
        }
        val small = isSmallEdit(current.text, next.text)
        if (small && canMergeNext && undoStack.isNotEmpty() &&
            now - lastPushTime <= mergeWindowMillis
        ) {
            current = next
            lastPushTime = now
            return
        }
        undoStack.addLast(current)
        if (undoStack.size > maxEntries) undoStack.removeFirst()
        redoStack.clear()
        current = next
        lastPushTime = now
        // Only ordinary typing may absorb the next edit: a pasted block or a
        // line break always closes the entry it belongs to.
        canMergeNext = small
    }

    /** Steps back one entry, or returns null when there is nothing to undo. */
    fun undo(): EditorSnapshot? {
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(current)
        current = previous
        canMergeNext = false
        return previous
    }

    /** Steps forward one entry, or returns null when there is nothing to redo. */
    fun redo(): EditorSnapshot? {
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(current)
        current = next
        canMergeNext = false
        return next
    }

    /** True for a one-character edit that is not a line break. */
    private fun isSmallEdit(before: String, after: String): Boolean {
        val delta = after.length - before.length
        if (delta != 1 && delta != -1) return false
        val changed = if (delta == 1) after else before
        val index = firstDifference(before, after)
        return index >= 0 && changed.getOrNull(index) != '\n'
    }

    private fun firstDifference(a: String, b: String): Int {
        val limit = minOf(a.length, b.length)
        for (i in 0 until limit) if (a[i] != b[i]) return i
        return if (a.length == b.length) -1 else limit
    }
}
