package com.harigs.codeedit.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

/** The result of reading a document. */
sealed interface LoadResult {
    data class Success(val document: LoadedDocument) : LoadResult
    data class Failure(val message: String) : LoadResult
}

data class LoadedDocument(
    val uri: Uri,
    val name: String,
    val text: String,
    /** True when the file used CRLF endings, so saving can restore them. */
    val crlf: Boolean,
)

/**
 * Reads and writes text through the Storage Access Framework. Every document
 * the app touches is a content URI, which keeps it working without any storage
 * permission on all supported Android versions.
 */
class DocumentStore(context: Context) {

    private val resolver: ContentResolver = context.applicationContext.contentResolver

    fun load(uri: Uri): LoadResult {
        return try {
            val size = queryLong(uri, OpenableColumns.SIZE)
            if (size != null && size > MAX_BYTES) {
                return LoadResult.Failure("File is too large to edit (${size / (1024 * 1024)} MB)")
            }
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return LoadResult.Failure("Could not open the file")
            if (bytes.size > MAX_BYTES) {
                return LoadResult.Failure("File is too large to edit")
            }
            if (looksBinary(bytes)) {
                return LoadResult.Failure("This looks like a binary file, not text")
            }
            val raw = decodeUtf8(bytes)
                ?: return LoadResult.Failure("The file is not valid UTF-8 text")
            LoadResult.Success(
                LoadedDocument(
                    uri = uri,
                    name = displayName(uri),
                    text = raw.replace("\r\n", "\n").replace('\r', '\n'),
                    crlf = raw.contains("\r\n"),
                ),
            )
        } catch (e: SecurityException) {
            LoadResult.Failure("No permission to read this file")
        } catch (e: Exception) {
            LoadResult.Failure(e.message ?: "Could not read the file")
        }
    }

    /** Writes [text] to [uri]; returns an error message, or null on success. */
    fun save(uri: Uri, text: String, crlf: Boolean): String? {
        val payload = if (crlf) text.replace("\n", "\r\n") else text
        return try {
            // "wt" truncates: without it a shorter document would leave a tail
            // of the previous contents behind.
            val stream = resolver.openOutputStream(uri, "wt")
                ?: return "Could not open the file for writing"
            stream.use {
                it.write(payload.toByteArray(StandardCharsets.UTF_8))
                it.flush()
            }
            null
        } catch (e: SecurityException) {
            "No permission to write this file"
        } catch (e: Exception) {
            e.message ?: "Could not save the file"
        }
    }

    /** Keeps access to [uri] across restarts, when the provider allows it. */
    fun persistPermission(uri: Uri, writable: Boolean = true) {
        val flags = if (writable) {
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        } else {
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        runCatching { resolver.takePersistableUriPermission(uri, flags) }
    }

    fun displayName(uri: Uri): String =
        queryString(uri, OpenableColumns.DISPLAY_NAME)
            ?: uri.lastPathSegment?.substringAfterLast('/')
            ?: "Untitled"

    fun canRead(uri: Uri): Boolean = runCatching {
        resolver.openInputStream(uri)?.use { true } ?: false
    }.getOrDefault(false)

    private fun queryString(uri: Uri, column: String): String? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getString(index)
            } else {
                null
            }
        }
    }.getOrNull()

    private fun queryLong(uri: Uri, column: String): Long? = runCatching {
        resolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                cursor.getLong(index)
            } else {
                null
            }
        }
    }.getOrNull()

    private fun decodeUtf8(bytes: ByteArray): String? = try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes))
            .toString()
    } catch (e: CharacterCodingException) {
        null
    }

    /** A NUL byte in the first few KB is the usual signal for "not text". */
    private fun looksBinary(bytes: ByteArray): Boolean {
        val limit = minOf(bytes.size, 4096)
        for (i in 0 until limit) if (bytes[i] == 0.toByte()) return true
        return false
    }

    companion object {
        /** Files larger than this are refused: editing them would be unusable. */
        const val MAX_BYTES = 8L * 1024 * 1024
    }
}
