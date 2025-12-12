package com.rk.runner.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import android.widget.PopupWindow
import android.widget.LinearLayout
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import kotlin.math.hypot
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import com.rk.DefaultScope
import com.rk.theme.XedTheme
import com.termux.terminal.KeyHandler
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalRow
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.terminal.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import com.rk.settings.Settings
import com.rk.settings.ssh.TerminalThemes

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
    
    // Extra key row state
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    
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
                .imePadding() // Handle keyboard insets
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
            
            // Extra key row
            ExtraKeyRow(
                terminalView = terminalView,
                ctrlActive = ctrlActive,
                altActive = altActive,
                shiftActive = shiftActive,
                onCtrlToggle = { 
                    ctrlActive = !ctrlActive
                    terminalView?.toggleCtrl()
                },
                onAltToggle = { 
                    altActive = !altActive
                    terminalView?.toggleAlt()
                },
                onShiftToggle = { 
                    shiftActive = !shiftActive
                    terminalView?.toggleShift()
                }
            )
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
                terminalView?.sendKey(byteArrayOf(0x1B))
            }
            
            // Tab key
            ExtraKeyButton(text = "TAB") {
                terminalView?.sendKey(byteArrayOf('\t'.code.toByte()))
            }
            
            // Ctrl modifier (toggle)
            ExtraKeyButton(
                text = "CTRL",
                isActive = ctrlActive,
                onClick = onCtrlToggle
            )
            
            // Alt modifier (toggle)
            ExtraKeyButton(
                text = "ALT",
                isActive = altActive,
                onClick = onAltToggle
            )
            
            // Shift modifier (toggle)
            ExtraKeyButton(
                text = "SHIFT",
                isActive = shiftActive,
                onClick = onShiftToggle
            )
            
            // Arrow keys
            ExtraKeyButton(text = "↑") {
                terminalView?.sendKey("\u001B[A".toByteArray())
            }
            ExtraKeyButton(text = "↓") {
                terminalView?.sendKey("\u001B[B".toByteArray())
            }
            ExtraKeyButton(text = "←") {
                terminalView?.sendKey("\u001B[D".toByteArray())
            }
            ExtraKeyButton(text = "→") {
                terminalView?.sendKey("\u001B[C".toByteArray())
            }
            
            // Home/End
            ExtraKeyButton(text = "HOME") {
                terminalView?.sendKey("\u001B[H".toByteArray())
            }
            ExtraKeyButton(text = "END") {
                terminalView?.sendKey("\u001B[F".toByteArray())
            }
            
            // Page Up/Down
            ExtraKeyButton(text = "PGUP") {
                terminalView?.sendKey("\u001B[5~".toByteArray())
            }
            ExtraKeyButton(text = "PGDN") {
                terminalView?.sendKey("\u001B[6~".toByteArray())
            }
            
            // Common shortcuts
            ExtraKeyButton(text = "-") {
                terminalView?.sendKey("-".toByteArray())
            }
            ExtraKeyButton(text = "/") {
                terminalView?.sendKey("/".toByteArray())
            }
            ExtraKeyButton(text = "|") {
                terminalView?.sendKey("|".toByteArray())
            }
            ExtraKeyButton(text = "~") {
                terminalView?.sendKey("~".toByteArray())
            }
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
        modifier = Modifier
            .clickable(onClick = onClick)
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
 * Terminal view that uses Termux TerminalEmulator for proper ANSI processing.
 * Supports: scrollback, zoom (pinch), copy/paste, text selection, special keys (ctrl, alt, etc.)
 */
class SSHTerminalView(context: Context) : View(context), TerminalSessionClient {
    
    companion object {
        private const val DEFAULT_FONT_SIZE = 13f
        private const val MIN_FONT_SIZE = 8f
        private const val MAX_FONT_SIZE = 32f
    }
    
    private var emulator: TerminalEmulator? = null
    private var terminalOutput: SSHTerminalOutput? = null
    
    // Font configuration - use monospace font optimized for terminals
    private var currentTypeface: Typeface = Typeface.MONOSPACE
    
