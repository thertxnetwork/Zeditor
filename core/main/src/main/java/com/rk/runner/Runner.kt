package com.rk.runner

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.runner.runners.languages.ClojureRunner
import com.rk.runner.runners.languages.CommonLispRunner
import com.rk.runner.runners.languages.CppRunner
import com.rk.runner.runners.languages.GoActualRunner
import com.rk.runner.runners.languages.GroovyRunner
import com.rk.runner.runners.languages.JavaRunner
import com.rk.runner.runners.languages.JavaScriptRunner
import com.rk.runner.runners.languages.KotlinScriptActualRunner
import com.rk.runner.runners.languages.KotlinScriptRunner
import com.rk.runner.runners.languages.LuaRunner
import com.rk.runner.runners.languages.PerlRunner
import com.rk.runner.runners.languages.PhpNativeRunner
import com.rk.runner.runners.languages.PhpRunner
import com.rk.runner.runners.languages.PrologRunner
import com.rk.runner.runners.languages.PythonNativeRunner
import com.rk.runner.runners.languages.PythonRunner
import com.rk.runner.runners.languages.RLangRunner
import com.rk.runner.runners.languages.RubyNativeRunner
import com.rk.runner.runners.languages.RubyRunner
import com.rk.runner.runners.languages.RustRunner
import com.rk.runner.runners.languages.SchemeRunner
import com.rk.runner.runners.languages.ShellRunner
import com.rk.runner.runners.languages.TypeScriptRunner
import com.rk.runner.runners.web.html.HtmlRunner
import com.rk.runner.runners.web.markdown.MarkDownRunner
import com.rk.utils.errorDialog
import java.lang.ref.WeakReference
import kotlin.text.Regex

abstract class RunnerImpl() {
    abstract suspend fun run(context: Context, fileObject: FileObject)

    abstract fun getName(): String

    abstract fun getIcon(context: Context): Drawable?

    abstract suspend fun isRunning(): Boolean

    abstract suspend fun stop()
}

var currentRunner = WeakReference<RunnerImpl?>(null)

abstract class RunnerBuilder(val regex: Regex, val clazz: Class<out RunnerImpl>) {
    fun build(): RunnerImpl {
        return clazz.getDeclaredConstructor().newInstance()
    }
}

object Runner {
    val runnerBuilders = mutableListOf<RunnerBuilder>()

