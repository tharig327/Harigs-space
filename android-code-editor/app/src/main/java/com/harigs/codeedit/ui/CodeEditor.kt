package com.harigs.codeedit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.harigs.codeedit.data.EditorPreferences
import com.harigs.codeedit.editor.ColorTools
import com.harigs.codeedit.editor.Language
import com.harigs.codeedit.editor.SyntaxHighlighter
import com.harigs.codeedit.editor.TextTools
import com.harigs.codeedit.editor.Token
import com.harigs.codeedit.editor.TokenType
import com.harigs.codeedit.editor.ZoomMath
import com.harigs.codeedit.ui.theme.LocalSyntaxPalette
import com.harigs.codeedit.ui.theme.SyntaxPalette

/** Documents longer than this are shown unhighlighted to keep typing smooth. */
private const val HIGHLIGHT_LIMIT = 256 * 1024

private val TEXT_PADDING_HORIZONTAL = 8.dp
private val TEXT_PADDING_VERTICAL = 6.dp

/**
 * The editing surface: a monospaced text field with an optional line-number
 * gutter that stays aligned with it, including when lines wrap.
 *
 * The text field itself does not scroll. It is laid out at full height inside a
 * scrolling row, which is what lets the gutter and the text share one scroll
 * position without any manual synchronisation.
 */
@Composable
fun CodeEditor(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    language: Language,
    preferences: EditorPreferences,
    matches: List<IntRange>,
    currentMatch: Int,
    onFontSizeChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val themePalette = LocalSyntaxPalette.current
    val palette = remember(themePalette, preferences.textColor, preferences.backgroundColor) {
        themePalette.withOverrides(preferences.textColor, preferences.backgroundColor)
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    val textStyle = remember(preferences.fontSizeSp, palette) {
        TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = preferences.fontSizeSp.sp,
            lineHeight = (preferences.fontSizeSp * 1.45f).sp,
            color = palette.plain,
        )
    }
    val gutterStyle = remember(textStyle, palette) { textStyle.copy(color = palette.gutter) }
    // A themed cursor disappears against a strongly coloured background, so a
    // custom background borrows the text colour for it instead.
    val cursorColor = if (preferences.backgroundColor == ColorTools.UNSET) {
        MaterialTheme.colorScheme.primary
    } else {
        palette.plain
    }

    val digits = maxOf(2, TextTools.lineCount(value.text).toString().length)
    val charWidthPx = remember(textStyle) {
        measurer.measure(AnnotatedString("0"), textStyle).size.width.toFloat()
    }
    val gutterWidth: Dp = with(density) { (charWidthPx * digits).toDp() + 16.dp }

    val transformation = remember(
        language,
        palette,
        preferences.highlightSyntax,
        matches,
        currentMatch,
    ) {
        CodeHighlightTransformation(
            language = language,
            palette = palette,
            enabled = preferences.highlightSyntax,
            matches = matches,
            currentMatch = currentMatch,
        )
    }

    // Read through state so the long-lived gesture loop always sees the
    // current size and callback rather than the ones from its first composition.
    val fontSize by rememberUpdatedState(preferences.fontSizeSp)
    val onZoom by rememberUpdatedState(onFontSizeChange)

    BoxWithConstraints(
        modifier
            .background(palette.background)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var previousSpan = 0f
                    var carriedScale = 1f
                    while (true) {
                        // The initial pass runs before the text field sees the
                        // event, so a two-finger pinch can be claimed without
                        // disturbing taps, selection or scrolling.
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.filter { it.pressed }
                        if (down.isEmpty()) break
                        if (down.size < 2) {
                            previousSpan = 0f
                            continue
                        }
                        val span = (down[0].position - down[1].position).getDistance()
                        if (previousSpan > 0f && span > 0f) {
                            val result = ZoomMath.applyScale(
                                currentSize = fontSize,
                                scale = carriedScale * (span / previousSpan),
                                range = EditorPreferences.FONT_SIZE_RANGE,
                            )
                            carriedScale = result.remainingScale
                            if (result.fontSize != fontSize) onZoom(result.fontSize)
                        }
                        previousSpan = span
                        down.forEach { it.consume() }
                    }
                }
            },
    ) {
        val viewportHeight = maxHeight
        val viewportWidth = maxWidth
        val contentHeight = layout?.let { with(density) { it.size.height.toDp() } }
            ?.plus(TEXT_PADDING_VERTICAL * 2)
            ?.coerceAtLeast(viewportHeight)
            ?: viewportHeight

        Row(
            Modifier
                .fillMaxWidth()
                .verticalScroll(verticalScroll),
        ) {
            if (preferences.showLineNumbers) {
                LineNumberGutter(
                    text = value.text,
                    layout = layout,
                    caretOffset = value.selection.start,
                    style = gutterStyle,
                    palette = palette,
                    charWidthPx = charWidthPx,
                    width = gutterWidth,
                    height = contentHeight,
                    scrollOffset = { verticalScroll.value },
                    viewportHeightPx = with(density) { viewportHeight.toPx() },
                )
            }
            // Word wrap is decided by the width constraint: a horizontally
            // scrolling parent measures the field with unbounded width, so long
            // lines run on; without it the field is bounded and wraps.
            Box(
                Modifier
                    .weight(1f)
                    .then(
                        if (preferences.wordWrap) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(horizontalScroll)
                        },
                    ),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .defaultMinSize(
                            minWidth = (viewportWidth - gutterWidth).coerceAtLeast(0.dp),
                            minHeight = viewportHeight,
                        )
                        .then(if (preferences.wordWrap) Modifier.fillMaxWidth() else Modifier)
                        .padding(
                            horizontal = TEXT_PADDING_HORIZONTAL,
                            vertical = TEXT_PADDING_VERTICAL,
                        ),
                    textStyle = textStyle,
                    cursorBrush = SolidColor(cursorColor),
                    visualTransformation = transformation,
                    onTextLayout = { layout = it },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        imeAction = ImeAction.Default,
                    ),
                )
            }
        }
    }
}

