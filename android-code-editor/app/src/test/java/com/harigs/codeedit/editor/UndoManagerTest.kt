package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UndoManagerTest {

    private fun snapshot(text: String) = EditorSnapshot(text, text.length, text.length)

    @Test
    fun `nothing to undo on a fresh document`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        assertFalse(manager.canUndo)
        assertNull(manager.undo())
    }

    @Test
    fun `undo and redo walk the history`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("one"), now = 0)
        manager.record(snapshot("one two"), now = 5_000)

        assertTrue(manager.canUndo)
        assertEquals("one", manager.undo()?.text)
        assertEquals("", manager.undo()?.text)
        assertNull(manager.undo())

        assertEquals("one", manager.redo()?.text)
        assertEquals("one two", manager.redo()?.text)
        assertNull(manager.redo())
    }

    @Test
    fun `a new edit clears the redo stack`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("a"), now = 0)
        manager.record(snapshot("ab"), now = 10_000)
        manager.undo()
        assertTrue(manager.canRedo)

        manager.record(snapshot("ac"), now = 20_000)
        assertFalse(manager.canRedo)
    }

    @Test
    fun `selection only changes are not history entries`() {
        val manager = UndoManager()
        manager.reset(snapshot("text"))
        manager.record(EditorSnapshot("text", 0, 4), now = 0)
        assertFalse(manager.canUndo)
    }

    @Test
    fun `quick single character edits merge into one entry`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("h"), now = 0)
        manager.record(snapshot("he"), now = 100)
        manager.record(snapshot("hel"), now = 200)
        manager.record(snapshot("hell"), now = 300)
        manager.record(snapshot("hello"), now = 400)

        assertEquals("", manager.undo()?.text)
        assertFalse(manager.canUndo)
    }

    @Test
    fun `a pause starts a new entry`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("h"), now = 0)
        manager.record(snapshot("hi"), now = 100)
        manager.record(snapshot("hi "), now = 10_000)
        manager.record(snapshot("hi t"), now = 10_100)

        assertEquals("hi", manager.undo()?.text)
        assertEquals("", manager.undo()?.text)
    }

    @Test
    fun `edits are never merged across a line break`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("a"), now = 0)
        manager.record(snapshot("a\n"), now = 50)
        manager.record(snapshot("a\nb"), now = 100)

        assertEquals("a\n", manager.undo()?.text)
        assertEquals("a", manager.undo()?.text)
    }

    @Test
    fun `large pastes are always their own entry`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("a"), now = 0)
        manager.record(snapshot("a" + "x".repeat(50)), now = 20)

        assertEquals("a", manager.undo()?.text)
    }

    @Test
    fun `history is bounded`() {
        val manager = UndoManager(maxEntries = 5)
        manager.reset(snapshot(""))
        repeat(20) { index ->
            manager.record(snapshot("line $index\n"), now = index * 10_000L)
        }
        var steps = 0
        while (manager.undo() != null) steps++
        assertEquals(5, steps)
    }

    @Test
    fun `reset clears the history`() {
        val manager = UndoManager()
        manager.reset(snapshot(""))
        manager.record(snapshot("a"), now = 0)
        manager.reset(snapshot("fresh"))
        assertFalse(manager.canUndo)
        assertFalse(manager.canRedo)
    }
}
