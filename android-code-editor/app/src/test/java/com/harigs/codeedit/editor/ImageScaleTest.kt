package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageScaleTest {

    @Test
    fun `a small image is never downscaled`() {
        assertEquals(1, ImageScale.sampleSize(200, 100, 1080, 1920))
    }

    @Test
    fun `a large photo is downscaled by a power of two`() {
        val sample = ImageScale.sampleSize(4000, 3000, 1000, 750)
        assertEquals(4, sample)
        assertTrue("must stay at least as large as the target", 4000 / sample >= 1000)
    }

    @Test
    fun `downscaling never goes below the target box`() {
        for (width in listOf(500, 1000, 2000, 4032, 8000)) {
            val height = width * 3 / 4
            val sample = ImageScale.sampleSize(width, height, 1080, 810)
            assertTrue(sample >= 1)
            if (sample > 1) {
                assertTrue(
                    "sample $sample took ${width}px below the 1080px target",
                    width / sample >= 1080,
                )
            }
        }
    }

    @Test
    fun `degenerate sizes fall back to no scaling`() {
        assertEquals(1, ImageScale.sampleSize(0, 0, 100, 100))
        assertEquals(1, ImageScale.sampleSize(-4, 10, 100, 100))
        assertEquals(1, ImageScale.sampleSize(4000, 3000, 0, 0))
    }

    @Test
    fun `byte sizes are formatted for people`() {
        assertEquals("512 B", ImageScale.formatBytes(512))
        assertEquals("2 KB", ImageScale.formatBytes(2048))
        assertEquals("unknown size", ImageScale.formatBytes(-1))
        assertTrue(ImageScale.formatBytes(5L * 1024 * 1024).endsWith("MB"))
    }
}
