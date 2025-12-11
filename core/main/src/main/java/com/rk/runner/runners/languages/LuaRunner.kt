package com.rk.runner.runners.languages

import android.content.Context
import android.graphics.drawable.Drawable
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.resources.drawables
import com.rk.resources.getDrawable
import com.rk.resources.getString
import com.rk.resources.strings
import com.rk.runner.ExecutionActivity
import com.rk.runner.currentRunner
import com.rk.utils.dialog
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.luaj.vm2.Globals
import org.luaj.vm2.LuaError
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Lua language runner using LuaJ (pure Java implementation).
 *
 * Features:
 * - Full Lua 5.2 support
 * - No JNI/NDK required (pure JVM)
 * - Direct Java interop
 * - Complete standard library
 *
 * Recommended by: https://github.com/luaj/luaj
 */
class LuaRunner : LanguageRunner() {

    private var globals: Globals? = null

    override fun getLanguageName(): String = "Lua"

    override fun getSupportedExtensions(): List<String> = listOf("lua")

    override fun getName(): String = "Lua (LuaJ)"

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

        // Launch the ExecutionActivity for a better experience
        withContext(Dispatchers.Main) {
            val intent = ExecutionActivity.createIntent(
                context = context,
                fileObject = fileObject,
                languageName = getLanguageName(),
                runnerClass = this@LuaRunner.javaClass.name
            )
            context.startActivity(intent)
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

            try {
                globals = JsePlatform.standardGlobals()
                globals?.let { g ->
                    g.STDOUT = printStream
                    g.STDERR = errorPrintStream

                    val chunk = g.load(code)
                    chunk.call()

                    val executionTime = System.currentTimeMillis() - startTime
                    val output = outputStream.toString("UTF-8")
                    val errorOutput = errorStream.toString("UTF-8")

                    ExecutionResult(
                        output = output.ifEmpty { "(Execution completed in ${executionTime}ms)" },
                        errorOutput = errorOutput,
                        isSuccess = true,
                        executionTimeMs = executionTime
                    )
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize Lua context",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: LuaError) {
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Lua Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Error: ${e.message}",
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
        globals = null
    }
}
