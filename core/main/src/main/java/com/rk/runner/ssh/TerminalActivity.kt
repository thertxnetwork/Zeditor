package com.rk.runner.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.rk.DefaultScope
import com.rk.theme.XedTheme
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TerminalActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val serverId = intent.getStringExtra(EXTRA_SERVER_ID) ?: run {
            Toast.makeText(this, "No server selected", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val server = SSHServerManager.getServer(serverId) ?: run {
            Toast.makeText(this, "Server not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
        
        setContent {
            XedTheme {
                TerminalScreen(
                    server = server,
                    filePath = filePath,
                    fileName = fileName,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(
    server: SSHServerConfig,
    filePath: String?,
    fileName: String?,
    onBackPressed: () -> Unit
) {
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Connecting to ${server.name}...") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionManager by remember { mutableStateOf<SSHConnectionManager?>(null) }
    var shellChannel by remember { mutableStateOf<ShellChannel?>(null) }
    var terminalView by remember { mutableStateOf<SSHTerminalView?>(null) }
    
    // Connect the shell channel to terminal view when both are available
    LaunchedEffect(shellChannel, terminalView) {
        val channel = shellChannel
        val view = terminalView
        if (channel != null && view != null) {
            // Set up write callback to send keyboard input to SSH
            view.setWriteCallback { data ->
                DefaultScope.launch(Dispatchers.IO) {
                    try {
                        channel.outputStream.write(data)
                        channel.outputStream.flush()
                    } catch (e: Exception) {
                        // Ignore write errors
                    }
                }
            }
        }
    }
    
    // Connect and set up terminal on start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val manager = SSHConnectionManager(server)
            withContext(Dispatchers.Main) {
                connectionManager = manager
            }
            
            val connectResult = manager.connect()
            if (connectResult.isSuccess) {
                withContext(Dispatchers.Main) {
                    statusText = "Connected to ${server.getDisplayInfo()}"
                }
                
                // Open shell
                val shellResult = manager.openShell()
                if (shellResult.isSuccess) {
                    val channel = shellResult.getOrNull()
                    
                    withContext(Dispatchers.Main) {
                        if (channel != null) {
                            shellChannel = channel
                            isConnected = true
                            statusText = "Shell session started"
                        }
                    }
                    
                    // If file path is provided, execute it after a short delay
                    if (filePath != null && fileName != null && channel != null) {
                        kotlinx.coroutines.delay(500)
                        
                        withContext(Dispatchers.Main) {
                            statusText = "Uploading and executing $fileName..."
                        }
                        
                        // Upload file
                        val remotePath = "${server.workingDirectory}/$fileName"
                        val uploadResult = manager.uploadFile(filePath, remotePath)
                        
                        if (uploadResult.isSuccess) {
                            withContext(Dispatchers.Main) {
                                statusText = "File uploaded to $remotePath"
                            }
                            
                            // Execute based on file extension
                            val command = getExecutionCommand(fileName, remotePath)
                            channel.outputStream.write("$command\n".toByteArray())
                            channel.outputStream.flush()
                        } else {
                            withContext(Dispatchers.Main) {
                                statusText = "Error uploading file: ${uploadResult.exceptionOrNull()?.message}"
                            }
                        }
                    }
                    
                    // Start reading from shell and update terminal
                    if (channel != null) {
                        try {
                            val buffer = ByteArray(4096)
                            while (channel.isOpen()) {
                                val bytesRead = channel.inputStream.read(buffer)
                                if (bytesRead > 0) {
                                    val data = buffer.copyOf(bytesRead)
                                    withContext(Dispatchers.Main) {
                                        terminalView?.appendData(data)
                                    }
                                } else if (bytesRead == -1) {
                                    break
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                statusText = "Connection error: ${e.message}"
                                isConnected = false
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        statusText = "Failed to open shell: ${shellResult.exceptionOrNull()?.message}"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    statusText = "Connection failed: ${connectResult.exceptionOrNull()?.message}"
                }
            }
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            terminalView?.cleanup()
            connectionManager?.disconnect()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal - ${server.name}") },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (isConnected) {
                        IconButton(
                            onClick = {
                                terminalView?.cleanup()
                                connectionManager?.disconnect()
                                isConnected = false
                                statusText = "Disconnected"
                            }
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Disconnect")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Status bar
            if (statusText.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
            
            // Terminal view
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AndroidView(
                    factory = { ctx ->
                        SSHTerminalView(ctx).also { view ->
                            terminalView = view
                            // Write callback will be set by LaunchedEffect when shellChannel is available
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // View updates are handled through state
                    }
                )
            }
        }
    }
}

/**
 * Custom terminal view that uses Termux TerminalEmulator for ANSI processing
 * but handles its own rendering and keyboard input.
 */
class SSHTerminalView(context: Context) : View(context) {
    
    private var emulator: TerminalEmulator? = null
    private var terminalOutput: SSHTerminalOutput? = null
    
    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = Typeface.MONOSPACE
        textSize = 28f
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    
    private var fontWidth: Float = 0f
    private var fontHeight: Float = 0f
    private var fontAscent: Float = 0f
    
    private var columns = 80
    private var rows = 24
    
    private var writeCallback: ((ByteArray) -> Unit)? = null
    
    // Terminal colors (basic 16-color palette)
    private val terminalColors = intArrayOf(
        Color.BLACK,           // 0: Black
        Color.parseColor("#CC0000"),  // 1: Red
        Color.parseColor("#00CC00"),  // 2: Green
        Color.parseColor("#CCCC00"),  // 3: Yellow
        Color.parseColor("#0000CC"),  // 4: Blue
        Color.parseColor("#CC00CC"),  // 5: Magenta
        Color.parseColor("#00CCCC"),  // 6: Cyan
        Color.parseColor("#CCCCCC"),  // 7: White
        Color.parseColor("#666666"),  // 8: Bright Black
        Color.parseColor("#FF0000"),  // 9: Bright Red
        Color.parseColor("#00FF00"),  // 10: Bright Green
        Color.parseColor("#FFFF00"),  // 11: Bright Yellow
        Color.parseColor("#0000FF"),  // 12: Bright Blue
        Color.parseColor("#FF00FF"),  // 13: Bright Magenta
        Color.parseColor("#00FFFF"),  // 14: Bright Cyan
        Color.WHITE            // 15: Bright White
    )
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Calculate font metrics
        paint.color = Color.GREEN
        fontWidth = paint.measureText("M")
        val metrics = paint.fontMetrics
        fontHeight = metrics.descent - metrics.ascent
        fontAscent = -metrics.ascent
    }
    
    fun setWriteCallback(callback: (ByteArray) -> Unit) {
        writeCallback = callback
        initializeEmulator()
    }
    
    private fun initializeEmulator() {
        if (width > 0 && height > 0) {
            columns = maxOf(4, (width / fontWidth).toInt())
            rows = maxOf(4, (height / fontHeight).toInt())
        }
        
        terminalOutput = SSHTerminalOutput { data ->
            writeCallback?.invoke(data)
        }
        
        val sessionClient = object : TerminalSessionClient {
            override fun onTextChanged(changedSession: com.termux.terminal.TerminalSession?) {
                post { invalidate() }
            }
            override fun onTitleChanged(changedSession: com.termux.terminal.TerminalSession?) {}
            override fun onSessionFinished(finishedSession: com.termux.terminal.TerminalSession?) {}
            override fun onCopyTextToClipboard(session: com.termux.terminal.TerminalSession?, text: String?) {
                text?.let {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("terminal", it))
                }
            }
            override fun onPasteTextFromClipboard(session: com.termux.terminal.TerminalSession?) {}
            override fun onBell(session: com.termux.terminal.TerminalSession?) {}
            override fun onColorsChanged(session: com.termux.terminal.TerminalSession?) { post { invalidate() } }
            override fun onTerminalCursorStateChange(state: Boolean) { post { invalidate() } }
            override fun setTerminalShellPid(session: com.termux.terminal.TerminalSession?, pid: Int) {}
            override fun getTerminalCursorStyle(): Int = 0
            override fun logError(tag: String?, message: String?) {}
            override fun logWarn(tag: String?, message: String?) {}
            override fun logInfo(tag: String?, message: String?) {}
            override fun logDebug(tag: String?, message: String?) {}
            override fun logVerbose(tag: String?, message: String?) {}
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
        
        emulator = TerminalEmulator(
            terminalOutput,
            columns,
            rows,
            fontWidth.toInt(),
            fontHeight.toInt(),
            2000,
            sessionClient
        )
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val newColumns = maxOf(4, (w / fontWidth).toInt())
            val newRows = maxOf(4, (h / fontHeight).toInt())
            
            if (emulator != null && (newColumns != columns || newRows != rows)) {
                columns = newColumns
                rows = newRows
                emulator?.resize(columns, rows, fontWidth.toInt(), fontHeight.toInt())
            } else if (emulator == null && writeCallback != null) {
                columns = newColumns
                rows = newRows
                initializeEmulator()
            }
        }
    }
    
    fun appendData(data: ByteArray) {
        emulator?.append(data, data.size)
        invalidate()
    }
    
    fun cleanup() {
        writeCallback = null
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        val screen = emulator?.screen ?: return
        
        // Draw terminal content - process one row at a time for efficiency
        for (row in 0 until rows) {
            val y = row * fontHeight + fontAscent
            
            // Get the entire row text at once
            val rowText = screen.getSelectedText(0, row, columns, row + 1)
            
            // Draw each character in the row
            for (col in 0 until minOf(columns, rowText.length)) {
                val x = col * fontWidth
                val char = rowText[col]
                
                if (char != ' ' && char != '\u0000') {
                    // Get the style for coloring
                    val style = screen.getStyleAt(row, col)
                    val fg = com.termux.terminal.TextStyle.decodeForeColor(style)
                    
                    // Set foreground color
                    paint.color = when {
                        fg >= 0 && fg < terminalColors.size -> terminalColors[fg]
                        fg == com.termux.terminal.TextStyle.COLOR_INDEX_FOREGROUND -> Color.GREEN
                        else -> Color.GREEN
                    }
                    
                    canvas.drawText(char.toString(), x, y, paint)
                }
            }
        }
        
        // Draw cursor
        if (emulator?.shouldCursorBeVisible() == true) {
            val cursorX = emulator!!.cursorCol * fontWidth
            val cursorY = emulator!!.cursorRow * fontHeight
            
            paint.color = Color.GREEN
            paint.style = Paint.Style.FILL
            canvas.drawRect(
                cursorX,
                cursorY,
                cursorX + fontWidth,
                cursorY + fontHeight,
                paint
            )
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            // Show keyboard when touched
            requestFocus()
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(this, 0)
        }
        return true
    }
    
    override fun onCheckIsTextEditor(): Boolean = true
    
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.toByteArray()?.let { bytes ->
                    writeCallback?.invoke(bytes)
                }
                return true
            }
            
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                // Send backspace
                for (i in 0 until beforeLength) {
                    writeCallback?.invoke(byteArrayOf(0x7F)) // DEL
                }
                return true
            }
            
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_ENTER -> writeCallback?.invoke(byteArrayOf('\r'.code.toByte()))
                        KeyEvent.KEYCODE_DEL -> writeCallback?.invoke(byteArrayOf(0x7F))
                        KeyEvent.KEYCODE_TAB -> writeCallback?.invoke(byteArrayOf('\t'.code.toByte()))
                        KeyEvent.KEYCODE_ESCAPE -> writeCallback?.invoke(byteArrayOf(0x1B))
                        KeyEvent.KEYCODE_DPAD_UP -> writeCallback?.invoke("\u001B[A".toByteArray())
                        KeyEvent.KEYCODE_DPAD_DOWN -> writeCallback?.invoke("\u001B[B".toByteArray())
                        KeyEvent.KEYCODE_DPAD_RIGHT -> writeCallback?.invoke("\u001B[C".toByteArray())
                        KeyEvent.KEYCODE_DPAD_LEFT -> writeCallback?.invoke("\u001B[D".toByteArray())
                        else -> {
                            val unicodeChar = event.unicodeChar
                            if (unicodeChar != 0) {
                                val text = unicodeChar.toChar().toString()
                                writeCallback?.invoke(text.toByteArray())
                            }
                        }
                    }
                }
                return true
            }
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                writeCallback?.invoke(byteArrayOf('\r'.code.toByte()))
                return true
            }
            KeyEvent.KEYCODE_DEL -> {
                writeCallback?.invoke(byteArrayOf(0x7F))
                return true
            }
            KeyEvent.KEYCODE_TAB -> {
                writeCallback?.invoke(byteArrayOf('\t'.code.toByte()))
                return true
            }
            KeyEvent.KEYCODE_ESCAPE -> {
                writeCallback?.invoke(byteArrayOf(0x1B))
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                writeCallback?.invoke("\u001B[A".toByteArray())
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                writeCallback?.invoke("\u001B[B".toByteArray())
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                writeCallback?.invoke("\u001B[C".toByteArray())
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                writeCallback?.invoke("\u001B[D".toByteArray())
                return true
            }
            else -> {
                val unicodeChar = event.unicodeChar
                if (unicodeChar != 0) {
                    writeCallback?.invoke(unicodeChar.toChar().toString().toByteArray())
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

/**
 * Terminal output implementation that writes to SSH
 */
class SSHTerminalOutput(private val writeCallback: (ByteArray) -> Unit) : TerminalOutput() {
    override fun write(data: ByteArray, offset: Int, count: Int) {
        writeCallback(data.copyOfRange(offset, offset + count))
    }
    
    override fun titleChanged(oldTitle: String?, newTitle: String?) {}
    override fun onCopyTextToClipboard(text: String?) {}
    override fun onPasteTextFromClipboard() {}
    override fun onBell() {}
    override fun onColorsChanged() {}
}

private fun getExecutionCommand(fileName: String, remotePath: String): String {
    return when {
        fileName.endsWith(".py") -> "python3 $remotePath"
        fileName.endsWith(".js") -> "node $remotePath"
        fileName.endsWith(".sh") -> "bash $remotePath"
        fileName.endsWith(".rb") -> "ruby $remotePath"
        fileName.endsWith(".php") -> "php $remotePath"
        fileName.endsWith(".pl") -> "perl $remotePath"
        fileName.endsWith(".go") -> "go run $remotePath"
        fileName.endsWith(".rs") -> "rustc $remotePath -o ${remotePath.removeSuffix(".rs")} && ${remotePath.removeSuffix(".rs")}"
        fileName.endsWith(".c") -> "gcc $remotePath -o ${remotePath.removeSuffix(".c")} && ${remotePath.removeSuffix(".c")}"
        fileName.endsWith(".cpp") || fileName.endsWith(".cc") -> 
            "g++ $remotePath -o ${remotePath.substringBeforeLast(".")} && ${remotePath.substringBeforeLast(".")}"
        fileName.endsWith(".java") -> {
            val className = fileName.removeSuffix(".java")
            "javac $remotePath && java $className"
        }
        fileName.endsWith(".kt") -> "kotlinc $remotePath -include-runtime -d ${remotePath.removeSuffix(".kt")}.jar && java -jar ${remotePath.removeSuffix(".kt")}.jar"
        else -> "cat $remotePath"
    }
}
