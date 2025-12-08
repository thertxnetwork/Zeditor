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
- **Terminal support** - Integrated terminal powered by Termux libraries
  - Professional terminal emulator with full ANSI color support
  - Run Python, JavaScript, Java, Kotlin, C/C++, Rust, and many more languages
  - Execute shell scripts directly from the editor
  - Support for complex terminal apps (nano, vim, htop, etc.)
  - zsh/bash syntax highlighting when using those shells
  - Access terminal via top bar button or "Add" menu
- LSP (Language Server Protocol) support

## Credits

This project is based on [Xed-Editor](https://github.com/Xed-Editor/Xed-Editor) by the Xed-Editor team.

## License

See the LICENSE file for details.
