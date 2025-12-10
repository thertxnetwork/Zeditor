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
import java.io.StringReader
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.armedbear.lisp.Interpreter
import org.armedbear.lisp.Stream

/**
 * Common Lisp language runner using ABCL (Armed Bear Common Lisp).
 *
 * Features:
 * - Full Common Lisp support
 * - No JNI/NDK required (pure JVM)
 * - ANSI Common Lisp compliance
 * - Access to Java libraries
 * - Functional and object-oriented programming
 *
 * ABCL is a full implementation of Common Lisp running on the JVM,
 * providing excellent Java interoperability.
 *
 * Recommended for: Lisp programming, AI applications, symbolic computation
 */
class CommonLispRunner : LanguageRunner() {

    private var interpreter: Interpreter? = null

    override fun getLanguageName(): String = "Common Lisp"

    override fun getSupportedExtensions(): List<String> = listOf("lisp", "lsp", "cl")

    override fun getName(): String = "Common Lisp (ABCL)"

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
                // Create ABCL interpreter
                interpreter = Interpreter.createInstance()
                
                interpreter?.let { interp ->
                    // Redirect output streams
                    System.setOut(printStream)
                    System.setErr(errorPrintStream)

                    // Redirect Lisp standard output
                    val outputLispStream = Stream.createCharacterOutputStream(printStream)
                    interp.eval("(setq *standard-output* (make-two-way-stream *standard-input* " +
                               "(make-synonym-stream '*terminal-io*)))")

                    // Evaluate the code
                    val reader = StringReader(code)
                    var result: org.armedbear.lisp.LispObject? = null
                    
                    // Read and evaluate all forms
                    while (true) {
                        try {
                            val form = interp.read(reader, false, null)
                            if (form == null) break
                            result = interp.eval(form)
                        } catch (e: Exception) {
                            if (e.message?.contains("end of file") == true) {
                                break
                            }
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
                        result != null -> result.writeToString()
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
                    errorOutput = "Failed to initialize ABCL interpreter",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Common Lisp Error: ${e.message}",
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
