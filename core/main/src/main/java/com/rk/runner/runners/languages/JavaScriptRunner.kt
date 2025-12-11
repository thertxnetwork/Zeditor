package com.rk.runner.runners.languages

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.eclipsesource.v8.V8
import com.eclipsesource.v8.V8Array
import com.eclipsesource.v8.V8Object
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

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputBuffer = StringBuilder()
            
            try {
                v8Runtime = V8.createV8Runtime()
                
                v8Runtime?.let { runtime ->
                    // Create console object
                    val console = V8Object(runtime)
                    
                    // Add console.log
                    console.registerJavaMethod({ receiver, parameters ->
                        val messages = mutableListOf<String>()
                        for (i in 0 until parameters.length()) {
                            messages.add(parameters.get(i).toString())
                        }
                        outputBuffer.append(messages.joinToString(" ")).append("\n")
                    }, "log")
                    
                    // Add console.error
                    console.registerJavaMethod({ receiver, parameters ->
                        val messages = mutableListOf<String>()
                        for (i in 0 until parameters.length()) {
                            messages.add(parameters.get(i).toString())
                        }
                        outputBuffer.append("[ERROR] ").append(messages.joinToString(" ")).append("\n")
                    }, "error")
                    
                    // Add console.warn
                    console.registerJavaMethod({ receiver, parameters ->
                        val messages = mutableListOf<String>()
                        for (i in 0 until parameters.length()) {
                            messages.add(parameters.get(i).toString())
                        }
                        outputBuffer.append("[WARN] ").append(messages.joinToString(" ")).append("\n")
                    }, "warn")
                    
                    // Add console.info
                    console.registerJavaMethod({ receiver, parameters ->
                        val messages = mutableListOf<String>()
                        for (i in 0 until parameters.length()) {
                            messages.add(parameters.get(i).toString())
                        }
                        outputBuffer.append("[INFO] ").append(messages.joinToString(" ")).append("\n")
                    }, "info")
                    
                    runtime.add("console", console)
                    console.release()
                    
                    // Execute the code
                    val result = runtime.executeScript(code)
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputBuffer.toString()
                    
                    val finalOutput = if (output.isNotEmpty()) {
                        output.trimEnd()
                    } else if (result != null && result.toString() != "undefined") {
                        result.toString()
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
                    output = output,
                    errorOutput = "JavaScript Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                try {
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
