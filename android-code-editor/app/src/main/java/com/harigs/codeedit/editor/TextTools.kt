package com.harigs.codeedit.editor

/**
 * Text manipulations shared by the editor UI. Everything here is pure so it can
 * be unit tested without an Android device.
 */
object TextTools {

    /** The offset at which the line containing [offset] starts. */
    fun lineStart(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length) - 1
        while (i >= 0) {
            if (text[i] == '\n') return i + 1
            i--
        }
        return 0
    }

    /** The offset just before the line terminator of the line containing [offset]. */
    fun lineEnd(text: CharSequence, offset: Int): Int {
        var i = offset.coerceIn(0, text.length)
        while (i < text.length && text[i] != '\n') i++
        return i
    }

    /** 1-based line and column of [offset], for the status bar. */
    fun lineAndColumn(text: CharSequence, offset: Int): Pair<Int, Int> {
        val end = offset.coerceIn(0, text.length)
        var line = 1
        var lastBreak = -1
        for (i in 0 until end) {
            if (text[i] == '\n') {
                line++
                lastBreak = i
            }
        }
        return line to (end - lastBreak)
    }

    /** The leading whitespace of the line containing [offset]. */
    fun indentAt(text: CharSequence, offset: Int): String {
        val start = lineStart(text, offset)
        var i = start
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return text.substring(start, i)
    }

    /**
     * The text to insert for a newline at [offset]: a line break plus the
     * current indentation, deepened by one level after an opening brace.
     */
    fun newlineInsertion(text: CharSequence, offset: Int, indentUnit: String): String {
        val indent = indentAt(text, offset)
        val previous = text.take(offset).lastOrNull { !it.isWhitespace() }
        val deeper = previous == '{' || previous == '[' || previous == '(' || previous == ':'
        return "\n" + indent + if (deeper) indentUnit else ""
    }

    /** The closing character that should be auto-inserted after [opening], if any. */
    fun closingFor(opening: Char): Char? = when (opening) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '"' -> '"'
        '\'' -> '\''
        '`' -> '`'
        else -> null
    }

    /**
     * Indents ([add] = true) or unindents every line touched by the selection
     * `[selectionStart, selectionEnd)`. Returns the new text together with the
     * selection that should be restored, so a block stays selected after the
     * shift.
     */
    fun shiftIndent(
        text: String,
        selectionStart: Int,
        selectionEnd: Int,
        indentUnit: String,
        add: Boolean,
    ): IndentResult {
        val from = lineStart(text, minOf(selectionStart, selectionEnd))
        val to = lineEnd(text, maxOf(selectionStart, selectionEnd))
        val block = text.substring(from, to)
        val lines = block.split("\n")

        var firstDelta = 0
        var totalDelta = 0
        val shifted = lines.mapIndexed { index, line ->
            if (add) {
                if (line.isEmpty() && lines.size > 1) {
                    line
                } else {
                    if (index == 0) firstDelta = indentUnit.length
                    totalDelta += indentUnit.length
                    indentUnit + line
                }
            } else {
                val removed = removedPrefixLength(line, indentUnit)
                if (index == 0) firstDelta = -removed
                totalDelta -= removed
                line.substring(removed)
            }
        }

        val newText = text.substring(0, from) + shifted.joinToString("\n") + text.substring(to)
        return IndentResult(
            text = newText,
            selectionStart = (minOf(selectionStart, selectionEnd) + firstDelta).coerceAtLeast(from),
            selectionEnd = (maxOf(selectionStart, selectionEnd) + totalDelta).coerceAtLeast(from),
        )
    }

    data class IndentResult(val text: String, val selectionStart: Int, val selectionEnd: Int)

    private fun removedPrefixLength(line: String, indentUnit: String): Int {
        if (line.startsWith(indentUnit)) return indentUnit.length
        if (line.startsWith("\t")) return 1
        var spaces = 0
        while (spaces < line.length && spaces < indentUnit.length && line[spaces] == ' ') spaces++
        return spaces
    }

    /** All non-overlapping matches of [query] in [text]. */
    fun findAll(
        text: CharSequence,
        query: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false,
    ): List<IntRange> {
        if (query.isEmpty()) return emptyList()
        val matches = ArrayList<IntRange>()
        var index = 0
        while (index <= text.length - query.length) {
            val found = text.indexOf(query, index, ignoreCase)
            if (found < 0) break
            val end = found + query.length
            if (!wholeWord || isWholeWord(text, found, end)) {
                matches += found until end
            }
            index = found + 1
        }
        return matches
    }

    private fun isWholeWord(text: CharSequence, start: Int, end: Int): Boolean {
        val before = text.getOrNull(start - 1)
        val after = text.getOrNull(end)
        return (before == null || !before.isLetterOrDigit() && before != '_') &&
            (after == null || !after.isLetterOrDigit() && after != '_')
    }

    /** Replaces every match of [query] with [replacement]. */
    fun replaceAll(
        text: String,
        query: String,
        replacement: String,
        ignoreCase: Boolean = true,
        wholeWord: Boolean = false,
    ): String {
        val matches = findAll(text, query, ignoreCase, wholeWord)
        if (matches.isEmpty()) return text
        val builder = StringBuilder(text.length)
        var cursor = 0
        for (match in matches) {
            if (match.first < cursor) continue // overlapping match, already consumed
            builder.append(text, cursor, match.first)
            builder.append(replacement)
            cursor = match.last + 1
        }
        builder.append(text, cursor, text.length)
        return builder.toString()
    }

    /** Normalises Windows and classic Mac line endings so editing behaves predictably. */
    fun normalizeLineEndings(text: String): String =
        if (text.indexOf('\r') < 0) text else text.replace("\r\n", "\n").replace('\r', '\n')

    /** The number of lines in [text] (an empty document still has one line). */
    fun lineCount(text: CharSequence): Int {
        var count = 1
        for (element in text) if (element == '\n') count++
        return count
    }
}
