package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomMathTest {

    private val range = 8..32

    @Test
    fun `a small pinch does not change the size yet`() {
        val result = ZoomMath.applyScale(14, 1.05f, range)
        assertEquals(14, result.fontSize)
        assertEquals(1.05f, result.remainingScale, 0.001f)
    }

    @Test
    fun `spreading past the step grows the font`() {
        assertEquals(15, ZoomMath.applyScale(14, ZoomMath.STEP, range).fontSize)
    }

    @Test
    fun `pinching past the step shrinks the font`() {
        assertEquals(13, ZoomMath.applyScale(14, 1f / ZoomMath.STEP, range).fontSize)
    }

    @Test
    fun `a fast pinch applies several steps at once`() {
        val scale = ZoomMath.STEP * ZoomMath.STEP * ZoomMath.STEP
        assertEquals(17, ZoomMath.applyScale(14, scale, range).fontSize)
    }

    @Test
    fun `leftover scale accumulates across events`() {
        var size = 14
        var carried = 1f
        // Ten nudges of 1.05 multiply out to about 1.63, which is four steps.
        repeat(10) {
            val result = ZoomMath.applyScale(size, carried * 1.05f, range)
            size = result.fontSize
            carried = result.remainingScale
        }
        assertEquals(18, size)
    }

    @Test
    fun `the size stays inside the allowed range`() {
        assertEquals(32, ZoomMath.applyScale(30, 100f, range).fontSize)
        assertEquals(8, ZoomMath.applyScale(10, 0.001f, range).fontSize)
    }

    @Test
    fun `leftover is bounded so a reversed pinch responds immediately`() {
        val result = ZoomMath.applyScale(32, 100f, range)
        assertTrue(result.remainingScale <= ZoomMath.STEP)
        assertTrue(result.remainingScale >= 1f / ZoomMath.STEP)
    }

    @Test
    fun `degenerate scales are ignored`() {
        assertEquals(14, ZoomMath.applyScale(14, 0f, range).fontSize)
        assertEquals(14, ZoomMath.applyScale(14, -2f, range).fontSize)
        assertEquals(14, ZoomMath.applyScale(14, Float.NaN, range).fontSize)
        assertEquals(1f, ZoomMath.applyScale(14, Float.NaN, range).remainingScale, 0.001f)
    }

    @Test
    fun `a size outside the range is pulled back in`() {
        assertEquals(32, ZoomMath.applyScale(99, 1f, range).fontSize)
        assertEquals(8, ZoomMath.applyScale(1, 1f, range).fontSize)
    }
}
