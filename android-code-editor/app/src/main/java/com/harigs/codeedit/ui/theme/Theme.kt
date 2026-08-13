package com.harigs.codeedit.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.harigs.codeedit.data.ThemeChoice
import com.harigs.codeedit.editor.TokenType

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F5DA8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E2FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFF565E71),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFAAC7FF),
    onPrimary = Color(0xFF002E69),
    primaryContainer = Color(0xFF204376),
    onPrimaryContainer = Color(0xFFD8E2FF),
    secondary = Color(0xFFBEC6DC),
    background = Color(0xFF121316),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF121316),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
)

/** The colours used to paint code. */
data class SyntaxPalette(
    val plain: Color,
    val gutter: Color,
    val currentLineGutter: Color,
    val selection: Color,
    val matchHighlight: Color,
    val activeMatchHighlight: Color,
    val tokens: Map<TokenType, Color>,
) {
    fun colorFor(type: TokenType): Color = tokens[type] ?: plain
}

private val LightSyntax = SyntaxPalette(
    plain = Color(0xFF1A1C1E),
    gutter = Color(0xFF9AA0A6),
    currentLineGutter = Color(0xFF2F5DA8),
    selection = Color(0x332F5DA8),
    matchHighlight = Color(0x33F5A623),
    activeMatchHighlight = Color(0x66F5A623),
    tokens = mapOf(
        TokenType.KEYWORD to Color(0xFF7B1FA2),
        TokenType.BUILTIN to Color(0xFF00695C),
        TokenType.STRING to Color(0xFF2E7D32),
        TokenType.NUMBER to Color(0xFFC62828),
        TokenType.COMMENT to Color(0xFF7A8896),
        TokenType.ANNOTATION to Color(0xFFB26500),
        TokenType.FUNCTION to Color(0xFF1565C0),
        TokenType.OPERATOR to Color(0xFF546E7A),
        TokenType.TAG to Color(0xFF7B1FA2),
        TokenType.ATTRIBUTE to Color(0xFF1565C0),
        TokenType.HEADING to Color(0xFF1565C0),
        TokenType.LINK to Color(0xFF00695C),
        TokenType.EMPHASIS to Color(0xFF5D4037),
        TokenType.STRONG to Color(0xFF5D4037),
        TokenType.CODE to Color(0xFF2E7D32),
    ),
)

private val DarkSyntax = SyntaxPalette(
    plain = Color(0xFFE3E2E6),
    gutter = Color(0xFF6B7280),
    currentLineGutter = Color(0xFFAAC7FF),
    selection = Color(0x33AAC7FF),
    matchHighlight = Color(0x40FFC46B),
    activeMatchHighlight = Color(0x80FFC46B),
    tokens = mapOf(
        TokenType.KEYWORD to Color(0xFFCE93D8),
        TokenType.BUILTIN to Color(0xFF80CBC4),
        TokenType.STRING to Color(0xFFA5D6A7),
        TokenType.NUMBER to Color(0xFFFFAB91),
        TokenType.COMMENT to Color(0xFF8A97A5),
        TokenType.ANNOTATION to Color(0xFFFFCC80),
        TokenType.FUNCTION to Color(0xFF90CAF9),
        TokenType.OPERATOR to Color(0xFFB0BEC5),
        TokenType.TAG to Color(0xFFCE93D8),
        TokenType.ATTRIBUTE to Color(0xFF90CAF9),
        TokenType.HEADING to Color(0xFF90CAF9),
        TokenType.LINK to Color(0xFF80CBC4),
        TokenType.EMPHASIS to Color(0xFFD7CCC8),
        TokenType.STRONG to Color(0xFFD7CCC8),
        TokenType.CODE to Color(0xFFA5D6A7),
    ),
)

val LocalSyntaxPalette = staticCompositionLocalOf { LightSyntax }

@Composable
fun CodeEditTheme(
    themeChoice: ThemeChoice,
    content: @Composable () -> Unit,
) {
    val dark = when (themeChoice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
    }
    CompositionLocalProvider(LocalSyntaxPalette provides if (dark) DarkSyntax else LightSyntax) {
        MaterialTheme(
            colorScheme = if (dark) DarkColors else LightColors,
            content = content,
        )
    }
}
