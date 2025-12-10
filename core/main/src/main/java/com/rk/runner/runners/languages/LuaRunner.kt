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

        val code = withContext(Dispatchers.IO) { fileObject.readText() }

        val result = executeCode(code)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                dialog(
                    title = "Lua Output",
                    msg = if (result.output.isNotEmpty()) result.output else "(No output)",
                    onOk = {}
                )
            } else {
                dialog(title = "Lua Error", msg = result.errorOutput.ifEmpty { result.output }, onOk = {})
            }
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
                globals!!.STDOUT = printStream
                globals!!.STDERR = errorPrintStream

                val chunk = globals!!.load(code)
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
