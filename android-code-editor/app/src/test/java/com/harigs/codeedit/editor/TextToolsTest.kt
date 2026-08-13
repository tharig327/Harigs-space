package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolsTest {

    @Test
    fun `line and column are one based`() {
        val text = "abc\nde\n"
        assertEquals(1 to 1, TextTools.lineAndColumn(text, 0))
        assertEquals(1 to 4, TextTools.lineAndColumn(text, 3))
        assertEquals(2 to 1, TextTools.lineAndColumn(text, 4))
        assertEquals(3 to 1, TextTools.lineAndColumn(text, 7))
    }

    @Test
    fun `line count counts the trailing empty line`() {
        assertEquals(1, TextTools.lineCount(""))
        assertEquals(1, TextTools.lineCount("one"))
        assertEquals(2, TextTools.lineCount("one\n"))
        assertEquals(3, TextTools.lineCount("one\ntwo\nthree"))
    }

    @Test
    fun `indent is taken from the current line`() {
        val text = "fun a() {\n    val x = 1\n}"
        assertEquals("    ", TextTools.indentAt(text, text.indexOf("val")))
        assertEquals("", TextTools.indentAt(text, 0))
    }

    @Test
    fun `new lines keep indentation and deepen after a brace`() {
        val text = "    if (x) {"
        assertEquals("\n        ", TextTools.newlineInsertion(text, text.length, "    "))

        val flat = "    doThing()"
        assertEquals("\n    ", TextTools.newlineInsertion(flat, flat.length, "    "))
    }

    @Test
    fun `indenting shifts every touched line`() {
        val text = "a\nb\nc"
        val result = TextTools.shiftIndent(text, 0, 3, "  ", add = true)
        assertEquals("  a\n  b\nc", result.text)
        assertEquals(2, result.selectionStart)
        assertEquals(7, result.selectionEnd)
    }

    @Test
    fun `unindenting removes one level and stops at zero`() {
        val indented = "    a\n\tb\nc"
        val once = TextTools.shiftIndent(indented, 0, indented.length, "    ", add = false)
        assertEquals("a\nb\nc", once.text)

        val again = TextTools.shiftIndent(once.text, 0, once.text.length, "    ", add = false)
        assertEquals("a\nb\nc", again.text)
    }

    @Test
    fun `find returns every match and honours case sensitivity`() {
        val text = "Cat cat CAT"
        assertEquals(3, TextTools.findAll(text, "cat").size)
        assertEquals(1, TextTools.findAll(text, "cat", ignoreCase = false).size)
        assertEquals(listOf(4 until 7), TextTools.findAll(text, "cat", ignoreCase = false))
    }

    @Test
    fun `whole word search skips substrings`() {
        val text = "cat concatenate cat_x cat"
        val matches = TextTools.findAll(text, "cat", wholeWord = true)
        assertEquals(listOf(0 until 3, 22 until 25), matches)
    }

    @Test
    fun `empty query finds nothing`() {
        assertTrue(TextTools.findAll("anything", "").isEmpty())
    }

    @Test
    fun `replace all rewrites every match`() {
        assertEquals("dog dog", TextTools.replaceAll("cat Cat", "cat", "dog"))
        assertEquals("dog Cat", TextTools.replaceAll("cat Cat", "cat", "dog", ignoreCase = false))
    }

    @Test
    fun `replace all consumes overlapping candidates left to right`() {
        assertEquals("ba", TextTools.replaceAll("aaa", "aa", "b"))
    }

    @Test
    fun `replacement containing the query does not loop`() {
        assertEquals("xaax", TextTools.replaceAll("aa", "aa", "xaax"))
    }

    @Test
    fun `line endings are normalised`() {
        assertEquals("a\nb\nc", TextTools.normalizeLineEndings("a\r\nb\rc"))
        val plain = "already\nfine"
        assertTrue(plain === TextTools.normalizeLineEndings(plain))
    }

    @Test
    fun `closing characters are known for the usual pairs`() {
        assertEquals(')', TextTools.closingFor('('))
        assertEquals('}', TextTools.closingFor('{'))
        assertEquals('"', TextTools.closingFor('"'))
        assertEquals(null, TextTools.closingFor('a'))
    }
}
