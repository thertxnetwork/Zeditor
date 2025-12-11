package com.rk.runner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.rk.theme.XedTheme

class ExecutionActivity : ComponentActivity() {
    
    companion object {
        const val EXTRA_TITLE = "execution_title"
        const val EXTRA_OUTPUT = "execution_output"
        const val EXTRA_ERROR = "execution_error"
        const val EXTRA_SUCCESS = "execution_success"
        const val EXTRA_TIME = "execution_time"
        const val EXTRA_ENGINE = "execution_engine"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Code Execution"
        val output = intent.getStringExtra(EXTRA_OUTPUT) ?: ""
        val error = intent.getStringExtra(EXTRA_ERROR) ?: ""
        val isSuccess = intent.getBooleanExtra(EXTRA_SUCCESS, true)
        val executionTime = intent.getLongExtra(EXTRA_TIME, 0)
        val engineInfo = intent.getStringExtra(EXTRA_ENGINE) ?: ""
        
        setContent {
            XedTheme {
                ExecutionScreen(
                    title = title,
                    output = output,
                    error = error,
                    isSuccess = isSuccess,
                    executionTimeMs = executionTime,
                    engineInfo = engineInfo,
                    onBackPressed = { finish() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExecutionScreen(
    title: String,
    output: String,
    error: String,
    isSuccess: Boolean,
    executionTimeMs: Long,
    engineInfo: String,
    onBackPressed: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Execution Info Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isSuccess) 
                        MaterialTheme.colorScheme.primaryContainer 
                    else 
                        MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isSuccess) "Execution Successful" else "Execution Failed",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Engine: $engineInfo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                    
                    Text(
                        text = "Execution Time: ${executionTimeMs}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSuccess)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Output Section
            if (output.isNotEmpty()) {
                Text(
                    text = "Output:",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Error Section
            if (error.isNotEmpty()) {
                Text(
                    text = "Error:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}
