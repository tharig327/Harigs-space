package com.harigs.codeedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harigs.codeedit.editor.ColorTools

/**
 * Picks one colour: a row of swatches for the common choices, sliders and a hex
 * field for anything else, and a preview showing the result against the colour
 * it will be paired with.
 *
 * @param counterpart the other half of the pair, used for the preview and the
 *   contrast warning: the background when picking text, and vice versa.
 */
@Composable
fun ColorPickerDialog(
    title: String,
    initial: Int,
    themeDefault: Int,
    counterpart: Int,
    pickingText: Boolean,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val start = if (initial == ColorTools.UNSET) themeDefault else initial
    var color by remember { mutableIntStateOf(ColorTools.opaque(start)) }
    var hexText by remember { mutableStateOf(ColorTools.toHex(ColorTools.opaque(start))) }

    fun choose(next: Int) {
        color = ColorTools.opaque(next)
        hexText = ColorTools.toHex(color)
    }

    val textColor = if (pickingText) color else counterpart
    val backgroundColor = if (pickingText) counterpart else color
    val readable = ColorTools.isReadable(textColor, backgroundColor)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(backgroundColor))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "fun main() {",
                        color = Color(textColor),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "    println(\"hello\")",
                        color = Color(textColor),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                    Text(
                        text = "}",
                        color = Color(textColor),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                    )
                }

                if (!readable) {
                    Text(
                        text = "Low contrast — this may be hard to read.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                ColorTools.PRESETS.chunked(6).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { preset ->
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(preset))
                                    .border(
                                        width = if (preset == color) 3.dp else 1.dp,
                                        color = if (preset == color) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outline
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                    )
                                    .clickable { choose(preset) },
                            )
                        }
                    }
                }

                ChannelSlider("R", ColorTools.red(color)) {
                    choose(ColorTools.argb(0xFF, it, ColorTools.green(color), ColorTools.blue(color)))
                }
                ChannelSlider("G", ColorTools.green(color)) {
                    choose(ColorTools.argb(0xFF, ColorTools.red(color), it, ColorTools.blue(color)))
                }
                ChannelSlider("B", ColorTools.blue(color)) {
                    choose(ColorTools.argb(0xFF, ColorTools.red(color), ColorTools.green(color), it))
                }

                OutlinedTextField(
                    value = hexText,
                    onValueChange = { entered ->
                        hexText = entered
                        ColorTools.parseHex(entered)?.let { color = ColorTools.opaque(it) }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    isError = ColorTools.parseHex(hexText) == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onPick(color) }) { Text("Use colour") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onPick(ColorTools.UNSET) }) { Text("Theme") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun ChannelSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.width(20.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(34.dp),
        )
    }
}

/** The colour chip shown in the settings sheet next to each colour row. */
@Composable
fun ColorChip(color: Int, fallback: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (color == ColorTools.UNSET) fallback else Color(color))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp)),
    )
}
