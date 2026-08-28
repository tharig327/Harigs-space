package com.harigs.codeedit.editor

import java.util.Base64

/** How an image was written in the document. */
enum class ImageSyntax { MARKDOWN, HTML, CSS }

/**
 * An image the document points at: `![alt](photo.png)`, `<img src="photo.png">`
 * or `url(photo.png)`. [start] and [end] cover the whole reference so the
 * editor can tell when the caret is sitting inside one.
 */
data class ImageRef(
    val start: Int,
    val end: Int,
    val target: String,
    val label: String,
    val syntax: ImageSyntax,
) {
    /** A short name for lists and buttons. */
    val displayName: String
        get() = when {
            label.isNotBlank() -> label
            ImageRefs.isDataUri(target) -> "Embedded image"
            else -> target.substringAfterLast('/').substringBefore('?').ifBlank { target }
        }
}

/**
 * Finds image references in text. This is a scanner, not an HTML or Markdown
 * parser: it recognises the three ways an image is normally written and leaves
 * everything else alone.
 */
object ImageRefs {

    private val MARKDOWN = Regex(
        """!\[([^\]\n]*)]\(\s*(?:<([^>\n]*)>|([^)\s"']+))(?:\s+["'][^"'\n]*["'])?\s*\)""",
    )

    private val HTML = Regex(
        """<img\b[^>]*?\bsrc\s*=\s*(?:"([^"\n]*)"|'([^'\n]*)'|([^\s>]+))[^>]*>""",
        RegexOption.IGNORE_CASE,
    )

    private val HTML_ALT = Regex("""\balt\s*=\s*(?:"([^"\n]*)"|'([^'\n]*)')""", RegexOption.IGNORE_CASE)

    private val CSS = Regex("""url\(\s*(?:"([^"\n]*)"|'([^'\n]*)'|([^)'"\n]+))\s*\)""", RegexOption.IGNORE_CASE)

    private val IMAGE_EXTENSIONS = setOf(
        "png", "jpg", "jpeg", "gif", "bmp", "webp", "heic", "heif", "avif", "svg", "ico",
    )

    /** Every image reference in [text], ordered by position and never overlapping. */
    fun findAll(text: CharSequence): List<ImageRef> {
        val found = ArrayList<ImageRef>()

        MARKDOWN.findAll(text).forEach { match ->
            val target = firstNonEmpty(match.groupValues, 2, 3).orEmpty()
            if (target.isNotBlank()) {
                found += ImageRef(
                    start = match.range.first,
                    end = match.range.last + 1,
                    target = target.trim(),
                    label = match.groupValues[1].trim(),
                    syntax = ImageSyntax.MARKDOWN,
                )
            }
        }

        HTML.findAll(text).forEach { match ->
            val target = firstNonEmpty(match.groupValues, 1, 2, 3) ?: return@forEach
            found += ImageRef(
                start = match.range.first,
                end = match.range.last + 1,
                target = target.trim(),
                label = HTML_ALT.find(match.value)
                    ?.let { firstNonEmpty(it.groupValues, 1, 2) }
                    ?.trim()
                    .orEmpty(),
                syntax = ImageSyntax.HTML,
            )
        }

        CSS.findAll(text).forEach { match ->
            val target = firstNonEmpty(match.groupValues, 1, 2, 3)?.trim() ?: return@forEach
            // url() is also used for fonts and stylesheets, so only take targets
            // that actually look like images.
            if (!looksLikeImage(target)) return@forEach
            found += ImageRef(
                start = match.range.first,
                end = match.range.last + 1,
                target = target,
                label = "",
                syntax = ImageSyntax.CSS,
            )
        }

        // An <img> may contain a url() in a style attribute; keep the outer one.
        return found
            .sortedBy { it.start }
            .fold(ArrayList<ImageRef>()) { kept, ref ->
                val previous = kept.lastOrNull()
                if (previous == null || ref.start >= previous.end) kept += ref
                kept
            }
    }

    /**
     * The reference the caret is inside, searching only the caret's own line so
     * that this stays cheap enough to run on every cursor move.
     */
    fun at(text: CharSequence, offset: Int): ImageRef? {
        if (text.isEmpty()) return null
        val caret = offset.coerceIn(0, text.length)
        val lineStart = TextTools.lineStart(text, caret)
        val lineEnd = TextTools.lineEnd(text, caret)
        val line = text.subSequence(lineStart, lineEnd)
        return findAll(line)
            .firstOrNull { caret - lineStart in it.start..it.end }
            ?.let { it.copy(start = it.start + lineStart, end = it.end + lineStart) }
    }

    fun isDataUri(target: String): Boolean =
        target.startsWith("data:", ignoreCase = true)

    fun isRemote(target: String): Boolean =
        target.startsWith("http://", ignoreCase = true) ||
            target.startsWith("https://", ignoreCase = true) ||
            target.startsWith("//")

    /** True for a target already usable as an Android content or file URI. */
    fun isAbsoluteLocal(target: String): Boolean =
        target.startsWith("content://", ignoreCase = true) ||
            target.startsWith("file://", ignoreCase = true)

    fun looksLikeImage(target: String): Boolean {
        if (isDataUri(target)) return target.startsWith("data:image", ignoreCase = true)
        val name = target.substringBefore('?').substringBefore('#').substringAfterLast('/')
        val extension = name.substringAfterLast('.', "").lowercase()
        return extension in IMAGE_EXTENSIONS
    }

    /** Strips `./` and any leading slash so a path can be walked from a folder. */
    fun normalizeRelativePath(target: String): List<String> =
        target.substringBefore('?')
            .substringBefore('#')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }

    /** True when the path climbs above its folder, which a folder grant cannot reach. */
    fun escapesFolder(target: String): Boolean = normalizeRelativePath(target).contains("..")

    /** Decodes the payload of a `data:` URI, or null when it is not usable. */
    fun decodeDataUri(target: String): ByteArray? {
        if (!isDataUri(target)) return null
        val comma = target.indexOf(',')
        if (comma < 0) return null
        val header = target.substring(0, comma)
        val payload = target.substring(comma + 1)
        if (!header.contains("base64", ignoreCase = true)) return null
        return try {
            // The MIME decoder tolerates the line breaks long data URIs pick up,
            // but it also ignores junk outside the alphabet: decoding a payload
            // to nothing means it held no base64 at all.
            Base64.getMimeDecoder().decode(payload).takeIf { it.isNotEmpty() }
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    private fun firstNonEmpty(groups: List<String>, vararg indices: Int): String? {
        for (index in indices) {
            val value = groups.getOrNull(index)
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }
}
