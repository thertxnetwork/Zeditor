package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.caucho.quercus.QuercusContext
import com.caucho.quercus.env.Env
import com.caucho.quercus.page.QuercusPage
import com.caucho.quercus.parser.QuercusParser
import com.caucho.quercus.program.QuercusProgram
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
import java.io.StringReader
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * PHP language runner using Quercus (PHP on JVM).
 *
 * Features:
 * - Full PHP support (PHP 5.x compatible)
 * - No JNI/NDK required (pure JVM)
 * - Access to Java libraries
 * - Most PHP standard functions
 * - File operations, database access
 *
 * Quercus is a 100% Java implementation of the PHP language that compiles
 * PHP to Java bytecode, enabling PHP execution on the JVM.
 *
 * Recommended for: PHP scripts, web development, server-side scripting
 */
class PhpQuercusRunner : LanguageRunner() {

    private var quercus: QuercusContext? = null

    override fun getLanguageName(): String = "PHP"

    override fun getSupportedExtensions(): List<String> = listOf("php")

    override fun getName(): String = "PHP (Quercus)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.run.getDrawable(context)
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
                // Create Quercus context
                quercus = QuercusContext()
                quercus?.init()
                quercus?.start()

                quercus?.let { qc ->
                    // Redirect output streams
                    System.setOut(printStream)
                    System.setErr(errorPrintStream)

                    // Create PHP environment
                    val env = qc.createEnv(null, null, printStream, null, null)
                    env.start()

                    // Parse and execute PHP code
                    val parser = QuercusParser(qc)
                    
                    // Ensure code has PHP tags
                    val trimmedCode = code.trim()
                    val phpCode = if (!trimmedCode.startsWith("<?php") && !trimmedCode.startsWith("<?")) {
                        "<?php\n$code\n?>"
                    } else {
                        code
                    }
                    
                    val program = parser.parse(StringReader(phpCode))
                    val value = program.execute(env)

                    env.close()

                    System.setOut(originalOut)
                    System.setErr(originalErr)

                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputStream.toString("UTF-8")
                    val errorOutput = errorStream.toString("UTF-8")

                    val finalOutput = when {
                        output.isNotEmpty() -> output
                        value != null && value.toString().isNotEmpty() -> value.toString()
                        else -> "(Execution completed in ${executionTime}ms)"
                    }

                    ExecutionResult(
                        output = finalOutput,
                        errorOutput = errorOutput,
                        isSuccess = errorOutput.isEmpty(),
                        executionTimeMs = executionTime
                    )
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize Quercus PHP engine",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "PHP Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                printStream.close()
                errorPrintStream.close()
                outputStream.close()
                errorStream.close()
                quercus?.destroy()
            }
        }
    }

    override suspend fun stop() {
        super.stop()
        quercus?.destroy()
        quercus = null
    }
}
