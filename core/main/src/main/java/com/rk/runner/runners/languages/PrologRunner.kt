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
import it.unibo.tuprolog.core.Struct
import it.unibo.tuprolog.core.parsing.ParseException
import it.unibo.tuprolog.solve.MutableSolver
import it.unibo.tuprolog.solve.classic.ClassicSolverFactory
import it.unibo.tuprolog.theory.parsing.ClausesParser
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.lang.ref.WeakReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Prolog language runner using tuProlog (full Prolog on JVM).
 *
 * Features:
 * - Full Prolog support
 * - No JNI/NDK required (pure JVM)
 * - Logic programming
 * - Query solving
 * - Unification and backtracking
 *
 * tuProlog is a light-weight Prolog engine designed for multi-paradigm programming,
 * particularly suited for teaching, rapid prototyping, and AI applications.
 *
 * Recommended for: Logic programming, AI, knowledge representation, theorem proving
 */
class PrologRunner : LanguageRunner() {

    private var solver: MutableSolver? = null

    override fun getLanguageName(): String = "Prolog"

    override fun getSupportedExtensions(): List<String> = listOf("pl", "pro", "prolog")

    override fun getName(): String = "Prolog (tuProlog)"

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
                // Redirect output streams
                System.setOut(printStream)
                System.setErr(errorPrintStream)

                // Create tuProlog solver
                solver = ClassicSolverFactory.mutableSolverWithDefaultBuiltins()

                solver?.let { slv ->
                    val output = StringBuilder()

                    try {
                        // Parse the Prolog code into a theory
                        val theory = ClausesParser.withStandardOperators().parseTheory(code)
                        
                        // Load the theory into the solver
                        slv.loadStaticKb(theory)

                        output.appendLine("Theory loaded successfully.")
                        output.appendLine("Facts and rules defined:")
                        
                        // Display loaded clauses
                        val clauseCount = theory.clauses.count()
                        theory.clauses.take(10).forEach { clause ->
                            output.appendLine("  ${clause}")
                        }
                        
                        if (clauseCount > 10) {
                            output.appendLine("  ... and ${clauseCount - 10} more clauses")
                        }

                        output.appendLine()
                        output.appendLine("To query the knowledge base, use Prolog query syntax.")
                        output.appendLine("Example queries you can try:")
                        output.appendLine("  ?- fact(X).")
                        output.appendLine("  ?- rule(A, B).")

                    } catch (e: ParseException) {
                        System.setErr(errorPrintStream)
                        errorPrintStream.println("Parse Error: ${e.message}")
                    }

                    System.setOut(originalOut)
                    System.setErr(originalErr)

                    val executionTime = System.currentTimeMillis() - startTime
                    val systemOutput = outputStream.toString("UTF-8")
                    val errorOutput = errorStream.toString("UTF-8")

                    val finalOutput = when {
                        output.isNotEmpty() -> output.toString()
                        systemOutput.isNotEmpty() -> systemOutput
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
                    errorOutput = "Failed to initialize tuProlog solver",
                    isSuccess = false,
                    executionTimeMs = 0
                )
            } catch (e: Exception) {
                System.setOut(originalOut)
                System.setErr(originalErr)
                val executionTime = System.currentTimeMillis() - startTime
                ExecutionResult(
                    output = outputStream.toString("UTF-8"),
                    errorOutput = "Prolog Error: ${e.message}",
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
        solver = null
    }
}
