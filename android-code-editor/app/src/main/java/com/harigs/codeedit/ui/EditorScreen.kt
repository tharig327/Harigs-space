package com.harigs.codeedit.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.harigs.codeedit.editor.Language
import com.harigs.codeedit.editor.TextTools

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(viewModel: EditorViewModel) {
    val ui = viewModel.ui
    val content = viewModel.content
    val snackbarHost = remember { SnackbarHostState() }
    val findFocus = remember { FocusRequester() }

    var menuOpen by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showRecents by remember { mutableStateOf(false) }
    var showLanguages by remember { mutableStateOf(false) }
    var pendingDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }

    val status = remember(content.text, content.selection.start, ui.language, ui.crlf) {
        statusLine(content.text, content.selection.start, ui)
    }

    val openLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::open) }

    val createLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let(viewModel::saveAs) }

    /** Runs [action], asking first when the current document has unsaved edits. */
    fun guarded(action: () -> Unit) {
        if (ui.isDirty) pendingDiscard = action else action()
    }

    LaunchedEffect(ui.message) {
        val message = ui.message ?: return@LaunchedEffect
        snackbarHost.showSnackbar(message)
        viewModel.consumeMessage()
    }

    LaunchedEffect(ui.findVisible) {
        if (ui.findVisible) runCatching { findFocus.requestFocus() }
    }

    BackHandler(enabled = ui.findVisible) { viewModel.setFindVisible(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = (if (ui.isDirty) "• " else "") + ui.fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = status,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::undo, enabled = ui.canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo")
                    }
                    IconButton(onClick = viewModel::redo, enabled = ui.canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo")
                    }
                    IconButton(onClick = { viewModel.setFindVisible(!ui.findVisible) }) {
                        Icon(Icons.Default.Search, contentDescription = "Find and replace")
                    }
                    IconButton(
                        onClick = { viewModel.save { createLauncher.launch(ui.fileName) } },
                        enabled = !ui.isBusy,
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("New file") },
                                onClick = {
                                    menuOpen = false
                                    guarded { viewModel.newDocument() }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Open…") },
                                onClick = {
                                    menuOpen = false
                                    guarded { openLauncher.launch(arrayOf("*/*")) }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Recent files") },
                                onClick = {
                                    menuOpen = false
                                    showRecents = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Save as…") },
                                onClick = {
                                    menuOpen = false
                                    createLauncher.launch(ui.fileName)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Toggle comment") },
                                onClick = {
                                    menuOpen = false
                                    viewModel.toggleComment()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Language: ${ui.language.displayName}") },
                                onClick = {
                                    menuOpen = false
                                    showLanguages = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    menuOpen = false
                                    showSettings = true
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        },
        bottomBar = {
            SymbolBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .imePadding(),
                onInsert = viewModel::insert,
                onIndent = { viewModel.shiftIndent(add = true) },
                onUnindent = { viewModel.shiftIndent(add = false) },
                onTab = {
                    if (content.selection.collapsed) {
                        viewModel.insert(ui.preferences.indentUnit)
                    } else {
                        viewModel.shiftIndent(add = true)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            if (ui.isBusy) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (ui.findVisible) {
                FindReplaceBar(
                    query = ui.query,
                    replacement = ui.replacement,
                    matchCase = ui.matchCase,
                    matchCount = ui.matches.size,
                    currentMatch = ui.currentMatch,
                    focusRequester = findFocus,
                    onQueryChange = viewModel::setQuery,
                    onReplacementChange = viewModel::setReplacement,
                    onMatchCaseChange = viewModel::setMatchCase,
                    onFindNext = { viewModel.findNext(forward = true) },
                    onFindPrevious = { viewModel.findNext(forward = false) },
                    onReplace = viewModel::replaceCurrent,
                    onReplaceAll = viewModel::replaceAll,
                    onClose = { viewModel.setFindVisible(false) },
                )
            }
            CodeEditor(
                value = content,
                onValueChange = viewModel::onTextChange,
                language = ui.language,
                preferences = ui.preferences,
                matches = ui.matches,
                currentMatch = ui.currentMatch,
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (showSettings) {
        SettingsSheet(
            preferences = ui.preferences,
            onChange = viewModel::updatePreferences,
            onDismiss = { showSettings = false },
        )
    }

    if (showLanguages) {
        LanguageDialog(
            current = ui.language,
            onPick = {
                viewModel.setLanguage(it)
                showLanguages = false
            },
            onDismiss = { showLanguages = false },
        )
    }

    if (showRecents) {
        ModalBottomSheet(onDismissRequest = { showRecents = false }) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Recent files", style = MaterialTheme.typography.titleLarge)
                    if (ui.recents.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearRecents) { Text("Clear") }
                    }
                }
                if (ui.recents.isEmpty()) {
                    Text(
                        "Files you open will show up here.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ui.recents.forEach { recent ->
                    Card(
                        onClick = {
                            showRecents = false
                            guarded { viewModel.open(recent.uri) }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(recent.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = recent.uri.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingDiscard?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("Discard changes?") },
            text = { Text("${ui.fileName} has unsaved changes that will be lost.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDiscard = null
                        action()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun LanguageDialog(
    current: Language,
    onPick: (Language) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Syntax") },
        text = {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .wrapContentHeight(),
            ) {
                Language.entries.forEach { language ->
                    TextButton(
                        onClick = { onPick(language) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = language.displayName + if (language == current) "  ✓" else "",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

private fun statusLine(text: String, caret: Int, ui: EditorUiState): String {
    val (line, column) = TextTools.lineAndColumn(text, caret)
    val endings = if (ui.crlf) "CRLF" else "LF"
    return "Ln $line, Col $column · ${TextTools.lineCount(text)} lines · " +
        "${ui.language.displayName} · UTF-8 $endings"
}
