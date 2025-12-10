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
import gnu.expr.Language
import gnu.kawa.io.CharArrayOutPort
import gnu.kawa.io.OutPort
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scheme language runner using Kawa (full Scheme R7RS implementation on JVM).
 *
 * Features:
 * - Full Scheme R7RS support
 * - No JNI/NDK required (pure JVM)
 * - Complete standard library
 * - Functional programming
 * - Lisp-family language
 * - Java interoperability
 *
 * Kawa is a mature, feature-rich Scheme implementation that compiles Scheme
 * code to Java bytecode, providing excellent performance.
 *
 * Recommended for: Functional programming, education, scripting
 */
class SchemeRunner : LanguageRunner() {

    override fun getLanguageName(): String = "Scheme"

    override fun getSupportedExtensions(): List<String> = listOf("scm", "ss", "sch")

    override fun getName(): String = "Scheme (Kawa)"

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
                // Get Scheme language instance
                val scheme = Language.getInstance("scheme")
                
                // Redirect output streams
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Create output port for Kawa
                val outPort = CharArrayOutPort()
                OutPort.setOutDefault(outPort)

                // Evaluate the code
                val result = scheme.eval(code)

                System.setOut(originalOut)
                System.setErr(originalErr)

                val executionTime = System.currentTimeMillis() - startTime
                val kawaOutput = outPort.toString()
                val systemOutput = outputStream.toString("UTF-8")
                val errorOutput = errorStream.toString("UTF-8")

                // Combine Kawa output and system output
                val combinedOutput = when {
                    kawaOutput.isNotEmpty() && systemOutput.isNotEmpty() -> 
                        "$systemOutput$kawaOutput"
                    kawaOutput.isNotEmpty() -> kawaOutput
                    systemOutput.isNotEmpty() -> systemOutput
                    else -> ""
                }

                val finalOutput = when {
                    combinedOutput.isNotEmpty() -> combinedOutput
                    result != null && result.toString() != "#!void" -> result.toString()
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
                    errorOutput = "Scheme Error: ${e.message}",
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
