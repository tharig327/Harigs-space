package com.harigs.codeedit.editor

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Colour arithmetic for the editor's custom text and background colours.
 *
 * Colours are plain ARGB integers here rather than Compose values, which keeps
 * the rules — parsing, contrast, derived shades — unit testable.
 */
object ColorTools {

    /** Stored instead of a colour to mean "follow the theme". */
    const val UNSET = 0

    fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int =
        ((alpha and 0xFF) shl 24) or
            ((red and 0xFF) shl 16) or
            ((green and 0xFF) shl 8) or
            (blue and 0xFF)

    fun alpha(color: Int): Int = (color ushr 24) and 0xFF
    fun red(color: Int): Int = (color ushr 16) and 0xFF
    fun green(color: Int): Int = (color ushr 8) and 0xFF
    fun blue(color: Int): Int = color and 0xFF

    /** The same colour, fully opaque. Every colour the user picks is opaque. */
    fun opaque(color: Int): Int = color or (0xFF shl 24)

    /** `#RRGGBB`, which is what the picker shows and accepts. */
    fun toHex(color: Int): String =
        "#%02X%02X%02X".format(red(color), green(color), blue(color))

    /**
     * Parses `#RGB`, `#RRGGBB` or `#AARRGGBB`, with or without the `#`.
     * Returns null for anything else, so the picker can reject as you type.
     */
    fun parseHex(text: String): Int? {
        val digits = text.trim().removePrefix("#")
        if (digits.any { it.digitToIntOrNull(16) == null }) return null
        return when (digits.length) {
            3 -> {
                val r = digits[0].digitToInt(16) * 17
                val g = digits[1].digitToInt(16) * 17
                val b = digits[2].digitToInt(16) * 17
                argb(0xFF, r, g, b)
            }
            6 -> opaque(digits.toLong(16).toInt())
            8 -> digits.toLong(16).toInt()
            else -> null
        }
    }

    /** Mixes [color] towards [towards]; [amount] of 0 keeps it, 1 replaces it. */
    fun blend(color: Int, towards: Int, amount: Float): Int {
        val t = amount.coerceIn(0f, 1f)
        fun mix(a: Int, b: Int) = (a + (b - a) * t).roundToInt().coerceIn(0, 255)
        return argb(
            alpha = alpha(color),
            red = mix(red(color), red(towards)),
            green = mix(green(color), green(towards)),
            blue = mix(blue(color), blue(towards)),
        )
    }

    /** Relative luminance as defined by WCAG. */
    fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val v = value / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(red(color)) +
            0.7152 * channel(green(color)) +
            0.0722 * channel(blue(color))
    }

    /** WCAG contrast ratio, from 1 (identical) to 21 (black on white). */
    fun contrastRatio(first: Int, second: Int): Double {
        val a = luminance(first)
        val b = luminance(second)
        val lighter = maxOf(a, b)
        val darker = minOf(a, b)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True when text of one colour is comfortable to read on the other. */
    fun isReadable(text: Int, background: Int): Boolean = contrastRatio(text, background) >= 4.5

    /** True when a colour is dark enough that light text suits it. */
    fun isDark(color: Int): Boolean = luminance(color) < 0.4

    /**
     * The line-number colour to use with a custom text colour: the text colour
     * faded towards the background so numbers stay legible but recede.
     */
    fun gutterColor(text: Int, background: Int): Int = blend(text, background, 0.45f)

    /** A subtle fill for the gutter strip against a custom background. */
    fun gutterBackground(background: Int): Int {
        val towards = if (isDark(background)) WHITE else BLACK
        return blend(background, towards, 0.06f)
    }

    /** A selection/highlight wash that stays visible on any background. */
    fun highlight(background: Int, strong: Boolean): Int {
        val towards = if (isDark(background)) WHITE else BLACK
        return blend(background, towards, if (strong) 0.34f else 0.16f)
    }

    /** How far apart two colours are, used to keep presets distinguishable. */
    fun distance(first: Int, second: Int): Int =
        abs(red(first) - red(second)) +
            abs(green(first) - green(second)) +
            abs(blue(first) - blue(second))

    const val BLACK = 0xFF000000.toInt()
    const val WHITE = 0xFFFFFFFF.toInt()

    /** The swatches offered in the picker; any colour is still reachable by hex. */
    val PRESETS = listOf(
        0xFF000000, 0xFF1A1C1E, 0xFF14181F, 0xFF1E1E1E, 0xFF002B36, 0xFF1B2B34,
        0xFF2B2B2B, 0xFF3C3836, 0xFF4A4A4A, 0xFF7A8896, 0xFF9AA0A6, 0xFFC4C6D0,
        0xFFFFFFFF, 0xFFFDF6E3, 0xFFF5F5DC, 0xFFE8EDF2, 0xFFD8E2FF, 0xFFFFF3E0,
        0xFF00FF41, 0xFF7FFF00, 0xFFFFB000, 0xFFFF6B6B, 0xFF64B5F6, 0xFFCE93D8,
    ).map { it.toInt() }
}
