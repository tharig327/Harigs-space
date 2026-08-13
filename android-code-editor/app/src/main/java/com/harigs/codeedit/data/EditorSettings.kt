package com.harigs.codeedit.data

import android.content.Context

enum class ThemeChoice { SYSTEM, LIGHT, DARK }

/** User preferences for the editor surface. */
data class EditorPreferences(
    val theme: ThemeChoice = ThemeChoice.SYSTEM,
    val fontSizeSp: Int = 14,
    val wordWrap: Boolean = false,
    val showLineNumbers: Boolean = true,
    val useSpaces: Boolean = true,
    val tabWidth: Int = 4,
    val autoIndent: Boolean = true,
    val autoCloseBrackets: Boolean = true,
    val highlightSyntax: Boolean = true,
) {
    /** The string inserted for one indentation level. */
    val indentUnit: String get() = if (useSpaces) " ".repeat(tabWidth) else "\t"

    companion object {
        val FONT_SIZE_RANGE = 8..32
        val TAB_WIDTH_RANGE = 2..8
    }
}

/** Persists [EditorPreferences] in shared preferences. */
class EditorSettings(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("editor_settings", Context.MODE_PRIVATE)

    fun load(): EditorPreferences {
        val defaults = EditorPreferences()
        return EditorPreferences(
            theme = runCatching { ThemeChoice.valueOf(prefs.getString("theme", null) ?: "") }
                .getOrDefault(defaults.theme),
            fontSizeSp = prefs.getInt("fontSize", defaults.fontSizeSp)
                .coerceIn(EditorPreferences.FONT_SIZE_RANGE),
            wordWrap = prefs.getBoolean("wordWrap", defaults.wordWrap),
            showLineNumbers = prefs.getBoolean("lineNumbers", defaults.showLineNumbers),
            useSpaces = prefs.getBoolean("useSpaces", defaults.useSpaces),
            tabWidth = prefs.getInt("tabWidth", defaults.tabWidth)
                .coerceIn(EditorPreferences.TAB_WIDTH_RANGE),
            autoIndent = prefs.getBoolean("autoIndent", defaults.autoIndent),
            autoCloseBrackets = prefs.getBoolean("autoClose", defaults.autoCloseBrackets),
            highlightSyntax = prefs.getBoolean("highlight", defaults.highlightSyntax),
        )
    }

    fun save(preferences: EditorPreferences) {
        prefs.edit()
            .putString("theme", preferences.theme.name)
            .putInt("fontSize", preferences.fontSizeSp)
            .putBoolean("wordWrap", preferences.wordWrap)
            .putBoolean("lineNumbers", preferences.showLineNumbers)
            .putBoolean("useSpaces", preferences.useSpaces)
            .putInt("tabWidth", preferences.tabWidth)
            .putBoolean("autoIndent", preferences.autoIndent)
            .putBoolean("autoClose", preferences.autoCloseBrackets)
            .putBoolean("highlight", preferences.highlightSyntax)
            .apply()
    }
}
