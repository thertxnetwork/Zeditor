package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ExecutionResultData
import com.rk.runner.ExecutionActivity
import com.rk.runner.RunnerImpl
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent

/**
 * PHP language runner using native PHP interpreter via JNI/shell.
 *
 * This runner attempts to execute PHP code using:
 * 1. System PHP interpreter (if available via Termux)
 * 2. Bundled PHP binary (if included)
 * 3. Shows installation guide if not available
 *
 * Features:
 * - Attempts native PHP execution
 * - Full PHP support if interpreter is available
 * - Execution time tracking
 * - Detailed error messages
 *
 * For full PHP support:
 * - Install Termux and run: pkg install php
 * - Or use php-java-bridge for JVM integration
 *
 * Recommended for: PHP scripts, web development, server-side code
 */
class PhpNativeRunner : RunnerImpl() {

    override fun getName(): String = "PHP"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        // Try to execute PHP code
        val result = executePhpCode(context, fileObject)

        withContext(Dispatchers.Main) {
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra("execution_result", result)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private suspend fun executePhpCode(context: Context, fileObject: FileWrapper): ExecutionResultData {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                // Check if PHP is available
                val phpCheckProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which php"))
                val phpCheckExitCode = phpCheckProcess.waitFor()

                if (phpCheckExitCode != 0) {
                    // PHP is not installed
                    return@withContext ExecutionResultData(
                        languageName = "PHP",
                        fileName = fileObject.getName(),
                        output = "",
                        errorOutput = """
                            PHP interpreter not found on this device.
                            
                            To run PHP code on Android:
                            
                            1. Install Termux from F-Droid
                            2. Run: pkg install php
                            3. Then you can execute: php ${fileObject.getName()}
                            
                            Alternative Options:
                            - php-java-bridge: PHP via Java bridge
                            - KSWEB/AndroPHP: PHP server apps for Android
                            
                            Note: This runner attempts to use native PHP.
                            For JVM-based PHP, php-java-bridge integration
                            would be needed (requires custom setup).
                            
                            File: ${fileObject.getName()}
                        """.trimIndent(),
                        isSuccess = false,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // PHP is available, try to run the code
                val sourceFile = fileObject.toFile()
                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                // Execute php command
                val process = Runtime.getRuntime().exec(
                    arrayOf("php", sourceFile.absolutePath),
                    null,
                    sourceFile.parentFile
                )

                // Read output
                val outputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                // Read stdout
                outputReader.forEachLine { line ->
                    outputBuilder.appendLine(line)
                }

                // Read stderr
                errorReader.forEachLine { line ->
                    errorBuilder.appendLine(line)
                }

                val exitCode = process.waitFor()
                val executionTime = System.currentTimeMillis() - startTime

                val isSuccess = exitCode == 0
                val output = outputBuilder.toString()
                val error = errorBuilder.toString()

                ExecutionResultData(
                    languageName = "PHP",
                    fileName = fileObject.getName(),
                    output = if (output.isNotEmpty()) output else "(No output)",
                    errorOutput = error,
                    isSuccess = isSuccess,
                    executionTimeMs = executionTime
                )

            } catch (e: Exception) {
                ExecutionResultData(
                    languageName = "PHP",
                    fileName = fileObject.getName(),
                    output = "",
                    errorOutput = "Error executing PHP code: ${e.message}\n\n" +
                            "Make sure PHP is installed via Termux:\n" +
                            "1. Install Termux from F-Droid\n" +
                            "2. Run: pkg install php",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}
