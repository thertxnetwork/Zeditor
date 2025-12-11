package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8ScriptExecutionException
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ExecutionActivity
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JavaScript language runner using J2V8 (V8 JavaScript engine).
 *
 * Features:
 * - Full ES2020+ JavaScript support via V8 engine
 * - High performance native execution
 * - Modern JavaScript features support
 * - Better performance than Rhino
 *
 * V8 is the JavaScript engine used in Chrome and Node.js.
 */
class JavaScriptRunner : LanguageRunner() {

    private var v8Runtime: V8? = null
    private val outputBuffer = StringBuilder()

    override fun getLanguageName(): String = "JavaScript"

    override fun getSupportedExtensions(): List<String> = listOf("js")

    override fun getName(): String = "JavaScript (J2V8)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_js.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)
        isCurrentlyRunning = true

        // Launch the ExecutionActivity for a better experience
        withContext(Dispatchers.Main) {
            val intent = ExecutionActivity.createIntent(
                context = context,
                fileObject = fileObject,
                languageName = getLanguageName(),
                runnerClass = this@JavaScriptRunner.javaClass.name
            )
            context.startActivity(intent)
        }

        isCurrentlyRunning = false
    }

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            outputBuffer.clear()

            try {
                v8Runtime = V8.createV8Runtime()
                v8Runtime?.let { v8 ->
                    // Create console object for logging
                    val console = V8Object(v8)
                    
                    // Register console.log function
                    console.registerJavaMethod({ _, parameters ->
                        val message = (0 until parameters.length())
                            .map { parameters.get(it)?.toString() ?: "null" }
                            .joinToString(" ")
                        outputBuffer.append(message).append("\n")
                    }, "log")

                    // Register console.error function
                    console.registerJavaMethod({ _, parameters ->
                        val message = (0 until parameters.length())
                            .map { parameters.get(it)?.toString() ?: "null" }
                            .joinToString(" ")
                        outputBuffer.append("[ERROR] ").append(message).append("\n")
                    }, "error")

                    // Register console.warn function
                    console.registerJavaMethod({ _, parameters ->
                        val message = (0 until parameters.length())
                            .map { parameters.get(it)?.toString() ?: "null" }
                            .joinToString(" ")
                        outputBuffer.append("[WARN] ").append(message).append("\n")
                    }, "warn")

                    // Register console.info function
                    console.registerJavaMethod({ _, parameters ->
                        val message = (0 until parameters.length())
                            .map { parameters.get(it)?.toString() ?: "null" }
                            .joinToString(" ")
                        outputBuffer.append("[INFO] ").append(message).append("\n")
                    }, "info")

                    v8.add("console", console)

                    // Add print function as alias to console.log
                    v8.executeVoidScript("var print = console.log;")

                    // Execute the user code
                    val result = v8.executeScript(code)

                    console.close()

                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputBuffer.toString()

                    val finalOutput = when {
                        output.isNotEmpty() -> output.trimEnd()
                        result != null && result.toString() != "undefined" -> result.toString()
                        else -> "(Execution completed in ${executionTime}ms)"
                    }

                    ExecutionResult(
                        output = finalOutput,
                        errorOutput = "",
                        isSuccess = true,
                        executionTimeMs = executionTime
                    )
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize V8 JavaScript runtime",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: V8ScriptExecutionException) {
                val executionTime = System.currentTimeMillis() - startTime
                val errorMsg = buildString {
                    append("JavaScript Error: ${e.jsMessage ?: e.message}\n")
                    e.jsStackTrace?.let { append("Stack trace:\n$it") }
                }
                ExecutionResult(
                    output = outputBuffer.toString(),
                    errorOutput = errorMsg,
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputBuffer.toString(),
                    errorOutput = "Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                v8Runtime?.close()
                v8Runtime = null
            }
        }
    }

    override suspend fun stop() {
        super.stop()
        v8Runtime?.terminateExecution()
        v8Runtime?.close()
        v8Runtime = null
    }
}
