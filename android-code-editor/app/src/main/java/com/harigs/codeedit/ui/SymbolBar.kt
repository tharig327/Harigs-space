package com.harigs.codeedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A row of characters that are awkward to reach on a phone keyboard, plus the
 * indent controls. Tapping inserts at the cursor; the arrows shift whole lines.
 */
@Composable
fun SymbolBar(
    onInsert: (String) -> Unit,
    onIndent: () -> Unit,
    onUnindent: () -> Unit,
    onTab: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 6.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SymbolKey("⇥", onClick = onTab)
        SymbolKey("⇤", onClick = onUnindent)
        SymbolKey("⇥⇥", onClick = onIndent)
        SYMBOLS.forEach { symbol ->
            SymbolKey(symbol) { onInsert(symbol) }
        }
    }
}

@Composable
private fun SymbolKey(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .defaultMinSize(minWidth = 36.dp, minHeight = 36.dp)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontFamily = FontFamily.Monospace,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private val SYMBOLS = listOf(
    "{", "}", "(", ")", "[", "]", "<", ">",
    "\"", "'", "`", ";", ":", ",", ".", "=",
    "+", "-", "*", "/", "\\", "|", "&", "!",
    "?", "_", "#", "$", "%", "@", "^", "~",
)
