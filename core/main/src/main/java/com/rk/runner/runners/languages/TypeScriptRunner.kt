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
import org.mozilla.javascript.Context as RhinoContext
import org.mozilla.javascript.RhinoException

/**
 * TypeScript runner - runs TypeScript as JavaScript using Rhino.
 *
 * Note: TypeScript type annotations are stripped at runtime.
 * For full TypeScript compilation, a terminal with Node.js is required.
 *
 * Features:
 * - Runs TypeScript files (types are ignored at runtime)
 * - Full ES5-ES6 JavaScript support via Rhino
 * - No JNI/NDK required (pure JVM)
 */
class TypeScriptRunner : LanguageRunner() {

    private var rhinoContext: RhinoContext? = null

    override fun getLanguageName(): String = "TypeScript"

    override fun getSupportedExtensions(): List<String> = listOf("ts")

    override fun getName(): String = "TypeScript (as JS)"

    override fun getIcon(context: Context): Drawable? {
        return drawables.ic_language_js.getDrawable(context)
    }

    override suspend fun run(context: Context, fileObject: FileObject) {
        if (fileObject !is FileWrapper) {
            dialog(title = strings.attention.getString(), msg = strings.non_native_filetype.getString(), onOk = {})
            return
        }

        currentRunner = WeakReference(this)
        isCurrentlyRunning = true

        val code = withContext(Dispatchers.IO) { fileObject.readText() }

        // Strip TypeScript type annotations (basic transpilation)
        val jsCode = stripTypeAnnotations(code)

        val result = executeCode(jsCode)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                dialog(
                    title = "TypeScript Output",
                    msg = if (result.output.isNotEmpty()) result.output else "(No output)",
                    onOk = {}
                )
            } else {
                dialog(title = "TypeScript Error", msg = result.errorOutput.ifEmpty { result.output }, onOk = {})
            }
        }

        isCurrentlyRunning = false
    }

    /**
     * Basic TypeScript to JavaScript transpilation.
     * Strips type annotations, interfaces, and type declarations.
     */
    private fun stripTypeAnnotations(tsCode: String): String {
        var code = tsCode

        // Remove type annotations from variables (let x: string = ...)
        code = code.replace(Regex(""":\s*\w+(\[\])?(\s*\|\s*\w+(\[\])?)*(\s*=)"""), "$4")

        // Remove type annotations from function parameters
        code = code.replace(Regex("""(\w+)\s*:\s*\w+(\[\])?(\s*\|\s*\w+(\[\])?)*(\s*[,)])"""), "$1$5")

        // Remove return type annotations
        code = code.replace(Regex("""\)\s*:\s*\w+(\[\])?(\s*\|\s*\w+(\[\])?)*\s*\{"""), ") {")

        // Remove interface declarations
        code = code.replace(Regex("""interface\s+\w+\s*\{[^}]*\}"""), "")

        // Remove type declarations
        code = code.replace(Regex("""type\s+\w+\s*=\s*[^;]+;"""), "")

        // Remove 'as' type assertions
        code = code.replace(Regex("""\s+as\s+\w+(\[\])?"""), "")

        // Remove angle bracket type assertions
        code = code.replace(Regex("""<\w+(\[\])?>\s*"""), "")

        // Remove generic type parameters
        code = code.replace(Regex("""<\w+(\s*,\s*\w+)*>"""), "")

        return code
    }

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            val originalOut = System.out

            try {
                rhinoContext = RhinoContext.enter()
                rhinoContext!!.optimizationLevel = -1

                val scope = rhinoContext!!.initStandardObjects()

                // Add console.log support
                val consoleScript =
                    """
                    var console = {
                        log: function() {
                            var args = Array.prototype.slice.call(arguments);
                            java.lang.System.out.println(args.join(' '));
                        },
                        error: function() {
                            var args = Array.prototype.slice.call(arguments);
                            java.lang.System.err.println(args.join(' '));
                        },
                        warn: function() {
                            var args = Array.prototype.slice.call(arguments);
                            java.lang.System.out.println('[WARN] ' + args.join(' '));
                        }
                    };
                    var print = console.log;
                """
                        .trimIndent()

                rhinoContext!!.evaluateString(scope, consoleScript, "console", 1, null)

                System.setOut(printStream)

                val result = rhinoContext!!.evaluateString(scope, code, "script.ts", 1, null)

                System.setOut(originalOut)

                val executionTime = System.currentTimeMillis() - startTime
                val output = outputStream.toString("UTF-8")

                val finalOutput =
                    if (output.isNotEmpty()) {
                        output
                    } else if (result != null && result != RhinoContext.getUndefinedValue()) {
                        RhinoContext.toString(result)
                    } else {
                        "(Execution completed in ${executionTime}ms)"
                    }

                ExecutionResult(output = finalOutput, errorOutput = "", isSuccess = true, executionTimeMs = executionTime)
            } catch (e: RhinoException) {
                System.setOut(originalOut)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "TypeScript Error: ${e.message}\nAt line ${e.lineNumber()}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Error: ${e.message}",
                    isSuccess = false,
                    executionTimeMs = executionTime
                )
            } finally {
                RhinoContext.exit()
                rhinoContext = null
                printStream.close()
                outputStream.close()
            }
        }
    }

    override suspend fun stop() {
        super.stop()
        rhinoContext?.let {
            try {
                RhinoContext.exit()
            } catch (_: Exception) {}
        }
        rhinoContext = null
    }
}
