package com.rk.runner.runners.languages

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.eclipsesource.v8.JavaCallback
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
 * TypeScript runner - attempts to run TypeScript as JavaScript using V8.
 *
 * LIMITATIONS:
 * - TypeScript-specific syntax (type annotations, interfaces, etc.) will cause errors
 * - Only works with TypeScript code that is also valid JavaScript
 * - For full TypeScript support, use Termux with Node.js/ts-node
 *
 * This runner is best for:
 * - Simple scripts without type annotations
 * - JavaScript files with .ts extension
 * - Quick testing of logic
 */
class TypeScriptRunner : LanguageRunner() {

    private var v8Runtime: V8? = null

    override fun getLanguageName(): String = "TypeScript"

    override fun getSupportedExtensions(): List<String> = listOf("ts")

    override fun getName(): String = "TypeScript (V8 JS mode)"

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
            val errorMsg = if (!result.isSuccess && 
                (result.errorOutput.contains("syntax error") || result.errorOutput.contains("Unexpected"))) {
                buildString {
                    append(result.errorOutput.ifEmpty { result.output })
                    append("\n\nNote: TypeScript type annotations are not supported.")
                    append("\nFor full TypeScript, use Termux with ts-node.")
                }
            } else {
                result.errorOutput
            }
            
            // Launch ExecutionActivity instead of showing dialog
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra(ExecutionActivity.EXTRA_TITLE, "TypeScript Execution")
                putExtra(ExecutionActivity.EXTRA_OUTPUT, result.output)
                putExtra(ExecutionActivity.EXTRA_ERROR, errorMsg)
                putExtra(ExecutionActivity.EXTRA_SUCCESS, result.isSuccess)
                putExtra(ExecutionActivity.EXTRA_TIME, result.executionTimeMs)
                putExtra(ExecutionActivity.EXTRA_ENGINE, "Google V8 JavaScript Engine (via J2V8) - JS Mode")
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
                        messages.add(parameters.get(i).toString())
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
            
            try {
                v8Runtime = V8.createV8Runtime()
                
                v8Runtime?.let { runtime ->
                    // Setup console object with methods
                    console = setupConsole(runtime, outputBuffer)
                    runtime.add("console", console)
                    
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
                    errorOutput = "TypeScript Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                try {
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
