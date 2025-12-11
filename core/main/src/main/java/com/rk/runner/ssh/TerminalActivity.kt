package com.rk.runner.ssh

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
    var sshTerminalSession by remember { mutableStateOf<SSHTerminalSession?>(null) }
    var terminalView by remember { mutableStateOf<TerminalView?>(null) }
    
    // Terminal session client implementation
    val terminalSessionClient = remember {
        object : TerminalSessionClient {
            override fun onTextChanged(changedSession: TerminalSession?) {
                terminalView?.onScreenUpdated()
            }
            
            override fun onTitleChanged(changedSession: TerminalSession?) {}
            
            override fun onSessionFinished(finishedSession: TerminalSession?) {
                isConnected = false
                statusText = "Session ended"
            }
            
            override fun onCopyTextToClipboard(session: TerminalSession?, text: String?) {
                text?.let {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("terminal", it))
                }
            }
            
            override fun onPasteTextFromClipboard(session: TerminalSession?) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let { text ->
                    sshTerminalSession?.emulator?.paste(text)
                }
            }
            
            override fun onBell(session: TerminalSession?) {}
            
            override fun onColorsChanged(session: TerminalSession?) {
                terminalView?.invalidate()
            }
            
            override fun onTerminalCursorStateChange(state: Boolean) {}
            
            override fun setTerminalShellPid(session: TerminalSession?, pid: Int) {}
            
            override fun getTerminalCursorStyle(): Int = 0
            
            override fun logError(tag: String?, message: String?) {}
            
            override fun logWarn(tag: String?, message: String?) {}
            
            override fun logInfo(tag: String?, message: String?) {}
            
            override fun logDebug(tag: String?, message: String?) {}
            
            override fun logVerbose(tag: String?, message: String?) {}
            
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            
            override fun logStackTrace(tag: String?, e: Exception?) {}
        }
    }
    
    // Terminal view client implementation
    val terminalViewClient = remember {
        object : TerminalViewClient {
            override fun onScale(scale: Float): Float = scale
            
            override fun onSingleTapUp(e: MotionEvent?) {
                // Show keyboard when terminal is tapped
                terminalView?.let { view ->
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    imm.showSoftInput(view, 0)
                }
            }
            
            override fun shouldBackButtonBeMappedToEscape(): Boolean = false
            
            override fun shouldEnforceCharBasedInput(): Boolean = true
            
            override fun shouldUseCtrlSpaceWorkaround(): Boolean = false
            
            override fun isTerminalViewSelected(): Boolean = true
            
            override fun copyModeChanged(copyMode: Boolean) {}
            
            override fun onKeyDown(keyCode: Int, e: KeyEvent?, session: TerminalSession?): Boolean = false
            
            override fun onKeyUp(keyCode: Int, e: KeyEvent?): Boolean = false
            
            override fun onLongPress(event: MotionEvent?): Boolean = false
            
            override fun readControlKey(): Boolean = false
            
            override fun readAltKey(): Boolean = false
            
            override fun readShiftKey(): Boolean = false
            
            override fun readFnKey(): Boolean = false
            
            override fun onCodePoint(codePoint: Int, ctrlDown: Boolean, session: TerminalSession?): Boolean = false
            
            override fun onEmulatorSet() {
                terminalView?.setTerminalCursorBlinkerState(true, true)
            }
            
            override fun logError(tag: String?, message: String?) {}
            
            override fun logWarn(tag: String?, message: String?) {}
            
            override fun logInfo(tag: String?, message: String?) {}
            
            override fun logDebug(tag: String?, message: String?) {}
            
            override fun logVerbose(tag: String?, message: String?) {}
            
            override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {}
            
            override fun logStackTrace(tag: String?, e: Exception?) {}
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
                    val shellChannel = shellResult.getOrNull()
                    
                    withContext(Dispatchers.Main) {
                        if (shellChannel != null) {
                            // Create SSH terminal session
                            val session = SSHTerminalSession(
                                shellChannel.channel,
                                2000,
                                terminalSessionClient
                            )
                            sshTerminalSession = session
                            isConnected = true
                            statusText = "Shell session started"
                            
                            // If file path is provided, execute it after a short delay
                            if (filePath != null && fileName != null) {
                                DefaultScope.launch(Dispatchers.IO) {
                                    // Wait for shell to initialize
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
                                        session.write("$command\n".toByteArray())
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            statusText = "Error uploading file: ${uploadResult.exceptionOrNull()?.message}"
                                        }
                                    }
                                }
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
            sshTerminalSession?.finishIfRunning()
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
                                sshTerminalSession?.finishIfRunning()
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
            if (!isConnected || statusText.isNotEmpty()) {
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
                        TerminalView(ctx, null).apply {
                            setTerminalViewClient(terminalViewClient)
                            setTextSize(14)
                            setTypeface(Typeface.MONOSPACE)
                            isFocusable = true
                            isFocusableInTouchMode = true
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            terminalView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    update = { view ->
                        // Attach the SSH terminal session when it's ready
                        sshTerminalSession?.let { session ->
                            if (session.emulator == null) {
                                // Initialize emulator with default size, will be updated in onSizeChanged
                                val width = view.width
                                val height = view.height
                                if (width > 0 && height > 0) {
                                    // Calculate approximate rows and columns
                                    val cellWidth = 12 // Approximate cell width in pixels
                                    val cellHeight = 24 // Approximate cell height in pixels
                                    val columns = maxOf(4, width / cellWidth)
                                    val rows = maxOf(4, height / cellHeight)
                                    session.initializeEmulator(columns, rows, cellWidth, cellHeight)
                                }
                            }
                            
                            // Set the emulator on the view
                            session.emulator?.let { emulator ->
                                view.mEmulator = emulator
                                view.invalidate()
                            }
                        }
                    }
                )
            }
        }
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
