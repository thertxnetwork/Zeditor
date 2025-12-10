package com.rk.runner.runners.code

import android.os.Environment
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Represents a language configuration for code execution.
 *
 * @property name Display name of the language
 * @property extension File extension (without dot)
 * @property interpreters List of interpreter paths to try (in order of preference)
 * @property buildCommand Optional command to compile/build before running
 * @property runArgs Additional arguments to pass to the interpreter
 */
data class LanguageConfig(
    val name: String,
    val extension: String,
    val interpreters: List<String>,
    val buildCommand: ((String) -> List<String>)? = null,
    val runArgs: List<String> = emptyList(),
    val compiledExtension: String? = null
)

/**
 * Result of code execution.
 *
 * @property output Combined stdout and stderr output
 * @property exitCode Process exit code
 * @property executionTimeMs Execution time in milliseconds
 * @property error Any error that occurred during execution
 */
data class ExecutionResult(
    val output: String,
    val exitCode: Int,
    val executionTimeMs: Long,
    val error: String? = null
)

/**
 * Executes code files using system interpreters.
 */
object CodeExecutor {
    
    /**
     * Supported language configurations.
     * Each configuration includes possible interpreter paths for different Android environments.
     * Note: The extension matching is case-insensitive, so we only need one entry per extension pattern.
     */
    private val languageConfigs = listOf(
        LanguageConfig(
            name = "Python",
            extension = "py",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/python",
                "/data/data/com.termux/files/usr/bin/python3",
                "/system/bin/python",
                "/system/bin/python3",
                "python",
                "python3"
            )
        ),
        LanguageConfig(
            name = "Node.js",
            extension = "js",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/node",
                "/system/bin/node",
                "node"
            )
        ),
        LanguageConfig(
            name = "PHP",
            extension = "php",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/php",
                "/system/bin/php",
                "php"
            )
        ),
        LanguageConfig(
            name = "Ruby",
            extension = "rb",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/ruby",
                "/system/bin/ruby",
                "ruby"
            )
        ),
        LanguageConfig(
            name = "Perl",
            extension = "pl",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/perl",
                "/system/bin/perl",
                "perl"
            )
        ),
        LanguageConfig(
            name = "Lua",
            extension = "lua",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/lua",
                "/data/data/com.termux/files/usr/bin/lua5.4",
                "/system/bin/lua",
                "lua"
            )
        ),
        // Shell script with .sh extension - uses sh primarily
        LanguageConfig(
            name = "Shell",
            extension = "sh",
            interpreters = listOf(
                "/system/bin/sh",
                "/data/data/com.termux/files/usr/bin/bash",
                "/bin/sh",
                "sh"
            )
        ),
        // Bash script with .bash extension - prefers bash
        LanguageConfig(
            name = "Bash",
            extension = "bash",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/bash",
                "/system/bin/sh",
                "/bin/bash",
                "bash"
            )
        ),
        LanguageConfig(
            name = "Zsh",
            extension = "zsh",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/zsh",
                "/system/bin/sh",
                "zsh"
            )
        ),
        LanguageConfig(
            name = "Fish",
            extension = "fish",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/fish",
                "fish"
            )
        ),
        // R files - extension matching is case-insensitive so this handles both .r and .R
        LanguageConfig(
            name = "R",
            extension = "r",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/Rscript",
                "/data/data/com.termux/files/usr/bin/R",
                "Rscript",
                "R"
            ),
            runArgs = listOf("--vanilla")
        ),
        LanguageConfig(
            name = "TypeScript",
            extension = "ts",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/ts-node",
                "/data/data/com.termux/files/usr/bin/npx",
                "ts-node",
                "npx"
            ),
            runArgs = listOf() // ts-node runs directly, npx needs ts-node arg added
        ),
        LanguageConfig(
            name = "Tcl",
            extension = "tcl",
            interpreters = listOf(
                "/data/data/com.termux/files/usr/bin/tclsh",
                "/system/bin/tclsh",
                "tclsh"
            )
        )
    )
    
    /**
     * Finds the appropriate language configuration for a file.
     */
    fun getLanguageConfig(fileObject: FileObject): LanguageConfig? {
        val fileName = fileObject.getName()
        val extension = fileName.substringAfterLast('.', "")
        return languageConfigs.find { it.extension.equals(extension, ignoreCase = true) }
    }
    
    /**
     * Checks if a file can be executed.
     */
    fun canExecute(fileObject: FileObject): Boolean {
        return getLanguageConfig(fileObject) != null
    }
    
    /**
     * Finds an available interpreter from the list of possible interpreters.
     */
    private fun findAvailableInterpreter(interpreters: List<String>): String? {
        for (interpreter in interpreters) {
            val file = File(interpreter)
            if (file.exists() && file.canExecute()) {
                return interpreter
            }
            // Also try to find in PATH
            try {
                val process = ProcessBuilder("which", interpreter)
                    .redirectErrorStream(true)
                    .start()
                val result = process.inputStream.bufferedReader().readText().trim()
                process.waitFor()
                if (process.exitValue() == 0 && result.isNotEmpty()) {
                    return result
                }
            } catch (_: Exception) {
                // Continue to next interpreter
            }
        }
        return null
    }
    
    /**
     * Executes a code file.
     *
     * @param fileObject The file to execute
     * @param workingDir Working directory for execution (defaults to file's parent)
     * @param timeout Timeout in milliseconds (0 for no timeout)
     * @param onOutput Callback for real-time output
     * @return ExecutionResult containing output and exit code
     */
    suspend fun execute(
        fileObject: FileObject,
        workingDir: File? = null,
        timeout: Long = 30000,
        onOutput: ((String) -> Unit)? = null
    ): ExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        val config = getLanguageConfig(fileObject)
            ?: return@withContext ExecutionResult(
                output = "",
                exitCode = -1,
                executionTimeMs = 0,
                error = "Unsupported file type: ${fileObject.getName()}"
            )
        
        val interpreter = findAvailableInterpreter(config.interpreters)
            ?: return@withContext ExecutionResult(
                output = "",
                exitCode = -1,
                executionTimeMs = 0,
                error = "No ${config.name} interpreter found. Please install ${config.name} (e.g., via Termux) to run this file."
            )
        
        val filePath = fileObject.getAbsolutePath()
        val actualWorkingDir = workingDir ?: fileObject.getParentFile()?.let {
            if (it is FileWrapper) it.file else File(it.getAbsolutePath())
        } ?: File(Environment.getExternalStorageDirectory().absolutePath)
        
        try {
            // Build the command
            val command = mutableListOf<String>()
            command.add(interpreter)
            
            // Handle special case for npx with TypeScript
            if (interpreter.endsWith("npx") && config.extension == "ts") {
                command.add("ts-node")
            }
            
            command.addAll(config.runArgs)
            command.add(filePath)
            
            val processBuilder = ProcessBuilder(command)
                .directory(actualWorkingDir)
                .redirectErrorStream(true)
            
            // Set up environment
            val env = processBuilder.environment()
            env["HOME"] = actualWorkingDir.absolutePath
            
            // Add Termux paths if available
            val termuxBin = "/data/data/com.termux/files/usr/bin"
            val termuxLib = "/data/data/com.termux/files/usr/lib"
            if (File(termuxBin).exists()) {
                env["PATH"] = "$termuxBin:${env["PATH"] ?: "/system/bin"}"
                env["LD_LIBRARY_PATH"] = "$termuxLib:${env["LD_LIBRARY_PATH"] ?: ""}"
            }
            
            val process = processBuilder.start()
            
            val output = StringBuilder()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            
            // Read output in real-time
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
                onOutput?.invoke(line!!)
            }
            
            // Wait for process with timeout
            val completed = if (timeout > 0) {
                process.waitFor(timeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            } else {
                process.waitFor()
                true
            }
            
            if (!completed) {
                process.destroyForcibly()
                return@withContext ExecutionResult(
                    output = output.toString(),
                    exitCode = -1,
                    executionTimeMs = System.currentTimeMillis() - startTime,
                    error = "Execution timed out after ${timeout}ms"
                )
            }
            
            val exitCode = process.exitValue()
            val executionTime = System.currentTimeMillis() - startTime
            
            return@withContext ExecutionResult(
                output = output.toString(),
                exitCode = exitCode,
                executionTimeMs = executionTime,
                error = null
            )
            
        } catch (e: Exception) {
            return@withContext ExecutionResult(
                output = "",
                exitCode = -1,
                executionTimeMs = System.currentTimeMillis() - startTime,
                error = "Execution failed: ${e.message}"
            )
        }
    }
    
    /**
     * Gets a list of supported file extensions.
     */
    fun getSupportedExtensions(): List<String> {
        return languageConfigs.map { it.extension }.distinct()
    }
    
    /**
     * Gets the language name for a given extension.
     */
    fun getLanguageName(extension: String): String? {
        return languageConfigs.find { it.extension.equals(extension, ignoreCase = true) }?.name
    }
}
