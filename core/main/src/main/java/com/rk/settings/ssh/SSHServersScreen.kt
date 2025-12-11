package com.rk.settings.ssh

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rk.components.compose.preferences.base.PreferenceLayout
import com.rk.components.compose.preferences.base.PreferenceLayoutLazyColumn
import com.rk.DefaultScope
import com.rk.runner.ssh.SSHServerConfig
import com.rk.runner.ssh.SSHServerManager
import kotlinx.coroutines.launch

@Composable
fun SSHServersScreen() {
    var showAddDialog by remember { mutableStateOf(false) }
    var serverToEdit by remember { mutableStateOf<SSHServerConfig?>(null) }
    var serverToDelete by remember { mutableStateOf<SSHServerConfig?>(null) }
    val servers = SSHServerManager.servers
    
    PreferenceLayoutLazyColumn(
        label = "SSH Servers",
        backArrowVisible = true
    ) {
        if (servers.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No SSH servers configured",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Add a server to run code remotely on your VPS",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(servers.size) { index ->
                val server = servers[index]
                ServerItem(
                    server = server,
                    onEdit = { serverToEdit = server },
                    onDelete = { serverToDelete = server }
                )
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(80.dp))
        }
    }
    
    // FAB for adding server
    Box(modifier = Modifier.fillMaxSize()) {
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Server")
        }
    }
    
    if (showAddDialog) {
        ServerConfigDialog(
            server = null,
            onDismiss = { showAddDialog = false },
            onSave = { server ->
                DefaultScope.launch {
                    SSHServerManager.addServer(server)
                    showAddDialog = false
                }
            }
        )
    }
    
    serverToEdit?.let { server ->
        ServerConfigDialog(
            server = server,
            onDismiss = { serverToEdit = null },
            onSave = { updatedServer ->
                DefaultScope.launch {
                    SSHServerManager.updateServer(updatedServer)
                    serverToEdit = null
                }
            }
        )
    }
    
    serverToDelete?.let { server ->
        AlertDialog(
            onDismissRequest = { serverToDelete = null },
            title = { Text("Delete Server") },
            text = { Text("Are you sure you want to delete ${server.name}?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        DefaultScope.launch {
                            SSHServerManager.deleteServer(server)
                            serverToDelete = null
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { serverToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ServerItem(
    server: SSHServerConfig,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = server.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = server.getDisplayInfo(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
