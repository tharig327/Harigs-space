package com.harigs.codeedit.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.harigs.codeedit.data.EditorPreferences
import com.harigs.codeedit.data.ThemeChoice
import com.harigs.codeedit.editor.ColorTools
import com.harigs.codeedit.ui.theme.LocalSyntaxPalette

/** Editor preferences, shown as a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    preferences: EditorPreferences,
    onChange: (EditorPreferences) -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalSyntaxPalette.current
    // What the editor would use right now, so the picker opens on the colour
    // actually on screen rather than on a theme default the user never chose.
    val resolvedText = if (preferences.textColor == ColorTools.UNSET) {
        palette.plain.toArgb()
    } else {
        preferences.textColor
    }
    val resolvedBackground = if (preferences.backgroundColor == ColorTools.UNSET) {
        palette.background.toArgb()
    } else {
        preferences.backgroundColor
    }
    var picking by remember { mutableStateOf<ColorTarget?>(null) }

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

            ColorRow(
                label = "Text colour",
                color = preferences.textColor,
                fallback = palette.plain,
                onClick = { picking = ColorTarget.TEXT },
            )
            ColorRow(
                label = "Background colour",
                color = preferences.backgroundColor,
                fallback = palette.background,
                onClick = { picking = ColorTarget.BACKGROUND },
            )
            if (preferences.highlightSyntax && preferences.textColor != ColorTools.UNSET) {
                Text(
                    text = "Syntax colours are unchanged; turn highlighting off to " +
                        "show everything in your text colour.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (preferences.hasCustomColors) {
                TextButton(
                    onClick = {
                        onChange(
                            preferences.copy(
                                textColor = ColorTools.UNSET,
                                backgroundColor = ColorTools.UNSET,
                            ),
                        )
                    },
                ) { Text("Reset colours to theme") }
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

    when (picking) {
        ColorTarget.TEXT -> ColorPickerDialog(
            title = "Text colour",
            initial = preferences.textColor,
            themeDefault = palette.plain.toArgb(),
            counterpart = resolvedBackground,
            pickingText = true,
            onPick = {
                onChange(preferences.copy(textColor = it))
                picking = null
            },
            onDismiss = { picking = null },
        )
        ColorTarget.BACKGROUND -> ColorPickerDialog(
            title = "Background colour",
            initial = preferences.backgroundColor,
            themeDefault = palette.background.toArgb(),
            counterpart = resolvedText,
            pickingText = false,
            onPick = {
                onChange(preferences.copy(backgroundColor = it))
                picking = null
            },
            onDismiss = { picking = null },
        )
        null -> Unit
    }
}

private enum class ColorTarget { TEXT, BACKGROUND }

@Composable
private fun ColorRow(label: String, color: Int, fallback: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (color == ColorTools.UNSET) "Theme" else ColorTools.toHex(color),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp),
            )
            ColorChip(color = color, fallback = fallback)
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
