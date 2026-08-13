package com.harigs.codeedit.data

import android.content.Context
import android.net.Uri
import org.json.JSONArray
import org.json.JSONObject

data class RecentFile(
    val uri: Uri,
    val name: String,
    val openedAt: Long,
)

/** The most recently opened documents, persisted as JSON in shared preferences. */
class RecentFiles(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("recent_files", Context.MODE_PRIVATE)

    fun list(): List<RecentFile> {
        val raw = prefs.getString(KEY, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val uri = item.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RecentFile(
                    uri = Uri.parse(uri),
                    name = item.optString("name", "Untitled"),
                    openedAt = item.optLong("openedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun add(uri: Uri, name: String) {
        val updated = buildList {
            add(RecentFile(uri, name, System.currentTimeMillis()))
            addAll(list().filter { it.uri != uri })
        }.take(MAX_ENTRIES)
        write(updated)
    }

    fun remove(uri: Uri) = write(list().filter { it.uri != uri })

    fun clear() = write(emptyList())

    private fun write(files: List<RecentFile>) {
        val array = JSONArray()
        files.forEach { file ->
            array.put(
                JSONObject()
                    .put("uri", file.uri.toString())
                    .put("name", file.name)
                    .put("openedAt", file.openedAt),
            )
        }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "entries"
        const val MAX_ENTRIES = 15
    }
}
