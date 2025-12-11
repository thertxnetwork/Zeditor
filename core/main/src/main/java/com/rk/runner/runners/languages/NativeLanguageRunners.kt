package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ExecutionActivity
import com.rk.runner.RunnerImpl
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * C/C++ runner - Shows information about compiling C/C++ on Android.
 *
 * Note: C/C++ requires compilation before execution. Runtime interpretation
 * is not possible without a pre-compiled interpreter binary.
 */
class CppRunner : RunnerImpl() {

    override fun getName(): String = "C/C++ Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_cpp.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            C/C++ requires a native compiler.
            
            Options for running C/C++ on Android:
            
            1. Android NDK (Official)
               - Full C/C++ support
               - Native performance
               - Integrated with Android Studio
               
            2. Termux (External)
               - Install: pkg install clang
               - Compile: clang ${fileObject.getName()} -o program
               - Run: ./program
               
            3. CppDroid (External App)
               - C/C++ IDE for Android
               - Offline compiler
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "C/C++ Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Go runner - Information about Go on Android.
 *
 * Note: Go (via gomobile) requires pre-compilation. You cannot execute
 * arbitrary Go code at runtime - all Go code must be compiled ahead of time.
 * gomobile generates native libraries that can be called from Java/Kotlin.
 */
class GoRunner : RunnerImpl() {

    override fun getName(): String = "Go (gomobile)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            🐹 Go Language on Android
            
            ⚠️ IMPORTANT: Go cannot execute arbitrary code at runtime.
            All Go code must be pre-compiled before use.
            
            ═══════════════════════════════════
            HOW GOMOBILE WORKS
            ═══════════════════════════════════
            
            gomobile compiles Go code into native libraries
            (.aar files) that can be called from Java/Kotlin.
            
            Steps to use Go in your app:
            
            1. Install gomobile:
               go install golang.org/x/mobile/cmd/gomobile@latest
            
            2. Initialize gomobile:
               gomobile init
            
            3. Create a Go package with exported functions:
               package mylib
               func Hello(name string) string {
                   return "Hello, " + name
               }
            
            4. Build for Android:
               gomobile bind -target=android ./mylib
            
            5. Import the generated .aar in your Android project
            
            ═══════════════════════════════════
            WHY RUNTIME EXECUTION ISN'T POSSIBLE
            ═══════════════════════════════════
            
            Go is a compiled language. Unlike Python or JavaScript,
            there's no Go interpreter that can execute code at runtime.
            
            Alternative: Use Termux
            - Install: pkg install golang
            - Run: go run ${fileObject.getName()}
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Go Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Rust runner - Shows information about running Rust on Android.
 */
class RustRunner : RunnerImpl() {

    override fun getName(): String = "Rust Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            Rust requires the Rust compiler to run.
            
            Options for running Rust on Android:
            
            1. cargo-ndk (Recommended)
               - Full Rust support via NDK/JNI
               - Native performance
               - Easy Android integration
               
            2. Mozilla's Rust Android
               - Official Mozilla toolchain
               - Full language support
               
            3. Termux (External)
               - Install: pkg install rust
               - Run: rustc ${fileObject.getName()} && ./program
               
            4. Cross-compilation
               - Build on PC with Android target
               - Transfer and run on Android
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Rust Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * PHP runner - Information about PHP on Android.
 *
 * Note: Full PHP execution requires cross-compiling PHP for Android NDK.
 * This is a complex process that requires building PHP as a native library.
 */
class PhpRunner : RunnerImpl() {

    override fun getName(): String = "PHP (Native JNI)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            🐘 PHP on Android
            
            ⚠️ PHP runtime execution requires native libraries.
            
            ═══════════════════════════════════
            INTEGRATION OPTIONS
            ═══════════════════════════════════
            
            1. Cross-compile PHP for Android:
               - Download PHP source from php.net
               - Configure for Android NDK toolchain
               - Build as shared library (.so)
               - Create JNI bindings
               - Add to app's jniLibs folder
            
