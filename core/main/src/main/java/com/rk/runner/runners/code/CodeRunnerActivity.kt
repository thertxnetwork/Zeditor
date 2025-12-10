package com.rk.runner.runners.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rk.file.FileWrapper
import com.rk.resources.strings
import com.rk.theme.XedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Activity that runs code and displays the output.
 */
class CodeRunnerActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        
        setContent {
            XedTheme {
                if (filePath != null) {
                    CodeRunnerScreen(
                        filePath = filePath,
                        onBack = { finish() }
                    )
                } else {
                    ErrorScreen(
                        message = "No file path provided",
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

sealed class ExecutionState {
    data object Idle : ExecutionState()
    data object Running : ExecutionState()
    data class Completed(val result: ExecutionResult) : ExecutionState()
    data class Error(val message: String) : ExecutionState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CodeRunnerScreen(
    filePath: String,
    onBack: () -> Unit
) {
    val file = remember { FileWrapper(File(filePath)) }
    val fileName = remember { file.getName() }
    val languageConfig = remember { CodeExecutor.getLanguageConfig(file) }
    
    var executionState by remember { mutableStateOf<ExecutionState>(ExecutionState.Idle) }
    var output by remember { mutableStateOf("") }
    
    val scope = rememberCoroutineScope()
    val verticalScrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    
    // Function to run the code
    fun runCode() {
        scope.launch {
            executionState = ExecutionState.Running
            output = ""
            
            val result = withContext(Dispatchers.IO) {
                CodeExecutor.execute(
                    fileObject = file,
                    timeout = 60000, // 60 second timeout
                    onOutput = { line ->
                        output += "$line\n"
                    }
                )
            }
            
            if (result.error != null) {
                executionState = ExecutionState.Error(result.error)
            } else {
                executionState = ExecutionState.Completed(result)
            }
        }
    }
    
    // Auto-run on launch
    LaunchedEffect(Unit) {
        runCode()
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = fileName,
                            style = MaterialTheme.typography.titleMedium
                        )
                        languageConfig?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { runCode() },
                        enabled = executionState !is ExecutionState.Running
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(strings.rerun_code))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Status card
            StatusCard(executionState = executionState)
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Output card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(strings.output),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp)
                    ) {
                        when (executionState) {
                            is ExecutionState.Idle -> {
                                Text(
                                    text = "Waiting to execute...",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            is ExecutionState.Running -> {
                                Column {
                                    if (output.isNotEmpty()) {
                                        SelectionContainer {
                                            Text(
                                                text = output,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp,
                                                modifier = Modifier
                                                    .verticalScroll(verticalScrollState)
                                                    .horizontalScroll(horizontalScrollState)
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Executing...",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }
                            }
                            is ExecutionState.Completed -> {
                                val result = (executionState as ExecutionState.Completed).result
                                SelectionContainer {
                                    Text(
                                        text = if (result.output.isNotEmpty()) result.output else "(No output)",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = if (result.output.isEmpty()) 
                                            MaterialTheme.colorScheme.onSurfaceVariant 
                                        else 
                                            MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .verticalScroll(verticalScrollState)
                                            .horizontalScroll(horizontalScrollState)
                                    )
                                }
                            }
                            is ExecutionState.Error -> {
                                val errorMessage = (executionState as ExecutionState.Error).message
                                SelectionContainer {
                                    Text(
                                        text = errorMessage,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .verticalScroll(verticalScrollState)
                                            .horizontalScroll(horizontalScrollState)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusCard(executionState: ExecutionState) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = when (executionState) {
                is ExecutionState.Idle -> MaterialTheme.colorScheme.surfaceVariant
                is ExecutionState.Running -> MaterialTheme.colorScheme.primaryContainer
                is ExecutionState.Completed -> {
                    val result = executionState.result
                    if (result.exitCode == 0) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.errorContainer
                    }
                }
                is ExecutionState.Error -> MaterialTheme.colorScheme.errorContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (executionState) {
                    is ExecutionState.Idle -> {
                        Text(
                            text = "Ready",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is ExecutionState.Running -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Running...",
                            fontWeight = FontWeight.Medium
                        )
                    }
                    is ExecutionState.Completed -> {
                        val result = executionState.result
                        if (result.exitCode == 0) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (result.exitCode == 0) "Completed" else "Failed",
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Exit code: ${result.exitCode} • ${result.executionTimeMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    is ExecutionState.Error -> {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Error",
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ErrorScreen(
    message: String,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Error") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
