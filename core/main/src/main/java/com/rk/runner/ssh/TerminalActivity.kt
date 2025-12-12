package com.rk.runner.ssh

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.rk.DefaultScope
import com.rk.theme.XedTheme
import com.rk.settings.Settings
import com.rk.settings.ssh.TerminalThemes
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.view.TerminalRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.OutputStream

/**
 * Activity for displaying SSH terminal sessions using ReTerminal's rendering components.
 */
class TerminalActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_SERVER_ID = "server_id"
        const val EXTRA_FILE_PATH = "file_path"
        const val EXTRA_FILE_NAME = "file_name"
        private const val TAG = "TerminalActivity"
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
    var statusText by remember { mutableStateOf("Connecting to ${server.name}...") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionManager by remember { mutableStateOf<SSHConnectionManager?>(null) }
    var shellChannel by remember { mutableStateOf<ShellChannel?>(null) }
    var terminalView by remember { mutableStateOf<SSHTerminalView?>(null) }
    
    // Extra key row state
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    
    // Connect the shell channel to terminal view when both are available
    LaunchedEffect(shellChannel, terminalView) {
        val channel = shellChannel
        val view = terminalView
        if (channel != null && view != null) {
            // Set up the terminal to write to the SSH channel
            view.setOutputStream(channel.outputStream)
            
            // Start reading from SSH and feeding to terminal
            DefaultScope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(4096)
                    while (channel.isOpen()) {
                        val bytesRead = channel.inputStream.read(buffer)
                        if (bytesRead > 0) {
                            view.appendData(buffer, bytesRead)
                            withContext(Dispatchers.Main) {
                                view.invalidate()
                            }
                        } else if (bytesRead == -1) {
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("TerminalActivity", "Read error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        statusText = "Connection error: ${e.message}"
                        isConnected = false
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
            shellChannel?.close()
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
                                shellChannel?.close()
                                connectionManager?.disconnect()
                                isConnected = false
                                statusText = "Disconnected"
                            }
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "Disconnect")
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
                .imePadding()
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
                            // Apply theme
                            val theme = TerminalThemes.getThemeById(Settings.terminal_theme)
                            view.setTerminalColors(theme)
                            view.setTextSize(Settings.terminal_font_size)
                            view.setModifierKeyStates(
                                ctrlGetter = { ctrlActive },
                                altGetter = { altActive },
                                shiftGetter = { shiftActive }
                            )
                            terminalView = view
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Update modifier states
                        view.setModifierKeyStates(
                            ctrlGetter = { ctrlActive },
                            altGetter = { altActive },
                            shiftGetter = { shiftActive }
                        )
                    }
                )
            }
            
            // Extra key row
            if (Settings.terminal_show_extra_keys) {
                ExtraKeyRow(
                    terminalView = terminalView,
                    ctrlActive = ctrlActive,
                    altActive = altActive,
                    shiftActive = shiftActive,
                    onCtrlToggle = { ctrlActive = !ctrlActive },
                    onAltToggle = { altActive = !altActive },
                    onShiftToggle = { shiftActive = !shiftActive }
                )
            }
        }
    }
}

@Composable
fun ExtraKeyRow(
    terminalView: SSHTerminalView?,
    ctrlActive: Boolean,
    altActive: Boolean,
    shiftActive: Boolean,
    onCtrlToggle: () -> Unit,
    onAltToggle: () -> Unit,
    onShiftToggle: () -> Unit
) {
    val scrollState = rememberScrollState()
    
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ESC key
            ExtraKeyButton(text = "ESC") {
                terminalView?.writeToTerminal(byteArrayOf(0x1B))
            }
            
            // Tab key
            ExtraKeyButton(text = "TAB") {
                terminalView?.writeToTerminal(byteArrayOf('\t'.code.toByte()))
            }
            
            // Modifier keys (toggles)
            ExtraKeyButton(text = "CTRL", isActive = ctrlActive, onClick = onCtrlToggle)
            ExtraKeyButton(text = "ALT", isActive = altActive, onClick = onAltToggle)
            ExtraKeyButton(text = "SHIFT", isActive = shiftActive, onClick = onShiftToggle)
            
            if (Settings.terminal_show_arrow_keys) {
                ExtraKeyButton(text = "↑") { terminalView?.writeToTerminal("\u001B[A".toByteArray()) }
                ExtraKeyButton(text = "↓") { terminalView?.writeToTerminal("\u001B[B".toByteArray()) }
                ExtraKeyButton(text = "←") { terminalView?.writeToTerminal("\u001B[D".toByteArray()) }
                ExtraKeyButton(text = "→") { terminalView?.writeToTerminal("\u001B[C".toByteArray()) }
            }
            
            ExtraKeyButton(text = "HOME") { terminalView?.writeToTerminal("\u001B[H".toByteArray()) }
            ExtraKeyButton(text = "END") { terminalView?.writeToTerminal("\u001B[F".toByteArray()) }
            ExtraKeyButton(text = "PGUP") { terminalView?.writeToTerminal("\u001B[5~".toByteArray()) }
            ExtraKeyButton(text = "PGDN") { terminalView?.writeToTerminal("\u001B[6~".toByteArray()) }
            
            ExtraKeyButton(text = "-") { terminalView?.writeToTerminal("-".toByteArray()) }
            ExtraKeyButton(text = "/") { terminalView?.writeToTerminal("/".toByteArray()) }
            ExtraKeyButton(text = "|") { terminalView?.writeToTerminal("|".toByteArray()) }
            ExtraKeyButton(text = "~") { terminalView?.writeToTerminal("~".toByteArray()) }
        }
    }
}