    private val paint = Paint().apply {
        isAntiAlias = true
        typeface = currentTypeface
        isSubpixelText = true
        letterSpacing = 0f  // No extra letter spacing
    }
    
    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    
    // Selection paint
    private val selectionPaint = Paint().apply {
        color = Color.argb(80, 100, 100, 255)
        style = Paint.Style.FILL
    }
    
    private var fontSize = DEFAULT_FONT_SIZE
    private var fontWidth: Float = 0f
    private var fontHeight: Float = 0f
    private var fontAscent: Float = 0f
    
    private var columns = 80
    private var rows = 24
    
    // Scrollback support
    private var topRow = 0
    private val scroller = OverScroller(context)
    private var scrollRemainder = 0f
    
    // Text selection support
    private var isSelecting = false
    private var selStartCol = -1
    private var selStartRow = -1
    private var selEndCol = -1
    private var selEndRow = -1
    private var draggingStartHandle = false
    private var draggingEndHandle = false
    
    // Copy/Paste popup
    private var copyPastePopup: android.widget.PopupWindow? = null
    
    // Gesture support for scroll, zoom, and selection
    private val gestureDetector: GestureDetector
    private val scaleDetector: ScaleGestureDetector
    
    // Modifier keys state
    private var ctrlDown = false
    private var altDown = false
    private var shiftDown = false
    private var fnDown = false
    
    // Clipboard
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    
    private var writeCallback: ((ByteArray) -> Unit)? = null
    
    // Current terminal theme
    private var currentTheme = TerminalThemes.getThemeById(Settings.terminal_theme)
    
    // Terminal colors (loaded from theme)
    private var terminalColors = currentTheme.getColorPalette()
    
    // Default colors from theme
    private var defaultForeground = currentTheme.foregroundColor
    private var defaultBackground = currentTheme.backgroundColor
    private var cursorColor = currentTheme.cursorColor
    
    private val mainHandler = Handler(Looper.getMainLooper())
    
    init {
        isFocusable = true
        isFocusableInTouchMode = true
        
        // Apply theme colors to paints
        backgroundPaint.color = defaultBackground
        
        updateFontMetrics()
        
        // Gesture detector for scrolling, taps, and selection
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                // Clear selection on tap
                clearSelection()
                requestFocus()
                showKeyboard()
                return true
            }
            
            override fun onDoubleTap(e: MotionEvent): Boolean {
                // Select word at tap position
                val col = (e.x / fontWidth).toInt().coerceIn(0, columns - 1)
                val row = (e.y / fontHeight).toInt().coerceIn(0, rows - 1)
                selectWordAt(col, row)
                return true
            }
            
            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (emulator == null) return true
                
                scrollRemainder += distanceY
                val deltaRows = (scrollRemainder / fontHeight).toInt()
                scrollRemainder -= deltaRows * fontHeight
                
