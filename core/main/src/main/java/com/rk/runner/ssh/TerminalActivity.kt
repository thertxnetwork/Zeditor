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
 * Custom terminal view with built-in ANSI escape sequence processing.
 * No external dependencies required.
 */
class SSHTerminalView(context: Context) : View(context) {
    
    // Terminal buffer - each cell contains a character and color info
    private data class TerminalCell(var char: Char = ' ', var fgColor: Int = Color.GREEN, var bgColor: Int = Color.BLACK)
    
    private var screenBuffer: Array<Array<TerminalCell>> = arrayOf()
    private var cursorRow = 0
    private var cursorCol = 0
    private var columns = 80
    private var rows = 24
    
    // Current text attributes
    private var currentFgColor = Color.GREEN
    private var currentBgColor = Color.BLACK
    private var isBold = false
    
    // ANSI escape sequence parsing state
    private var escapeState = EscapeState.NORMAL
    private val escapeBuffer = StringBuilder()
    
    private enum class EscapeState {
        NORMAL, ESCAPE, CSI
    }
    
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
        
        initializeBuffer()
    }
    
    private fun initializeBuffer() {
        screenBuffer = Array(rows) { Array(columns) { TerminalCell() } }
    }
    
    fun setWriteCallback(callback: (ByteArray) -> Unit) {
        writeCallback = callback
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val newColumns = maxOf(4, (w / fontWidth).toInt())
            val newRows = maxOf(4, (h / fontHeight).toInt())
            
            if (newColumns != columns || newRows != rows) {
                columns = newColumns
                rows = newRows
                initializeBuffer()
                cursorRow = minOf(cursorRow, rows - 1)
                cursorCol = minOf(cursorCol, columns - 1)
            }
        }
    }
    
    fun appendData(data: ByteArray) {
        val text = String(data, Charsets.UTF_8)
        for (char in text) {
            processChar(char)
        }
        invalidate()
    }
    
    private fun processChar(char: Char) {
        when (escapeState) {
            EscapeState.NORMAL -> {
                when (char) {
                    '\u001B' -> { // ESC
                        escapeState = EscapeState.ESCAPE
                        escapeBuffer.clear()
                    }
                    '\r' -> { // Carriage return
                        cursorCol = 0
                    }
                    '\n' -> { // Line feed
                        newLine()
                    }
                    '\b' -> { // Backspace
                        if (cursorCol > 0) cursorCol--
                    }
                    '\t' -> { // Tab
                        val nextTab = ((cursorCol / 8) + 1) * 8
                        cursorCol = minOf(nextTab, columns - 1)
                    }
                    '\u0007' -> { // Bell - ignore
                    }
                    else -> {
                        if (char.code >= 32) { // Printable characters
                            putChar(char)
                        }
                    }
                }
            }
            EscapeState.ESCAPE -> {
                when (char) {
                    '[' -> {
                        escapeState = EscapeState.CSI
                        escapeBuffer.clear()
                    }
                    else -> {
                        // Unknown escape sequence, return to normal
                        escapeState = EscapeState.NORMAL
                    }
                }
            }
            EscapeState.CSI -> {
                if (char in '0'..'9' || char == ';' || char == '?') {
                    escapeBuffer.append(char)
                } else {
                    // End of CSI sequence
                    processCSI(char, escapeBuffer.toString())
                    escapeState = EscapeState.NORMAL
                }
            }
        }
    }
    
    private fun processCSI(command: Char, params: String) {
        val args = if (params.isEmpty()) listOf(0) else {
            params.split(';').mapNotNull { it.toIntOrNull() }.ifEmpty { listOf(0) }
        }
        
        when (command) {
            'A' -> { // Cursor up
                cursorRow = maxOf(0, cursorRow - maxOf(1, args.getOrElse(0) { 1 }))
            }
            'B' -> { // Cursor down
                cursorRow = minOf(rows - 1, cursorRow + maxOf(1, args.getOrElse(0) { 1 }))
            }
            'C' -> { // Cursor forward
                cursorCol = minOf(columns - 1, cursorCol + maxOf(1, args.getOrElse(0) { 1 }))
            }
            'D' -> { // Cursor back
                cursorCol = maxOf(0, cursorCol - maxOf(1, args.getOrElse(0) { 1 }))
            }
            'H', 'f' -> { // Cursor position
                cursorRow = maxOf(0, minOf(rows - 1, args.getOrElse(0) { 1 } - 1))
                cursorCol = maxOf(0, minOf(columns - 1, args.getOrElse(1) { 1 } - 1))
            }
            'J' -> { // Erase in display
                when (args.getOrElse(0) { 0 }) {
                    0 -> clearFromCursor() // Clear from cursor to end
                    1 -> clearToCursor()   // Clear from start to cursor
                    2, 3 -> clearScreen()  // Clear entire screen
                }
            }
            'K' -> { // Erase in line
                when (args.getOrElse(0) { 0 }) {
                    0 -> clearLineFromCursor() // Clear from cursor to end of line
                    1 -> clearLineToCursor()   // Clear from start to cursor
                    2 -> clearLine()           // Clear entire line
                }
            }
            'm' -> { // SGR - Select Graphic Rendition
                processSGR(args)
            }
            'r' -> { // Set scrolling region - ignore for now
            }
            's' -> { // Save cursor position - ignore for now
            }
            'u' -> { // Restore cursor position - ignore for now
            }
        }
    }
    
    private fun processSGR(args: List<Int>) {
        var i = 0
        while (i < args.size) {
            when (val code = args[i]) {
                0 -> { // Reset
                    currentFgColor = Color.GREEN
                    currentBgColor = Color.BLACK
                    isBold = false
                }
                1 -> isBold = true
                22 -> isBold = false
                in 30..37 -> { // Foreground color
                    val colorIndex = code - 30 + (if (isBold) 8 else 0)
                    currentFgColor = terminalColors.getOrElse(colorIndex) { Color.GREEN }
                }
                38 -> { // Extended foreground color
                    if (i + 2 < args.size && args[i + 1] == 5) {
                        val colorIndex = args[i + 2]
                        currentFgColor = get256Color(colorIndex)
                        i += 2
                    }
                }
                39 -> currentFgColor = Color.GREEN // Default foreground
                in 40..47 -> { // Background color
                    currentBgColor = terminalColors.getOrElse(code - 40) { Color.BLACK }
                }
                48 -> { // Extended background color
                    if (i + 2 < args.size && args[i + 1] == 5) {
                        val colorIndex = args[i + 2]
                        currentBgColor = get256Color(colorIndex)
                        i += 2
                    }
                }
                49 -> currentBgColor = Color.BLACK // Default background
                in 90..97 -> { // Bright foreground colors
                    currentFgColor = terminalColors.getOrElse(code - 90 + 8) { Color.GREEN }
                }
                in 100..107 -> { // Bright background colors
                    currentBgColor = terminalColors.getOrElse(code - 100 + 8) { Color.BLACK }
                }
            }
            i++
        }
    }
    
    private fun get256Color(index: Int): Int {
        return when {
            index < 16 -> terminalColors.getOrElse(index) { Color.GREEN }
            index < 232 -> {
                // 216 color cube (6x6x6)
                val i = index - 16
                val r = ((i / 36) % 6) * 51
                val g = ((i / 6) % 6) * 51
                val b = (i % 6) * 51
                Color.rgb(r, g, b)
            }
            else -> {
                // Grayscale (24 shades)
                val gray = (index - 232) * 10 + 8
                Color.rgb(gray, gray, gray)
            }
        }
    }
    
    private fun putChar(char: Char) {
        if (cursorRow in 0 until rows && cursorCol in 0 until columns) {
            screenBuffer[cursorRow][cursorCol] = TerminalCell(char, currentFgColor, currentBgColor)
            cursorCol++
            if (cursorCol >= columns) {
                cursorCol = 0
                newLine()
            }
        }
    }
    
    private fun newLine() {
        cursorRow++
        if (cursorRow >= rows) {
            // Scroll up
            for (r in 0 until rows - 1) {
                screenBuffer[r] = screenBuffer[r + 1]
            }
            screenBuffer[rows - 1] = Array(columns) { TerminalCell() }
            cursorRow = rows - 1
        }
    }
    
    private fun clearScreen() {
        for (r in 0 until rows) {
            for (c in 0 until columns) {
                screenBuffer[r][c] = TerminalCell()
            }
        }
        cursorRow = 0
        cursorCol = 0
    }
    
    private fun clearFromCursor() {
        // Clear from cursor to end of line
        for (c in cursorCol until columns) {
            screenBuffer[cursorRow][c] = TerminalCell()
        }
        // Clear all following lines
        for (r in cursorRow + 1 until rows) {
            for (c in 0 until columns) {
                screenBuffer[r][c] = TerminalCell()
            }
        }
    }
    
    private fun clearToCursor() {
        // Clear all preceding lines
        for (r in 0 until cursorRow) {
            for (c in 0 until columns) {
                screenBuffer[r][c] = TerminalCell()
            }
        }
        // Clear from start of line to cursor
        for (c in 0..cursorCol) {
            screenBuffer[cursorRow][c] = TerminalCell()
        }
    }
    
    private fun clearLine() {
        for (c in 0 until columns) {
            screenBuffer[cursorRow][c] = TerminalCell()
        }
    }
    
    private fun clearLineFromCursor() {
        for (c in cursorCol until columns) {
            screenBuffer[cursorRow][c] = TerminalCell()
        }
    }
    
    private fun clearLineToCursor() {
        for (c in 0..cursorCol) {
            screenBuffer[cursorRow][c] = TerminalCell()
        }
    }
    
    fun cleanup() {
        writeCallback = null
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        // Draw terminal content
        for (row in 0 until minOf(rows, screenBuffer.size)) {
            val y = row * fontHeight + fontAscent
            for (col in 0 until minOf(columns, screenBuffer[row].size)) {
                val cell = screenBuffer[row][col]
                val x = col * fontWidth
                
                // Draw background if not black
                if (cell.bgColor != Color.BLACK) {
                    backgroundPaint.color = cell.bgColor
                    canvas.drawRect(x, row * fontHeight, x + fontWidth, (row + 1) * fontHeight, backgroundPaint)
                    backgroundPaint.color = Color.BLACK
                }
                
                // Draw character
                if (cell.char != ' ' && cell.char != '\u0000') {
                    paint.color = cell.fgColor
                    canvas.drawText(cell.char.toString(), x, y, paint)
                }
            }
        }
        
        // Draw cursor
        val cursorX = cursorCol * fontWidth
        val cursorY = cursorRow * fontHeight
        paint.color = Color.GREEN
        paint.style = Paint.Style.FILL
        paint.alpha = 128
        canvas.drawRect(cursorX, cursorY, cursorX + fontWidth, cursorY + fontHeight, paint)
        paint.alpha = 255
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
