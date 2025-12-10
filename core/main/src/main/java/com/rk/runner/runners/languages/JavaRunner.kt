package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
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
 * Java language runner using BeanShell (pure Java interpreter).
 *
 * Features:
 * - Run Java code without compilation
 * - Script-style Java execution
 * - No JNI/NDK required (pure JVM)
 * - Dynamic Java scripting
 * - Access to Java standard library
 *
 * BeanShell allows running Java code in a scripting mode, making it perfect
 * for quick prototyping and testing Java code snippets.
 *
 * Recommended for: Java snippets, quick testing, scripting tasks
 */
class JavaRunner : LanguageRunner() {

    private var interpreter: Interpreter? = null

    override fun getLanguageName(): String = "Java"

    override fun getSupportedExtensions(): List<String> = listOf("java", "bsh")

    override fun getName(): String = "Java (BeanShell)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_java.getDrawable(context)
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
                interpreter = Interpreter()
                interpreter?.let { interp ->
                    // Redirect output streams
                    System.setOut(printStream)
                    System.setErr(errorPrintStream)

                    // Set output streams in BeanShell
                    interp.setOut(printStream)
                    interp.setErr(errorPrintStream)

                    // Evaluate the code
                    val result = interp.eval(code)

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
                    errorOutput = "Failed to initialize BeanShell interpreter",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Java Error: ${e.message}",
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
