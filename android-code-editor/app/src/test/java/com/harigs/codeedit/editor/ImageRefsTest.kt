package com.harigs.codeedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ImageRefsTest {

    @Test
    fun `markdown images are found with their alt text`() {
        val text = "Look: ![A cat](cats/tabby.png) and more."
        val refs = ImageRefs.findAll(text)
        assertEquals(1, refs.size)
        assertEquals("cats/tabby.png", refs[0].target)
        assertEquals("A cat", refs[0].label)
        assertEquals(ImageSyntax.MARKDOWN, refs[0].syntax)
        assertEquals("![A cat](cats/tabby.png)", text.substring(refs[0].start, refs[0].end))
    }

    @Test
    fun `markdown title and angle brackets are ignored`() {
        assertEquals("a.png", ImageRefs.findAll("""![x](a.png "a title")""").single().target)
        assertEquals("a b.png", ImageRefs.findAll("![x](<a b.png>)").single().target)
    }

    @Test
    fun `a plain markdown link is not an image`() {
        assertTrue(ImageRefs.findAll("[not an image](page.html)").isEmpty())
    }

    @Test
    fun `html images are found with either quote style`() {
        val text = """<img src="a.png" alt="First"><img src='b.jpg' alt='Second'>"""
        val refs = ImageRefs.findAll(text)
        assertEquals(listOf("a.png", "b.jpg"), refs.map { it.target })
        assertEquals(listOf("First", "Second"), refs.map { it.label })
    }

    @Test
    fun `html attribute order does not matter`() {
        val refs = ImageRefs.findAll("""<img alt="Logo" width="20" src="logo.png" />""")
        assertEquals("logo.png", refs.single().target)
        assertEquals("Logo", refs.single().label)
    }

    @Test
    fun `css url is taken only when it looks like an image`() {
        val text = """
            body { background: url("bg.png"); }
            @font-face { src: url(font.woff2); }
            .a { background: url('/images/hero.jpg'); }
        """.trimIndent()
        assertEquals(
            listOf("bg.png", "/images/hero.jpg"),
            ImageRefs.findAll(text).map { it.target },
        )
    }

    @Test
    fun `data uris are recognised in both syntaxes`() {
        val data = "data:image/png;base64,iVBORw0KGgo="
        val refs = ImageRefs.findAll("""![](  $data )<img src="$data">""")
        assertEquals(2, refs.size)
        assertTrue(refs.all { ImageRefs.isDataUri(it.target) })
        assertEquals("Embedded image", refs[0].displayName)
    }

    @Test
    fun `references are ordered and never overlap`() {
        val text = """
            ![one](1.png)
            <img src="2.png" style="background: url(3.png)">
            ![four](4.png)
        """.trimIndent()
        val refs = ImageRefs.findAll(text)
        assertEquals(listOf("1.png", "2.png", "4.png"), refs.map { it.target })
        for (i in 1 until refs.size) {
            assertTrue("overlap at $i", refs[i].start >= refs[i - 1].end)
        }
    }

    @Test
    fun `the caret finds the reference it sits in`() {
        val text = "before\ntext ![alt](pic.png) after\nlast"
        val start = text.indexOf("![")
        val end = text.indexOf(")", start) + 1

        assertNotNull(ImageRefs.at(text, start))
        assertNotNull(ImageRefs.at(text, start + 4))
        assertNotNull(ImageRefs.at(text, end))
        assertEquals("pic.png", ImageRefs.at(text, start + 4)!!.target)

        assertNull(ImageRefs.at(text, start - 2))
        assertNull(ImageRefs.at(text, end + 3))
        assertNull(ImageRefs.at(text, 2))
    }

    @Test
    fun `caret offsets are absolute not line relative`() {
        val text = "line one\nline two ![a](x.png)"
        val ref = ImageRefs.at(text, text.indexOf("x.png"))!!
        assertEquals("![a](x.png)", text.substring(ref.start, ref.end))
    }

    @Test
    fun `target kinds are told apart`() {
        assertTrue(ImageRefs.isRemote("https://example.com/a.png"))
        assertTrue(ImageRefs.isRemote("//cdn.example.com/a.png"))
        assertFalse(ImageRefs.isRemote("a.png"))
        assertTrue(ImageRefs.isDataUri("data:image/png;base64,AAAA"))
        assertTrue(ImageRefs.isAbsoluteLocal("content://media/1"))
        assertFalse(ImageRefs.isAbsoluteLocal("images/a.png"))
    }

    @Test
    fun `image extensions are recognised, query strings included`() {
        assertTrue(ImageRefs.looksLikeImage("a.PNG"))
        assertTrue(ImageRefs.looksLikeImage("dir/b.jpeg?v=2"))
        assertTrue(ImageRefs.looksLikeImage("data:image/gif;base64,AA"))
        assertFalse(ImageRefs.looksLikeImage("style.css"))
        assertFalse(ImageRefs.looksLikeImage("data:text/plain;base64,AA"))
        assertFalse(ImageRefs.looksLikeImage("noextension"))
    }

    @Test
    fun `relative paths are split into walkable segments`() {
        assertEquals(listOf("a.png"), ImageRefs.normalizeRelativePath("./a.png"))
        assertEquals(listOf("img", "a.png"), ImageRefs.normalizeRelativePath("/img/a.png"))
        assertEquals(listOf("img", "a.png"), ImageRefs.normalizeRelativePath("img//a.png?x=1"))
        assertTrue(ImageRefs.escapesFolder("../secret.png"))
        assertFalse(ImageRefs.escapesFolder("img/a.png"))
    }

    @Test
    fun `data uris decode back to their bytes`() {
        val bytes = byteArrayOf(1, 2, 3, 4, 5)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val decoded = ImageRefs.decodeDataUri("data:image/png;base64,$encoded")
        assertArrayEqualsSafely(bytes, decoded)
    }

    @Test
    fun `wrapped data uris still decode`() {
        val bytes = ByteArray(120) { it.toByte() }
        val encoded = Base64.getMimeEncoder().encodeToString(bytes) // inserts line breaks
        assertTrue(encoded.contains("\n"))
        assertArrayEqualsSafely(bytes, ImageRefs.decodeDataUri("data:image/png;base64,$encoded"))
    }

    @Test
    fun `unusable data uris decode to null`() {
        assertNull(ImageRefs.decodeDataUri("a.png"))
        assertNull(ImageRefs.decodeDataUri("data:image/png,notbase64"))
        assertNull(ImageRefs.decodeDataUri("data:image/png;base64"))
        assertNull(ImageRefs.decodeDataUri("data:image/png;base64,!!!!"))
    }

    @Test
    fun `an empty or image free document yields nothing`() {
        assertTrue(ImageRefs.findAll("").isEmpty())
        assertTrue(ImageRefs.findAll("fun main() { println(\"![no](\") }").isEmpty())
        assertNull(ImageRefs.at("", 0))
    }

    @Test
    fun `a real document with figures in a subfolder`() {
        val text = """
            ## 3. Loading the wire

            ![Figure 3](figures/fig3_loading_wire_into_applicator.jpg)

            Feed the wire until it seats, then confirm against the curve.

            ![Figure 4](figures/fig4_cfm_learn_curves.jpg)
        """.trimIndent()

        val refs = ImageRefs.findAll(text)
        assertEquals(2, refs.size)
        assertEquals("figures/fig3_loading_wire_into_applicator.jpg", refs[0].target)
        assertEquals("Figure 3", refs[0].label)
        assertEquals("Figure 3", refs[0].displayName)
        assertTrue(refs.all { ImageRefs.looksLikeImage(it.target) })
        assertTrue(refs.none { ImageRefs.escapesFolder(it.target) })
        assertEquals(
            listOf("figures", "fig4_cfm_learn_curves.jpg"),
            ImageRefs.normalizeRelativePath(refs[1].target),
        )
    }

    private fun assertArrayEqualsSafely(expected: ByteArray, actual: ByteArray?) {
        assertNotNull(actual)
        assertEquals(expected.toList(), actual!!.toList())
    }
}
