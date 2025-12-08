# Terminal Feature Testing Guide

## Overview
The terminal integration allows you to run code and shell scripts directly from Zeditor. The implementation uses **Termux's terminal-view and terminal-emulator libraries**, providing a professional terminal experience with features like:
- Full ANSI escape code support and colors
- zsh/bash syntax highlighting (when using those shells)
- Better text rendering and scrolling
- Support for complex terminal applications (nano, vim, etc.)
- Advanced terminal features from the Termux project

## How to Access the Terminal

### Method 1: Top Bar Button
- Look for the terminal (bash) icon in the top application bar
- Click it to open a new terminal session

### Method 2: Add Menu
- Click the "+" button in the top bar
- Select "Terminal" from the menu
- A new terminal session will open

### Method 3: File Runners
When you have a supported file open:
- Click the "Run" button
- For supported file types, the code will execute in the terminal

## Supported File Types

### Script Files
- `.sh`, `.bash`, `.zsh`, `.fish` - Shell scripts
- These run directly with `/system/bin/sh`

### Programming Languages
- `.py` - Python (tries python3 first, falls back to python)
- `.js` - JavaScript (via Node.js)
- `.java` - Java (compiles and runs)
- `.kt` - Kotlin (compiles and runs)
- `.rs` - Rust (compiles with rustc and runs)
- `.rb` - Ruby
- `.php` - PHP
- `.c` - C (compiles with gcc and runs)
- `.cpp`, `.cc`, `.cxx` - C++ (compiles with g++ and runs)
- `.cs` - C# (compiles with mcs, runs with mono)
- `.pl` - Perl
- `.lua` - Lua
- `.r`, `.R` - R (via Rscript)
- `.go` - Go (go run)
- `.ts` - TypeScript (via ts-node)

## Testing the Terminal

### Test 1: Basic Shell Commands
1. Open terminal from top bar
2. Try these commands:
```bash
echo "Hello from Zeditor Terminal"
ls
pwd
whoami
date
```

### Test 2: Simple Python Script
1. Create a new file named `test.py`
2. Add this content:
```python
print("Hello from Python!")
for i in range(5):
    print(f"Count: {i}")
```
3. Click the Run button
4. Terminal should open and execute the script

### Test 3: Shell Script
1. Create a new file named `test.sh`
2. Add this content:
```bash
#!/bin/sh
echo "Testing shell script"
echo "Current directory: $(pwd)"
echo "Files:"
ls -la
```
3. Click the Run button
4. Terminal should execute the script

### Test 4: JavaScript/Node.js
1. Create a new file named `test.js`
2. Add this content:
```javascript
console.log("Hello from Node.js!");
console.log("Process ID:", process.pid);
```
3. Click the Run button
4. Terminal should execute via Node.js (if installed)

## Terminal Features

### Input Methods
- **Input Field**: Type commands in the bottom input field and press send button or Enter
- **Keyboard**: Direct keyboard input is captured by the terminal view
- **Touch**: Tap terminal to show keyboard

### Display Features (via Termux Terminal)
- Full ANSI escape code support with 256 colors
- Proper text rendering with ligatures and Unicode support
- Smooth scrolling and text selection
- Customizable terminal themes
- Support for complex terminal applications (nano, vim, htop, etc.)
- Proper handling of control characters

### Process Handling
- Terminal runs in a separate process using Termux's TerminalSession
- Output is streamed in real-time with proper buffering
- Input is sent directly to the process
- Session ends when process completes
- Support for interactive programs

## Known Limitations

1. **Interpreter Availability**: Some language runners require the interpreter to be installed on the Android device
   - Python: Usually available on Android
   - Node.js, Ruby, Go, etc.: May need to be installed via Termux or similar

2. **Working Directory**: Scripts run in their parent directory, but compiled languages need write access to compile

## Troubleshooting

### "Command not found" errors
- The interpreter for the language may not be installed
- Try installing interpreters via Termux or a similar app
- Basic shell commands should always work

### Script doesn't run
- Check file permissions
- Verify the file extension matches the runner pattern
- Look at the terminal output for error messages

### Terminal appears but shows no output
- The command may be running but not producing output
- Try adding `echo` statements to debug
- Check if the process is waiting for input

## Future Enhancements

Potential improvements for the terminal feature:
- Terminal themes and additional customization options
- Tab support for multiple terminal sessions
- Integration with Termux packages for full Linux environment
- Full PRoot/Ubuntu integration (as described in ubuntu_terminal_blog.md)

## Reference
The terminal implementation uses **Termux's terminal-view and terminal-emulator libraries** from the [Termux project](https://github.com/termux/termux-app), which provides a professional-grade terminal emulator for Android. The blog post `ubuntu_terminal_blog.md` describes how to extend this with full Ubuntu rootfs and PRoot support.
