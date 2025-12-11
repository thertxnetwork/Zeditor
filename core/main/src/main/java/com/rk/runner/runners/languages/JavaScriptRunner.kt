package com.rk.runner.runners.languages

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.eclipsesource.v8.JavaCallback
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
import com.eclipsesource.v8.V8Value
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ExecutionActivity
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JavaScript language runner using J2V8 (V8 JavaScript engine for Java/Android).
 *
 * Features:
 * - Full ES6+ JavaScript support via Google V8 engine
 * - Native performance (V8 compiled code)
 * - Modern JavaScript features
 * - Better performance than Rhino
 *
 * Engine: Google V8 via J2V8 binding
 */
class JavaScriptRunner : LanguageRunner() {

    private var v8Runtime: V8? = null

    override fun getLanguageName(): String = "JavaScript"

    override fun getSupportedExtensions(): List<String> = listOf("js")

    override fun getName(): String = "JavaScript (V8)"

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

        val code = withContext(Dispatchers.IO) { fileObject.readText() }

        val result = executeCode(code)

        withContext(Dispatchers.Main) {
            // Launch ExecutionActivity instead of showing dialog
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra(ExecutionActivity.EXTRA_TITLE, "JavaScript Execution")
                putExtra(ExecutionActivity.EXTRA_OUTPUT, result.output)
                putExtra(ExecutionActivity.EXTRA_ERROR, result.errorOutput)
                putExtra(ExecutionActivity.EXTRA_SUCCESS, result.isSuccess)
                putExtra(ExecutionActivity.EXTRA_TIME, result.executionTimeMs)
                putExtra(ExecutionActivity.EXTRA_ENGINE, "Google V8 JavaScript Engine (via J2V8)")
            }
            context.startActivity(intent)
        }

        isCurrentlyRunning = false
    }

    private fun setupConsole(runtime: V8, outputBuffer: StringBuilder): V8Object {
        val console = V8Object(runtime)
        
        // Helper to create console methods using JavaCallback interface
        fun addConsoleMethod(methodName: String, prefix: String = "") {
            console.registerJavaMethod(object : JavaCallback {
                override fun invoke(receiver: V8Object, parameters: V8Array): Any? {
                    val messages = mutableListOf<String>()
                    for (i in 0 until parameters.length()) {
                        // Get the value type and convert appropriately
                        val stringValue = try {
                            when (parameters.getType(i)) {
                                V8Value.STRING -> parameters.getString(i)
                                V8Value.INTEGER -> parameters.getInteger(i).toString()
                                V8Value.DOUBLE -> parameters.getDouble(i).toString()
                                V8Value.BOOLEAN -> parameters.getBoolean(i).toString()
                                V8Value.NULL -> "null"
                                V8Value.UNDEFINED -> "undefined"
                                else -> parameters.get(i).toString()
                            }
                        } catch (e: Exception) {
                            parameters.get(i).toString()
                        }
                        messages.add(stringValue)
                    }
                    if (prefix.isNotEmpty()) {
                        outputBuffer.append(prefix).append(" ")
                    }
                    outputBuffer.append(messages.joinToString(" ")).append("\n")
                    return null
                }
            }, methodName)
        }
        
        addConsoleMethod("log")
        addConsoleMethod("error", "[ERROR]")
        addConsoleMethod("warn", "[WARN]")
        addConsoleMethod("info", "[INFO]")
        
        return console
    }
    
    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputBuffer = StringBuilder()
            var console: V8Object? = null
            var scriptResult: Any? = null
            
            try {
                v8Runtime = V8.createV8Runtime()
                
                v8Runtime?.let { runtime ->
                    // Setup console object with methods
                    console = setupConsole(runtime, outputBuffer)
                    runtime.add("console", console)
                    
                    // Execute the code
                    scriptResult = runtime.executeScript(code)
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputBuffer.toString()
                    
                    val finalOutput = if (output.isNotEmpty()) {
                        output.trimEnd()
                    } else if (scriptResult != null && scriptResult.toString() != "undefined") {
                        scriptResult.toString()
                    } else {
                        "(Execution completed in ${executionTime}ms)"
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
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                val output = outputBuffer.toString()
                ExecutionResult(
                    output = if (output.isNotEmpty()) output.trimEnd() else "",
                    errorOutput = "JavaScript Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                try {
                    // Release V8 objects in correct order
                    console?.release()
                    v8Runtime?.release()
                } catch (_: Exception) {}
                v8Runtime = null
            }
        }
    }

    override suspend fun stop() {
        super.stop()
        v8Runtime?.let {
            try {
                it.release()
            } catch (_: Exception) {}
        }
        v8Runtime = null
    }
}
