package com.rk.runner

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.rk.file.FileObject
import com.rk.file.FileWrapper
import com.rk.runner.runners.languages.ExecutionResult
import com.rk.runner.runners.languages.LanguageRunner
import com.rk.theme.XedTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

/**
 * ExecutionActivity - A dedicated screen for code execution with real-time output,
 * execution time, and compiler/interpreter information.
 */
class ExecutionActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_FILE_PATH = "file_path"
        private const val EXTRA_LANGUAGE_NAME = "language_name"
        private const val EXTRA_RUNNER_CLASS = "runner_class"

        fun createIntent(
            context: Context,
            fileObject: FileObject,
            languageName: String,
            runnerClass: String
        ): Intent {
            return Intent(context, ExecutionActivity::class.java).apply {
                putExtra(EXTRA_FILE_PATH, (fileObject as? FileWrapper)?.getAbsolutePath())
                putExtra(EXTRA_LANGUAGE_NAME, languageName)
                putExtra(EXTRA_RUNNER_CLASS, runnerClass)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        val languageName = intent.getStringExtra(EXTRA_LANGUAGE_NAME) ?: "Unknown"
        val runnerClassName = intent.getStringExtra(EXTRA_RUNNER_CLASS)

        setContent {
            XedTheme {
                ExecutionScreen(
                    filePath = filePath,
                    languageName = languageName,
                    runnerClassName = runnerClassName,
                    onBack = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionScreen(
    filePath: String?,
    languageName: String,
    runnerClassName: String?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isRunning by remember { mutableStateOf(false) }
    var output by remember { mutableStateOf("") }
    var errorOutput by remember { mutableStateOf("") }
    var executionTime by remember { mutableLongStateOf(0L) }
    var isSuccess by remember { mutableStateOf<Boolean?>(null) }
    var startTime by remember { mutableStateOf<String?>(null) }
    var endTime by remember { mutableStateOf<String?>(null) }
    var compilerInfo by remember { mutableStateOf("") }
    var currentRunner by remember { mutableStateOf<LanguageRunner?>(null) }

    val scrollState = rememberScrollState()
    val horizontalScrollState = rememberScrollState()
    val dateFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    fun runCode() {
        if (filePath == null || runnerClassName == null) {
            errorOutput = "Error: Invalid file path or runner configuration"
            return
        }

        scope.launch {
            isRunning = true
            output = ""
            errorOutput = ""
            isSuccess = null
            startTime = dateFormat.format(Date())
            endTime = null

            try {
                // Create runner instance
                val runnerClass = Class.forName(runnerClassName)
                val runner = runnerClass.getDeclaredConstructor().newInstance() as LanguageRunner
                currentRunner = runner

                // Set compiler info
                compilerInfo = buildString {
                    append("Interpreter: ${runner.getName()}\n")
                    append("Language: ${runner.getLanguageName()}\n")
                    append("Extensions: ${runner.getSupportedExtensions().joinToString(", ")}\n")
                    append("Runtime: Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                }

                // Read file content
                val file = File(filePath)
                val code = withContext(Dispatchers.IO) { file.readText() }

                // Execute code
                val result = runner.executeCode(code)

                // Update UI with results
                output = result.output
                errorOutput = result.errorOutput
                executionTime = result.executionTimeMs
                isSuccess = result.isSuccess
                endTime = dateFormat.format(Date())

            } catch (e: Exception) {
                errorOutput = "Execution Error: ${e.message}\n${e.stackTraceToString()}"
                isSuccess = false
                endTime = dateFormat.format(Date())
            } finally {
                isRunning = false
                currentRunner = null
            }
        }
    }

    fun stopExecution() {
        scope.launch {
            currentRunner?.stop()
            isRunning = false
            endTime = dateFormat.format(Date())
            errorOutput = "Execution stopped by user"
            isSuccess = false
        }
    }

    // Auto-run on first load
    LaunchedEffect(Unit) {
        runCode()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Execution",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = languageName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isRunning) {
                        IconButton(onClick = { stopExecution() }) {
                            Icon(
                                Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        IconButton(onClick = { runCode() }) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Run Again",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Compiler/Interpreter Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Compiler Info",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = compilerInfo.ifEmpty { "Loading..." },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Execution Time Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = when (isSuccess) {
                        true -> MaterialTheme.colorScheme.primaryContainer
                        false -> MaterialTheme.colorScheme.errorContainer
                        null -> MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Execution Time",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isRunning) "Running..." else formatExecutionTime(executionTime),
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        startTime?.let {
                            Text(
                                text = "Start: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        endTime?.let {
                            Text(
                                text = "End: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Progress indicator
                AnimatedVisibility(visible = isRunning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Status Badge
            isSuccess?.let { success ->
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = if (success) Color(0xFF4CAF50) else Color(0xFFF44336),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (success) "SUCCESS" else "FAILED",
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Output Section
            Text(
                text = "Output",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E1E))
                        .horizontalScroll(horizontalScrollState)
                        .verticalScroll(scrollState)
                        .padding(12.dp)
                ) {
                    Column {
                        // Standard output
                        if (output.isNotEmpty()) {
                            Text(
                                text = output,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF4EC9B0),
                                lineHeight = 18.sp
                            )
                        }

                        // Error output
                        if (errorOutput.isNotEmpty()) {
                            if (output.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = Color(0xFF424242))
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Text(
                                text = errorOutput,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFF44336),
                                lineHeight = 18.sp
                            )
                        }

                        // Empty state
                        if (output.isEmpty() && errorOutput.isEmpty() && !isRunning) {
                            Text(
                                text = "(No output)",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFF808080),
                                lineHeight = 18.sp
                            )
                        }

                        // Running state
                        if (isRunning && output.isEmpty() && errorOutput.isEmpty()) {
                            Text(
                                text = "Executing code...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = Color(0xFFFFD700),
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            // File path info
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "File: ${filePath ?: "Unknown"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

private fun formatExecutionTime(timeMs: Long): String {
    return when {
        timeMs < 1000 -> "${timeMs}ms"
        timeMs < 60000 -> String.format("%.2fs", timeMs / 1000.0)
        else -> {
            val minutes = timeMs / 60000
            val seconds = (timeMs % 60000) / 1000.0
            String.format("%dm %.2fs", minutes, seconds)
        }
    }
}
