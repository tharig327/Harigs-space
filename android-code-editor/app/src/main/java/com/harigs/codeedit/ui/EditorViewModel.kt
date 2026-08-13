package com.harigs.codeedit.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.harigs.codeedit.data.DocumentStore
import com.harigs.codeedit.data.EditorPreferences
import com.harigs.codeedit.data.EditorSettings
import com.harigs.codeedit.data.LoadResult
import com.harigs.codeedit.data.RecentFile
import com.harigs.codeedit.data.RecentFiles
import com.harigs.codeedit.editor.EditorSnapshot
import com.harigs.codeedit.editor.Language
import com.harigs.codeedit.editor.TextTools
import com.harigs.codeedit.editor.UndoManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Everything the editor screen needs to draw itself, apart from the text. */
data class EditorUiState(
    val uri: Uri? = null,
    val fileName: String = UNTITLED,
    val language: Language = Language.PLAIN,
    val isDirty: Boolean = false,
    val isBusy: Boolean = false,
    val crlf: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val findVisible: Boolean = false,
    val query: String = "",
    val replacement: String = "",
    val matchCase: Boolean = false,
    val matches: List<IntRange> = emptyList(),
    val currentMatch: Int = -1,
    val preferences: EditorPreferences = EditorPreferences(),
    val recents: List<RecentFile> = emptyList(),
    val message: String? = null,
) {
    val hasFile: Boolean get() = uri != null

    companion object {
        const val UNTITLED = "Untitled"
    }
}

class EditorViewModel(application: Application) : AndroidViewModel(application) {

    private val store = DocumentStore(application)
    private val settings = EditorSettings(application)
    private val recentFiles = RecentFiles(application)
    private val undoManager = UndoManager()

    /** Text is kept apart from [ui] so typing only invalidates the text field. */
    var content by mutableStateOf(TextFieldValue(""))
        private set

    var ui by mutableStateOf(EditorUiState())
        private set

    private var savedText: String = ""

    /** Set once the user picks a language by hand, so saving cannot override it. */
    private var languageChosenByUser = false

    init {
        ui = ui.copy(preferences = settings.load(), recents = recentFiles.list())
        undoManager.reset(EditorSnapshot("", 0, 0))
    }

    // ------------------------------------------------------------- editing --

    fun onTextChange(value: TextFieldValue) {
        val previous = content
        val assisted = if (previous.text == value.text) {
            value
        } else {
            applyTypingAssist(previous, value, ui.preferences.indentUnit)
        }

        content = assisted
        undoManager.record(assisted.toSnapshot())
        refreshAfterEdit(textChanged = previous.text != assisted.text)
    }

    fun undo() = restore(undoManager.undo())

    fun redo() = restore(undoManager.redo())

    private fun restore(snapshot: EditorSnapshot?) {
        if (snapshot == null) return
        content = snapshot.toValue()
        refreshAfterEdit(textChanged = true)
    }

    /** Replaces the selection with [text], leaving the cursor after it. */
    fun insert(text: String) {
        val current = content
        val start = current.selection.min
        val end = current.selection.max
        val updated = current.text.substring(0, start) + text + current.text.substring(end)
        content = TextFieldValue(updated, TextRange(start + text.length))
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
    }

    /** Wraps the selection in [prefix]/[suffix], or inserts the pair at the cursor. */
    fun surround(prefix: String, suffix: String) {
        val current = content
        val start = current.selection.min
        val end = current.selection.max
        val selected = current.text.substring(start, end)
        val updated = current.text.substring(0, start) + prefix + selected + suffix +
            current.text.substring(end)
        val caret = if (selected.isEmpty()) start + prefix.length else start + prefix.length + selected.length + suffix.length
        content = TextFieldValue(updated, TextRange(caret))
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
    }

