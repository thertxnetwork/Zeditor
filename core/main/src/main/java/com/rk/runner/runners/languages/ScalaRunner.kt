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
import java.io.PrintWriter
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import scala.tools.nsc.Settings
import scala.tools.nsc.interpreter.IMain
import scala.tools.nsc.interpreter.Results

/**
 * Scala language runner using Scala REPL.
 *
 * Features:
 * - Full Scala language support
 * - No JNI/NDK required (pure JVM)
 * - Functional and object-oriented programming
 * - Access to Java libraries
 * - Advanced type system
 *
 * Scala is a powerful multi-paradigm language that runs on the JVM,
 * combining functional and object-oriented programming.
 *
 * Recommended for: Functional programming, data processing, type-safe code
 */
class ScalaRunner : LanguageRunner() {

    private var interpreter: IMain? = null

    override fun getLanguageName(): String = "Scala"

    override fun getSupportedExtensions(): List<String> = listOf("scala", "sc")

    override fun getName(): String = "Scala"

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
                // Create Scala settings
                val settings = Settings()
                settings.usejavacp().value_$eq(true)

                // Redirect output streams
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Create Scala interpreter
                val outWriter = PrintWriter(printStream, true)
                interpreter = new IMain(settings, outWriter)

                interpreter?.let { interp ->
                    // Execute the code line by line
                    val lines = code.split("\n")
                    var lastResult: Results.Result = Results.Success()
                    
                    for (line in lines) {
                        if (line.trim().isNotEmpty()) {
                            lastResult = interp.interpret(line)
                            if (lastResult == Results.Error()) {
                                break
                            }
                        }
                    }

                    System.setOut(originalOut)
                    System.setErr(originalErr)

                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputStream.toString("UTF-8")
                    val errorOutput = errorStream.toString("UTF-8")

                    val finalOutput = when {
                        output.isNotEmpty() -> output
                        else -> "(Execution completed in ${executionTime}ms)"
                    }

                    val isSuccess = lastResult != Results.Error() && errorOutput.isEmpty()

                    ExecutionResult(
                        output = finalOutput,
                        errorOutput = errorOutput,
                        isSuccess = isSuccess,
                        executionTimeMs = executionTime
                    )
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize Scala interpreter",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Scala Error: ${e.message}",
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
        interpreter?.close()
        interpreter = null
    }
}
