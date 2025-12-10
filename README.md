# Zeditor

**Zeditor** is an Android text editor based on Xed-Editor, offering advanced functionality such as syntax highlighting, extensive customization options, and a streamlined interface for efficient editing.

This is a refactored version of [Xed-Editor](https://github.com/Xed-Editor/Xed-Editor) with the package name changed to `com.thertxnetwork.zeditor`.

## Building

This project uses Gradle for building. To build the APK:

```bash
./gradlew assembleRelease
```

Or for debug builds:

```bash
./gradlew assembleDebug
```

## Features

- Syntax highlighting for multiple programming languages
- Extensive customization options
- Streamlined interface for efficient editing
- **Built-in Language Runners** - Run code directly without external tools:
  - **JavaScript** (Rhino) - Full ES5-ES6 support, runs directly on JVM
  - **TypeScript** - Basic transpilation to JavaScript
  - **Lua** (LuaJ) - Full Lua 5.2 support on JVM
  - **Java** (BeanShell) - Run Java snippets without compilation
  - **Groovy** - Full Groovy scripting support
  - **HTML/SVG** - Local web server preview
  - **Markdown** - Built-in preview
- **Language Support Info** - Guidance for running other languages:
  - Python (via Chaquopy, p4a, or Termux)
  - C/C++ (via NDK or Termux)
  - Go (via gomobile or Termux)
  - Rust (via cargo-ndk or Termux)
  - PHP, Ruby, Kotlin, Perl, R, and more
- LSP (Language Server Protocol) support

## Supported Language Runners

| Language | Runner | Type | Notes |
|----------|--------|------|-------|
| JavaScript | Rhino | JVM | Full ES5-ES6, console.log support |
| TypeScript | Rhino | JVM | Basic transpilation, runs as JS |
| Lua | LuaJ | JVM | Full Lua 5.2, standard library |
| Java | BeanShell | JVM | Script-style Java execution |
| Groovy | GroovyShell | JVM | Full Groovy with closures/DSL |
| HTML/SVG | HttpServer | Web | Local server at port 8357 |
| Markdown | WebView | Web | Built-in preview |
| Python | Info | - | Setup instructions provided |
| C/C++ | Info | - | NDK/Termux instructions |
| Go | Info | - | gomobile/Termux instructions |
| Rust | Info | - | cargo-ndk/Termux instructions |

## Credits

This project is based on [Xed-Editor](https://github.com/Xed-Editor/Xed-Editor) by the Xed-Editor team.

## License

See the LICENSE file for details.
