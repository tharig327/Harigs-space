package com.harigs.codeedit.editor

/** The kinds of token the editor knows how to colour. */
enum class TokenType {
    KEYWORD,
    BUILTIN,
    STRING,
    NUMBER,
    COMMENT,
    ANNOTATION,
    FUNCTION,
    OPERATOR,
    TAG,
    ATTRIBUTE,
    HEADING,
    LINK,
    EMPHASIS,
    STRONG,
    CODE,
}

/** A half-open range `[start, end)` of [text] that should be drawn as [type]. */
data class Token(val start: Int, val end: Int, val type: TokenType)

/**
 * A small hand-written tokenizer. It is deliberately not a parser: it does one
 * left-to-right pass over the text and never backtracks, which keeps it fast
 * enough to run on the UI thread for the file sizes the editor allows.
 */
object SyntaxHighlighter {

    fun tokenize(text: CharSequence, language: Language): List<Token> = when (language) {
        Language.PLAIN -> emptyList()
        Language.MARKDOWN -> tokenizeMarkdown(text)
        Language.XML, Language.HTML -> tokenizeMarkup(text)
        else -> tokenizeCode(text, specFor(language))
    }

    // ---------------------------------------------------------------- code --

    private class CodeSpec(
        val keywords: Set<String>,
        val builtins: Set<String>,
        val lineComments: List<String>,
        val blockComment: Pair<String, String>?,
        val stringDelimiters: String,
        val tripleQuoted: Boolean = false,
        val annotationPrefixes: String = "",
        val identifierExtra: String = "_$",
        val keyValues: Boolean = false,
    )

    private fun tokenizeCode(text: CharSequence, spec: CodeSpec): List<Token> {
        val tokens = ArrayList<Token>()
        val n = text.length
        var i = 0
        var lineStart = true

        while (i < n) {
            val c = text[i]

            if (c == '\n') {
                lineStart = true
                i++
                continue
            }
            if (c == ' ' || c == '\t' || c == '\r') {
                i++
                continue
            }

            val comment = spec.lineComments.firstOrNull { startsWith(text, i, it) }
            if (comment != null) {
                val end = indexOf(text, '\n', i).let { if (it < 0) n else it }
                tokens += Token(i, end, TokenType.COMMENT)
                i = end
                continue
            }

            val block = spec.blockComment
            if (block != null && startsWith(text, i, block.first)) {
                val close = indexOf(text, block.second, i + block.first.length)
                val end = if (close < 0) n else close + block.second.length
                tokens += Token(i, end, TokenType.COMMENT)
                i = end
                lineStart = false
                continue
            }

            if (spec.tripleQuoted && (startsWith(text, i, "\"\"\"") || startsWith(text, i, "'''"))) {
                val fence = text.substring(i, i + 3)
                val close = indexOf(text, fence, i + 3)
                val end = if (close < 0) n else close + 3
                tokens += Token(i, end, TokenType.STRING)
                i = end
                lineStart = false
                continue
            }

            if (c in spec.stringDelimiters) {
                val end = scanString(text, i, c)
                val type = if (spec.keyValues && isFollowedByColon(text, end)) {
                    TokenType.ATTRIBUTE
                } else {
                    TokenType.STRING
                }
                tokens += Token(i, end, type)
                i = end
                lineStart = false
                continue
            }

            if (c.isDigit() || (c == '.' && i + 1 < n && text[i + 1].isDigit())) {
                val end = scanNumber(text, i)
                tokens += Token(i, end, TokenType.NUMBER)
                i = end
                lineStart = false
                continue
            }

            if (c in spec.annotationPrefixes && i + 1 < n && isIdentifierStart(text[i + 1], spec)) {
                var j = i + 1
                while (j < n && isIdentifierPart(text[j], spec)) j++
                tokens += Token(i, j, TokenType.ANNOTATION)
                i = j
                lineStart = false
                continue
            }

            if (isIdentifierStart(c, spec)) {
                var j = i
                while (j < n && isIdentifierPart(text[j], spec)) j++
                val word = text.substring(i, j)
                val type = when {
                    word in spec.keywords -> TokenType.KEYWORD
                    word in spec.builtins -> TokenType.BUILTIN
                    spec.keyValues && lineStart && isFollowedByColon(text, j) -> TokenType.ATTRIBUTE
                    isFollowedByCall(text, j) -> TokenType.FUNCTION
                    else -> null
                }
                if (type != null) tokens += Token(i, j, type)
                i = j
                lineStart = false
                continue
            }

            if (c in OPERATOR_CHARS) {
                var j = i
                while (j < n && text[j] in OPERATOR_CHARS) j++
                tokens += Token(i, j, TokenType.OPERATOR)
                i = j
                lineStart = false
                continue
            }

            lineStart = false
            i++
        }
        return tokens
    }

