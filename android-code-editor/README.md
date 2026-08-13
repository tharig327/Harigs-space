# CodeEdit

An Android app for editing code and text files on a phone or tablet. Native
Kotlin, Jetpack Compose, no third-party libraries.

## What it does

**Files**
- Opens and saves any text file through the Storage Access Framework, so it
  needs no storage permission at all. New files are offered with an extension
  matching the current language, and the name typed in the save dialog is kept
  exactly as typed.
- Opens files handed over by file managers and other apps (`VIEW`, `EDIT` and
  `SEND` intents for text types).
- Remembers the last 15 files, with persisted access across restarts.
- Refuses binary files, non-UTF-8 files, and anything above 8 MB rather than
  corrupting them, and preserves CRLF line endings on save.
- Marks unsaved changes in the title bar and asks before discarding them.

**Editing**
- Syntax highlighting for Kotlin, Java, C/C++/C#/Go/Rust/Swift/Dart,
  JavaScript/TypeScript, Python, Ruby, PHP, shell, SQL, CSS, JSON, YAML,
  TOML/INI, HTML, XML and Markdown, detected from the file name and overridable
  from the menu.
- A line-number gutter that stays aligned with the text, including when lines
  wrap.
- Undo and redo, with keystrokes coalesced so one undo steps back a word rather
  than a character.
- Find and replace with a match counter, match-case toggle, in-place highlights
  and replace-all.
- Auto-indent, bracket and quote closing, block indent/unindent, and
  comment/uncomment for the current selection.
- An extra key row for the characters phone keyboards hide: braces, brackets,
  quotes, operators and tab.
- Word wrap, font size, indent width, tabs-versus-spaces, light/dark/system
  theme — all persisted.

## Building

Requires JDK 17 and the Android SDK (compileSdk 35). Everything else is fetched
by Gradle.

```
./gradlew assembleDebug          # APK at app/build/outputs/apk/debug/
./gradlew installDebug           # build and install on a connected device
./gradlew testDebugUnitTest      # unit tests
```

CI (`.github/workflows/android.yml`) runs the tests and lint and uploads a debug
APK as a build artifact on every push that touches this directory.

## Layout

```
app/src/main/java/com/harigs/codeedit/
├── MainActivity.kt              entry point, handles VIEW/EDIT/SEND intents
├── data/
│   ├── DocumentStore.kt         SAF reads and writes, encoding and size checks
│   ├── EditorSettings.kt        persisted preferences
│   └── RecentFiles.kt           recent-file list
├── editor/                      pure Kotlin, unit tested
│   ├── Language.kt              language table and detection from file names
│   ├── SyntaxHighlighter.kt     single-pass tokenizer
│   ├── TextTools.kt             indent, find/replace, line arithmetic
│   └── UndoManager.kt           bounded undo/redo history
└── ui/
    ├── EditorViewModel.kt       document and editor state
    ├── EditorScreen.kt          scaffold, menus, dialogs, launchers
    ├── CodeEditor.kt            text field, gutter, highlight transformation
    ├── FindReplaceBar.kt
    ├── SettingsSheet.kt
    ├── SymbolBar.kt
    └── theme/Theme.kt           Material colours and the syntax palette
```

Everything in `editor/` is free of Android dependencies and covered by the JVM
tests in `app/src/test/`.

## Notes and limits

- Syntax highlighting is switched off automatically above 256 KB to keep typing
  responsive; the file still opens and edits normally.
- The tokenizer is a lexer, not a parser: it colours tokens, it does not
  understand the code.
- Only UTF-8 is supported. Other encodings are reported rather than mangled.