    fun shiftIndent(add: Boolean) {
        val current = content
        val result = TextTools.shiftIndent(
            text = current.text,
            selectionStart = current.selection.min,
            selectionEnd = current.selection.max,
            indentUnit = ui.preferences.indentUnit,
            add = add,
        )
        content = TextFieldValue(
            text = result.text,
            selection = TextRange(
                result.selectionStart.coerceIn(0, result.text.length),
                result.selectionEnd.coerceIn(0, result.text.length),
            ),
        )
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
    }

    /** Toggles the line comment for every line the selection touches. */
    fun toggleComment() {
        val marker = ui.language.lineComment ?: run {
            ui = ui.copy(message = "${ui.language.displayName} has no line comment")
            return
        }
        val current = content
        val text = current.text
        val from = TextTools.lineStart(text, current.selection.min)
        val to = TextTools.lineEnd(text, current.selection.max)
        val lines = text.substring(from, to).split("\n")
        val meaningful = lines.filter { it.isNotBlank() }
        val allCommented = meaningful.isNotEmpty() && meaningful.all { it.trimStart().startsWith(marker) }

        val updated = lines.joinToString("\n") { line ->
            when {
                line.isBlank() -> line
                allCommented -> {
                    val indent = line.takeWhile { it == ' ' || it == '\t' }
                    val rest = line.substring(indent.length).removePrefix(marker).removePrefix(" ")
                    indent + rest
                }
                else -> {
                    val indent = line.takeWhile { it == ' ' || it == '\t' }
                    indent + marker + " " + line.substring(indent.length)
                }
            }
        }
        val newText = text.substring(0, from) + updated + text.substring(to)
        content = TextFieldValue(newText, TextRange((from + updated.length).coerceAtMost(newText.length)))
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
    }

    private fun refreshAfterEdit(textChanged: Boolean) {
        ui = ui.copy(
            isDirty = content.text != savedText,
            canUndo = undoManager.canUndo,
            canRedo = undoManager.canRedo,
        )
        if (textChanged && ui.findVisible) recomputeMatches(moveToNearest = false)
    }

    // ---------------------------------------------------------------- find --

    fun setFindVisible(visible: Boolean) {
        ui = ui.copy(findVisible = visible)
        if (visible) recomputeMatches(moveToNearest = true) else ui = ui.copy(matches = emptyList(), currentMatch = -1)
    }

    fun setQuery(query: String) {
        ui = ui.copy(query = query)
        recomputeMatches(moveToNearest = true)
    }

    fun setReplacement(replacement: String) {
        ui = ui.copy(replacement = replacement)
    }

    fun setMatchCase(matchCase: Boolean) {
        ui = ui.copy(matchCase = matchCase)
        recomputeMatches(moveToNearest = true)
    }

    fun findNext(forward: Boolean = true) {
        val matches = ui.matches
        if (matches.isEmpty()) return
        val next = if (forward) {
            (ui.currentMatch + 1).mod(matches.size)
        } else {
            (ui.currentMatch - 1).mod(matches.size)
        }
        selectMatch(next)
    }

    fun replaceCurrent() {
        val index = ui.currentMatch
        val match = ui.matches.getOrNull(index) ?: return
        val text = content.text
        val updated = text.substring(0, match.first) + ui.replacement + text.substring(match.last + 1)
        content = TextFieldValue(updated, TextRange(match.first + ui.replacement.length))
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
        recomputeMatches(moveToNearest = true)
    }

    fun replaceAll() {
        if (ui.query.isEmpty()) return
        val count = ui.matches.size
        val updated = TextTools.replaceAll(
            text = content.text,
            query = ui.query,
            replacement = ui.replacement,
            ignoreCase = !ui.matchCase,
        )
        content = TextFieldValue(updated, TextRange(content.selection.min.coerceAtMost(updated.length)))
        undoManager.record(content.toSnapshot())
        refreshAfterEdit(textChanged = true)
        recomputeMatches(moveToNearest = false)
        ui = ui.copy(message = "Replaced $count ${if (count == 1) "match" else "matches"}")
    }

