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
import groovy.lang.Binding
import groovy.lang.GroovyShell
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Groovy language runner using GroovyShell.
 *
 * Features:
 * - Full Groovy language support
 * - Java interoperability
 * - No JNI/NDK required (pure JVM)
 * - Dynamic scripting capabilities
 * - Closures and DSL support
 *
 * Recommended for: scripting, DSLs, and Java-compatible code
 */
class GroovyRunner : LanguageRunner() {

    private var shell: GroovyShell? = null

    override fun getLanguageName(): String = "Groovy"

    override fun getSupportedExtensions(): List<String> = listOf("groovy", "gvy", "gy", "gsh")

    override fun getName(): String = "Groovy"

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
            if (result.isSuccess) {
                dialog(
                    title = "Groovy Output",
                    msg = if (result.output.isNotEmpty()) result.output else "(No output)",
                    onOk = {}
                )
            } else {
                dialog(title = "Groovy Error", msg = result.errorOutput.ifEmpty { result.output }, onOk = {})
            }
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
                val binding = Binding()
                binding.setVariable("out", printStream)

                shell = GroovyShell(binding)

                // Redirect System.out and System.err
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                val result = shell!!.evaluate(code)

                System.setOut(originalOut)
                System.setErr(originalErr)

                val executionTime = System.currentTimeMillis() - startTime
                val output = outputStream.toString("UTF-8")
                val errorOutput = errorStream.toString("UTF-8")

                val finalOutput =
                    when {
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
                    errorOutput = "Groovy Error: ${e.message}",
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
        shell = null
    }
}
