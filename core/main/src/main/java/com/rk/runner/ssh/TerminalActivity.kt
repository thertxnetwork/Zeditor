package com.rk.runner.ssh

import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
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
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import com.termux.view.TerminalView
import com.termux.view.TerminalViewClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val context = LocalContext.current
    var statusText by remember { mutableStateOf("Connecting to ${server.name}...") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionManager by remember { mutableStateOf<SSHConnectionManager?>(null) }
    var shellChannel by remember { mutableStateOf<ShellChannel?>(null) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    var sshSession by remember { mutableStateOf<SSHTerminalSession?>(null) }
    
    // Extra key row state
    var ctrlActive by remember { mutableStateOf(false) }
    var altActive by remember { mutableStateOf(false) }
    var shiftActive by remember { mutableStateOf(false) }
    
    // Connect the shell channel to terminal view when both are available
    LaunchedEffect(shellChannel, terminalView, sshSession) {
        val channel = shellChannel
        val view = terminalView
        val session = sshSession
        if (channel != null && view != null && session != null) {
            // Set up the SSH session to write to the channel
            session.setOutputStream(channel.outputStream)
            
            // Start reading from SSH and feeding to terminal
            DefaultScope.launch(Dispatchers.IO) {
                try {
                    val buffer = ByteArray(4096)
                    while (channel.isOpen()) {
                        val bytesRead = channel.inputStream.read(buffer)
                        if (bytesRead > 0) {
                            session.processInput(buffer, bytesRead)
                            withContext(Dispatchers.Main) {
                                view.onScreenUpdated()
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
                        TerminalView(ctx, null).also { view ->
                            // Create session client and view client
                            val sessionClient = SSHTerminalSessionClient()
                            val viewClient = SSHTerminalViewClient(view, 
                                onCtrlRead = { ctrlActive },
                                onAltRead = { altActive },
                                onShiftRead = { shiftActive }
                            )
                            
                            // Create the SSH terminal session
                            val session = SSHTerminalSession(sessionClient)
                            
                            // Set up the terminal view
                            view.setTerminalViewClient(viewClient)
                            view.attachSession(session)
                            
                            // Apply theme
                            val theme = TerminalThemes.getThemeById(Settings.terminal_theme)
                            session.emulator?.mColors?.apply {
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
                            
                            view.setTextSize(Settings.terminal_font_size)
                            view.setBackgroundColor(theme.backgroundColor)
                            
                            terminalView = view
                            sshSession = session
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // View updates are handled through state
                    }
                )
            }
            
            // Extra key row
            if (Settings.terminal_show_extra_keys) {
                ExtraKeyRow(
                    sshSession = sshSession,
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
    sshSession: SSHTerminalSession?,
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
                sshSession?.write(byteArrayOf(0x1B))
            }
            
            // Tab key
            ExtraKeyButton(text = "TAB") {
                sshSession?.write(byteArrayOf('\t'.code.toByte()))
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
            
            if (Settings.terminal_show_arrow_keys) {
                // Arrow keys
                ExtraKeyButton(text = "↑") {
                    sshSession?.write("\u001B[A".toByteArray())
                }
                ExtraKeyButton(text = "↓") {
                    sshSession?.write("\u001B[B".toByteArray())
                }
                ExtraKeyButton(text = "←") {
                    sshSession?.write("\u001B[D".toByteArray())
                }
                ExtraKeyButton(text = "→") {
                    sshSession?.write("\u001B[C".toByteArray())
                }
            }
            
            // Home/End
            ExtraKeyButton(text = "HOME") {
                sshSession?.write("\u001B[H".toByteArray())
            }
            ExtraKeyButton(text = "END") {
                sshSession?.write("\u001B[F".toByteArray())
            }
            
            // Page Up/Down
            ExtraKeyButton(text = "PGUP") {
                sshSession?.write("\u001B[5~".toByteArray())
            }
            ExtraKeyButton(text = "PGDN") {
                sshSession?.write("\u001B[6~".toByteArray())
            }
            
            // Common shortcuts
            ExtraKeyButton(text = "-") {
                sshSession?.write("-".toByteArray())
            }
            ExtraKeyButton(text = "/") {
                sshSession?.write("/".toByteArray())
            }
            ExtraKeyButton(text = "|") {
                sshSession?.write("|".toByteArray())
            }
            ExtraKeyButton(text = "~") {
                sshSession?.write("~".toByteArray())
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
 * Custom TerminalSession that works with SSH streams instead of local PTY.
 * This is a simplified implementation that delegates terminal emulation to the parent
 * but handles I/O through SSH channels.
 */
class SSHTerminalSession(
    private val client: TerminalSessionClient
) : TerminalSession("/system/bin/sh", "/", arrayOf(), arrayOf(), 2000, client) {
    
    private var sshOutputStream: java.io.OutputStream? = null
    var emulator: TerminalEmulator? = null
        private set
    
    init {
        // Initialize emulator with default size
        initializeForSSH(80, 24)
    }
    
    private fun initializeForSSH(columns: Int, rows: Int) {
        // Create emulator without starting a subprocess
        emulator = TerminalEmulator(
            this,
            columns,
            rows,
            16,  // cell width pixels
            32,  // cell height pixels
            2000, // transcript rows
            client
        )
    }
    
    fun setOutputStream(outputStream: java.io.OutputStream) {
        sshOutputStream = outputStream
    }
    
    /**
     * Process input from SSH channel and feed to emulator
     */
    fun processInput(data: ByteArray, length: Int) {
        emulator?.append(data, length)
    }
    
    /**
     * Write data to SSH channel
     */
    override fun write(data: ByteArray, offset: Int, count: Int) {
        try {
            sshOutputStream?.write(data, offset, count)
            sshOutputStream?.flush()
        } catch (e: Exception) {
            Log.e("SSHTerminalSession", "Write error: ${e.message}")
        }
    }
    
    /**
     * Write data to SSH channel (convenience method)
     */
    fun write(data: ByteArray) {
        write(data, 0, data.size)
    }
    
    override fun getEmulator(): TerminalEmulator? = emulator
    
    override fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        if (emulator == null) {
            initializeForSSH(columns, rows)
        } else {
            emulator?.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }
}

/**
 * Terminal session client for SSH
 */
class SSHTerminalSessionClient : TerminalSessionClient {
    override fun onTextChanged(changedSession: TerminalSession) {
        // Handle text changes if needed
    }
    
    override fun onTitleChanged(changedSession: TerminalSession) {}
    
    override fun onSessionFinished(finishedSession: TerminalSession) {}
    
    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
    
    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
    
    override fun onBell(session: TerminalSession) {}
    
    override fun onColorsChanged(session: TerminalSession) {}
    
    override fun onTerminalCursorStateChange(state: Boolean) {}
    
    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
    
    override fun getTerminalCursorStyle(): Int = TerminalEmulator.DEFAULT_TERMINAL_CURSOR_STYLE
    
    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: "SSHTerminal", message ?: "")
    }
    
    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: "SSHTerminal", message ?: "")
    }
    
    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: "SSHTerminal", message ?: "")
    }
    
    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: "SSHTerminal", message ?: "")
    }
    
    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: "SSHTerminal", message ?: "")
    }
    
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "SSHTerminal", message ?: "", e)
    }
    
    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "SSHTerminal", "Stack trace", e)
    }
}

