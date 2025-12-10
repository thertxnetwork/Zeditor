package com.rk.runner.runners.languages

import android.content.Context
import android.content.Intent
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

/**
 * Python runner using native interpreter (via Termux or system).
 * Provides full Python 3.x support via native execution.
 */
class PythonNativeRunner : RunnerImpl() {

    override fun getName(): String = "Python"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_python.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)
        val result = executePythonCode(context, fileObject)

        withContext(Dispatchers.Main) {
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra("execution_result", result)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private suspend fun executePythonCode(context: Context, fileObject: FileWrapper): ExecutionResultData {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                // Check if Python is available
                val pythonCheckProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which python3 || which python"))
                val pythonCheckExitCode = pythonCheckProcess.waitFor()

                if (pythonCheckExitCode != 0) {
                    return@withContext ExecutionResultData(
                        languageName = "Python",
                        fileName = fileObject.getName(),
                        output = "",
                        errorOutput = """
                            Python interpreter not found on this device.
                            
                            To run Python code on Android:
                            
                            1. Install Termux from F-Droid
                            2. Run: pkg install python
                            3. Then execute: python ${fileObject.getName()}
                            
                            Alternative: Use Pydroid 3 app from Play Store
                            
                            File: ${fileObject.getName()}
                        """.trimIndent(),
                        isSuccess = false,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                // Python is available, execute the code
                val sourceFile = fileObject.toFile()
                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                val process = Runtime.getRuntime().exec(
                    arrayOf("sh", "-c", "python3 '${sourceFile.absolutePath}' || python '${sourceFile.absolutePath}'"),
                    null,
                    sourceFile.parentFile
                )

                val outputReader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))

                outputReader.forEachLine { outputBuilder.appendLine(it) }
                errorReader.forEachLine { errorBuilder.appendLine(it) }

                val exitCode = process.waitFor()
                val executionTime = System.currentTimeMillis() - startTime

                ExecutionResultData(
                    languageName = "Python",
                    fileName = fileObject.getName(),
                    output = if (outputBuilder.isNotEmpty()) outputBuilder.toString() else "(No output)",
                    errorOutput = errorBuilder.toString(),
                    isSuccess = exitCode == 0,
                    executionTimeMs = executionTime
                )

            } catch (e: Exception) {
                ExecutionResultData(
                    languageName = "Python",
                    fileName = fileObject.getName(),
                    output = "",
                    errorOutput = "Error executing Python: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    override suspend fun isRunning(): Boolean = false
    override suspend fun stop() {}
}
