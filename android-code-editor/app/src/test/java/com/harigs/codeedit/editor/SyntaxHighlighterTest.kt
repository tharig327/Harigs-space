package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlighterTest {

    private fun tokenText(source: String, token: Token) = source.substring(token.start, token.end)

    private fun firstOfType(source: String, language: Language, type: TokenType): String? =
        SyntaxHighlighter.tokenize(source, language)
            .firstOrNull { it.type == type }
            ?.let { tokenText(source, it) }

    @Test
    fun `plain text produces no tokens`() {
        assertTrue(SyntaxHighlighter.tokenize("fun main() {}", Language.PLAIN).isEmpty())
    }

    @Test
    fun `kotlin keywords strings and comments`() {
        val source = """
            // greet the world
            fun main() {
                val name = "world"
                println(name) /* trailing */
            }
        """.trimIndent()
        val tokens = SyntaxHighlighter.tokenize(source, Language.KOTLIN)

        assertEquals("// greet the world", firstOfType(source, Language.KOTLIN, TokenType.COMMENT))
        assertEquals("\"world\"", firstOfType(source, Language.KOTLIN, TokenType.STRING))
        assertEquals("fun", firstOfType(source, Language.KOTLIN, TokenType.KEYWORD))
        assertTrue(tokens.any { it.type == TokenType.FUNCTION && tokenText(source, it) == "main" })
        assertTrue(tokens.any { it.type == TokenType.COMMENT && tokenText(source, it) == "/* trailing */" })
    }

    @Test
    fun `comment markers inside a string are not comments`() {
        val source = """val url = "https://example.com/path" // real comment"""
        val comments = SyntaxHighlighter.tokenize(source, Language.KOTLIN)
            .filter { it.type == TokenType.COMMENT }
        assertEquals(1, comments.size)
        assertEquals("// real comment", tokenText(source, comments.first()))
    }

    @Test
    fun `escaped quote does not end a string`() {
        val source = """val s = "a \" b" + 1"""
        assertEquals("\"a \\\" b\"", firstOfType(source, Language.KOTLIN, TokenType.STRING))
        assertEquals("1", firstOfType(source, Language.KOTLIN, TokenType.NUMBER))
    }

    @Test
    fun `unterminated string stops at the line end`() {
        val source = "val s = \"oops\nval t = 2"
        val string = SyntaxHighlighter.tokenize(source, Language.KOTLIN)
            .first { it.type == TokenType.STRING }
        assertEquals("\"oops", tokenText(source, string))
    }

    @Test
    fun `numbers include hex and floating point forms`() {
        val source = "a = 0xFF; b = 1_000; c = 3.14e-2; d = 42L"
        val numbers = SyntaxHighlighter.tokenize(source, Language.KOTLIN)
            .filter { it.type == TokenType.NUMBER }
            .map { tokenText(source, it) }
        assertEquals(listOf("0xFF", "1_000", "3.14e-2", "42L"), numbers)
    }

    @Test
    fun `python triple quoted strings span lines`() {
        val source = "x = '''\nstill a string\n''' + 1"
        val string = SyntaxHighlighter.tokenize(source, Language.PYTHON)
            .first { it.type == TokenType.STRING }
        assertEquals("'''\nstill a string\n'''", tokenText(source, string))
    }

    @Test
    fun `python decorators are annotations`() {
        assertEquals("@property", firstOfType("@property\ndef x(): pass", Language.PYTHON, TokenType.ANNOTATION))
    }

    @Test
    fun `json keys and values are told apart`() {
        val source = """{"name": "value", "count": 3}"""
        val tokens = SyntaxHighlighter.tokenize(source, Language.JSON)
        val attributes = tokens.filter { it.type == TokenType.ATTRIBUTE }.map { tokenText(source, it) }
        val strings = tokens.filter { it.type == TokenType.STRING }.map { tokenText(source, it) }
        assertEquals(listOf("\"name\"", "\"count\""), attributes)
        assertEquals(listOf("\"value\""), strings)
    }

    @Test
    fun `xml tags attributes and comments`() {
        val source = """<!-- note --><a href="x.html" id='2'>text</a>"""
        val tokens = SyntaxHighlighter.tokenize(source, Language.XML)
        assertEquals("<!-- note -->", tokens.first { it.type == TokenType.COMMENT }.let { tokenText(source, it) })
        assertEquals(
            listOf("href", "id"),
            tokens.filter { it.type == TokenType.ATTRIBUTE }.map { tokenText(source, it) },
        )
        assertEquals(
            listOf("\"x.html\"", "'2'"),
            tokens.filter { it.type == TokenType.STRING }.map { tokenText(source, it) },
        )
        assertTrue(tokens.any { it.type == TokenType.TAG && tokenText(source, it) == "<a" })
        assertTrue(tokens.any { it.type == TokenType.TAG && tokenText(source, it) == "</a" })
    }

    @Test
    fun `a bare angle bracket is not a tag`() {
        val source = "3 < 5 and 6 > 2"
        assertTrue(SyntaxHighlighter.tokenize(source, Language.XML).none { it.type == TokenType.TAG })
    }

    @Test
    fun `markdown headings code fences and links`() {
        val source = """
            # Title
            Some *emphasis* and `code`.
            [link](https://example.com)
            ```
            fenced
            ```
        """.trimIndent()
        val tokens = SyntaxHighlighter.tokenize(source, Language.MARKDOWN)
        assertEquals("# Title", tokens.first { it.type == TokenType.HEADING }.let { tokenText(source, it) })
        assertEquals("*emphasis*", tokens.first { it.type == TokenType.EMPHASIS }.let { tokenText(source, it) })
        assertEquals(
            "[link](https://example.com)",
            tokens.first { it.type == TokenType.LINK }.let { tokenText(source, it) },
        )
        assertTrue(tokens.count { it.type == TokenType.CODE } >= 3)
    }

    @Test
    fun `tokens stay inside the text and are ordered`() {
        val source = """
            #include <stdio.h>
            int main(void) { /* start
            still a comment */ printf("hi %d\n", 42); return 0; }
        """.trimIndent()
        for (language in Language.entries) {
            val tokens = SyntaxHighlighter.tokenize(source, language)
            var previousEnd = 0
            for (token in tokens) {
                assertTrue("$language: negative range", token.start < token.end)
                assertTrue("$language: out of bounds", token.end <= source.length)
                assertTrue("$language: overlapping or unsorted", token.start >= previousEnd || language == Language.MARKDOWN)
                previousEnd = maxOf(previousEnd, token.end)
            }
        }
    }

    @Test
    fun `language is detected from the file name`() {
        assertEquals(Language.KOTLIN, Language.fromFileName("Main.kt"))
        assertEquals(Language.KOTLIN, Language.fromFileName("build.gradle.kts"))
        assertEquals(Language.PYTHON, Language.fromFileName("/storage/emulated/0/script.PY"))
        assertEquals(Language.SHELL, Language.fromFileName("Makefile"))
        assertEquals(Language.SHELL, Language.fromFileName(".bashrc"))
        assertEquals(Language.MARKDOWN, Language.fromFileName("README.md"))
        assertEquals(Language.PLAIN, Language.fromFileName("mystery"))
        assertEquals(Language.PLAIN, Language.fromFileName(null))
    }

    @Test
    fun `empty input is handled by every language`() {
        for (language in Language.entries) {
            assertNotNull(SyntaxHighlighter.tokenize("", language))
            assertTrue(SyntaxHighlighter.tokenize("", language).isEmpty())
        }
    }

    @Test
    fun `plain language has no line comment marker`() {
        assertNull(Language.PLAIN.lineComment)
    }
}
