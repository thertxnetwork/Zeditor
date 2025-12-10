package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.script.experimental.api.*
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.script.templates.standard.ScriptTemplateWithArgs

/**
 * Kotlin Script language runner using Kotlin Scripting Host.
 *
 * Features:
 * - Full Kotlin scripting support (.kts files)
 * - Native Android support (official language)
 * - No JNI/NDK required (pure JVM)
 * - Access to all Kotlin features
 * - Type safety and null safety
 * - Coroutines support
 *
 * Kotlin is Android's official programming language with full scripting support.
 * This runner executes .kts (Kotlin Script) files directly.
 *
 * Recommended for: Kotlin scripting, Android automation, type-safe scripts
 */
class KotlinScriptActualRunner : LanguageRunner() {

    override fun getLanguageName(): String = "Kotlin"

    override fun getSupportedExtensions(): List<String> = listOf("kts")

    override fun getName(): String = "Kotlin Script"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_kotlin.getDrawable(context)
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
            showExecutionResult(context, result, fileObject.getName())
        }

        isCurrentlyRunning = false
    }

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()
            val errorStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            val errorPrintStream = PrintStream(errorStream)
            val originalOut = System.out
            val originalErr = System.err

            try {
                // Redirect output streams
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Create scripting host
                val scriptingHost = BasicJvmScriptingHost()

                // Configure compilation
                val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<ScriptTemplateWithArgs> {
                    jvm {
                        dependenciesFromCurrentContext(wholeClasspath = true)
                    }
                }

                // Evaluate the script
                val evalResult = scriptingHost.eval(
                    code.toScriptSource(),
                    compilationConfiguration,
                    null
                )

                System.setOut(originalOut)
                System.setErr(originalErr)

                val executionTime = System.currentTimeMillis() - startTime
                val output = outputStream.toString("UTF-8")
                val errorOutput = errorStream.toString("UTF-8")

                when (evalResult) {
                    is ResultWithDiagnostics.Success -> {
                        val resultValue = evalResult.value.returnValue
                        val finalOutput = when {
                            output.isNotEmpty() -> output
                            resultValue is ResultValue.Value -> resultValue.value?.toString() ?: "(Execution completed in ${executionTime}ms)"
                            else -> "(Execution completed in ${executionTime}ms)"
                        }

                        ExecutionResult(
                            output = finalOutput,
                            errorOutput = errorOutput,
                            isSuccess = errorOutput.isEmpty(),
                            executionTimeMs = executionTime
                        )
                    }
                    is ResultWithDiagnostics.Failure -> {
                        val diagnostics = evalResult.reports.joinToString("\n") { 
                            "${it.severity}: ${it.message}"
                        }
                        ExecutionResult(
                            output = output,
                            errorOutput = "Kotlin Script Error:\n$diagnostics\n$errorOutput",
                            isSuccess = false,
                            executionTimeMs = executionTime
                        )
                    }
                }
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Kotlin Script Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                printStream.close()
                errorPrintStream.close()
                outputStream.close()
                errorStream.close()
            }
        }
    }

    override suspend fun stop() {
        super.stop()
    }
}