    init {
        runnerBuilders.apply {
            // Web runners
            add(object : RunnerBuilder(regex = Regex(".*\\.(html|svg)$"), clazz = HtmlRunner::class.java) {})
            add(object : RunnerBuilder(regex = Regex(".*\\.md$"), clazz = MarkDownRunner::class.java) {})

            // ========================================
            // JVM-based interpreters (run directly, no terminal needed)
            // ========================================

            // Lua - LuaJ (Full Lua 5.2 on JVM)
            add(object : RunnerBuilder(regex = Regex(".*\\.lua$"), clazz = LuaRunner::class.java) {})

            // JavaScript - Rhino (Full ES5-ES6 on JVM)
            add(object : RunnerBuilder(regex = Regex(".*\\.js$"), clazz = JavaScriptRunner::class.java) {})

            // TypeScript - Rhino with basic transpilation
            add(object : RunnerBuilder(regex = Regex(".*\\.ts$"), clazz = TypeScriptRunner::class.java) {})

            // Groovy - Full Groovy on JVM
            add(object : RunnerBuilder(regex = Regex(".*\\.(groovy|gvy|gy|gsh)$"), clazz = GroovyRunner::class.java) {})

            // Java - BeanShell (Script-style Java execution)
            add(object : RunnerBuilder(regex = Regex(".*\\.(java|bsh)$"), clazz = JavaRunner::class.java) {})

            // Clojure - Full Clojure on JVM
            add(object : RunnerBuilder(regex = Regex(".*\\.(clj|cljs|cljc)$"), clazz = ClojureRunner::class.java) {})

            // Kotlin Script - Full Kotlin scripting support
            add(object : RunnerBuilder(regex = Regex(".*\\.kts$"), clazz = KotlinScriptActualRunner::class.java) {})

            // ========================================
            // Native/System-based runners (execute via system interpreters)
            // ========================================

            // Python - Native via system interpreter (Termux)
            add(object : RunnerBuilder(regex = Regex(".*\\.py$"), clazz = PythonNativeRunner::class.java) {})

            // Ruby - Native via system interpreter (Termux)
            add(object : RunnerBuilder(regex = Regex(".*\\.rb$"), clazz = RubyNativeRunner::class.java) {})

            // PHP - Native via system interpreter (Termux)
            add(object : RunnerBuilder(regex = Regex(".*\\.php$"), clazz = PhpNativeRunner::class.java) {})

            // Go - Native via system compiler (Termux)
            add(object : RunnerBuilder(regex = Regex(".*\\.go$"), clazz = GoActualRunner::class.java) {})

            // ========================================
            // Info runners (show setup instructions for languages that need native compilers)
            // ========================================

            // C/C++ - Info about NDK, Termux
            add(
                object :
                    RunnerBuilder(regex = Regex(".*\\.(c|cpp|cc|cxx|h|hpp)$"), clazz = CppRunner::class.java) {}
            )

            // Rust - Info about cargo-ndk, Termux
            add(object : RunnerBuilder(regex = Regex(".*\\.rs$"), clazz = RustRunner::class.java) {})

            // Kotlin (non-script) - Info about Android native, compilation
            add(object : RunnerBuilder(regex = Regex(".*\\.kt$"), clazz = KotlinScriptRunner::class.java) {})

            // Shell scripts - Info about Termux, ADB
            add(
                object :
                    RunnerBuilder(
                        regex = Regex(".*\\.(sh|bash|zsh|fish)$"),
                        clazz = ShellRunner::class.java
                    ) {}
            )

            // Perl - Info about perl-android, SL4A, Termux
            add(object : RunnerBuilder(regex = Regex(".*\\.pl$"), clazz = PerlRunner::class.java) {})

            // R - Info about Renjin, Termux
            add(object : RunnerBuilder(regex = Regex(".*\\.(r|R)$"), clazz = RLangRunner::class.java) {})

            // Scheme - Info about Kawa, Termux
            add(object : RunnerBuilder(regex = Regex(".*\\.(scm|ss|sch)$"), clazz = SchemeRunner::class.java) {})

            // Prolog - Info about tuProlog, SWI-Prolog
            add(object : RunnerBuilder(regex = Regex(".*\\.(pro|prolog)$"), clazz = PrologRunner::class.java) {})

            // Common Lisp - Info about ABCL, ECL
            add(object : RunnerBuilder(regex = Regex(".*\\.(lisp|lsp|cl)$"), clazz = CommonLispRunner::class.java) {})
        }
    }

    fun isRunnable(fileObject: FileObject): Boolean {
        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                return true
            }
        }

        runnerBuilders.forEach {
            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)) {
                return true
            }
        }
        return false
    }

    suspend fun run(context: Context, fileObject: FileObject, onMultipleRunners: (List<RunnerImpl>) -> Unit) {
        val availableRunners = mutableListOf<RunnerImpl>()

        ShellBasedRunners.runners.forEach {
            val name = fileObject.getName()
            val regex = Regex(it.regex)

            if (regex.matches(name)) {
                availableRunners.add(it)
            }
        }

        runnerBuilders.forEach {
            val name = fileObject.getName()
            val regex = it.regex

            if (regex.matches(name)) {
                availableRunners.add(it.build())
            }
        }

        if (availableRunners.isEmpty()) {
            errorDialog("No runners available")
            return
        }

        if (availableRunners.size == 1) {
            availableRunners[0].run(context, fileObject)
        } else {
            onMultipleRunners.invoke(availableRunners)
        }
    }

    suspend fun onMainActivityResumed() {
        currentRunner.get()?.stop()
    }
}
