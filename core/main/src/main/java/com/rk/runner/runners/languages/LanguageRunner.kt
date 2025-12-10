package com.rk.runner.runners.languages

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.runner.ExecutionResultData
import com.rk.runner.ExecutionActivity
import com.rk.runner.RunnerImpl

/**
 * Base class for language interpreters that can execute code directly on Android
 * without requiring an external terminal or shell.
 *
 * These runners use JVM-based or NDK-based interpreters:
 * - LuaJ for Lua (JVM-based, full Lua 5.2 support)
 * - Rhino for JavaScript (JVM-based, ES6 support)
 * - QuickJS for JavaScript (NDK-based, ES2020 support)
 */
abstract class LanguageRunner : RunnerImpl() {

    protected var isCurrentlyRunning = false
    protected var executionThread: Thread? = null

    abstract fun getLanguageName(): String

    abstract fun getSupportedExtensions(): List<String>

    /**
     * Execute the code and return the output
     */
    abstract suspend fun executeCode(code: String): ExecutionResult

    override suspend fun isRunning(): Boolean = isCurrentlyRunning

    override suspend fun stop() {
        isCurrentlyRunning = false
        executionThread?.interrupt()
        executionThread = null
    }

    /**
     * Check if this runner can handle the given file
     */
    fun canHandle(fileObject: FileObject): Boolean {
        val extension = fileObject.getName().substringAfterLast('.', "")
        return getSupportedExtensions().contains(extension)
    }

    /**
     * Show execution result in ExecutionActivity
     */
    protected fun showExecutionResult(context: Context, result: ExecutionResult, fileName: String) {
        val intent = Intent(context, ExecutionActivity::class.java).apply {
            putExtra("execution_result", ExecutionResultData(
                languageName = getLanguageName(),
                fileName = fileName,
                output = result.output,
                errorOutput = result.errorOutput,
                isSuccess = result.isSuccess,
                executionTimeMs = result.executionTimeMs
            ))
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}

/**
 * Result of code execution
 */
data class ExecutionResult(
    val output: String,
    val errorOutput: String = "",
    val isSuccess: Boolean,
    val executionTimeMs: Long = 0
)
