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
import java.io.File
import java.io.InputStreamReader
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Intent

/**
 * Go language runner with actual execution support.
 *
 * This runner attempts to execute Go code using installed Go compiler (via Termux or system).
 * It first checks if 'go' command is available, then compiles and runs the code.
 *
 * Features:
 * - Attempts to compile and run Go code if Go is installed
 * - Shows detailed error messages
 * - Execution time tracking
 * - Falls back to installation instructions if Go is not available
 *
 * For full Go support:
 * - gomobile (Official) - Full Go via NDK bindings
 * - Termux: pkg install golang
 *
 * Recommended for: Systems programming, CLI tools, backend services
 */
class GoActualRunner : RunnerImpl() {

    override fun getName(): String = "Go"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)

        // Try to execute Go code
        val result = executeGoCode(context, fileObject)

        withContext(Dispatchers.Main) {
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra("execution_result", result)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private suspend fun executeGoCode(context: Context, fileObject: FileWrapper): ExecutionResultData {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                // Check if Go is available
                val goCheckProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which go"))
                val goCheckExitCode = goCheckProcess.waitFor()

                if (goCheckExitCode != 0) {
                    // Go is not installed
                    return@withContext ExecutionResultData(
                        languageName = "Go",
                        fileName = fileObject.getName(),
                        output = "",
                        errorOutput = """
                            Go compiler not found on this device.
                            
                            To run Go code on Android:
                            
                            1. Install Termux from F-Droid
                            2. Run: pkg install golang
                            3. Then you can execute: go run ${fileObject.getName()}
                            
                            Alternative: Use gomobile for native Go on Android
                            - gomobile (Official): Full Go via NDK bindings
                            - Cross-compile: Build on PC with GOOS=android
                            
                            File: ${fileObject.getName()}
                        """.trimIndent(),
                        isSuccess = false,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Go is available, try to run the code
                val sourceFile = fileObject.toFile()
                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                // Execute go run command
                val process = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", "go run ${sourceFile.absolutePath}"),
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
                    languageName = "Go",
                    fileName = fileObject.getName(),
                    output = if (output.isNotEmpty()) output else "(No output)",
                    errorOutput = error,
                    isSuccess = isSuccess,
                    executionTimeMs = executionTime
                )

            } catch (e: Exception) {
                ExecutionResultData(
                    languageName = "Go",
                    fileName = fileObject.getName(),
                    output = "",
                    errorOutput = "Error executing Go code: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    override suspend fun isRunning(): Boolean = false

    override suspend fun stop() {}
}
