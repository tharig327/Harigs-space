package com.harigs.codeedit.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.harigs.codeedit.editor.Language

/** One row in the folder browser. */
data class FolderEntry(
    val uri: Uri,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
) {
    /** True for files the editor can sensibly open as text. */
    val isEditable: Boolean
        get() {
            if (isDirectory) return false
            if (Language.fromFileName(name) != Language.PLAIN) return true
            val extension = name.substringAfterLast('.', "").lowercase()
            return extension in TEXT_EXTENSIONS
        }

    private companion object {
        // Plain-text kinds the language table maps to PLAIN, plus "no extension".
        val TEXT_EXTENSIONS = setOf("txt", "log", "text", "csv", "env", "")
    }
}

/**
 * Remembers the folder the user granted access to, and lists it.
 *
 * A folder grant is what makes relative image paths resolvable, and it doubles
 * as a way to browse a project without going through the system picker for
 * every file.
 */
class FolderStore(context: Context) {

    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("folder_access", Context.MODE_PRIVATE)

    /** The granted folder, or null when the grant is gone or never happened. */
    fun current(): Uri? {
        val stored = prefs.getString(KEY_FOLDER, null) ?: return null
        val uri = runCatching { Uri.parse(stored) }.getOrNull() ?: return null
        val held = app.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
        return if (held) uri else null
    }

    fun name(folder: Uri): String =
        runCatching { DocumentFile.fromTreeUri(app, folder)?.name }.getOrNull()
            ?: folder.lastPathSegment?.substringAfterLast('/')
            ?: "Folder"

    /** Persists the grant so it survives a restart. */
    fun remember(folder: Uri) {
        runCatching {
            app.contentResolver.takePersistableUriPermission(
                folder,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        prefs.edit().putString(KEY_FOLDER, folder.toString()).apply()
    }

    fun forget() {
        prefs.edit().remove(KEY_FOLDER).apply()
    }

    /** Lists [folder], directories first then files, both alphabetically. */
    fun list(folder: Uri): List<FolderEntry> {
        val document = runCatching { DocumentFile.fromTreeUri(app, folder) }.getOrNull()
            ?: return emptyList()
        return runCatching { document.listFiles() }
            .getOrDefault(emptyArray())
            .mapNotNull { file ->
                val name = file.name ?: return@mapNotNull null
                FolderEntry(
                    uri = file.uri,
                    name = name,
                    isDirectory = file.isDirectory,
                    sizeBytes = if (file.isDirectory) -1L else file.length(),
                )
            }
            .sortedWith(compareByDescending<FolderEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private companion object {
        const val KEY_FOLDER = "folder"
    }
}