/**
 * Draws line numbers next to the text. Positions come from the text field's own
 * layout, so a wrapped line keeps exactly one number and the two columns stay
 * aligned at any font size. Only the visible range is drawn.
 */
@OptIn(ExperimentalTextApi::class)
@Composable
private fun LineNumberGutter(
    text: String,
    layout: TextLayoutResult?,
    caretOffset: Int,
    style: TextStyle,
    palette: SyntaxPalette,
    charWidthPx: Float,
    width: Dp,
    height: Dp,
    scrollOffset: () -> Int,
    viewportHeightPx: Float,
) {
    val measurer = rememberTextMeasurer()
    val caretLine = remember(text, caretOffset) { TextTools.lineAndColumn(text, caretOffset).first }
    val activeStyle = remember(style, palette) { style.copy(color = palette.currentLineGutter) }

    Box(
        Modifier
            .width(width)
            .height(height)
            .background(palette.gutterBackground)
            .drawBehind {
                val result = layout ?: return@drawBehind
                val textTop = TEXT_PADDING_VERTICAL.toPx()
                val top = (scrollOffset().toFloat() - textTop).coerceAtLeast(0f)
                val bottom = top + viewportHeightPx

                val firstLine = result.getLineForVerticalPosition(top)
                val lastLine = result.getLineForVerticalPosition(bottom)

                // Work out the document line number of the first visible row.
                var number = 1
                val firstStart = result.getLineStart(firstLine)
                for (i in 0 until firstStart) if (text[i] == '\n') number++

                for (line in firstLine..minOf(lastLine, result.lineCount - 1)) {
                    val start = result.getLineStart(line)
                    val isHardStart = start == 0 || text.getOrNull(start - 1) == '\n'
                    if (line > firstLine && isHardStart) number++
                    if (!isHardStart) continue // wrapped continuation: no number

                    val label = number.toString()
                    drawText(
                        textMeasurer = measurer,
                        text = label,
                        topLeft = Offset(
                            x = size.width - charWidthPx * label.length - 8.dp.toPx(),
                            y = textTop + result.getLineTop(line),
                        ),
                        style = if (number == caretLine) activeStyle else style,
                    )
                }
            },
    )
}

/**
 * Applies syntax colours and search highlights. The transformation never
 * changes the text, so offsets map straight through.
 */
private class CodeHighlightTransformation(
    private val language: Language,
    private val palette: SyntaxPalette,
    private val enabled: Boolean,
    private val matches: List<IntRange>,
    private val currentMatch: Int,
) : VisualTransformation {

    private var cachedSource: String? = null
    private var cachedTokens: List<Token> = emptyList()

    override fun filter(text: AnnotatedString): TransformedText {
        val source = text.text
        val spans = ArrayList<AnnotatedString.Range<SpanStyle>>()

        if (enabled && language != Language.PLAIN && source.length <= HIGHLIGHT_LIMIT) {
            for (token in tokensFor(source)) {
                if (token.start >= token.end || token.end > source.length) continue
                spans += AnnotatedString.Range(styleFor(token.type), token.start, token.end)
            }
        }

        matches.forEachIndexed { index, match ->
            val start = match.first
            val end = (match.last + 1).coerceAtMost(source.length)
            if (start in 0 until end) {
                spans += AnnotatedString.Range(
                    SpanStyle(
                        background = if (index == currentMatch) {
                            palette.activeMatchHighlight
                        } else {
                            palette.matchHighlight
                        },
                    ),
                    start,
                    end,
                )
            }
        }

        return TransformedText(AnnotatedString(source, spans), OffsetMapping.Identity)
    }

    private fun tokensFor(source: String): List<Token> {
        if (cachedSource == source) return cachedTokens
        val tokens = SyntaxHighlighter.tokenize(source, language)
        cachedSource = source
        cachedTokens = tokens
        return tokens
    }

    private fun styleFor(type: TokenType): SpanStyle {
        val color = palette.colorFor(type)
        return when (type) {
            TokenType.COMMENT -> SpanStyle(color = color, fontStyle = FontStyle.Italic)
            TokenType.HEADING, TokenType.STRONG, TokenType.KEYWORD, TokenType.TAG ->
                SpanStyle(color = color, fontWeight = FontWeight.Bold)
            TokenType.EMPHASIS -> SpanStyle(color = color, fontStyle = FontStyle.Italic)
            TokenType.LINK -> SpanStyle(color = color, textDecoration = TextDecoration.Underline)
            else -> SpanStyle(color = color)
        }
    }
}
