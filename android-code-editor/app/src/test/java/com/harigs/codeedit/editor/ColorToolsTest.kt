package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorToolsTest {

    @Test
    fun `channels round trip`() {
        val color = ColorTools.argb(0xFF, 0x12, 0x34, 0x56)
        assertEquals(0xFF, ColorTools.alpha(color))
        assertEquals(0x12, ColorTools.red(color))
        assertEquals(0x34, ColorTools.green(color))
        assertEquals(0x56, ColorTools.blue(color))
    }

    @Test
    fun `hex is formatted without the alpha channel`() {
        assertEquals("#123456", ColorTools.toHex(ColorTools.argb(0xFF, 0x12, 0x34, 0x56)))
        assertEquals("#000000", ColorTools.toHex(ColorTools.BLACK))
        assertEquals("#FFFFFF", ColorTools.toHex(ColorTools.WHITE))
    }

    @Test
    fun `hex parsing accepts the common forms`() {
        assertEquals(ColorTools.WHITE, ColorTools.parseHex("#FFFFFF"))
        assertEquals(ColorTools.WHITE, ColorTools.parseHex("ffffff"))
        assertEquals(ColorTools.WHITE, ColorTools.parseHex("#fff"))
        assertEquals(ColorTools.WHITE, ColorTools.parseHex("  #FFF  "))
        assertEquals(ColorTools.argb(0xFF, 0x11, 0x22, 0x33), ColorTools.parseHex("#123"))
        assertEquals(ColorTools.argb(0x80, 0x11, 0x22, 0x33), ColorTools.parseHex("#80112233"))
    }

    @Test
    fun `hex parsing rejects junk`() {
        assertNull(ColorTools.parseHex(""))
        assertNull(ColorTools.parseHex("#"))
        assertNull(ColorTools.parseHex("#12"))
        assertNull(ColorTools.parseHex("#12345"))
        assertNull(ColorTools.parseHex("#GGGGGG"))
        assertNull(ColorTools.parseHex("rebeccapurple"))
    }

    @Test
    fun `parsed six digit colours are opaque`() {
        assertEquals(0xFF, ColorTools.alpha(ColorTools.parseHex("#010203")!!))
    }

    @Test
    fun `blending moves between the two colours`() {
        assertEquals(ColorTools.BLACK, ColorTools.blend(ColorTools.BLACK, ColorTools.WHITE, 0f))
        assertEquals(ColorTools.WHITE, ColorTools.blend(ColorTools.BLACK, ColorTools.WHITE, 1f))
        val half = ColorTools.blend(ColorTools.BLACK, ColorTools.WHITE, 0.5f)
        assertEquals(128, ColorTools.red(half))
        assertEquals(128, ColorTools.blue(half))
    }

    @Test
    fun `blend clamps out of range amounts`() {
        assertEquals(ColorTools.BLACK, ColorTools.blend(ColorTools.BLACK, ColorTools.WHITE, -5f))
        assertEquals(ColorTools.WHITE, ColorTools.blend(ColorTools.BLACK, ColorTools.WHITE, 5f))
    }

    @Test
    fun `contrast matches the known extremes`() {
        assertEquals(21.0, ColorTools.contrastRatio(ColorTools.BLACK, ColorTools.WHITE), 0.05)
        assertEquals(1.0, ColorTools.contrastRatio(ColorTools.WHITE, ColorTools.WHITE), 0.001)
    }

    @Test
    fun `readability follows the contrast threshold`() {
        assertTrue(ColorTools.isReadable(ColorTools.BLACK, ColorTools.WHITE))
        assertFalse(ColorTools.isReadable(ColorTools.WHITE, ColorTools.WHITE))
        // Mid grey on white is the classic "looks fine, is not" case.
        assertFalse(ColorTools.isReadable(0xFF999999.toInt(), ColorTools.WHITE))
    }

    @Test
    fun `darkness is judged from luminance`() {
        assertTrue(ColorTools.isDark(ColorTools.BLACK))
        assertTrue(ColorTools.isDark(0xFF1E1E1E.toInt()))
        assertFalse(ColorTools.isDark(ColorTools.WHITE))
        assertFalse(ColorTools.isDark(0xFFFDF6E3.toInt()))
    }

    @Test
    fun `derived gutter sits between the text and the background`() {
        val gutter = ColorTools.gutterColor(ColorTools.WHITE, ColorTools.BLACK)
        assertTrue(ColorTools.luminance(gutter) < ColorTools.luminance(ColorTools.WHITE))
        assertTrue(ColorTools.luminance(gutter) > ColorTools.luminance(ColorTools.BLACK))
    }

    @Test
    fun `gutter background stays close to the editor background`() {
        for (background in listOf(ColorTools.BLACK, ColorTools.WHITE, 0xFF1E1E1E.toInt())) {
            val strip = ColorTools.gutterBackground(background)
            assertTrue(
                "strip drifted too far from the background",
                ColorTools.distance(strip, background) <= 60,
            )
        }
    }

    @Test
    fun `highlights are visible against any background`() {
        for (background in listOf(ColorTools.BLACK, ColorTools.WHITE, 0xFF002B36.toInt())) {
            val soft = ColorTools.highlight(background, strong = false)
            val strong = ColorTools.highlight(background, strong = true)
            assertTrue(ColorTools.distance(soft, background) > 0)
            assertTrue(
                "the active match should stand out more than the others",
                ColorTools.distance(strong, background) > ColorTools.distance(soft, background),
            )
        }
    }

    @Test
    fun `presets are opaque and distinct`() {
        val presets = ColorTools.PRESETS
        assertTrue(presets.size >= 12)
        presets.forEach { assertEquals(0xFF, ColorTools.alpha(it)) }
        assertEquals(presets.size, presets.toSet().size)
    }

    @Test
    fun `unset is distinguishable from any picked colour`() {
        assertEquals(0, ColorTools.UNSET)
        ColorTools.PRESETS.forEach { assertTrue(it != ColorTools.UNSET) }
        assertTrue(ColorTools.opaque(ColorTools.UNSET) != ColorTools.UNSET)
    }
}
