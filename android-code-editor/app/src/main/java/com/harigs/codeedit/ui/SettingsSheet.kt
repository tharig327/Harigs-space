package com.harigs.codeedit.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.harigs.codeedit.data.EditorPreferences
import com.harigs.codeedit.data.ThemeChoice

/** Editor preferences, shown as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    preferences: EditorPreferences,
    onChange: (EditorPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)

            SectionLabel("Appearance")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = preferences.theme == choice,
                        onClick = { onChange(preferences.copy(theme = choice)) },
                        label = { Text(choice.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Stepper(
                label = "Font size",
                value = "${preferences.fontSizeSp} sp",
                onDecrease = {
                    onChange(
                        preferences.copy(
                            fontSizeSp = (preferences.fontSizeSp - 1)
                                .coerceIn(EditorPreferences.FONT_SIZE_RANGE),
                        ),
                    )
                },
                onIncrease = {
                    onChange(
                        preferences.copy(
                            fontSizeSp = (preferences.fontSizeSp + 1)
                                .coerceIn(EditorPreferences.FONT_SIZE_RANGE),
                        ),
                    )
                },
            )
            SwitchRow("Show line numbers", preferences.showLineNumbers) {
                onChange(preferences.copy(showLineNumbers = it))
            }
            SwitchRow("Word wrap", preferences.wordWrap) {
                onChange(preferences.copy(wordWrap = it))
            }
            SwitchRow("Syntax highlighting", preferences.highlightSyntax) {
                onChange(preferences.copy(highlightSyntax = it))
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            SectionLabel("Editing")
            SwitchRow("Insert spaces instead of tabs", preferences.useSpaces) {
                onChange(preferences.copy(useSpaces = it))
            }
            Stepper(
                label = "Indent width",
                value = "${preferences.tabWidth}",
                onDecrease = {
                    onChange(
                        preferences.copy(
                            tabWidth = (preferences.tabWidth - 1)
                                .coerceIn(EditorPreferences.TAB_WIDTH_RANGE),
                        ),
                    )
                },
                onIncrease = {
                    onChange(
                        preferences.copy(
                            tabWidth = (preferences.tabWidth + 1)
                                .coerceIn(EditorPreferences.TAB_WIDTH_RANGE),
                        ),
                    )
                },
            )
            SwitchRow("Keep indentation on new lines", preferences.autoIndent) {
                onChange(preferences.copy(autoIndent = it))
            }
            SwitchRow("Close brackets and quotes", preferences.autoCloseBrackets) {
                onChange(preferences.copy(autoCloseBrackets = it))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(
    label: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDecrease) { Text("−", style = MaterialTheme.typography.titleLarge) }
            Text(value, style = MaterialTheme.typography.bodyLarge)
            IconButton(onClick = onIncrease) { Text("+", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