/**
 * Terminal view client for SSH
 */
class SSHTerminalViewClient(
    private val terminalView: TerminalView,
    private val onCtrlRead: () -> Boolean,
    private val onAltRead: () -> Boolean,
    private val onShiftRead: () -> Boolean
) : TerminalViewClient {
    
    override fun onScale(scale: Float): Float {
        val fontScale = scale.coerceIn(8f, 32f)
        terminalView.setTextSize(fontScale.toInt())
        return fontScale
    }
    
    override fun onSingleTapUp(e: MotionEvent) {
        terminalView.requestFocus()
        val imm = terminalView.context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) 
            as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(terminalView, 0)
    }
    
    override fun shouldBackButtonBeMappedToEscape(): Boolean = false
    
    override fun shouldEnforceCharBasedInput(): Boolean = true
    
    override fun shouldUseCtrlSpaceWorkaround(): Boolean = true
    
    override fun isTerminalViewSelected(): Boolean = true
    
    override fun copyModeChanged(copyMode: Boolean) {}
    
    override fun onKeyDown(keyCode: Int, e: KeyEvent, session: TerminalSession): Boolean = false
    
    override fun onKeyUp(keyCode: Int, e: KeyEvent): Boolean = false
    
    override fun onLongPress(event: MotionEvent): Boolean = false
    
    override fun readControlKey(): Boolean = onCtrlRead()
    
    override fun readAltKey(): Boolean = onAltRead()
    
    override fun readShiftKey(): Boolean = onShiftRead()
    
    override fun readFnKey(): Boolean = false
    
    override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession): Boolean = false
    
    override fun onEmulatorSet() {
        terminalView.setTerminalCursorBlinkerState(true, true)
    }
    
    override fun logError(tag: String?, message: String?) {
        Log.e(tag ?: "TerminalView", message ?: "")
    }
    
    override fun logWarn(tag: String?, message: String?) {
        Log.w(tag ?: "TerminalView", message ?: "")
    }
    
    override fun logInfo(tag: String?, message: String?) {
        Log.i(tag ?: "TerminalView", message ?: "")
    }
    
    override fun logDebug(tag: String?, message: String?) {
        Log.d(tag ?: "TerminalView", message ?: "")
    }
    
    override fun logVerbose(tag: String?, message: String?) {
        Log.v(tag ?: "TerminalView", message ?: "")
    }
    
    override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
        Log.e(tag ?: "TerminalView", message ?: "", e)
    }
    
    override fun logStackTrace(tag: String?, e: Exception?) {
        Log.e(tag ?: "TerminalView", "Stack trace", e)
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
