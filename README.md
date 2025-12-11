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
- **SSH Code Runner** - Execute code on remote VPS servers:
  - Configure multiple servers with password or SSH key authentication
  - Support for Ubuntu, CentOS, Fedora, Arch, Alpine, openSUSE
  - Interactive terminal with real-time output
  - Automatic file upload and execution
  - Works with Python, Node.js, Go, Rust, C/C++, Java, and more
  - [See SSH Code Runner documentation](docs/SSH_CODE_RUNNER.md)
- **Built-in Language Runners** - Run code directly without external tools:
  - **JavaScript** (V8) - Full ES6+ support via Google V8 engine
  - **TypeScript** - Basic transpilation to JavaScript
  - **Lua** (LuaJ) - Full Lua 5.2 support on JVM
  - **Groovy** - Full Groovy scripting support
  - **HTML/SVG** - Local web server preview
  - **Markdown** - Built-in preview
- **Language Support Info** - Guidance for running other languages:
  - Python (via SSH VPS or Termux)
  - C/C++ (via SSH VPS, NDK or Termux)
  - Go (via SSH VPS or Termux)
  - Rust (via SSH VPS or Termux)
  - PHP, Ruby, Kotlin, Perl, R, and more
- LSP (Language Server Protocol) support

## Supported Language Runners

| Language | Runner | Type | Notes |
|----------|--------|------|-------|
| **Any Language** | **SSH VPS** | **Remote** | **Run on your VPS server via SSH** |
| JavaScript | V8 | JVM/Native | Full ES6+, Google V8 engine |
| TypeScript | V8 | JVM/Native | Basic transpilation, runs as JS |
| Lua | LuaJ | JVM | Full Lua 5.2, standard library |
| Groovy | GroovyShell | JVM | Full Groovy with closures/DSL |
| HTML/SVG | HttpServer | Web | Local server at port 8357 |
| Markdown | WebView | Web | Built-in preview |
| Python | SSH/Info | Remote | Via SSH VPS or Termux |
| C/C++ | SSH/Info | Remote | Via SSH VPS, NDK or Termux |
| Go | SSH/Info | Remote | Via SSH VPS or Termux |
| Rust | SSH/Info | Remote | Via SSH VPS or Termux |
| PHP/Ruby/Perl | SSH/Info | Remote | Via SSH VPS or Termux |

## Credits

This project is based on [Xed-Editor](https://github.com/Xed-Editor/Xed-Editor) by the Xed-Editor team.

## License

See the LICENSE file for details.
