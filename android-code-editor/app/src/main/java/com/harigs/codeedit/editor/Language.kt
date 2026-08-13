package com.harigs.codeedit.editor

/**
 * The set of languages the editor can highlight. Every language knows how to
 * recognise itself from a file name so that opening a document is enough to
 * pick a highlighter.
 */
enum class Language(
    val displayName: String,
    val extensions: List<String>,
    val lineComment: String? = null,
    val blockComment: Pair<String, String>? = null,
) {
    PLAIN("Plain text", listOf("txt", "log", "text", "csv", "env")),

    KOTLIN(
        displayName = "Kotlin",
        extensions = listOf("kt", "kts"),
        lineComment = "//",
        blockComment = "/*" to "*/",
    ),
    JAVA(
        displayName = "Java",
        extensions = listOf("java"),
        lineComment = "//",
        blockComment = "/*" to "*/",
    ),
    C_LIKE(
        displayName = "C/C++",
        extensions = listOf("c", "h", "cc", "cpp", "cxx", "hpp", "hh", "cs", "swift", "go", "rs", "dart", "scala", "groovy", "gradle"),
        lineComment = "//",
        blockComment = "/*" to "*/",
    ),
    JAVASCRIPT(
        displayName = "JavaScript/TypeScript",
        extensions = listOf("js", "jsx", "mjs", "cjs", "ts", "tsx"),
        lineComment = "//",
        blockComment = "/*" to "*/",
    ),
    PYTHON(
        displayName = "Python",
        extensions = listOf("py", "pyw", "pyi"),
        lineComment = "#",
    ),
    SHELL(
        displayName = "Shell",
        extensions = listOf("sh", "bash", "zsh", "ksh", "bashrc", "zshrc", "profile"),
        lineComment = "#",
    ),
    RUBY(
        displayName = "Ruby",
        extensions = listOf("rb", "rake", "gemspec"),
        lineComment = "#",
    ),
    PHP(
        displayName = "PHP",
        extensions = listOf("php", "phtml"),
        lineComment = "//",
        blockComment = "/*" to "*/",
    ),
    SQL(
        displayName = "SQL",
        extensions = listOf("sql"),
        lineComment = "--",
        blockComment = "/*" to "*/",
    ),
    CSS(
        displayName = "CSS",
        extensions = listOf("css", "scss", "less"),
        blockComment = "/*" to "*/",
    ),
    JSON(
        displayName = "JSON",
        extensions = listOf("json", "jsonc", "webmanifest"),
    ),
    YAML(
        displayName = "YAML",
        extensions = listOf("yml", "yaml"),
        lineComment = "#",
    ),
    TOML(
        displayName = "TOML/INI",
        extensions = listOf("toml", "ini", "cfg", "conf", "properties", "gitconfig", "editorconfig"),
        lineComment = "#",
    ),
    HTML(
        displayName = "HTML",
        extensions = listOf("html", "htm", "xhtml", "vue"),
        blockComment = "<!--" to "-->",
    ),
    XML(
        displayName = "XML",
        extensions = listOf("xml", "svg", "plist", "xsd", "xsl", "resx"),
        blockComment = "<!--" to "-->",
    ),
    MARKDOWN(
        displayName = "Markdown",
        extensions = listOf("md", "markdown", "mdx"),
    ),
    ;

    /** The extension suggested when saving a document that has no name yet. */
    val defaultExtension: String get() = extensions.firstOrNull() ?: "txt"

    companion object {
        /** File names (lowercase, no extension) that map directly to a language. */
        private val byFileName = mapOf(
            "makefile" to SHELL,
            "dockerfile" to SHELL,
            "gemfile" to RUBY,
            "rakefile" to RUBY,
            "podfile" to RUBY,
            "cmakelists.txt" to SHELL,
            "gradle.properties" to TOML,
            "local.properties" to TOML,
            ".gitignore" to TOML,
            ".gitattributes" to TOML,
        )

        /** Picks a language from a file name, falling back to [PLAIN]. */
        fun fromFileName(fileName: String?): Language {
            if (fileName.isNullOrBlank()) return PLAIN
            val name = fileName.substringAfterLast('/').lowercase()
            byFileName[name]?.let { return it }

            // Dotfiles such as ".bashrc" have no real extension; treat the whole
            // name after the dot as one.
            val extension = when {
                name.startsWith(".") && name.count { it == '.' } == 1 -> name.substring(1)
                name.contains('.') -> name.substringAfterLast('.')
                else -> return PLAIN
            }
            return entries.firstOrNull { extension in it.extensions } ?: PLAIN
        }
    }
}
