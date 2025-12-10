package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import clojure.lang.Compiler
import clojure.lang.RT
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PushbackReader
import java.io.StringReader
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clojure language runner using the official Clojure JVM implementation.
 *
 * Features:
 * - Full Clojure language support
 * - Functional programming capabilities
 * - No JNI/NDK required (pure JVM)
 * - Complete Clojure standard library
 * - Lisp-style syntax
 * - Immutable data structures
 *
 * Clojure is a modern, dynamic, functional dialect of Lisp that runs on the JVM.
 * Perfect for functional programming, data manipulation, and scripting.
 *
 * Recommended for: Functional programming, data processing, scripting
 */
class ClojureRunner : LanguageRunner() {

    override fun getLanguageName(): String = "Clojure"

    override fun getSupportedExtensions(): List<String> = listOf("clj", "cljs", "cljc")

    override fun getName(): String = "Clojure"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)
        isCurrentlyRunning = true

        val code = withContext(Dispatchers.IO) { fileObject.readText() }

        val result = executeCode(code)

        withContext(Dispatchers.Main) {
            showExecutionResult(context, result, fileObject.getName())
        }

        isCurrentlyRunning = false
    }

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            val errorPrintStream = PrintStream(errorStream)
            val originalOut = System.out
            val originalErr = System.err

            try {
                // Initialize Clojure runtime if needed
                RT.init()

                // Redirect output streams
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Bind *out* and *err* for Clojure printing
                val outWriter = java.io.PrintWriter(printStream, true)
                val errWriter = java.io.PrintWriter(errorPrintStream, true)
                
                RT.OUT.bindRoot(outWriter)
                RT.ERR.bindRoot(errWriter)

                // Evaluate the code
                val reader = PushbackReader(StringReader(code))
                var result: Any? = null
                
                try {
                    while (true) {
                        val form = clojure.lang.LispReader.read(reader, false, null, false)
                        if (form == null) break
                        result = Compiler.eval(form)
                    }
                } catch (e: Exception) {
                    if (e.message?.contains("EOF") != true) {
                        throw e
                    }
                }

                System.setOut(originalOut)
                System.setErr(originalErr)

                val executionTime = System.currentTimeMillis() - startTime
                val output = outputStream.toString("UTF-8")
                val errorOutput = errorStream.toString("UTF-8")

                val finalOutput = when {
                    output.isNotEmpty() -> output
                    result != null -> result.toString()
                    else -> "(Execution completed in ${executionTime}ms)"
                }

                ExecutionResult(
                    output = finalOutput,
                    errorOutput = errorOutput,
                    isSuccess = errorOutput.isEmpty(),
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Clojure Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                printStream.close()
                errorPrintStream.close()
                outputStream.close()
                errorStream.close()
            }
        }
    }

    override suspend fun stop() {
        super.stop()
    }
}