@Composable
fun ExtraKeyButton(
    text: String,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        )
    }
}

/**
 * SSH Terminal View using ReTerminal's TerminalEmulator and TerminalRenderer.
 * This view handles terminal emulation and rendering for SSH connections.
 */
class SSHTerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    
    companion object {
        private const val TAG = "SSHTerminalView"
        private const val DEFAULT_COLUMNS = 80
        private const val DEFAULT_ROWS = 24
        private const val DEFAULT_TRANSCRIPT_ROWS = 2000
    }
    
    // Terminal emulation components from ReTerminal
    private var emulator: TerminalEmulator? = null
    private var renderer: TerminalRenderer? = null
    private var terminalOutput: SSHTerminalOutput? = null
    
    // SSH output stream
    private var outputStream: OutputStream? = null
    
    // Display settings
    private var textSize = 14
    private var backgroundColor = 0xFF1E1E1E.toInt()
    private var foregroundColor = 0xFFD4D4D4.toInt()
    
    // Scrolling
    private var topRow = 0
    
    // Modifier key state getters
    private var ctrlKeyGetter: () -> Boolean = { false }
    private var altKeyGetter: () -> Boolean = { false }
    private var shiftKeyGetter: () -> Boolean = { false }
    
    // Gesture detector for taps
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            requestFocus()
            showSoftKeyboard()
            return true
        }
        
        override fun onLongPress(e: MotionEvent) {
            // Could implement copy/paste here
        }
    })
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        initializeTerminal()
    }
    
    private fun initializeTerminal() {
        renderer = TerminalRenderer(textSize, Typeface.MONOSPACE)
        terminalOutput = SSHTerminalOutput { data, offset, count ->
            try {
                outputStream?.write(data, offset, count)
                outputStream?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Write error: ${e.message}")
            }
        }
        
        emulator = TerminalEmulator(
            terminalOutput,
            DEFAULT_COLUMNS,
            DEFAULT_ROWS,
            renderer!!.getFontWidth().toInt(),
            renderer!!.getFontLineSpacing(),
            DEFAULT_TRANSCRIPT_ROWS,
            null // TerminalSessionClient not needed for direct emulator usage
        )
    }
    
    fun setOutputStream(stream: OutputStream) {
        outputStream = stream
    }
    
    fun appendData(data: ByteArray, length: Int) {
        emulator?.append(data, length)
    }
    
    fun writeToTerminal(data: ByteArray) {
        try {
            outputStream?.write(data)
            outputStream?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Write error: ${e.message}")
        }
    }
    
    fun setTextSize(size: Int) {
        textSize = size
        renderer = TerminalRenderer(size, Typeface.MONOSPACE)
        updateTerminalSize()
        invalidate()
    }
    
    fun setTerminalColors(theme: com.rk.settings.ssh.TerminalTheme) {
        backgroundColor = theme.backgroundColor
        foregroundColor = theme.foregroundColor
        
        emulator?.mColors?.apply {
            mCurrentColors[0] = theme.black
            mCurrentColors[1] = theme.red
            mCurrentColors[2] = theme.green
            mCurrentColors[3] = theme.yellow
            mCurrentColors[4] = theme.blue
            mCurrentColors[5] = theme.magenta
            mCurrentColors[6] = theme.cyan
            mCurrentColors[7] = theme.white
            mCurrentColors[8] = theme.brightBlack
            mCurrentColors[9] = theme.brightRed
            mCurrentColors[10] = theme.brightGreen
            mCurrentColors[11] = theme.brightYellow
            mCurrentColors[12] = theme.brightBlue
            mCurrentColors[13] = theme.brightMagenta
            mCurrentColors[14] = theme.brightCyan
            mCurrentColors[15] = theme.brightWhite
            mCurrentColors[256] = theme.foregroundColor
            mCurrentColors[258] = theme.backgroundColor
            mCurrentColors[259] = theme.cursorColor
        }
        
        invalidate()
    }
    
    fun setModifierKeyStates(
        ctrlGetter: () -> Boolean,
        altGetter: () -> Boolean,
        shiftGetter: () -> Boolean
    ) {
        ctrlKeyGetter = ctrlGetter
        altKeyGetter = altGetter
        shiftKeyGetter = shiftGetter
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateTerminalSize()
    }
    
    private fun updateTerminalSize() {
        val r = renderer ?: return
        val em = emulator ?: return
        
        if (width == 0 || height == 0) return
        
        val newColumns = maxOf(4, (width / r.getFontWidth()).toInt())
        val newRows = maxOf(4, (height - r.mFontLineSpacingAndAscent) / r.getFontLineSpacing())
        
        if (newColumns != em.mColumns || newRows != em.mRows) {
            em.resize(newColumns, newRows, r.getFontWidth().toInt(), r.getFontLineSpacing())
            topRow = 0
        }
    }
    
    override fun onDraw(canvas: Canvas) {
        val em = emulator
        val r = renderer
        
        if (em == null || r == null) {
            canvas.drawColor(backgroundColor)
            return
        }
        
        // Draw background
        canvas.drawColor(backgroundColor)
        
        // Render terminal content
        r.render(em, canvas, topRow, -1, -1, -1, -1)
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(event)
        return true
    }
    
    override fun onCheckIsTextEditor(): Boolean = true
    
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                if (text != null) {
                    sendTextToTerminal(text)
                }
                return true
            }
            
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                for (i in 0 until beforeLength) {
                    writeToTerminal(byteArrayOf(0x7F)) // DEL
                }
                return true
            }
        }
    }
    
    private fun sendTextToTerminal(text: CharSequence) {
        val ctrl = ctrlKeyGetter()
        
        for (i in text.indices) {
            var codePoint = text[i].code
            
            if (ctrl && codePoint >= 'a'.code && codePoint <= 'z'.code) {
                codePoint = codePoint - 'a'.code + 1
            } else if (ctrl && codePoint >= 'A'.code && codePoint <= 'Z'.code) {
                codePoint = codePoint - 'A'.code + 1
            }
            
            if (codePoint == '\n'.code) {
                codePoint = '\r'.code
            }
            
            writeCodePoint(codePoint)
        }
    }
    
    private fun writeCodePoint(codePoint: Int) {
        val alt = altKeyGetter()
        val bytes = mutableListOf<Byte>()
        
        if (alt) {
            bytes.add(0x1B) // ESC for alt
        }
        
        if (codePoint <= 0x7F) {
            bytes.add(codePoint.toByte())
        } else if (codePoint <= 0x7FF) {
            bytes.add((0xC0 or (codePoint shr 6)).toByte())
            bytes.add((0x80 or (codePoint and 0x3F)).toByte())
        } else if (codePoint <= 0xFFFF) {
            bytes.add((0xE0 or (codePoint shr 12)).toByte())
            bytes.add((0x80 or ((codePoint shr 6) and 0x3F)).toByte())
            bytes.add((0x80 or (codePoint and 0x3F)).toByte())
        } else {
            bytes.add((0xF0 or (codePoint shr 18)).toByte())
            bytes.add((0x80 or ((codePoint shr 12) and 0x3F)).toByte())
            bytes.add((0x80 or ((codePoint shr 6) and 0x3F)).toByte())
            bytes.add((0x80 or (codePoint and 0x3F)).toByte())
        }
        
        writeToTerminal(bytes.toByteArray())
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (emulator == null) return super.onKeyDown(keyCode, event)
        
        when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> writeToTerminal(byteArrayOf('\r'.code.toByte()))
            KeyEvent.KEYCODE_DEL -> writeToTerminal(byteArrayOf(0x7F))
            KeyEvent.KEYCODE_TAB -> writeToTerminal(byteArrayOf('\t'.code.toByte()))
            KeyEvent.KEYCODE_ESCAPE -> writeToTerminal(byteArrayOf(0x1B))
            KeyEvent.KEYCODE_DPAD_UP -> writeToTerminal("\u001B[A".toByteArray())
            KeyEvent.KEYCODE_DPAD_DOWN -> writeToTerminal("\u001B[B".toByteArray())
            KeyEvent.KEYCODE_DPAD_RIGHT -> writeToTerminal("\u001B[C".toByteArray())
            KeyEvent.KEYCODE_DPAD_LEFT -> writeToTerminal("\u001B[D".toByteArray())
            else -> {
                val unicodeChar = event.unicodeChar
                if (unicodeChar != 0) {
                    sendTextToTerminal(unicodeChar.toChar().toString())
                    return true
                }
                return super.onKeyDown(keyCode, event)
            }
        }
        return true
    }
    
    private fun showSoftKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }
}

/**
 * Terminal output handler that sends data to SSH stream.
 */
class SSHTerminalOutput(
    private val writeHandler: (ByteArray, Int, Int) -> Unit
) : TerminalOutput() {
    
    override fun write(data: ByteArray, offset: Int, count: Int) {
        writeHandler(data, offset, count)
    }
    
    override fun titleChanged(oldTitle: String?, newTitle: String?) {
        // Not used for SSH
    }
    
    override fun onCopyTextToClipboard(text: String?) {
        // Could implement clipboard support
    }
    
    override fun onPasteTextFromClipboard() {
        // Could implement clipboard support
    }
    
    override fun onBell() {
        // Could implement bell sound
    }
    
    override fun onColorsChanged() {
        // Not used for SSH
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
