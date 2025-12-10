package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
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
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jruby.embed.ScriptingContainer

/**
 * Ruby language runner using JRuby (Ruby on JVM).
 *
 * Features:
 * - Full Ruby 3.x support
 * - No JNI/NDK required (pure JVM)
 * - Access to Java libraries
 * - Ruby standard library
 * - Complete Ruby language features
 *
 * JRuby is a high-performance implementation of Ruby that runs on the JVM,
 * providing full compatibility with Ruby while offering Java interoperability.
 *
 * Recommended for: Ruby scripts, Rails apps, Java-Ruby interop
 */
class RubyJRubyRunner : LanguageRunner() {

    private var container: ScriptingContainer? = null

    override fun getLanguageName(): String = "Ruby"

    override fun getSupportedExtensions(): List<String> = listOf("rb")

    override fun getName(): String = "Ruby (JRuby)"

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
                // Create JRuby scripting container
                container = ScriptingContainer()
                
                container?.let { sc ->
                    // Redirect output streams
                    System.setOut(printStream)
                    System.setErr(errorPrintStream)

                    // Set output streams for Ruby
                    sc.output = printStream
                    sc.error = errorPrintStream

                    // Execute the code
                    val result = sc.runScriptlet(code)

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
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize JRuby container",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Ruby Error: ${e.message}",
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
        container?.terminate()
        container = null
    }
}
