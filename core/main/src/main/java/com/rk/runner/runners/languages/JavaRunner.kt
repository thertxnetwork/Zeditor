package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import bsh.EvalError
import bsh.Interpreter
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

/**
 * Java/BeanShell language runner using BeanShell interpreter.
 *
 * Features:
 * - Full Java syntax support
 * - Scripting capabilities without compilation
 * - No JNI/NDK required (pure JVM)
 * - Direct Java interop
 *
 * BeanShell allows you to run Java code without compilation.
 * Perfect for quick Java snippets and prototyping.
 */
class JavaRunner : LanguageRunner() {

    private var interpreter: Interpreter? = null

    override fun getLanguageName(): String = "Java (BeanShell)"

    override fun getSupportedExtensions(): List<String> = listOf("java", "bsh")

    override fun getName(): String = "Java (BeanShell)"

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
                    title = "Java Output",
                    msg = if (result.output.isNotEmpty()) result.output else "(No output)",
                    onOk = {}
                )
            } else {
                dialog(title = "Java Error", msg = result.errorOutput.ifEmpty { result.output }, onOk = {})
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
                interpreter = Interpreter()
                interpreter!!.out = printStream
                interpreter!!.err = errorPrintStream

                // Redirect System.out and System.err
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Add some useful imports by default
                val setupCode =
                    """
                    import java.util.*;
                    import java.io.*;
                    import java.lang.*;
                """
                        .trimIndent()

                interpreter!!.eval(setupCode)
                val result = interpreter!!.eval(code)

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
            } catch (e: EvalError) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Java Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Error: ${e.message}",
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
        interpreter = null
    }
}
