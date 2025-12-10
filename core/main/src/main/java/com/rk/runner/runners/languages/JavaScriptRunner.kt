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
import org.mozilla.javascript.ScriptableObject

/**
 * JavaScript language runner using Mozilla Rhino (pure Java implementation).
 *
 * Features:
 * - Full JavaScript support (ES5-ES6)
 * - No JNI/NDK required (pure JVM)
 * - Complete ECMAScript implementation
 * - Good performance on Android
 *
 * Recommended by: https://github.com/nicklockwood/rhino
 */
class JavaScriptRunner : LanguageRunner() {

    private var rhinoContext: RhinoContext? = null

    override fun getLanguageName(): String = "JavaScript"

    override fun getSupportedExtensions(): List<String> = listOf("js")

    override fun getName(): String = "JavaScript (Rhino)"

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
            showExecutionResult(context, result, fileObject.getName())
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
                    // Set optimization level to -1 to interpret code (required on Android)
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
                            },
                            info: function() {
                                var args = Array.prototype.slice.call(arguments);
                                java.lang.System.out.println('[INFO] ' + args.join(' '));
                            }
                        };
                        var print = console.log;
                    """
                            .trimIndent()

                    ctx.evaluateString(scope, consoleScript, "console", 1, null)

                    // Redirect System.out to capture output
                    System.setOut(printStream)

                    val result = ctx.evaluateString(scope, code, "script.js", 1, null)

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
                    errorOutput = "JavaScript Error: ${e.message}\nAt line ${e.lineNumber()}",
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