    private fun recomputeMatches(moveToNearest: Boolean) {
        val matches = TextTools.findAll(
            text = content.text,
            query = ui.query,
            ignoreCase = !ui.matchCase,
        )
        ui = ui.copy(matches = matches)
        if (matches.isEmpty()) {
            ui = ui.copy(currentMatch = -1)
            return
        }
        val index = if (moveToNearest) {
            val caret = content.selection.min
            matches.indexOfFirst { it.first >= caret }.takeIf { it >= 0 } ?: 0
        } else {
            ui.currentMatch.coerceIn(0, matches.size - 1)
        }
        ui = ui.copy(currentMatch = index)
    }

    private fun selectMatch(index: Int) {
        val match = ui.matches.getOrNull(index) ?: return
        ui = ui.copy(currentMatch = index)
        content = content.copy(selection = TextRange(match.first, match.last + 1))
    }

    // ----------------------------------------------------------- documents --

    fun newDocument() {
        content = TextFieldValue("")
        savedText = ""
        languageChosenByUser = false
        undoManager.reset(EditorSnapshot("", 0, 0))
        ui = ui.copy(
            uri = null,
            fileName = EditorUiState.UNTITLED,
            language = Language.PLAIN,
            isDirty = false,
            crlf = false,
            canUndo = false,
            canRedo = false,
            matches = emptyList(),
            currentMatch = -1,
        )
    }

    fun open(uri: Uri) {
        ui = ui.copy(isBusy = true)
        viewModelScope.launch {
            store.persistPermission(uri)
            when (val result = withContext(Dispatchers.IO) { store.load(uri) }) {
                is LoadResult.Failure -> {
                    ui = ui.copy(isBusy = false, message = result.message)
                }
                is LoadResult.Success -> {
                    val document = result.document
                    languageChosenByUser = false
                    content = TextFieldValue(document.text)
                    savedText = document.text
                    undoManager.reset(EditorSnapshot(document.text, 0, 0))
                    recentFiles.add(document.uri, document.name)
                    ui = ui.copy(
                        uri = document.uri,
                        fileName = document.name,
                        language = Language.fromFileName(document.name),
                        isDirty = false,
                        isBusy = false,
                        crlf = document.crlf,
                        canUndo = false,
                        canRedo = false,
                        recents = recentFiles.list(),
                        matches = emptyList(),
                        currentMatch = -1,
                        message = null,
                    )
                }
            }
        }
    }

    /**
     * The name to offer in the system "create file" dialog. An untitled
     * document is named after the chosen language, because a name without an
     * extension makes the document provider invent one — which is how an HTML
     * file ends up saved as .txt.
     */
    fun suggestedFileName(): String {
        val name = ui.fileName
        val hasExtension = name != EditorUiState.UNTITLED &&
            name.substringAfterLast('.', "").isNotEmpty()
        return if (hasExtension) name else "untitled.${ui.language.defaultExtension}"
    }

    /** Saves to the current file; does nothing when the document is untitled. */
    fun save(onNeedsTarget: () -> Unit) {
        val uri = ui.uri
        if (uri == null) {
            onNeedsTarget()
            return
        }
        writeTo(uri, ui.fileName)
    }

    /** Saves to a freshly picked location and adopts it as the current file. */
    fun saveAs(uri: Uri) {
        store.persistPermission(uri)
        writeTo(uri, store.displayName(uri))
    }

    private fun writeTo(uri: Uri, name: String) {
        val text = content.text
        ui = ui.copy(isBusy = true)
        viewModelScope.launch {
            val error = withContext(Dispatchers.IO) { store.save(uri, text, ui.crlf) }
            if (error != null) {
                ui = ui.copy(isBusy = false, message = error)
                return@launch
            }
            savedText = text
            recentFiles.add(uri, name)
            ui = ui.copy(
                uri = uri,
                fileName = name,
                language = if (languageChosenByUser) ui.language else Language.fromFileName(name),
                isDirty = content.text != savedText,
                isBusy = false,
                recents = recentFiles.list(),
                message = "Saved $name",
            )
        }
    }

