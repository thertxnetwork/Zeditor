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
import org.python.util.PythonInterpreter
import org.python.core.PySystemState

/**
 * Python language runner using Jython (Python on JVM).
 *
 * Features:
 * - Full Python 2.7 support
 * - No JNI/NDK required (pure JVM)
 * - Access to Java libraries
 * - Python standard library
 *
 * Jython is a Java implementation of Python that compiles Python code to Java bytecode.
 * Note: This is Python 2.7 compatible. For Python 3.x, external tools like Chaquopy are needed.
 *
 * Recommended for: Python 2.7 scripts, Java-Python interop, scripting
 */
class PythonJythonRunner : LanguageRunner() {

    private var interpreter: PythonInterpreter? = null

    override fun getLanguageName(): String = "Python"

    override fun getSupportedExtensions(): List<String> = listOf("py")

    override fun getName(): String = "Python (Jython 2.7)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_python.getDrawable(context)
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
                // Initialize Python system state
                val systemState = PySystemState()
                
                // Create interpreter with custom system state
                interpreter = PythonInterpreter(null, systemState)
                
                interpreter?.let { interp ->
                    // Redirect output streams
                    System.setOut(printStream)
                    System.setErr(errorPrintStream)

                    // Set output streams for Python
                    interp.setOut(printStream)
                    interp.setErr(errorPrintStream)

                    // Execute the code
                    interp.exec(code)

                    System.setOut(originalOut)
                    System.setErr(originalErr)

                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputStream.toString("UTF-8")
                    val errorOutput = errorStream.toString("UTF-8")

                    val finalOutput = when {
                        output.isNotEmpty() -> output
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
                    errorOutput = "Failed to initialize Jython interpreter",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Python Error: ${e.message}",
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
        interpreter?.cleanup()
        interpreter?.close()
        interpreter = null
    }
}
