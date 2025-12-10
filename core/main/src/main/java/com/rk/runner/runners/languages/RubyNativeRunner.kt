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
 * Ruby runner using native interpreter (via Termux or system).
 * Provides full Ruby 3.x support via native execution.
 */
class RubyNativeRunner : RunnerImpl() {

    override fun getName(): String = "Ruby"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)
        val result = executeRubyCode(context, fileObject)

        withContext(Dispatchers.Main) {
            val intent = Intent(context, ExecutionActivity::class.java).apply {
                putExtra("execution_result", result)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private suspend fun executeRubyCode(context: Context, fileObject: FileWrapper): ExecutionResultData {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            try {
                val rubyCheckProcess = Runtime.getRuntime().exec(arrayOf("sh", "-c", "which ruby"))
                val rubyCheckExitCode = rubyCheckProcess.waitFor()

                if (rubyCheckExitCode != 0) {
                    return@withContext ExecutionResultData(
                        languageName = "Ruby",
                        fileName = fileObject.getName(),
                        output = "",
                        errorOutput = """
                            Ruby interpreter not found on this device.
                            
                            To run Ruby code on Android:
                            
                            1. Install Termux from F-Droid
                            2. Run: pkg install ruby
                            3. Then execute: ruby ${fileObject.getName()}
                            
                            File: ${fileObject.getName()}
                        """.trimIndent(),
                        isSuccess = false,
                        executionTimeMs = System.currentTimeMillis() - startTime
                    )
                }

                val sourceFile = fileObject.toFile()
                val outputBuilder = StringBuilder()
                val errorBuilder = StringBuilder()

                val process = Runtime.getRuntime().exec(
                    arrayOf("ruby", sourceFile.absolutePath),
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
                    languageName = "Ruby",
                    fileName = fileObject.getName(),
                    output = if (outputBuilder.isNotEmpty()) outputBuilder.toString() else "(No output)",
                    errorOutput = errorBuilder.toString(),
                    isSuccess = exitCode == 0,
                    executionTimeMs = executionTime
                )

            } catch (e: Exception) {
                ExecutionResultData(
                    languageName = "Ruby",
                    fileName = fileObject.getName(),
                    output = "",
                    errorOutput = "Error executing Ruby: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = System.currentTimeMillis() - startTime
                )
            }
        }
    }

    override suspend fun isRunning(): Boolean = false
    override suspend fun stop() {}
}