                doScroll(deltaRows)
                return true
            }
            
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (emulator == null) return true
                
                val screen = emulator?.screen ?: return true
                scroller.fling(0, topRow, 0, -(velocityY * 0.25f).toInt(), 0, 0, -screen.activeTranscriptRows, 0)
                post(flingRunnable)
                return true
            }
            
            override fun onLongPress(e: MotionEvent) {
                // Start selection or show popup
                val col = (e.x / fontWidth).toInt().coerceIn(0, columns - 1)
                val row = (e.y / fontHeight).toInt().coerceIn(0, rows - 1) + topRow
                
                if (hasSelection()) {
                    // Show copy/paste popup
                    showCopyPastePopup(e.x, e.y)
                } else {
                    // Start selection by selecting word at position
                    selectWordAt(col, row - topRow)
                    // Show popup after selection
                    showCopyPastePopup(e.x, e.y)
                }
            }
        })
        
        // Scale detector for pinch zoom
        scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val scaleFactor = detector.scaleFactor
                val newSize = fontSize * scaleFactor
                setFontSize(newSize.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE))
                return true
            }
        })
    }
    
    private val flingRunnable = object : Runnable {
        override fun run() {
            if (scroller.isFinished) return
            if (scroller.computeScrollOffset()) {
                val newTopRow = scroller.currY
                if (newTopRow != topRow) {
                    val screen = emulator?.screen
                    val maxScroll = screen?.activeTranscriptRows ?: 0
                    topRow = newTopRow.coerceIn(-maxScroll, 0)
                    invalidate()
                }
                post(this)
            }
        }
    }
    
    private fun doScroll(deltaRows: Int) {
        if (deltaRows == 0 || emulator == null) return
        
        val screen = emulator?.screen ?: return
        val maxScroll = screen.activeTranscriptRows
        
        topRow = (topRow + deltaRows).coerceIn(-maxScroll, 0)
        invalidate()
    }
    
    // Text selection methods
    private fun startSelection(col: Int, row: Int) {
        isSelecting = true
        selStartCol = col
        selStartRow = row
        selEndCol = col
        selEndRow = row
        invalidate()
    }
    
    private fun updateSelection(col: Int, row: Int) {
        if (isSelecting) {
            selEndCol = col
            selEndRow = row
            invalidate()
        }
    }
    
    private fun endSelection() {
        isSelecting = false
    }
    
    fun clearSelection() {
        selStartCol = -1
        selStartRow = -1
        selEndCol = -1
        selEndRow = -1
        isSelecting = false
        invalidate()
    }
    
    fun hasSelection(): Boolean {
        return selStartCol >= 0 && selStartRow >= 0 && selEndCol >= 0 && selEndRow >= 0
    }
    
    private fun selectWordAt(col: Int, row: Int) {
        val screen = emulator?.screen ?: return
        val actualRow = topRow + row
        
        // Find word boundaries
        val internalRow = screen.externalToInternalRow(actualRow)
        val lineObject = try {
            screen.allocateFullLineIfNecessary(internalRow)
        } catch (e: Exception) {
            return
        }
        
        val line = lineObject.mText
        val lineLen = lineObject.spaceUsed
        
        // Find start of word
        var startCol = col
        while (startCol > 0 && startCol < lineLen && !Character.isWhitespace(line[startCol - 1])) {
            startCol--
        }
        
        // Find end of word
        var endCol = col
        while (endCol < lineLen - 1 && endCol < columns - 1 && !Character.isWhitespace(line[endCol + 1])) {
            endCol++
        }
        
        selStartCol = startCol
        selStartRow = actualRow
        selEndCol = endCol
        selEndRow = actualRow
        invalidate()
    }
    
    fun copySelectionToClipboard() {
        if (!hasSelection()) return
        
        val text = getSelectedText(selStartCol, selStartRow, selEndCol, selEndRow)
        if (!text.isNullOrEmpty()) {
            copyToClipboard(text)
            clearSelection()
            dismissCopyPastePopup()
            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun showCopyPastePopup(x: Float, y: Float) {
        dismissCopyPastePopup()
        
        val popupLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Color.parseColor("#333333"))
            setPadding(16, 8, 16, 8)
        }
        
        val copyBtn = Button(context).apply {
            text = "Copy"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#2196F3"))
            setOnClickListener {
                copySelectionToClipboard()
            }
        }
        
        val pasteBtn = Button(context).apply {
            text = "Paste"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#4CAF50"))
            setOnClickListener {
                pasteFromClipboard()
                dismissCopyPastePopup()
                clearSelection()
            }
        }
        
        val selectAllBtn = Button(context).apply {
            text = "Select All"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#FF9800"))
            setOnClickListener {
                selectAll()
                dismissCopyPastePopup()
            }
        }
        
        popupLayout.addView(copyBtn)
        popupLayout.addView(pasteBtn)
        popupLayout.addView(selectAllBtn)
        
        copyPastePopup = PopupWindow(popupLayout, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true).apply {
            elevation = 10f
            // Use showAtLocation with the view's root for proper positioning
            val location = IntArray(2)
            this@SSHTerminalView.getLocationOnScreen(location)
            showAtLocation(this@SSHTerminalView, Gravity.NO_GRAVITY, location[0] + x.toInt(), location[1] + y.toInt() - 120)
        }
    }
    
    private fun dismissCopyPastePopup() {
        copyPastePopup?.dismiss()
        copyPastePopup = null
    }
    
    private fun selectAll() {
        val screen = emulator?.screen ?: return
        selStartCol = 0
        selStartRow = -screen.activeTranscriptRows
        selEndCol = columns - 1
        selEndRow = rows - 1
        invalidate()
    }
    
    private fun updateFontMetrics() {
        paint.typeface = currentTypeface
        paint.textSize = fontSize * resources.displayMetrics.density
        // Use a monospace character to calculate exact width
        fontWidth = paint.measureText("M")
        val metrics = paint.fontMetrics
        // Proper font spacing - use leading for line spacing
        fontHeight = metrics.descent - metrics.ascent + metrics.leading
        fontAscent = -metrics.ascent
    }
    
    // Set custom font
    fun setTypeface(typeface: Typeface) {
        currentTypeface = typeface
        paint.typeface = typeface
        updateFontMetrics()
        if (width > 0 && height > 0) {
            recalculateSize()
        }
        invalidate()
    }
    
    // Set font from file path
    fun setFontFromPath(fontPath: String): Boolean {
        return try {
            val typeface = Typeface.createFromFile(fontPath)
            setTypeface(typeface)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    // Set terminal theme
    fun setTheme(theme: com.rk.settings.ssh.TerminalTheme) {
        currentTheme = theme
        terminalColors = theme.getColorPalette()
        defaultForeground = theme.foregroundColor
        defaultBackground = theme.backgroundColor
        cursorColor = theme.cursorColor
        backgroundPaint.color = defaultBackground
        invalidate()
    }
    
    // Reload theme from settings
    fun reloadThemeFromSettings() {
        setTheme(TerminalThemes.getThemeById(Settings.terminal_theme))
    }
    
    fun setFontSize(size: Float) {
        fontSize = size.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
        updateFontMetrics()
        if (width > 0 && height > 0) {
            recalculateSize()
        }
        invalidate()
    }
    
    // Send key bytes directly to SSH
    fun sendKey(bytes: ByteArray) {
        writeCallback?.invoke(bytes)
    }
    
    private fun recalculateSize() {
        val newColumns = maxOf(4, (width / fontWidth).toInt())
        val newRows = maxOf(4, (height / fontHeight).toInt())
        
        if (newColumns != columns || newRows != rows) {
            columns = newColumns
            rows = newRows
            emulator?.resize(columns, rows, fontWidth.toInt(), fontHeight.toInt())
        }
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
        
        emulator = TerminalEmulator(
            terminalOutput,
            columns,
            rows,
            fontWidth.toInt(),
            fontHeight.toInt(),
            2000,  // transcript rows for scrollback
            this   // TerminalSessionClient
        )
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            recalculateSize()
            if (emulator == null && writeCallback != null) {
                initializeEmulator()
            }
        }
    }
    
    fun appendData(data: ByteArray) {
        emulator?.append(data, data.size)
        // Reset scroll to bottom on new data
        topRow = 0
        invalidate()
    }
    
    fun cleanup() {
        writeCallback = null
    }
    
    private fun showKeyboard() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(this, 0)
    }
    
    fun pasteFromClipboard() {
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()
            if (!text.isNullOrEmpty()) {
                writeCallback?.invoke(text.toByteArray())
            }
        }
    }
    
    fun copyToClipboard(text: String) {
        clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
    }
    
    // Get selected text from terminal (for copy functionality)
    fun getSelectedText(startCol: Int, startRow: Int, endCol: Int, endRow: Int): String? {
        return emulator?.screen?.getSelectedText(startCol, startRow, endCol, endRow)
    }
    
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw background
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        
        val emu = emulator ?: return
        val screen = emu.screen ?: return
        
        // Draw terminal content with scrollback support
        // Use batch rendering for better performance and correct spacing
        val activeTranscriptRows = screen.activeTranscriptRows
        val screenRows = screen.screenRows
        
        for (row in 0 until rows) {
            val y = row * fontHeight + fontAscent
            val screenRow = topRow + row
            
            // Validate that screenRow is within valid range before accessing
            // Valid range is: -activeTranscriptRows to screenRows-1
            if (screenRow < -activeTranscriptRows || screenRow >= screenRows) {
                continue
            }
            
            // Get the terminal row
            val internalRow = try {
                screen.externalToInternalRow(screenRow)
            } catch (e: IllegalArgumentException) {
                continue
            }
            val lineObject = try {
                screen.allocateFullLineIfNecessary(internalRow)
            } catch (e: Exception) {
                continue
            }
            
            val line = lineObject.mText
            val charsUsed = lineObject.spaceUsed
            
            // Build text runs with same style for batch rendering
            var runStartCol = 0
            var runStartCharIndex = 0
            var currentCharIndex = 0
            var col = 0
            var currentStyle = if (charsUsed > 0) lineObject.getStyle(0) else 0L
            var currentFg = TextStyle.decodeForeColor(currentStyle)
            var currentBg = TextStyle.decodeBackColor(currentStyle)
            var currentEffect = TextStyle.decodeEffect(currentStyle)
            
            val textBuilder = StringBuilder()
            
            while (col < columns && currentCharIndex < charsUsed) {
                // Get character
                val charAtIndex = line[currentCharIndex]
                val isHighSurrogate = Character.isHighSurrogate(charAtIndex)
                val codePoint = if (isHighSurrogate && currentCharIndex + 1 < charsUsed) {
                    Character.toCodePoint(charAtIndex, line[currentCharIndex + 1])
                } else {
                    charAtIndex.code
                }
                val charsForCodePoint = if (isHighSurrogate) 2 else 1
                
                // Get style for this column
                val style = lineObject.getStyle(col)
                val fg = TextStyle.decodeForeColor(style)
                val bg = TextStyle.decodeBackColor(style)
                val effect = TextStyle.decodeEffect(style)
                
                // Check if style changed - if so, flush the current run
                if (fg != currentFg || bg != currentBg || effect != currentEffect) {
                    // Draw the accumulated text run
                    if (textBuilder.isNotEmpty()) {
                        drawTextRun(canvas, textBuilder.toString(), runStartCol, row, y, currentFg, currentBg, currentEffect)
                        textBuilder.clear()
                    }
                    runStartCol = col
                    runStartCharIndex = currentCharIndex
                    currentFg = fg
                    currentBg = bg
                    currentEffect = effect
                }
                
                // Add character to run
                if (codePoint >= 32) {
                    textBuilder.append(String(Character.toChars(codePoint)))
                } else {
                    textBuilder.append(' ')
                }
                
                currentCharIndex += charsForCodePoint
                col++
            }
            
            // Draw remaining text run
            if (textBuilder.isNotEmpty()) {
                drawTextRun(canvas, textBuilder.toString(), runStartCol, row, y, currentFg, currentBg, currentEffect)
            }
        }
        
        // Draw cursor
        if (emu.shouldCursorBeVisible() && topRow == 0) {
            val cursorCol = emu.cursorCol
            val cursorRow = emu.cursorRow
            
            if (cursorRow >= 0 && cursorRow < rows && cursorCol >= 0 && cursorCol < columns) {
                val cursorX = cursorCol * fontWidth
                val cursorY = cursorRow * fontHeight
                
                paint.color = cursorColor
                paint.alpha = 128
                canvas.drawRect(cursorX, cursorY, cursorX + fontWidth, cursorY + fontHeight, paint)
                paint.alpha = 255
            }
        }
        
        // Draw selection highlight
        if (hasSelection()) {
            drawSelection(canvas)
        }
    }
    
    private fun drawSelection(canvas: Canvas) {
        // Normalize selection coordinates (start should be before end)
        val startRow = minOf(selStartRow, selEndRow)
        val endRow = maxOf(selStartRow, selEndRow)
        val startCol = if (selStartRow <= selEndRow) selStartCol else selEndCol
        val endCol = if (selStartRow <= selEndRow) selEndCol else selStartCol
        
        for (row in startRow..endRow) {
            val displayRow = row - topRow
            if (displayRow < 0 || displayRow >= rows) continue
            
            val rowStartCol = if (row == startRow) startCol else 0
            val rowEndCol = if (row == endRow) endCol else columns - 1
            
            val x1 = rowStartCol * fontWidth
            val y1 = displayRow * fontHeight
            val x2 = (rowEndCol + 1) * fontWidth
            val y2 = (displayRow + 1) * fontHeight
            
            canvas.drawRect(x1, y1, x2, y2, selectionPaint)
        }
        
        // Draw selection handles for adjustment
        if (hasSelection()) {
            drawSelectionHandles(canvas, startRow, startCol, endRow, endCol)
        }
    }
    
    private fun drawSelectionHandles(canvas: Canvas, startRow: Int, startCol: Int, endRow: Int, endCol: Int) {
        val handleRadius = 12f * resources.displayMetrics.density
        val handlePaint = Paint().apply {
            color = Color.rgb(33, 150, 243) // Material Blue
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        
        // Start handle
        val startDisplayRow = startRow - topRow
        if (startDisplayRow >= 0 && startDisplayRow < rows) {
            val x = startCol * fontWidth
            val y = (startDisplayRow + 1) * fontHeight
            canvas.drawCircle(x, y, handleRadius, handlePaint)
        }
        
        // End handle
        val endDisplayRow = endRow - topRow
        if (endDisplayRow >= 0 && endDisplayRow < rows) {
            val x = (endCol + 1) * fontWidth
            val y = (endDisplayRow + 1) * fontHeight
            canvas.drawCircle(x, y, handleRadius, handlePaint)
        }
    }
    
    private fun drawTextRun(canvas: Canvas, text: String, startCol: Int, row: Int, y: Float, fg: Int, bg: Int, effect: Int) {
        val x = startCol * fontWidth
        val endCol = startCol + text.length
        
        // Draw background if not default
        val bgColor = getColor(bg, false)
        if (bgColor != defaultBackground) {
            backgroundPaint.color = bgColor
            canvas.drawRect(x, row * fontHeight, endCol * fontWidth, (row + 1) * fontHeight, backgroundPaint)
            backgroundPaint.color = defaultBackground
        }
        
        // Set text color and effects
        paint.color = getColor(fg, true)
        paint.isFakeBoldText = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
        paint.isUnderlineText = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0
        paint.textSkewX = if ((effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC) != 0) -0.25f else 0f
        
        // Draw text as a single string - this ensures proper character spacing
        canvas.drawText(text, x, y, paint)
    }
    
    private fun getColor(colorIndex: Int, isForeground: Boolean): Int {
        return when {
            colorIndex == TextStyle.COLOR_INDEX_FOREGROUND -> if (isForeground) defaultForeground else defaultBackground
            colorIndex == TextStyle.COLOR_INDEX_BACKGROUND -> defaultBackground
            colorIndex >= 0 && colorIndex < 256 -> get256Color(colorIndex)
            else -> if (isForeground) defaultForeground else defaultBackground
        }
    }
    
    private fun get256Color(index: Int): Int {
        return when {
            index < 16 -> terminalColors.getOrElse(index) { defaultForeground }
            index < 232 -> {
                val i = index - 16
                val r = ((i / 36) % 6) * 51
                val g = ((i / 6) % 6) * 51
                val b = (i % 6) * 51
                Color.rgb(r, g, b)
            }
            else -> {
                val gray = (index - 232) * 10 + 8
                Color.rgb(gray, gray, gray)
            }
        }
    }
    
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle selection handle dragging
        if (hasSelection()) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    val handleRadius = 20f * resources.displayMetrics.density
                    val touchX = event.x
                    val touchY = event.y
                    
                    // Check if touching start handle
                    val startDisplayRow = selStartRow - topRow
                    if (startDisplayRow >= 0 && startDisplayRow < rows) {
                        val startHandleX = selStartCol * fontWidth
                        val startHandleY = (startDisplayRow + 1) * fontHeight
                        if (hypot((touchX - startHandleX).toDouble(), (touchY - startHandleY).toDouble()) < handleRadius) {
                            draggingStartHandle = true
                            return true
                        }
                    }
                    
                    // Check if touching end handle
                    val endDisplayRow = selEndRow - topRow
                    if (endDisplayRow >= 0 && endDisplayRow < rows) {
                        val endHandleX = (selEndCol + 1) * fontWidth
                        val endHandleY = (endDisplayRow + 1) * fontHeight
                        if (hypot((touchX - endHandleX).toDouble(), (touchY - endHandleY).toDouble()) < handleRadius) {
                            draggingEndHandle = true
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (draggingStartHandle || draggingEndHandle) {
                        val col = (event.x / fontWidth).toInt().coerceIn(0, columns - 1)
                        val row = (event.y / fontHeight).toInt().coerceIn(0, rows - 1) + topRow
                        
                        if (draggingStartHandle) {
                            selStartCol = col
                            selStartRow = row
                        } else {
                            selEndCol = col
                            selEndRow = row
                        }
                        invalidate()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (draggingStartHandle || draggingEndHandle) {
                        draggingStartHandle = false
                        draggingEndHandle = false
                        // Show copy popup after handle drag
                        showCopyPastePopup(event.x, event.y)
                        return true
                    }
                }
            }
        }
        
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }
    
    override fun onCheckIsTextEditor(): Boolean = true
    
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        
        return object : BaseInputConnection(this, true) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                text?.toString()?.let { str ->
                    if (ctrlDown) {
                        // Handle Ctrl+key combinations
                        for (c in str) {
                            val code = c.uppercaseChar().code
                            if (code in 64..95) {
                                writeCallback?.invoke(byteArrayOf((code - 64).toByte()))
                            }
                        }
                    } else {
                        writeCallback?.invoke(str.toByteArray())
                    }
                }
                return true
            }
            
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) {
                    writeCallback?.invoke(byteArrayOf(0x7F))
                }
                return true
            }
            
            override fun sendKeyEvent(event: KeyEvent): Boolean {
                if (event.action == KeyEvent.ACTION_DOWN) {
                    handleKeyDown(event.keyCode, event)
                }
                return true
            }
        }
    }
    
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return handleKeyDown(keyCode, event)
    }
    
    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> ctrlDown = false
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> altDown = false
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> shiftDown = false
            KeyEvent.KEYCODE_FUNCTION -> fnDown = false
        }
        return super.onKeyUp(keyCode, event)
    }
    
    private fun handleKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val ctrlHeld = event.isCtrlPressed || ctrlDown
        val altHeld = event.isAltPressed || altDown
        val shiftHeld = event.isShiftPressed || shiftDown
        
        // Update modifier state
        when (keyCode) {
            KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_CTRL_RIGHT -> { ctrlDown = true; return true }
            KeyEvent.KEYCODE_ALT_LEFT, KeyEvent.KEYCODE_ALT_RIGHT -> { altDown = true; return true }
            KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_SHIFT_RIGHT -> { shiftDown = true; return true }
            KeyEvent.KEYCODE_FUNCTION -> { fnDown = true; return true }
        }
        
        // Handle special keys
        val bytes: ByteArray? = when (keyCode) {
            KeyEvent.KEYCODE_ENTER -> byteArrayOf('\r'.code.toByte())
            KeyEvent.KEYCODE_DEL -> byteArrayOf(0x7F)
            KeyEvent.KEYCODE_TAB -> if (shiftHeld) "\u001B[Z".toByteArray() else byteArrayOf('\t'.code.toByte())
            KeyEvent.KEYCODE_ESCAPE -> byteArrayOf(0x1B)
            KeyEvent.KEYCODE_DPAD_UP -> "\u001B[A".toByteArray()
            KeyEvent.KEYCODE_DPAD_DOWN -> "\u001B[B".toByteArray()
            KeyEvent.KEYCODE_DPAD_RIGHT -> "\u001B[C".toByteArray()
            KeyEvent.KEYCODE_DPAD_LEFT -> "\u001B[D".toByteArray()
            KeyEvent.KEYCODE_MOVE_HOME -> "\u001B[H".toByteArray()
            KeyEvent.KEYCODE_MOVE_END -> "\u001B[F".toByteArray()
            KeyEvent.KEYCODE_PAGE_UP -> "\u001B[5~".toByteArray()
            KeyEvent.KEYCODE_PAGE_DOWN -> "\u001B[6~".toByteArray()
            KeyEvent.KEYCODE_INSERT -> "\u001B[2~".toByteArray()
            KeyEvent.KEYCODE_FORWARD_DEL -> "\u001B[3~".toByteArray()
            KeyEvent.KEYCODE_F1 -> "\u001BOP".toByteArray()
            KeyEvent.KEYCODE_F2 -> "\u001BOQ".toByteArray()
            KeyEvent.KEYCODE_F3 -> "\u001BOR".toByteArray()
            KeyEvent.KEYCODE_F4 -> "\u001BOS".toByteArray()
            KeyEvent.KEYCODE_F5 -> "\u001B[15~".toByteArray()
            KeyEvent.KEYCODE_F6 -> "\u001B[17~".toByteArray()
            KeyEvent.KEYCODE_F7 -> "\u001B[18~".toByteArray()
            KeyEvent.KEYCODE_F8 -> "\u001B[19~".toByteArray()
            KeyEvent.KEYCODE_F9 -> "\u001B[20~".toByteArray()
            KeyEvent.KEYCODE_F10 -> "\u001B[21~".toByteArray()
            KeyEvent.KEYCODE_F11 -> "\u001B[23~".toByteArray()
            KeyEvent.KEYCODE_F12 -> "\u001B[24~".toByteArray()
            else -> {
                val unicodeChar = event.unicodeChar
                if (unicodeChar != 0) {
                    if (ctrlHeld && unicodeChar in 97..122) {
                        // Ctrl+a-z -> send 1-26
                        byteArrayOf((unicodeChar - 96).toByte())
                    } else if (ctrlHeld && unicodeChar in 65..90) {
                        // Ctrl+A-Z -> send 1-26
                        byteArrayOf((unicodeChar - 64).toByte())
                    } else if (altHeld) {
                        // Alt+key -> send ESC followed by key
                        byteArrayOf(0x1B, unicodeChar.toByte())
                    } else {
                        unicodeChar.toChar().toString().toByteArray()
                    }
                } else null
            }
        }
        
        if (bytes != null) {
            writeCallback?.invoke(bytes)
            return true
        }
        
        return super.onKeyDown(keyCode, event)
    }
    
    // Toggle modifier keys (for on-screen buttons)
    fun toggleCtrl() { ctrlDown = !ctrlDown }
    fun toggleAlt() { altDown = !altDown }
    fun toggleShift() { shiftDown = !shiftDown }
    fun toggleFn() { fnDown = !fnDown }
    
    fun isCtrlActive() = ctrlDown
    fun isAltActive() = altDown
    fun isShiftActive() = shiftDown
    fun isFnActive() = fnDown
    
    // TerminalSessionClient implementation (not all methods used for SSH)
    override fun onTextChanged(changedSession: TerminalSession) {
        mainHandler.post { invalidate() }
    }
    
    override fun onTitleChanged(changedSession: TerminalSession) {}
    
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    
    override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {
        text?.let { copyToClipboard(it) }
    }
    
    override fun onPasteTextFromClipboard(session: TerminalSession?) {
        pasteFromClipboard()
    }
    
    override fun onBell(session: TerminalSession) {}
    
    override fun onColorsChanged(session: TerminalSession) {
        mainHandler.post { invalidate() }
    }
    
    override fun onTerminalCursorStateChange(state: Boolean) {
        mainHandler.post { invalidate() }
    }
    
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    
    override fun getTerminalCursorStyle(): Int? = 0
    
    override fun logError(tag: String?, message: String?) {}
    override fun logWarn(tag: String?, message: String?) {}
    override fun logInfo(tag: String?, message: String?) {}
    override fun logDebug(tag: String?, message: String?) {}
    override fun logVerbose(tag: String?, message: String?) {}
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
    override fun logStackTrace(tag: String?, e: Exception?) {}
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
