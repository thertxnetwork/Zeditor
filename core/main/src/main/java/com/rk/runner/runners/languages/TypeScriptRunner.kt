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
 * TypeScript runner - attempts to run TypeScript as JavaScript using Rhino.
 *
 * LIMITATIONS:
 * - TypeScript-specific syntax (type annotations, interfaces, etc.) will cause errors
 * - Only works with TypeScript code that is also valid JavaScript
 * - For full TypeScript support, use Termux with Node.js/ts-node
 *
 * This runner is best for:
 * - Simple scripts without type annotations
 * - JavaScript files with .ts extension
 * - Quick testing of logic
 */
class TypeScriptRunner : LanguageRunner() {

    private var rhinoContext: RhinoContext? = null

    override fun getLanguageName(): String = "TypeScript"

    override fun getSupportedExtensions(): List<String> = listOf("ts")

    override fun getName(): String = "TypeScript (JS mode)"

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

        val result = executeCode(code)

        withContext(Dispatchers.Main) {
            if (result.isSuccess) {
                dialog(
                    title = "TypeScript Output",
                    msg = if (result.output.isNotEmpty()) result.output else "(No output)",
                    onOk = {}
                )
            } else {
                val errorMsg = buildString {
                    append(result.errorOutput.ifEmpty { result.output })
                    if (result.errorOutput.contains("syntax error") || result.errorOutput.contains("Unexpected")) {
                        append("\n\nNote: TypeScript type annotations are not supported.")
                        append("\nFor full TypeScript, use Termux with ts-node.")
                    }
                }
                dialog(title = "TypeScript Error", msg = errorMsg, onOk = {})
            }
        }

        isCurrentlyRunning = false
    }

    override suspend fun executeCode(code: String): ExecutionResult {
        return withContext(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()
            val outputStream = ByteArrayOutputStream()
            val printStream = PrintStream(outputStream)
            val originalOut = System.out

            try {
                rhinoContext = RhinoContext.enter()
                rhinoContext?.let { ctx ->
                    ctx.optimizationLevel = -1

                    val scope = ctx.initStandardObjects()

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

                    ctx.evaluateString(scope, consoleScript, "console", 1, null)

                    System.setOut(printStream)

                    val result = ctx.evaluateString(scope, code, "script.ts", 1, null)

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
                } ?: ExecutionResult(
                    output = "",
                    errorOutput = "Failed to initialize JavaScript context",
                    isSuccess = false,
                    executionTimeMs = 0
                )
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
                try {
                    RhinoContext.exit()
                } catch (_: Exception) {}
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