    fun forgetRecent(uri: Uri) {
        recentFiles.remove(uri)
        ui = ui.copy(recents = recentFiles.list())
    }

    fun clearRecents() {
        recentFiles.clear()
        ui = ui.copy(recents = emptyList())
    }

    // ------------------------------------------------------------ settings --

    fun updatePreferences(preferences: EditorPreferences) {
        settings.save(preferences)
        ui = ui.copy(preferences = preferences)
    }

    fun setLanguage(language: Language) {
        languageChosenByUser = true
        ui = ui.copy(language = language)
    }

    fun consumeMessage() {
        ui = ui.copy(message = null)
    }

    fun showMessage(message: String) {
        ui = ui.copy(message = message)
    }

    // -------------------------------------------------------------- typing --

    private fun applyTypingAssist(
        previous: TextFieldValue,
        next: TextFieldValue,
        indentUnit: String,
    ): TextFieldValue {
        val preferences = ui.preferences
        if (!preferences.autoIndent && !preferences.autoCloseBrackets) return next
        val typed = singleInsertion(previous, next) ?: return next
        val (caret, char) = typed
        val text = next.text

        if (char == '\n' && preferences.autoIndent) {
            val before = previous.text
            val at = caret - 1
            val insertion = TextTools.newlineInsertion(before, at, indentUnit)
            val opensBlock = before.getOrNull(at - 1) == '{' && before.getOrNull(at) == '}'
            return if (opensBlock) {
                val indent = TextTools.indentAt(before, at)
                val body = "\n$indent$indentUnit"
                val closing = "\n$indent"
                TextFieldValue(
                    text = before.substring(0, at) + body + closing + before.substring(at),
                    selection = TextRange(at + body.length),
                )
            } else {
                TextFieldValue(
                    text = before.substring(0, at) + insertion + before.substring(at),
                    selection = TextRange(at + insertion.length),
                )
            }
        }

        if (preferences.autoCloseBrackets) {
            // Typing a closing character straight over an auto-inserted one
            // should step past it instead of doubling it up.
            if (char in ")]}\"'`" && previous.text.getOrNull(caret - 1) == char &&
                previous.selection.collapsed
            ) {
                return TextFieldValue(previous.text, TextRange(caret))
            }
            val closing = TextTools.closingFor(char)
            if (closing != null && shouldAutoClose(text, caret, char)) {
                return TextFieldValue(
                    text = text.substring(0, caret) + closing + text.substring(caret),
                    selection = TextRange(caret),
                )
            }
        }
        return next
    }

    /** True when exactly one character was inserted at the cursor. */
    private fun singleInsertion(previous: TextFieldValue, next: TextFieldValue): Pair<Int, Char>? {
        if (next.text.length != previous.text.length + 1) return null
        if (!next.selection.collapsed) return null
        val caret = next.selection.start
        val index = caret - 1
        if (index < 0 || index >= next.text.length) return null
        if (previous.text.regionMatches(0, next.text, 0, index) &&
            previous.text.regionMatches(index, next.text, caret, previous.text.length - index)
        ) {
            return caret to next.text[index]
        }
        return null
    }

    private fun shouldAutoClose(text: String, caret: Int, opening: Char): Boolean {
        val after = text.getOrNull(caret)
        if (after != null && (after.isLetterOrDigit() || after == '_')) return false
        if (opening == '"' || opening == '\'' || opening == '`') {
            val before = text.getOrNull(caret - 2)
            if (before != null && (before.isLetterOrDigit() || before == opening)) return false
        }
        return true
    }

    private fun TextFieldValue.toSnapshot() =
        EditorSnapshot(text, selection.start, selection.end)

    private fun EditorSnapshot.toValue() = TextFieldValue(
        text = text,
        selection = TextRange(
            selectionStart.coerceIn(0, text.length),
            selectionEnd.coerceIn(0, text.length),
        ),
    )
}
