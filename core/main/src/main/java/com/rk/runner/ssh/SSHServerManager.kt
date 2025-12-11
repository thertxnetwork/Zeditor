package com.rk.runner.ssh

import androidx.compose.runtime.mutableStateListOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.rk.DefaultScope
import com.rk.file.child
import com.rk.file.localDir
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manager for SSH server configurations
 */
object SSHServerManager {
    
    val servers = mutableStateListOf<SSHServerConfig>()
    
    private val gson = Gson()
    
    init {
        DefaultScope.launch { loadServers() }
    }
    
    /**
     * Load servers from storage
     */
    suspend fun loadServers() {
        withContext(Dispatchers.IO) {
            try {
                val file = localDir().child("ssh_servers.json")
                if (file.exists()) {
                    val content = file.readText()
                    val type = object : TypeToken<List<SSHServerConfig>>() {}.type
                    val loadedServers = gson.fromJson<List<SSHServerConfig>>(content, type)
                    withContext(Dispatchers.Main) {
                        servers.clear()
                        servers.addAll(loadedServers)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Save servers to storage
     */
    suspend fun saveServers() {
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(servers)
                localDir().child("ssh_servers.json").writeText(json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    /**
     * Add a new server
     */
    suspend fun addServer(server: SSHServerConfig): Boolean {
        return if (server.validate() && servers.none { it.id == server.id }) {
            withContext(Dispatchers.Main) {
                servers.add(server)
            }
            saveServers()
            true
        } else {
            false
        }
    }
    
    /**
     * Update an existing server
     */
    suspend fun updateServer(server: SSHServerConfig): Boolean {
        return if (server.validate()) {
            withContext(Dispatchers.Main) {
                val index = servers.indexOfFirst { it.id == server.id }
                if (index != -1) {
                    servers[index] = server
                    true
                } else {
                    false
                }
            }.also { success ->
                if (success) saveServers()
            }
        } else {
            false
        }
    }
    
    /**
     * Delete a server
     */
    suspend fun deleteServer(server: SSHServerConfig) {
        withContext(Dispatchers.Main) {
            servers.remove(server)
        }
        saveServers()
    }
    
    /**
     * Get server by ID
     */
    fun getServer(id: String): SSHServerConfig? {
        return servers.find { it.id == id }
    }
    
    /**
     * Test connection to a server
     */
    suspend fun testConnection(server: SSHServerConfig): Result<String> {
        val manager = SSHConnectionManager(server)
        return try {
            val connectResult = manager.connect()
            if (connectResult.isSuccess) {
                val result = manager.executeCommand("echo 'Connection successful'")
                manager.disconnect()
                result
            } else {
                Result.failure(connectResult.exceptionOrNull() ?: Exception("Connection failed"))
            }
        } catch (e: Exception) {
            manager.disconnect()
            Result.failure(e)
        }
    }
}
