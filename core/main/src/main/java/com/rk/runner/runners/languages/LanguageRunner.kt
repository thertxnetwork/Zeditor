package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
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