            2. Use pre-built PHP libraries:
               - Some projects provide pre-compiled PHP
               - Look for php-android or similar
            
            ═══════════════════════════════════
            WHY IT'S COMPLEX
            ═══════════════════════════════════
            
            PHP is written in C and requires:
            - NDK cross-compilation setup
            - Android-specific patches
            - JNI wrapper code
            - Proper library loading
            
            This is significantly more complex than
            using interpreted languages like Python
            (which Chaquopy handles automatically).
            
            ═══════════════════════════════════
            RECOMMENDED ALTERNATIVE
            ═══════════════════════════════════
            
            Use Termux for PHP development:
            - Install: pkg install php
            - Run: php ${fileObject.getName()}
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "PHP Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Ruby runner - Shows information about running Ruby on Android.
 */
class RubyRunner : RunnerImpl() {

    override fun getName(): String = "Ruby Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            Ruby requires a Ruby interpreter to run.
            
            Options for running Ruby on Android:
            
            1. JRuby (Recommended for Android)
               - Full Ruby 2.6/3.x on JVM
               - Can be integrated as dependency
               - No JNI required
               
            2. mruby-android
               - Lightweight Ruby via JNI
               - Smaller footprint
               
            3. Termux (External)
               - Install: pkg install ruby
               - Run: ruby ${fileObject.getName()}
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Ruby Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Kotlin runner - Shows information about running Kotlin scripts.
 */
class KotlinScriptRunner : RunnerImpl() {

    override fun getName(): String = "Kotlin Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_kotlin.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            Kotlin is Android's official language!
            
            Options for running Kotlin:
            
            1. Android Native
               - Kotlin is built into Android
               - Compile with Android Studio
               - Full language support
               
            2. Kotlin Scripting (.kts)
               - Kotlin scripts can run directly
               - Requires Kotlin compiler
               
            3. Termux (External)
               - Install Kotlin compiler
               - Run: kotlinc -script ${fileObject.getName()}
               
            4. Build as Android App
               - Create a new module
               - Add this file to the project
               - Build and run
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Kotlin Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Shell script runner - Shows information about running shell scripts.
 */
class ShellRunner : RunnerImpl() {

    override fun getName(): String = "Shell Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.bash.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            Shell scripts require a terminal to run.
            
            Options for running shell scripts:
            
            1. Termux (Recommended)
               - Full Linux environment
               - Install: Download from F-Droid
               - Run: bash ${fileObject.getName()}
               
            2. ADB Shell
               - Connect via USB
               - Run: adb shell < ${fileObject.getName()}
               
            3. Root Terminal (Requires Root)
               - Direct shell access
               - Full system access
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Shell Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * Perl runner - Shows information about running Perl on Android.
 */
class PerlRunner : RunnerImpl() {

    override fun getName(): String = "Perl Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            Perl requires a Perl interpreter to run.
            
            Options for running Perl on Android:
            
            1. perl-android
               - Full Perl 5 via JNI/NDK
               - Can be integrated into apps
               
            2. SL4A (Scripting Layer for Android)
               - Includes Perl support
               - Easy scripting interface
               
            3. Termux (External)
               - Install: pkg install perl
               - Run: perl ${fileObject.getName()}
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "Perl Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}

/**
 * R language runner - Shows information about running R on Android.
 */
class RLangRunner : RunnerImpl() {

    override fun getName(): String = "R Info"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        val message =
            """
            R requires an R interpreter to run.
            
            Options for running R on Android:
            
            1. Renjin (Recommended)
               - Full R implementation on JVM
               - Can be added as dependency
               - No native code required
               
            2. Native R via JNI
               - Full R via custom build
               - More complex setup
               
            3. Termux (External)
               - Install: pkg install r-base
               - Run: R -f ${fileObject.getName()}
            
            File: ${fileObject.getName()}
            """
                .trimIndent()

        withContext(Dispatchers.Main) { dialog(title = "R Runner", msg = message, onOk = {}) }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}
