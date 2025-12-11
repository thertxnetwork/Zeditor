package com.rk.settings.ssh

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.rk.DefaultScope
import com.rk.runner.ssh.DistroType
import com.rk.runner.ssh.SSHAuthType
import com.rk.runner.ssh.SSHServerConfig
import com.rk.runner.ssh.SSHServerManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerConfigDialog(
    server: SSHServerConfig?,
    onDismiss: () -> Unit,
    onSave: (SSHServerConfig) -> Unit
) {
    var name by remember { mutableStateOf(server?.name ?: "") }
    var host by remember { mutableStateOf(server?.host ?: "") }
    var port by remember { mutableStateOf(server?.port?.toString() ?: "22") }
    var username by remember { mutableStateOf(server?.username ?: "") }
    var authType by remember { mutableStateOf(server?.authType ?: SSHAuthType.PASSWORD) }
    var password by remember { mutableStateOf(server?.password ?: "") }
    var privateKey by remember { mutableStateOf(server?.privateKey ?: "") }
    var distroType by remember { mutableStateOf(server?.distroType ?: DistroType.UBUNTU) }
    var workingDirectory by remember { mutableStateOf(server?.workingDirectory ?: "~") }
    var showDistroMenu by remember { mutableStateOf(false) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (server == null) "Add SSH Server" else "Edit SSH Server") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Server Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Host
                OutlinedTextField(
                    value = host,
                    onValueChange = { host = it },
                    label = { Text("Host (IP or Domain)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Port
                OutlinedTextField(
                    value = port,
                    onValueChange = { port = it },
                    label = { Text("Port") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Auth Type
                Text("Authentication Type", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.fillMaxWidth()) {
                    FilterChip(
                        selected = authType == SSHAuthType.PASSWORD,
                        onClick = { authType = SSHAuthType.PASSWORD },
                        label = { Text("Password") },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    FilterChip(
                        selected = authType == SSHAuthType.KEY,
                        onClick = { authType = SSHAuthType.KEY },
                        label = { Text("Private Key") }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Password or Key based on auth type
                if (authType == SSHAuthType.PASSWORD) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Password") },
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = privateKey,
                        onValueChange = { privateKey = it },
                        label = { Text("Private Key (PEM format)") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 5
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Distro Type
                ExposedDropdownMenuBox(
                    expanded = showDistroMenu,
                    onExpandedChange = { showDistroMenu = !showDistroMenu }
                ) {
                    OutlinedTextField(
                        value = distroType.displayName,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Distribution Type") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDistroMenu)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    
                    ExposedDropdownMenu(
                        expanded = showDistroMenu,
                        onDismissRequest = { showDistroMenu = false }
                    ) {
                        DistroType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.displayName) },
                                onClick = {
                                    distroType = type
                                    showDistroMenu = false
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Working Directory
                OutlinedTextField(
                    value = workingDirectory,
                    onValueChange = { workingDirectory = it },
                    label = { Text("Working Directory") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Test Connection Button
                Button(
                    onClick = {
                        val testConfig = SSHServerConfig(
                            id = server?.id ?: "",
                            name = name,
                            host = host,
                            port = port.toIntOrNull() ?: 22,
                            username = username,
                            authType = authType,
                            password = password.takeIf { authType == SSHAuthType.PASSWORD },
                            privateKey = privateKey.takeIf { authType == SSHAuthType.KEY },
                            distroType = distroType,
                            workingDirectory = workingDirectory
                        )
                        
                        if (!testConfig.validate()) {
                            testResult = "Please fill all required fields"
                            return@Button
                        }
                        
                        isTesting = true
                        testResult = null
                        
                        DefaultScope.launch {
                            val result = SSHServerManager.testConnection(testConfig)
                            testResult = if (result.isSuccess) {
                                "✓ Connection successful!"
                            } else {
                                "✗ Connection failed: ${result.exceptionOrNull()?.message}"
                            }
                            isTesting = false
                        }
                    },
                    enabled = !isTesting,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isTesting) "Testing..." else "Test Connection")
                }
                
                testResult?.let { result ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (result.startsWith("✓")) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newServer = SSHServerConfig(
                        id = server?.id ?: java.util.UUID.randomUUID().toString(),
                        name = name,
                        host = host,
                        port = port.toIntOrNull() ?: 22,
                        username = username,
                        authType = authType,
                        password = password.takeIf { authType == SSHAuthType.PASSWORD },
                        privateKey = privateKey.takeIf { authType == SSHAuthType.KEY },
                        distroType = distroType,
                        workingDirectory = workingDirectory
                    )
                    
                    if (newServer.validate()) {
                        onSave(newServer)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
