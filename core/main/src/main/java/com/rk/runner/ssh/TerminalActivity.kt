package com.rk.runner.ssh

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rk.DefaultScope
import com.rk.theme.XedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

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
    var terminalOutput by remember { mutableStateOf("Connecting to ${server.name}...\n") }
    var inputText by remember { mutableStateOf("") }
    var isConnected by remember { mutableStateOf(false) }
    var connectionManager by remember { mutableStateOf<SSHConnectionManager?>(null) }
    var shellChannel by remember { mutableStateOf<ShellChannel?>(null) }
    
    // Helper function to handle command input
    val handleCommandInput: () -> Unit = {
        if (inputText.isNotBlank()) {
            val cmd = inputText
            DefaultScope.launch(Dispatchers.IO) {
                try {
                    shellChannel?.outputStream?.write("$cmd\n".toByteArray())
                    shellChannel?.outputStream?.flush()
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        terminalOutput += "\nError sending command: ${e.message}\n"
                    }
                }
            }
            terminalOutput += "$ $cmd\n"
            inputText = ""
        }
    }
    
    // Connect on start
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val manager = SSHConnectionManager(server)
            withContext(Dispatchers.Main) {
                connectionManager = manager
            }
            
            val connectResult = manager.connect()
            if (connectResult.isSuccess) {
                withContext(Dispatchers.Main) {
                    terminalOutput += "Connected to ${server.getDisplayInfo()}\n"
                }
                
                // Open shell
                val shellResult = manager.openShell()
                if (shellResult.isSuccess) {
                    val channel = shellResult.getOrNull()
                    withContext(Dispatchers.Main) {
                        shellChannel = channel
                        isConnected = true
                        terminalOutput += "Shell session started\n"
                    }
                    
                    // If file path is provided, execute it
                    if (filePath != null && fileName != null) {
                        withContext(Dispatchers.Main) {
                            terminalOutput += "Uploading and executing $fileName...\n"
                        }
                        
                        // Upload file
                        val remotePath = "${server.workingDirectory}/$fileName"
                        val uploadResult = manager.uploadFile(filePath, remotePath)
                        
                        if (uploadResult.isSuccess) {
                            withContext(Dispatchers.Main) {
                                terminalOutput += "File uploaded to $remotePath\n"
                            }
                            
                            // Execute based on file extension
                            try {
                                val command = getExecutionCommand(fileName, remotePath)
                                channel?.outputStream?.write("$command\n".toByteArray())
                                channel?.outputStream?.flush()
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    terminalOutput += "Error executing file: ${e.message}\n"
                                }
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                terminalOutput += "Error uploading file: ${uploadResult.exceptionOrNull()?.message}\n"
                            }
                        }
                    }

                    
                    // Start reading output using blocking I/O
                    DefaultScope.launch(Dispatchers.IO) {
                        try {
                            channel?.let { ch ->
                                val inputStream = ch.inputStream
                                val buffer = ByteArray(4096)
                                
                                while (ch.isOpen() && isConnected) {
                                    try {
                                        // Blocking read - more efficient and reliable
                                        val count = inputStream.read(buffer)
                                        if (count > 0) {
                                            val output = String(buffer, 0, count)
                                            withContext(Dispatchers.Main) {
                                                terminalOutput += output
                                            }
                                        } else if (count == -1) {
                                            // End of stream
                                            break
                                        }
                                    } catch (e: Exception) {
                                        if (!ch.isOpen() || !isConnected) {
                                            break
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                terminalOutput += "\nConnection error: ${e.message}\n"
                                isConnected = false
                            }
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        terminalOutput += "Failed to open shell: ${shellResult.exceptionOrNull()?.message}\n"
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    terminalOutput += "Connection failed: ${connectResult.exceptionOrNull()?.message}\n"
                }
            }
        }
    }
    
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
                                terminalOutput += "\nDisconnected\n"
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
                .background(Color.Black)
        ) {
            // Terminal output
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                val horizontalScroll = rememberScrollState()
                val verticalScroll = rememberScrollState()
                
                LaunchedEffect(terminalOutput) {
                    verticalScroll.animateScrollTo(verticalScroll.maxValue)
                }
                
                Text(
                    text = terminalOutput,
                    color = Color.Green,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(verticalScroll)
                        .horizontalScroll(horizontalScroll)
                )
            }
            
            // Input field
            if (isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = "$ ",
                            color = MaterialTheme.colorScheme.primary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp
                        )
                        
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = { handleCommandInput() }
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        
                        Button(
                            onClick = { handleCommandInput() },
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Send")
                        }
                    }
                }
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