    private fun scanString(text: CharSequence, start: Int, quote: Char): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i++ // skip the escaped character
                quote -> return i + 1
                '\n' -> return i // unterminated strings stop at the line end
            }
            i++
        }
        return text.length
    }

    private fun scanNumber(text: CharSequence, start: Int): Int {
        val n = text.length
        var i = start
        if (text[i] == '0' && i + 1 < n && (text[i + 1] == 'x' || text[i + 1] == 'X' ||
                text[i + 1] == 'b' || text[i + 1] == 'B' || text[i + 1] == 'o' || text[i + 1] == 'O')
        ) {
            i += 2
            while (i < n && (text[i].isLetterOrDigit() || text[i] == '_')) i++
            return i
        }
        var seenExponent = false
        while (i < n) {
            val c = text[i]
            when {
                c.isDigit() || c == '_' -> i++
                c == '.' && !seenExponent -> i++
                (c == 'e' || c == 'E') && !seenExponent &&
                    i + 1 < n && (text[i + 1].isDigit() || text[i + 1] == '+' || text[i + 1] == '-') -> {
                    seenExponent = true
                    i += 2
                }
                // Type suffixes: 10L, 1.5f, 3u, 100n
                c == 'f' || c == 'F' || c == 'L' || c == 'l' || c == 'u' || c == 'U' || c == 'n' -> {
                    i++
                    break
                }
                else -> break
            }
        }
        return i
    }

    private fun isFollowedByCall(text: CharSequence, from: Int): Boolean {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i < text.length && text[i] == '('
    }

    private fun isFollowedByColon(text: CharSequence, from: Int): Boolean {
        var i = from
        while (i < text.length && (text[i] == ' ' || text[i] == '\t')) i++
        return i < text.length && (text[i] == ':' || text[i] == '=')
    }

    private fun isIdentifierStart(c: Char, spec: CodeSpec) = c.isLetter() || c in spec.identifierExtra

    private fun isIdentifierPart(c: Char, spec: CodeSpec) =
        c.isLetterOrDigit() || c in spec.identifierExtra

    // -------------------------------------------------------------- markup --

    private fun tokenizeMarkup(text: CharSequence): List<Token> {
        val tokens = ArrayList<Token>()
        val n = text.length
        var i = 0

        while (i < n) {
            if (text[i] != '<') {
                i++
                continue
            }
            if (startsWith(text, i, "<!--")) {
                val close = indexOf(text, "-->", i + 4)
                val end = if (close < 0) n else close + 3
                tokens += Token(i, end, TokenType.COMMENT)
                i = end
                continue
            }

            val tagStart = i
            var j = i + 1
            if (j < n && (text[j] == '/' || text[j] == '!' || text[j] == '?')) j++
            while (j < n && (text[j].isLetterOrDigit() || text[j] in ":-_.")) j++
            if (j == i + 1) { // a bare '<' in prose
                i++
                continue
            }
            tokens += Token(tagStart, j, TokenType.TAG)

            // Attributes up to the closing '>'.
            while (j < n && text[j] != '>') {
                val c = text[j]
                when {
                    c == '"' || c == '\'' -> {
                        val end = scanString(text, j, c)
                        tokens += Token(j, end, TokenType.STRING)
                        j = end
                    }
                    c.isLetter() || c == '_' || c == ':' -> {
                        var k = j
                        while (k < n && (text[k].isLetterOrDigit() || text[k] in ":-_.")) k++
                        tokens += Token(j, k, TokenType.ATTRIBUTE)
                        j = k
                    }
                    else -> j++
                }
            }
            if (j < n) {
                tokens += Token(j, j + 1, TokenType.TAG)
                j++
            }
            i = j
        }
        return tokens
    }

    // ------------------------------------------------------------ markdown --

    private fun tokenizeMarkdown(text: CharSequence): List<Token> {
        val tokens = ArrayList<Token>()
        val n = text.length
        var i = 0
        var inFence = false

        while (i < n) {
            val lineEnd = indexOf(text, '\n', i).let { if (it < 0) n else it }
            val line = text.subSequence(i, lineEnd)
            val trimmedOffset = line.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) line.length else it }
            val content = line.subSequence(trimmedOffset, line.length)

            when {
                content.startsWith("```") || content.startsWith("~~~") -> {
                    inFence = !inFence
                    tokens += Token(i, lineEnd, TokenType.CODE)
                }
                inFence -> tokens += Token(i, lineEnd, TokenType.CODE)
                content.startsWith("#") -> tokens += Token(i, lineEnd, TokenType.HEADING)
                content.startsWith(">") -> tokens += Token(i, lineEnd, TokenType.COMMENT)
                else -> {
                    val bullet = bulletLength(content)
                    if (bullet > 0) {
                        val start = i + trimmedOffset
                        tokens += Token(start, start + bullet, TokenType.KEYWORD)
                    }
                    tokens += inlineMarkdown(text, i, lineEnd)
                }
            }
            i = lineEnd + 1
        }
        return tokens.sortedBy { it.start }
    }

    /** Returns the length of a list marker at the start of [content], or 0. */
    private fun bulletLength(content: CharSequence): Int {
        if (content.length >= 2 && content[0] in "-*+" && content[1] == ' ') return 1
        var i = 0
        while (i < content.length && content[i].isDigit()) i++
        if (i in 1..3 && i + 1 < content.length && content[i] == '.' && content[i + 1] == ' ') return i + 1
        return 0
    }

    private fun inlineMarkdown(text: CharSequence, from: Int, to: Int): List<Token> {
        val tokens = ArrayList<Token>()
        var i = from
        while (i < to) {
            when (text[i]) {
                '`' -> {
                    val close = indexOf(text, '`', i + 1, to)
                    if (close < 0) return tokens
                    tokens += Token(i, close + 1, TokenType.CODE)
                    i = close + 1
                }
                '*', '_' -> {
                    val marker = text[i]
                    val strong = i + 1 < to && text[i + 1] == marker
                    val delimiter = if (strong) "$marker$marker" else marker.toString()
                    val close = indexOf(text, delimiter, i + delimiter.length, to)
                    if (close < 0) {
                        i++
                    } else {
                        val end = close + delimiter.length
                        tokens += Token(i, end, if (strong) TokenType.STRONG else TokenType.EMPHASIS)
                        i = end
                    }
                }
                '[' -> {
                    val close = indexOf(text, ']', i + 1, to)
                    if (close < 0 || close + 1 >= to || text[close + 1] != '(') {
                        i++
                    } else {
                        val paren = indexOf(text, ')', close + 2, to)
                        if (paren < 0) {
                            i++
                        } else {
                            tokens += Token(i, paren + 1, TokenType.LINK)
                            i = paren + 1
                        }
                    }
                }
                else -> i++
            }
        }
        return tokens
    }

    // ------------------------------------------------------------- helpers --

    private const val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"

    private fun startsWith(text: CharSequence, at: Int, prefix: String): Boolean {
        if (at + prefix.length > text.length) return false
        for (k in prefix.indices) if (text[at + k] != prefix[k]) return false
        return true
    }

    private fun indexOf(text: CharSequence, c: Char, from: Int, to: Int = text.length): Int {
        var i = from
        while (i < to) {
            if (text[i] == c) return i
            i++
        }
        return -1
    }

    private fun indexOf(text: CharSequence, s: String, from: Int, to: Int = text.length): Int {
        var i = from
        while (i <= to - s.length) {
            if (startsWith(text, i, s)) return i
            i++
        }
        return -1
    }

    // ---------------------------------------------------------- vocabulary --

    private fun specFor(language: Language): CodeSpec {
        val lineComments = listOfNotNull(language.lineComment)
        return when (language) {
            Language.KOTLIN -> CodeSpec(
                keywords = KOTLIN_KEYWORDS,
                builtins = KOTLIN_BUILTINS,
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
                tripleQuoted = true,
                annotationPrefixes = "@",
            )
            Language.JAVA -> CodeSpec(
                keywords = JAVA_KEYWORDS,
                builtins = JAVA_BUILTINS,
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
                annotationPrefixes = "@",
            )
            Language.C_LIKE -> CodeSpec(
                keywords = C_KEYWORDS,
                builtins = C_BUILTINS,
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
                annotationPrefixes = "#@",
            )
            Language.JAVASCRIPT -> CodeSpec(
                keywords = JS_KEYWORDS,
                builtins = JS_BUILTINS,
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'`",
                annotationPrefixes = "@",
            )
            Language.PYTHON -> CodeSpec(
                keywords = PYTHON_KEYWORDS,
                builtins = PYTHON_BUILTINS,
                lineComments = lineComments,
                blockComment = null,
                stringDelimiters = "\"'",
                tripleQuoted = true,
                annotationPrefixes = "@",
            )
            Language.SHELL -> CodeSpec(
                keywords = SHELL_KEYWORDS,
                builtins = SHELL_BUILTINS,
                lineComments = lineComments,
                blockComment = null,
                stringDelimiters = "\"'",
                identifierExtra = "_$",
            )
            Language.RUBY -> CodeSpec(
                keywords = RUBY_KEYWORDS,
                builtins = RUBY_BUILTINS,
                lineComments = lineComments,
                blockComment = null,
                stringDelimiters = "\"'",
                annotationPrefixes = "@",
                identifierExtra = "_?!",
            )
            Language.PHP -> CodeSpec(
                keywords = PHP_KEYWORDS,
                builtins = PHP_BUILTINS,
                lineComments = listOf("//", "#"),
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
                identifierExtra = "_$",
            )
            Language.SQL -> CodeSpec(
                keywords = SQL_KEYWORDS,
                builtins = SQL_BUILTINS,
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'`",
            )
            Language.CSS -> CodeSpec(
                keywords = CSS_KEYWORDS,
                builtins = emptySet(),
                lineComments = emptyList(),
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
                annotationPrefixes = "@",
                identifierExtra = "_-",
                keyValues = true,
            )
            Language.JSON -> CodeSpec(
                keywords = setOf("true", "false", "null"),
                builtins = emptySet(),
                lineComments = listOf("//"),
                blockComment = "/*" to "*/",
                stringDelimiters = "\"",
                keyValues = true,
            )
            Language.YAML -> CodeSpec(
                keywords = setOf("true", "false", "null", "yes", "no", "on", "off", "~"),
                builtins = emptySet(),
                lineComments = lineComments,
                blockComment = null,
                stringDelimiters = "\"'",
                identifierExtra = "_-.",
                keyValues = true,
            )
            Language.TOML -> CodeSpec(
                keywords = setOf("true", "false"),
                builtins = emptySet(),
                lineComments = listOf("#", ";"),
                blockComment = null,
                stringDelimiters = "\"'",
                identifierExtra = "_-.",
                keyValues = true,
            )
            else -> CodeSpec(
                keywords = emptySet(),
                builtins = emptySet(),
                lineComments = lineComments,
                blockComment = language.blockComment,
                stringDelimiters = "\"'",
            )
        }
    }

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if", "in",
        "interface", "is", "null", "object", "package", "return", "super", "this", "throw",
        "true", "try", "typealias", "typeof", "val", "var", "when", "while", "by", "catch",
        "constructor", "delegate", "dynamic", "field", "file", "finally", "get", "import",
        "init", "param", "property", "receiver", "set", "setparam", "value", "where", "actual",
        "abstract", "annotation", "companion", "const", "crossinline", "data", "enum",
        "expect", "external", "final", "infix", "inline", "inner", "internal", "lateinit",
        "noinline", "open", "operator", "out", "override", "private", "protected", "public",
        "reified", "sealed", "suspend", "tailrec", "vararg", "it",
    )
    private val KOTLIN_BUILTINS = setOf(
        "Any", "Array", "Boolean", "Byte", "Char", "CharSequence", "Comparable", "Double",
        "Exception", "Float", "Int", "Iterable", "List", "Long", "Map", "MutableList",
        "MutableMap", "MutableSet", "Nothing", "Number", "Pair", "Sequence", "Set", "Short",
        "String", "Throwable", "Triple", "Unit", "listOf", "mapOf", "setOf", "arrayOf",
        "mutableListOf", "mutableMapOf", "mutableSetOf", "println", "print", "require",
        "check", "error", "let", "run", "apply", "also", "with", "lazy",
    )

    private val JAVA_KEYWORDS = setOf(
        "abstract", "assert", "break", "case", "catch", "class", "const", "continue",
        "default", "do", "else", "enum", "extends", "final", "finally", "for", "goto", "if",
        "implements", "import", "instanceof", "interface", "native", "new", "package",
        "private", "protected", "public", "record", "return", "sealed", "static", "strictfp",
        "super", "switch", "synchronized", "this", "throw", "throws", "transient", "try",
        "var", "volatile", "while", "yield", "true", "false", "null",
    )
    private val JAVA_BUILTINS = setOf(
        "boolean", "byte", "char", "double", "float", "int", "long", "short", "void",
        "Boolean", "Byte", "Character", "Double", "Exception", "Float", "Integer", "List",
        "Long", "Map", "Object", "Set", "Short", "String", "StringBuilder", "System",
        "Thread", "Throwable", "Void",
    )

    private val C_KEYWORDS = setOf(
        "alignas", "alignof", "auto", "break", "case", "catch", "class", "const", "constexpr",
        "continue", "default", "defer", "delete", "do", "else", "enum", "explicit", "export",
        "extern", "fallthrough", "false", "final", "fn", "for", "func", "go", "goto", "if",
        "impl", "import", "in", "inline", "interface", "let", "loop", "match", "mod", "move",
        "mut", "namespace", "new", "nil", "noexcept", "nullptr", "operator", "override",
        "package", "priv", "private", "protected", "pub", "public", "range", "ref", "register",
        "return", "select", "self", "sizeof", "static", "struct", "super", "switch",
        "template", "this", "throw", "trait", "true", "try", "type", "typedef", "typename",
        "union", "unsafe", "use", "using", "var", "virtual", "volatile", "where", "while",
        "guard", "extension", "protocol", "internal", "lazy", "throws", "async", "await",
    )
    private val C_BUILTINS = setOf(
        "bool", "char", "double", "float", "i8", "i16", "i32", "i64", "int", "int8", "int16",
        "int32", "int64", "long", "short", "signed", "size_t", "string", "u8", "u16", "u32",
        "u64", "uint", "uint8", "uint16", "uint32", "uint64", "unsigned", "usize", "void",
        "wchar_t", "String", "Vec", "Option", "Result", "Box", "Self",
    )

    private val JS_KEYWORDS = setOf(
        "abstract", "any", "as", "async", "await", "break", "case", "catch", "class", "const",
        "constructor", "continue", "debugger", "declare", "default", "delete", "do", "else",
        "enum", "export", "extends", "false", "finally", "for", "from", "function", "get",
        "if", "implements", "import", "in", "instanceof", "interface", "keyof", "let", "new",
        "null", "of", "private", "protected", "public", "readonly", "return", "satisfies",
        "set", "static", "super", "switch", "this", "throw", "true", "try", "type", "typeof",
        "undefined", "var", "void", "while", "with", "yield",
    )
    private val JS_BUILTINS = setOf(
        "Array", "Boolean", "Date", "Error", "JSON", "Map", "Math", "Number", "Object",
        "Promise", "RegExp", "Set", "String", "Symbol", "WeakMap", "boolean", "console",
        "document", "globalThis", "number", "process", "require", "string", "unknown",
        "window", "never",
    )

    private val PYTHON_KEYWORDS = setOf(
        "and", "as", "assert", "async", "await", "break", "class", "continue", "def", "del",
        "elif", "else", "except", "finally", "for", "from", "global", "if", "import", "in",
        "is", "lambda", "match", "case", "nonlocal", "not", "or", "pass", "raise", "return",
        "try", "while", "with", "yield", "True", "False", "None", "self", "cls",
    )
    private val PYTHON_BUILTINS = setOf(
        "abs", "all", "any", "bool", "bytes", "dict", "dir", "enumerate", "filter", "float",
        "format", "frozenset", "getattr", "hasattr", "hash", "id", "input", "int",
        "isinstance", "issubclass", "iter", "len", "list", "map", "max", "min", "next",
        "object", "open", "ord", "print", "range", "repr", "reversed", "round", "set",
        "setattr", "sorted", "str", "sum", "super", "tuple", "type", "zip", "Exception",
        "ValueError", "TypeError", "KeyError",
    )

    private val SHELL_KEYWORDS = setOf(
        "if", "then", "else", "elif", "fi", "case", "esac", "for", "select", "while", "until",
        "do", "done", "in", "function", "time", "coproc", "return", "break", "continue",
        "local", "readonly", "declare", "export", "unset", "shift", "trap", "exit",
    )
    private val SHELL_BUILTINS = setOf(
        "awk", "cat", "cd", "chmod", "cp", "curl", "cut", "echo", "eval", "exec", "find",
        "git", "grep", "head", "kill", "ls", "make", "mkdir", "mv", "printf", "pwd", "read",
        "rm", "sed", "set", "sleep", "sort", "source", "tail", "tar", "test", "touch", "tr",
        "uniq", "wget", "wc", "xargs",
    )

    private val RUBY_KEYWORDS = setOf(
        "alias", "and", "begin", "break", "case", "class", "def", "defined?", "do", "else",
        "elsif", "end", "ensure", "false", "for", "if", "in", "module", "next", "nil", "not",
        "or", "redo", "rescue", "retry", "return", "self", "super", "then", "true", "undef",
        "unless", "until", "when", "while", "yield", "require", "require_relative", "attr_accessor",
        "attr_reader", "attr_writer",
    )
    private val RUBY_BUILTINS = setOf(
        "puts", "print", "p", "raise", "lambda", "proc", "loop", "new", "Array", "Hash",
        "String", "Symbol", "Integer", "Float", "Struct", "Module", "Class",
    )

    private val PHP_KEYWORDS = setOf(
        "abstract", "and", "array", "as", "break", "callable", "case", "catch", "class",
        "clone", "const", "continue", "declare", "default", "do", "echo", "else", "elseif",
        "empty", "enddeclare", "endfor", "endforeach", "endif", "endswitch", "endwhile",
        "enum", "extends", "final", "finally", "fn", "for", "foreach", "function", "global",
        "goto", "if", "implements", "include", "include_once", "instanceof", "insteadof",
        "interface", "isset", "list", "match", "namespace", "new", "or", "print", "private",
        "protected", "public", "readonly", "require", "require_once", "return", "static",
        "switch", "throw", "trait", "try", "unset", "use", "var", "while", "xor", "yield",
        "true", "false", "null",
    )
    private val PHP_BUILTINS = setOf(
        "array_filter", "array_map", "array_merge", "count", "die", "explode", "implode",
        "in_array", "json_decode", "json_encode", "preg_match", "preg_replace", "sprintf",
        "str_replace", "strlen", "strpos", "substr", "trim", "var_dump",
    )

    private val SQL_KEYWORDS = setOf(
        "add", "all", "alter", "and", "as", "asc", "begin", "between", "by", "case", "cast",
        "column", "commit", "constraint", "create", "cross", "delete", "desc", "distinct",
        "drop", "else", "end", "exists", "foreign", "from", "full", "group", "having", "in",
        "index", "inner", "insert", "into", "is", "join", "key", "left", "like", "limit",
        "not", "null", "offset", "on", "or", "order", "outer", "primary", "references",
        "right", "rollback", "select", "set", "table", "then", "transaction", "union",
        "unique", "update", "values", "view", "when", "where", "with",
        "ADD", "ALL", "ALTER", "AND", "AS", "ASC", "BEGIN", "BETWEEN", "BY", "CASE", "CAST",
        "COLUMN", "COMMIT", "CONSTRAINT", "CREATE", "CROSS", "DELETE", "DESC", "DISTINCT",
        "DROP", "ELSE", "END", "EXISTS", "FOREIGN", "FROM", "FULL", "GROUP", "HAVING", "IN",
        "INDEX", "INNER", "INSERT", "INTO", "IS", "JOIN", "KEY", "LEFT", "LIKE", "LIMIT",
        "NOT", "NULL", "OFFSET", "ON", "OR", "ORDER", "OUTER", "PRIMARY", "REFERENCES",
        "RIGHT", "ROLLBACK", "SELECT", "SET", "TABLE", "THEN", "TRANSACTION", "UNION",
        "UNIQUE", "UPDATE", "VALUES", "VIEW", "WHEN", "WHERE", "WITH",
    )
    private val SQL_BUILTINS = setOf(
        "avg", "boolean", "char", "coalesce", "count", "date", "datetime", "decimal",
        "float", "int", "integer", "max", "min", "now", "sum", "text", "timestamp", "varchar",
        "AVG", "BOOLEAN", "CHAR", "COALESCE", "COUNT", "DATE", "DATETIME", "DECIMAL",
        "FLOAT", "INT", "INTEGER", "MAX", "MIN", "NOW", "SUM", "TEXT", "TIMESTAMP", "VARCHAR",
    )

    private val CSS_KEYWORDS = setOf(
        "important", "inherit", "initial", "unset", "auto", "none", "block", "flex", "grid",
        "inline", "absolute", "relative", "fixed", "sticky", "hidden", "visible", "solid",
        "dashed", "dotted", "bold", "italic", "center", "left", "right", "transparent",
    )
}
