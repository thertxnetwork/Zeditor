package com.rk.terminal

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import java.io.*
import java.util.concurrent.LinkedBlockingQueue

class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val MAX_LINES = 5000
        private const val CHAR_WIDTH = 22f
        private const val LINE_HEIGHT = 45f
    }
    
    private val textPaint = TextPaint().apply {
        color = Color.parseColor("#00FF00") // Terminal green
        typeface = Typeface.MONOSPACE
        textSize = 36f
        isAntiAlias = true
    }
    
    private val bgPaint = Paint().apply {
        color = Color.parseColor("#000000") // Black background
        style = Paint.Style.FILL
    }
    
    private val cursorPaint = Paint().apply {
        color = Color.parseColor("#00FF00")
        style = Paint.Style.FILL
    }
    
    private val lines = mutableListOf<String>()
    private var currentLine = StringBuilder()
    private val inputQueue = LinkedBlockingQueue<String>()
    
    private var process: Process? = null
    private var outputReader: Thread? = null
    private var inputWriter: BufferedWriter? = null
    
    private var scrollY = 0f
    private val maxScrollY: Float
        get() = maxOf(0f, (lines.size * LINE_HEIGHT) - height)
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setBackgroundColor(Color.BLACK)
    }
    
    /**
     * Start a terminal session
     */
    fun startSession(command: Array<String>, environment: Map<String, String>) {
        try {
            val processBuilder = ProcessBuilder(*command)
                .redirectErrorStream(true)
            
            // Set environment variables
            processBuilder.environment().apply {
                clear()
                putAll(environment)
            }
            
            val newProcess = processBuilder.start()
            process = newProcess
            
            inputWriter = BufferedWriter(OutputStreamWriter(newProcess.outputStream))
            
            // Start output reader thread
            outputReader = Thread {
                val reader = BufferedReader(InputStreamReader(newProcess.inputStream))
                
                try {
                    val buffer = CharArray(1024)
                    var count: Int
                    
                    while (reader.read(buffer).also { count = it } != -1) {
                        val text = String(buffer, 0, count)
                        post {
                            appendOutput(text)
                        }
                    }
                } catch (e: IOException) {
                    post {
                        val currentProcess = process
                        if (currentProcess?.isAlive == true) {
                            appendOutput("\n[Session terminated unexpectedly]\n")
                        } else {
                            appendOutput("\n[Session ended]\n")
                        }
                    }
                }
            }.apply { start() }
            
            // Start input writer thread
            Thread {
                try {
                    val writer = inputWriter
                    while (newProcess.isAlive && writer != null) {
                        val input = inputQueue.take()
                        writer.write(input)
                        writer.flush()
                    }
                } catch (e: InterruptedException) {
                    // Thread interrupted, exit gracefully
                } catch (e: IOException) {
                    post {
                        appendOutput("\n[Failed to write input]\n")
                    }
                }
            }.start()
            
        } catch (e: Exception) {
            appendOutput("Failed to start session: ${e.message}\n")
        }
    }
    
    /**
     * Send input to the terminal
     */
    fun sendInput(text: String) {
        inputQueue.offer(text)
    }
    
    /**
     * Append text to terminal output
     */
    private fun appendOutput(text: String) {
        for (char in text) {
            when (char) {
                '\n' -> {
                    lines.add(currentLine.toString())
                    currentLine.clear()
                    
                    // Limit number of lines
                    while (lines.size > MAX_LINES) {
                        lines.removeAt(0)
                    }
                }
                '\r' -> {
                    // Carriage return - move to start of line
                    currentLine.clear()
                }
                '\b' -> {
                    // Backspace
                    if (currentLine.isNotEmpty()) {
                        currentLine.deleteCharAt(currentLine.length - 1)
                    }
                }
                else -> {
                    if (char.isISOControl()) {
                        // Ignore other control characters
                        continue
                    }
                    currentLine.append(char)
                }
            }
        }
        
        // Auto-scroll to bottom
        scrollY = maxScrollY
        invalidate()
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)
        
        // Calculate visible range
        val firstVisibleLine = (scrollY / LINE_HEIGHT).toInt()
        val lastVisibleLine = ((scrollY + height) / LINE_HEIGHT).toInt() + 1
        
        // Draw lines
        var y = LINE_HEIGHT - (scrollY % LINE_HEIGHT)
        
        for (i in firstVisibleLine until minOf(lastVisibleLine, lines.size)) {
            canvas.drawText(lines[i], 20f, y, textPaint)
            y += LINE_HEIGHT
        }
        
        // Draw current line
        if (lines.size >= firstVisibleLine && lines.size <= lastVisibleLine) {
            canvas.drawText(currentLine.toString(), 20f, y, textPaint)
            
            // Draw cursor
            val cursorX = 20f + (currentLine.length * CHAR_WIDTH)
            canvas.drawRect(
                cursorX, y - LINE_HEIGHT + 10,
                cursorX + CHAR_WIDTH, y - 5,
                cursorPaint
            )
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        event?.let {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER -> {
                    sendInput("\n")
                    return true
                }
                KeyEvent.KEYCODE_DEL -> {
                    sendInput("\b")
                    return true
                }
                KeyEvent.KEYCODE_TAB -> {
                    sendInput("\t")
                    return true
                }
                else -> {
                    val char = it.unicodeChar
                    if (char != 0) {
                        sendInput(char.toChar().toString())
                        return true
                    }
                }
            }
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            outputReader?.interrupt()
            inputWriter?.close()
            
            // Try graceful shutdown first
            process?.destroy()
            
            // Wait with timeout, then force if needed
            val currentProcess = process
            if (currentProcess != null) {
                // Wait up to 3 seconds for graceful shutdown
                val thread = Thread {
                    try {
                        currentProcess.waitFor()
                    } catch (e: InterruptedException) {
                        // Timeout occurred, will be handled below
                    }
                }
                thread.start()
                thread.join(3000) // 3 second timeout
                
                // Force kill if still alive
                if (currentProcess.isAlive) {
                    currentProcess.destroyForcibly()
                }
            }
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}
